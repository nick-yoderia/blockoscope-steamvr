package nzy.blockoscope.steamvr;

import java.nio.file.Files;
import java.nio.file.Path;

/** Developer logging, on when config/blockoscope-steamvr.debug exists (or -Dblockoscope_steamvr.debug=true). */
public final class StereoDebug {
    public static final boolean ENABLED = Boolean.getBoolean("blockoscope_steamvr.debug")
        || Files.exists(Path.of("config", "blockoscope-steamvr.debug"));

    private StereoDebug() {}

    public static void log(String message) {
        if (ENABLED) {
            System.out.println("[Blockoscope SteamVR] " + message);
        }
    }
}
