package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.hotbarcycler.HotbarCycler;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;
import com.trevorschoeny.inventoryplus.toolbar.PowerUsersToolbar;
import com.trevorschoeny.inventoryplus.toolbar.Toolbar;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Locks and Reach hold in survival and creative alike, and the
 * inventory toolbar and column show wherever the main inventory is (survival,
 * creative's inventory tab, a chest) and nowhere else (creative's item tabs).
 * The gesture is Q on a slot, which the default Slot lock stops, sent through
 * the screen's own {@code slotClicked}, so creative's is judged at creative's
 * seam. On creative's tabs the lock and cycle keys work, and from gameplay in
 * creative both cyclers turn on the server.
 */
public class ModeChecks implements FabricClientGameTest {

    /** Locked main inventory slot, and its unlocked neighbour. */
    private static final int MAIN_LOCKED = 20, MAIN_FREE = 21;
    /** Locked hotbar slot, and its unlocked neighbour: the only player slots on an item tab. */
    private static final int HOTBAR_LOCKED = 3, HOTBAR_FREE = 4;

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            stock(world);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                String slot = Reach.defaultOf(LockKind.SLOT);
                LockedSlots.setLockedSlot(CarriedItems.menuSlot(mc.player, MAIN_LOCKED), slot);
                LockedSlots.setLockedSlot(CarriedItems.menuSlot(mc.player, HOTBAR_LOCKED), slot);
            });

            // Survival inventory.
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(5);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "survival: the inventory is open");
            expectShown(true, true, "survival inventory");
            expectRefused(MAIN_LOCKED, "survival: a locked main slot");
            expectRefused(HOTBAR_LOCKED, "survival: a locked hotbar slot");
            expectTaken(MAIN_FREE, "survival: an unlocked main slot");
            sortChecks(world, "survival");
            ctx.setScreen(() -> null);
            stock(world);

            // Creative: the inventory opens as the creative screen.
            world.getServer().runCommand("gamemode creative @a");
            ctx.waitTicks(6);
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(5);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof CreativeModeInventoryScreen),
                    "creative: the inventory opens as the creative screen");

            selectTab(CreativeModeTabs.INVENTORY);
            // The column shows; the toolbar has nothing to show, since Sort sits out
            // there and Move Matching is for containers.
            expectShown(false, true, "creative's inventory tab");
            expectRefused(MAIN_LOCKED, "creative inventory tab: a locked main slot");
            expectRefused(HOTBAR_LOCKED, "creative inventory tab: a locked hotbar slot");
            expectTaken(MAIN_FREE, "creative inventory tab: an unlocked main slot");
            keyChecks(MAIN_FREE, "creative inventory tab");

            selectTab(CreativeModeTabs.BUILDING_BLOCKS);
            expectShown(false, false, "creative item tab");
            expectRefused(HOTBAR_LOCKED, "creative item tab: a locked hotbar slot");
            expectTaken(HOTBAR_FREE, "creative item tab: an unlocked hotbar slot");
            // The lock key works on the hotbar, the only player slots an item tab has.
            lockKeyCheck(HOTBAR_FREE, "creative item tab");

            // Sort from creative's inventory tab lands on the server: the scattered
            // stacks merge, the locked slot keeps its own.
            selectTab(CreativeModeTabs.INVENTORY);
            // Sort sits out on creative's inventory tab: its clicks don't reach
            // creative's item-picker menu (Sorter.canSort; a SlotKit gap).
            ctx.runOnClient(mc -> expect(!com.trevorschoeny.inventoryplus.sort.Sorter.canSort(
                            ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu()),
                    "creative inventory tab: Sort sits out"));
            // Closed as a player closes it (E or Escape run onClose, which hands the
            // player back the inventory menu); setScreen(null) would leave creative's
            // item-picker menu in place.
            ctx.runOnClient(mc -> mc.gui.screen().onClose());
            ctx.waitTicks(3);

            // The cyclers turn in creative from gameplay, on the server.
            cyclerChecks(world);

            // A chest while in creative: both toolbars, and the locks still hold.
            stock(world);
            BlockPos pos = onServerGet(world, p -> {
                BlockPos at = p.blockPosition().offset(2, 0, 0);
                p.level().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                return at;
            });
            ctx.waitTicks(5);
            onServer(world, p -> p.openMenu((MenuProvider) p.level().getBlockEntity(pos)));
            ctx.waitTicks(8);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "creative: the chest is open");
            ctx.runOnClient(mc -> {
                expect(Toolbar.inventoryToolbar().placedBounds() != null, "a chest in creative: the inventory toolbar shows");
                expect(Toolbar.externalToolbars().stream().anyMatch(t -> t.placedBounds() != null),
                        "a chest in creative: the chest's Sort toolbar shows");
            });
            expectRefused(MAIN_LOCKED, "a chest in creative: a locked main slot");
            expectTaken(MAIN_FREE, "a chest in creative: an unlocked main slot");
            ctx.setScreen(() -> null);

            ctx.runOnClient(mc -> {
                LockedSlots.setLockedSlot(CarriedItems.menuSlot(mc.player, MAIN_LOCKED), null);
                LockedSlots.setLockedSlot(CarriedItems.menuSlot(mc.player, HOTBAR_LOCKED), null);
            });
        }
    }

    /**
     * Sort by key with the mouse off the slots, which sorts the inventory: the
     * scattered dirt merges into one stack on the server, and the locked
     * slot's stone stays where it is.
     */
    private void sortChecks(TestSingleplayerContext world, String where) {
        onServer(world, p -> {
            clear(p);
            p.getInventory().setItem(MAIN_LOCKED, new ItemStack(Items.STONE, 10));
            p.getInventory().setItem(30, new ItemStack(Items.DIRT, 5));
            p.getInventory().setItem(12, new ItemStack(Items.DIRT, 7));
        });
        ctx.waitTicks(10);
        ctx.getInput().setCursorPos(1, 1);   // off every slot: the key sorts the inventory
        ctx.getInput().pressKey(IPKeybinds.SORT);
        ctx.waitTicks(15);
        String client = ctx.computeOnClient(mc -> dirtStacks(mc.player.getInventory()));
        onServer(world, p -> {
            String server = dirtStacks(p.getInventory());
            expect(server.equals("1x12"), where + ": Sort merges the dirt on the server (server "
                    + server + ", client " + client + ")");
            expect(p.getInventory().getItem(MAIN_LOCKED).is(Items.STONE)
                            && p.getInventory().getItem(MAIN_LOCKED).getCount() == 10,
                    where + ": Sort leaves the locked slot's stone where it is");
        });
    }

    /**
     * The keys on an open screen: L locks the slot under the mouse and L again
     * unlocks it; C adds that slot to its column's cycle (Column Cycler on).
     * Neither sends a click, so both work on creative's tabs today.
     */
    private void keyChecks(int index, String where) {
        lockKeyCheck(index, where);
        ctx.runOnClient(mc -> {
            IPConfig.setColumnCyclerEnabled(true);
            ColumnCycler.setCycle(index, false);
        });
        hover(index);
        ctx.getInput().pressKey(IPKeybinds.CYCLE_SLOT);
        ctx.waitTicks(4);
        expect(ctx.computeOnClient(mc -> ColumnCycler.isCycleSlot(index)),
                where + ": C adds the slot under the mouse to its column's cycle");
        ctx.runOnClient(mc -> {
            ColumnCycler.setCycle(index, false);
            IPConfig.setColumnCyclerEnabled(false);
        });
    }

    private void lockKeyCheck(int index, String where) {
        hover(index);
        ctx.getInput().pressKey(IPKeybinds.LOCK_SLOT);
        ctx.waitTicks(4);
        expect(isLocked(index), where + ": L locks the slot under the mouse");
        ctx.getInput().pressKey(IPKeybinds.LOCK_SLOT);
        ctx.waitTicks(4);
        expect(!isLocked(index), where + ": L again unlocks it");
    }

    private boolean isLocked(int index) {
        return ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(
                invSlot(((AbstractContainerScreen<?>) mc.gui.screen()).getMenu(), mc.player, index)));
    }

    /**
     * In creative, from gameplay: the column key turns a column's cycle and the
     * hotbar key turns a row through the hotbar, and the server has the result.
     */
    private void cyclerChecks(TestSingleplayerContext world) {
        int top = 9 + 4, row0col6 = HotbarCycler.firstSlotOf(0) + 6;
        onServer(world, p -> {
            clear(p);
            p.getInventory().setItem(top, new ItemStack(Items.COBBLESTONE, 3));
            p.getInventory().setItem(4, new ItemStack(Items.DIRT, 7));
            p.getInventory().setItem(row0col6, new ItemStack(Items.OAK_LOG, 2));
            p.getInventory().setItem(6, new ItemStack(Items.SAND, 5));
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {
            IPConfig.setColumnCyclerEnabled(true);
            ColumnCycler.setCycle(top, true);
            mc.player.getInventory().setSelectedSlot(4);
        });
        ctx.getInput().pressKey(IPKeybinds.CYCLE_FORWARD);
        ctx.waitTicks(10);
        String client = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(4) + " / "
                + mc.player.getInventory().getItem(top) + " sel " + mc.player.getInventory().getSelectedSlot()
                + " cycle " + ColumnCycler.isCycleSlot(top) + " menu " + mc.player.containerMenu.getClass().getSimpleName()
                + " screen " + mc.gui.screen());
        onServer(world, p -> {
            expect(p.getInventory().getItem(4).is(Items.COBBLESTONE) && p.getInventory().getItem(top).is(Items.DIRT),
                    "creative, gameplay: the column key brings the column's item into the hotbar (server "
                            + p.getInventory().getItem(4) + " / " + p.getInventory().getItem(top) + "; client " + client + ")");
        });
        ctx.runOnClient(mc -> {
            ColumnCycler.setCycle(top, false);
            IPConfig.setColumnCyclerEnabled(false);
            IPConfig.setHotbarCyclerEnabled(true);
            HotbarCycler.setRow(0, true);
        });
        ctx.getInput().pressKey(IPKeybinds.HOTBAR_CYCLE_FORWARD);
        ctx.waitTicks(10);
        onServer(world, p -> {
            expect(p.getInventory().getItem(6).is(Items.OAK_LOG) && p.getInventory().getItem(row0col6).is(Items.SAND),
                    "creative, gameplay: the hotbar key turns the row through the hotbar");
        });
        ctx.runOnClient(mc -> {
            HotbarCycler.setRow(0, false);
            IPConfig.setHotbarCyclerEnabled(false);
        });
    }

    /** Puts the cursor over the open screen's slot for inventory slot {@code index}. */
    private void hover(int index) {
        double[] at = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot slot = invSlot(screen.getMenu(), mc.player, index);
            double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{(ScreenLayout.leftPos(screen) + slot.x + 8) * scale,
                    (ScreenLayout.topPos(screen) + slot.y + 8) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
    }

    /** "stacks x total" of the dirt in the main inventory. */
    private static String dirtStacks(Inventory inv) {
        int stacks = 0, total = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.DIRT)) { stacks++; total += s.getCount(); }
        }
        return stacks + "x" + total;
    }

    /** Stone in the locked slots, dirt in the free ones, on the server. */
    private void stock(TestSingleplayerContext world) {
        onServer(world, p -> {
            clear(p);
            p.getInventory().setItem(MAIN_LOCKED, new ItemStack(Items.STONE, 10));
            p.getInventory().setItem(MAIN_FREE, new ItemStack(Items.DIRT, 7));
            p.getInventory().setItem(HOTBAR_LOCKED, new ItemStack(Items.STONE, 10));
            p.getInventory().setItem(HOTBAR_FREE, new ItemStack(Items.DIRT, 7));
        });
        ctx.waitTicks(5);
    }

    private static <T> T onServerGet(TestSingleplayerContext world, Function<ServerPlayer, T> f) {
        return world.getServer().computeOnServer(server -> f.apply(server.getPlayerList().getPlayers().get(0)));
    }

    /** The inventory toolbar and the column each show or not on the open screen. */
    private void expectShown(boolean toolbarShown, boolean columnShown, String where) {
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            Panel toolbar = Toolbar.inventoryToolbar(), column = PowerUsersToolbar.panel();
            expect((toolbar.placedBounds() != null) == toolbarShown,
                    where + ": the inventory toolbar " + (toolbarShown ? "shows" : "is absent"));
            expect((column.placedBounds() != null) == columnShown,
                    where + ": the feature column " + (columnShown ? "shows" : "is absent"));
        });
    }

    // Q on a slot (drop one): the default Slot lock stops it, a plain pickup it allows.

    private void expectRefused(int index, String what) {
        expect(drop(index) == 0, what + " refuses Q");
    }

    private void expectTaken(int index, String what) {
        expect(drop(index) == 1, what + ": Q drops one");
    }

    /** Q on the open screen's slot for inventory slot {@code index}, as the screen does it; how many it dropped. */
    private int drop(int index) {
        int before = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(index).getCount());
        click(index, ContainerInput.THROW);
        return before - ctx.computeOnClient(mc -> mc.player.getInventory().getItem(index).getCount());
    }

    private void click(int index, ContainerInput input) {
        ctx.runOnClient(mc -> {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot slot = invSlot(screen.getMenu(), mc.player, index);
            try {
                SLOT_CLICKED.invoke(screen, slot, slot.index, 0, input);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Modes: clicking slot " + index, e);
            }
        });
        ctx.waitTicks(3);
    }

    private void selectTab(ResourceKey<CreativeModeTab> key) {
        ctx.runOnClient(mc -> {
            try {
                SELECT_TAB.invoke(mc.gui.screen(), BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(key));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Modes: selecting tab " + key, e);
            }
        });
        ctx.waitTicks(3);
    }

    /** The slot on {@code menu} holding the player's inventory slot {@code index}. */
    private static Slot invSlot(AbstractContainerMenu menu, Player player, int index) {
        for (Slot slot : menu.slots) {
            SlotRef ref = SlotRef.of(menu, slot, player);
            if (ref.container() instanceof Inventory && ref.containerSlot() == index) return slot;
        }
        throw new AssertionError("Modes: no slot for inventory slot " + index + " on " + menu);
    }

    // The screens' own click and tab methods, as a player's click reaches them.
    // ponytail: reflection by name, since the game is unobfuscated in dev; an
    // invoker mixin in the checks if a rename ever breaks it.
    private static final Method SLOT_CLICKED = method(AbstractContainerScreen.class, "slotClicked",
            Slot.class, int.class, int.class, ContainerInput.class);
    private static final Method SELECT_TAB = method(CreativeModeInventoryScreen.class, "selectTab", CreativeModeTab.class);

    private static Method method(Class<?> owner, String name, Class<?>... args) {
        try {
            Method m = owner.getDeclaredMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Modes: " + what);
    }
}
