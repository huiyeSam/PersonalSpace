package me.eigenraven.personalspace.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

/**
 * Smooth Stone backported from newer Minecraft versions (1.14+).
 * A simple decorative block with the smooth stone texture.
 */
public class SmoothStoneBlock extends Block {

    public SmoothStoneBlock() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(6.0F);
        setHarvestLevel("pickaxe", 0);
        setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
        setUnlocalizedName("personalspace.smoothStone");
    }
}
