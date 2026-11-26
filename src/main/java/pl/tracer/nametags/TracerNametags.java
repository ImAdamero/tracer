package pl.tracer.nametags;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TracerNametags implements ClientModInitializer {

    private static final String MOD_ID = "tracer-nametags";

    private static KeyBinding toggleKey;
    private static KeyBinding menuKey;
    private static KeyBinding freecamKey;
    private static KeyBinding saveEqKey;
    private static KeyBinding antiKey;
    private static boolean enabled = false;

    private record Line(String text, int color) {}

    /** Ostatnia znana pozycja gracza (gdy serwer przestał go wysyłać). */
    private record Seen(String name, Vec3d pos, long timeMs, Object dim, float hp, List<ItemStack> stacks) {}

    private record Row(String name, Vec3d pos, long ageSec, double dist) {}

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    @Override
    public void onInitializeClient() {
        TracerConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tracernametags.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tracernametags.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        freecamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tracernametags.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, category));

        saveEqKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tracernametags.saveeq", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        antiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tracernametags.antishuffle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientTickEvents.START_CLIENT_TICK.register(Freecam::tick);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AntiShuffle.tick(client);
            while (saveEqKey.wasPressed()) AntiShuffle.saveNow(client);
            while (antiKey.wasPressed()) {
                TracerConfig.antiShuffle = !TracerConfig.antiShuffle;
                TracerConfig.save();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Anty-kostka: " + (TracerConfig.antiShuffle ? "WŁĄCZONA" : "WYŁĄCZONA")), true);
                }
            }
            while (freecamKey.wasPressed()) {
                Freecam.toggle(client);
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Freecam: " + (Freecam.isActive() ? "WŁĄCZONY" : "WYŁĄCZONY")), true);
                }
            }
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Tracery: " + (enabled ? "WŁĄCZONE" : "WYŁĄCZONE")), true);
                }
            }
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ConfigScreen(null));
                }
            }
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of(MOD_ID, "overlay"),
                TracerNametags::render);
    }

    private static void render(DrawContext g, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) SEEN.clear();
        if (!enabled) return;
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;

        float tickDelta = tickCounter.getTickProgress(true);
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;

        int sw = g.getScaledWindowWidth();
        int sh = g.getScaledWindowHeight();
        float aspect = (float) sw / (float) sh;
        double fovRad = Math.toRadians(mc.options.getFov().getValue());
        float f = (float) (1.0 / Math.tan(fovRad / 2.0));

        Quaternionf inv = new Quaternionf(camera.getRotation()).conjugate();
        Vec3d camPos = camera.getCameraPos();

        // zasięgi liczone w chunkach (1 chunk = 16 kratek), w poziomie
        double tracerBlocks = TracerConfig.tracerRange * 16.0;
        double tagBlocks = TracerConfig.nametagRange * 16.0;
        double tracerRange2 = tracerBlocks * tracerBlocks;
        double tagRange2 = tagBlocks * tagBlocks;
        int color = TracerConfig.COLORS[TracerConfig.tracerColor];

        long nowMs = System.currentTimeMillis();
        Object dim = mc.world.getRegistryKey();
        Set<UUID> visible = new HashSet<>();
        List<Row> rows = new ArrayList<>();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !Freecam.isActive()) continue;

            Vec3d base = p.getLerpedPos(tickDelta);
            double ddx = base.x - camPos.x;
            double ddz = base.z - camPos.z;
            double dist2 = ddx * ddx + ddz * ddz;

            String pname = p.getName().getString();
            float php = p.getHealth() + p.getAbsorptionAmount();
            List<ItemStack> eq = equipment(p);
            if (p != mc.player) {
                // daleko serwer często nie wysyła ekwipunku - wtedy używamy ostatnio znanego
                Seen old = SEEN.get(p.getUuid());
                if (allEmpty(eq) && old != null && !allEmpty(old.stacks())) eq = old.stacks();
                visible.add(p.getUuid());
                SEEN.put(p.getUuid(), new Seen(pname, base, nowMs, dim, php, eq));
                rows.add(new Row(pname, base, 0L, Math.sqrt(dist2)));
            }

            boolean doTracer = TracerConfig.showTracers && dist2 <= tracerRange2;
            boolean doTag = TracerConfig.showNametags && dist2 <= tagRange2;
            if (!doTracer && !doTag) continue;

            if (doTracer) {
                float[] body = project(base.add(0, p.getHeight() * 0.5, 0), camPos, inv, f, aspect, sw, sh);
                if (body != null) {
                    // linia wychodzi ze środka ekranu (celownika)
                    drawLine(g, sw / 2f, sh / 2f, body[0], body[1], TracerConfig.tracerThickness, color);
                }
            }
            if (doTag) {
                float[] head = project(base.add(0, p.getHeight() + 0.4, 0), camPos, inv, f, aspect, sw, sh);
                if (head != null) {
                    float sc = TracerConfig.tagScale / 100f;
                    g.getMatrices().pushMatrix();
                    g.getMatrices().translate(head[0], head[1]);
                    g.getMatrices().scale(sc, sc);
                    drawTag(g, mc, pname, php, eq, base, 0, 0);
                    g.getMatrices().popMatrix();
                }
            }
        }

        // gracze zapamiętani: serwer przestał ich wysyłać, ale jeszcze są online
        long memMs = TracerConfig.memorySeconds * 1000L;
        if (memMs > 0 && mc.getNetworkHandler() != null) {
            Iterator<Map.Entry<UUID, Seen>> it = SEEN.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Seen> en = it.next();
                Seen s = en.getValue();
                if (visible.contains(en.getKey())) continue;
                long age = nowMs - s.timeMs();
                if (age > memMs || mc.getNetworkHandler().getPlayerListEntry(en.getKey()) == null) {
                    it.remove();
                    continue;
                }
                if (!dim.equals(s.dim())) continue;

                double ddx = s.pos().x - camPos.x;
                double ddz = s.pos().z - camPos.z;
                double dist2 = ddx * ddx + ddz * ddz;
                long ageSec = age / 1000;
                rows.add(new Row(s.name(), s.pos(), ageSec, Math.sqrt(dist2)));

                if (TracerConfig.showTracers && dist2 <= tracerRange2) {
                    float[] body = project(s.pos().add(0, 0.9, 0), camPos, inv, f, aspect, sw, sh);
                    if (body != null) {
                        // tracer stopniowo blaknie z czasem od ostatniego zobaczenia
                        double frac = Math.min(1.0, age / (double) memMs);
                        int al = 0xFF - (int) (0xAA * frac);
                        drawLine(g, sw / 2f, sh / 2f, body[0], body[1], TracerConfig.tracerThickness,
                                (al << 24) | (color & 0x00FFFFFF));
                        g.fill((int) body[0] - 2, (int) body[1] - 2, (int) body[0] + 2, (int) body[1] + 2, 0xFFFFD27F);
                    }
                }
                if (TracerConfig.showNametags && dist2 <= tagRange2) {
                    float[] head = project(s.pos().add(0, 2.2, 0), camPos, inv, f, aspect, sw, sh);
                    if (head != null) {
                        float sc = TracerConfig.tagScale / 100f;
                        g.getMatrices().pushMatrix();
                        g.getMatrices().translate(head[0], head[1]);
                        g.getMatrices().scale(sc, sc);
                        drawTag(g, mc, s.name() + " (" + ageSec + "s)", s.hp(), s.stacks(), s.pos(), 0, 0);
                        g.getMatrices().popMatrix();
                    }
                }
            }
        } else if (memMs <= 0) {
            SEEN.clear();
        }

        if (TracerConfig.showList && !rows.isEmpty()) drawList(g, mc, rows);
    }

    private static String coords(Vec3d v) {
        return (int) Math.floor(v.x) + " " + (int) Math.floor(v.y) + " " + (int) Math.floor(v.z);
    }

    /** Znacznik ostatniej znanej pozycji gracza. */
    private static void drawMarker(DrawContext g, MinecraftClient mc, String text, float x, float y) {
        float sc = TracerConfig.tagScale / 100f;
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(x, y);
        g.getMatrices().scale(sc, sc);
        int w = mc.textRenderer.getWidth(text);
        g.fill(-2, -2, 2, 2, 0xFFFFD27F);
        g.fill(-w / 2 - 2, -14, w / 2 + 2, -3, 0x90000000);
        g.drawText(mc.textRenderer, text, -w / 2, -13, 0xFFFFD27F, true);
        g.getMatrices().popMatrix();
    }

    /** Lista graczy z koordynatami w lewym górnym rogu (najbliżsi na górze). */
    private static void drawList(DrawContext g, MinecraftClient mc, List<Row> rows) {
        rows.sort(Comparator.comparingDouble(Row::dist));
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(4f, 4f);
        g.getMatrices().scale(0.8f, 0.8f);
        int n = Math.min(rows.size(), 10);
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            String t = r.name() + "  " + coords(r.pos()) + "  " + (int) r.dist() + "m"
                    + (r.ageSec() > 0 ? "  (" + r.ageSec() + "s)" : "");
            int w = mc.textRenderer.getWidth(t);
            g.fill(-1, i * 11 - 1, w + 2, i * 11 + 9, 0x90000000);
            g.drawText(mc.textRenderer, t, 0, i * 11, r.ageSec() > 0 ? 0xFFFFD27F : 0xFFFFFFFF, true);
        }
        g.getMatrices().popMatrix();
    }

    /** Rzut punktu świata na współrzędne ekranu (GUI). Zwraca null, gdy punkt jest za kamerą. */
    private static float[] project(Vec3d world, Vec3d camPos, Quaternionf inv,
                                   float f, float aspect, int sw, int sh) {
        Vector3f v = new Vector3f(
                (float) (world.x - camPos.x),
                (float) (world.y - camPos.y),
                (float) (world.z - camPos.z));
        inv.transform(v);
        if (v.z >= -0.05f) return null; // kamera patrzy w -Z
        float ndcX = (v.x / -v.z) * f / aspect;
        float ndcY = (v.y / -v.z) * f;
        return new float[]{(ndcX * 0.5f + 0.5f) * sw, (0.5f - ndcY * 0.5f) * sh};
    }

    /** Jedna linia = jeden obrócony prostokąt (jedno wywołanie fill), więc bez lagów. */
    private static void drawLine(DrawContext g, float x1, float y1, float x2, float y2, int thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float angle = (float) Math.atan2(dy, dx);
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(x1, y1);
        g.getMatrices().rotate(angle);
        int t = Math.max(1, thickness);
        g.fill(0, -(t / 2), Math.round(len), t - (t / 2), color);
        g.getMatrices().popMatrix();
    }

    private static List<ItemStack> equipment(AbstractClientPlayerEntity p) {
        List<ItemStack> l = new ArrayList<>(6);
        l.add(p.getMainHandStack().copy());
        l.add(p.getEquippedStack(EquipmentSlot.HEAD).copy());
        l.add(p.getEquippedStack(EquipmentSlot.CHEST).copy());
        l.add(p.getEquippedStack(EquipmentSlot.LEGS).copy());
        l.add(p.getEquippedStack(EquipmentSlot.FEET).copy());
        l.add(p.getOffHandStack().copy());
        return l;
    }

    private static boolean allEmpty(List<ItemStack> l) {
        for (ItemStack s : l) if (!s.isEmpty()) return false;
        return true;
    }

    /** equip: [0]=ręka, [1..4]=hełm,napierśnik,spodnie,buty, [5]=druga ręka. */
    private static void drawTag(DrawContext g, MinecraftClient mc, String name, float hp,
                                List<ItemStack> equip, Vec3d pos, int cx, int bottom) {
        List<ItemStack> stacks = new ArrayList<>();
        if (TracerConfig.showHeld) stacks.add(equip.get(0));
        if (TracerConfig.showArmor) {
            stacks.add(equip.get(1));
            stacks.add(equip.get(2));
            stacks.add(equip.get(3));
            stacks.add(equip.get(4));
        }
        if (TracerConfig.showHeld) stacks.add(equip.get(5));

        final int slot = 18;
        final float scale = 0.7f;
        final int lineH = 6;

        List<List<Line>> lines = new ArrayList<>();
        int maxLines = 0;
        for (ItemStack s : stacks) {
            List<Line> l = itemLines(s);
            lines.add(l);
            maxLines = Math.max(maxLines, l.size());
        }

        int rowH = stacks.isEmpty() ? 0 : 18;
        int itemsY = bottom - 4 - maxLines * lineH - rowH;

        // nick + serca
        boolean top = TracerConfig.showNick || TracerConfig.showHearts;
        if (top) {
            String nick = TracerConfig.showNick ? name : "";
            String hearts = TracerConfig.showHearts ? String.format("❤ %.1f", hp / 2f) : "";
            int hpColor = hp <= 6 ? 0xFFFF5555 : hp <= 14 ? 0xFFFFAA00 : 0xFF55FF55;
            int nickW = mc.textRenderer.getWidth(nick);
            int heartsW = mc.textRenderer.getWidth(hearts);
            int gapW = (nickW > 0 && heartsW > 0) ? 6 : 0;
            int totalW = nickW + gapW + heartsW;
            int tx = cx - totalW / 2;
            int ty = (stacks.isEmpty() ? bottom - 4 : itemsY) - 12;
            g.fill(tx - 2, ty - 2, tx + totalW + 2, ty + 10, 0x80000000);
            if (nickW > 0) g.drawText(mc.textRenderer, nick, tx, ty, 0xFFFFFFFF, true);
            if (heartsW > 0) g.drawText(mc.textRenderer, hearts, tx + nickW + gapW, ty, hpColor, true);
        }

        // koordynaty i dystans nad nickiem
        if (TracerConfig.showCoords) {
            Vec3d cp = mc.gameRenderer.getCamera().getCameraPos();
            double cdx = pos.x - cp.x, cdy = pos.y - cp.y, cdz = pos.z - cp.z;
            String ct = coords(pos) + "  " + (int) Math.sqrt(cdx * cdx + cdy * cdy + cdz * cdz) + "m";
            int cw = mc.textRenderer.getWidth(ct);
            int cy = ((stacks.isEmpty() ? bottom - 4 : itemsY) - 12) - (top ? 11 : 0);
            g.fill(cx - cw / 2 - 2, cy - 2, cx + cw / 2 + 2, cy + 9, 0x80000000);
            g.drawText(mc.textRenderer, ct, cx - cw / 2, cy, 0xFFFFD27F, true);
        }

        // itemy + linie pod nimi (enchanty / wytrzymałość)
        int startX = cx - (stacks.size() * slot) / 2;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            if (s.isEmpty()) continue;
            int ix = startX + i * slot;
            g.drawItem(s, ix, itemsY);
            g.drawStackOverlay(mc.textRenderer, s, ix, itemsY);

            List<Line> ls = lines.get(i);
            for (int l = 0; l < ls.size(); l++) {
                Line line = ls.get(l);
                int lw = mc.textRenderer.getWidth(line.text());
                g.getMatrices().pushMatrix();
                g.getMatrices().translate((float) (ix + 8), (float) (itemsY + 18 + l * lineH));
                g.getMatrices().scale(scale, scale);
                g.drawText(mc.textRenderer, line.text(), -lw / 2, 0, line.color(), true);
                g.getMatrices().popMatrix();
            }
        }
    }

    private static List<Line> itemLines(ItemStack stack) {
        List<Line> out = new ArrayList<>();
        if (stack.isEmpty()) return out;

        if (TracerConfig.showEnchants) {
            ItemEnchantmentsComponent ench = stack.getEnchantments();
            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : ench.getEnchantmentEntries()) {
                String name = Enchantment.getName(e.getKey(), e.getIntValue()).getString();
                String[] parts = name.split(" ");
                String base = parts[0];
                if (base.length() > 4) base = base.substring(0, 4);
                String lvl = parts.length > 1 ? parts[parts.length - 1] : "";
                out.add(new Line((base + " " + lvl).trim(), 0xFFAAAAFF));
            }
        }

        if (TracerConfig.showDurability && stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int left = max - stack.getDamage();
            float pct = max <= 0 ? 1f : (float) left / max;
            int c = pct > 0.5f ? 0xFF55FF55 : pct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
            out.add(new Line(String.valueOf(left), c));
        }
        return out;
    }
}
