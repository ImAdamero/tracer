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
import java.util.List;

public class TracerNametags implements ClientModInitializer {

    private static final String MOD_ID = "tracer-nametags";

    private static KeyBinding toggleKey;
    private static KeyBinding menuKey;
    private static KeyBinding freecamKey;
    private static boolean enabled = false;

    private record Line(String text, int color) {}

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

        ClientTickEvents.START_CLIENT_TICK.register(Freecam::tick);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
        Freecam.update(mc);
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

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !Freecam.isActive()) continue;

            Vec3d base = p.getLerpedPos(tickDelta);
            double ddx = base.x - camPos.x;
            double ddz = base.z - camPos.z;
            double dist2 = ddx * ddx + ddz * ddz;
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
                    drawTag(g, mc, p, 0, 0);
                    g.getMatrices().popMatrix();
                }
            }
        }
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

    private static void drawTag(DrawContext g, MinecraftClient mc, AbstractClientPlayerEntity p, int cx, int bottom) {
        List<ItemStack> stacks = new ArrayList<>();
        if (TracerConfig.showHeld) stacks.add(p.getMainHandStack());
        if (TracerConfig.showArmor) {
            stacks.add(p.getEquippedStack(EquipmentSlot.HEAD));
            stacks.add(p.getEquippedStack(EquipmentSlot.CHEST));
            stacks.add(p.getEquippedStack(EquipmentSlot.LEGS));
            stacks.add(p.getEquippedStack(EquipmentSlot.FEET));
        }
        if (TracerConfig.showHeld) stacks.add(p.getOffHandStack());

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
            float hp = p.getHealth() + p.getAbsorptionAmount();
            String nick = TracerConfig.showNick ? p.getName().getString() : "";
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
