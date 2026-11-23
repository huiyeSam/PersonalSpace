package me.eigenraven.personalspace;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import me.eigenraven.personalspace.config.Config;
import me.eigenraven.personalspace.net.Packets;
import me.eigenraven.personalspace.world.DimensionConfig;
import me.eigenraven.personalspace.world.PersonalTeleporter;

public class PersonalSpaceCommand extends CommandBase {

    @Override
    public String getName() {
        return "pspace";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Collections.singletonList("psp");
    }

    /**
     * Return the required permission level for this command.
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.pspace.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || sender == null) {
            throw new WrongUsageException("commands.pspace.usage");
        }
        if (args[0].equalsIgnoreCase("debug")) {
            if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
                sender.sendMessage(new TextComponentTranslation("commands.pspace.debug.status", Config.debugLogging));
                return;
            }
            if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true")) {
                Config.setDebugLogging(true);
            } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false")) {
                Config.setDebugLogging(false);
            } else {
                throw new WrongUsageException("commands.pspace.usage");
            }
            sender.sendMessage(new TextComponentTranslation("commands.pspace.debug.status", Config.debugLogging));
            return;
        }
        if (args[0].equalsIgnoreCase("ls")) {
            CommonProxy.getDimensionConfigObjects(false).forEachEntry((dimId, dimCfg) -> {
                if (dimCfg == null) {
                    return true;
                }
                java.io.File root = DimensionManager.getCurrentSaveRootDirectory();
                java.io.File worldDir = root == null ? null : new java.io.File(root, dimCfg.getSaveDir(dimId));
                sender.sendMessage(new TextComponentString(String.format("%d: %s", dimId, worldDir == null ? dimCfg.getSaveDir(dimId) : worldDir.getAbsolutePath())));
                return true;
            });
            return;
        }
        if (args[0].equalsIgnoreCase("where")) {
            if (args.length < 2) {
                throw new WrongUsageException("commands.pspace.usage");
            }
            EntityPlayerMP player = getPlayer(server, sender, args[1]);
            sender.sendMessage(
                    new TextComponentTranslation(
                            "commands.pspace.where",
                            sender.getName(),
                            player.world.provider.getDimension()));
            return;
        }
        if (args[0].equalsIgnoreCase("tpx")) {
            if (args.length < 3) {
                throw new WrongUsageException("commands.pspace.usage");
            }
            EntityPlayerMP player = getPlayer(server, sender, args[1]);
            int dim = parseInt(args[2]);
            if (!DimensionManager.isDimensionRegistered(dim)) {
                if (PersonalSpaceMod.INSTANCE == null || !PersonalSpaceMod.INSTANCE.restoreDimensionConfig(dim)) {
                    throw new CommandException("commands.pspace.badDimension");
                }
            }
            WorldServer dimWorld = DimensionManager.getWorld(dim);
            if (dimWorld == null) {
                DimensionManager.initDimension(dim);
                dimWorld = DimensionManager.getWorld(dim);
                if (dimWorld == null) {
                    throw new CommandException("commands.pspace.badDimension");
                }
            }
            BlockPos target = dimWorld.getSpawnPoint();
            target = dimWorld.getTopSolidOrLiquidBlock(target).up();
            double x = target.getX(), y = target.getY(), z = target.getZ();
            if (args.length >= 6) {
                BlockPos coords = sender.getPosition();
                x = parseDouble(coords.getX(), args[3], false);
                y = parseDouble(coords.getY(), args[4], false);
                z = parseDouble(coords.getZ(), args[5], false);
            }
            DimensionConfig pointConfig = DimensionConfig.getForDimension(dim, false);
            BlockPos point = pointConfig == null ? null : pointConfig.getTeleportPoint();
            if (args.length < 6 && point != null
                    && dimWorld.getBlockState(point).getBlock() == PersonalSpaceMod.BLOCK_TELEPORT_POINT) {
                x = point.getX();
                y = point.getY() + 1;
                z = point.getZ();
            }
            PersonalTeleporter tp = new PersonalTeleporter(dimWorld, (int) x, (int) y, (int) z);
            player.mcServer.getPlayerList().transferPlayerToDimension(player, dim, tp);
            sender.sendMessage(
                    new TextComponentTranslation("commands.pspace.tpx", sender.getName(), dim, x, y, z));
            return;
        }
        if (args[0].equalsIgnoreCase("give-portal")) {
            if (args.length < 3) {
                throw new WrongUsageException("commands.pspace.usage");
            }
            EntityPlayerMP player = getPlayer(server, sender, args[1]);
            int dim = parseInt(args[2]);
            if (!DimensionManager.isDimensionRegistered(dim)) {
                if (PersonalSpaceMod.INSTANCE == null || !PersonalSpaceMod.INSTANCE.restoreDimensionConfig(dim)) {
                    throw new CommandException("commands.pspace.badDimension");
                }
            }
            WorldServer dimWorld = DimensionManager.getWorld(dim);
            if (dimWorld == null) {
                DimensionManager.initDimension(dim);
                dimWorld = DimensionManager.getWorld(dim);
                if (dimWorld == null) {
                    throw new CommandException("commands.pspace.badDimension");
                }
            }
            BlockPos target = dimWorld.getSpawnPoint();
            target = dimWorld.getTopSolidOrLiquidBlock(target).up();
            double x = target.getX(), y = target.getY(), z = target.getZ();
            if (args.length >= 6) {
                BlockPos coords = sender.getPosition();
                x = parseDouble(coords.getX(), args[3], false);
                y = parseDouble(coords.getY(), args[4], false);
                z = parseDouble(coords.getZ(), args[5], false);
            }
            ItemStack itemstack = new ItemStack(PersonalSpaceMod.BLOCK_PORTAL, 1, 0);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("active", true);
            tag.setIntArray("target", new int[] { dim, (int) x, (int) y, (int) z });
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setBoolean("CommandLink", true);
            itemTag.setTag("PersonalSpacePortal", tag);
            itemstack.setTagCompound(itemTag);
            EntityItem entityitem = player.dropItem(itemstack, false);
            entityitem.setPickupDelay(0);
            entityitem.setThrower(player.getName());
            return;
        }
        if (args[0].equalsIgnoreCase("allow-worldgen-change")) {
            if (args.length < 2) {
                throw new WrongUsageException("commands.pspace.usage");
            }
            int dim = parseInt(args[1]);
            DimensionConfig cfg = DimensionConfig.getForDimension(dim, false);
            if (cfg == null) {
                throw new CommandException("commands.pspace.badDimension");
            }
            cfg.setAllowGenerationChanges(true);
            Packets.INSTANCE.sendWorldList().sendToClients();
            sender.sendMessage(
                    new TextComponentTranslation(
                            "commands.pspace.allow-worldgen-change",
                            sender.getName(),
                            dim));
            return;
        }
        if (args[0].equalsIgnoreCase("reload-config")) {
            java.io.File configFile = Config.getSavedConfigFile();
            if (configFile == null) {
                throw new CommandException("commands.pspace.reload-config.fail");
            }
            Config.synchronizeConfiguration(configFile);
            Config.validateBlocks();
            Packets.INSTANCE.sendWorldList().sendToClients();
            sender.sendMessage(new TextComponentTranslation("commands.pspace.reload-config.success"));
            return;
        }

        throw new WrongUsageException("commands.pspace.usage");
    }

    /**
     * Adds the strings available in this command to the given list of tab completion options.
     */
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        switch (args.length) {
            case 0:
            case 1:
                return getListOfStringsMatchingLastWord(
                        args,
                        "ls",
                        "where",
                        "tpx",
                        "give-portal",
                        "allow-worldgen-change",
                        "reload-config",
                        "debug");
            case 2:
                return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
            default:
                return null;
        }
    }

    

    /**
     * Return whether the specified command parameter index is a username parameter.
     */
    public boolean isUsernameIndex(String[] args, int idx) {
        return idx == 1;
    }
}
