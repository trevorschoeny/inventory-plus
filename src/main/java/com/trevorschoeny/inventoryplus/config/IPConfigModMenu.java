package com.trevorschoeny.inventoryplus.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu entrypoint — surfaces IP's config screen as the "config" button
 * next to IP's row on the mods list.
 *
 * <p>ModMenu is a compileOnly + modLocalRuntime dependency
 * (see {@code build.gradle}). If ModMenu isn't installed at runtime,
 * this class is never loaded (its referenced types don't resolve), but
 * IP's mod loading is unaffected — ModMenu integration is purely
 * additive surface, not load-bearing.
 */
public final class IPConfigModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return IPConfigScreen::create;
    }
}
