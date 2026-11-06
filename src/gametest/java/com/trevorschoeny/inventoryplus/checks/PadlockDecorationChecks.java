package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.menuSlot;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.SlotMarks;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * The padlock's MenuKit over-decoration (7.5.0, replacing the old
 * {@code extractSlot} mixin): a locked slot's tooltip says "Locked" when
 * the mouse is on the padlock's own pixels, and nothing elsewhere on the
 * slot or once the lock comes off.
 */
public class PadlockDecorationChecks implements FabricClientGameTest {

    // Not slot 3: ModeChecks locks it as its own HOTBAR_LOCKED, and the
    // per-world lock file outlives any one check's world.
    private static final int HOTBAR_SLOT = 7;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);

            Slot slot = ctx.computeOnClient(mc -> menuSlot(mc.player, HOTBAR_SLOT));
            // Forced rather than assumed: nothing here depends on another
            // check's leftover lock state.
            ctx.runOnClient(mc -> LockedSlots.setLocked(slot, false));

            expect(ctx.computeOnClient(mc -> SlotMarks.DECORATION.tooltip(null, slot, slot.getItem(), 12, 3)) == null,
                    "an unlocked slot answers no padlock tooltip");

            ctx.runOnClient(mc -> LockedSlots.setLocked(slot, true));
            expect(ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(slot)), "the hotbar slot locks");

            List<String> onPadlock = ctx.computeOnClient(mc ->
                    SlotMarks.DECORATION.tooltip(null, slot, slot.getItem(), 12, 3))
                    .stream().map(Component::getString).toList();
            expect(onPadlock.equals(List.of("Locked")),
                    "the mouse on the padlock's pixels says Locked (" + onPadlock + ")");

            expect(ctx.computeOnClient(mc -> SlotMarks.DECORATION.tooltip(null, slot, slot.getItem(), 8, 8)) == null,
                    "the mouse elsewhere on the locked slot answers no padlock tooltip");

            ctx.runOnClient(mc -> LockedSlots.setLocked(slot, false));
            expect(ctx.computeOnClient(mc -> SlotMarks.DECORATION.tooltip(null, slot, slot.getItem(), 12, 3)) == null,
                    "the padlock tooltip goes once the slot unlocks");
        }
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Padlock decoration: " + what);
    }
}
