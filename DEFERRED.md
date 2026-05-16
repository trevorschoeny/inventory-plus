# Inventory Plus — Deferred Items

Items intentionally postponed during 18b's feature passes. Each will be
addressed in a dedicated end-of-18b polish round before close-out.

## Button click feedback (Trev 2026-05-16)

Move-matching (and future Sort) buttons have no visible click / press
animation. Options:

- Second sprite frame for the pressed state; toggle in `renderWidget`
  based on a brief per-click flash (e.g., a press-tick counter set in
  `onClick`, decremented in `renderWidget`).
- Overlay treatment on click — brief tint or 1-px scale-down.
- Either / both for cycle as well — when shift-click cycles, flash a
  different visual to distinguish from trigger.

## Configuration UI (Trev 2026-05-16)

All configurable behavior gets a config screen at end-of-18b. Pulls
together every option currently hard-coded:

- **Buttons** — toggle show/hide for move-matching, toggle show/hide for sort
- **Auto-restock** — offhand on/off, armor on/off, proactive low-durability
  swap on/off, shulker / bundle nested sources on/off, direct ammo pull
  for bows on/off, low-durability threshold (default 10)
- **Move-matching** — default cycle for new containers; override per-cycle
  defaults across the mod
- **Sort** — default cycle for new containers (when Sort lands)
- **Keybinds** — make `M` and `S` rebindable via vanilla's Controls menu
  (currently raw `GLFW_KEY_M`)

## Auto-restock — tier-fallback (Imp flag, 2026-05-15)

Spec §"Tools" pass-2 ("highest-tier same-kind fallback") and §"Armor"
pass-2 unimplemented. Vanilla 1.21.11 dropped `TieredItem` / `Tier`
inheritance in favor of `DataComponents`; the replacement search needs
item-id tables or `ItemTags` to define tier ordering. Pass-1 (same-item
exact match) handles the common case — player has duplicates in
inventory — correctly today.

## Minecart hopper / chest persistence (Imp flag, 2026-05-15)

Vehicle right-click doesn't fire `UseBlockCallback`, so the per-container
cycle setting for chest minecart / hopper minecart doesn't persist (reads
the global default, doesn't save). Fix: hook `UseEntityCallback`, pin the
entity UUID as a new `ContainerKey.Entity(UUID)` variant.

## Locked-slots integration (post-locked-slots-feature)

Not in 18b's zero-gap subset. When the Locked-slots feature lands,
move-matching needs to:

- Skip locked **source** slots (don't pull from them)
- Skip locked **target** slots when iterating destinations
- Still count items in locked target slots toward the match set

## Dimension scoping for block-position keys (Imp flag, 2026-05-15)

`ContainerKey.Block(BlockPos)` uses just XYZ — two chests at the same
coords across different dimensions in the same world share a setting.
Low-impact (cycle settings are user prefs, not world state) but worth
upgrading to `dim:<dim-id>+block:<x,y,z>` if it bites in practice.
