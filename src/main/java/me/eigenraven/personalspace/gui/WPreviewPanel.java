package me.eigenraven.personalspace.gui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.gen.FlatLayerInfo;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw;
import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.world.DimensionConfig;

/**
 * Preview of the world layout, rendered asynchronously.
 *
 * Threading model: the render thread only snapshots the config, resolves block sprites
 * (cheap map lookups) and uploads the finished texture. A single background thread does
 * the expensive parts: reading texture PNG files with ImageIO and computing the
 * 128x128 pixel buffer. The GPU atlas readback fallback (glGetTexImage) cannot leave
 * the render thread, so it runs once per icon when the CPU-side resolution failed.
 *
 * The worker never touches the DynamicTexture, the config object or OpenGL. Results
 * are published through a volatile field and applied on the next frame only when the
 * config hash still matches, so stale results from fast setting changes are dropped.
 */
public class WPreviewPanel extends Widget {

    private static final int TEX_SIZE = 128;

    /** Shared color cache, written by the worker thread and the render thread. */
    private static final Map<String, Integer> textureColorCache = new ConcurrentHashMap<>();
    /** Bumped whenever a new color enters the cache so open previews recompute once. */
    private static final AtomicInteger textureColorVersion = new AtomicInteger();
    /** Icons that already failed the render-thread GPU readback; never retried. */
    private static final Set<String> glFallbackFailed = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final ExecutorService PREVIEW_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PersonalSpace-PreviewRenderer");
        t.setDaemon(true);
        return t;
    });

    /** One block color to resolve: sprite + map-color fallback gathered on the render thread. */
    private static final class ColorSlot {
        final TextureAtlasSprite sprite;
        final int fallbackColor;
        /** Written by the worker thread, 0 = unresolved. */
        int color;

        ColorSlot(TextureAtlasSprite sprite, int fallbackColor) {
            this.sprite = sprite;
            this.fallbackColor = fallbackColor;
        }
    }

    /** Immutable snapshot of everything the worker needs to draw one frame of the preview. */
    private static final class PreviewParams {
        int boundaryIntervalX, boundaryIntervalZ;
        int intervalX, intervalZ;
        int gapWidth;
        boolean overwriteFirstLayer;
        DimensionConfig.GapPreset gapPreset;
        boolean centerEnabled;
        DimensionConfig.CenterDirection centerDirection;
        /** True when the top surface layer is bedrock: roads/boundaries never replace it. */
        boolean mainIsBedrock;
        ColorSlot main, boundaryA, boundaryB, gapA, gapB, gapC, center;
        final List<ColorSlot> slots = new ArrayList<>();

        void collect(ColorSlot slot) {
            if (slot != null) slots.add(slot);
        }
    }

    private static final class PreviewResult {
        final int hash;
        final int[] pixels;
        final PreviewParams params;

        PreviewResult(int hash, int[] pixels, PreviewParams params) {
            this.hash = hash;
            this.pixels = pixels;
            this.params = params;
        }
    }

    private DimensionConfig config;
    private DynamicTexture texture;
    private ResourceLocation textureLocation;
    private int[] pixelData;
    private int lastHash = 0;
    private volatile PreviewResult pendingResult = null;
    private volatile boolean taskInFlight = false;

    public WPreviewPanel(Rectangle position, DimensionConfig config) {
        this.position = position;
        this.config = config;
        this.texture = new DynamicTexture(TEX_SIZE, TEX_SIZE);
        this.pixelData = texture.getTextureData();
        this.textureLocation = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("personalspace_preview", texture);
    }

    public void setConfig(DimensionConfig config) {
        this.config = config;
        this.lastHash = 0;
    }

    @Override
    protected void drawImpl(int mouseX, int mouseY, float partialTicks) {
        int hash = computeConfigHash();
        PreviewResult done = pendingResult;
        if (done != null) {
            pendingResult = null;
            if (done.hash == hash) {
                applyResult(done);
                lastHash = hash;
            }
            // Stale results (config changed while computing) are simply dropped.
        }
        if (hash != lastHash && !taskInFlight) {
            taskInFlight = true;
            submitTask(hash);
        }

        // Draw border
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GuiDraw.drawRect(-1, -1, TEX_SIZE + 2, TEX_SIZE + 2, 0xFF000000);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Draw texture
        GL11.glColor4f(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(textureLocation);
        Tessellator tess = Tessellator.getInstance();
        tess.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        float drawSize = Math.min(position.width, position.height) - 2.0F;
        tess.getBuffer().pos(0, drawSize, 0).tex(0, 1).endVertex();
        tess.getBuffer().pos(drawSize, drawSize, 0).tex(1, 1).endVertex();
        tess.getBuffer().pos(drawSize, 0, 0).tex(1, 0).endVertex();
        tess.getBuffer().pos(0, 0, 0).tex(0, 0).endVertex();
        tess.draw();
    }

    private int computeConfigHash() {
        return Objects.hash(
                config.getBoundaryBlockA(),
                config.getBoundaryMetaA(),
                config.getBoundaryBlockB(),
                config.getBoundaryMetaB(),
                config.getBoundaryChunkIntervalX(),
                config.getBoundaryChunkIntervalZ(),
                config.isOverwriteFirstLayer(),
                config.getGapWidth(),
                config.getGapPreset(),
                config.getRoadAreaChunks(),
                config.getGapBlockA(),
                config.getGapMetaA(),
                config.getGapBlockB(),
                config.getGapMetaB(),
                config.getGapBlockC(),
                config.getGapMetaC(),
                config.isCenterEnabled(),
                config.getCenterDirection(),
                config.getCenterBlock(),
                config.getCenterMeta(),
                config.getLayersAsString(),
                textureColorVersion.get());
    }

    /** Render thread: snapshot the config and queue the expensive work. */
    private void submitTask(int hash) {
        final PreviewParams params = new PreviewParams();
        params.gapPreset = config.getGapPreset();
        // Match PersonalChunkProvider: clamp boundary intervals to 0-20
        params.boundaryIntervalX = MathHelper.clamp(config.getBoundaryChunkIntervalX(), 0, 20);
        params.boundaryIntervalZ = MathHelper.clamp(config.getBoundaryChunkIntervalZ(), 0, 20);
        params.intervalX = params.boundaryIntervalX;
        params.intervalZ = params.boundaryIntervalZ;
        params.gapWidth = MathHelper.clamp(config.getGapWidth(), 0, 5);
        params.overwriteFirstLayer = config.isOverwriteFirstLayer();
        if (params.gapPreset == DimensionConfig.GapPreset.ROAD) {
            params.intervalX = config.getRoadAreaChunks();
            params.intervalZ = config.getRoadAreaChunks();
            if (params.gapWidth == 0) params.gapWidth = 1;
        }
        params.centerEnabled = config.isCenterEnabled();
        params.centerDirection = config.getCenterDirection();

        // If the top surface layer is bedrock, generation never replaces it with
        // roads or boundaries; the preview must reflect that.
        params.mainIsBedrock = false;
        List<FlatLayerInfo> layers = config.getLayers();
        if (layers != null && !layers.isEmpty()) {
            Block topBlock = layers.get(layers.size() - 1).getLayerMaterial().getBlock();
            params.mainIsBedrock = topBlock == Blocks.BEDROCK;
        }

        params.main = topLayerSlot();
        params.boundaryA = slotFor(config.getBoundaryBlockA(), config.getBoundaryMetaA());
        params.boundaryB = slotFor(config.getBoundaryBlockB(), config.getBoundaryMetaB());
        params.gapA = slotFor(config.getGapBlockA(), config.getGapMetaA());
        params.gapB = slotFor(config.getGapBlockB(), config.getGapMetaB());
        params.gapC = slotFor(config.getGapBlockC(), config.getGapMetaC());
        params.center = params.centerEnabled ? slotFor(config.getCenterBlock(), config.getCenterMeta()) : null;
        params.collect(params.main);
        params.collect(params.boundaryA);
        params.collect(params.boundaryB);
        params.collect(params.gapA);
        params.collect(params.gapB);
        params.collect(params.gapC);
        params.collect(params.center);

        PREVIEW_EXECUTOR.submit(() -> {
            PreviewResult result;
            try {
                for (ColorSlot slot : params.slots) {
                    resolveColorCpu(slot);
                }
                result = new PreviewResult(hash, computePixels(params), params);
            } catch (Throwable t) {
                PersonalSpaceMod.LOG.error("PersonalSpace preview computation failed", t);
                // Publish a gray result so the hash is consumed and we don't retry forever.
                int[] gray = new int[TEX_SIZE * TEX_SIZE];
                Arrays.fill(gray, 0xFF808080);
                result = new PreviewResult(hash, gray, params);
            }
            // Publish the result before clearing the flag: a reader that sees the
            // result is then guaranteed to also see taskInFlight == false.
            pendingResult = result;
            taskInFlight = false;
        });
    }

    /** Render thread: apply a finished result, resolving rare GPU-only colors first. */
    private void applyResult(PreviewResult result) {
        for (ColorSlot slot : result.params.slots) {
            if (slot.color != 0 || slot.sprite == null) continue;
            String iconName = slot.sprite.getIconName();
            if (iconName == null || glFallbackFailed.contains(iconName)) continue;
            int glColor = resolveColorViaGl(slot.sprite);
            if (glColor != 0) {
                textureColorCache.put(iconName, glColor);
                textureColorVersion.incrementAndGet();
                slot.color = glColor;
            } else {
                glFallbackFailed.add(iconName);
            }
        }
        System.arraycopy(result.pixels, 0, pixelData, 0, Math.min(result.pixels.length, pixelData.length));
        texture.updateDynamicTexture();
    }

    // ---------- worker thread: pixel computation ----------

    private static int colorOr(ColorSlot slot, int fallback) {
        if (slot == null) return fallback;
        return slot.color != 0 ? slot.color : slot.fallbackColor;
    }

    private static int[] computePixels(PreviewParams p) {
        int[] pixels = new int[TEX_SIZE * TEX_SIZE];
        int mainColor = colorOr(p.main, 0xFF202020);

        if (p.intervalX <= 0 && p.intervalZ <= 0) {
            Arrays.fill(pixels, mainColor);
            return pixels;
        }

        int periodX = p.intervalX + p.gapWidth;
        int periodZ = p.intervalZ + p.gapWidth;
        int periodXBlocks = periodX * 16;
        int periodZBlocks = periodZ * 16;

        int boundaryAColor = colorOr(p.boundaryA, 0);
        int boundaryBColor = colorOr(p.boundaryB, 0);
        int gapAColor = colorOr(p.gapA, 0);
        int gapBColor = colorOr(p.gapB, 0);
        int gapCColor = colorOr(p.gapC, 0);
        int centerColorRaw = p.center != null ? colorOr(p.center, 0) : 0;
        int centerColor = centerColorRaw != 0 ? centerColorRaw : mainColor;

        // Calculate uniform scale: show ~2 periods of the larger axis, keep chunks square
        int maxPeriodBlocks = Math.max(periodXBlocks > 0 ? periodXBlocks : 16, periodZBlocks > 0 ? periodZBlocks : 16);
        float scale = (maxPeriodBlocks * 2.0f) / TEX_SIZE;
        if (scale < 1.0f) scale = 1.0f;

        for (int pz = 0; pz < TEX_SIZE; pz++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                // Supersample the whole block footprint of this pixel. With a
                // fractional scale a single sample point can skip 1-block-wide
                // features (boundary stripes, road edge lines), making them
                // vanish from the preview even though they generate in-game.
                int x0 = (int) (px * scale);
                int x1 = (int) ((px + 1) * scale);
                int z0 = (int) (pz * scale);
                int z1 = (int) ((pz + 1) * scale);
                if (x1 <= x0) x1 = x0 + 1;
                if (z1 <= z0) z1 = z0 + 1;

                long best = -1;
                for (int wz = z0; wz < z1; wz++) {
                    for (int wx = x0; wx < x1; wx++) {
                        long packed = computeBlockColor(
                                p,
                                wx,
                                wz,
                                periodX,
                                periodZ,
                                periodXBlocks,
                                periodZBlocks,
                                mainColor,
                                boundaryAColor,
                                boundaryBColor,
                                gapAColor,
                                gapBColor,
                                gapCColor,
                                centerColor);
                        // Higher priority wins; on a tie keep the first sample.
                        if (best < 0 || (packed >>> 32) > (best >>> 32)) {
                            best = packed;
                        }
                    }
                }
                pixels[pz * TEX_SIZE + px] = (int) (best & 0xFFFFFFFFL);
            }
        }
        return pixels;
    }

    // Feature priorities for supersampled pixels: road > boundary > center > ground.
    private static final int PRIO_MAIN = 0;
    private static final int PRIO_CENTER = 1;
    private static final int PRIO_BOUNDARY = 2;
    private static final int PRIO_GAP = 3;

    private static long pack(int priority, int color) {
        return ((long) priority << 32) | (color & 0xFFFFFFFFL);
    }

    private static long computeBlockColor(PreviewParams p, int worldX, int worldZ, int periodX, int periodZ,
            int periodXBlocks, int periodZBlocks, int mainColor, int bAColor, int bBColor, int gapAColor, int gapBColor,
            int gapCColor, int centerColor) {
        int intervalX = p.intervalX;
        int intervalZ = p.intervalZ;
        int gapWidth = p.gapWidth;
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        int localX = worldX & 15;
        int localZ = worldZ & 15;

        // Bedrock surface layers are never replaced by roads or boundaries in
        // generation, so the preview must not paint them either.
        boolean protectedSurface = p.mainIsBedrock;

        boolean isGapChunkX = gapWidth > 0 && intervalX > 0 && periodX > 0 && mod(chunkX, periodX) >= intervalX;
        boolean isGapChunkZ = gapWidth > 0 && intervalZ > 0 && periodZ > 0 && mod(chunkZ, periodZ) >= intervalZ;

        // Render gap/road blocks first so they are visible in the preview.
        if (!protectedSurface && (isGapChunkX || isGapChunkZ)) {
            if (p.gapPreset == DimensionConfig.GapPreset.ROAD) {
                int gapWidthBlocks = gapWidth * 16;
                boolean isIntersection = isGapChunkX && isGapChunkZ;
                if (isIntersection) {
                    int gapOffsetX = mod(chunkX, periodX) - intervalX;
                    int offsetX = gapOffsetX * 16 + localX;
                    int gapOffsetZ = mod(chunkZ, periodZ) - intervalZ;
                    int offsetZ = gapOffsetZ * 16 + localZ;
                    boolean onEdgeX = offsetX == 0 || offsetX == gapWidthBlocks - 1;
                    boolean onEdgeZ = offsetZ == 0 || offsetZ == gapWidthBlocks - 1;
                    if (onEdgeX && onEdgeZ && gapBColor != 0) {
                        return pack(PRIO_GAP, gapBColor);
                    }
                    return pack(PRIO_GAP, gapAColor != 0 ? gapAColor : mainColor);
                } else if (isGapChunkX) {
                    int gapChunkOffset = mod(chunkX, periodX) - intervalX;
                    int offsetInGap = gapChunkOffset * 16 + localX;
                    return pack(
                            PRIO_GAP,
                            computeRoadColor(offsetInGap, worldZ, gapWidthBlocks, gapAColor, gapBColor, gapCColor, mainColor));
                } else {
                    int gapChunkOffset = mod(chunkZ, periodZ) - intervalZ;
                    int offsetInGap = gapChunkOffset * 16 + localZ;
                    return pack(
                            PRIO_GAP,
                            computeRoadColor(offsetInGap, worldX, gapWidthBlocks, gapAColor, gapBColor, gapCColor, mainColor));
                }
            } else {
                // SOLID gap
                return pack(PRIO_GAP, gapAColor != 0 ? gapAColor : mainColor);
            }
        }

        // Match PersonalChunkProvider: boundaries are only generated when
        // overwriteFirstLayer is enabled and at least one boundary block is set.
        boolean canDrawBoundary = !protectedSurface && p.overwriteFirstLayer && (bAColor != 0 || bBColor != 0);

        // Every chunk owns its west and north edge. Road chunks are excluded so
        // the preview matches generation and never shows boundaries on roads.
        boolean isBoundaryX;
        boolean isBoundaryZ;
        boolean isEastEdgeOfArea = false;
        boolean isSouthEdgeOfArea = false;
        if (p.gapPreset == DimensionConfig.GapPreset.ROAD) {
            // The interval is evaluated against the position inside the private
            // area (matching PersonalChunkProvider), otherwise the west/north
            // borders only line up when the road period is a multiple of the
            // interval and vanish on most areas.
            int modCX = mod(chunkX, periodX);
            int modCZ = mod(chunkZ, periodZ);
            boolean isInnerChunkX = modCX < intervalX;
            boolean isInnerChunkZ = modCZ < intervalZ;
            isBoundaryX = canDrawBoundary && p.boundaryIntervalX > 0
                    && mod(modCX, p.boundaryIntervalX) == 0;
            isBoundaryZ = canDrawBoundary && p.boundaryIntervalZ > 0
                    && mod(modCZ, p.boundaryIntervalZ) == 0;
            // Force boundary on the edge of the private area next to a road.
            // Only mark the east/south edge flag: the chunk's own west/north
            // edge stays governed by the interval.
            if (isInnerChunkX && modCX == intervalX - 1 && intervalX > 0) {
                isEastEdgeOfArea = true;
            }
            if (isInnerChunkZ && modCZ == intervalZ - 1 && intervalZ > 0) {
                isSouthEdgeOfArea = true;
            }
            isBoundaryX &= canDrawBoundary && isInnerChunkX;
            isBoundaryZ &= canDrawBoundary && isInnerChunkZ;
        } else {
            isBoundaryX = canDrawBoundary && p.boundaryIntervalX > 0
                    && mod(chunkX, p.boundaryIntervalX) == 0;
            isBoundaryZ = canDrawBoundary && p.boundaryIntervalZ > 0
                    && mod(chunkZ, p.boundaryIntervalZ) == 0;
        }
        // Check west/north edges (localX=0 / localZ=0) and east/south edges
        // (localX=15 / localZ=15) for chunks on the edge of a private area.
        boolean onBoundary = false;
        if (isBoundaryX && localX == 0
                && me.eigenraven.personalspace.world.BoundaryTemplate.vertical(chunkX, localX)) {
            onBoundary = true;
        }
        if (isEastEdgeOfArea && localX == 15) {
            onBoundary = true;
        }
        if (isBoundaryZ && localZ == 0
                && me.eigenraven.personalspace.world.BoundaryTemplate.horizontal(chunkZ, localZ)) {
            onBoundary = true;
        }
        if (isSouthEdgeOfArea && localZ == 15) {
            onBoundary = true;
        }
        if (onBoundary) {
            boolean useA = ((worldX + worldZ) & 1) == 0;
            if (useA) {
                return pack(PRIO_BOUNDARY, bAColor != 0 ? bAColor : (bBColor != 0 ? bBColor : mainColor));
            } else {
                return pack(PRIO_BOUNDARY, bBColor != 0 ? bBColor : (bAColor != 0 ? bAColor : mainColor));
            }
        }

        // Center
        if (p.centerEnabled && intervalX > 0 && intervalZ > 0 && periodX > 0 && periodZ > 0) {
            DimensionConfig.CenterDirection dir = p.centerDirection;
            int dirOffX = (dir == DimensionConfig.CenterDirection.SW || dir == DimensionConfig.CenterDirection.NW) ? -1
                    : 0;
            int dirOffZ = (dir == DimensionConfig.CenterDirection.NE || dir == DimensionConfig.CenterDirection.NW) ? -1
                    : 0;
            int centerLocalX = intervalX * 8 + dirOffX;
            int centerLocalZ = intervalZ * 8 + dirOffZ;

            int modCX = mod(chunkX, periodX);
            int modCZ = mod(chunkZ, periodZ);

            if (modCX < intervalX && modCZ < intervalZ) {
                int areaBlockX = modCX * 16 + localX;
                int areaBlockZ = modCZ * 16 + localZ;
                // Make center marker 3x3 for visibility at any scale
                int dx = areaBlockX - centerLocalX;
                int dz = areaBlockZ - centerLocalZ;
                if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
                    return pack(PRIO_CENTER, centerColor);
                }
            }
        }

        return pack(PRIO_MAIN, mainColor);
    }

    // ---------- color resolution ----------

    /** Render thread: slot for the top surface layer, or null for air/void (dark gray). */
    private ColorSlot topLayerSlot() {
        List<FlatLayerInfo> layers = config.getLayers();
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        FlatLayerInfo topLayer = layers.get(layers.size() - 1);
        IBlockState layerState = topLayer.getLayerMaterial();
        Block block = layerState.getBlock();
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        int meta = block.getMetaFromState(layerState);
        return new ColorSlot(spriteFor(block, meta), mapColorFallback(layerState));
    }

    /** Render thread: slot for a configured block, or null when unset/air. */
    private static ColorSlot slotFor(String blockName, int meta) {
        if (blockName == null || blockName.isEmpty()) {
            return null;
        }
        Block block = DimensionConfig.blockFromString(blockName);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        IBlockState state;
        try {
            state = block.getStateFromMeta(meta);
        } catch (Exception e) {
            state = block.getDefaultState();
        }
        return new ColorSlot(spriteFor(block, meta), mapColorFallback(state));
    }

    /** Render thread: sprite lookup is a cheap model-map read. */
    private static TextureAtlasSprite spriteFor(Block block, int meta) {
        try {
            return Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes()
                    .getTexture(block.getStateFromMeta(meta));
        } catch (Exception e) {
            return null;
        }
    }

    /** Render thread: material map color as the always-available fallback. */
    private static int mapColorFallback(IBlockState state) {
        try {
            return 0xFF000000 | state.getMapColor(Minecraft.getMinecraft().world, BlockPos.ORIGIN).colorValue;
        } catch (Exception e) {
            return 0xFF808080;
        }
    }

    /** Worker thread: resolve a slot color from cache or CPU-readable texture data. */
    private static void resolveColorCpu(ColorSlot slot) {
        if (slot.sprite == null) {
            slot.color = 0;
            return;
        }
        String iconName = slot.sprite.getIconName();
        if (iconName == null || iconName.isEmpty()) {
            slot.color = 0;
            return;
        }
        Integer cached = textureColorCache.get(iconName);
        if (cached != null) {
            slot.color = cached;
            return;
        }
        int color = computeColorFromIconCpu(slot.sprite);
        if (color != 0) {
            textureColorCache.put(iconName, color);
            textureColorVersion.incrementAndGet();
        }
        slot.color = color;
    }

    /** Worker thread: PNG file read and in-memory frame data only, no OpenGL. */
    private static int computeColorFromIconCpu(TextureAtlasSprite icon) {
        try {
            String iconName = icon.getIconName();
            ResourceLocation loc = new ResourceLocation(iconName);
            ResourceLocation texPath = new ResourceLocation(
                    loc.getResourceDomain(),
                    "textures/" + loc.getResourcePath() + ".png");
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(texPath);
            BufferedImage image = ImageIO.read(resource.getInputStream());
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                int w = image.getWidth();
                int h = Math.min(image.getHeight(), w);
                int[] pixels = new int[w * h];
                image.getRGB(0, 0, w, h, pixels, 0, w);
                return computeWeightedAverageColor(pixels);
            }
        } catch (Exception ignored) {}

        try {
            if (icon.getFrameCount() > 0) {
                int[][] frameData = icon.getFrameTextureData(0);
                if (frameData != null && frameData.length > 0 && frameData[0] != null) {
                    return computeWeightedAverageColor(frameData[0]);
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }

    /** Render thread only: last-resort GPU readback for textures without a readable PNG. */
    private static int resolveColorViaGl(TextureAtlasSprite sprite) {
        try {
            int[] pixels = readSpritePixelsFromAtlas(sprite);
            if (pixels != null && pixels.length > 0) {
                return computeWeightedAverageColor(pixels);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static int[] readSpritePixelsFromAtlas(TextureAtlasSprite sprite) {
        int spriteW = sprite.getIconWidth();
        int spriteH = sprite.getIconHeight();
        int ox = sprite.getOriginX();
        int oy = sprite.getOriginY();
        if (spriteW <= 0 || spriteH <= 0) return null;

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        int atlasW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int atlasH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (atlasW <= 0 || atlasH <= 0) return null;
        if (ox + spriteW > atlasW || oy + spriteH > atlasH) return null;

        java.nio.IntBuffer buf = BufferUtils.createIntBuffer(atlasW * atlasH);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);

        int[] spritePixels = new int[spriteW * spriteH];
        for (int y = 0; y < spriteH; y++) {
            for (int x = 0; x < spriteW; x++) {
                spritePixels[y * spriteW + x] = buf.get((oy + y) * atlasW + (ox + x));
            }
        }
        return spritePixels;
    }

    private static int computeWeightedAverageColor(int[] pixels) {
        long totalR = 0, totalG = 0, totalB = 0;
        int count = 0;

        for (int pixel : pixels) {
            int a = (pixel >> 24) & 0xFF;
            if (a < 128) continue;

            totalR += (pixel >> 16) & 0xFF;
            totalG += (pixel >> 8) & 0xFF;
            totalB += pixel & 0xFF;
            count++;
        }

        if (count == 0) return 0;

        int avgR = (int) (totalR / count);
        int avgG = (int) (totalG / count);
        int avgB = (int) (totalB / count);

        return 0xFF000000 | (avgR << 16) | (avgG << 8) | avgB;
    }

    /** Worker thread: road block color matching PersonalChunkProvider.getRoadBlock. */
    private static int computeRoadColor(int offsetInGap, int alongRoad, int gapWidthBlocks,
            int gapAColor, int gapBColor, int gapCColor, int mainColor) {
        // Edge lines (B block)
        if (gapBColor != 0 && (offsetInGap == 0 || offsetInGap == gapWidthBlocks - 1)) {
            return gapBColor;
        }
        // Center dashed line (C block)
        if (gapCColor != 0 && gapWidthBlocks >= 4) {
            int center = gapWidthBlocks / 2;
            if ((offsetInGap == center || offsetInGap == center - 1) && mod(alongRoad + 2, 8) < 4) {
                return gapCColor;
            }
        }
        return gapAColor != 0 ? gapAColor : mainColor;
    }

    private static int mod(int a, int b) {
        if (b <= 0) return 0;
        int m = a % b;
        return m < 0 ? m + b : m;
    }
}
