## 3.0.0

Requires MenuKit 7.1.0 or newer, below 8.0.0, and SlotKit 1.1.0 or newer, below 2.0.0. It still works on any server, vanilla included, with nothing installed there. Your locks, keys and other settings carry over when you update.

The strip beside the hotbar that shows a Column Cycler column is now drawn by MenuKit, so it looks and slides the same as other mods' strips, like Hotbar Pockets' mini hotbar. When Hotbar Cycler turns, the strip slides with the hotbar.

When a screen opens, tooltips stay hidden until you move the mouse, so the slot under your cursor doesn't pop one up the moment it opens. It's a switch on the Tooltips tab, on by default.

The Sort button's tooltip, and other button tooltips of two lines, show on two lines again instead of with a stray "LF" box.

Other mods can add their own tabs to the settings menu. They go after Inventory Plus's tabs. Hotbar Pockets adds a Pockets tab.

Inventory Max is now Hotbar Pockets, and Inventory Plus no longer has any code for Inventory Max. Anything your Reach settings saved for Inventory Max's moves or slots is dropped the first time this version loads. Inventory Max still can't be installed alongside Inventory Plus.

For mod developers: this is a major version because the API changed. The cycle HUD API is removed: `CycleHudSource`, `CycleView`, `CyclerDirection`, `InventoryPlusApi.registerHudSource` and `InventoryPlusApi.playCycleAnimation`. A mod with a cycle to show registers it with MenuKit's `HudCycleStrip` instead. `InventoryPlusApi.SETTINGS_MENU` is new. It names the settings menu, for adding a tab with MenuKit's `Tabs.addTo`.
