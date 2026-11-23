package me.eigenraven.personalspace;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import me.eigenraven.personalspace.config.Config;

/** Small opt-in diagnostic log for reproducing GUI/configuration issues. */
public final class DebugLog {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private DebugLog() {}

    public static synchronized void initialize(File configDirectory) {
        File logFile = new File(configDirectory, "personalspace-debug.log");
        try {
            if (!configDirectory.isDirectory() && !configDirectory.mkdirs()) {
                throw new IOException("Could not create debug log directory " + configDirectory);
            }
            try (PrintWriter out = new PrintWriter(new FileWriter(logFile, false))) {
                out.println(DATE.format(new Date()) + " PersonalSpace " + Tags.VERSION + " session started");
            }
        } catch (IOException ex) {
            PersonalSpaceMod.LOG.warn("Could not reset PersonalSpace debug log", ex);
        }
    }

    public static synchronized void log(String message, Object... args) {
        // Routine GUI/configuration traces are intentionally disabled. They can
        // flood the debug file during layer validation without aiding diagnosis.
    }

    public static synchronized void logException(String message, Throwable error, Object... args) {
        write(message, error, args);
    }

    private static void write(String message, Throwable error, Object... args) {
        if (!Config.debugLogging) {
            return;
        }
        String line;
        try {
            line = String.format(message, args);
        } catch (RuntimeException ex) {
            line = message;
        }
        File configFile = Config.getSavedConfigFile();
        File parent = configFile == null ? null : configFile.getParentFile();
        if (parent == null) {
            return;
        }
        File logFile = new File(parent, "personalspace-debug.log");
        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            out.println(DATE.format(new Date()) + " " + line);
            if (error != null) {
                error.printStackTrace(out);
            }
        } catch (Exception ex) {
            PersonalSpaceMod.LOG.warn("Could not write PersonalSpace debug log", ex);
        }
    }
}
