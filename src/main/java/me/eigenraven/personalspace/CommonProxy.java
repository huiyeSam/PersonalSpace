package me.eigenraven.personalspace;

import net.minecraft.world.World;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import gnu.trove.map.hash.TIntObjectHashMap;
import me.eigenraven.personalspace.block.PortalTileEntity;
import me.eigenraven.personalspace.config.Config;
import me.eigenraven.personalspace.world.DimensionConfig;
import me.eigenraven.personalspace.world.OreGenInterceptor;
import net.minecraftforge.common.MinecraftForge;

public class CommonProxy {

    protected final TIntObjectHashMap<DimensionConfig> clientDimensionConfigObjects = new TIntObjectHashMap<>();
    protected final TIntObjectHashMap<DimensionConfig> serverDimensionConfigObjects = new TIntObjectHashMap<>();

    public static TIntObjectHashMap<DimensionConfig> getDimensionConfigObjects(boolean isClient) {
        if (isClient) {
            return PersonalSpaceMod.proxy.clientDimensionConfigObjects;
        } else {
            return PersonalSpaceMod.proxy.serverDimensionConfigObjects;
        }
    }

    // preInit "Run before anything else. Read your config, create blocks, items,
    // etc., and register them with the GameRegistry."
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new OreGenInterceptor());
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes."
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this."
    public void postInit(FMLPostInitializationEvent event) {}

    public void completeInit(FMLLoadCompleteEvent event) {}

    public void serverAboutToStart(FMLServerAboutToStartEvent event) {}

    // register server commands in this event handler
    public void serverStarting(FMLServerStartingEvent event) {}

    public void serverStarted(FMLServerStartedEvent event) {}

    public void serverStopping(FMLServerStoppingEvent event) {}

    public void serverStopped(FMLServerStoppedEvent event) {}

    public void openPortalGui(World world, int x, int y, int z) {}

    public void closePortalGui(PortalTileEntity owner) {}
}
