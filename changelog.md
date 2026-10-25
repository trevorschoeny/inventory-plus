## 3.3.0

#### Added

- Inventory Plus works in creative now. On creative's inventory tab you get the Sort button and the feature column on the right, and S sorts your inventory there.
- Locks hold in creative, on the inventory tab and on the item tabs, and the lock key works there.
- Column Cycler's keys turn a column in creative, on the inventory tab and, with your mouse on a hotbar slot, on the item tabs. Both cyclers' keys work in creative with no screen open.
- When another mod shows more than one container on a chest's screen, such as a shulker box opened below the chest, each container keeps its own Sort order and pin and its own locks. Move Matching out puts each item only into a container that already has it, instead of the first empty slot it finds.
- A shulker box, ender chest, or bundle that another mod opens on a screen gets its own Sort button. Bundles sort too.
- A setting on the Tooltips tab turns the hide key around, so item tooltips stay hidden and holding the key shows them.
- On a game window too short for a chest's screen, the chest's rows scroll inside its frame instead of running off the screen.

#### Changed

- Requires MenuKit 7.4.0 or newer, below 8.0.0, and SlotKit 2.1.0 or newer, below 3.0.0.
- Slot lock no longer stops number keys or the offhand swap. Pick block reaching into a carried container is its own move now, so a Slot lock still stops it while number keys work.
- Item locks no longer stop collecting, the cyclers, or Move Matching topping up a stack, and now count only while you're carrying the item, so one left in a chest comes back out like anything else.
- A slot in a cycle is no longer locked against everything by default, only Slot lock and whatever boxes you've ticked.

#### Fixed

- A chest's slot locks could stop working after you switched worlds, until the chest was opened again.
- When Hotbar Cycler turns, the hotbar's slide now finishes along with the strip beside it.
