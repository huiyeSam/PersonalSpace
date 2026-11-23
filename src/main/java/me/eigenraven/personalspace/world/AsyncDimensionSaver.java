package me.eigenraven.personalspace.world;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.eigenraven.personalspace.PersonalSpaceMod;

/**
 * Serializes personal dimension config files on a single background thread.
 *
 * Why: saving a dimension config goes through Forge's Configuration, which re-reads
 * and rewrites the whole file on every save. WorldEvent.Save / WorldEvent.Unload fire
 * on the server thread, so each dirty personal dimension used to stall the tick loop
 * with disk I/O during autosaves.
 *
 * How: the server thread takes an immutable-enough snapshot via
 * {@link DimensionConfig#createSaveSnapshot()} and hands it here together with the
 * live config. A single daemon thread performs the actual write, which keeps writes
 * to the same file strictly ordered (later saves always win). If the write fails the
 * live config is marked dirty again so the next save event retries.
 *
 * Shutdown: {@link #flushAndWait()} must be called while the server is stopping so no
 * queued save is lost; it blocks until the queue drains or a timeout elapses.
 */
public final class AsyncDimensionSaver {

    private static ExecutorService executor;
    private static boolean shutDown = false;

    private AsyncDimensionSaver() {}

    private static ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PersonalSpace-DimensionSaver");
                t.setDaemon(true);
                return t;
            });
        }
        return executor;
    }

    /**
     * Queue an asynchronous config file write.
     *
     * @param dimId      dimension being saved (for logging)
     * @param liveConfig live config, re-marked dirty if the write fails
     * @param snapshot   snapshot taken on the server thread; must not be mutated afterwards
     * @param file       target file
     */
    public static synchronized void saveAsync(int dimId, DimensionConfig liveConfig, DimensionConfig snapshot,
            File file) {
        if (shutDown) {
            // The queue was already flushed (server is stopping); write synchronously.
            writeSync(dimId, liveConfig, snapshot, file);
            return;
        }
        executor().submit(() -> writeSync(dimId, liveConfig, snapshot, file));
    }

    /**
     * Re-enable asynchronous saving for a new server session. Called from
     * FMLServerAboutToStartEvent so integrated-server restarts in singleplayer work.
     */
    public static synchronized void reset() {
        shutDown = false;
    }

    private static void writeSync(int dimId, DimensionConfig liveConfig, DimensionConfig snapshot, File file) {
        try {
            snapshot.syncWithFile(file, true, dimId);
        } catch (Exception e) {
            PersonalSpaceMod.LOG.error("Couldn't save personal dimension data for " + dimId, e);
            // Re-mark the live config so a later save event retries the write.
            if (liveConfig != null) {
                liveConfig.markDirty();
            }
        }
    }

    /**
     * Stop accepting new tasks and wait for all queued writes to finish. Called from
     * FMLServerStoppingEvent after the final round of saves has been queued.
     */
    public static synchronized void flushAndWait() {
        shutDown = true;
        ExecutorService exec = executor;
        executor = null;
        if (exec == null) {
            return;
        }
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
                PersonalSpaceMod.LOG.error("Timed out waiting for personal dimension config saves to finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            PersonalSpaceMod.LOG.error("Interrupted while waiting for personal dimension config saves", e);
        }
    }
}
