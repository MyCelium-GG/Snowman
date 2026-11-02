package my.celium.org.snowmen.neoforge;

import my.celium.org.client.ClientHooks;
import my.celium.org.snowmen.SnowmenMod;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only: the settings screen in the mod list. A dedicated server never loads this class. */
public final class ClientSnowmen {
    private ClientSnowmen() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent, SnowmenMod.ID));
    }
}
