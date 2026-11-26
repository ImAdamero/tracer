package pl.tracer.nametags;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Anty-kostka. Co tick zapamiętuje układ ekwipunku (hotbar + główny). Jeśli w JEDNYM ticku
 * zmieni się naraz wiele slotów, a przedmioty są te same (tylko inaczej poukładane), to jest
 * to wymieszanie przez event, a nie Twój ruch: układ sprzed ticku wraca natychmiast, w tym
 * samym ticku. Pojedyncze przekładanie przez Ciebie (1-3 sloty naraz) jest ignorowane,
 * więc możesz swobodnie edytować EQ także podczas walki.
 */
public final class AntiShuffle {
    private static final int SLOTS = 36; // hotbar 0-8 + główny ekwipunek 9-35

    /** Ile slotów musi zmienić się w jednym ticku, żeby uznać to za event. */
    private static final int NEED_CLOSED = 2; // bez otwartego ekranu Ty niczego nie przekładasz
    private static final int NEED_OPEN = 4;   // przy otwartym ekwipunku: pewnie nie Twoje kliknięcie

    private static List<ItemStack> prev;

    private AntiShuffle() {}

    public static void tick(MinecraftClient mc) {
        if (!TracerConfig.antiShuffle || mc.player == null || mc.world == null || mc.interactionManager == null) {
            prev = null;
            return;
        }

        List<ItemStack> cur = snapshot(mc);
        if (prev != null) {
            int need = mc.currentScreen == null ? NEED_CLOSED : NEED_OPEN;
            if (changedSlots(prev, cur) >= need && sameMultiset(prev, cur)) {
                int swaps = restore(mc, cur, prev);
                if (swaps > 0) {
                    mc.player.sendMessage(Text.literal("Anty-kostka: EQ przywrócone"), true);
                    prev = snapshot(mc); // klikanie zmienia ekwipunek lokalnie od razu
                    return;
                }
            }
        }
        prev = cur;
    }

    private static List<ItemStack> snapshot(MinecraftClient mc) {
        List<ItemStack> l = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) l.add(mc.player.getInventory().getStack(i).copy());
        return l;
    }

    private static int changedSlots(List<ItemStack> a, List<ItemStack> b) {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (!ItemStack.areEqual(a.get(i), b.get(i))) n++;
        return n;
    }

    private static boolean sameMultiset(List<ItemStack> a, List<ItemStack> b) {
        boolean[] used = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            boolean found = false;
            for (int j = 0; j < SLOTS; j++) {
                if (!used[j] && ItemStack.areEqual(a.get(i), b.get(j))) {
                    used[j] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** Zamienia 'cur' w 'target' od razu (wszystkie zamiany w tym samym ticku). Zwraca liczbę zamian. */
    private static int restore(MinecraftClient mc, List<ItemStack> cur, List<ItemStack> target) {
        List<ItemStack> w = new ArrayList<>(cur);
        int swaps = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (ItemStack.areEqual(w.get(i), target.get(i))) continue;
            for (int j = i + 1; j < SLOTS; j++) {
                if (ItemStack.areEqual(w.get(j), target.get(i))) {
                    swap(mc, i, j);
                    ItemStack t = w.get(i);
                    w.set(i, w.get(j));
                    w.set(j, t);
                    swaps++;
                    break;
                }
            }
        }
        return swaps;
    }

    /** Id slotu w aktualnie otwartym ekranie dla indeksu ekwipunku gracza (0-35). */
    private static int slotId(ScreenHandler h, MinecraftClient mc, int invIndex) {
        for (Slot s : h.slots) {
            if (s.inventory == mc.player.getInventory() && s.getIndex() == invIndex) return s.id;
        }
        return -1;
    }

    /** Zamienia zawartość dwóch slotów ekwipunku (indeksy 0-35) zwykłymi kliknięciami SWAP. */
    private static void swap(MinecraftClient mc, int i, int j) {
        ScreenHandler h = mc.player.currentScreenHandler;
        int sync = h.syncId;
        var im = mc.interactionManager;
        if (i < 9 && j < 9) {
            int id = slotId(h, mc, i);
            if (id >= 0) im.clickSlot(sync, id, j, SlotActionType.SWAP, mc.player);
        } else if (i < 9) {
            int id = slotId(h, mc, j);
            if (id >= 0) im.clickSlot(sync, id, i, SlotActionType.SWAP, mc.player);
        } else if (j < 9) {
            int id = slotId(h, mc, i);
            if (id >= 0) im.clickSlot(sync, id, j, SlotActionType.SWAP, mc.player);
        } else {
            // oba w głównym ekwipunku: przez hotbar 0 jako bufor
            int a = slotId(h, mc, i);
            int b = slotId(h, mc, j);
            if (a >= 0 && b >= 0) {
                im.clickSlot(sync, a, 0, SlotActionType.SWAP, mc.player);
                im.clickSlot(sync, b, 0, SlotActionType.SWAP, mc.player);
                im.clickSlot(sync, a, 0, SlotActionType.SWAP, mc.player);
            }
        }
    }
}
