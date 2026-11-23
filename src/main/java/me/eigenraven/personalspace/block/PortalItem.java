package me.eigenraven.personalspace.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.world.World;

public class PortalItem extends ItemBlock {

    public PortalItem(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack itemStack, World world, List<String> list, ITooltipFlag tooltipFlag) {
        if (itemStack.getTagCompound() != null && itemStack.getTagCompound().hasKey("PersonalSpacePortal")) {
            int[] target = itemStack.getTagCompound().getCompoundTag("PersonalSpacePortal").getIntArray("target");
            if (target.length >= 4) {
                list.add(String.format("DIM%d: %d, %d, %d", target[0], target[1], target[2], target[3]));
            }
        }
    }

    @Override
    public boolean hasEffect(ItemStack itemStack) {
        return itemStack != null && itemStack.getTagCompound() != null
                && itemStack.getTagCompound().hasKey("PersonalSpacePortal");
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        ItemStack itemStack = player.getHeldItem(hand);
        for (BlockPos nearbyPos : BlockPos.getAllInBox(pos.add(-2, -2, -2), pos.add(2, 2, 2))) {
            if (world.isBlockLoaded(nearbyPos) && world.getBlockState(nearbyPos).getBlock() instanceof PortalBlock) {
                if (!world.isRemote) {
                    player.sendMessage(new TextComponentTranslation("chat.personalWorld.proximity"));
                }
                return EnumActionResult.FAIL;
            }
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    /**
     * Prevents despawning of portals on the ground
     */
    @Override
    public int getEntityLifespan(ItemStack itemStack, World world) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    /**
     * Override the portal item entity to be indestructible by fire, lava, cactus, explosion etc. damage
     */
    @Override
    public Entity createEntity(World world, Entity location, ItemStack itemstack) {
        return new PortalEntityItem(world, location, itemstack);
    }
}
