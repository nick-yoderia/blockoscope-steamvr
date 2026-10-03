package nzy.parallaxscreen;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development only: while {@code config/parallax-screen.dump} exists, saves the texture handed to the SteamVR screen
 * (both eyes side by side) to {@code .parallax-screen/screen.png} once and deletes the file. Shows exactly what the mod
 * gives SteamVR, without a headset or a capturable desktop.
 */
final class TextureDump {
    private static final Path TRIGGER = Path.of("config", "parallax-screen.dump");

    private TextureDump() {}

    static void maybeDump(RenderTarget target) {
        if (!Files.exists(TRIGGER) || !(target.getColorTexture() instanceof GlTexture gl)) {
            return;
        }
        int id = gl.glId();
        int width = GL45.glGetTextureLevelParameteri(id, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL45.glGetTextureLevelParameteri(id, 0, GL11.GL_TEXTURE_HEIGHT);
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GL45.glGetTextureImage(id, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                // OpenGL's first row is the bottom one.
                int row = (height - 1 - y) * width;
                for (int x = 0; x < width; x++) {
                    int i = (row + x) * 4;
                    image.setRGB(x, y, (buffer.get(i) & 0xFF) << 16 | (buffer.get(i + 1) & 0xFF) << 8 | buffer.get(i + 2) & 0xFF);
                }
            }
            Path out = Path.of(".parallax-screen", "screen.png");
            Files.createDirectories(out.getParent());
            ImageIO.write(image, "png", out.toFile());
            Files.deleteIfExists(TRIGGER);
            System.out.println("[Parallax Screen] Saved the SteamVR screen texture (" + width + "x" + height + ", target "
                + target.width + "x" + target.height + ", pack row length was " + rowLength + ") to " + out);
        } catch (Exception e) {
            System.out.println("[Parallax Screen] Texture dump failed: " + e);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            MemoryUtil.memFree(buffer);
        }
    }
}
