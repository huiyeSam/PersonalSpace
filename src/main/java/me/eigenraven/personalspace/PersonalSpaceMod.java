package me.eigenraven.personalspace;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;

import appeng.api.AEApi;
import appeng.api.IAppEngApi;
import appeng.api.features.IWorldGen;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.packet.ICustomPacketHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkHandshakeEstablished;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import me.eigenraven.personalspace.block.PortalBlock;
import me.eigenraven.personalspace.block.PortalEntityItem;
import me.eigenraven.personalspace.block.PortalItem;
import me.eigenraven.personalspace.block.PortalTileEntity;
import me.eigenraven.personalspace.item.PersonalSpaceKeyItem;
import me.eigenraven.personalspace.config.Config;
import me.eigenraven.personalspace.net.Packets;
import me.eigenraven.personalspace.world.AsyncDimensionSaver;
import me.eigenraven.personalspace.world.DimensionConfig;
import me.eigenraven.personalspace.world.PersonalWorldProvider;

@Mod(
        modid = "personalspace",
        version = Tags.VERSION,
        name = PersonalSpaceMod.MODNAME,
        acceptedMinecraftVersions = "[1.12.2]",
        guiFactory = "me.eigenraven.personalspace.gui.PersonalSpaceGuiFactory",
        dependencies = "after:utilityworlds;after:appliedenergistics2-core;after:galaxyspace;after:thaumcraft")
public class PersonalSpaceMod {

    public static final String MODID = "personalspace";
    public static final String MODNAME = "Personal Dimension";

    public static final String DIM_METADATA_FILE = "personalspace_metadata.cfg";

    @Mod.Instance
    public static PersonalSpaceMod INSTANCE;

    public static Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
            clientSide = "me.eigenraven.personalspace.ClientProxy",
            serverSide = "me.eigenraven.personalspace.CommonProxy")
    public static CommonProxy proxy;

    public static me.eigenraven.personalspace.block.TeleportPointBlock BLOCK_TELEPORT_POINT;
    public static PortalBlock BLOCK_PORTAL, BP_MIGRATION_2, BP_MIGRATION_3, BP_MIGRATION_4;
    public static me.eigenraven.personalspace.block.SmoothStoneBlock BLOCK_SMOOTH_STONE;
    public static PersonalSpaceKeyItem ITEM_PERSONAL_SPACE_KEY;

    public static final String CHANNEL = MODID;

    public static List<String> clientAllowedBlocks = Lists.newArrayList();
    public static List<String> clientAllowedBiomes = Lists.newArrayList();
    public static List<String> clientAllowedBoundaryBlocks = Lists.newArrayList();
    public static List<String> clientAllowedGapBlocks = Lists.newArrayList();
    public static List<String> clientAllowedCenterBlocks = Lists.newArrayList();

    private boolean stopping = false;
    private static Field worldInfoField = null;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        DebugLog.initialize(event.getModConfigurationDirectory());
        if (Loader.isModLoaded("utilityworlds")) {
            throw new RuntimeException(
                    "Personal space mod cannot be loaded in the same instance with utilityworlds mod, because it replaces its functionality");
        }
        // Initialize GTCEu reflection-based interception for ore veins and oil deposits
        me.eigenraven.personalspace.world.OreGenInterceptor.initialize();
        if (event.getSide().isClient()) {
            PacketCustom
                    .assignHandler(CHANNEL, (ICustomPacketHandler.IClientPacketHandler) Packets.INSTANCE::handleClientPacket);
        }
        PacketCustom.assignHandler(CHANNEL, (ICustomPacketHandler.IServerPacketHandler) Packets.INSTANCE::handleServerPacket);
        NetworkRegistry.INSTANCE.newEventDrivenChannel(CHANNEL + "_event").register(this);
        proxy.preInit(event);
        BLOCK_TELEPORT_POINT = new me.eigenraven.personalspace.block.TeleportPointBlock();
        BLOCK_TELEPORT_POINT.setRegistryName(MODID, "teleport_point");
        BLOCK_PORTAL = new PortalBlock(false);
        // Legacy fields remain as aliases so old save migration points to the one real portal block.
        BP_MIGRATION_2 = BLOCK_PORTAL;
        BP_MIGRATION_3 = BLOCK_PORTAL;
        BP_MIGRATION_4 = BLOCK_PORTAL;
        BLOCK_PORTAL.setRegistryName(MODID, "personalPortal");
        BLOCK_SMOOTH_STONE = new me.eigenraven.personalspace.block.SmoothStoneBlock();
        BLOCK_SMOOTH_STONE.setRegistryName(MODID, "smooth_stone");
        ITEM_PERSONAL_SPACE_KEY = new PersonalSpaceKeyItem();
        GameRegistry.registerTileEntity(PortalTileEntity.class, "personalspace:personalPortal");
        EntityRegistry.registerModEntity(
                new net.minecraft.util.ResourceLocation(MODID, "portal_item"),
                PortalEntityItem.class, "PortalItem", 1, this, 64, 20, true);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        if (Loader.isModLoaded("appliedenergistics2-core")) {
            removeAe2Meteors();
        }

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @Optional.Method(modid = "appliedenergistics2-core")
    private void removeAe2Meteors() {
        IAppEngApi aeApi = AEApi.instance();
        if (aeApi == null) {
            return;
        }
        aeApi.registries().worldgen()
                .disableWorldGenForProviderID(IWorldGen.WorldGenType.METEORITES, PersonalWorldProvider.class);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this."
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void completeInit(FMLLoadCompleteEvent event) {
        proxy.completeInit(event);
        Config.validateBlocks();
        me.eigenraven.personalspace.world.OreGenInterceptor.installGeneratorOverride();
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        stopping = false;
        AsyncDimensionSaver.reset();
        proxy.serverAboutToStart(event);
        loadDimensionConfigs();
    }

    @Mod.EventHandler
    // register server commands in this event handler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
        event.registerServerCommand(new PersonalSpaceCommand());
    }

    public boolean restoreDimensionConfig(int dimId) {
        if (DimensionConfig.getForDimension(dimId, false) != null
                && DimensionManager.isDimensionRegistered(dimId)) return true;
        File root = DimensionManager.getCurrentSaveRootDirectory();
        if (root == null || !root.isDirectory()) return false;
        File[] dirs = root.listFiles();
        if (dirs == null) return false;
        String expectedDir = String.format("PERSONAL_DIM_%d", dimId);
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            File file = new File(dir, DIM_METADATA_FILE);
            if (file.isFile()) {
                try {
                    DimensionConfig cfg = new DimensionConfig();
                    int storedId = cfg.syncWithFile(file, false, 0);
                    if (storedId == dimId) {
                        cfg.setSaveDirOverride(dir.getName());
                        cfg.registerWithDimensionManager(dimId, false);
                        LOG.info("Restored PersonalSpace world {} from {}", dimId, dir.getName());
                        return true;
                    }
                    if (!dir.getName().equals(expectedDir)) continue;
                    LOG.warn("Metadata in {} has dimension ID {}; restoring directory identity {}",
                            file.getPath(), storedId, dimId);
                    cfg.syncWithFile(file, true, dimId);
                    cfg.setSaveDirOverride(dir.getName());
                    cfg.registerWithDimensionManager(dimId, false);
                    return true;
                } catch (Exception e) {
                    LOG.error("Couldn't restore personal dimension metadata from " + file.getPath(), e);
                }
            } else if (dir.getName().equals(expectedDir)) {
                DimensionConfig cfg = new DimensionConfig();
                cfg.setSaveDirOverride(dir.getName());
                cfg.registerWithDimensionManager(dimId, false);
                try {
                    saveDimensionConfig(dimId, cfg);
                } catch (IOException e) {
                    LOG.error("Couldn't recreate metadata for existing personal dimension " + dimId, e);
                    return false;
                }
                LOG.warn("Restored personal dimension {} from its existing save directory", dimId);
                return true;
            }
        }
        return false;
    }

    void loadDimensionConfigs() {
        try {
            deregisterDimensions(false);
            File saveDir = DimensionManager.getCurrentSaveRootDirectory();
            if (saveDir == null || !saveDir.isDirectory()) {
                return;
            }
            LOG.info("Searching for PS worlds at {}", saveDir.getPath());
            for (File dir : Objects.requireNonNull(saveDir.listFiles())) {
                if (!dir.isDirectory()) {
                    continue;
                }
                File dimConfig = new File(dir, DIM_METADATA_FILE);
                if (dimConfig.exists() && dimConfig.isFile()) {
                    try {
                        DimensionConfig dimCfg = new DimensionConfig();
                        int dimId = dimCfg.syncWithFile(dimConfig, false, 0);
                        dimCfg.setSaveDirOverride(dir.getName());
                        dimCfg.registerWithDimensionManager(dimId, false);
                        LOG.info("Loaded PersonalSpace world {} (at {})", dimId, dir.getName());
                    } catch (Exception e) {
                        LOG.error("Couldn't load personal dimension data from " + dimConfig.getPath(), e);
                    }
                    continue;
                }
                if (dir.getName().startsWith("PERSONAL_DIM_")) {
                    try {
                        int dimId = Integer.parseInt(dir.getName().substring("PERSONAL_DIM_".length()));
                        if (restoreDimensionConfig(dimId)) {
                            LOG.info("Recovered PersonalSpace world {} from directory {}", dimId, dir.getName());
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        LOG.warn("Ignoring invalid PersonalSpace dimension directory {}", dir.getName());
                    }
                }
                // Metadata not found, test for UW worlds.
                MutablePair<DimensionConfig, Integer> dc = DimensionConfig.fromUtilityWorldsWorld(dir.getName());
                if (dc != null) {
                    dc.getLeft().registerWithDimensionManager(dc.getRight(), false);
                    saveDimensionConfig(dc.getRight(), dc.getLeft());
                    LOG.info("Migrated world {} (at {}) from utilityworlds", dc.getRight(), dir.getName());
                }
            }
            bulkDimSettingsUpdate();
        } catch (Exception e) {
            LOG.error("Caught error while loading and registering personal space dimensions", e);
        }
    }

    /** Persist metadata before a newly-created dimension is exposed to Forge. */
    public void saveDimensionConfig(int dimId, DimensionConfig config) throws IOException {
        File saveDir = DimensionManager.getCurrentSaveRootDirectory();
        if (saveDir == null || !saveDir.isDirectory()) {
            throw new IOException("World save root is not available while creating dimension " + dimId);
        }
        saveDir = new File(saveDir, config.getSaveDir(dimId));
        if (!(saveDir.exists() && saveDir.isDirectory())) {
            if (!saveDir.mkdirs()) {
                throw new IOException(
                        "Couldn't create save directory for personal dimension " + config.getSaveDir(dimId));
            }
        }
        File dataFile = new File(saveDir, DIM_METADATA_FILE);
        // Forge's Configuration re-reads and rewrites the whole file, which is too slow
        // for the server thread during autosaves. Snapshot here and write off-thread;
        // the dirty flag is cleared now so this save is not queued twice, and any
        // setter called afterwards re-marks the config for the next save event.
        DimensionConfig snapshot = config.createSaveSnapshot();
        config.clearNeedsSavingForAsyncSave();
        AsyncDimensionSaver.saveAsync(dimId, config, snapshot, dataFile);
    }

    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote) return;
        if (!(event.getWorld().provider instanceof PersonalWorldProvider)) return;
        PersonalWorldProvider provider = (PersonalWorldProvider) event.getWorld().provider;
        DimensionConfig config = provider.getConfig();
        if (config == null) return;
        try {
            WorldInfo derivedInfo = event.getWorld().getWorldInfo();
            WorldInfo standaloneInfo = new WorldInfo(derivedInfo.cloneNBTCompound(null));
            // DerivedWorldInfo.cloneNBTCompound writes its own default fields, not the
            // delegate's values. Copy time/weather from the live getters which delegate correctly.
            standaloneInfo.setWorldTime(derivedInfo.getWorldTime());
            standaloneInfo.setRaining(derivedInfo.isRaining());
            standaloneInfo.setRainTime(derivedInfo.getRainTime());
            standaloneInfo.setThundering(derivedInfo.isThundering());
            standaloneInfo.setThunderTime(derivedInfo.getThunderTime());

            if (config.getDaylightCycle() == DimensionConfig.DaylightCycle.SUN) {
                standaloneInfo.setWorldTime(6000L);
            } else if (config.getDaylightCycle() == DimensionConfig.DaylightCycle.MOON) {
                standaloneInfo.setWorldTime(18000L);
            } else if (config.isTimeDataPersisted()) {
                config.applyTimeTo(standaloneInfo);
                LOG.info("Restored persisted time/weather for personal dimension {}", provider.getDimension());
            } else {
                standaloneInfo.setWorldTime(derivedInfo.getWorldTime());
                config.captureTimeFrom(standaloneInfo);
                LOG.info("Initialized time/weather from overworld for personal dimension {} (first load)", provider.getDimension());
            }

            if (!config.isWeatherEnabled()) {
                standaloneInfo.setRaining(false);
                standaloneInfo.setThundering(false);
            }

            if (worldInfoField == null) {
                worldInfoField = ReflectionHelper.findField(World.class, "worldInfo", "field_72986_A");
            }
            worldInfoField.set(event.getWorld(), standaloneInfo);
        } catch (Exception e) {
            LOG.error("Failed to decouple WorldInfo for dimension " + provider.getDimension(), e);
        }
    }

    /**
     * Personal dimensions are player-built spaces.  Do not let GregTech/GTCEu
     * inject ore veins (including oil-related deposits) or replacement stone
     * during Forge terrain generation.  The custom chunk generator already
     * creates the configured layers, so cancelling this event is safe here.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void protectPersonalPortals(net.minecraftforge.event.world.ExplosionEvent.Detonate event) {
        if (event.getWorld().provider instanceof PersonalWorldProvider) {
            event.getAffectedBlocks().removeIf(pos -> event.getWorld().getBlockState(pos).getBlock() == BLOCK_PORTAL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void preventPersonalDimensionOreGen(OreGenEvent.GenerateMinable event) {
        if (event.getWorld() != null
                && event.getWorld().provider instanceof PersonalWorldProvider) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void preventPersonalDimensionCustomWorldgen(PopulateChunkEvent.Populate event) {
        if (event.getWorld() != null
                && event.getWorld().provider instanceof PersonalWorldProvider) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    /**
     * GTCEu generates ore veins through DecorateBiomeEvent.Decorate with custom
     * event types. Block all decoration in personal dimensions to prevent ore
     * veins, oil deposits and other modded worldgen from spawning.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void preventPersonalDimensionDecorate(DecorateBiomeEvent.Decorate event) {
        if (event.getWorld() != null
                && event.getWorld().provider instanceof PersonalWorldProvider) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void syncPersonalWorldTime(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote
                || !(event.world.provider instanceof PersonalWorldProvider)) return;
        PersonalWorldProvider provider = (PersonalWorldProvider) event.world.provider;
        DimensionConfig.DaylightCycle cycle = provider.getConfig().getDaylightCycle();
        if (cycle == DimensionConfig.DaylightCycle.SUN) {
            event.world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
            event.world.setWorldTime(6000L);
        } else if (cycle == DimensionConfig.DaylightCycle.MOON) {
            event.world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
            event.world.setWorldTime(18000L);
        } else {
            event.world.getGameRules().setOrCreateGameRule("doDaylightCycle", "true");
            WorldServer overworld = DimensionManager.getWorld(0);
            if (overworld != null) event.world.setWorldTime(overworld.getWorldTime());
        }
    }

    @SubscribeEvent
    public void worldSave(WorldEvent.Save event) {
        if (event.getWorld().isRemote) return;
        if (!(event.getWorld().provider instanceof PersonalWorldProvider)) return;
        PersonalWorldProvider provider = (PersonalWorldProvider) event.getWorld().provider;
        DimensionConfig config = provider.getConfig();
        if (config == null) return;
        try {
            config.captureTimeFrom(event.getWorld().getWorldInfo());
            if (!config.needsSaving()) return;
            saveDimensionConfig(provider.getDimension(), config);
        } catch (Exception e) {
            LOG.fatal("Couldn't save personal dimension data for " + event.getWorld().provider.getDimension(), e);
        }
    }

    private static final String PLAYER_PERSISTED = "PlayerPersisted";
    private static final String LAST_PERSONAL_POSITION = "PersonalSpaceLastPosition";

    private static NBTTagCompound getPersistedPlayerData(EntityPlayerMP player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(PLAYER_PERSISTED, 10)) root.setTag(PLAYER_PERSISTED, new NBTTagCompound());
        return root.getCompoundTag(PLAYER_PERSISTED);
    }

    private static void rememberPersonalPosition(EntityPlayerMP player) {
        if (player.world == null || !(player.world.provider instanceof PersonalWorldProvider)) return;
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("dimension", player.world.provider.getDimension());
        data.setDouble("x", player.posX);
        data.setDouble("y", player.posY);
        data.setDouble("z", player.posZ);
        data.setFloat("yaw", player.rotationYaw);
        data.setFloat("pitch", player.rotationPitch);
        getPersistedPlayerData(player).setTag(LAST_PERSONAL_POSITION, data);
        LOG.debug("Saved last personal position for {}: dimension {}, {}, {}, {}",
                player.getName(), data.getInteger("dimension"), data.getDouble("x"), data.getDouble("y"), data.getDouble("z"));
    }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.ticksExisted % 20 == 0) rememberPersonalPosition(player);
    }

    @SubscribeEvent
    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) rememberPersonalPosition((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        NBTTagCompound data = getPersistedPlayerData(player);
        if (!data.hasKey(LAST_PERSONAL_POSITION, 10)) return;
        NBTTagCompound last = data.getCompoundTag(LAST_PERSONAL_POSITION);
        int dimension = last.getInteger("dimension");
        if (dimension == 0 || (!DimensionManager.isDimensionRegistered(dimension)
                && !restoreDimensionConfig(dimension))) {
            LOG.warn("Saved personal dimension {} is unavailable for player {}", dimension, player.getName());
            return;
        }
        player.mcServer.addScheduledTask(() -> {
            if (player.connection == null) return;
            WorldServer destination = DimensionManager.getWorld(dimension);
            if (destination == null) {
                DimensionManager.initDimension(dimension);
                destination = DimensionManager.getWorld(dimension);
            }
            if (destination == null || !(destination.provider instanceof PersonalWorldProvider)) {
                LOG.error("Could not restore personal dimension {} for player {}", dimension, player.getName());
                return;
            }
            LOG.info("Restoring player {} to personal dimension {} at {}, {}, {}", player.getName(), dimension,
                    last.getDouble("x"), last.getDouble("y"), last.getDouble("z"));
            player.mcServer.getPlayerList().transferPlayerToDimension(player, dimension,
                    new me.eigenraven.personalspace.world.PersonalTeleporter(destination,
                            (int) Math.floor(last.getDouble("x")), (int) Math.floor(last.getDouble("y")),
                            (int) Math.floor(last.getDouble("z")), last.getFloat("yaw"), last.getFloat("pitch")));
        });
    }
    @SubscribeEvent
    public void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        rememberPersonalPosition(player);
        WorldServer world = player.mcServer.getWorld(event.toDim);
        if (world == null || !(world.provider instanceof PersonalWorldProvider)) return;
        DimensionConfig config = ((PersonalWorldProvider) world.provider).getConfig();
        if (config == null || !config.isWeatherEnabled()) return;
        // Vanilla S2B(1) "begin rain" resets client rainingStrength to 0, and
        // WorldClient.updateWeatherBody is a no-op so it never recovers.
        // S2B(7) sets rainingStrength without the reset.
        float rain = world.getRainStrength(1.0F);
        float thunder = world.getThunderStrength(1.0F);
        if (rain > 0.0F) {
            player.connection.sendPacket(new SPacketChangeGameState(7, rain));
        }
        if (thunder > 0.0F) {
            player.connection.sendPacket(new SPacketChangeGameState(8, thunder));
        }
    }

    @SubscribeEvent
    public void worldUnload(WorldEvent.Unload event) {
        if (stopping) return;
        if (event.getWorld().isRemote) return;
        if (!(event.getWorld().provider instanceof PersonalWorldProvider)) return;
        PersonalWorldProvider provider = (PersonalWorldProvider) event.getWorld().provider;
        DimensionConfig config = provider.getConfig();
        if (config == null) return;
        if (config.getDaylightCycle() == DimensionConfig.DaylightCycle.CYCLE) {
            config.captureTimeFrom(event.getWorld().getWorldInfo());
        }
        config.markDirty();
        try {
            saveDimensionConfig(provider.getDimension(), config);
        } catch (IOException e) {
            LOG.error("Couldn't save dimension " + provider.getDimension() + " on unload", e);
        }
    }

    private static final String NATURA_MODID = "Natura";
    private static final String NATURA_IMC = "set-worldgen-overrides";
    private static final String NATURA_IMC_DIMS = "dimensions";
    private static final String NATURA_IMC_SETS = "settings";
    private static final int NATURA_DIM_WORLDGEN_CROP_BIT = 1;
    private static final int NATURA_DIM_WORLDGEN_CLOUD_BIT = 2;
    private static final int NATURA_DIM_WORLDGEN_TREE_BIT = 4;

    private static final String THAUMCRAFT_MODID = "Thaumcraft";
    private static Field thaumcraftDimensionBlacklist = null;

    private static int naturaConfigForDim(DimensionConfig config) {
        int gen = 0;
        if (config.isGeneratingVegetation()) {
            gen |= NATURA_DIM_WORLDGEN_CROP_BIT;
            gen |= NATURA_DIM_WORLDGEN_CLOUD_BIT;
        }
        if (config.isGeneratingTrees()) {
            gen |= NATURA_DIM_WORLDGEN_TREE_BIT;
        }
        return gen;
    }

    @SuppressWarnings("unchecked")
    private static HashMap<Integer, Integer> getThaumcraftDimensionBlacklist() {
        if (Loader.isModLoaded(THAUMCRAFT_MODID)) {
            try {
                if (thaumcraftDimensionBlacklist == null) {
                    Class<?> klass = Class.forName("thaumcraft.common.lib.world.ThaumcraftWorldGenerator");
                    thaumcraftDimensionBlacklist = klass.getField("dimensionBlacklist");
                }
                return (HashMap<Integer, Integer>) thaumcraftDimensionBlacklist.get(null);
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private void bulkDimSettingsUpdate() {
        TIntArrayList dimIds = new TIntArrayList();
        TIntArrayList dimNaturaGens = new TIntArrayList();
        HashMap<Integer, Integer> tcBlacklist = getThaumcraftDimensionBlacklist();
        CommonProxy.getDimensionConfigObjects(false).forEachEntry((dimId, config) -> {
            if (config == null) {
                return true;
            }
            dimIds.add(dimId);
            dimNaturaGens.add(naturaConfigForDim(config));
            if (tcBlacklist != null) {
                if (config.isGeneratingTrees()) {
                    tcBlacklist.remove(dimId);
                } else {
                    tcBlacklist.put(dimId, 1); // disable vegetation but not aura node functionality
                }
            }
            return true;
        });
        if (Loader.isModLoaded(NATURA_MODID) && !dimIds.isEmpty()) {
            NBTTagCompound naturaImc = new NBTTagCompound();
            naturaImc.setIntArray(NATURA_IMC_DIMS, dimIds.toArray());
            naturaImc.setIntArray(NATURA_IMC_SETS, dimNaturaGens.toArray());
            FMLInterModComms.sendRuntimeMessage(this, NATURA_MODID, NATURA_IMC, naturaImc);
        }
    }

    public void onDimSettingsChangeServer(int dimId) {
        DimensionConfig config = DimensionConfig.getForDimension(dimId, false);
        if (config == null) {
            return;
        }
        WorldServer liveWorld = DimensionManager.getWorld(dimId);
        if (liveWorld != null && liveWorld.provider instanceof PersonalWorldProvider) {
            liveWorld.getGameRules().setOrCreateGameRule("doDaylightCycle",
                    config.getDaylightCycle() == DimensionConfig.DaylightCycle.CYCLE ? "true" : "false");
        }
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) for (EntityPlayerMP online : server.getPlayerList().getPlayers()) {
            me.eigenraven.personalspace.net.Packets.INSTANCE.sendWorldList().sendToPlayer(online);
        }
        HashMap<Integer, Integer> tcBlacklist = getThaumcraftDimensionBlacklist();
        if (tcBlacklist != null) {
            if (config.isGeneratingTrees()) {
                tcBlacklist.remove(dimId);
            } else {
                tcBlacklist.put(dimId, 0);
            }
        }
        if (Loader.isModLoaded(NATURA_MODID)) {
            NBTTagCompound naturaImc = new NBTTagCompound();
            naturaImc.setIntArray(NATURA_IMC_DIMS, new int[] { dimId });
            naturaImc.setIntArray(NATURA_IMC_SETS, new int[] { naturaConfigForDim(config) });
            FMLInterModComms.sendRuntimeMessage(this, NATURA_MODID, NATURA_IMC, naturaImc);
        }
    }

    private static boolean thermosLogged = false;

    public static boolean isInThermos() {
        try {
            Class.forName("thermos.ThermosRemapper");
            if (!thermosLogged) {
                thermosLogged = true;
                LOG.warn("Thermos detected, applying workarounds");
            }
            return true;
        } catch (ClassNotFoundException e) {
            if (!thermosLogged) {
                thermosLogged = true;
                LOG.info("Thermos not detected");
            }
            return false;
        }
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        proxy.serverStarted(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        stopping = true;
        proxy.serverStopping(event);
        TIntObjectHashMap<DimensionConfig> configs = CommonProxy.getDimensionConfigObjects(false);
        configs.forEachEntry((dimId, dimCfg) -> {
            if (dimCfg == null) return true;
            WorldServer world = DimensionManager.getWorld(dimId);
            if (world != null && !world.isRemote) {
                dimCfg.captureTimeFrom(world.getWorldInfo());
                dimCfg.markDirty();
            }
            return true;
        });
        // Save all dirty configs
        configs.forEachEntry((dimId, dimCfg) -> {
            if (dimCfg == null || !dimCfg.needsSaving()) return true;
            try {
                saveDimensionConfig(dimId, dimCfg);
            } catch (IOException e) {
                LOG.error("Couldn't save dimension " + dimId, e);
            }
            return true;
        });
        // Block until every queued async config write has reached the disk.
        AsyncDimensionSaver.flushAndWait();
    }

    private void deregisterDimensions(boolean isClient) {
        synchronized (CommonProxy.getDimensionConfigObjects(isClient)) {
            CommonProxy.getDimensionConfigObjects(isClient).forEachEntry((dimId, dimCfg) -> {
                if (DimensionManager.isDimensionRegistered(dimId)) {
                    FMLLog.info("Deregistering PersonalSpace dimension %d", dimId);
                    DimensionManager.unregisterDimension(dimId);
                }
                return true;
            });
            CommonProxy.getDimensionConfigObjects(isClient).clear();
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        stopping = false;
        proxy.serverStopped(event);
        deregisterDimensions(false);
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            deregisterDimensions(true);
            synchronized (CommonProxy.getDimensionConfigObjects(true)) {
                CommonProxy.getDimensionConfigObjects(true).clear();
            }
        }
    }

    @SubscribeEvent
    public void netEventHandler(FMLNetworkEvent.CustomNetworkEvent event) {
        if (event.getWrappedEvent() instanceof NetworkHandshakeEstablished) {
            NetworkHandshakeEstablished hs = (NetworkHandshakeEstablished) event.getWrappedEvent();
            if (hs.netHandler instanceof NetHandlerPlayServer) {
                NetHandlerPlayServer netHandler = (NetHandlerPlayServer) hs.netHandler;
                PacketCustom pkt = Packets.INSTANCE.sendWorldList();
                netHandler.sendPacket(pkt.toPacket());
            }
        }
    }

    @SubscribeEvent
    public void clientDisconnectionHandler(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        // Integrated server and client share the global DimensionManager. Do not
        // unregister dimensions during local disconnect; serverStopped performs cleanup.
        if (event.getManager().isLocalChannel()) {
            return;
        }
        deregisterDimensions(true);
        deregisterDimensions(false);
    }

    @Mod.EventBusSubscriber(modid = MODID)
    public static class RegistryMigrationHandler {
        @SubscribeEvent
        public static void registerBlocks(RegistryEvent.Register<Block> event) {
            event.getRegistry().register(BLOCK_PORTAL);
            event.getRegistry().register(BLOCK_TELEPORT_POINT);
            event.getRegistry().register(BLOCK_SMOOTH_STONE);
        }

        @SubscribeEvent
        public static void registerItems(RegistryEvent.Register<Item> event) {
            event.getRegistry().register(
                    new PortalItem(BLOCK_PORTAL).setRegistryName(BLOCK_PORTAL.getRegistryName()));
            event.getRegistry().register(new me.eigenraven.personalspace.block.TeleportPointItem(BLOCK_TELEPORT_POINT)
                    .setRegistryName(BLOCK_TELEPORT_POINT.getRegistryName()));
            event.getRegistry().register(new net.minecraft.item.ItemBlock(BLOCK_SMOOTH_STONE)
                    .setRegistryName(BLOCK_SMOOTH_STONE.getRegistryName()));
            event.getRegistry().register(ITEM_PERSONAL_SPACE_KEY);
        }

        @SubscribeEvent
        public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
            for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getMappings()) {
                switch (mapping.key.toString()) {
                    case "utilityworlds:uw_portal_mining": mapping.remap(BLOCK_PORTAL); break;
                    case "utilityworlds:uw_portal_void": mapping.remap(BLOCK_PORTAL); break;
                    case "utilityworlds:uw_portal_garden": mapping.remap(BLOCK_PORTAL); break;
                    case "utilityworlds:uw_portal_return": mapping.remap(BLOCK_PORTAL); break;
                    default: break;
                }
            }
        }

        @SubscribeEvent
        public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
            for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getMappings()) {
                switch (mapping.key.toString()) {
                    case "utilityworlds:uw_portal_mining": mapping.remap(Item.getItemFromBlock(BLOCK_PORTAL)); break;
                    case "utilityworlds:uw_portal_void": mapping.remap(Item.getItemFromBlock(BLOCK_PORTAL)); break;
                    case "utilityworlds:uw_portal_garden": mapping.remap(Item.getItemFromBlock(BLOCK_PORTAL)); break;
                    case "utilityworlds:uw_portal_return": mapping.remap(Item.getItemFromBlock(BLOCK_PORTAL)); break;
                    default: break;
                }
            }
        }
    }

    public static void debug(String message) {
        LOG.debug(message);
    }

    public static void info(String message) {
        LOG.info(message);
    }

    public static void warn(String message) {
        LOG.warn(message);
    }

    public static void error(String message) {
        LOG.error(message);
    }
}
