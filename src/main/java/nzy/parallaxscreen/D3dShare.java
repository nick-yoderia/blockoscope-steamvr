package nzy.parallaxscreen;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Hands the screen to SteamVR as a Direct3D 11 texture instead of an OpenGL one.
 *
 * Given an OpenGL texture, SteamVR's client library shares it with its compositor through the driver's OpenGL/Direct3D
 * interop on its own, and on AMD that wait occasionally never returned (the render thread blocked inside the GL driver
 * for good, even after SteamVR was closed). Here the mod owns the interop instead: a D3D11 device of its own, one
 * D3D11 texture registered with OpenGL through WGL_NV_DX_interop, and a GPU copy of the packed eyes into it each frame
 * between lock and unlock. Only this process ever uses that texture, so the lock waits on nothing but our own GPU
 * work; SteamVR then takes it by the D3D11 path most overlay applications use.
 *
 * Direct3D's rows run top-down and OpenGL's bottom-up, so the picture arrives upside down; the overlay's texture
 * bounds flip it back ({@link #FLIPPED}).
 */
final class D3dShare {
    /** The copy lands upside down for Direct3D; the overlay bounds must flip it. */
    static final boolean FLIPPED = true;

    private static final int D3D_DRIVER_TYPE_HARDWARE = 1;
    private static final int D3D11_SDK_VERSION = 7;
    private static final int DXGI_FORMAT_R8G8B8A8_UNORM = 28;
    private static final int D3D11_BIND_SHADER_RESOURCE = 0x8;
    private static final int D3D11_BIND_RENDER_TARGET = 0x20;
    private static final int WGL_ACCESS_WRITE_DISCARD_NV = 0x2;

    // COM vtable slots (d3d11.h).
    private static final int IUNKNOWN_RELEASE = 2;
    private static final int DEVICE_CREATE_TEXTURE_2D = 5;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena ARENA = Arena.ofAuto();
    /** D3D11_TEXTURE2D_DESC: eleven 32-bit fields. */
    private static final MemorySegment DESC = ARENA.allocate(44, 4);
    private static final MemorySegment OUT = ARENA.allocate(ADDRESS);
    private static final MemorySegment OBJECTS = ARENA.allocate(ADDRESS);

    private static boolean initialized;
    private static String failure;
    private static MemorySegment device = MemorySegment.NULL;
    private static MethodHandle createTexture2D;

    private static MethodHandle dxOpenDevice;
    private static MethodHandle dxRegisterObject;
    private static MethodHandle dxUnregisterObject;
    private static MethodHandle dxLockObjects;
    private static MethodHandle dxUnlockObjects;
    private static MemorySegment interopDevice = MemorySegment.NULL;

    private static MemorySegment texture = MemorySegment.NULL;
    private static MemorySegment interopObject = MemorySegment.NULL;
    private static int glTexture;
    private static int width;
    private static int height;

    private D3dShare() {}

    /** Why the D3D11 path can't be used (the screen then goes over as an OpenGL texture), or null. */
    static String failure() {
        return failure;
    }

    /**
     * Copies the OpenGL texture {@code source} (RGBA8, {@code w} x {@code h}) into the shared D3D11 texture and returns
     * that texture (an ID3D11Texture2D pointer) for SteamVR, or {@link MemorySegment#NULL} if this frame can't go this
     * way. Render thread, with the game's OpenGL context current.
     */
    static MemorySegment frame(int source, int w, int h) {
        if (failure != null) {
            return MemorySegment.NULL;
        }
        try {
            if (!initialized) {
                initialized = true;
                init();
            }
            if (w != width || h != height || texture.address() == 0L) {
                resize(w, h);
            }
            OBJECTS.set(ADDRESS, 0, interopObject);
            if ((int) dxLockObjects.invokeExact(interopDevice, 1, OBJECTS) == 0) {
                return MemorySegment.NULL;
            }
            try {
                GL43.glCopyImageSubData(source, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                    glTexture, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            } finally {
                OBJECTS.set(ADDRESS, 0, interopObject);
                int ignored = (int) dxUnlockObjects.invokeExact(interopDevice, 1, OBJECTS);
            }
            return texture;
        } catch (Throwable t) {
            failure = t.getMessage() != null ? t.getMessage() : t.toString();
            return MemorySegment.NULL;
        }
    }

    private static void init() throws Throwable {
        SymbolLookup d3d11 = SymbolLookup.libraryLookup("d3d11", ARENA);
        MethodHandle createDevice = LINKER.downcallHandle(d3d11.findOrThrow("D3D11CreateDevice"), FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        // The default adapter: the one the desktop, and so the game's OpenGL context, runs on.
        int result = (int) createDevice.invokeExact(MemorySegment.NULL, D3D_DRIVER_TYPE_HARDWARE, MemorySegment.NULL, 0,
            MemorySegment.NULL, 0, D3D11_SDK_VERSION, OUT, MemorySegment.NULL, MemorySegment.NULL);
        if (result < 0) {
            throw new IllegalStateException("Direct3D 11 device creation failed (0x" + Integer.toHexString(result) + ")");
        }
        device = OUT.get(ADDRESS, 0);
        MemorySegment vtable = device.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0).reinterpret(ADDRESS.byteSize() * 64);
        createTexture2D = LINKER.downcallHandle(vtable.getAtIndex(ADDRESS, DEVICE_CREATE_TEXTURE_2D),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        SymbolLookup opengl = SymbolLookup.libraryLookup("opengl32", ARENA);
        MethodHandle getProcAddress = LINKER.downcallHandle(opengl.findOrThrow("wglGetProcAddress"),
            FunctionDescriptor.of(ADDRESS, ADDRESS));
        dxOpenDevice = wgl(getProcAddress, "wglDXOpenDeviceNV", FunctionDescriptor.of(ADDRESS, ADDRESS));
        dxRegisterObject = wgl(getProcAddress, "wglDXRegisterObjectNV",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
        dxUnregisterObject = wgl(getProcAddress, "wglDXUnregisterObjectNV", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        dxLockObjects = wgl(getProcAddress, "wglDXLockObjectsNV", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        dxUnlockObjects = wgl(getProcAddress, "wglDXUnlockObjectsNV", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        interopDevice = (MemorySegment) dxOpenDevice.invokeExact(device);
        if (interopDevice.address() == 0L) {
            throw new IllegalStateException("the OpenGL driver can't share textures with Direct3D 11");
        }
    }

    private static MethodHandle wgl(MethodHandle getProcAddress, String name, FunctionDescriptor descriptor) throws Throwable {
        MemorySegment address;
        try (Arena arena = Arena.ofConfined()) {
            address = (MemorySegment) getProcAddress.invokeExact(arena.allocateFrom(name));
        }
        if (address.address() == 0L) {
            throw new IllegalStateException("the OpenGL driver lacks " + name);
        }
        return LINKER.downcallHandle(address, descriptor);
    }

    private static void resize(int w, int h) throws Throwable {
        freeTexture();
        DESC.setAtIndex(JAVA_INT, 0, w);
        DESC.setAtIndex(JAVA_INT, 1, h);
        DESC.setAtIndex(JAVA_INT, 2, 1);                                   // MipLevels
        DESC.setAtIndex(JAVA_INT, 3, 1);                                   // ArraySize
        DESC.setAtIndex(JAVA_INT, 4, DXGI_FORMAT_R8G8B8A8_UNORM);
        DESC.setAtIndex(JAVA_INT, 5, 1);                                   // SampleDesc.Count
        DESC.setAtIndex(JAVA_INT, 6, 0);                                   // SampleDesc.Quality
        DESC.setAtIndex(JAVA_INT, 7, 0);                                   // Usage: default
        DESC.setAtIndex(JAVA_INT, 8, D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET);
        DESC.setAtIndex(JAVA_INT, 9, 0);                                   // CPUAccessFlags
        DESC.setAtIndex(JAVA_INT, 10, 0);                                  // MiscFlags
        int result = (int) createTexture2D.invokeExact(device, DESC, MemorySegment.NULL, OUT);
        if (result < 0) {
            throw new IllegalStateException("Direct3D 11 texture creation failed (0x" + Integer.toHexString(result) + ")");
        }
        texture = OUT.get(ADDRESS, 0);
        glTexture = GL11.glGenTextures();
        interopObject = (MemorySegment) dxRegisterObject.invokeExact(interopDevice, texture, glTexture,
            GL11.GL_TEXTURE_2D, WGL_ACCESS_WRITE_DISCARD_NV);
        if (interopObject.address() == 0L) {
            throw new IllegalStateException("OpenGL could not share a Direct3D 11 texture (0x"
                + Integer.toHexString(GL11.glGetError()) + ")");
        }
        width = w;
        height = h;
    }

    private static void freeTexture() throws Throwable {
        if (interopObject.address() != 0L) {
            int ignored = (int) dxUnregisterObject.invokeExact(interopDevice, interopObject);
            interopObject = MemorySegment.NULL;
        }
        if (glTexture != 0) {
            GL11.glDeleteTextures(glTexture);
            glTexture = 0;
        }
        if (texture.address() != 0L) {
            release(texture);
            texture = MemorySegment.NULL;
        }
    }

    /** IUnknown::Release through the object's own vtable. */
    private static void release(MemorySegment object) throws Throwable {
        MemorySegment vtable = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0).reinterpret(ADDRESS.byteSize() * 3);
        MethodHandle release = LINKER.downcallHandle(vtable.getAtIndex(ADDRESS, IUNKNOWN_RELEASE),
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
        int ignored = (int) release.invokeExact(object);
    }
}
