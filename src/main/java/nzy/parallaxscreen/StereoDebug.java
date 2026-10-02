package nzy.parallaxscreen;

import java.nio.file.Files;
import java.nio.file.Path;

/** Developer logging, on when config/parallax-screen.debug exists (or -Dparallaxscreen.debug=true). */
public final class StereoDebug {
    public static final boolean ENABLED = Boolean.getBoolean("parallaxscreen.debug")
        || Files.exists(Path.of("config", "parallax-screen.debug"));

    private StereoDebug() {}

    public static void log(String message) {
        if (ENABLED) {
            System.out.println("[Parallax Screen] " + message);
        }
    }
}
