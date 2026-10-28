import com.trevorschoeny.inventoryplus.lockgroups.*;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.google.gson.*;
import java.util.*;

public class ReachCheck {
    static int n = 0;
    static void check(boolean ok, String what) { n++; if (!ok) throw new AssertionError("FAIL " + what); }
    public static void main(String[] a) {
        int[] saves = {0};
        Reach.onChange(() -> saves[0]++);
        Reach.read(null);
        // defaults
        check(Reach.groups().size() == 2, "two defaults");
        check(Reach.lockDenies("slot_lock", "inventoryplus:sort"), "slot lock stops sort by default");
        check(!Reach.lockDenies("slot_lock", "inventoryplus:auto_tool_switch"), "slot lock allows ATS");
        check(Reach.lockDenies("exact_item", "menukit:drop"), "exact stops drop");
        check(!Reach.lockDenies("exact_item", "menukit:shift_click_in"), "exact allows shift-click in");
        check(!Reach.denies("inventoryplus:sort", "vanilla|menukit|player_hotbar"), "no slot group denied by default");
        check(Reach.active().id().equals("slot_lock"), "active default");
        // custom group copies kind default on untouched ops
        LockGroup g = Reach.createGroup(LockKind.SLOT, "Mine", "red");
        check(g.id().equals("g1"), "first custom id g1");
        check(Reach.lockDenies("g1", "inventoryplus:sort"), "new slot group copies default on untouched op");
        check(!Reach.lockDenies("g1", "inventoryplus:auto_tool_switch"), "and its allows");
        // an edited op: entry exact; a later group copies membership of its kind's default
        Reach.setLockDenies("slot_lock", "inventoryplus:sort", false);
        check(!Reach.lockDenies("slot_lock", "inventoryplus:sort"), "entry turns slot lock off for sort");
        check(Reach.lockDenies("g1", "inventoryplus:sort"), "g1 kept its own choice in the written entry");
        check(Reach.lockDenies("exact_item", "inventoryplus:sort"), "exact kept in entry");
        LockGroup g2 = Reach.createGroup(LockKind.SLOT, "Two", "blue");
        check(!Reach.lockDenies("g2", "inventoryplus:sort"), "g2 copies slot_lock's (off) membership in the entry");
        check(Reach.lockDenies("g2", "menukit:drop"), "g2 copies default on untouched op");
        // item kind copies exact item
        LockGroup gi = Reach.createGroup(LockKind.ITEM, "Items", "gray");
        check(Reach.lockDenies(gi.id(), "menukit:drop") && !Reach.lockDenies(gi.id(), "inventoryplus:restock_take"), "item copies exact");
        // resolve orphans
        check(Reach.resolve("nope", LockKind.SLOT).id().equals("slot_lock"), "orphan slot -> slot_lock");
        check(Reach.resolve("g1", LockKind.EXACT).id().equals("exact_item"), "kind mismatch -> default of kind");
        check(Reach.resolve("nope", LockKind.ITEM).id().equals("exact_item"), "orphan item -> exact_item");
        // cycle
        check(Reach.cycleActive(1).id().equals("exact_item"), "cycle next");
        check(Reach.cycleActive(-1).id().equals("slot_lock"), "cycle back");
        check(Reach.cycleActive(-1).id().equals(gi.id()), "wraps");
        // round trip keeps unknown keys
        JsonObject json = Reach.write();
        json.getAsJsonObject("denied").add("othermod:op", JsonParser.parseString("[\"othermod:group\",\"inventoryplus:lock/slot_lock\"]"));
        Reach.read(json);
        check(Reach.denies("othermod:op", "othermod:group"), "unknown op and key kept and readable");
        check(Reach.write().getAsJsonObject("denied").has("othermod:op"), "unknown written back");
        check(Reach.groups().size() == 5, "groups round trip");
        check(Reach.active().id().equals(gi.id()), "active round trip");
        check(Reach.write().getAsJsonArray("groups").get(0).getAsJsonObject().get("key").getAsString().equals("key.inventoryplus.lock_slot"), "slot lock key");
        check(Reach.write().getAsJsonArray("groups").get(1).getAsJsonObject().get("key").isJsonNull(), "exact key null");
        // a hand-edited file missing the defaults still has them
        Reach.read(JsonParser.parseString("{\"groups\":[{\"id\":\"g5\",\"kind\":\"exact\",\"name\":\"x\"}]}").getAsJsonObject());
        check(Reach.groups().size() == 3 && Reach.groups().get(0).id().equals("slot_lock"), "defaults restored");
        check(Reach.createGroup(LockKind.SLOT, "n", "gray").id().equals("g6"), "ids never below the max");
        // ── stage 3 writes ──
        Reach.read(null);
        check(Reach.lockDenies("exact_item", "inventorymax:pocket_cycle"), "exact stops pocket cycle (09-27)");
        Reach.setDenied("inventoryplus:sort", List.of("vanilla|menukit|chest_storage"), true);
        check(Reach.denies("inventoryplus:sort", "vanilla|menukit|chest_storage"), "slot group denied");
        check(Reach.lockDenies("slot_lock", "inventoryplus:sort"), "writing an entry keeps the lock defaults");
        Reach.setDenied("inventoryplus:sort", List.of("a|x", "a|y"), true);
        check(Reach.denies("inventoryplus:sort", "a|y"), "a set writes every member");
        Reach.revert("inventoryplus:sort");
        check(!Reach.hasEntry("inventoryplus:sort") && !Reach.denies("inventoryplus:sort", "a|y"), "revert drops the entry");
        LockGroup c = Reach.createGroup(LockKind.SLOT, "Mine", "red");
        Reach.setLockDenies(c.id(), "menukit:drop", false);
        check(Reach.effectiveDenied("menukit:drop").contains("inventoryplus:lock/slot_lock"), "entry written");
        Reach.rename(c.id(), "  Renamed ");
        check(Reach.group(c.id()).name().equals("Renamed"), "rename trims");
        Reach.rename(c.id(), "   ");
        check(Reach.group(c.id()).name().equals("Renamed"), "blank rename ignored");
        Reach.recolour(c.id(), "blue");
        check(Reach.group(c.id()).colour().equals("blue"), "recolour");
        Reach.recolour(c.id(), "not_a_dye");
        check(Reach.group(c.id()).colour().equals("blue"), "unknown dye ignored");
        check(!Reach.nextColour().equals("blue") && !Reach.nextColour().equals("gray"), "next colour unused");
        Reach.cycleActive(-1);
        Reach.deleteGroup(c.id());
        check(Reach.group(c.id()) == null, "deleted");
        check(Reach.write().toString().indexOf("lock/" + c.id()) < 0, "key scrubbed from every entry");
        check(Reach.active().id().equals("slot_lock"), "active falls back when its group goes");
        Reach.deleteGroup("slot_lock");
        check(Reach.group("slot_lock") != null, "defaults cannot be deleted");
        LockGroup d = Reach.createGroup(LockKind.ITEM, "X", "red");
        Reach.rename("slot_lock", "Mine now");
        Reach.setDenied("menukit:drop", List.of("q|q"), true);
        Reach.resetGroups();
        check(Reach.groups().size() == 2 && Reach.group("slot_lock").name().equals("Slot lock"), "reset groups");
        check(Reach.write().toString().indexOf("lock/" + d.id()) < 0, "reset scrubs custom keys");
        check(Reach.denies("menukit:drop", "q|q"), "reset groups leaves reach choices");
        Reach.revertAll();
        check(!Reach.hasEntry("menukit:drop"), "revert all");
        Reach.markSeen(List.of("menukit:drop"));
        check(Reach.seen("menukit:drop") && !Reach.seen("menukit:drag_fill"), "seen");
        // MenuKit 6.0.0: saved keys from before it still mean the same thing
        try {
            java.lang.reflect.Method key = Reach.class.getDeclaredMethod("key", String.class);
            key.setAccessible(true);
            String hotbar = com.trevlar.menukit.api.slot.SlotGroupId.parse("vanilla|menukit|player_hotbar").asString();
            check(hotbar.startsWith("category|"), "6.0.0 writes a category group as category|");
            check(key.invoke(null, "vanilla|menukit|player_hotbar").equals(hotbar), "a 5.x slot group key reads as 6.0.0's");
            check(key.invoke(null, "inventoryplus:lock/slot_lock").equals("inventoryplus:lock/slot_lock"), "other keys kept");
            Reach.read(JsonParser.parseString("{\"denied\":{\"inventoryplus:sort\":[\"vanilla|menukit|player_hotbar\"]}}").getAsJsonObject());
            check(Reach.denies("inventoryplus:sort", hotbar), "a 5.x denial still denies after 6.0.0");

            Class<?> csk = Class.forName("com.trevorschoeny.inventoryplus.lockedslots.CreatedSlotKey");
            java.lang.reflect.Method migrate = csk.getDeclaredMethod("migrate", String.class);
            migrate.setAccessible(true);
            String pocket = com.trevlar.menukit.api.window.Address.createdSlot(
                    com.trevlar.menukit.api.slot.SlotGroupId.created("inventorymax:pocket_0_0", "pocket_0_0"), 0).asString();
            check(migrate.invoke(null, "created:1:menukit:panel:primary:inventorymax:pocket_0_0:pocket_0_0\u00000").equals(pocket),
                    "a 5.x created-slot key (as saved in IM's dev run) becomes the pocket's address text");
            check(com.trevlar.menukit.api.window.Address.parse(pocket).asString().equals(pocket), "and that text reads back");
            check(migrate.invoke(null, "created:1:menukit:panel:primary:inventorymax:equip_elytra:elytra\u00007").equals(
                    com.trevlar.menukit.api.window.Address.createdSlot(com.trevlar.menukit.api.slot.SlotGroupId.created(
                            "inventorymax:equip_elytra", "elytra"), 7).asString()), "any group and index");
            check(migrate.invoke(null, pocket).equals(pocket), "a 6.0.0 key is kept");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        System.out.println("PASS " + n + " checks, " + saves[0] + " saves");
    }
}
