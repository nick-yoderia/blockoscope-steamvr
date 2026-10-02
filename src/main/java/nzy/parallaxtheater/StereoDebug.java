package nzy.parallaxtheater;

import java.nio.file.Files;
import java.nio.file.Path;

/** Developer logging, on when config/parallax-theater.debug exists (or -Dparallaxtheater.debug=true). */
public final class StereoDebug {
    public static final boolean ENABLED = Boolean.getBoolean("parallaxtheater.debug")
        || Files.exists(Path.of("config", "parallax-theater.debug"));

    private StereoDebug() {}

    public static void log(String message) {
        if (ENABLED) {
            System.out.println("[Parallax Theater] " + message);
        }
    }
}
