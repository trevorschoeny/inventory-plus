# Inventory Plus

Inventory Plus brings the inventory features you'd normally collect from a handful of mods into one. Restocking, sorting, locking slots, switching tools, and cycling your hotbar are all here, built to work with each other and configured from one menu.

It's client-side, so it works on any server.

## Features

### Restock

- Refills the stack in your hand from your inventory when it runs out.
- Replaces a broken tool or armor piece with the same item from your inventory, or the closest match you have.
- Can also swap out a tool or armor piece just before it breaks. You set how low its durability gets before it swaps.
- Can also take from shulker boxes and bundles in your inventory, and from your ender chest, when your inventory has nothing that fits. It's off by default.

### Sort

- Sorts any container, or your own inventory, with a button or a key.
- Right-click the button to sort by category, quantity, ID, or rarity.
- The key sorts whichever container your mouse is over.

### Move Matching

- Moves every item that the other side already has between your inventory and an open container, in one click.
- Right-click a button to choose how much moves. It can move everything, all but one of each stack, all but one stack of each item, or only items that all fit.
- A setting can be pinned to one container, so that container always uses it.

### Auto Tool Switch

- Puts the right tool in your hand when you start mining a block.
- Can also switch to a weapon when you attack a mob, using the weapon type you prefer.
- Switches back to what you were holding a moment after you stop, or when you press a key.
- Can also take a tool or weapon from a shulker box or bundle in your inventory.
- It's off until you turn it on.

### Lock groups

- Locks a slot so sorting, shift-clicks, drags, drops, and number-key swaps leave it alone.
- Locks one exact item, with its enchantments and name, wherever it goes and however worn it gets.
- Works in your inventory, your ender chest, and the chests, barrels, shulker boxes, hoppers, and dispensers you open.
- You can make your own groups, each with its own color, that lock slots, every item of a type, or one exact item.

### Reach

- Decides which slots each move is allowed to use, for every Inventory Plus feature and for vanilla moves like shift-clicking and dropping.
- Each lock group has a checkbox under each move, so you choose exactly what a lock stops.
- Also sets whether Restock, Auto Tool Switch, and pick block may take from shulker boxes and bundles in your inventory, and from your ender chest.

### Tooltips

- Adds durability, tool stats, food values, food effects, and a short description of each enchantment to item tooltips.
- Compares armor and tools with what you're wearing or holding.
- Hold Space to hide every tooltip and see what's behind it.
- Tooltips stay hidden when a screen opens until you move the mouse, so none pops up under the cursor.
- Long tooltips scroll with the mouse wheel, and wide ones wrap to fit the screen.

### Column Cycler and Hotbar Cycler

- Column Cycler rotates the items in one inventory column through a hotbar slot.
- Hotbar Cycler rotates whole inventory rows through your hotbar, so a second set of hotbar items is one key away.
- Both are off by default.

### One settings menu

- Every feature has its own tab, with its switch, its keys, and its settings.
- Ctrl-click (Cmd on a Mac) any Inventory Plus button to open that feature's tab.

## Requirements

- [Keybindery](https://modrinth.com/mod/keybindery) 1.1.0 or newer, for the keys and the key buttons in the settings menu.
- [MenuKit](https://modrinth.com/mod/menukit) 7.3.0 or newer, below 8.0.0, which draws the buttons, the settings menu, the strip beside the hotbar, and the tooltip changes.
- [SlotKit](https://modrinth.com/mod/slotkit) 2.0.0 or newer, below 3.0.0, which tells Inventory Plus what each slot is, so locks and Reach know what a move may touch.
- [Fabric API](https://modrinth.com/mod/fabric-api).

## Pairs well with

- [Hotbar Pockets](https://modrinth.com/mod/hotbar-pockets), my mod that adds extra slots behind your hotbar. It adds a Pockets tab to the Inventory Plus settings menu and a switch to the inventory's right column.

## Compatibility

It runs on Minecraft 26.3 with Fabric. Install it on your client, and it works on any server with nothing installed there. It can also be installed on a server, with Keybindery, MenuKit, SlotKit, and Fabric API alongside it, and nothing changes for players. It can't be installed together with Inventory Max, which is now Hotbar Pockets.

## How to use

### The buttons on your inventory

Open your inventory or any container to see the buttons. They don't appear in the creative inventory.

The row of small buttons above your inventory is for actions. From left to right, it has Move Matching out, Move Matching in, and Sort. The Move Matching buttons only appear while a container is open next to your inventory. A container with a Sort button of its own (a chest, barrel, ender chest, shulker box, hopper, dispenser, or dropper) has it above its slots.

The column to the right of your inventory turns features on and off. From top to bottom, it has the Settings button, then a switch for Lock groups, Restock, Auto Tool Switch, Column Cycler, and Hotbar Cycler. The two cycler switches are hidden until you show them from their tabs. Sort and Move Matching have no switch in the column, and are turned on and off from their tabs.

Every button works the same way.

- Left-click a button to do its action, or left-click a switch to turn its feature on or off. A switch's icon inverts while its feature is on.
- Right-click a button that has modes to go to the next mode. Shift-right-click goes back one.
- Ctrl-click (Cmd on a Mac) any Inventory Plus button to open that feature's tab in the settings menu. Holding Ctrl changes the tooltip to say so.
- Hover a button to see its tooltip, which shows the current mode and what each click does.

### Sort

- Left-click the Sort button above your inventory to sort your inventory. Your hotbar is never sorted.
- Left-click the Sort button above a container to sort that container.
- Right-click a Sort button to change the order. The orders are Category (the creative menu's order, the default), Quantity (most of an item first), ID (alphabetical by item ID), and Rarity (rarest first).
- Middle-click the Sort button above a container to pin the current order to that container. A pinned button is tinted pink, and middle-clicking it again unpins it.
- Press S over a container to sort it. With no container open, S sorts your inventory wherever your mouse is.
- Locked slots and locked items stay where they are while everything else is sorted around them.

The Sort tab has the Sort switch (on by default), Show the Sort button (on), and the Sort key (S).

### Move Matching

- Left-click Move Matching out to move items from your inventory into the open container, but only items the container already has.
- Left-click Move Matching in to move items from the container into your inventory, but only items your inventory already has.
- Right-click either button to change how much moves. Move all matching items moves everything. All but one of each stack leaves one item in each stack. All but one stack of each leaves one full stack of each item. Only if they all fit moves an item only when all of it fits.
- The in and out buttons keep separate modes.
- Middle-click Move Matching out to pin its mode to the open container. Middle-click it again to unpin.
- Press O to move matching items out and I to move them in. The keys work anywhere in the screen.

The Move Matching tab has the Move Matching switch (on by default), Show the Move Matching buttons (on), and the keys Move Matching Out of Inventory (O) and Move Matching Into Inventory (I).

### Restock

Restock is on by default and works while you play, with no screen open.

- When the stack in your hand or offhand runs out, it's refilled with the same item from your hotbar or inventory.
- When a tool or armor piece breaks, it's replaced with the same item. If you don't have one, it's replaced with the best item of the same kind you have, such as an iron pickaxe for a broken diamond one.
- With Swap before it breaks turned on, a tool or armor piece is swapped for a fresher one once its durability drops to the number you set.
- Restock can also take from a shulker box or bundle in your inventory, or from your ender chest, when your inventory has nothing that fits. This is set on the Reach tab, separately for items, tools, armor, and ammo (arrows or rockets for the bow or crossbow in your hand), and each starts off.
- Restock never takes from a slot locked with Slot lock.

| Setting on the Restock tab | Default |
| --- | --- |
| Restock (the switch) | On |
| Show the Restock button | On |
| Swap before breaking at durability | 10 (from 2 to 50) |
| Restock items | On |
| Restock tools | On |
| Swap before it breaks (under Tools) | Off |
| Restock armor | On |
| Swap before it breaks (under Armor) | Off |

### Auto Tool Switch

Auto Tool Switch is off by default. Turn it on with its switch in the inventory's right column, or on its tab.

- When you start mining a block, the best tool you have for it is put in your hand. If the tool is in your hotbar, the selected slot changes. If it's in your inventory, it's swapped into your hand.
- With Switch weapons too turned on, attacking a hostile mob puts a weapon in your hand. Your preferred weapon type is used when you have it, whatever its material. Turn on All mobs, not just hostile ones to switch for every mob.
- Hold Sneak to mine or attack without switching.
- When your inventory and hand have no tool or weapon that fits, it can take one from a shulker box or bundle in your inventory. This is set on the Reach tab and starts off. Your ender chest isn't searched, since the tool is picked on your side and the game doesn't show you what's in a closed ender chest.
- By default, your hotbar switches back to what you were holding 1 second after you stop. You can change the wait, turn it off, or use a key instead.
- It does nothing in creative.

| Setting on the Auto Tool Switch tab | Default |
| --- | --- |
| Auto Tool Switch (the switch) | Off |
| Show the Auto Tool Switch button | On |
| Switch back automatically | On |
| Seconds after you stop | 1 (from 0 to 10) |
| Key to previous tool | Off |
| Key (switches back to the previous tool) | Left Shift |
| Switch weapons too | Off |
| All mobs, not just hostile ones | Off |
| Preferred weapon | Sword (or Axe, Mace, Trident, or Spear) |

### Lock groups

A lock group is a named, colored set of locks. There are two to start with.

- Slot lock locks a slot. Whatever is in it stays put, and moves leave the slot alone even after you put something else in it. By default it stops sorting, Move Matching, shift-clicks, double-click collecting, item pickup into the slot, dropping, drag-filling, number-key and offhand swaps, and Restock taking from it.
- Exact item locks one item as it is, with its enchantments and name, however worn it gets. Another item of the same type isn't locked. By default it stops dropping, shift-clicking it out, double-click collecting, sorting, Move Matching, and both cyclers. Restock and Auto Tool Switch can still use it.

To lock something:

- Right-click the Lock groups switch in the inventory's right column to pick a group. Shift-right-click goes back. The switch is tinted with the group's color, and its tooltip names the group.
- Press L over a slot or an item to lock it with that group. Press L on it again to unlock it.
- With a slot group picked, hold L and move the mouse across slots to lock several at once.
- Slot locks work in your inventory (armor and offhand included), your ender chest, and any chest, barrel, shulker box, hopper, or dispenser you open. Locks in placed containers are kept on your computer, so they work on any server.
- Left-click the Lock groups switch to pause every lock without deleting any.

On the Lock groups tab, your groups are a list:

- Show the lock button (on) shows or hides the Lock groups switch.
- Click a group to open its settings. They show its kind and key, Color, which opens a picker with the 16 dye colors on one tab and any color on its Custom tab, and Rename. Slot lock's key is the Lock Slot key, and the other groups have no key of their own, since L applies whichever group is picked.
- An Item or Exact item group also lists the items it locks. Click the empty slot to pick one from a searchable grid of every item, or remove one with its corner button. These are kept per world, so pick them while you're in one.
- The + Slot group, + Item group, and + Exact item group buttons above the list make a new group. An Item group locks every item of one type, so locking one diamond pickaxe locks every diamond pickaxe.
- The order of the list is the order the Lock groups switch goes through when you right-click it. Move a group up or down with the arrows above the list. Slot lock and Exact item stay at the top.
- A group you made can be deleted with the button above the list. Its locks move to Slot lock or Exact item, except an Item group's, which are removed. It asks first and says how many locks it affects.
- Reset to Defaults removes every group you made and every lock, in every world.

What each group stops can be changed in Reach.

### Reach

The Reach tab lists every move that can change your slots, under Vanilla, Inventory Plus, and Other mods. Each move has a section with checkboxes for the slot groups, lock groups, and cycler slots it can use. A cleared box means that move never touches that group.

- For example, clearing Exact item in the Restock takes from section stops Restock from using items locked with Exact item.
- Clearing Slot lock in the Auto Tool Switch section stops Auto Tool Switch from taking tools out of locked slots.
- The Restock takes from, Auto Tool Switch, and Pick block sections also have boxes for the shulker boxes and bundles in your inventory, and Restock and Pick block have one for your ender chest. Restock's are split into items, tools, armor, and ammo. They all start off.
- With those boxes ticked, pick block takes the picked block's item from those containers when your inventory has none. Creative isn't affected.
- The closed section shows how many boxes are on and which lock groups stop the move. A move that's new since you last opened the tab is marked new.
- Revert to default puts one move back. Reset to Defaults at the top puts every move back.

### Tooltips

The Tooltips switch is on by default. Turning it off leaves every tooltip as vanilla shows it.

- Hold Space to hide every tooltip. Space is shared with Jump, and both still work. You can change the key on the Tooltips tab or in Controls.
- When a screen opens, tooltips stay hidden until you move the mouse.
- A tooltip taller than the screen scrolls with the mouse wheel, and one wider than the screen wraps. These are always on.
- With Dock tooltips at top or bottom turned on, a tooltip that would open beside your mouse sits at the bottom center of the screen instead, or at the top center when your mouse is down there.

| Setting on the Tooltips tab | Default |
| --- | --- |
| Dock tooltips at top or bottom | Off |
| Hide tooltips until the mouse moves | On |
| Hold a key to hide tooltips | On |
| Hide Tooltips key | Space |
| Durability | On |
| Food: nutrition and saturation | On |
| Food effects | On |
| Enchantment descriptions | On |
| Sort enchantments alphabetically | On |
| Tool stats: mining speed, tier and enchantability | On |
| Compare with what you're wearing or holding | On |
| Condense armor trim lines | On |
| Mod name | Off |

### The settings menu

Open it with the Settings button at the top of the inventory's right column, with Inventory Plus's config button in Mod Menu, or with the Open Settings key, which has no key until you set one. Ctrl-click (Cmd on a Mac) any Inventory Plus button to open it on that feature's tab.

The tabs are General, Tooltips, Reach, Lock groups, Sort, Move Matching, Restock, Auto Tool Switch, Column Cycler, and Hotbar Cycler. Tabs from other mods come after these. Each feature tab has the feature's switch at the top, then its settings and keys, which are grayed while the feature is off. Every tab has Reset to Defaults, which asks before it resets that tab's settings and keys.

Some settings are grayed even with their feature on. Those are planned and don't do anything yet.

On the General tab:

- Open Settings sets the key that opens the menu.
- Compact tooltips: short, one line on inventory buttons (off) shortens the buttons' tooltips to one line.
- Look switches the menu between Default, which looks like Minecraft's Options screens (the default), and Panel, which looks like a container. The button names the look in use, and clicking it switches to the other.
- Reset everything puts every setting in the menu back to its default and removes every lock, after asking.

Every key is also in Minecraft's Controls menu, under Inventory Plus.

| Key | Default |
| --- | --- |
| Lock Slot | L |
| Sort | S |
| Move Matching Out of Inventory | O |
| Move Matching Into Inventory | I |
| Toggle Cycle Slot | C |
| Column Cycle Forward | Up arrow |
| Column Cycle Backward | Down arrow |
| Hotbar Cycle Forward | \] |
| Hotbar Cycle Backward | \[ |
| Auto-Switch: Return to Previous Tool | Left Shift |
| Open Settings | Not set |
| Hide Tooltips | Space |

### Column Cycler

Column Cycler is off by default. Turn it on from its tab. To put its switch in the inventory's right column, turn on Show the Column Cycler button.

- Press C over a slot in your inventory (not your hotbar) to add it to its column's cycle. Press C again to take it out. Hold C and move across slots to add several.
- With no screen open, press the Up and Down arrows to rotate the cycle in the column of your selected hotbar slot.
- In your inventory, hover a slot in the cycle and press Up or Down to rotate that column.
- The column's items show in a strip beside the hotbar, which you can turn off with Beside the hotbar.
- With Scroll to cycle on, scrolling the mouse wheel on a hotbar slot with a cycle rotates the cycle instead of changing slots. Turning it on turns off Hotbar Cycler's Scroll to cycle, since there is one wheel.

| Setting on the Column Cycler tab | Default |
| --- | --- |
| Column Cycler (the switch) | Off |
| Beside the hotbar | Mini hotbar (or Off) |
| Show the Column Cycler button | Off |
| Toggle Cycle Slot | C |
| Column Cycle Forward | Up arrow |
| Column Cycle Backward | Down arrow |
| Scroll to cycle | Off |

### Hotbar Cycler

Hotbar Cycler is off by default. Turn it on from its tab. To put its switch in the inventory's right column, turn on Show the Hotbar Cycler button.

- Hover a row of your inventory and a small button appears at its left. Click it to add the row to the cycle, or click it again to take the row out. Buttons of rows in the cycle stay visible.
- Press \] to bring the next row down into your hotbar and \[ to send it back up. Items keep their columns, and the hotbar slides to show the change.
- The keys work with no screen open and in your inventory, including with a chest open. They only move your own rows.
- With Scroll to cycle on, the mouse wheel rotates the rows instead of changing hotbar slots. Turning it on turns off Column Cycler's Scroll to cycle.

| Setting on the Hotbar Cycler tab | Default |
| --- | --- |
| Hotbar Cycler (the switch) | Off |
| Show the Hotbar Cycler button | Off |
| Show the row buttons | On |
| Hotbar Cycle Forward | \] |
| Hotbar Cycle Backward | \[ |
| Scroll to cycle | Off |

## For mod developers

Other mods can add their own tab and switch to Inventory Plus, and work with its cyclers, Restock, and Reach. The public API is the `com.trevorschoeny.inventoryplus.api` package, and nothing in it changes shape without a major version.

- `InventoryPlusApi.SETTINGS_MENU` names the settings menu, for adding a tab with MenuKit's `Tabs.addTo`. Tabs added without an anchor go after Inventory Plus's own.
- `registerSettingsReset` adds a reset to Reset everything, and `confirmInSettings` asks before a tab's own Reset to Defaults, the same way Inventory Plus's tabs do.
- `addFeatureSwitch` adds an on and off switch to the inventory's right column, which behaves like Inventory Plus's own.
- `openSettings` and `settingsScreen` open the menu on a given tab, for a Ctrl-click or a Mod Menu entry.
- `HotbarCyclable` and `registerCyclable` let a mod with its own cycle share it with Auto Tool Switch and Restock, which can then find items in it and bring them down.
- `findRestockSource` and `suppressRestockFor` give access to Restock's search and keep Restock from treating a deliberate hotbar change as an item running out.
- `InventoryPlusOperations` holds the slot operations Inventory Plus performs as MenuKit keys, so a mod can send its own moves as one of them. `denyInReach` takes slot groups out of what an operation may use.

Adding a tab, from Hotbar Pockets:

```java
Tabs.addTo(InventoryPlusApi.SETTINGS_MENU,
        Tabs.tab("hotbarpockets").label(Component.literal("Pockets")).body(PocketsSettingsTab::body));
InventoryPlusApi.registerSettingsReset(PocketsSettingsTab::reset);
```

Inventory Plus is a client mod, so depend on it as an optional or required mod and don't bundle it with Jar-in-Jar. The API is documented in the Javadoc of the [source code](https://github.com/trevorschoeny/inventory-plus).

## License

MIT.
