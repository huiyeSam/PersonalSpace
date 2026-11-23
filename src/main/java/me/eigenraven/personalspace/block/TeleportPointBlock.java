package me.eigenraven.personalspace.block;

import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.world.DimensionConfig;
import me.eigenraven.personalspace.world.PersonalTeleporter;
import me.eigenraven.personalspace.world.PersonalWorldProvider;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class TeleportPointBlock extends Block {
    public TeleportPointBlock() {
        super(Material.ROCK);
        setUnlocalizedName("personalspace.teleportPoint");
        setHardness(3.0F);
        setResistance(20.0F);
        setCreativeTab(CreativeTabs.TRANSPORTATION);
        setLightOpacity(0);
    }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return new AxisAlignedBB(0, 0, 0, 1, 0.75, 1);
    }

    public static boolean canPlacePoint(World world) {
        if (!(world.provider instanceof PersonalWorldProvider)) return false;
        if (world.isRemote) return true;
        DimensionConfig cfg = ((PersonalWorldProvider) world.provider).getConfig();
        BlockPos old = cfg.getTeleportPoint();
        return old == null || world.getBlockState(old).getBlock() != PersonalSpaceMod.BLOCK_TELEPORT_POINT;
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && canPlacePoint(world);
    }

    private static void save(World world, DimensionConfig cfg) {
        try {
            PersonalSpaceMod.INSTANCE.saveDimensionConfig(world.provider.getDimension(), cfg);
        } catch (java.io.IOException e) {
            PersonalSpaceMod.LOG.error("Could not save teleport point", e);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote || !(world.provider instanceof PersonalWorldProvider)) return;
        DimensionConfig cfg = ((PersonalWorldProvider) world.provider).getConfig();
        cfg.setTeleportPoint(pos);
        save(world, cfg);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.provider instanceof PersonalWorldProvider) {
            DimensionConfig cfg = ((PersonalWorldProvider) world.provider).getConfig();
            if (pos.equals(cfg.getTeleportPoint())) {
                cfg.setTeleportPoint(null);
                save(world, cfg);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (!(world.provider instanceof PersonalWorldProvider) || !(player instanceof EntityPlayerMP)) return false;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        me.eigenraven.personalspace.world.PersonalReturnData returnData =
                me.eigenraven.personalspace.world.PersonalReturnData.get(world);
        int[] entry = returnData.get(mp.getUniqueID());
        int dim = 0;
        WorldServer destination = DimensionManager.getWorld(dim);
        if (destination == null) {
            DimensionManager.initDimension(dim);
            destination = DimensionManager.getWorld(dim);
        }
        if (destination == null) return true;
        BlockPos exit = entry == null ? destination.getSpawnPoint().up() : new BlockPos(entry[0], entry[1], entry[2]);
        mp.mcServer.getPlayerList().transferPlayerToDimension(mp, dim,
                new PersonalTeleporter(destination, exit.getX(), exit.getY(), exit.getZ()));
        return true;
    }
}
