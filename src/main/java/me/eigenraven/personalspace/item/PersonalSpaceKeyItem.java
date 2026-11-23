package me.eigenraven.personalspace.item;

import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.world.PersonalReturnData;
import me.eigenraven.personalspace.world.PersonalTeleporter;
import me.eigenraven.personalspace.world.PersonalWorldProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumActionResult;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class PersonalSpaceKeyItem extends Item {
    private static final String BOUND_DIM = "PersonalSpaceDimension";

    public PersonalSpaceKeyItem() {
        setMaxStackSize(1);
        setRegistryName(PersonalSpaceMod.MODID, "personal_space_key");
        setUnlocalizedName(PersonalSpaceMod.MODID + ".personal_space_key");
    }

    @Override
    public net.minecraft.util.ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        onItemUseFirst(player, world, player.getPosition(), net.minecraft.util.EnumFacing.UP, 0, 0, 0, hand);
        return new net.minecraft.util.ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
            net.minecraft.util.EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote || !(player instanceof EntityPlayerMP)) return EnumActionResult.SUCCESS;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        ItemStack stack = mp.getHeldItem(hand);
        if (mp.isSneaking() && world.provider instanceof PersonalWorldProvider) {
            stack.getOrCreateSubCompound("PersonalSpace").setInteger(BOUND_DIM, world.provider.getDimension());
            mp.sendMessage(new TextComponentTranslation("chat.personalWorld.keyBound", world.provider.getDimension()));
            return EnumActionResult.SUCCESS;
        }
        if (mp.isSneaking()) return EnumActionResult.PASS;
        int target = stack.hasTagCompound() && stack.getTagCompound().hasKey("PersonalSpace")
                ? stack.getTagCompound().getCompoundTag("PersonalSpace").getInteger(BOUND_DIM) : Integer.MIN_VALUE;
        if (target == Integer.MIN_VALUE) return EnumActionResult.PASS;
        if (world.provider instanceof PersonalWorldProvider && world.provider.getDimension() == target) {
            net.minecraft.nbt.NBTTagCompound keyData = stack.getOrCreateSubCompound("PersonalSpace");
            net.minecraft.nbt.NBTTagCompound coordinates = keyData.getCompoundTag("Return");
            int[] entry = coordinates.hasKey("Dimension")
                    ? new int[] { coordinates.getInteger("Dimension"), coordinates.getInteger("X"),
                            coordinates.getInteger("Y"), coordinates.getInteger("Z") }
                    : null;
            if (entry == null) return EnumActionResult.SUCCESS;
            WorldServer destination = DimensionManager.getWorld(entry[0]);
            if (destination == null) { DimensionManager.initDimension(entry[0]); destination = DimensionManager.getWorld(entry[0]); }
            if (destination != null) {
                int x = entry[1];
                int y = entry[2];
                int z = entry[3];
                keyData.setInteger("LastPrivateX", (int) mp.posX);
                keyData.setInteger("LastPrivateY", (int) mp.posY);
                keyData.setInteger("LastPrivateZ", (int) mp.posZ);
                mp.mcServer.getPlayerList().transferPlayerToDimension(mp, entry[0],
                        new PersonalTeleporter(destination, x, y, z));
            }
            return EnumActionResult.SUCCESS;
        }
        if (!DimensionManager.isDimensionRegistered(target)) {
            if (PersonalSpaceMod.INSTANCE == null || !PersonalSpaceMod.INSTANCE.restoreDimensionConfig(target)) return EnumActionResult.FAIL;
        }
        WorldServer destination = DimensionManager.getWorld(target);
        if (destination == null) { DimensionManager.initDimension(target); destination = DimensionManager.getWorld(target); }
        if (destination == null) return EnumActionResult.FAIL;
        net.minecraft.nbt.NBTTagCompound keyData = stack.getOrCreateSubCompound("PersonalSpace");
        net.minecraft.nbt.NBTTagCompound coordinates = new net.minecraft.nbt.NBTTagCompound();
        coordinates.setInteger("Dimension", world.provider.getDimension());
        coordinates.setInteger("X", (int) mp.posX);
        coordinates.setInteger("Y", (int) mp.posY);
        coordinates.setInteger("Z", (int) mp.posZ);
        keyData.setTag("Return", coordinates);
        BlockPos privatePosition = keyData.hasKey("LastPrivateX")
                ? new BlockPos(keyData.getInteger("LastPrivateX"), keyData.getInteger("LastPrivateY"),
                        keyData.getInteger("LastPrivateZ"))
                : destination.getSpawnPoint().up();
        mp.mcServer.getPlayerList().transferPlayerToDimension(mp, target,
                new PersonalTeleporter(destination, privatePosition.getX(), privatePosition.getY(), privatePosition.getZ()));
        return EnumActionResult.SUCCESS;
    }
}
