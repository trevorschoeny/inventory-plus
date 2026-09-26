Fixes a crash when breaking a block. In any world where you had never locked a container slot, the first block you broke closed the game. It came in with container locks in 1.5.0.

Fixes restock taking from the wrong slot when the spare was on your hotbar. Refilling your offhand, or replacing armour that broke or wore down, clicked a crafting or armour slot instead of the spare, so the offhand stayed empty or a piece of armour you were wearing moved. Restock now always moves the spare it found.

Built on MenuKit 5.0.0. Requires MenuKit 5.0.0 or newer, below 6.0.0. Companion mods now reach Inventory Plus through one public package instead of its internals. Inventory Max 1.0.2 and older used the old names and will not load next to this version, so update Inventory Max to 1.0.3.
