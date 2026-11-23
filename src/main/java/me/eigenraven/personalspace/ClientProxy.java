package me.eigenraven.personalspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import me.eigenraven.personalspace.block.PortalTileEntity;
import me.eigenraven.personalspace.block.RenderPortal;
import me.eigenraven.personalspace.gui.GuiEditWorld;

public class ClientProxy extends CommonProxy {

    @Mod.EventBusSubscriber(modid = PersonalSpaceMod.MODID, value = Side.CLIENT)
    public static class ModelRegistration {
        @SubscribeEvent
        public static void registerModels(ModelRegistryEvent event) {
            ModelLoader.setCustomModelResourceLocation(
                    Item.getItemFromBlock(PersonalSpaceMod.BLOCK_TELEPORT_POINT), 0,
                    new ModelResourceLocation(PersonalSpaceMod.BLOCK_TELEPORT_POINT.getRegistryName(), "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    Item.getItemFromBlock(PersonalSpaceMod.BLOCK_PORTAL), 0,
                    new ModelResourceLocation(PersonalSpaceMod.BLOCK_PORTAL.getRegistryName(), "inventory"));
            ModelLoader.setCustomModelResourceLocation(
                    Item.getItemFromBlock(PersonalSpaceMod.BLOCK_SMOOTH_STONE), 0,
                    new ModelResourceLocation(PersonalSpaceMod.BLOCK_SMOOTH_STONE.getRegistryName(), "inventory"));
            ModelLoader.setCustomMeshDefinition(PersonalSpaceMod.ITEM_PERSONAL_SPACE_KEY,
                    stack -> new ModelResourceLocation(PersonalSpaceMod.ITEM_PERSONAL_SPACE_KEY.getRegistryName(), "inventory"));
        }
    }

    // preInit "Run before anything else. Read your config, create blocks, items,
    // etc., and register them with the GameRegistry."
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes."
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientRegistry.bindTileEntitySpecialRenderer(PortalTileEntity.class, new RenderPortal());
    }

    // postInit "Handle interaction with other mods, complete your setup based on this."
    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    @Override
    public void completeInit(FMLLoadCompleteEvent event) {
        super.completeInit(event);
    }

    @Override
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        super.serverAboutToStart(event);
    }

    // register server commands in this event handler
    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
    }

    @Override
    public void serverStarted(FMLServerStartedEvent event) {
        super.serverStarted(event);
    }

    @Override
    public void serverStopping(FMLServerStoppingEvent event) {
        super.serverStopping(event);
    }

    @Override
    public void serverStopped(FMLServerStoppedEvent event) {
        super.serverStopped(event);
    }

    @Override
    public void openPortalGui(World world, int x, int y, int z) {
        TileEntity wte = world.getTileEntity(new BlockPos(x, y, z));
        if (!(wte instanceof PortalTileEntity)) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        PortalTileEntity te = (PortalTileEntity) wte;
        if (!te.active && player.world.provider.getDimension() != 0) {
            player.sendMessage(new TextComponentString(I18n.format("chat.overworldPersonalDimension")));
            return;
        }
        if (!te.active || player.isSneaking()) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiEditWorld(te));
        }
    }

    @Override
    public void closePortalGui(PortalTileEntity owner) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiEditWorld) {
            GuiEditWorld gui = (GuiEditWorld) screen;
            if (gui.tile == owner) {
                Minecraft.getMinecraft().displayGuiScreen(null);
            }
        }
    }
}
