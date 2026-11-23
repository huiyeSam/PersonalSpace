package me.eigenraven.personalspace.world;

/**
 * Shared chunk-edge layout. Each chunk owns its west and north edge, so an
 * edge shared by two chunks is written exactly once.
 */
public final class BoundaryTemplate {

    private BoundaryTemplate() {}

    public static boolean vertical(int chunkX, int localX) {
        return localX == 0;
    }

    public static boolean horizontal(int chunkZ, int localZ) {
        return localZ == 0;
    }
}
