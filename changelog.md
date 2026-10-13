Inventory Plus now runs on MenuKit 7 and SlotKit, the new library that took over MenuKit's slot features. It requires SlotKit 1.0.0 or newer, below 2.0.0, and MenuKit 7.0.0 or newer, below 8.0.0. It still works on any server, vanilla included, with nothing installed there. Your locks, keys and other settings carry over when you update.

Inventory Max cannot run alongside this version. A profile with Inventory Max stays on Inventory Plus 1.10.0.

The "Keep container locks on the server" option is hidden in this version, because it only worked with Inventory Max. Your setting is kept.

For mod developers: this is a major version because the API changed. `SlotLockProvider` and `InventoryPlusApi.registerSlotLockProvider` are removed. The slot types in the API (`SlotGroupId`, `BehaviorKey`, `TriBool` and the rest) now come from SlotKit's packages, `com.trevlar.slotkit.api.*`.
