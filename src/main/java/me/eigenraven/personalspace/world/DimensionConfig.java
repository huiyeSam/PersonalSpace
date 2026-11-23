package me.eigenraven.personalspace.world;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.FlatLayerInfo;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.apache.commons.lang3.tuple.MutablePair;

import com.google.common.collect.Lists;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import net.minecraftforge.fml.common.registry.GameRegistry;
import me.eigenraven.personalspace.CommonProxy;
import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.config.AllowedBlock;
import me.eigenraven.personalspace.config.AllowedBlockRules;
import me.eigenraven.personalspace.config.Config;

/**
 * Current world generation settings for a given dimension
 */
public class DimensionConfig {

    public static class ParsedBlock {

        private final String blockName;
        private final int meta;

        private ParsedBlock(String blockName, int meta) {
            this.blockName = blockName;
            this.meta = meta;
        }

        public String blockName() {
            return blockName;
        }

        public int meta() {
            return meta;
        }
    }

    public enum SkyType {

        VANILLA(null, null),
        BARNADA_C("galaxyspace.BarnardsSystem.planets.barnardaC.dimension.sky.SkyProviderBarnardaC",
                "galaxyspace.BarnardsSystem.planets.barnardaC.dimension.sky.CloudProviderBarnardaC"),
        GARDEN_OF_GLASS("vazkii.botania.client.render.world.SkyblockSkyRenderer", null),;

        public final String skyProvider, cloudProvider;
        private Boolean isLoaded = null;

        SkyType(String skyProvider, String cloudProvider) {
            this.skyProvider = skyProvider;
            this.cloudProvider = cloudProvider;
        }

        public static SkyType fromOrdinal(int ord) {
            return (ord < 0 || ord >= values().length) ? SkyType.VANILLA : values()[ord];
        }

        public boolean isLoaded() {
            if (skyProvider == null && cloudProvider == null) {
                return true;
            }
            if (isLoaded == null) {
                try {
                    Class<?> skyClass;
                    if (skyProvider != null) skyClass = Class.forName(skyProvider);
                    Class<?> cloudClass;
                    if (cloudProvider != null) cloudClass = Class.forName(cloudProvider);
                    isLoaded = Boolean.TRUE;
                } catch (ClassNotFoundException e) {
                    isLoaded = Boolean.FALSE;
                }
            }
            return isLoaded;
        }

        private Object makeClass(String name) {
            if (name == null || !isLoaded()) {
                return null;
            }
            try {
                Class<?> klass = Class.forName(name);
                return klass.newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        public Object makeSkyProvider() {
            return makeClass(skyProvider);
        }

        public Object makeCloudProvider() {
            return makeClass(cloudProvider);
        }

        public String getButtonText() {
            return this.toString().substring(0, 1);
        }

        public String getButtonTooltip() {
            return I18n.translateToLocal("gui.personalWorld.skyType." + this);
        }
    }

    public enum DaylightCycle {

        SUN,
        MOON,
        CYCLE;

        DaylightCycle() {

        }

        public static DaylightCycle fromOrdinal(int ordinal) {
            return (ordinal < 0 || ordinal >= values().length) ? DaylightCycle.CYCLE : values()[ordinal];
        }
    }

    public enum GapPreset {

        ROAD,
        SOLID;

        GapPreset() {}

        public static GapPreset fromOrdinal(int ordinal) {
            return (ordinal < 0 || ordinal >= values().length) ? GapPreset.ROAD : values()[ordinal];
        }
    }

    public enum CenterDirection {

        SE, // x+ z+ (default)
        SW, // x- z+
        NE, // x+ z-
        NW; // x- z-

        CenterDirection() {}

        public static CenterDirection fromOrdinal(int ordinal) {
            return (ordinal < 0 || ordinal >= values().length) ? CenterDirection.SE : values()[ordinal];
        }
    }

    private String saveDirOverride = "";
    private int skyColor = 0xc0d8ff;
    private float starBrightness = 1.0F;
    private boolean weatherEnabled = false;
    private DaylightCycle daylightCycle = DaylightCycle.CYCLE;
    private boolean cloudsEnabled = true;
    private SkyType skyType = SkyType.VANILLA;
    private boolean generatingVegetation = false;
    private boolean generatingTrees = false;
    private boolean allowGenerationChanges = false;
    private String biomeId = "Plains";
    private ArrayList<FlatLayerInfo> layers = Lists.newArrayList();

    private long worldTime = 0;
    private boolean raining = false;
    private int rainTime = 0;
    private boolean thundering = false;
    private int thunderTime = 0;
    private boolean timeDataPersisted = false;

    private String boundaryBlockA = "minecraft:concrete";
    private int boundaryMetaA = 4;
    private String boundaryBlockB = "minecraft:concrete";
    private int boundaryMetaB = 15;
    private int boundaryChunkIntervalX = 1;
    private int boundaryChunkIntervalZ = 1;
    private boolean overwriteFirstLayer = false;

    private int gapWidth = 0;
    // Roads are opt-in; the default keeps the configured X/Z intervals independent.
    private GapPreset gapPreset = GapPreset.SOLID;
    private int roadAreaChunks = Config.roadAreaChunks;
    private String gapBlockA = "minecraft:concrete";
    private int gapMetaA = 15;
    private String gapBlockB = "minecraft:concrete";
    private int gapMetaB = 0;
    private String gapBlockC = "minecraft:concrete";
    private int gapMetaC = 0;
    private boolean applyToAllSurfaceLayers = false;

    private boolean centerEnabled = false;
    private CenterDirection centerDirection = CenterDirection.SE;
    private String centerBlock = "minecraft:concrete";
    private int centerMeta = 14;

    private boolean needsSaving = true;
    private int[] teleportPoint = new int[0];

    public net.minecraft.util.math.BlockPos getTeleportPoint() {
        return teleportPoint.length == 3
                ? new net.minecraft.util.math.BlockPos(teleportPoint[0], teleportPoint[1], teleportPoint[2]) : null;
    }

    public void setTeleportPoint(net.minecraft.util.math.BlockPos pos) {
        teleportPoint = pos == null ? new int[0] : new int[] {pos.getX(), pos.getY(), pos.getZ()};
        needsSaving = true;
    }

    // Forge 1.12.2 FlatLayerInfo must always contain a real layer, including void worlds.
    public static final String PRESET_UW_VOID = "minecraft:air";
    public static final String PRESET_UW_GARDEN = "minecraft:bedrock;minecraft:dirt*3;minecraft:grass";
    public static final String PRESET_UW_MINING = "minecraft:bedrock*4;minecraft:stone*58;minecraft:dirt;minecraft:grass";
    public static final int MAX_ROAD_AREA_CHUNKS = 20;
    public static final Pattern PRESET_VALIDATION_PATTERN = Pattern
            .compile("^([^:\\*;]+:[^:\\*;]+(:\\d+)?(\\*\\d+)?;)*([^:\\*;]+:[^:\\*;]+(:\\d+)?(\\*\\d+)?)?$");

    public DimensionConfig() {}

    public void writeToPacket(MCDataOutput pkt) {
        DimensionConfigPackets.write(this, pkt);
    }

    public void readFromPacket(MCDataInput pkt) {
        DimensionConfigPackets.readInto(this, pkt);
    }

    /**
     * @return Loaded dimension ID
     */
    public int syncWithFile(File file, boolean write, int dimId) {
        final String VISUAL = "visual";
        final String WORLDGEN = "worldgen";
        final String BOUNDARY = "boundary";
        final String GAP = "gap";
        Configuration cfg = new Configuration(file);
        Property cur;

        cur = cfg.get(VISUAL, "skyColor", skyColor,
                "天空颜色（RGB 整数）。\nSky color (RGB integer).", 0, 0xFFFFFF);
        if (write) {
            cur.set(skyColor);
        } else {
            setSkyColor(cur.getInt());
        }

        cur = cfg.get(VISUAL, "starBrightness", starBrightness,
                "固定昼夜模式的星空亮度。\nStar brightness in fixed daylight modes.", 0.0F, 1.0F);
        if (write) {
            cur.set(starBrightness);
        } else {
            setStarBrightness((float) cur.getDouble());
        }

        cur = cfg.get(WORLDGEN, "biomeId", getBiomeId(),
                "新区块的生物群系标识。\nBiome identifier for newly generated chunks.");
        if (write) {
            cur.set(getBiomeId());
        } else {
            setBiomeId(cur.getString());
        }

        cur = cfg.get(VISUAL, "weatherEnabled", weatherEnabled,
                "启用天气。\nEnable weather.");
        if (write) {
            cur.set(weatherEnabled);
        } else {
            setWeatherEnabled(cur.getBoolean());
        }

        cur = cfg.get(VISUAL, "daylightCycle", daylightCycle.ordinal(),
                "0 恒昼、1 恒夜、2 自然循环。\n0 fixed day, 1 fixed night, 2 natural cycle.");
        if (write) {
            cur.set(daylightCycle.ordinal());
        } else {
            setDaylightCycle(DaylightCycle.fromOrdinal(cur.getInt()));
        }

        // handle old nightTime config
        if (cfg.hasKey(VISUAL, "nightTime")) {
            if (write) {
                cfg.getCategory(VISUAL).remove("nightTime");
            } else {
                boolean isNight = cfg.getCategory(VISUAL).get("nightTime").getBoolean();
                setDaylightCycle(isNight ? DaylightCycle.MOON : DaylightCycle.SUN);
                needsSaving = true;
            }
        }

        cur = cfg.get(VISUAL, "cloudsEnabled", cloudsEnabled,
                "显示云。\nShow clouds.");
        if (write) {
            cur.set(cloudsEnabled);
        } else {
            setCloudsEnabled(cur.getBoolean());
        }

        cur = cfg.get(VISUAL, "skyType", skyType.ordinal(),
                "0 原版、1 巴纳德 C、2 水晶花园。\n0 Vanilla, 1 Barnard C, 2 Garden of Glass.");
        if (write) {
            cur.set(skyType.ordinal());
        } else {
            setSkyType(SkyType.fromOrdinal(cur.getInt()));
        }

        cur = cfg.get(WORLDGEN, "generatingTrees", generatingTrees,
                "生成树木。\nGenerate trees.");
        if (write) {
            cur.set(generatingTrees);
        } else {
            setGeneratingTrees(cur.getBoolean());
        }

        cur = cfg.get(WORLDGEN, "generatingVegetation", generatingVegetation,
                "生成植被。\nGenerate vegetation.");
        if (write) {
            cur.set(generatingVegetation);
        } else {
            setGeneratingVegetation(cur.getBoolean());
        }

        cur = cfg.get(WORLDGEN, "allowGenerationChanges", allowGenerationChanges,
                "一次性解锁世界生成设置。\nOne-time permission to edit world generation settings.");
        if (write) {
            cur.set(allowGenerationChanges);
        } else {
            setAllowGenerationChanges(cur.getBoolean());
        }

        cur = cfg.get(WORLDGEN, "layers", getLayersAsString(),
                "从下到上的地层定义。\nTerrain layers from bottom to top.");
        if (write) {
            cur.set(getLayersAsString());
        } else {
            setLayers(cur.getString());
        }

        boolean applyToAllSurfaceLayersDefault = isApplyToAllSurfaceLayers();
        if (!write && cfg.hasKey(GAP, "onExposedSurfaces")) {
            applyToAllSurfaceLayersDefault = cfg.getCategory(GAP).get("onExposedSurfaces").getBoolean();
            needsSaving = true;
        }
        cur = cfg.get(WORLDGEN, "applyToAllSurfaceLayers", applyToAllSurfaceLayersDefault,
                "将边界、隔段与中心标记应用到所有露天表面层，而非仅地面。\nApply boundaries, gaps and centers to all exposed surface layers instead of ground level only.");
        if (write) {
            cur.set(isApplyToAllSurfaceLayers());
        } else {
            setApplyToAllSurfaceLayers(cur.getBoolean());
        }

        cur = cfg.get(BOUNDARY, "blockA", getBoundaryBlockA(),
                "边界方块 A 的注册名。\nBoundary block A registry name.");
        if (write) {
            cur.set(getBoundaryBlockA());
        } else {
            setBoundaryBlockA(cur.getString());
        }

        cur = cfg.get(BOUNDARY, "metaA", getBoundaryMetaA(),
                "边界方块 A 的元数据。\nBoundary block A metadata.", 0, 15);
        if (write) {
            cur.set(getBoundaryMetaA());
        } else {
            setBoundaryMetaA(cur.getInt());
        }

        cur = cfg.get(BOUNDARY, "blockB", getBoundaryBlockB(),
                "边界方块 B 的注册名。\nBoundary block B registry name.");
        if (write) {
            cur.set(getBoundaryBlockB());
        } else {
            setBoundaryBlockB(cur.getString());
        }

        cur = cfg.get(BOUNDARY, "metaB", getBoundaryMetaB(),
                "边界方块 B 的元数据。\nBoundary block B metadata.", 0, 15);
        if (write) {
            cur.set(getBoundaryMetaB());
        } else {
            setBoundaryMetaB(cur.getInt());
        }

        cur = cfg.get(BOUNDARY, "chunkIntervalX", getBoundaryChunkIntervalX(),
                "X 方向边界间隔（区块）。\nBoundary interval along X (chunks).", 0, 20);
        if (write) {
            cur.set(getBoundaryChunkIntervalX());
        } else {
            setBoundaryChunkIntervalX(cur.getInt());
        }

        cur = cfg.get(BOUNDARY, "chunkIntervalZ", getBoundaryChunkIntervalZ(),
                "Z 方向边界间隔（区块）。\nBoundary interval along Z (chunks).", 0, 20);
        if (write) {
            cur.set(getBoundaryChunkIntervalZ());
        } else {
            setBoundaryChunkIntervalZ(cur.getInt());
        }

        cur = cfg.get(BOUNDARY, "overwriteFirstLayer", isOverwriteFirstLayer(),
                "边界方块覆盖首个配置的表面层。\nBoundaries replace blocks in the first configured surface layer.");
        if (write) {
            cur.set(isOverwriteFirstLayer());
        } else {
            setOverwriteFirstLayer(cur.getBoolean());
        }

        cur = cfg.get(GAP, "width", getGapWidth(),
                "隔段宽度（区块）。\nGap width (chunks).", 0, 5);
        if (write) {
            cur.set(getGapWidth());
        } else {
            setGapWidth(cur.getInt());
        }

        cur = cfg.get(GAP, "preset", getGapPreset().ordinal(),
                "隔段样式：0 马路，1 纯方块。\nGap style: 0 road, 1 solid blocks.");
        if (write) {
            cur.set(getGapPreset().ordinal());
        } else {
            setGapPreset(GapPreset.fromOrdinal(cur.getInt()));
        }

        cur = cfg.get(GAP, "roadAreaChunks", getRoadAreaChunks(),
                "边界间隔为 0 时的马路区域边长（区块）。\nRoad area width (chunks) when boundary interval is zero.", 1, MAX_ROAD_AREA_CHUNKS);
        if (write) {
            cur.set(getRoadAreaChunks());
        } else {
            setRoadAreaChunks(cur.getInt());
        }

        cur = cfg.get(GAP, "blockA", getGapBlockA(),
                "隔段方块 A 的注册名。\nGap block A registry name.");
        if (write) {
            cur.set(getGapBlockA());
        } else {
            setGapBlockA(cur.getString());
        }

        cur = cfg.get(GAP, "metaA", getGapMetaA(),
                "隔段方块 A 的元数据。\nGap block A metadata.", 0, 15);
        if (write) {
            cur.set(getGapMetaA());
        } else {
            setGapMetaA(cur.getInt());
        }

        cur = cfg.get(GAP, "blockB", getGapBlockB(),
                "隔段方块 B 的注册名。\nGap block B registry name.");
        if (write) {
            cur.set(getGapBlockB());
        } else {
            setGapBlockB(cur.getString());
        }

        cur = cfg.get(GAP, "metaB", getGapMetaB(),
                "隔段方块 B 的元数据。\nGap block B metadata.", 0, 15);
        if (write) {
            cur.set(getGapMetaB());
        } else {
            setGapMetaB(cur.getInt());
        }

        cur = cfg.get(GAP, "blockC", getGapBlockC(),
                "隔段方块 C 的注册名。\nGap block C registry name.");
        if (write) {
            cur.set(getGapBlockC());
        } else {
            setGapBlockC(cur.getString());
        }

        cur = cfg.get(GAP, "metaC", getGapMetaC(),
                "隔段方块 C 的元数据。\nGap block C metadata.", 0, 15);
        if (write) {
            cur.set(getGapMetaC());
        } else {
            setGapMetaC(cur.getInt());
        }

        if (write && cfg.hasKey(GAP, "onExposedSurfaces")) {
            cfg.getCategory(GAP).remove("onExposedSurfaces");
        }

        final String CENTER = "center";

        cur = cfg.get(CENTER, "enabled", isCenterEnabled(),
                "生成中心标记。\nGenerate center markers.");
        if (write) {
            cur.set(isCenterEnabled());
        } else {
            setCenterEnabled(cur.getBoolean());
        }

        cur = cfg.get(CENTER, "direction", getCenterDirection().ordinal(),
                "中心标记方向：0 东南、1 西南、2 东北、3 西北。\nCenter marker direction: 0 SE, 1 SW, 2 NE, 3 NW.");
        if (write) {
            cur.set(getCenterDirection().ordinal());
        } else {
            setCenterDirection(CenterDirection.fromOrdinal(cur.getInt()));
        }

        cur = cfg.get(CENTER, "block", getCenterBlock(),
                "中心标记方块的注册名。\nCenter marker block registry name.");
        if (write) {
            cur.set(getCenterBlock());
        } else {
            setCenterBlock(cur.getString());
        }

        cur = cfg.get(CENTER, "meta", getCenterMeta(),
                "中心标记方块的元数据。\nCenter marker block metadata.", 0, 15);
        if (write) {
            cur.set(getCenterMeta());
        } else {
            setCenterMeta(cur.getInt());
        }

        final String TIME = "time";
        final boolean hasTimeData = !write && cfg.hasCategory(TIME);

        if (write && timeDataPersisted) {
            cur = cfg.get(TIME, "worldTime", String.valueOf(worldTime),
                        "保存的世界时间（刻）。\nSaved world time (ticks).");
            cur.set(String.valueOf(worldTime));
            cur = cfg.get(TIME, "raining", raining,
                        "保存的降雨状态。\nSaved rain state.");
            cur.set(raining);
            cur = cfg.get(TIME, "rainTime", rainTime,
                        "保存的降雨计时器（刻）。\nSaved rain timer (ticks).");
            cur.set(rainTime);
            cur = cfg.get(TIME, "thundering", thundering,
                        "保存的雷暴状态。\nSaved thunder state.");
            cur.set(thundering);
            cur = cfg.get(TIME, "thunderTime", thunderTime,
                        "保存的雷暴计时器（刻）。\nSaved thunder timer (ticks).");
            cur.set(thunderTime);
        } else if (!write && hasTimeData) {
            try {
                cur = cfg.get(TIME, "worldTime", String.valueOf(worldTime),
                        "保存的世界时间（刻）。\nSaved world time (ticks).");
                worldTime = Long.parseLong(cur.getString());
                cur = cfg.get(TIME, "raining", raining,
                        "保存的降雨状态。\nSaved rain state.");
                raining = cur.getBoolean();
                cur = cfg.get(TIME, "rainTime", rainTime,
                        "保存的降雨计时器（刻）。\nSaved rain timer (ticks).");
                rainTime = cur.getInt();
                cur = cfg.get(TIME, "thundering", thundering,
                        "保存的雷暴状态。\nSaved thunder state.");
                thundering = cur.getBoolean();
                cur = cfg.get(TIME, "thunderTime", thunderTime,
                        "保存的雷暴计时器（刻）。\nSaved thunder timer (ticks).");
                thunderTime = cur.getInt();
                timeDataPersisted = true;
            } catch (NumberFormatException e) {
                PersonalSpaceMod.LOG.warn("Invalid time data in config, will initialize from world", e);
                resetTimeFields();
            }
        }

        cur = cfg.get("access", "teleportPoint", teleportPoint,
                "传送点的 X、Y、Z 坐标。\nTeleport point X, Y and Z coordinates.");
        if (write) cur.set(teleportPoint);
        else teleportPoint = cur.getIntList();

        cur = cfg.get(WORLDGEN, "dimId", dimId,
                "私人维度 ID，由游戏管理。\nPersonal dimension ID managed by the game.");
        if (write) {
            cur.set(dimId);
        } else {
            dimId = cur.getInt();
        }

        if (write) {
            cfg.save();
            needsSaving = false;
        }
        return dimId;
    }

    public static DimensionConfig fromPacket(MCDataInput pkt) {
        return DimensionConfigPackets.fromPacket(pkt);
    }

    String getSaveDirOverrideForPacket() {
        return saveDirOverride == null ? "" : saveDirOverride;
    }

    void setLayersFromPacket(ArrayList<FlatLayerInfo> layers) {
        this.layers = layers;
    }

    void setNeedsSavingForPacket(boolean needsSaving) {
        this.needsSaving = needsSaving;
    }

    public boolean copyFrom(DimensionConfig source, boolean copySaveInfo, boolean copyVisualInfo,
            boolean copyGenerationInfo) {
        this.needsSaving = false;
        if (copySaveInfo) {
            this.saveDirOverride = source.saveDirOverride;
        }
        if (copyVisualInfo) {
            this.setSkyColor(source.getSkyColor());
            this.setStarBrightness(source.getStarBrightness());
            this.setDaylightCycle(source.getDaylightCycle());
            this.setCloudsEnabled(source.isCloudsEnabled());
            this.setSkyType(source.getSkyType());
            this.setWeatherEnabled(source.isWeatherEnabled());
        }
        if (copyGenerationInfo) {
            this.setAllowGenerationChanges(source.getAllowGenerationChanges());
            this.setBiomeId(source.getBiomeId());
            this.setGeneratingTrees(source.isGeneratingTrees());
            this.setGeneratingVegetation(source.isGeneratingVegetation());
            this.layers = source.layers;

            this.setBoundaryBlockA(source.getBoundaryBlockA());
            this.setBoundaryMetaA(source.getBoundaryMetaA());
            this.setBoundaryBlockB(source.getBoundaryBlockB());
            this.setBoundaryMetaB(source.getBoundaryMetaB());
            this.setBoundaryChunkIntervalX(source.getBoundaryChunkIntervalX());
            this.setBoundaryChunkIntervalZ(source.getBoundaryChunkIntervalZ());
            this.setOverwriteFirstLayer(source.isOverwriteFirstLayer());

            this.setGapWidth(source.getGapWidth());
            this.setGapPreset(source.getGapPreset());
            this.setRoadAreaChunks(source.getRoadAreaChunks());
            this.setGapBlockA(source.getGapBlockA());
            this.setGapMetaA(source.getGapMetaA());
            this.setGapBlockB(source.getGapBlockB());
            this.setGapMetaB(source.getGapMetaB());
            this.setGapBlockC(source.getGapBlockC());
            this.setGapMetaC(source.getGapMetaC());
            this.setApplyToAllSurfaceLayers(source.isApplyToAllSurfaceLayers());

            this.setCenterEnabled(source.isCenterEnabled());
            this.setCenterDirection(source.getCenterDirection());
            this.setCenterBlock(source.getCenterBlock());
            this.setCenterMeta(source.getCenterMeta());

            this.needsSaving = true;
        }
        boolean modified = this.needsSaving;
        this.needsSaving = true;
        return modified;
    }

    /**
     * Applies boundary, gap, surface-layer and center-marker settings without changing
     * the one-time world-generation permission. These options are safe to edit on an
     * already-created personal dimension and are used by the More Settings page.
     */
    public boolean copyExtendedSettingsFrom(DimensionConfig source) {
        if (source == null) {
            return false;
        }
        boolean changed = !this.getBoundaryBlockA().equals(source.getBoundaryBlockA())
                || this.getBoundaryMetaA() != source.getBoundaryMetaA()
                || !this.getBoundaryBlockB().equals(source.getBoundaryBlockB())
                || this.getBoundaryMetaB() != source.getBoundaryMetaB()
                || this.getBoundaryChunkIntervalX() != source.getBoundaryChunkIntervalX()
                || this.getBoundaryChunkIntervalZ() != source.getBoundaryChunkIntervalZ()
                || this.isOverwriteFirstLayer() != source.isOverwriteFirstLayer()
                || this.getGapWidth() != source.getGapWidth()
                || this.getGapPreset() != source.getGapPreset()
                || this.getRoadAreaChunks() != source.getRoadAreaChunks()
                || !this.getGapBlockA().equals(source.getGapBlockA())
                || this.getGapMetaA() != source.getGapMetaA()
                || !this.getGapBlockB().equals(source.getGapBlockB())
                || this.getGapMetaB() != source.getGapMetaB()
                || !this.getGapBlockC().equals(source.getGapBlockC())
                || this.getGapMetaC() != source.getGapMetaC()
                || this.isApplyToAllSurfaceLayers() != source.isApplyToAllSurfaceLayers()
                || this.isCenterEnabled() != source.isCenterEnabled()
                || this.getCenterDirection() != source.getCenterDirection()
                || !this.getCenterBlock().equals(source.getCenterBlock())
                || this.getCenterMeta() != source.getCenterMeta();

        this.setBoundaryBlockA(source.getBoundaryBlockA());
        this.setBoundaryMetaA(source.getBoundaryMetaA());
        this.setBoundaryBlockB(source.getBoundaryBlockB());
        this.setBoundaryMetaB(source.getBoundaryMetaB());
        this.setBoundaryChunkIntervalX(source.getBoundaryChunkIntervalX());
        this.setBoundaryChunkIntervalZ(source.getBoundaryChunkIntervalZ());
        this.setOverwriteFirstLayer(source.isOverwriteFirstLayer());
        this.setGapWidth(source.getGapWidth());
        this.setGapPreset(source.getGapPreset());
        this.setRoadAreaChunks(source.getRoadAreaChunks());
        this.setGapBlockA(source.getGapBlockA());
        this.setGapMetaA(source.getGapMetaA());
        this.setGapBlockB(source.getGapBlockB());
        this.setGapMetaB(source.getGapMetaB());
        this.setGapBlockC(source.getGapBlockC());
        this.setGapMetaC(source.getGapMetaC());
        this.setApplyToAllSurfaceLayers(source.isApplyToAllSurfaceLayers());
        this.setCenterEnabled(source.isCenterEnabled());
        this.setCenterDirection(source.getCenterDirection());
        this.setCenterBlock(source.getCenterBlock());
        this.setCenterMeta(source.getCenterMeta());
        return changed;
    }
    /**
     * Copies every field that {@link #syncWithFile(File, boolean, int)} persists, so the
     * async save worker can write the file without touching the live config. Call on the
     * server thread; the returned snapshot must be treated as immutable afterwards. The
     * layers list is structurally copied; its FlatLayerInfo elements are never mutated in
     * place by this mod, only replaced as a whole.
     */
    public DimensionConfig createSaveSnapshot() {
        DimensionConfig copy = new DimensionConfig();
        copy.saveDirOverride = this.saveDirOverride;
        copy.skyColor = this.skyColor;
        copy.starBrightness = this.starBrightness;
        copy.weatherEnabled = this.weatherEnabled;
        copy.daylightCycle = this.daylightCycle;
        copy.cloudsEnabled = this.cloudsEnabled;
        copy.skyType = this.skyType;
        copy.generatingVegetation = this.generatingVegetation;
        copy.generatingTrees = this.generatingTrees;
        copy.allowGenerationChanges = this.allowGenerationChanges;
        copy.biomeId = this.biomeId;
        copy.layers = new ArrayList<>(this.layers);
        copy.worldTime = this.worldTime;
        copy.raining = this.raining;
        copy.rainTime = this.rainTime;
        copy.thundering = this.thundering;
        copy.thunderTime = this.thunderTime;
        copy.timeDataPersisted = this.timeDataPersisted;
        copy.boundaryBlockA = this.boundaryBlockA;
        copy.boundaryMetaA = this.boundaryMetaA;
        copy.boundaryBlockB = this.boundaryBlockB;
        copy.boundaryMetaB = this.boundaryMetaB;
        copy.boundaryChunkIntervalX = this.boundaryChunkIntervalX;
        copy.boundaryChunkIntervalZ = this.boundaryChunkIntervalZ;
        copy.overwriteFirstLayer = this.overwriteFirstLayer;
        copy.gapWidth = this.gapWidth;
        copy.gapPreset = this.gapPreset;
        copy.roadAreaChunks = this.roadAreaChunks;
        copy.gapBlockA = this.gapBlockA;
        copy.gapMetaA = this.gapMetaA;
        copy.gapBlockB = this.gapBlockB;
        copy.gapMetaB = this.gapMetaB;
        copy.gapBlockC = this.gapBlockC;
        copy.gapMetaC = this.gapMetaC;
        copy.applyToAllSurfaceLayers = this.applyToAllSurfaceLayers;
        copy.centerEnabled = this.centerEnabled;
        copy.centerDirection = this.centerDirection;
        copy.centerBlock = this.centerBlock;
        copy.centerMeta = this.centerMeta;
        copy.teleportPoint = this.teleportPoint.clone();
        copy.needsSaving = false;
        return copy;
    }

    /**
     * Clears the dirty flag once an async save of this config has been queued. Any setter
     * called afterwards re-marks the config dirty, so changes made while the write is in
     * flight are picked up by the next save event.
     */
    public void clearNeedsSavingForAsyncSave() {
        this.needsSaving = false;
    }

    @SuppressWarnings("unchecked")
    public void registerWithDimensionManager(int dimId, boolean isClient) {
        if (!DimensionManager.isDimensionRegistered(dimId)) {
            // Work around bad thermos logic
            if (PersonalSpaceMod.isInThermos()) {
                try {
                    Class<?> bukkitWorldEnv = Class.forName("org.bukkit.World$Environment");
                    Field lookupField = bukkitWorldEnv.getDeclaredField("lookup");
                    lookupField.setAccessible(true);
                    Map<Integer, ?> lookup = (Map<Integer, ?>) lookupField.get(null);
                    if (lookup.remove(dimId) != null) {
                        PersonalSpaceMod.LOG
                                .info("Removed bad thermos environment lookup entry for dimension {}", dimId);
                    }
                } catch (Exception e) {
                    PersonalSpaceMod.LOG.error("Couldn't adjust thermos environment lookup table", e);
                }
            }
            DimensionManager.registerDimension(dimId, getDimensionTypeForProvider());
            if (Config.debugLogging) {

                PersonalSpaceMod.LOG
                        .info("DimensionConfig registered for dim {}, client {}", dimId, isClient, new Throwable());
            }
        }
        synchronized (CommonProxy.getDimensionConfigObjects(isClient)) {
            if (!CommonProxy.getDimensionConfigObjects(isClient).containsKey(dimId)) {
                CommonProxy.getDimensionConfigObjects(isClient).put(dimId, this);
            } else {
                CommonProxy.getDimensionConfigObjects(isClient).get(dimId).copyFrom(this, true, true, true);
            }
        }
        // Provider construction reads the config table, so initialize only after publishing this config.
        if (!isClient && DimensionManager.getWorld(dimId) == null) {
            DimensionManager.initDimension(dimId);
        }
    }

    public static int nextFreeDimId() {
        AtomicInteger nextIdV = new AtomicInteger(Config.firstDimensionId);
        CommonProxy.getDimensionConfigObjects(false).forEachKey(id -> {
            if (nextIdV.get() <= id) {
                nextIdV.set(id + 1);
            }
            return true;
        });
        int nextId = nextIdV.get();
        File saveRoot = DimensionManager.getCurrentSaveRootDirectory();
        while (nextId < Integer.MAX_VALUE - 1) {
            boolean registered = DimensionManager.isDimensionRegistered(nextId);
            boolean savedDirectoryExists = saveRoot != null
                    && new File(saveRoot, String.format("PERSONAL_DIM_%d", nextId)).exists();
            if (!registered && !savedDirectoryExists) break;
            ++nextId;
        }
        if (nextId >= Integer.MAX_VALUE - 1 || DimensionManager.isDimensionRegistered(nextId)) {
            throw new IllegalStateException("No unused personal dimension id is available");
        }
        return nextId;
    }

    public static DimensionConfig getForDimension(int dimId, boolean isClient) {
        synchronized (CommonProxy.getDimensionConfigObjects(isClient)) {
            return CommonProxy.getDimensionConfigObjects(isClient).get(dimId);
        }
    }

    public void setSaveDirOverride(String override) {
        if (override == null) {
            override = "";
        }
        this.saveDirOverride = override;
    }

    public String getSaveDir(int dimId) {
        return (saveDirOverride != null && !saveDirOverride.isEmpty()) ? saveDirOverride
                : String.format("PERSONAL_DIM_%d", dimId);
    }

    public float getStarBrightness() {
        return starBrightness;
    }

    public void setStarBrightness(float starBrightness) {
        if (starBrightness != this.starBrightness) {
            this.needsSaving = true;
            this.starBrightness = MathHelper.clamp(starBrightness, 0.0F, 1.0F);
        }
    }

    public int getSkyColor() {
        return skyColor;
    }

    public void setSkyColor(int skyColor) {
        if (skyColor != this.skyColor) {
            this.needsSaving = true;
            this.skyColor = MathHelper.clamp(skyColor, 0, 0xFFFFFF);
        }
    }

    public boolean isWeatherEnabled() {
        return weatherEnabled;
    }

    public void setWeatherEnabled(boolean weatherEnabled) {
        if (this.weatherEnabled != weatherEnabled) {
            this.needsSaving = true;
            this.weatherEnabled = weatherEnabled;
        }
    }

    public DaylightCycle getDaylightCycle() {
        return daylightCycle;
    }

    public void setDaylightCycle(DaylightCycle cycle) {
        if (this.daylightCycle != cycle) {
            this.needsSaving = true;
            this.daylightCycle = cycle;
        }
    }

    public boolean isNightTime() {
        return daylightCycle == DaylightCycle.MOON;
    }

    public boolean isCloudsEnabled() {
        return cloudsEnabled;
    }

    public void setCloudsEnabled(boolean cloudsEnabled) {
        if (this.cloudsEnabled != cloudsEnabled) {
            this.needsSaving = true;
            this.cloudsEnabled = cloudsEnabled;
        }
    }

    public SkyType getSkyType() {
        return skyType;
    }

    public void setSkyType(SkyType skyType) {
        if (this.skyType != skyType) {
            this.needsSaving = true;
            this.skyType = skyType;
        }
    }

    public boolean isGeneratingVegetation() {
        return generatingVegetation;
    }

    public void setGeneratingVegetation(boolean generatingVegetation) {
        if (this.generatingVegetation != generatingVegetation) {
            this.needsSaving = true;
            this.generatingVegetation = generatingVegetation;
        }
    }

    public boolean isGeneratingTrees() {
        return generatingTrees;
    }

    public void setGeneratingTrees(boolean generatingTrees) {
        if (this.generatingTrees != generatingTrees) {
            this.needsSaving = true;
            this.generatingTrees = generatingTrees;
        }
    }

    public String getBiomeId() {
        return biomeId;
    }

    public int getRawBiomeId() {
        Biome[] biomes = Lists.newArrayList(Biome.REGISTRY).toArray(new Biome[0]);
        for (int i = 0; i < biomes.length; i++) {
            if (biomes[i] != null && biomes[i].getBiomeName() != null && biomes[i].getBiomeName().equalsIgnoreCase(biomeId)) {
                return i;
            }
        }
        return 1;
    }

    public void setBiomeId(String biomeId) {
        if (biomeId == null) {
            biomeId = "Plains";
        }
        if (!this.biomeId.equalsIgnoreCase(biomeId)) {
            this.needsSaving = true;
            this.biomeId = biomeId;
        }
    }

    public boolean getAllowGenerationChanges() {
        return allowGenerationChanges;
    }

    public void setAllowGenerationChanges(boolean allowGenerationChanges) {
        if (this.allowGenerationChanges != allowGenerationChanges) {
            this.needsSaving = true;
            this.allowGenerationChanges = allowGenerationChanges;
        }
    }

    public boolean needsSaving() {
        return needsSaving;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public void setWorldTime(long worldTime) {
        this.worldTime = worldTime;
    }

    public boolean isRaining() {
        return raining;
    }

    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    public int getRainTime() {
        return rainTime;
    }

    public void setRainTime(int rainTime) {
        this.rainTime = rainTime;
    }

    public boolean isThundering() {
        return thundering;
    }

    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    public int getThunderTime() {
        return thunderTime;
    }

    public void setThunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }

    public boolean isTimeDataPersisted() {
        return timeDataPersisted;
    }

    public void markDirty() {
        this.needsSaving = true;
    }

    public void captureTimeFrom(WorldInfo info) {
        worldTime = info.getWorldTime();
        raining = info.isRaining();
        rainTime = info.getRainTime();
        thundering = info.isThundering();
        thunderTime = info.getThunderTime();
        timeDataPersisted = true;
    }

    public void applyTimeTo(WorldInfo info) {
        info.setWorldTime(worldTime);
        info.setRaining(raining);
        info.setRainTime(rainTime);
        info.setThundering(thundering);
        info.setThunderTime(thunderTime);
    }

    public boolean hasTimeChanged(WorldInfo info) {
        return worldTime != info.getWorldTime() || raining != info.isRaining()
                || rainTime != info.getRainTime()
                || thundering != info.isThundering()
                || thunderTime != info.getThunderTime();
    }

    private void resetTimeFields() {
        worldTime = 0;
        raining = false;
        rainTime = 0;
        thundering = false;
        thunderTime = 0;
        timeDataPersisted = false;
    }

    public static ArrayList<FlatLayerInfo> parseLayers(String preset) {
        if (preset == null) {
            return Lists.newArrayList();
        }
        preset = preset.replaceAll("\\s+", "");
        if (preset.isEmpty() || !PRESET_VALIDATION_PATTERN.matcher(preset).matches()) {
            return Lists.newArrayList();
        }
        ArrayList<FlatLayerInfo> infos = new ArrayList<>();
        int currY = 0;
        for (String layerStr : preset.split(";")) {
            if (layerStr.isEmpty()) {
                continue;
            }
            // Format: modid:block:meta*count or modid:block*count (meta defaults to 0)
            String[] components = layerStr.split("\\*", 2);
            String blockPart = components[0];
            int blockCount = 1;
            if (components.length > 1) {
                try {
                    blockCount = Integer.parseInt(components[1]);
                } catch (NumberFormatException nfe) {
                    return Lists.newArrayList();
                }
            }
            blockCount = MathHelper.clamp(blockCount, 1, 255);

            ParsedBlock parsedBlock = parseBlock(blockPart);
            Block block = blockFromParsedBlock(parsedBlock);
            if (block == null) {
                return Lists.newArrayList();
            }
            FlatLayerInfo info = new FlatLayerInfo(3, blockCount, block, parsedBlock.meta());
            info.setMinY(currY);
            infos.add(info);
            currY += blockCount;
            if (currY > 255) {
                break;
            }
        }
        return infos;
    }

    public List<FlatLayerInfo> getLayers() {
        return Collections.unmodifiableList(this.layers);
    }

    public List<FlatLayerInfo> getMutableLayers() {
        return this.layers;
    }

    private static final Object DIMENSION_TYPE_LOCK = new Object();
    private static DimensionType PERSONAL_DIMENSION_TYPE;

    public static DimensionType getDimensionTypeForProvider() {
        synchronized (DIMENSION_TYPE_LOCK) {
            if (PERSONAL_DIMENSION_TYPE != null) {
                return PERSONAL_DIMENSION_TYPE;
            }

            // Dimension type IDs are global in Forge 1.12.2. Do not use the old
            // fixed 127 value: modpacks commonly already occupy it.
            final String name = PersonalSpaceMod.MODID + "_personal";
            DimensionType existing = findDimensionTypeByName(name);
            if (existing != null) {
                PERSONAL_DIMENSION_TYPE = existing;
                return existing;
            }

            int typeId = -127;
            while (typeId > -4096 && findDimensionTypeById(typeId) != null) {
                --typeId;
            }
            if (typeId <= -4096) {
                throw new IllegalStateException("No free DimensionType ID is available for " + name);
            }

            PERSONAL_DIMENSION_TYPE = DimensionType.register(
                    name,
                    name,
                    typeId,
                    PersonalWorldProvider.class,
                    false);
            PersonalSpaceMod.LOG.info("Registered personal dimension type {} with type ID {}", name, typeId);
            return PERSONAL_DIMENSION_TYPE;
        }
    }

    private static DimensionType findDimensionTypeByName(String name) {
        try {
            return DimensionType.byName(name);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    private static DimensionType findDimensionTypeById(int id) {
        try {
            return DimensionType.getById(id);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    public static String layersToString(List<FlatLayerInfo> layers) {
        StringBuilder b = new StringBuilder();
        for (FlatLayerInfo info : layers) {
            int count = info.getLayerCount();
            if (count < 1) {
                continue;
            }
            ResourceLocation block = Block.REGISTRY.getNameForObject(info.getLayerMaterial().getBlock());
            if (block == null) {
                block = new ResourceLocation("minecraft:air");
            }
            b.append(block.toString());
            int meta = info.getLayerMaterial().getBlock().getMetaFromState(info.getLayerMaterial());
            if (meta != 0) {
                b.append(':');
                b.append(meta);
            }
            if (count > 1) {
                b.append('*');
                b.append(count);
            }
            b.append(';');
        }
        if (b.length() > 0) {
            b.deleteCharAt(b.length() - 1);
        }
        return b.toString();
    }

    public String getLayersAsString() {
        return layersToString(this.layers);
    }

    /**
     * Encodes a block name and meta into modid:name:meta format (meta omitted if 0).
     */
    private static String encodeBlock(String blockName, int meta) {
        return meta != 0 ? blockName + ":" + meta : blockName;
    }

    /**
     * Parses a block string in modid:name or modid:name:meta format.
     */
    private static ParsedBlock parseBlock(String blockStr) {
        // blockStr is like "minecraft:stone" or "minecraft:stone:3"
        int lastColon = blockStr.lastIndexOf(':');
        int firstColon = blockStr.indexOf(':');
        if (lastColon > firstColon && lastColon >= 0) {
            // Has meta part
            String name = blockStr.substring(0, lastColon);
            try {
                int meta = Integer.parseInt(blockStr.substring(lastColon + 1));
                return new ParsedBlock(name, meta);
            } catch (NumberFormatException e) {
                // Not a valid meta, treat whole string as block name
                return new ParsedBlock(blockStr, 0);
            }
        }
        return new ParsedBlock(blockStr, 0);
    }

    private static Block blockFromParsedBlock(ParsedBlock parsedBlock) {
        String blockName = parsedBlock.blockName();
        int separator = blockName.indexOf(':');
        if (separator <= 0 || separator != blockName.lastIndexOf(':') || separator >= blockName.length() - 1) {
            return null;
        }
        return Block.REGISTRY.getObject(new ResourceLocation(blockName));
    }

    /**
     * Encodes all generation settings (layers + boundary + gap + center) into a single string for copy/paste
     * convenience. Format:
     * layers_part|B,blockA,blockB,intervalX,intervalZ|G,width,preset,blockA,blockB,blockC,roadArea|S,allSurfaces|O,overwriteFirstLayer|C,enabled,dir,block
     * where each block is modid:name or modid:name:meta (meta omitted if 0), and allSurfaces and overwriteFirstLayer are 0 or 1. The center
     * section is omitted when no center marker block is configured. For compatibility, old preset strings may omit the
     * S section.
     */
    public String getFullPresetString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getLayersAsString());
        // Boundary section
        sb.append("|B,");
        sb.append(encodeBlock(getBoundaryBlockA(), getBoundaryMetaA())).append(',');
        sb.append(encodeBlock(getBoundaryBlockB(), getBoundaryMetaB())).append(',');
        sb.append(getBoundaryChunkIntervalX()).append(',').append(getBoundaryChunkIntervalZ());
        // Gap section
        sb.append("|G,");
        sb.append(getGapWidth()).append(',').append(getGapPreset().ordinal()).append(',');
        sb.append(encodeBlock(getGapBlockA(), getGapMetaA())).append(',');
        sb.append(encodeBlock(getGapBlockB(), getGapMetaB())).append(',');
        sb.append(encodeBlock(getGapBlockC(), getGapMetaC())).append(',');
        sb.append(getRoadAreaChunks());
        // Apply-to-surfaces section
        sb.append("|S,");
        sb.append(isApplyToAllSurfaceLayers() ? 1 : 0);
        // First-layer overwrite section
        sb.append("|O,");
        sb.append(isOverwriteFirstLayer() ? 1 : 0);
        // Center section
        if (isCenterEnabled() && !getCenterBlock().isEmpty()) {
            sb.append("|C,");
            sb.append(1).append(',').append(getCenterDirection().ordinal()).append(',');
            sb.append(encodeBlock(getCenterBlock(), getCenterMeta()));
        }
        return sb.toString();
    }

    /**
     * Extracts the layers-only part from a full preset string (before the first '|').
     */
    public static String extractLayersPart(String fullPreset) {
        if (fullPreset == null) return "";
        int pipe = fullPreset.indexOf('|');
        return pipe >= 0 ? fullPreset.substring(0, pipe) : fullPreset;
    }

    /**
     * Returns true if the string contains extended settings (boundary/gap/center sections).
     */
    public static boolean hasExtendedSettings(String fullPreset) {
        return fullPreset != null && fullPreset.contains("|");
    }

    /**
     * Applies extended settings (boundary/gap/center) from a full preset string. Either all present sections are
     * applied or none of them are.
     */
    public void applyExtendedSettings(String fullPreset) {
        if (fullPreset == null || !fullPreset.contains("|")) return;

        DimensionConfig parsedConfig = new DimensionConfig();
        parsedConfig.copyFrom(this, false, false, true);
        parsedConfig.setApplyToAllSurfaceLayers(false);
        parsedConfig.setOverwriteFirstLayer(false);
        if (!applyExtendedSettingsSections(fullPreset.split("\\|"), parsedConfig)) {
            return;
        }

        copyFrom(parsedConfig, false, false, true);
    }

    private static boolean applyExtendedSettingsSections(String[] sections, DimensionConfig target) {
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            if (section.isEmpty()) continue;
            String[] parts = section.split(",", -1);
            String type = parts[0];
            try {
                switch (type) {
                    case "B":
                        if (parts.length < 5) {
                            return false;
                        }
                        ParsedBlock bA = parseBlock(parts[1]);
                        target.setBoundaryBlockA(bA.blockName());
                        target.setBoundaryMetaA(bA.meta());
                        ParsedBlock bB = parseBlock(parts[2]);
                        target.setBoundaryBlockB(bB.blockName());
                        target.setBoundaryMetaB(bB.meta());
                        target.setBoundaryChunkIntervalX(Integer.parseInt(parts[3]));
                        target.setBoundaryChunkIntervalZ(Integer.parseInt(parts[4]));
                        break;
                    case "G":
                        if (parts.length < 6) {
                            return false;
                        }
                        target.setGapWidth(Integer.parseInt(parts[1]));
                        target.setGapPreset(GapPreset.fromOrdinal(Integer.parseInt(parts[2])));
                        ParsedBlock gA = parseBlock(parts[3]);
                        target.setGapBlockA(gA.blockName());
                        target.setGapMetaA(gA.meta());
                        ParsedBlock gB = parseBlock(parts[4]);
                        target.setGapBlockB(gB.blockName());
                        target.setGapMetaB(gB.meta());
                        ParsedBlock gC = parseBlock(parts[5]);
                        target.setGapBlockC(gC.blockName());
                        target.setGapMetaC(gC.meta());
                        if (parts.length >= 7) {
                            target.setRoadAreaChunks(Integer.parseInt(parts[6]));
                        }
                        break;
                    case "S":
                        if (parts.length < 2 || parts[1].isEmpty()) {
                            return false;
                        }
                        target.setApplyToAllSurfaceLayers(Integer.parseInt(parts[1]) != 0);
                        break;
                    case "O":
                        if (parts.length < 2 || parts[1].isEmpty()) {
                            return false;
                        }
                        target.setOverwriteFirstLayer(Integer.parseInt(parts[1]) != 0);
                        break;
                    case "C":
                        if (parts.length < 4 || parts[1].isEmpty() || parts[2].isEmpty() || parts[3].isEmpty()) {
                            break;
                        }
                        int centerEnabled = Integer.parseInt(parts[1]);
                        if (centerEnabled != 0) {
                            target.setCenterEnabled(true);
                            target.setCenterDirection(CenterDirection.fromOrdinal(Integer.parseInt(parts[2])));
                            ParsedBlock cB = parseBlock(parts[3]);
                            target.setCenterBlock(cB.blockName());
                            target.setCenterMeta(cB.meta());
                        }
                        break;
                    default:
                        return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    public static boolean canUseLayers(String preset, boolean onClient) {
        if (preset == null) {
            preset = "";
        }
        if (preset.equals(PRESET_UW_GARDEN) || preset.equals(PRESET_UW_VOID) || preset.equals(PRESET_UW_MINING)) {
            return true;
        }
        List<FlatLayerInfo> infos = parseLayers(preset);
        me.eigenraven.personalspace.DebugLog.log("canUseLayers client=%s input=%s parsed=%d rules=%s", onClient, preset, infos.size(), onClient ? PersonalSpaceMod.clientAllowedBlocks : Config.allowedBlocks);
        // A dimension must always have at least one valid layer. Empty input is not a void world;
        // the void preset is represented by an explicit AIR layer.
        if (infos.isEmpty()) {
            return false;
        }
        // A missing client sync must not make the layer editor unusable in singleplayer or during handshake.
        List<String> rawRules = onClient && !PersonalSpaceMod.clientAllowedBlocks.isEmpty()
                ? PersonalSpaceMod.clientAllowedBlocks
                : new ArrayList<>(Config.allowedBlocks);
        List<AllowedBlock> rules = AllowedBlockRules.parseAll(rawRules);
        for (FlatLayerInfo info : infos) {
            Block block = info.getLayerMaterial().getBlock();
            if (block == null) {
                return false;
            }
            if (block == Blocks.AIR) {
                continue;
            }
            ResourceLocation blockId = Block.REGISTRY.getNameForObject(block);
            if (blockId == null) {
                return false;
            }
            String blockName = blockId.toString();
            int meta = info.getLayerMaterial().getBlock().getMetaFromState(info.getLayerMaterial());
            AllowedBlock rule = AllowedBlockRules.findByBlockName(rules, blockName);
            if (rule == null || !rule.isMetaAllowed(meta)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canUseBiome(String biome, boolean onClient) {
        if (biome.equalsIgnoreCase("Plains")) {
            return true;
        }
        return onClient ? PersonalSpaceMod.clientAllowedBiomes.contains(biome.toLowerCase())
                : Config.allowedBiomes.contains(biome.toLowerCase());
    }

    /**
     * Doesn't check if the layers are valid, make sure to call canUseLayers on user-provided input
     */
    public void setLayers(String preset) {
        ArrayList<FlatLayerInfo> parsed = parseLayers(preset);
        if (parsed.isEmpty()) {
            parsed = parseLayers(PRESET_UW_VOID);
        }
        this.layers = parsed;
        for (int i = 0; i < this.layers.size(); i++) {
            FlatLayerInfo info = this.layers.get(i);
            if (info.getLayerCount() < 1) {
                Block block = info.getLayerMaterial().getBlock();
                int meta = block.getMetaFromState(info.getLayerMaterial());
                this.layers.set(i, new FlatLayerInfo(3, 1, block, meta));
            }
            this.layers.get(i).setMinY(i == 0 ? 0 : this.layers.get(i - 1).getMinY()
                    + this.layers.get(i - 1).getLayerCount());
        }
        me.eigenraven.personalspace.DebugLog.log("setLayers input=%s output=%s count=%d", preset, getLayersAsString(), this.layers.size());
    }

    /**
     * @param name Original UW save folder name
     * @return Dimension config generating a UW-compatible world, and the dimension ID of the original world; or null if
     *         it's not a UW world
     */
    public static MutablePair<DimensionConfig, Integer> fromUtilityWorldsWorld(String name) {
        boolean isMining = name.startsWith("UW_MINING_");
        boolean isGarden = name.startsWith("UW_GARDEN_");
        boolean isVoid = name.startsWith("UW_VOID_");
        if (!(isMining || isGarden || isVoid)) {
            return null;
        }
        String dimIdStr = name.split("_", 3)[2];
        int dimId;
        try {
            dimId = Integer.parseInt(dimIdStr);
        } catch (NumberFormatException nfe) {
            PersonalSpaceMod.LOG.warn("Couldn't parse dimension ID from folder name " + name, nfe);
            return null;
        }
        DimensionConfig cfg = new DimensionConfig();
        cfg.saveDirOverride = name;
        cfg.skyColor = isVoid ? 0x000000 : 0xc0d8ff;
        cfg.starBrightness = isVoid ? 0.0F : 1.0F;
        if (isMining) {
            cfg.setLayers(PRESET_UW_MINING);
        } else if (isGarden) {
            cfg.setLayers(PRESET_UW_GARDEN);
            cfg.generatingVegetation = true;
        } else /* isVoid */ {
            cfg.setLayers(PRESET_UW_VOID);
        }
        return MutablePair.of(cfg, dimId);
    }

    public int getGroundLevel() {
        if (layers.isEmpty()) {
            return 128;
        }
        int y = 0;
        for (FlatLayerInfo info : layers) {
            y += info.getLayerCount();
        }
        return MathHelper.clamp(y, 0, 255);
    }

    public static String blockToString(Block block) {
        if (block == null) {
            return "";
        }
        ResourceLocation uid = Block.REGISTRY.getNameForObject(block);
        if (uid == null) {
            return "";
        }
        return uid.toString();
    }

    public static Block blockFromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return blockFromParsedBlock(parseBlock(name.trim()));
    }

    public String getBoundaryBlockA() {
        return boundaryBlockA == null ? "" : boundaryBlockA;
    }

    public void setBoundaryBlockA(String boundaryBlockA) {
        if (boundaryBlockA == null) {
            boundaryBlockA = "";
        }
        if (!this.getBoundaryBlockA().equals(boundaryBlockA)) {
            this.needsSaving = true;
            this.boundaryBlockA = boundaryBlockA;
        }
    }

    public int getBoundaryMetaA() {
        return boundaryMetaA;
    }

    public void setBoundaryMetaA(int boundaryMetaA) {
        int v = Math.max(boundaryMetaA, 0);
        if (this.boundaryMetaA != v) {
            this.needsSaving = true;
            this.boundaryMetaA = v;
        }
    }

    public String getBoundaryBlockB() {
        return boundaryBlockB == null ? "" : boundaryBlockB;
    }

    public void setBoundaryBlockB(String boundaryBlockB) {
        if (boundaryBlockB == null) {
            boundaryBlockB = "";
        }
        if (!this.getBoundaryBlockB().equals(boundaryBlockB)) {
            this.needsSaving = true;
            this.boundaryBlockB = boundaryBlockB;
        }
    }

    public int getBoundaryMetaB() {
        return boundaryMetaB;
    }

    public void setBoundaryMetaB(int boundaryMetaB) {
        int v = Math.max(boundaryMetaB, 0);
        if (this.boundaryMetaB != v) {
            this.needsSaving = true;
            this.boundaryMetaB = v;
        }
    }

    public int getBoundaryChunkIntervalX() {
        return boundaryChunkIntervalX;
    }

    public void setBoundaryChunkIntervalX(int boundaryChunkIntervalX) {
        int v = MathHelper.clamp(boundaryChunkIntervalX, 0, 20);
        if (this.boundaryChunkIntervalX != v) {
            this.needsSaving = true;
            this.boundaryChunkIntervalX = v;
        }
    }

    public int getBoundaryChunkIntervalZ() {
        return boundaryChunkIntervalZ;
    }

    public void setBoundaryChunkIntervalZ(int boundaryChunkIntervalZ) {
        int v = MathHelper.clamp(boundaryChunkIntervalZ, 0, 20);
        if (this.boundaryChunkIntervalZ != v) {
            this.needsSaving = true;
            this.boundaryChunkIntervalZ = v;
        }
    }

    public Block getBoundaryBlockAResolved() {
        return blockFromString(boundaryBlockA);
    }

    public Block getBoundaryBlockBResolved() {
        return blockFromString(boundaryBlockB);
    }

    public boolean isOverwriteFirstLayer() {
        return overwriteFirstLayer;
    }

    public void setOverwriteFirstLayer(boolean overwriteFirstLayer) {
        if (this.overwriteFirstLayer != overwriteFirstLayer) {
            this.needsSaving = true;
            this.overwriteFirstLayer = overwriteFirstLayer;
        }
    }

    public int getGapWidth() {
        return gapWidth;
    }

    public void setGapWidth(int gapWidth) {
        int v = MathHelper.clamp(gapWidth, 0, 5);
        if (this.gapWidth != v) {
            this.needsSaving = true;
            this.gapWidth = v;
        }
    }

    public GapPreset getGapPreset() {
        return gapPreset;
    }

    public void setGapPreset(GapPreset gapPreset) {
        if (this.gapPreset != gapPreset) {
            this.needsSaving = true;
            this.gapPreset = gapPreset;
        }
    }

    public int getRoadAreaChunks() {
        return roadAreaChunks;
    }

    public void setRoadAreaChunks(int roadAreaChunks) {
        int v = MathHelper.clamp(roadAreaChunks, 1, MAX_ROAD_AREA_CHUNKS);
        if (this.roadAreaChunks != v) {
            this.needsSaving = true;
            this.roadAreaChunks = v;
        }
    }

    public String getGapBlockA() {
        return gapBlockA == null ? "" : gapBlockA;
    }

    public void setGapBlockA(String gapBlockA) {
        if (gapBlockA == null) {
            gapBlockA = "";
        }
        if (!this.getGapBlockA().equals(gapBlockA)) {
            this.needsSaving = true;
            this.gapBlockA = gapBlockA;
        }
    }

    public int getGapMetaA() {
        return gapMetaA;
    }

    public void setGapMetaA(int gapMetaA) {
        int v = Math.max(gapMetaA, 0);
        if (this.gapMetaA != v) {
            this.needsSaving = true;
            this.gapMetaA = v;
        }
    }

    public String getGapBlockB() {
        return gapBlockB == null ? "" : gapBlockB;
    }

    public void setGapBlockB(String gapBlockB) {
        if (gapBlockB == null) {
            gapBlockB = "";
        }
        if (!this.getGapBlockB().equals(gapBlockB)) {
            this.needsSaving = true;
            this.gapBlockB = gapBlockB;
        }
    }

    public int getGapMetaB() {
        return gapMetaB;
    }

    public void setGapMetaB(int gapMetaB) {
        int v = Math.max(gapMetaB, 0);
        if (this.gapMetaB != v) {
            this.needsSaving = true;
            this.gapMetaB = v;
        }
    }

    public Block getGapBlockAResolved() {
        return blockFromString(gapBlockA);
    }

    public Block getGapBlockBResolved() {
        return blockFromString(gapBlockB);
    }

    public String getGapBlockC() {
        return gapBlockC == null ? "" : gapBlockC;
    }

    public void setGapBlockC(String gapBlockC) {
        if (gapBlockC == null) {
            gapBlockC = "";
        }
        if (!this.getGapBlockC().equals(gapBlockC)) {
            this.needsSaving = true;
            this.gapBlockC = gapBlockC;
        }
    }

    public int getGapMetaC() {
        return gapMetaC;
    }

    public void setGapMetaC(int gapMetaC) {
        int v = Math.max(gapMetaC, 0);
        if (this.gapMetaC != v) {
            this.needsSaving = true;
            this.gapMetaC = v;
        }
    }

    public Block getGapBlockCResolved() {
        return blockFromString(gapBlockC);
    }

    public boolean isApplyToAllSurfaceLayers() {
        return applyToAllSurfaceLayers;
    }

    public void setApplyToAllSurfaceLayers(boolean applyToAllSurfaceLayers) {
        if (this.applyToAllSurfaceLayers != applyToAllSurfaceLayers) {
            this.needsSaving = true;
            this.applyToAllSurfaceLayers = applyToAllSurfaceLayers;
        }
    }

    public boolean isCenterEnabled() {
        return centerEnabled;
    }

    public void setCenterEnabled(boolean centerEnabled) {
        if (this.centerEnabled != centerEnabled) {
            this.needsSaving = true;
            this.centerEnabled = centerEnabled;
        }
    }

    public CenterDirection getCenterDirection() {
        return centerDirection;
    }

    public void setCenterDirection(CenterDirection centerDirection) {
        if (this.centerDirection != centerDirection) {
            this.needsSaving = true;
            this.centerDirection = centerDirection;
        }
    }

    public String getCenterBlock() {
        return centerBlock == null ? "" : centerBlock;
    }

    public void setCenterBlock(String centerBlock) {
        if (centerBlock == null) {
            centerBlock = "";
        }
        if (!this.getCenterBlock().equals(centerBlock)) {
            this.needsSaving = true;
            this.centerBlock = centerBlock;
        }
    }

    public int getCenterMeta() {
        return centerMeta;
    }

    public void setCenterMeta(int centerMeta) {
        int v = Math.max(centerMeta, 0);
        if (this.centerMeta != v) {
            this.needsSaving = true;
            this.centerMeta = v;
        }
    }

    public Block getCenterBlockResolved() {
        return blockFromString(centerBlock);
    }
}
