package nzy.stereotheater;

import java.nio.file.Files;
import java.nio.file.Path;

/** Developer logging, on when config/stereo-theater.debug exists (or -Dstereotheater.debug=true). */
public final class StereoDebug {
    public static final boolean ENABLED = Boolean.getBoolean("stereotheater.debug")
        || Files.exists(Path.of("config", "stereo-theater.debug"));

    private StereoDebug() {}

    public static void log(String message) {
        if (ENABLED) {
            System.out.println("[Stereo Theater] " + message);
        }
    }
}
