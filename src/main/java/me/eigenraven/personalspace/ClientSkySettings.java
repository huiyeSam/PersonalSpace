package me.eigenraven.personalspace;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import me.eigenraven.personalspace.world.DimensionConfig;

/** Client-local sky preferences, isolated by the active save and dimension id. */
@SideOnly(Side.CLIENT)
public final class ClientSkySettings {
    private ClientSkySettings() {}

    private static File file(World world, int dimension) {
        File root = world.getSaveHandler().getWorldDirectory();
        File directory = new File(root, "personalspace-client-sky");
        if (!directory.isDirectory()) directory.mkdirs();
        return new File(directory, "dimension-" + dimension + ".cfg");
    }

    public static void save(World world, int dimension, DimensionConfig config) {
        Configuration cfg = new Configuration(file(world, dimension));
        cfg.get("sky", "color", config.getSkyColor()).set(config.getSkyColor());
        cfg.get("sky", "stars", config.getStarBrightness()).set(config.getStarBrightness());
        cfg.get("sky", "weather", config.isWeatherEnabled()).set(config.isWeatherEnabled());
        cfg.get("sky", "cycle", config.getDaylightCycle().ordinal()).set(config.getDaylightCycle().ordinal());
        cfg.get("sky", "clouds", config.isCloudsEnabled()).set(config.isCloudsEnabled());
        cfg.get("sky", "type", config.getSkyType().ordinal()).set(config.getSkyType().ordinal());
        cfg.save();
    }

    public static void apply(World world, int dimension, DimensionConfig config) {
        File saved = file(world, dimension);
        if (!saved.isFile()) return;
        Configuration cfg = new Configuration(saved);
        Property value = cfg.get("sky", "color", config.getSkyColor()); config.setSkyColor(value.getInt());
        value = cfg.get("sky", "stars", config.getStarBrightness()); config.setStarBrightness((float) value.getDouble());
        value = cfg.get("sky", "weather", config.isWeatherEnabled()); config.setWeatherEnabled(value.getBoolean());
        value = cfg.get("sky", "cycle", config.getDaylightCycle().ordinal()); config.setDaylightCycle(DimensionConfig.DaylightCycle.fromOrdinal(value.getInt()));
        value = cfg.get("sky", "clouds", config.isCloudsEnabled()); config.setCloudsEnabled(value.getBoolean());
        value = cfg.get("sky", "type", config.getSkyType().ordinal()); config.setSkyType(DimensionConfig.SkyType.fromOrdinal(value.getInt()));
    }
}
