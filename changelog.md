## 3.3.2

#### Added

- Reach gets two checklists for every put, Empty slots and Matching. Matching only adds to a container already holding that item, and fills more of its slots there.
- Pickups, shift-click in and Move Matching in can fill a carried shulker box, bundle or your ender chest that already holds the item, off by default and needing Inventory Plus on the server too.
- Sort and Move Matching run while you're holding an item, setting it aside on an empty main-inventory or hotbar slot for the run. They still won't run with a completely full inventory.

#### Fixed

- A container's Sort button sat over a bundle's title when it showed one slot, or mid-row on a hopper or dispenser; it now sits at the right end of its title row, in line with the inventory's Sort.
- Sort, lock-drag and the Column Cycler keys could miss the slot under the mouse at the edge of a grid, or pick a vanilla slot a created slot covers.
- Sort and Move Matching could put a shulker box inside another shulker box.
- Shift-click or Move Matching out of your ender chest could copy a bundle into itself, then remove the original with the copy still inside it, losing it.
- Shift-click out of a shulker box or bundle opened from the inventory could drop the item back into the box it came from.
- Restock's own checks could refuse a put the real click or write would have allowed, or allow one it should have refused.

#### Changed

- Requires SlotKit 2.3.0 or newer, below 3.0.0.
