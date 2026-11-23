package me.eigenraven.personalspace.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public class TeleportPointItem extends ItemBlock {
    public TeleportPointItem(TeleportPointBlock block) { super(block); }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!TeleportPointBlock.canPlacePoint(world)) {
            if (!world.isRemote) player.sendMessage(new TextComponentTranslation("chat.personalWorld.pointDenied"));
            return EnumActionResult.FAIL;
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }
}
