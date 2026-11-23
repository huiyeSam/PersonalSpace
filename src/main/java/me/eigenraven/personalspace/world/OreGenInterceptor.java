package me.eigenraven.personalspace.world;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.registry.GameRegistry;

import me.eigenraven.personalspace.PersonalSpaceMod;

/**
 * Intercepts GTCEu ore vein generation in personal dimensions.
 *
 * GTCEu registers its ore veins as a single IWorldGenerator
 * (gregtech.api.worldgen.generator.WorldGeneratorImpl) through
 * GameRegistry.registerWorldGenerator(). These are called by Forge's
 * ChunkProviderServer.populate() via GameRegistry.generateWorld(), completely
 * bypassing the standard Forge events, and GTCEu has no IMC key to blacklist
 * dimensions at runtime (vein dimension filters are fixed predicates parsed
 * from config JSON).
 *
 * The working approach: reflectively remove GTCEu's generator from
 * GameRegistry.worldGenerators and register a wrapper in its place that
 * delegates to the original everywhere except personal dimensions.
 */
public class OreGenInterceptor {

    private static boolean gregtechLoaded = false;

    /**
     * Check if GTCEu is installed. Called once during mod construction.
     */
    public static void initialize() {
        gregtechLoaded = Loader.isModLoaded("gregtech");
        if (gregtechLoaded) {
            PersonalSpaceMod.LOG.info("GTCEu detected, ore vein interception will be installed at load-complete");
        } else {
            PersonalSpaceMod.LOG.info("GTCEu not detected, ore vein interception disabled");
        }
    }

    /**
     * Replace GTCEu's world generator with a wrapper that skips personal
     * dimensions. Must run after GTCEu registered its generator, i.e. in
     * FMLLoadCompleteEvent.
     */
    public static void installGeneratorOverride() {
        if (!gregtechLoaded) return;
        try {
            Field worldGeneratorsField = GameRegistry.class.getDeclaredField("worldGenerators");
            worldGeneratorsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<IWorldGenerator> worldGenerators = (Set<IWorldGenerator>) worldGeneratorsField.get(null);

            IWorldGenerator gtGenerator = null;
            for (IWorldGenerator gen : worldGenerators) {
                if (gen.getClass().getName().equals("gregtech.api.worldgen.generator.WorldGeneratorImpl")) {
                    gtGenerator = gen;
                    break;
                }
            }
            if (gtGenerator == null) {
                PersonalSpaceMod.LOG.warn("GTCEu WorldGeneratorImpl not found in GameRegistry, ore interception unavailable");
                return;
            }

            worldGenerators.remove(gtGenerator);
            // registerWorldGenerator re-sorts the list (resets sortedGeneratorList)
            // and keeps the same weight (1) GTCEu used.
            GameRegistry.registerWorldGenerator(new PersonalSpaceWorldGenFilter(gtGenerator), 1);
            PersonalSpaceMod.LOG.info("GTCEu world generator wrapped, ore veins disabled in personal dimensions");
        } catch (Exception e) {
            PersonalSpaceMod.LOG.error("Failed to intercept GTCEu world generation", e);
        }
    }

    /** Delegating wrapper that skips GTCEu generation in personal dimensions. */
    private static class PersonalSpaceWorldGenFilter implements IWorldGenerator {
        private final IWorldGenerator delegate;

        PersonalSpaceWorldGenFilter(IWorldGenerator delegate) {
            this.delegate = delegate;
        }

        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                IChunkProvider chunkProvider) {
            if (world != null && world.provider instanceof PersonalWorldProvider) {
                return;
            }
            delegate.generate(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
        }
    }

    // Fallback: standard Forge events still work for non-GTCEu mods
    @SubscribeEvent
    public void onOreGen(OreGenEvent.GenerateMinable event) {
        if (event.getWorld() != null && event.getWorld().provider instanceof PersonalWorldProvider) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onOreGenPost(OreGenEvent.Post event) {
        if (event.getWorld() != null && event.getWorld().provider instanceof PersonalWorldProvider) {
            event.setResult(Event.Result.DENY);
        }
    }
}
