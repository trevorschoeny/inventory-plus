# Inventory Plus

Client-side inventory quality-of-life for Fabric. Installs on any server, vanilla or modded.

## Features

Auto-Restock refills your held stack from your inventory when it runs out, and replaces a tool or armor piece when it breaks. An optional "restock before it breaks" setting swaps in a fresh piece before the current one shatters. When Inventory Max is installed, restock can also pull from its pockets.

Auto Tool Switch puts the right tool in your hand when you hit a block. It can also switch to a weapon when you attack a mob, with a preferred-weapon setting. You choose how to return to what you were holding: never, automatically after a delay, or on a keybind. Hold sneak to suppress it. It is disabled in creative.

Sort tidies any container or your inventory with a button or a keybind. Right-click the button to change the order between category, quantity, ID and rarity.

Move Matching moves every matching item between your inventory and a container in one click. Right-click a button to choose how much moves: everything, everything but one, everything but a full stack, or only what fits. The in and out buttons keep separate settings, and middle-clicking any of these buttons pins its setting to the container you have open.

Locked Slots keeps sorting and quick-move away from slots you mark.

Locked Items protects the item instead of the slot, so it stays protected wherever it ends up. Press L on an item to lock its whole type, or lock one particular item with its enchantments and name, and it keeps that lock as it wears down. Sorting, move matching and the cyclers all leave it where it is. Auto Tool Switch and Auto-Restock still use it, since the tool you went to the trouble of locking is usually the one you want in your hand, and each has a setting if you would rather they did not.

Column Cycler turns a vertical column of inventory slots into a cycle you rotate through a hotbar slot with the Up and Down arrows, shown as a vertical overlay beside the hotbar.

Hotbar Cycler rotates whole rows of your inventory through the hotbar, so a mining row and a combat row are one key apart. Hover a row and click the button beside it to add it to the cycle. Press ] to bring the next row down into your hotbar and [ to send it back up. Items keep their columns, and the hotbar slides to show the change. It is off by default and lives under Power Users.

Every feature has its own toggle and settings under Mod Menu, Inventory Plus.

Pockets, extra equipment slots, and container locks live in [Inventory Max](https://modrinth.com/mod/inventory-max), the server-side companion mod.

## Requirements

- MenuKit 2.0.0 or newer
- Fabric API

Client only.

## License

MIT.
