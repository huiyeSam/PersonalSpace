package me.eigenraven.personalspace.block;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumHand;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import me.eigenraven.personalspace.PersonalSpaceMod;

public class PortalBlock extends Block implements ITileEntityProvider {

    public PortalBlock(boolean isMigration) {
        super(Material.ROCK);
        this.setUnlocalizedName(isMigration ? "personalspace.personalSpacePortalLegacy" : "personalspace.personalSpacePortal");
        this.setHardness(25.0F);
        this.setResistance(6000000.0F);
        this.setDefaultState(this.blockState.getBaseState());
        this.setLightOpacity(0);
        this.setCreativeTab(isMigration ? null : CreativeTabs.TRANSPORTATION);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D);
    }

    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    public TextureAtlasSprite getIcon(int side, int _meta) {
        // top
        if (side == 1) {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/portal");
        }
        // bottom
        if (side == 0) {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/enchanting_table_top");
        }
        // sides
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/obsidian");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new PortalTileEntity();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX,
            float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        PortalTileEntity portal = tile instanceof PortalTileEntity ? (PortalTileEntity) tile : null;
        if (world.isRemote) {
            if (portal == null) return false;
            if (portal.active && !player.isSneaking()) return true;
            PersonalSpaceMod.proxy.openPortalGui(world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        if (portal == null) {
            PersonalSpaceMod.LOG.warn("Portal interaction failed: missing tile entity at {} in dimension {}", pos,
                    world.provider.getDimension());
            return false;
        }
        if (portal.active && !player.isSneaking() && player instanceof EntityPlayerMP) {
            PersonalSpaceMod.LOG.info("Player {} activated portal at {} in dimension {} targeting dimension {}",
                    player.getName(), pos, world.provider.getDimension(), portal.targetDimId);
            portal.transport((EntityPlayerMP) player);
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack fromItem) {
        TileEntity wte = world.getTileEntity(pos);
        if (!(wte instanceof PortalTileEntity)) {
            return;
        }
        PortalTileEntity te = (PortalTileEntity) wte;
        double dx = placer.posX - (double) pos.getX();
        double dz = placer.posZ - (double) pos.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            te.facing = (dx > 0) ? EnumFacing.EAST : EnumFacing.WEST;
        } else {
            te.facing = (dz > 0) ? EnumFacing.SOUTH : EnumFacing.NORTH;
        }
        if (world.isRemote) {
            return;
        }
        if (fromItem.hasTagCompound() && fromItem.getTagCompound().getBoolean("CommandLink")
                && fromItem.getTagCompound().hasKey("PersonalSpacePortal")) {
            te.readLinkFromNBT(fromItem.getTagCompound().getCompoundTag("PersonalSpacePortal"));
            EntityPlayerMP player = null;
            if (placer instanceof EntityPlayerMP) {
                player = (EntityPlayerMP) placer;
            }
            te.linkOtherPortal(false, player);
        }
        te.markDirty();
    }

    ItemStack getItemStack(IBlockAccess w, int x, int y, int z) {
        return new ItemStack(PersonalSpaceMod.BLOCK_PORTAL);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(getItemStack(world, pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return getItemStack(world, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        if (world.provider instanceof me.eigenraven.personalspace.world.PersonalWorldProvider) return false;
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    @Override
    public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
        return world.provider instanceof me.eigenraven.personalspace.world.PersonalWorldProvider
                ? -1.0F : super.getBlockHardness(state, world, pos);
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, net.minecraft.entity.Entity entity) {
        return !(world instanceof World && ((World) world).provider
                instanceof me.eigenraven.personalspace.world.PersonalWorldProvider);
    }
}
