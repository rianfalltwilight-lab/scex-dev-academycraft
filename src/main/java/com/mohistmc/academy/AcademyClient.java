package com.mohistmc.academy;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: Mods list → Config opens NeoForge's configuration screen for both specs. */
@Mod(value = AcademyCraft.MODID, dist = Dist.CLIENT)
public final class AcademyClient {
    public AcademyClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
