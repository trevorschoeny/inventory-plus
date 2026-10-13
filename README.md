# Inventory Plus

Inventory Plus takes the small chores out of managing your inventory, like digging out a spare pickaxe, sorting chests by hand, and shift-clicking around the items you meant to keep. It handles those without changing how the game plays. It works on any server, vanilla or modded, with nothing installed there.

## Features

### Restock

Restock refills your held stack from your inventory when it runs out, and replaces a tool or armor piece when it breaks. Turn on "swap before it breaks" and it puts in a fresh piece before the current one shatters.

### Auto Tool Switch

Auto Tool Switch puts the right tool in your hand when you hit a block, and can switch to a weapon when you attack a mob, with a preferred weapon you choose. Once you stop, it switches back to what you were holding after a wait you set, from 0 to 10 seconds. You can turn that off, or add a key that switches back early. Hold sneak to stop it switching. It is off until you turn it on, and does nothing in creative.

### Sort

Sort tidies any container or your inventory with a button or a key. Right-click the button to change the order between category, quantity, ID and rarity. The key sorts whichever container your mouse is over, and with only your inventory open it sorts that wherever your mouse is.

### Move Matching

Move Matching moves every matching item between your inventory and a container in one click. Right-click a button to choose how much moves: everything, everything but one, everything but a full stack, or only what fits. The in and out buttons keep separate settings. Middle-click Move Matching out, or the Sort button above a container, to pin its setting to that container.

### Lock groups

Lock groups keep the slots and items you care about where they are. Press L over a slot or an item to lock it with the current group, and right-click the Lock groups switch beside your inventory to pick another group. A slot lock holds a slot in place, so sorting, shift-clicks, drags, drops and number-key swaps leave it alone. It works in your inventory, your ender chest, and any chest, barrel, shulker box, hopper or dispenser you open. An exact item lock protects one particular item, with its enchantments and name, wherever it ends up and however worn it gets. Restock and Auto Tool Switch still use a locked item, because the tool you bothered to lock is usually the one you want in your hand. On the Lock groups tab you can make your own groups, each with its own colour, that lock slots, a type of item, or one exact item.

Container locks belong to you and stay on your computer, so they work on any server.

### Reach

Reach decides where each move may go. Every Inventory Plus feature, and every vanilla move such as shift-clicking, dragging and dropping, has a list of slot groups and lock groups, and clearing a box keeps that move out of it. Clear Hotbar under Sort and sorting leaves your hotbar alone. Clear Exact item under Restock and Restock stops using locked items.

### Tooltips

Tooltips show more. Tools show durability and mining speed, each enchantment gets a line saying what it does, and food shows what it restores. Each of those lines has its own checkbox. Hold Space to hide every tooltip and see what is behind it, or change the key if Space is in your way. A tooltip taller than the screen scrolls with the mouse wheel, and one wider than the screen wraps. Turn on Dock tooltips at top or bottom and a tooltip that would open beside your mouse sits at the bottom centre of the screen instead, or at the top centre when your mouse is down there.

### Column Cycler

Column Cycler turns a column of inventory slots into a cycle that you rotate through a hotbar slot with the Up and Down arrows. Press C over a slot to add it, and the cycle shows beside the hotbar. It is off by default.

### Hotbar Cycler

Hotbar Cycler rotates whole rows of your inventory through the hotbar, so a mining row and a combat row are one key apart. Hover a row and click the button beside it to add it to the cycle. Press ] to bring the next row down into your hotbar and [ to send it back up. Items keep their columns, and the hotbar slides to show the change. It is off by default.

### Buttons and settings

The row of buttons above your inventory is for acting: Move Matching out, Move Matching in, and Sort. The column to the right turns features on and off, with the settings gear at the top and a switch for each feature below it. Left-click a switch to turn its feature on or off. The Column Cycler and Hotbar Cycler switches start hidden, and each tab has a checkbox to show or hide its switch.

Every feature has a tab in the settings menu, with its on and off switch, its keys and its settings. Open it with the gear, or with Inventory Plus's config button in Mod Menu. Ctrl-click (Cmd on a Mac) any Inventory Plus button to open that feature's tab. The menu looks like Minecraft's own Options screens, and the Look button on the General tab switches it to a panel like a container's.

## Requirements

- [Keybindery](https://modrinth.com/mod/keybindery) 1.1.0 or newer
- [MenuKit](https://modrinth.com/mod/menukit) 7.0.0 or newer, below 8.0.0
- [SlotKit](https://modrinth.com/mod/slotkit) 1.0.0 or newer, below 2.0.0
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Compatibility

Minecraft 26.3 with Fabric. Install it on your client, and it works on any server with nothing installed there. It also installs on servers, with Keybindery, MenuKit, SlotKit and Fabric API alongside it, and what players see does not change. It cannot be installed together with Inventory Max.

## License

MIT.
