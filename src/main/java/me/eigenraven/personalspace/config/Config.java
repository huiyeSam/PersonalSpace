package me.eigenraven.personalspace.config;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraftforge.common.config.Configuration;

import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.world.DimensionConfig;

public class Config {

    private static class Defaults {

        public static final String[] defaultPresets = new String[] { DimensionConfig.PRESET_UW_VOID,
                DimensionConfig.PRESET_UW_GARDEN, DimensionConfig.PRESET_UW_MINING, };

        public static final String[] allowedBlocks = new String[] { "minecraft:air", "minecraft:bedrock",
                "minecraft:stone", "minecraft:cobblestone", "minecraft:dirt", "minecraft:grass",
                "minecraft:double_stone_slab", "minecraft:netherrack", "minecraft:concrete:0-15",
                "minecraft:stonebrick:0-3", "personalspace:smooth_stone" };

        public static final String[] allowedBoundaryBlocks = new String[] { "minecraft:concrete:0-15",
                "minecraft:stonebrick:0-3", "personalspace:smooth_stone" };

        public static final String[] allowedGapBlocks = new String[] { "minecraft:concrete:0-15",
                "minecraft:stonebrick:0-3", "personalspace:smooth_stone" };

        public static final String[] allowedCenterBlocks = new String[] { "minecraft:concrete:0-15",
                "minecraft:stonebrick:0-3", "personalspace:smooth_stone" };

        public static final String[] allowedBiomes = new String[] { "Plains", "Ocean", "Desert", "Extreme Hills",
                "Forest", "Taiga", "Swampland", "River", "MushroomIsland", "Swampland", "Jungle", "Savanna", "Mesa" };

        public static final int firstDimensionId = 180;
        public static final boolean debugLogging = false;
        public static final boolean useBlockEventChecks = true;
        public static final int dropdownMaxVisibleRows = 6;
        public static final int dropdownMaxVisibleColumns = 12;
        public static final int roadAreaChunks = 5;
    }

    private static class Categories {

        public static final String general = "general";
    }

    public static String[] defaultPresets = Arrays.copyOf(Defaults.defaultPresets, Defaults.defaultPresets.length);
    public static HashSet<String> allowedBlocks = new HashSet<>(Arrays.asList(Defaults.allowedBlocks));
    public static HashSet<String> allowedBoundaryBlocks = new HashSet<>(Arrays.asList(Defaults.allowedBoundaryBlocks));
    public static HashSet<String> allowedGapBlocks = new HashSet<>(Arrays.asList(Defaults.allowedGapBlocks));
    public static HashSet<String> allowedCenterBlocks = new HashSet<>(Arrays.asList(Defaults.allowedCenterBlocks));
    public static HashSet<String> allowedBiomes = new HashSet<>(Arrays.asList(Defaults.allowedBiomes));
    public static int firstDimensionId = Defaults.firstDimensionId;
    public static boolean debugLogging = Defaults.debugLogging;
    public static boolean useBlockEventChecks = Defaults.useBlockEventChecks;
    public static int dropdownMaxVisibleRows = Defaults.dropdownMaxVisibleRows;
    public static int dropdownMaxVisibleColumns = Defaults.dropdownMaxVisibleColumns;
    public static int roadAreaChunks = Defaults.roadAreaChunks;

    private static File savedConfigFile = null;

    public static File getSavedConfigFile() {
        return savedConfigFile;
    }

    /** Stone bricks are always selectable everywhere, even in old config files. */
    private static final String STONEBRICK_ENTRY = "minecraft:stonebrick:0-3";

    private static void ensureStonebrick(HashSet<String> set) {
        boolean missing = true;
        for (String entry : set) {
            if (entry.toLowerCase().startsWith("minecraft:stonebrick")) {
                missing = false;
                break;
            }
        }
        if (missing) {
            set.add(STONEBRICK_ENTRY);
        }
    }

    private static final String LANG_PREFIX = "personalspace.config.";
    private static final String BLOCK_RULE_FORMAT = "\n格式：modid:block[:元数据]，例如 minecraft:stone:0-6；可用 !3 排除元数据 3。"
            + "\nFormat: modid:block[:meta], e.g. minecraft:stone:0-6; use !3 to exclude meta 3.";
    private static Configuration configuration;

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static void setDebugLogging(boolean enabled) {
        debugLogging = enabled;
        if (configuration != null) {
            configuration.get(Categories.general, "debugLogging", Defaults.debugLogging).set(enabled);
            configuration.save();
        }
    }

    public static void synchronizeConfiguration(File configFile) {
        savedConfigFile = configFile;
        configuration = new Configuration(configFile);
        configuration.load();
        applyConfiguration();
        // Persist updated comments even when migrating a file with no changed values.
        configuration.save();
    }

    public static void saveGuiChanges() {
        applyConfiguration();
        validateBlocks();
        configuration.save();
    }

    private static void applyConfiguration() {
        defaultPresets = stringList("defaultPresets", Defaults.defaultPresets,
                "私人维度的地层预设。格式：方块ID*层数;方块ID*层数，例如 minecraft:bedrock;minecraft:dirt*3;minecraft:grass。",
                "Layer presets for personal dimensions. Format: block_id*layers;block_id*layers, e.g. minecraft:bedrock;minecraft:dirt*3;minecraft:grass.");

        allowedBlocks = blockRules("allowedBlocks", Defaults.allowedBlocks,
                "允许用作地层的方块。", "Blocks allowed in terrain layers.");
        allowedBoundaryBlocks = blockRules("allowedBoundaryBlocks", Defaults.allowedBoundaryBlocks,
                "允许用作区块边界的方块。", "Blocks allowed in chunk boundaries.");
        allowedGapBlocks = blockRules("allowedGapBlocks", Defaults.allowedGapBlocks,
                "允许用作隔段或马路的方块。", "Blocks allowed in gaps or roads.");
        allowedCenterBlocks = blockRules("allowedCenterBlocks", Defaults.allowedCenterBlocks,
                "允许用作中心标记的方块。", "Blocks allowed as center markers.");

        allowedBiomes = Arrays.stream(stringList("allowedBiomes", Defaults.allowedBiomes,
                "私人维度可选的生物群系名称，不区分大小写。",
                "Biome names allowed in personal dimensions, case-insensitive."))
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(HashSet::new));

        firstDimensionId = integer("firstDimensionId", Defaults.firstDimensionId, 0, Integer.MAX_VALUE,
                "分配新私人维度 ID 的起始值，不改变已有维度的 ID。",
                "Starting ID for new personal dimensions. Does not change existing IDs.");
        debugLogging = bool("debugLogging", Defaults.debugLogging,
                "是否启用详细调试日志。", "Enable detailed debug logging.");
        useBlockEventChecks = bool("useBlockEventChecks", Defaults.useBlockEventChecks,
                "通过模拟方块破坏事件检查权限。仅在事件处理器不兼容时考虑关闭。",
                "Check permissions using simulated block break events. Consider disabling only if an event handler is incompatible.");
        dropdownMaxVisibleRows = integer("dropdownMaxVisibleRows", Defaults.dropdownMaxVisibleRows, 1, 20,
                "方块下拉菜单同时可见的最大行数。", "Maximum visible rows in block dropdown menus.");
        dropdownMaxVisibleColumns = integer("dropdownMaxVisibleColumns", Defaults.dropdownMaxVisibleColumns, 1, 24,
                "方块下拉菜单同时可见的最大列数。", "Maximum visible columns in block dropdown menus.");
        roadAreaChunks = integer("roadAreaChunks", Defaults.roadAreaChunks, 1, 20,
                "边界间隔为 0 时，马路之间区域的默认边长，单位为区块。",
                "Default area width between roads in chunks when the boundary interval is zero.");
    }

    private static String[] stringList(String name, String[] defaults, String zh, String en) {
        return configuration.getStringList(name, Categories.general, defaults, zh + "\n" + en, null,
                LANG_PREFIX + name);
    }

    private static HashSet<String> blockRules(String name, String[] defaults, String zh, String en) {
        String comment = zh + "\n" + en + BLOCK_RULE_FORMAT
                + "\n未指定元数据时使用 0；石砖始终可选。\nOmitted meta defaults to 0; stone bricks are always selectable.";
        HashSet<String> rules = new HashSet<>(Arrays.asList(configuration.getStringList(name, Categories.general,
                defaults, comment, null, LANG_PREFIX + name)));
        ensureStonebrick(rules);
        return rules;
    }

    private static int integer(String name, int defaultValue, int min, int max, String zh, String en) {
        return configuration.getInt(name, Categories.general, defaultValue, min, max, zh + "\n" + en,
                LANG_PREFIX + name);
    }

    private static boolean bool(String name, boolean defaultValue, String zh, String en) {
        return configuration.getBoolean(name, Categories.general, defaultValue, zh + "\n" + en, LANG_PREFIX + name);
    }

    public static void validateBlocks() {
        filterInvalidBlocks(allowedBlocks);
        filterInvalidBlocks(allowedBoundaryBlocks);
        filterInvalidBlocks(allowedGapBlocks);
        filterInvalidBlocks(allowedCenterBlocks);
    }

    private static void filterInvalidBlocks(HashSet<String> blockSet) {
        Iterator<String> it = blockSet.iterator();
        while (it.hasNext()) {
            String entry = it.next();
            AllowedBlock parsed = null;
            try {
                parsed = AllowedBlock.parse(entry);
            } catch (NumberFormatException ignored) {}
            if (parsed == null) {
                PersonalSpaceMod.LOG.warn("Removing invalid block config entry: {}", entry);
                it.remove();
                continue;
            }
            Block block = DimensionConfig.blockFromString(parsed.blockName());
            if (block == null) {
                PersonalSpaceMod.LOG.warn("Removing non-existent block from config: {}", entry);
                it.remove();
            }
        }
    }
}
