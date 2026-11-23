package me.eigenraven.personalspace.block;

import java.util.Random;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ITickable;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.event.world.BlockEvent;

import me.eigenraven.personalspace.CommonProxy;
import me.eigenraven.personalspace.DebugLog;
import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.config.Config;
import me.eigenraven.personalspace.net.Packets;
import me.eigenraven.personalspace.world.DimensionConfig;
import me.eigenraven.personalspace.world.PersonalTeleporter;
import me.eigenraven.personalspace.world.PersonalWorldProvider;

public class PortalTileEntity extends TileEntity implements ITickable {

    public static final EnumFacing DEFAULT_FACING = EnumFacing.NORTH;

    // Client-side book animation state (matches TileEntityEnchantmentTable fields)
    public int tickCount;
    public float pagePosition;
    public float prevPagePosition;
    public float pageTarget;
    public float pageVelocity;
    private static final Random RAND = new Random();

    public boolean active = false;
    public int targetDimId = 0;
    public int targetPosX = 8;
    public int targetPosY = 8;
    public int targetPosZ = 8;
    public EnumFacing targetFacing = DEFAULT_FACING;
    public EnumFacing facing = DEFAULT_FACING;

    public PortalTileEntity() {}

    public void update() {
        if (getWorld() == null || !getWorld().isRemote) return;

        prevPagePosition = pagePosition;

        if (RAND.nextInt(40) == 0) {
            float prev = pageTarget;
            do {
                pageTarget += (float) (RAND.nextInt(4) - RAND.nextInt(4));
            } while (prev == pageTarget);
        }

        ++tickCount;
        float delta = (pageTarget - pagePosition) * 0.4F;
        delta = MathHelper.clamp(delta, -0.2F, 0.2F);
        pageVelocity += (delta - pageVelocity) * 0.9F;
        pagePosition += pageVelocity;
    }

    public void readLinkFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("active")) {
            this.active = tag.getBoolean("active");
        }
        if (tag.hasKey("target")) {
            int[] target = tag.getIntArray("target");
            if (target.length >= 4) {
                this.targetDimId = target[0];
                this.targetPosX = target[1];
                this.targetPosY = target[2];
                this.targetPosZ = target[3];
            }
        }
        if (tag.hasKey("facing")) {
            this.facing = EnumFacing.getFront(tag.getInteger("facing"));
        }
        if (tag.hasKey("targetFacing")) {
            this.targetFacing = EnumFacing.getFront(tag.getInteger("targetFacing"));
        }
        if (this.facing == null) this.facing = DEFAULT_FACING;
        if (this.targetFacing == null) this.targetFacing = DEFAULT_FACING;
    }

    public NBTTagCompound writeLinkToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("active", this.active);
        tag.setIntArray("target", new int[] { this.targetDimId, this.targetPosX, this.targetPosY, this.targetPosZ });
        tag.setInteger("facing", this.facing == null ? DEFAULT_FACING.ordinal() : this.facing.ordinal());
        tag.setInteger("targetFacing", this.targetFacing == null ? DEFAULT_FACING.ordinal() : this.targetFacing.ordinal());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("x") || tag.hasKey("y") || tag.hasKey("z")) {
            super.readFromNBT(tag);
        }
        boolean isLegacy = false;
        if (tag.hasKey("isReturnPortal")) {
            isLegacy = true;
            if (tag.getBoolean("isReturnPortal")) {
                this.targetDimId = 0;
            }
        }
        if (tag.hasKey("remoteDimensionId")) {
            isLegacy = true;
            this.targetDimId = tag.getInteger("remoteDimensionId");
        }
        if (tag.hasKey("remotePos")) {
            isLegacy = true;
            int[] pos_array = tag.getIntArray("remotePos");
            if (pos_array.length >= 3) {
                this.targetPosX = pos_array[0];
                this.targetPosY = pos_array[1];
                this.targetPosZ = pos_array[2];
            }
        }
        if (tag.hasKey("remoteDir")) {
            isLegacy = true;
            byte remoteDir = tag.getByte("remoteDir");
            switch (remoteDir) {
                case 0:
                    this.facing = EnumFacing.NORTH;
                    break;
                case 1:
                    this.facing = EnumFacing.EAST;
                    break;
                case 3:
                    this.facing = EnumFacing.WEST;
                    break;
                default:
                    this.facing = EnumFacing.SOUTH;
                    break;
            }
        }
        if (tag.hasKey("localDimensionId")) {
            isLegacy = true;
        }
        if (tag.hasKey("localDir")) {
            isLegacy = true;
        }

        if (tag.hasKey("active")) {
            this.active = tag.getBoolean("active");
        }
        if (tag.hasKey("target")) {
            int[] t_array = tag.getIntArray("target");
            if (t_array.length >= 4) {
                this.targetDimId = t_array[0];
                this.targetPosX = t_array[1];
                this.targetPosY = t_array[2];
                this.targetPosZ = t_array[3];
            }
        }
        if (tag.hasKey("facing")) {
            facing = EnumFacing.getFront(tag.getInteger("facing"));
        }
        if (tag.hasKey("targetFacing")) {
            targetFacing = EnumFacing.getFront(tag.getInteger("targetFacing"));
        }

        if (isLegacy) {
            this.active = true;
            PersonalSpaceMod.LOG.info(
                    "Migrated old UW portal to dim {} : target {},{},{}",
                    targetDimId,
                    targetPosX,
                    targetPosY,
                    targetPosZ);
            markDirty();
        }
        if (facing == null) {
            facing = EnumFacing.NORTH;
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (getWorld() != null) {
            getWorld().notifyBlockUpdate(pos, getWorld().getBlockState(pos), getWorld().getBlockState(pos), 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("active", this.active);
        tag.setIntArray("target", new int[] { this.targetDimId, this.targetPosX, this.targetPosY, this.targetPosZ });
        tag.setInteger("facing", this.facing.ordinal());
        tag.setInteger("targetFacing", this.targetFacing.ordinal());
        return tag;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new SPacketUpdateTileEntity(pos, 1, tag);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return tag;
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (getWorld() != null && getWorld().isRemote) {
            readFromNBT(pkt.getNbtCompound());
            PersonalSpaceMod.proxy.closePortalGui(this);
        }
    }

    public void transport(EntityPlayerMP player) {
        if (getWorld().isRemote || player == null) return;
        if (!this.active) {
            PersonalSpaceMod.LOG.warn("Player {} activated inactive portal at {} in dimension {}",
                    player.getName(), pos, getWorld().provider.getDimension());
            return;
        }
        if (this.targetDimId == getWorld().provider.getDimension()) {
            PersonalSpaceMod.LOG.error("Portal at {} in dimension {} targets itself; teleport cancelled", pos,
                    this.targetDimId);
            return;
        }
        DimensionConfig cfg = DimensionConfig.getForDimension(this.targetDimId, false);
        if (!DimensionManager.isDimensionRegistered(this.targetDimId) && cfg == null
                && PersonalSpaceMod.INSTANCE != null) {
            PersonalSpaceMod.INSTANCE.restoreDimensionConfig(this.targetDimId);
            cfg = DimensionConfig.getForDimension(this.targetDimId, false);
        }
        if (!DimensionManager.isDimensionRegistered(this.targetDimId) && cfg != null) {
            PersonalSpaceMod.LOG.warn("Portal target dimension {} was not registered; restoring it from saved metadata", this.targetDimId);
            cfg.registerWithDimensionManager(this.targetDimId, false);
        }
        if (!DimensionManager.isDimensionRegistered(this.targetDimId)) {
            PersonalSpaceMod.LOG.warn(
                    "Player {} attempted to teleport to dimension {} which is not registered yet",
                    player.getGameProfile(),
                    this.targetDimId);
            return;
        }

        WorldServer destination = DimensionManager.getWorld(this.targetDimId);
        if (destination == null) {
            DimensionManager.initDimension(this.targetDimId);
            destination = DimensionManager.getWorld(this.targetDimId);
        }
        if (destination == null) {
            PersonalSpaceMod.LOG.error("Portal target dimension {} failed to initialize for player {}",
                    this.targetDimId, player.getName());
            return;
        }
        PersonalTeleporter tp;
        if (this.targetDimId == 0 && getWorld().provider instanceof PersonalWorldProvider) {
            int[] entry = me.eigenraven.personalspace.world.PersonalReturnData.get(getWorld()).get(player.getUniqueID());
            BlockPos exit = entry == null ? destination.getSpawnPoint().up() : new BlockPos(entry[0], entry[1], entry[2]);
            tp = new PersonalTeleporter(destination, exit.getX(), exit.getY(), exit.getZ());
        } else {
            // Store each player's overworld entrance in this private dimension's saved data.
            me.eigenraven.personalspace.world.PersonalReturnData.get(destination)
                    .set(player.getUniqueID(), MathHelper.floor(player.posX), MathHelper.floor(player.posY),
                            MathHelper.floor(player.posZ));
            tp = new PersonalTeleporter(this, destination);
        }
        if (cfg != null) {
            net.minecraft.util.math.BlockPos point = cfg.getTeleportPoint();
            if (point != null && destination.getBlockState(point).getBlock() == PersonalSpaceMod.BLOCK_TELEPORT_POINT) {
                tp = new PersonalTeleporter(destination, point.getX(), point.getY() + 1, point.getZ());
            }
        }

        player.mcServer.getPlayerList().transferPlayerToDimension(player, this.targetDimId, tp);
    }

    public int getTargetTeleportX() {
        return targetPosX + targetFacing.getDirectionVec().getX();
    }

    public int getTargetTeleportY() {
        return targetPosY + targetFacing.getDirectionVec().getY();
    }

    public int getTargetTeleportZ() {
        return targetPosZ + targetFacing.getDirectionVec().getZ();
    }

    public void linkOtherPortal(boolean spawnNewPortal, EntityPlayerMP player) {
        if (!this.active) {
            return;
        }
        if (getWorld().isRemote) {
            return;
        }
        WorldServer otherWorld = DimensionManager.getWorld(this.targetDimId);
        if (otherWorld == null) {
            DimensionManager.initDimension(this.targetDimId);
            otherWorld = DimensionManager.getWorld(this.targetDimId);
        }
        if (otherWorld == null) {
            PersonalSpaceMod.LOG.fatal("Couldn't initialize world {}", this.targetDimId);
            return;
        }
        int otherX = targetPosX, otherY = targetPosY, otherZ = targetPosZ;
        searchloop: for (otherX = targetPosX - 1; otherX <= targetPosX + 1; otherX++) {
            for (otherY = targetPosY - 1; otherY <= targetPosY + 1; otherY++) {
                if (otherY < 0 || otherY > otherWorld.getHeight()) continue;
                for (otherZ = targetPosZ - 1; otherZ <= targetPosZ + 1; otherZ++) {
                    BlockPos searchPos = new BlockPos(otherX, otherY, otherZ);
                    if (!otherWorld.isBlockLoaded(searchPos)) {
                        otherWorld.getChunkProvider().loadChunk(otherX >> 4, otherZ >> 4);
                    }
                    if (otherWorld.getBlockState(searchPos).getBlock() instanceof PortalBlock) {
                        break searchloop;
                    }
                }
            }
        }
        PortalTileEntity otherPortal = null;

        BlockPos otherPos = new BlockPos(otherX, otherY, otherZ);
        if (otherWorld.getBlockState(otherPos).getBlock() == PersonalSpaceMod.BLOCK_PORTAL) {
            TileEntity wte = otherWorld.getTileEntity(otherPos);
            if (wte instanceof PortalTileEntity) {
                otherPortal = (PortalTileEntity) wte;
            }
        } else if (spawnNewPortal) {
            otherX = targetPosX;
            otherY = targetPosY;
            otherZ = targetPosZ;
            BlockPos spawnPos = new BlockPos(otherX, otherY, otherZ);
            otherWorld.setBlockState(spawnPos, PersonalSpaceMod.BLOCK_PORTAL.getDefaultState(), 3);
            TileEntity created = otherWorld.getTileEntity(spawnPos);
            if (created instanceof PortalTileEntity) otherPortal = (PortalTileEntity) created;
        }
        if (otherPortal != null) {
            otherPortal.active = true;
            DimensionConfig otherPortalDimCfg = DimensionConfig.getForDimension(otherPortal.targetDimId, false);
            if (otherPortal.targetDimId != getWorld().provider.getDimension() && otherPortalDimCfg != null) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("chat.personalWorld.relinked.error"));
                }
                return;
            }
            otherPortal.targetDimId = getWorld().provider.getDimension();
            otherPortal.targetPosX = pos.getX();
            otherPortal.targetPosY = pos.getY();
            otherPortal.targetPosZ = pos.getZ();
            otherPortal.targetFacing = facing;
            otherPortal.markDirty();
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("chat.personalWorld.relinked", targetDimId));
            }
            PersonalSpaceMod.LOG.info(
                    "Linked portal at {}:{},{},{} to {}:{},{},{}",
                    targetDimId,
                    otherX,
                    otherY,
                    otherZ,
                    getWorld().provider.getDimension(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ());
        }
    }

    public void updateSettings(EntityPlayerMP player, DimensionConfig unsafeConfig) {
        if (getWorld().isRemote || player == null) return;
        if (!getWorld().canMineBlockBody(player, pos)) {
            player.sendMessage(new TextComponentTranslation("chat.personalWorld.denied"));
            return;
        }
        if (Config.useBlockEventChecks) {
            BlockEvent.BreakEvent check = new BlockEvent.BreakEvent(getWorld(), pos, getWorld().getBlockState(pos), player);
            if (MinecraftForge.EVENT_BUS.post(check)) {
                player.sendMessage(new TextComponentTranslation("chat.personalWorld.denied"));
                return;
            }
        }
        if (getWorld().provider instanceof PersonalWorldProvider || this.active) {
            // The private dimension owns its settings. Only sky presentation may change after creation.
            int dim = getWorld().provider instanceof PersonalWorldProvider
                    ? getWorld().provider.getDimension() : this.targetDimId;
            DimensionConfig real = DimensionConfig.getForDimension(dim, false);
            if (real == null) return;
            real.setSkyColor(unsafeConfig.getSkyColor());
            real.setStarBrightness(unsafeConfig.getStarBrightness());
            real.setWeatherEnabled(unsafeConfig.isWeatherEnabled());
            real.setDaylightCycle(unsafeConfig.getDaylightCycle());
            real.setCloudsEnabled(unsafeConfig.isCloudsEnabled());
            real.setSkyType(unsafeConfig.getSkyType());
            PersonalSpaceMod.INSTANCE.onDimSettingsChangeServer(dim);
            return;
        }
        if (getWorld().provider.getDimension() != 0 || this.active) {
            player.sendMessage(new TextComponentTranslation("chat.personalWorld.settingsLocked"));
            return;
        }
        if (unsafeConfig.getLayers().isEmpty()
                || !DimensionConfig.canUseLayers(unsafeConfig.getLayersAsString(), false)) {
            player.sendMessage(new TextComponentTranslation("chat.personalWorld.badLayers"));
            return;
        }
        if (!DimensionConfig.canUseBiome(unsafeConfig.getBiomeId(), false)) unsafeConfig.setBiomeId("Plains");
        DimensionConfig created = new DimensionConfig();
        created.copyFrom(unsafeConfig, false, true, true);
        created.setAllowGenerationChanges(false);
        int newId = DimensionConfig.nextFreeDimId();
        try {
            PersonalSpaceMod.INSTANCE.saveDimensionConfig(newId, created);
            created.registerWithDimensionManager(newId, false);
            if (DimensionManager.getWorld(newId) == null) DimensionManager.initDimension(newId);
            if (DimensionManager.getWorld(newId) == null) throw new IllegalStateException("Dimension initialization failed");
        } catch (Exception e) {
            PersonalSpaceMod.LOG.error("Could not create personal dimension " + newId, e);
            if (DimensionManager.isDimensionRegistered(newId)) DimensionManager.unregisterDimension(newId);
            CommonProxy.getDimensionConfigObjects(false).remove(newId);
            player.sendMessage(new TextComponentTranslation("chat.personalWorld.createFailed"));
            return;
        }
        this.active = true;
        this.targetDimId = newId;
        this.targetPosY = created.getGroundLevel() + 1;
        markDirty();
        linkOtherPortal(true, player);
        Packets.INSTANCE.sendWorldList().sendToClients();
        player.sendMessage(new TextComponentTranslation("chat.personalWorld.created"));
    }

}
