package me.eigenraven.personalspace.world;

import java.util.Arrays;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.client.IRenderHandler;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import me.eigenraven.personalspace.CommonProxy;
import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.config.Config;

/**
 * Based on WorldProviderEnd
 */
public class PersonalWorldProvider extends WorldProvider {

    DimensionConfig config;
    DimensionConfig.SkyType lastSkyType = null;

    public PersonalWorldProvider() {
        // Called by Forge, followed by setDimension
    }

    @Override
    public void setDimension(int dim) {
        // Called by Forge/Vanilla immediately after the constructor
        super.setDimension(dim);
    }

    public net.minecraft.world.World getWorldObject() {
        return this.world;
    }

    public DimensionConfig getConfig() {
        if (this.config == null) {
            boolean isClient = (this.world != null) ? this.world.isRemote
                    : FMLCommonHandler.instance().getEffectiveSide().isClient();
            if (Config.debugLogging) {
                PersonalSpaceMod.LOG.info(
                        "PersonalWorldProvider loading config for dim {}, client {}",
                        this.getDimension(),
                        isClient,
                        new Throwable());
            }
            this.config = DimensionConfig.getForDimension(this.getDimension(), isClient);
            if (this.config == null && isClient) {
                this.config = new DimensionConfig();
                synchronized (CommonProxy.getDimensionConfigObjects(true)) {
                    CommonProxy.getDimensionConfigObjects(true).put(this.getDimension(), this.config);
                }
                PersonalSpaceMod.LOG.warn(
                        "Client has no settings yet for personal dimension {}; using temporary defaults until server sync",
                        this.getDimension());
            }
            if (this.config == null) {
                PersonalSpaceMod.LOG.fatal(
                        "PersonalSpace couldn't find dimension config for dimension {}, detected side: {}\nknown client dimension IDs: {}\nknown server dimension IDs: {}\n",
                        this.getDimension(),
                        isClient ? "CLIENT" : "SERVER",
                        Arrays.toString(CommonProxy.getDimensionConfigObjects(true).keys()),
                        Arrays.toString(CommonProxy.getDimensionConfigObjects(false).keys()),
                        new Throwable());
            }
        }
        return this.config;
    }

    public void registerWorldChunkManager() {
        getConfig();
        this.biomeProvider = new BiomeProviderSingle(Biome.getBiome(1));
    }

    public IChunkGenerator createChunkGenerator() {
        return new PersonalChunkProvider(this, this.world.getSeed());
    }

    @SideOnly(Side.CLIENT)
    public Vec3d getFogColor(float sunAngle, float timeSinceLastTick) {
        int baseColor = this.config.getSkyColor();

        float red = (float) (baseColor >> 16 & 255) / 255.0F;
        float green = (float) (baseColor >> 8 & 255) / 255.0F;
        float blue = (float) (baseColor & 255) / 255.0F;
        return new Vec3d(red, green, blue);
    }

    @Override
    public BlockPos getSpawnPoint() {
        return new BlockPos(8, getConfig().getGroundLevel() + 2, 8);
    }

    @Override
    public BlockPos getRandomizedSpawnPoint() {
        return getSpawnPoint();
    }

    @Override
    public Vec3d getSkyColor(Entity cameraEntity, float partialTicks) {
        return getFogColor(0.0f, partialTicks);
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getCloudHeight() {
        return getConfig().isCloudsEnabled() ? 256.0F : Float.NEGATIVE_INFINITY;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        return this.world.getGroundAboveSeaLevel(new BlockPos(x, 0, z)).getMaterial().blocksMovement();
    }

    @Override
    public int getAverageGroundLevel() {
        return getConfig().getGroundLevel();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean doesXZShowFog(int x, int z) {
        return false;
    }

    public boolean getWorldHasVoidParticles() {
        return false;
    }

    @Override
    public double getVoidFogYFactor() {
        return 1.0F;
    }

    @Override
    public String getSaveFolder() {
        String name = getConfig().getSaveDir(this.getDimension());
        if (Config.debugLogging) {
            PersonalSpaceMod.LOG.info("Save folder for dim id {} is {}", this.getDimension(), name, new Throwable());
        }
        return name;
    }

    @Override
    public boolean isDaytime() {
        if (getConfig().getDaylightCycle() == DimensionConfig.DaylightCycle.CYCLE) return super.isDaytime();

        return !this.getConfig().isNightTime();
    }

    @Override
    public float getSunBrightnessFactor(float par1) {
        if (getConfig().getDaylightCycle() == DimensionConfig.DaylightCycle.CYCLE)
            return super.getSunBrightnessFactor(par1);

        return this.getConfig().isNightTime() ? 0.0F : 1.0F;
    }

    @Override
    public float getSunBrightness(float par1) {
        if (getConfig().getDaylightCycle() == DimensionConfig.DaylightCycle.CYCLE) return super.getSunBrightness(par1);

        return this.getConfig().isNightTime() ? 0.2F : 1.0F;
    }

    @Override
    public float getStarBrightness(float par1) {
        if (getConfig().getDaylightCycle() == DimensionConfig.DaylightCycle.CYCLE) return super.getStarBrightness(par1);
        return getConfig().getStarBrightness();
    }

    @Override
    public float calculateCelestialAngle(long p_76563_1_, float p_76563_3_) {
        if (getConfig().getDaylightCycle() == DimensionConfig.DaylightCycle.CYCLE)
            return super.calculateCelestialAngle(p_76563_1_, p_76563_3_);

        return this.getConfig().isNightTime() ? 0.5f : 0.0f;
    }

    @Override
    public void calculateInitialWeather() {
        if (this.getConfig().isWeatherEnabled()) {
            super.calculateInitialWeather();
        } else {
            this.world.rainingStrength = 0.0F;
            this.world.thunderingStrength = 0.0F;
            this.world.prevRainingStrength = 0.0F;
            this.world.prevThunderingStrength = 0.0F;
        }
    }

    @Override
    public void updateWeather() {
        if (!this.world.isRemote && !this.getConfig().isWeatherEnabled()) {
            this.resetRainAndThunder();
            this.world.rainingStrength = 0.0F;
            this.world.thunderingStrength = 0.0F;
            this.world.prevRainingStrength = 0.0F;
            this.world.prevThunderingStrength = 0.0F;
        } else {
            super.updateWeather();
        }
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return this.getConfig().isWeatherEnabled();
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return this.getConfig().isWeatherEnabled();
    }

    @Override
    public boolean canBlockFreeze(BlockPos pos, boolean byWater) {
        return this.getConfig().isWeatherEnabled() && super.canBlockFreeze(pos, byWater);
    }

    @Override
    public boolean canSnowAt(BlockPos pos, boolean checkLight) {
        return this.getConfig().isWeatherEnabled() && super.canSnowAt(pos, checkLight);
    }

    public BlockPos getEntrancePortalLocation() {
        return new BlockPos(8, getAverageGroundLevel(), 8);
    }

    public void invalidateSkyRenderer() {
        this.lastSkyType = null;
        if (this.world != null && this.world.isRemote) updateSkyType();
    }

    private void updateSkyType() {
        if (this.world == null || !this.world.isRemote) {
            return;
        }
        DimensionConfig.SkyType currentSkyType = getConfig().getSkyType();
        if (lastSkyType != currentSkyType) {
            lastSkyType = currentSkyType;
            IRenderHandler sky = (IRenderHandler) lastSkyType.makeSkyProvider();
            IRenderHandler clouds = (IRenderHandler) lastSkyType.makeCloudProvider();
            setSkyRenderer(sky);
            setCloudRenderer(clouds);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getSkyRenderer() {
        updateSkyType();
        return super.getSkyRenderer();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IRenderHandler getCloudRenderer() {
        updateSkyType();
        return super.getCloudRenderer();
    }

    @Override
    public net.minecraft.world.DimensionType getDimensionType() {
        return DimensionConfig.getDimensionTypeForProvider();
    }
}
