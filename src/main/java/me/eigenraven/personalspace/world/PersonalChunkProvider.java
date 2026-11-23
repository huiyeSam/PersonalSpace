package me.eigenraven.personalspace.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.FlatLayerInfo;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import me.eigenraven.personalspace.config.Config;
import net.minecraftforge.event.terraingen.TerrainGen;


import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.config.Config;

public class PersonalChunkProvider implements IChunkGenerator {

    private PersonalWorldProvider world;
    private long seed;
    private Random random;
    private String savedBiomeName = null;
    private int savedBiomeId = -1;

    public PersonalChunkProvider(PersonalWorldProvider world, long seed) {
        this.world = world;
        this.seed = seed;
        this.random = new Random(seed);
        if (Config.debugLogging) {
            PersonalSpaceMod.LOG.info("PersonalChunkProvider created for world {}", world.getDimension(), new Throwable());
        }
    }

    @Override
    public Chunk generateChunk(int chunkX, int chunkZ) {
        Chunk chunk = new Chunk(this.world.getWorldObject(), chunkX, chunkZ);

        List<FlatLayerInfo> layers = world.getConfig().getLayers();
        int y = 0;
        int worldHeight = world.getHeight();
        for (FlatLayerInfo info : layers) {
            net.minecraft.block.state.IBlockState layerState = info.getLayerMaterial();
            Block block = layerState.getBlock();
            if (block == null || block == Blocks.AIR) {
                y += info.getLayerCount();
                continue;
            }
            for (; y < info.getMinY() + info.getLayerCount() && y < worldHeight; ++y) {
                int yChunk = y >> 4;
                ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[yChunk];
                if (ebs == null) {
                    ebs = new ExtendedBlockStorage(y & ~15, true);
                    chunk.getBlockStorageArray()[yChunk] = ebs;
                }
                int yLocal = y & 15;
                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        ebs.set(x, yLocal, z, layerState);
                    }
                }
            }
            if (y >= worldHeight) {
                break;
            }
        }

        DimensionConfig cfg = world.getConfig();

        int groundLevel = cfg.getGroundLevel() - 1;
        if (groundLevel >= 0 && groundLevel < worldHeight) {
            List<Integer> surfaceLevels = cfg.isApplyToAllSurfaceLayers() ? getExposedSurfaceLevels(layers, worldHeight)
                    : Collections.singletonList(groundLevel);

            int boundaryIntervalX = MathHelper.clamp(cfg.getBoundaryChunkIntervalX(), 0, 20);
            int boundaryIntervalZ = MathHelper.clamp(cfg.getBoundaryChunkIntervalZ(), 0, 20);
            int intervalX = boundaryIntervalX;
            int intervalZ = boundaryIntervalZ;
            if (cfg.getGapPreset() == DimensionConfig.GapPreset.ROAD) {
                // Roads surround a fixed private area. Boundary settings only control
                // borders inside that area; they must not resize the area itself.
                intervalX = cfg.getRoadAreaChunks();
                intervalZ = cfg.getRoadAreaChunks();
            }

            int gapWidth = MathHelper.clamp(cfg.getGapWidth(), 0, 5);
            if (cfg.getGapPreset() == DimensionConfig.GapPreset.ROAD && gapWidth == 0) {
                gapWidth = 1;
            }
            int periodX = intervalX + gapWidth;
            int periodZ = intervalZ + gapWidth;
            boolean isGapChunkX = gapWidth > 0 && intervalX > 0 && mod(chunkX, periodX) >= intervalX;
            boolean isGapChunkZ = gapWidth > 0 && intervalZ > 0 && mod(chunkZ, periodZ) >= intervalZ;

            if (isGapChunkX || isGapChunkZ) {
                for (int surfaceLevel : surfaceLevels) {
                    generateGapInChunk(
                            chunk,
                            chunkX,
                            chunkZ,
                            surfaceLevel,
                            cfg,
                            isGapChunkX,
                            isGapChunkZ,
                            periodX,
                            periodZ,
                            gapWidth,
                            intervalX,
                            intervalZ);
                }
            }

            // Each chunk owns its west and north edge. This makes every chunk
            // participate while ensuring shared edges are never written twice.
            // Road chunks are excluded completely: road blocks must never be
            // replaced by boundary blocks, including at road intersections.
            boolean isBoundaryX;
            boolean isBoundaryZ;
            boolean isRoadChunk = cfg.getGapPreset() == DimensionConfig.GapPreset.ROAD
                    && (isGapChunkX || isGapChunkZ);
            // In road mode, track whether this chunk is on the east/south edge of a
            // private area so we can also draw its east/south boundary edge.
            boolean isEastEdgeOfArea = false;
            boolean isSouthEdgeOfArea = false;
            if (cfg.getGapPreset() == DimensionConfig.GapPreset.ROAD) {
                // In road mode, the outermost chunks of each private area (adjacent to
                // roads) always get a boundary, regardless of the configured interval.
                int modCX = mod(chunkX, periodX);
                int modCZ = mod(chunkZ, periodZ);
                boolean isInnerChunkX = modCX < intervalX;
                boolean isInnerChunkZ = modCZ < intervalZ;
                // The boundary interval must be evaluated against the position inside
                // the private area, not the global chunk coordinate. Otherwise the
                // west/north borders only line up when the road period happens to be
                // a multiple of the interval and vanish on most areas.
                isBoundaryX = boundaryIntervalX > 0 && mod(modCX, boundaryIntervalX) == 0;
                isBoundaryZ = boundaryIntervalZ > 0 && mod(modCZ, boundaryIntervalZ) == 0;
                // Force boundary on the edge of the private area next to a road.
                // Only mark the east/south edge flag: the chunk's own west/north
                // edge must stay governed by the interval, otherwise an extra
                // line appears inside the area.
                if (isInnerChunkX && modCX == intervalX - 1 && intervalX > 0) {
                    isEastEdgeOfArea = true;
                }
                if (isInnerChunkZ && modCZ == intervalZ - 1 && intervalZ > 0) {
                    isSouthEdgeOfArea = true;
                }
                isBoundaryX &= isInnerChunkX;
                isBoundaryZ &= isInnerChunkZ;
            } else {
                isBoundaryX = boundaryIntervalX > 0 && mod(chunkX, boundaryIntervalX) == 0;
                isBoundaryZ = boundaryIntervalZ > 0 && mod(chunkZ, boundaryIntervalZ) == 0;
            }
            Block boundaryBlockA = cfg.getBoundaryBlockAResolved();
            int boundaryMetaA = cfg.getBoundaryMetaA();
            Block boundaryBlockB = cfg.getBoundaryBlockBResolved();
            int boundaryMetaB = cfg.getBoundaryMetaB();

            boolean hasA = boundaryBlockA != null && boundaryBlockA != Blocks.AIR;
            boolean hasB = boundaryBlockB != null && boundaryBlockB != Blocks.AIR;

            boolean canDrawBoundary = hasA || hasB;

            // Roads are generated first. When enabled, boundaries are written on
            // the configured surface, but never replace a road block or its stripe.
            if (cfg.isOverwriteFirstLayer() && canDrawBoundary && !isRoadChunk
                    && (isBoundaryX || isBoundaryZ || isEastEdgeOfArea || isSouthEdgeOfArea)) {
                for (int surfaceLevel : surfaceLevels) {
                    int yChunk = surfaceLevel >> 4;
                    int yLocal = surfaceLevel & 15;
                    ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[yChunk];
                    if (ebs == null) {
                        ebs = new ExtendedBlockStorage(surfaceLevel & ~15, true);
                        chunk.getBlockStorageArray()[yChunk] = ebs;
                    }

                    // West edge (localX=0) for all boundary chunks
                    for (int localZ = 0; localZ < 16; localZ++) {
                        if (isBoundaryX) {
                            int localX = 0;
                            if (!BoundaryTemplate.vertical(chunkX, localX)) {
                                continue;
                            }
                            int worldX = (chunkX << 4) + localX;
                            int worldZ = (chunkZ << 4) + localZ;

                            StripeBlock stripe = getStripeBlock(
                                    worldX,
                                    worldZ,
                                    boundaryBlockA,
                                    boundaryMetaA,
                                    boundaryBlockB,
                                    boundaryMetaB);
                            if (stripe.block != null && stripe.block != Blocks.AIR) {
                                if (ebs.get(localX, yLocal, localZ).getBlock() != Blocks.BEDROCK) {
                                    ebs.set(localX, yLocal, localZ, stripe.block.getStateFromMeta(stripe.meta));
                                }
                            }
                        }
                    }

                    // East edge (localX=15) for chunks on the east edge of a private area
                    // Note: we bypass BoundaryTemplate here because it only allows localX==0
                    if (isEastEdgeOfArea) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                            int localX = 15;
                            int worldX = (chunkX << 4) + localX;
                            int worldZ = (chunkZ << 4) + localZ;

                            StripeBlock stripe = getStripeBlock(
                                    worldX,
                                    worldZ,
                                    boundaryBlockA,
                                    boundaryMetaA,
                                    boundaryBlockB,
                                    boundaryMetaB);
                            if (stripe.block != null && stripe.block != Blocks.AIR) {
                                if (ebs.get(localX, yLocal, localZ).getBlock() != Blocks.BEDROCK) {
                                    ebs.set(localX, yLocal, localZ, stripe.block.getStateFromMeta(stripe.meta));
                                }
                            }
                        }
                    }

                    // North edge (localZ=0) for all boundary chunks
                    for (int localX = 0; localX < 16; localX++) {
                        if (isBoundaryZ) {
                            int localZ = 0;
                            if (!BoundaryTemplate.horizontal(chunkZ, localZ)) {
                                continue;
                            }
                            int worldX = (chunkX << 4) + localX;
                            int worldZ = (chunkZ << 4) + localZ;

                            StripeBlock stripe = getStripeBlock(
                                    worldX,
                                    worldZ,
                                    boundaryBlockA,
                                    boundaryMetaA,
                                    boundaryBlockB,
                                    boundaryMetaB);
                            if (stripe.block != null && stripe.block != Blocks.AIR) {
                                if (ebs.get(localX, yLocal, localZ).getBlock() != Blocks.BEDROCK) {
                                    ebs.set(localX, yLocal, localZ, stripe.block.getStateFromMeta(stripe.meta));
                                }
                            }
                        }
                    }

                    // South edge (localZ=15) for chunks on the south edge of a private area
                    // Note: we bypass BoundaryTemplate here because it only allows localZ==0
                    if (isSouthEdgeOfArea) {
                        for (int localX = 0; localX < 16; localX++) {
                            int localZ = 15;
                            int worldX = (chunkX << 4) + localX;
                            int worldZ = (chunkZ << 4) + localZ;

                            StripeBlock stripe = getStripeBlock(
                                    worldX,
                                    worldZ,
                                    boundaryBlockA,
                                    boundaryMetaA,
                                    boundaryBlockB,
                                    boundaryMetaB);
                            if (stripe.block != null && stripe.block != Blocks.AIR) {
                                if (ebs.get(localX, yLocal, localZ).getBlock() != Blocks.BEDROCK) {
                                    ebs.set(localX, yLocal, localZ, stripe.block.getStateFromMeta(stripe.meta));
                                }
                            }
                        }
                    }
                }
            }

            // --- Center block generation ---
            if (cfg.isCenterEnabled() && intervalX > 0 && intervalZ > 0) {
                Block centerBlock = cfg.getCenterBlockResolved();
                int centerMeta = cfg.getCenterMeta();
                if (centerBlock != null && centerBlock != Blocks.AIR) {
                    DimensionConfig.CenterDirection dir = cfg.getCenterDirection();
                    int dirOffX = (dir == DimensionConfig.CenterDirection.SW
                            || dir == DimensionConfig.CenterDirection.NW) ? -1 : 0;
                    int dirOffZ = (dir == DimensionConfig.CenterDirection.NE
                            || dir == DimensionConfig.CenterDirection.NW) ? -1 : 0;
                    int centerLocalX = intervalX * 8 + dirOffX;
                    int centerLocalZ = intervalZ * 8 + dirOffZ;

                    int modCX = mod(chunkX, periodX);
                    int modCZ = mod(chunkZ, periodZ);

                    if (modCX < intervalX && modCZ < intervalZ) {
                        int blockStartX = modCX * 16;
                        int blockStartZ = modCZ * 16;
                        if (centerLocalX >= blockStartX && centerLocalX < blockStartX + 16
                                && centerLocalZ >= blockStartZ
                                && centerLocalZ < blockStartZ + 16) {
                            int lx = centerLocalX - blockStartX;
                            int lz = centerLocalZ - blockStartZ;
                            for (int surfaceLevel : surfaceLevels) {
                                int yChunk = surfaceLevel >> 4;
                                int yLocal = surfaceLevel & 15;
                                ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[yChunk];
                                if (ebs == null) {
                                    ebs = new ExtendedBlockStorage(surfaceLevel & ~15, true);
                                    chunk.getBlockStorageArray()[yChunk] = ebs;
                                }
                                ebs.set(lx, yLocal, lz, centerBlock.getStateFromMeta(centerMeta));
                            }
                        }
                    }
                }
            }
        }

        if (chunkX == 0 && chunkZ == 0) {
            int platformLevel = this.world.getAverageGroundLevel();
            int yChunk = platformLevel >> 4;
            ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[yChunk];
            if (ebs == null) {
                ebs = new ExtendedBlockStorage(platformLevel & ~15, true);
                chunk.getBlockStorageArray()[yChunk] = ebs;
            }
            for (int z = 4; z < 13; z++) {
                for (int x = 4; x < 13; x++) {
                    ebs.set(x, platformLevel & 15, z, Blocks.DOUBLE_STONE_SLAB.getDefaultState());
                }
            }
        }

        if (savedBiomeId < 0 || !Objects.equals(savedBiomeName, world.getConfig().getBiomeId())) {
            savedBiomeName = world.getConfig().getBiomeId();
            savedBiomeId = world.getConfig().getRawBiomeId();
        }

        Arrays.fill(chunk.getBiomeArray(), (byte) savedBiomeId);
        // Skip generateSkylightMap: personal dimensions are always fully bright
        // via client-side gamma override, so the expensive skylight scan is wasted work.

        return chunk;
    }

    private List<Integer> getExposedSurfaceLevels(List<FlatLayerInfo> layers, int worldHeight) {
        boolean[] solid = new boolean[worldHeight];

        for (FlatLayerInfo info : layers) {
            Block block = info.getLayerMaterial().getBlock();
            if (block == null || block == Blocks.AIR) {
                continue;
            }

            int startY = MathHelper.clamp(info.getMinY(), 0, worldHeight);
            int endY = MathHelper.clamp(info.getMinY() + info.getLayerCount(), 0, worldHeight);
            for (int y = startY; y < endY; y++) {
                solid[y] = true;
            }
        }

        List<Integer> surfaceLevels = new ArrayList<>();
        // A surface layer is a non-air layer with air directly above it.
        // In air;stone*3;air, only the top stone layer is a surface layer.
        for (int y = 0; y < worldHeight; y++) {
            if (solid[y] && (y + 1 >= worldHeight || !solid[y + 1])) {
                surfaceLevels.add(y);
            }
        }
        return surfaceLevels;
    }

    private static class StripeBlock {
        final Block block;
        final int meta;
        StripeBlock(Block block, int meta) { this.block = block; this.meta = meta; }
    }

    private StripeBlock getStripeBlock(int worldX, int worldZ, Block blockA, int metaA, Block blockB, int metaB) {
        boolean useA = ((worldX + worldZ) & 1) == 0;

        if (useA) {
            if (blockA != null && blockA != Blocks.AIR) {
                return new StripeBlock(blockA, metaA);
            }
            if (blockB != null && blockB != Blocks.AIR) {
                return new StripeBlock(blockB, metaB);
            }
        } else {
            if (blockB != null && blockB != Blocks.AIR) {
                return new StripeBlock(blockB, metaB);
            }
            if (blockA != null && blockA != Blocks.AIR) {
                return new StripeBlock(blockA, metaA);
            }
        }

        return new StripeBlock(null, 0);
    }

    private int mod(int a, int b) {
        int m = a % b;
        return m < 0 ? m + b : m;
    }

    private void generateGapInChunk(Chunk chunk, int chunkX, int chunkZ, int surfaceLevel, DimensionConfig cfg,
            boolean isGapX, boolean isGapZ, int periodX, int periodZ, int gapWidth, int intervalX, int intervalZ) {
        int yChunk = surfaceLevel >> 4;
        ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[yChunk];
        if (ebs == null) {
            ebs = new ExtendedBlockStorage(surfaceLevel & ~15, true);
            chunk.getBlockStorageArray()[yChunk] = ebs;
        }

        DimensionConfig.GapPreset preset = cfg.getGapPreset();
        Block gapBlockA = cfg.getGapBlockAResolved();
        int gapMetaA = cfg.getGapMetaA();
        Block gapBlockB = cfg.getGapBlockBResolved();
        int gapMetaB = cfg.getGapMetaB();
        Block gapBlockC = cfg.getGapBlockCResolved();
        int gapMetaC = cfg.getGapMetaC();

        if (gapBlockA == null || gapBlockA == Blocks.AIR) return;

        int gapWidthBlocks = gapWidth * 16;
        int yLocal = surfaceLevel & 15;

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;

                Block block = gapBlockA;
                int meta = gapMetaA;

                if (preset == DimensionConfig.GapPreset.ROAD) {
                    boolean isIntersection = isGapX && isGapZ;
                    boolean hasStripe = gapBlockB != null && gapBlockB != Blocks.AIR;
                    if (isIntersection) {
                        // At intersection: draw corner blocks where both edges meet
                        if (hasStripe) {
                            int gapOffsetX = mod(chunkX, periodX) - intervalX;
                            int offsetX = gapOffsetX * 16 + localX;
                            int gapOffsetZ = mod(chunkZ, periodZ) - intervalZ;
                            int offsetZ = gapOffsetZ * 16 + localZ;
                            boolean onEdgeX = offsetX == 0 || offsetX == gapWidthBlocks - 1;
                            boolean onEdgeZ = offsetZ == 0 || offsetZ == gapWidthBlocks - 1;
                            if (onEdgeX && onEdgeZ) {
                                block = gapBlockB;
                                meta = gapMetaB;
                            }
                        }
                    } else {
                        if (isGapX && periodX > 0) {
                            int gapChunkOffset = mod(chunkX, periodX) - intervalX;
                            int offsetInGap = gapChunkOffset * 16 + localX;
                            StripeBlock road = getRoadBlock(
                                    offsetInGap,
                                    worldZ,
                                    gapWidthBlocks,
                                    gapBlockA,
                                    gapMetaA,
                                    gapBlockB,
                                    gapMetaB,
                                    gapBlockC,
                                    gapMetaC);
                            block = road.block;
                            meta = road.meta;
                        } else if (isGapZ && periodZ > 0) {
                            int gapChunkOffset = mod(chunkZ, periodZ) - intervalZ;
                            int offsetInGap = gapChunkOffset * 16 + localZ;
                            StripeBlock road = getRoadBlock(
                                    offsetInGap,
                                    worldX,
                                    gapWidthBlocks,
                                    gapBlockA,
                                    gapMetaA,
                                    gapBlockB,
                                    gapMetaB,
                                    gapBlockC,
                                    gapMetaC);
                            block = road.block;
                            meta = road.meta;
                        }
                    }
                } else {
                    // SOLID preset: use block A with configured meta
                    block = gapBlockA;
                    meta = gapMetaA;
                }

                if (block != null && block != Blocks.AIR) {
                    // Bedrock layers must never be replaced by roads, matching the
                    // protection boundary stripes already have.
                    if (ebs.get(localX, yLocal, localZ).getBlock() != Blocks.BEDROCK) {
                        ebs.set(localX, yLocal, localZ, block.getStateFromMeta(meta));
                    }
                }
            }
        }
    }

    private StripeBlock getRoadBlock(int offsetInGap, int alongRoad, int gapWidthBlocks, Block blockA, int metaA,
            Block blockB, int metaB, Block blockC, int metaC) {
        boolean hasStripe = blockB != null && blockB != Blocks.AIR;
        boolean hasDash = blockC != null && blockC != Blocks.AIR;

        // Edge lines (B block)
        if (hasStripe && (offsetInGap == 0 || offsetInGap == gapWidthBlocks - 1)) {
            return new StripeBlock(blockB, metaB);
        }

        // Center dashed line (C block)
        if (hasDash && gapWidthBlocks >= 4) {
            int center = gapWidthBlocks / 2;
            if ((offsetInGap == center || offsetInGap == center - 1) && mod(alongRoad + 2, 8) < 4) {
                return new StripeBlock(blockC, metaC);
            }
        }

        return new StripeBlock(blockA, metaA);
    }

    @Override
    public void populate(int chunkX, int chunkZ) {
        Biome biome = this.world.getWorldObject().getBiome(new BlockPos(chunkX * 16 + 16, 0, chunkZ * 16 + 16));
        this.random.setSeed(this.seed);
        long i1 = this.random.nextLong() / 2L * 2L + 1L;
        long j1 = this.random.nextLong() / 2L * 2L + 1L;
        this.random.setSeed((long) chunkX * i1 + (long) chunkZ * j1 ^ this.seed);
        // Do not call Biome.decorate in personal dimensions. GTCEu registers
        // custom vein, surface-stone and oil populators through this path.
        if (this.world.getConfig().isGeneratingTrees() && TerrainGen.decorate(
                world.getWorldObject(),
                random,
                new BlockPos(chunkX * 16, 0, chunkZ * 16),
                DecorateBiomeEvent.Decorate.EventType.TREE)) {
            int x = chunkX * 16 + random.nextInt(16) + 8;
            int z = chunkZ * 16 + random.nextInt(16) + 8;
            BlockPos treePos = new BlockPos(x, 0, z);
            int y = this.world.getWorldObject().getHeight(treePos).getY();
            treePos = new BlockPos(x, y, z);
            WorldGenAbstractTree worldgenabstracttree = Biome.getBiome(1).getRandomTreeFeature(random);
            worldgenabstracttree.setDecorationDefaults();
            worldgenabstracttree.generate(world.getWorldObject(), random, treePos);
        }
    }

    @Override
    public boolean generateStructures(Chunk chunk, int x, int z) {
        return false;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, BlockPos pos) {
        return Collections.emptyList();
    }

    @Override
    public BlockPos getNearestStructurePos(World world, String structureName, BlockPos pos, boolean findUnexplored) {
        return null;
    }

    @Override
    public void recreateStructures(Chunk chunk, int x, int z) {
    }

    @Override
    public boolean isInsideStructure(World world, String structureName, BlockPos pos) {
        return false;
    }
}
