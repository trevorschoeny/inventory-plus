# Inventory Plus

Inventory Plus takes the small chores out of managing your inventory. Vanilla's inventory works, but you still stop to dig out a spare pickaxe, sort chests by hand, and shift-click around the items you meant to keep. Inventory Plus handles those without changing how the game plays. It works on any server, vanilla or modded, with nothing installed there.

## Features

Restock refills your held stack from your inventory when it runs out, and replaces a tool or armor piece when it breaks. An optional "swap before it breaks" setting puts in a fresh piece before the current one shatters. When Inventory Max is installed, Restock can also pull from its pockets.

Auto Tool Switch puts the right tool in your hand when you hit a block. It can also switch to a weapon when you attack a mob, with a preferred weapon setting. You choose how to return to what you were holding: never, automatically after a delay, or on a key. Hold sneak to stop it switching. It is off until you turn it on, and does nothing in creative.

Sort tidies any container or your inventory with a button or a key. Right-click the button to change the order between category, quantity, ID and rarity.

Move Matching moves every matching item between your inventory and a container in one click. Right-click a button to choose how much moves: everything, everything but one, everything but a full stack, or only what fits. The in and out buttons keep separate settings, and middle-clicking any of these buttons pins its setting to the container you have open.

Lock groups keep the slots and items you care about where they are. Click the lock button to pick a group, then press L over a slot or an item. Slot lock holds a slot in place, so sorting, shift-clicks, drags, drops and number-key swaps leave it alone. It works in your inventory, your ender chest, and any chest, barrel, shulker box, hopper or dispenser you open, and pockets and equipment slots from Inventory Max lock the same way. Exact item protects one particular item, with its enchantments and name, wherever it ends up and as it wears down. Restock and Auto Tool Switch still use a locked item, since the tool you went to the trouble of locking is usually the one you want in your hand. On the Lock groups tab you can make your own groups, each with its own colour, locking slots, a type of item, or one exact item.

Container locks are yours alone and stay on your computer, so they work on any server. On a server running Inventory Max, turn on "Also keep container locks on the server" and the container locks you place from then on are shared with the server, which enforces them for everyone. It is off by default.

Reach decides where each action may go. Every Inventory Plus feature, and every vanilla move such as shift-clicking, dragging and dropping, has a list of slot groups and lock groups, and clearing a box keeps that action out of it. Clear Hotbar under Sort and sorting leaves your hotbar alone. Clear Exact item under Restock and Restock stops using locked items.

Tooltips carry more information. Tools show durability and mining speed, each enchantment gets a line saying what it does, and food shows what it restores. Hold Space to hide every tooltip and see what is behind it, and change the key if Space is in your way. A tooltip taller than the screen scrolls with the mouse wheel, and one wider than the screen is kept on screen. Each line and behaviour has its own checkbox.

Column Cycler turns a vertical column of inventory slots into a cycle you rotate through a hotbar slot with the Up and Down arrows, shown as a vertical overlay beside the hotbar. It is off by default.

Hotbar Cycler rotates whole rows of your inventory through the hotbar, so a mining row and a combat row are one key apart. Hover a row and click the button beside it to add it to the cycle. Press ] to bring the next row down into your hotbar and [ to send it back up. Items keep their columns, and the hotbar slides to show the change. It is off by default.

Every feature has a tab in the settings menu, with its switch, its keys and its settings. Open it with the gear at the right end of the toolbar above your inventory, or with Inventory Plus's config button in Mod Menu. Ctrl-click (Cmd on a Mac) any Inventory Plus button to open that feature's tab.

Pockets, extra equipment slots, and container locks the whole server shares live in [Inventory Max](https://modrinth.com/mod/inventory-max), the companion mod.

## Requirements

- Keybindery 1.1.0 or newer
- MenuKit 6.0.0 or newer, below 7.0.0
- Fabric API

## Compatibility

Minecraft 26.2 with Fabric. Install it on your client, and it works on any server with nothing installed there. It also installs on servers, with Keybindery, MenuKit and Fabric API alongside it. On a server it only lets companion mods such as Inventory Max recognise what Inventory Plus does, and what players see does not change.

## License

MIT.
