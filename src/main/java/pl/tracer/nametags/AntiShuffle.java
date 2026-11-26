package pl.tracer.nametags;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Anty-kostka: gdy ekwipunek zostanie "wymieszany" poza Twoją wiedzą (te same przedmioty,
 * inne sloty, żaden ekran nie jest otwarty), układ jest przywracany zwykłymi kliknięciami
 * w ekwipunek. Przywraca zapisany układ EQ, a jeśli go nie ma lub nie pasuje - układ sprzed mieszania.
 */
public final class AntiShuffle {
    private static final int SLOTS = 36; // hotbar 0-8 + główny ekwipunek 9-35

    private static List<ItemStack> saved;
    private static List<ItemStack> prev;
    private static boolean screenWasOpen = true;
    private static final ArrayDeque<int[]> QUEUE = new ArrayDeque<>();

    private AntiShuffle() {}

    public static void saveNow(MinecraftClient mc) {
        if (mc.player == null) return;
        saved = snapshot(mc);
        mc.player.sendMessage(Text.literal("EQ zapisane"), true);
    }

    public static void tick(MinecraftClient mc) {
        if (!TracerConfig.antiShuffle || mc.player == null || mc.world == null || mc.interactionManager == null) {
            prev = null;
            QUEUE.clear();
            screenWasOpen = true;
            return;
        }

        // wykonuj zaplanowane zamiany po 2 na tick (żeby nie zalać serwera pakietami)
        if (!QUEUE.isEmpty()) {
            for (int n = 0; n < 2 && !QUEUE.isEmpty(); n++) {
                int[] sw = QUEUE.poll();
                swap(mc, sw[0], sw[1]);
            }
            if (QUEUE.isEmpty()) prev = snapshot(mc);
            return;
        }

        // gdy jest otwarty jakikolwiek ekran, to Ty przekładasz rzeczy - nie ruszamy
        if (mc.currentScreen != null) {
            screenWasOpen = true;
            prev = null;
            return;
        }

        List<ItemStack> cur = snapshot(mc);
        if (screenWasOpen || prev == null) {
            screenWasOpen = false;
            prev = cur;
            return;
        }

        // wymieszanie = te same przedmioty (z ilościami), ale min. 2 sloty zmieniły zawartość
        if (changedSlots(prev, cur) >= 2 && sameMultiset(prev, cur)) {
            List<ItemStack> target = (saved != null && sameMultiset(saved, cur)) ? saved : prev;
            plan(cur, target);
            if (!QUEUE.isEmpty()) {
                mc.player.sendMessage(Text.literal("Anty-kostka: przywracam EQ"), true);
                return; // prev zaktualizuje się po wykonaniu kolejki
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

    /** Układa listę zamian (selection sort), która zamieni 'cur' w 'target'. */
    private static void plan(List<ItemStack> cur, List<ItemStack> target) {
        List<ItemStack> w = new ArrayList<>(cur);
        for (int i = 0; i < SLOTS; i++) {
            if (ItemStack.areEqual(w.get(i), target.get(i))) continue;
            for (int j = i + 1; j < SLOTS; j++) {
                if (ItemStack.areEqual(w.get(j), target.get(i))) {
                    QUEUE.add(new int[]{i, j});
                    ItemStack t = w.get(i);
                    w.set(i, w.get(j));
                    w.set(j, t);
                    break;
                }
            }
        }
    }

    /** Zamienia zawartość dwóch slotów ekwipunku (indeksy 0-35) zwykłymi kliknięciami SWAP. */
    private static void swap(MinecraftClient mc, int i, int j) {
        var im = mc.interactionManager;
        int sync = mc.player.playerScreenHandler.syncId;
        if (i < 9 && j < 9) {
            im.clickSlot(sync, 36 + i, j, SlotActionType.SWAP, mc.player);
        } else if (i < 9) {
            im.clickSlot(sync, j, i, SlotActionType.SWAP, mc.player);
        } else if (j < 9) {
            im.clickSlot(sync, i, j, SlotActionType.SWAP, mc.player);
        } else {
            // oba w głównym ekwipunku: przez hotbar 0 jako bufor
            im.clickSlot(sync, i, 0, SlotActionType.SWAP, mc.player);
            im.clickSlot(sync, j, 0, SlotActionType.SWAP, mc.player);
            im.clickSlot(sync, i, 0, SlotActionType.SWAP, mc.player);
        }
    }
}
