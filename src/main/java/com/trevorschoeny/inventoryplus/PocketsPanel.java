package com.trevorschoeny.inventoryplus;


import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKPanel;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Pockets — each hotbar slot has 3 extra item slots behind it, accessible
 * through a pocket panel below the hotbar in the inventory screen.
 *
 * <p><b>Inventory UI:</b> 9 small buttons under the hotbar, one per slot.
 * Clicking a button reveals that slot's pocket panel (3 storage slots
 * in a row from the MKContainer). Only one pocket is open at a time.
 *
 * <p><b>Disabled Slots:</b> Clicking an empty pocket slot with an empty
 * cursor toggles it as "disabled." Disabled slots are excluded from
 * cycling and hidden from the HUD. The barrier icon (🚫) shows in the
 * inventory panel to indicate the slot is out of rotation. Items can
 * still be placed into disabled slots manually.
 *
 * <p><b>HUD:</b> The pocket items for the selected hotbar slot show
 * to the right of the hotbar. Cycling keybinds rotate items through
 * the hotbar position, skipping disabled slots.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class PocketsPanel {

    /** Number of extra pocket slots per hotbar position. */
    private static final int POCKET_SIZE = 3;

    // Hotbar slot spacing (same in all screens)
    private static final int SLOT_SPACING = 18;

    // Pocket button dimensions — sized to fit 13×13 icon + padding
    private static final int BUTTON_WIDTH = 16;
    private static final int BUTTON_HEIGHT = 16;

    // Pocket button icon textures
    private static final Identifier POCKET_ICON =
            Identifier.fromNamespaceAndPath("trevs-mod", "menukit/pocket");
    private static final Identifier POCKET_TOGGLED_ICON =
            Identifier.fromNamespaceAndPath("trevs-mod", "menukit/pocket_toggled");

    // Barrier icon — vanilla's barrier item texture, used as ghost icon for
    // disabled slots. The 🚫 symbol tells the player "this slot is out of rotation."
    private static final Identifier BARRIER_ICON =
            Identifier.withDefaultNamespace("item/barrier");

    // Contexts where pockets show — all screens with player inventory visible.
    // Uses MKContext.ALL_WITH_PLAYER_INVENTORY directly via .showInAll() on each panel builder.
    private static final Set<MKContext> POCKET_CONTEXTS = MKContext.ALL_WITH_PLAYER_INVENTORY;

    // ── Disabled Slots ───────────────────────────────────────────────────────
    // Per-hotbar-slot sets of pocket indices (0–2) that are disabled.
    // Disabled slots are excluded from cycling and hidden from the HUD.
    // Persisted via panel onSave/onLoad.

    @SuppressWarnings("unchecked")
    private static final Set<Integer>[] disabledSlots = new Set[9];

    static {
        for (int i = 0; i < 9; i++) {
            disabledSlots[i] = new HashSet<>();
        }
    }

    /**
     * Whether a pocket slot is disabled (excluded from cycling).
     * A slot is disabled if the user manually disabled it OR if it
     * exceeds the configured slot count in settings.
     */
    public static boolean isDisabled(int hotbarSlot, int pocketIndex) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return false;
        int maxSlots = InventoryPlusConfig.get().pocketSlotCount;
        if (pocketIndex >= maxSlots) return true;
        return disabledSlots[hotbarSlot].contains(pocketIndex);
    }

    /** Returns the disabled set for a hotbar slot (used by PocketCycler/PocketHud). */
    public static Set<Integer> getDisabledSlots(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return Set.of();
        return disabledSlots[hotbarSlot];
    }

    /**
     * Applies the current config state to pocket panels and buttons.
     * Called on config save and on init. Hides all pocket UI when
     * pockets are disabled; shows them when enabled.
     */
    public static void applyConfig() {
        boolean enabled = InventoryPlusConfig.get().enablePockets;
        for (int i = 0; i < 9; i++) {
            if (enabled) {
                // Show buttons (panels stay hidden until user clicks a button)
                MenuKit.showPanel("pocket_btn_" + i);
            } else {
                // Hide everything — both pocket panels and their buttons
                MenuKit.hidePanel("pocket_" + i);
                MenuKit.hidePanel("pocket_btn_" + i);
            }
        }
    }

    /** Toggles a pocket slot's disabled state. */
    private static void toggleDisabled(int hotbarSlot, int pocketIndex) {
        if (hotbarSlot < 0 || hotbarSlot >= 9) return;
        Set<Integer> disabled = disabledSlots[hotbarSlot];
        if (disabled.contains(pocketIndex)) {
            disabled.remove(pocketIndex);
            InventoryPlus.LOGGER.info("[Pockets] Enabled slot: hotbar={} pocket={}", hotbarSlot, pocketIndex);
        } else {
            disabled.add(pocketIndex);
            InventoryPlus.LOGGER.info("[Pockets] Disabled slot: hotbar={} pocket={}", hotbarSlot, pocketIndex);
        }
    }

    // ── Layout helpers ──────────────────────────────────────────────────────
    //
    // All hotbar position data now lives in MKContextLayout.getHotbarX/Y.
    // These local constants express the pocket-specific offsets relative to
    // the hotbar row — the only layout knowledge PocketsPanel needs.

    // Buttons sit one slot-height below the hotbar row
    private static final int BUTTON_Y_OFFSET = SLOT_SPACING;

    // Panels sit below the button row: slot spacing + button height + 2px gap
    private static final int PANEL_Y_OFFSET = SLOT_SPACING + BUTTON_HEIGHT + 2;

    // Half-width of the pocket panel (3 slots + padding ≈ 63px), for centering
    private static final int PANEL_HALF_WIDTH = 32;

    // Center of a slot within the slot spacing (18 / 2 = 9px)
    private static final int SLOT_CENTER = SLOT_SPACING / 2;

    /**
     * X offset from hotbar left edge for centering a pocket panel under
     * a specific hotbar slot. The panel is wider than one slot, so we
     * center it under the slot's midpoint.
     */
    private static int panelXOffset(int hotbarIndex) {
        // Slot center relative to hotbar: index * 18 + 9
        // Panel left edge: center - halfWidth
        return hotbarIndex * SLOT_SPACING + SLOT_CENTER - PANEL_HALF_WIDTH;
    }

    // ── Registration ────────────────────────────────────────────────────────

    /**
     * Registers all pocket containers, panels, and buttons.
     * Called from {@link InventoryPlus#init()}.
     */
    public static void register() {
        // 9 separate containers, one per hotbar slot (3 extra items each)
        for (int i = 0; i < 9; i++) {
            MenuKit.container("pocket_" + i).playerBound().size(POCKET_SIZE).register();
        }

        // 9 pocket panels (one per hotbar slot), all starting hidden
        for (int i = 0; i < 9; i++) {
            registerPocketPanel(i);
        }

        // 9 individual button panels (one per hotbar slot)
        for (int i = 0; i < 9; i++) {
            registerButtonForSlot(i);
        }

        // Register persistence for disabled slots
        registerDisabledSlotPersistence();

        InventoryPlus.LOGGER.info("[Pockets] Registered 9 pocket panels + 9 pocket buttons");
    }

    /**
     * Registers one pocket panel for hotbar slot index i.
     * Contains 3 pocket storage slots in a row.
     */
    private static void registerPocketPanel(int hotbarIndex) {
        // Position the panel relative to the hotbar — MenuKit resolves the
        // hotbar's actual screen position per-context automatically. No more
        // per-context override loops!
        var panelBuilder = MKPanel.builder("pocket_" + hotbarIndex)
                .showIn(POCKET_CONTEXTS)
                .posRelativeToHotbar(panelXOffset(hotbarIndex), PANEL_Y_OFFSET)
                .padding(4)
                .style(MKPanel.Style.RAISED)
                .hidden()
                .allowOverlap();

        // Enter row layout group, add 3 pocket slots
        var builder = panelBuilder.row().gap(0);
        final int hIdx = hotbarIndex;
        for (int j = 0; j < POCKET_SIZE; j++) {
            final int pIdx = j;
            builder = builder.slot()
                    .container("pocket_" + hotbarIndex, j)
                    // Hide the slot entirely when it exceeds the configured slot count
                    .disabledWhen(() -> pIdx >= InventoryPlusConfig.get().pocketSlotCount)
                    .filter(stack -> !isDisabled(hIdx, pIdx)) // disabled slots reject all items
                    .ghostIcon(() -> isDisabled(hIdx, pIdx) ? BARRIER_ICON : null)
                    .onEmptyClick(slot -> toggleDisabled(hIdx, pIdx))
                    .emptyTooltip(() -> isDisabled(hIdx, pIdx)
                            ? Component.literal("Enable slot")
                            : Component.literal("Disable slot"))
                    .done();
        }

        builder.build();
    }

    /**
     * Registers a single button panel for one hotbar slot.
     * Each button is in its own panel to avoid the label-matching issue
     * (updateButtonPositions matches by panel name + label).
     */
    private static void registerButtonForSlot(int hotbarIndex) {
        final int index = hotbarIndex;

        // Button sits directly under its hotbar slot — posRelativeToHotbarSlot
        // handles per-context hotbar position automatically.
        var builder = MKPanel.builder("pocket_btn_" + hotbarIndex)
                .showIn(POCKET_CONTEXTS)
                .posRelativeToHotbarSlot(hotbarIndex, 0, BUTTON_Y_OFFSET)
                .padding(0)
                .autoSize()
                .allowOverlap()
                .style(MKPanel.Style.NONE);

        builder
                .button(0, 0)
                    .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                    .icon(POCKET_ICON)
                    .toggledIcon(POCKET_TOGGLED_ICON)
                    .iconSize(13)
                    .pressedWhen(() -> !MenuKit.isPanelInactive("pocket_" + index))
                    .onClick(btn -> togglePocket(index))
                    .done()
                .build();
    }

    /**
     * Persists disabled slot state using MenuKit's standalone persistence API.
     * No dummy panel needed — registerPersistence hooks directly into the
     * same player NBT save/load lifecycle that panels use.
     *
     * Key is "panel_pocket_disabled" for backward compatibility — the old
     * dummy-panel approach wrote to output.child("panel_" + panelName).
     */
    private static void registerDisabledSlotPersistence() {
        MenuKit.registerPersistence("panel_pocket_disabled",
                // Save: write each hotbar slot's disabled set as comma-separated string.
                // e.g., hotbar 3 with slots 0 and 2 disabled → "0,2"
                output -> {
                    for (int h = 0; h < 9; h++) {
                        Set<Integer> disabled = disabledSlots[h];
                        if (!disabled.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (int idx : disabled) {
                                if (sb.length() > 0) sb.append(',');
                                sb.append(idx);
                            }
                            output.putString("pocket_" + h, sb.toString());
                        }
                    }
                },
                // Load: restore disabled sets from saved comma-separated strings.
                input -> {
                    for (int h = 0; h < 9; h++) {
                        final int hotbar = h;
                        disabledSlots[hotbar].clear();
                        input.getString("pocket_" + hotbar).ifPresent(str -> {
                            for (String part : str.split(",")) {
                                try {
                                    disabledSlots[hotbar].add(Integer.parseInt(part.trim()));
                                } catch (NumberFormatException ignored) {}
                            }
                        });
                    }
                    InventoryPlus.LOGGER.info("[Pockets] Loaded disabled slots");
                }
        );
    }

    /**
     * Toggles a pocket panel. Hides all other pockets first so only
     * one is open at a time. Button visual state is driven automatically
     * by pressedWhen — no manual button state management needed.
     */
    public static void togglePocket(int hotbarIndex) {
        String panelName = "pocket_" + hotbarIndex;
        boolean isCurrentlyVisible = !MenuKit.isPanelInactive(panelName);

        // Hide all pockets
        for (int i = 0; i < 9; i++) {
            MenuKit.hidePanel("pocket_" + i);
        }

        // If the clicked pocket wasn't visible, show it
        if (!isCurrentlyVisible) {
            MenuKit.showPanel(panelName);
        }
    }
}
