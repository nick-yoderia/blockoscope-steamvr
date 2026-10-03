package nzy.parallaxscreen;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The few OpenVR calls the SteamVR screen needs, made straight into Valve's openvr_api.dll with Java's foreign
 * function API.
 *
 * Not LWJGL's OpenVR bindings: the last release of those (3.3.6) was built for LWJGL 3.3, and Minecraft 26.2 ships
 * LWJGL 3.4, which dropped the configuration field and native call helpers they use (Vivecraft patches a dozen of
 * their classes with mixins to keep them working). openvr_api.dll is a plain C library: its exports start SteamVR
 * and hand out each interface as a table of C function pointers ("FnTable:" + interface version), so only the slot
 * numbers below, taken from Valve's openvr_capi.h for these interface versions, are needed.
 */
final class OpenVrApi {
    static final String SYSTEM_VERSION = "IVRSystem_022";
    static final String OVERLAY_VERSION = "IVROverlay_027";

    static final int APPLICATION_OVERLAY = 2;
    static final int TEXTURE_OPENGL = 1;
    static final int COLOR_SPACE_AUTO = 0;
    static final int OVERLAY_FLAG_SIDE_BY_SIDE_PARALLEL = 1 << 10;
    static final int OVERLAY_FLAG_IGNORE_TEXTURE_ALPHA = 1 << 22;
    /** Tracking spaces: seated (moved by SteamVR's "reset seated position") and standing (room scale). */
    static final int UNIVERSE_SEATED = 0;
    static final int UNIVERSE_STANDING = 1;
    static final int EVENT_QUIT = 700;
    static final int MAX_DEVICES = 64;
    static final int HMD_INDEX = 0;

    /** sizeof(VREvent_t), sizeof(TrackedDevicePose_t) and the pose's bPoseIsValid offset on 64-bit Windows. */
    static final int EVENT_SIZE = 64;
    static final int POSE_SIZE = 80;
    static final int POSE_VALID_OFFSET = 76;

    // Slots in the function tables of the interface versions above.
    private static final int OVERLAY_CREATE = 1;
    private static final int OVERLAY_DESTROY = 2;
    private static final int OVERLAY_ERROR_NAME = 7;
    private static final int OVERLAY_SET_FLAG = 10;
    private static final int OVERLAY_SET_WIDTH = 21;
    private static final int OVERLAY_SET_CURVATURE = 23;
    private static final int OVERLAY_SET_TEXTURE_BOUNDS = 29;
    private static final int OVERLAY_SET_TRANSFORM_ABSOLUTE = 32;
    private static final int OVERLAY_SHOW = 41;
    private static final int OVERLAY_SET_TEXTURE = 58;
    private static final int SYSTEM_GET_POSES = 11;
    private static final int SYSTEM_POLL_EVENT = 29;
    private static final int SYSTEM_ACKNOWLEDGE_QUIT = 43;

    private static final Linker LINKER = Linker.nativeLinker();
    private static SymbolLookup library;

    private static MethodHandle isRuntimeInstalled;
    private static MethodHandle initInternal2;
    private static MethodHandle shutdownInternal;
    private static MethodHandle initErrorDescription;
    private static MethodHandle getGenericInterface;
    private static MethodHandle isInterfaceVersionValid;

    private static MethodHandle createOverlay;
    private static MethodHandle destroyOverlay;
    private static MethodHandle overlayErrorName;
    private static MethodHandle setOverlayFlag;
    private static MethodHandle setOverlayWidth;
    private static MethodHandle setOverlayCurvature;
    private static MethodHandle setOverlayTextureBounds;
    private static MethodHandle setOverlayTransformAbsolute;
    private static MethodHandle showOverlay;
    private static MethodHandle setOverlayTexture;
    private static MethodHandle getPoses;
    private static MethodHandle pollEvent;
    private static MethodHandle acknowledgeQuit;

    private OpenVrApi() {}

    /** Loads openvr_api.dll (copied out of the mod jar) and its exported functions; once. */
    static synchronized void load() throws IOException {
        if (library != null) {
            return;
        }
        Path dll = Path.of(".parallax-screen", "openvr_api.dll").toAbsolutePath();
        Files.createDirectories(dll.getParent());
        try (InputStream in = OpenVrApi.class.getResourceAsStream("/natives/windows-x64/openvr_api.dll")) {
            if (in == null) {
                throw new IOException("openvr_api.dll is missing from the mod jar");
            }
            try {
                Files.copy(in, dll, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Already loaded by another running copy of the game; the file on disk is the same.
                if (!Files.isRegularFile(dll)) {
                    throw e;
                }
            }
        }
        applyDevelopmentEnvironment();
        SymbolLookup lookup = SymbolLookup.libraryLookup(dll, Arena.global());
        isRuntimeInstalled = export(lookup, "VR_IsRuntimeInstalled", FunctionDescriptor.of(JAVA_BOOLEAN));
        initInternal2 = export(lookup, "VR_InitInternal2", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        shutdownInternal = export(lookup, "VR_ShutdownInternal", FunctionDescriptor.ofVoid());
        initErrorDescription = export(lookup, "VR_GetVRInitErrorAsEnglishDescription", FunctionDescriptor.of(ADDRESS, JAVA_INT));
        getGenericInterface = export(lookup, "VR_GetGenericInterface", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
        isInterfaceVersionValid = export(lookup, "VR_IsInterfaceVersionValid", FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));
        library = lookup;
    }

    /**
     * Development only: {@code config/parallax-screen.vrenv} holds KEY=VALUE lines set as environment variables of the
     * game before OpenVR starts (a SteamVR it launches inherits them). With VR_CONFIG_PATH pointing at a separate
     * config folder that forces SteamVR's null headset driver, the screen can be tested without a headset and without
     * touching the real SteamVR settings.
     */
    private static void applyDevelopmentEnvironment() {
        Path file = Path.of("config", "parallax-screen.vrenv");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle setVariable = LINKER.downcallHandle(
                SymbolLookup.libraryLookup("kernel32", arena).findOrThrow("SetEnvironmentVariableW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
            for (String line : Files.readAllLines(file)) {
                int equals = line.indexOf('=');
                if (equals <= 0 || line.startsWith("#")) {
                    continue;
                }
                String key = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                int ok = (int) setVariable.invokeExact(wide(arena, key), wide(arena, value));
                System.out.println("[Parallax Screen] Development environment: " + key + "=" + value + (ok != 0 ? "" : " (failed)"));
            }
        } catch (Throwable t) {
            System.out.println("[Parallax Screen] Could not apply " + file + ": " + t);
        }
    }

    private static MemorySegment wide(Arena arena, String text) {
        return arena.allocateFrom(text + "\0", java.nio.charset.StandardCharsets.UTF_16LE);
    }

    private static MethodHandle export(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(lookup.findOrThrow(name), descriptor);
    }

    static boolean isRuntimeInstalled() throws Throwable {
        return (boolean) isRuntimeInstalled.invokeExact();
    }

    /** Starts OpenVR as an overlay application and looks up the interfaces; returns null or an error message. */
    static String init() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment error = arena.allocate(JAVA_INT);
            int token = (int) initInternal2.invokeExact(error, APPLICATION_OVERLAY, MemorySegment.NULL);
            int code = error.get(JAVA_INT, 0);
            if (code != 0) {
                return initErrorText(code);
            }
            for (String version : new String[] {SYSTEM_VERSION, OVERLAY_VERSION}) {
                if (!(boolean) isInterfaceVersionValid.invokeExact(arena.allocateFrom(version))) {
                    shutdown();
                    return "this SteamVR doesn't offer " + version;
                }
            }
            MemorySegment system = table(arena, SYSTEM_VERSION, error);
            MemorySegment overlay = table(arena, OVERLAY_VERSION, error);
            if (system == null || overlay == null) {
                shutdown();
                return initErrorText(error.get(JAVA_INT, 0));
            }
            createOverlay = slot(overlay, OVERLAY_CREATE, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            destroyOverlay = slot(overlay, OVERLAY_DESTROY, FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
            overlayErrorName = slot(overlay, OVERLAY_ERROR_NAME, FunctionDescriptor.of(ADDRESS, JAVA_INT));
            setOverlayFlag = slot(overlay, OVERLAY_SET_FLAG, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_BOOLEAN));
            setOverlayWidth = slot(overlay, OVERLAY_SET_WIDTH, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_FLOAT));
            setOverlayCurvature = slot(overlay, OVERLAY_SET_CURVATURE, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_FLOAT));
            setOverlayTextureBounds = slot(overlay, OVERLAY_SET_TEXTURE_BOUNDS, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
            setOverlayTransformAbsolute = slot(overlay, OVERLAY_SET_TRANSFORM_ABSOLUTE,
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS));
            showOverlay = slot(overlay, OVERLAY_SHOW, FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
            setOverlayTexture = slot(overlay, OVERLAY_SET_TEXTURE, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
            getPoses = slot(system, SYSTEM_GET_POSES, FunctionDescriptor.ofVoid(JAVA_INT, JAVA_FLOAT, ADDRESS, JAVA_INT));
            pollEvent = slot(system, SYSTEM_POLL_EVENT, FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT));
            acknowledgeQuit = slot(system, SYSTEM_ACKNOWLEDGE_QUIT, FunctionDescriptor.ofVoid());
            return null;
        }
    }

    private static MemorySegment table(Arena arena, String version, MemorySegment error) throws Throwable {
        MemorySegment table = (MemorySegment) getGenericInterface.invokeExact(arena.allocateFrom("FnTable:" + version), error);
        return table.address() == 0L ? null : table.reinterpret(ADDRESS.byteSize() * 128L);
    }

    private static MethodHandle slot(MemorySegment table, int index, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(table.getAtIndex(ADDRESS, index), descriptor);
    }

    static void shutdown() throws Throwable {
        shutdownInternal.invokeExact();
    }

    private static String initErrorText(int code) throws Throwable {
        MemorySegment text = (MemorySegment) initErrorDescription.invokeExact(code);
        return text.address() == 0L ? "error " + code : text.reinterpret(1024).getString(0);
    }

    static String overlayErrorText(int code) {
        try {
            MemorySegment text = (MemorySegment) overlayErrorName.invokeExact(code);
            return text.address() == 0L ? "error " + code : text.reinterpret(256).getString(0);
        } catch (Throwable t) {
            return "error " + code;
        }
    }

    /** Creates the overlay; returns its handle, or throws with SteamVR's error. */
    static long createOverlay(String key, String name) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = arena.allocate(JAVA_LONG);
            int result = (int) createOverlay.invokeExact(arena.allocateFrom(key), arena.allocateFrom(name), handle);
            if (result != 0) {
                throw new IllegalStateException(overlayErrorText(result));
            }
            return handle.get(JAVA_LONG, 0);
        }
    }

    static int destroyOverlay(long overlay) throws Throwable {
        return (int) destroyOverlay.invokeExact(overlay);
    }

    static int setOverlayFlag(long overlay, int flag, boolean enabled) throws Throwable {
        return (int) setOverlayFlag.invokeExact(overlay, flag, enabled);
    }

    static int setOverlayWidth(long overlay, float metres) throws Throwable {
        return (int) setOverlayWidth.invokeExact(overlay, metres);
    }

    static int setOverlayCurvature(long overlay, float curvature) throws Throwable {
        return (int) setOverlayCurvature.invokeExact(overlay, curvature);
    }

    /** VRTextureBounds_t: four floats. */
    static int setOverlayTextureBounds(long overlay, MemorySegment bounds) throws Throwable {
        return (int) setOverlayTextureBounds.invokeExact(overlay, bounds);
    }

    /** HmdMatrix34_t: twelve floats, row by row. */
    static int setOverlayTransformAbsolute(long overlay, int universe, MemorySegment matrix) throws Throwable {
        return (int) setOverlayTransformAbsolute.invokeExact(overlay, universe, matrix);
    }

    static int showOverlay(long overlay) throws Throwable {
        return (int) showOverlay.invokeExact(overlay);
    }

    /** Texture_t: the texture handle (8 bytes), its type and its colour space (4 bytes each). */
    static int setOverlayTexture(long overlay, MemorySegment texture) throws Throwable {
        return (int) setOverlayTexture.invokeExact(overlay, texture);
    }

    /** Fills {@code poses} (an array of TrackedDevicePose_t) with every device's current pose. */
    static void getPoses(int universe, MemorySegment poses, int count) throws Throwable {
        getPoses.invokeExact(universe, 0f, poses, count);
    }

    static boolean pollEvent(MemorySegment event) throws Throwable {
        return (boolean) pollEvent.invokeExact(event, EVENT_SIZE);
    }

    static void acknowledgeQuit() throws Throwable {
        acknowledgeQuit.invokeExact();
    }
}
