package me.eigenraven.personalspace.gui;

import java.util.Collections;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import me.eigenraven.personalspace.PersonalSpaceMod;
import me.eigenraven.personalspace.config.Config;

@Mod.EventBusSubscriber(modid = PersonalSpaceMod.MODID, value = Side.CLIENT)
public class PersonalSpaceGuiFactory implements IModGuiFactory {

    private static final String CONFIG_ID = "personalspace.general";

    @Override
    public void initialize(Minecraft minecraft) {
        ModContainer mod = Loader.instance().getIndexedModList().get(PersonalSpaceMod.MODID);
        if (mod != null && minecraft.getResourceManager() instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) minecraft.getResourceManager()).registerReloadListener(resources -> {
                String key = "personalspace.name";
                mod.getMetadata().name = I18n.hasKey(key) ? I18n.format(key) : PersonalSpaceMod.MODNAME;
            });
        }
    }

    @Override
    public boolean hasConfigGui() {
        return Config.getConfiguration() != null;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        // Global rules are shared with the integrated server. Edit them from the main menu.
        return new GuiConfig(parentScreen, new ConfigElement(Config.getConfiguration().getCategory("general"))
                .getChildElements(), PersonalSpaceMod.MODID, CONFIG_ID, true, false,
                I18n.format("personalspace.config.title"), I18n.format("personalspace.config.scope"));
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (PersonalSpaceMod.MODID.equals(event.getModID()) && CONFIG_ID.equals(event.getConfigID())
                && !event.isWorldRunning()) {
            Config.saveGuiChanges();
        }
    }
}
