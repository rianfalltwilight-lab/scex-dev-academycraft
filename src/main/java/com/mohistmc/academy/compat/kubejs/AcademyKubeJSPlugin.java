package com.mohistmc.academy.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * Loaded by KubeJS only (listed in kubejs.plugins.txt); nothing else in the mod references KubeJS,
 * so AcademyCraft runs unchanged without it.  Recipe schemas ship as JSON under
 * data/academy/kubejs/recipe_schema.
 */
public final class AcademyKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void init() {
        AcademyKubeEvents.bridge();
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(AcademyKubeEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("Academy", AcademyJS.class);
    }
}
