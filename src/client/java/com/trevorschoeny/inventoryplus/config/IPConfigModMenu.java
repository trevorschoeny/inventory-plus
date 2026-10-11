package com.trevorschoeny.inventoryplus.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.trevorschoeny.inventoryplus.settings.SettingsMenu;

/**
 * ModMenu entrypoint: shows IP's settings menu as the "config" button next
 * to IP's row on the mods list.
 *
 * <p>ModMenu is a compileOnly + modLocalRuntime dependency (see
 * {@code build.gradle}). If ModMenu isn't installed at runtime, this class is
 * never loaded (its referenced types don't resolve), and IP loads normally.
 */
public final class IPConfigModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsMenu::create;
    }
}
