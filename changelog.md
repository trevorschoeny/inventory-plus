Inventory Plus now runs on MenuKit 7 and SlotKit, the new library that took over MenuKit's slot features. It requires SlotKit 1.1.0 or newer, below 2.0.0, and MenuKit 7.1.0 or newer, below 8.0.0. It still works on any server, vanilla included, with nothing installed there. Your locks, keys and other settings carry over when you update.

Inventory Max cannot run alongside this version. A profile with Inventory Max stays on Inventory Plus 1.10.0.

The strip beside the hotbar that shows a Column Cycler column is now drawn by MenuKit's cycle strip, so it looks and slides the same as other mods' cycle strips. When Hotbar Cycler turns, the strip slides with the hotbar.

Inventory Plus no longer has any code for Inventory Max. Anything your Reach settings saved for Inventory Max's moves or slots is dropped the first time this version loads.

Other mods can add their own tabs to the settings menu. They go after Inventory Plus's tabs.

The "Keep container locks on the server" option is hidden in this version, because it only worked with Inventory Max. Your setting is kept.

Opening your inventory now starts the cursor a few pixels above your items, so a long tooltip isn't the first thing you see. Turn it off on the Tooltips tab if you'd rather it land on the first slot as before.

For mod developers: this is a major version because the API changed. `SlotLockProvider` and `InventoryPlusApi.registerSlotLockProvider` are removed. The cycle HUD API is removed too: `CycleHudSource`, `CycleView`, `CyclerDirection`, `InventoryPlusApi.registerHudSource` and `InventoryPlusApi.playCycleAnimation`. A mod with a cycle to show now registers it with MenuKit's `HudCycleStrip`. `InventoryPlusApi.SETTINGS_MENU` is new: the settings menu's name, for adding a tab with MenuKit's `Tabs.addTo`. The slot types in the API (`SlotGroupId`, `BehaviorKey`, `TriBool` and the rest) now come from SlotKit's packages, `com.trevlar.slotkit.api.*`.
