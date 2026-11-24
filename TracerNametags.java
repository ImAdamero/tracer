package pl.tracer.nametags;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.ArrayList;
import java.util.List;

public class TracerNametags implements ClientModInitializer {

    private static final double MAX_DISTANCE = 128.0;

    private record Tag(AbstractClientPlayerEntity player, float x, float y) {}

    private static final List<Tag> TAGS = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        WorldRenderEvents.END.register(TracerNametags::collect);
        HudRenderCallback.EVENT.register(TracerNametags::draw);
    }

    /** Rzutuje pozycję głowy każdego gracza na ekran. */
    private static void collect(WorldRenderContext ctx) {
        TAGS.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Camera camera = ctx.camera();
        Matrix4f view = ctx.positionMatrix();
        Matrix4f proj = ctx.projectionMatrix();
        float tickDelta = ctx.tickCounter().getTickDelta(true);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.squaredDistanceTo(mc.player) > MAX_DISTANCE * MAX_DISTANCE) continue;

            Vec3d pos = p.getLerpedPos(tickDelta).add(0, p.getHeight() + 0.5, 0);
            Vec3d rel = pos.subtract(camera.getPos());

            Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f);
            v.mul(view);
            v.mul(proj);
            if (v.w <= 0.05f) continue; // za plecami

            float ndcX = v.x / v.w;
            float ndcY = v.y / v.w;
            float sx = (ndcX * 0.5f + 0.5f) * sw;
            float sy = (1f - (ndcY * 0.5f + 0.5f)) * sh;
            TAGS.add(new Tag(p, sx, sy));
        }
    }

    private static void draw(DrawContext g, net.minecraft.client.render.RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;

        for (Tag t : TAGS) {
            AbstractClientPlayerEntity p = t.player();
            int cx = Math.round(t.x());
            int bottom = Math.round(t.y());

            // Kolejność: ręka główna, zbroja (hełm->buty), druga ręka
            ItemStack[] items = {
                    p.getMainHandStack(),
                    p.getEquippedStack(EquipmentSlot.HEAD),
                    p.getEquippedStack(EquipmentSlot.CHEST),
                    p.getEquippedStack(EquipmentSlot.LEGS),
                    p.getEquippedStack(EquipmentSlot.FEET),
                    p.getOffHandStack()
            };

            int slot = 20;
            int rowWidth = items.length * slot;
            int startX = cx - rowWidth / 2;

            // maksymalna liczba linii enchantów, żeby wiedzieć, jak wysoko rysować
            int maxLines = 0;
            for (ItemStack s : items) {
                if (!s.isEmpty()) maxLines = Math.max(maxLines, enchantLines(s).size());
            }
            int lineH = 9;
            int itemsY = bottom - 12 - 18 - 10 - maxLines * lineH;

            // Nick + serca nad rzędem itemów
            float hp = p.getHealth() + p.getAbsorptionAmount();
            String hearts = String.format("\u2764 %.1f", hp / 2f);
            String nick = p.getName().getString();
            int hpColor = hp <= 6 ? 0xFFFF5555 : hp <= 14 ? 0xFFFFAA00 : 0xFF55FF55;

            int nickW = mc.textRenderer.getWidth(nick);
            int heartsW = mc.textRenderer.getWidth(hearts);
            int totalW = nickW + 6 + heartsW;
            int tx = cx - totalW / 2;
            int ty = itemsY - 12;

            g.fill(tx - 2, ty - 2, tx + totalW + 2, ty + 10, 0x80000000);
            g.drawText(mc.textRenderer, nick, tx, ty, 0xFFFFFFFF, true);
            g.drawText(mc.textRenderer, hearts, tx + nickW + 6, ty, hpColor, true);

            // Itemy + enchanty pod nimi
            for (int i = 0; i < items.length; i++) {
                ItemStack s = items[i];
                if (s.isEmpty()) continue;
                int ix = startX + i * slot;
                g.drawItem(s, ix, itemsY);
                g.drawStackOverlay(mc.textRenderer, s, ix, itemsY);

                List<String> lines = enchantLines(s);
                for (int l = 0; l < lines.size(); l++) {
                    String line = lines.get(l);
                    int lw = mc.textRenderer.getWidth(line);
                    // małe litery, żeby się mieściło
                    g.getMatrices().push();
                    float scale = 0.5f;
                    g.getMatrices().translate(ix + 8, itemsY + 18 + l * lineH * scale * 1.0f, 0);
                    g.getMatrices().scale(scale, scale, 1f);
                    g.drawText(mc.textRenderer, line, -lw / 2, 0, 0xFFAAAAFF, true);
                    g.getMatrices().pop();
                }
            }
        }
    }

    /** Np. "Prot IV", "Unb III", "Mend I". */
    private static List<String> enchantLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        ItemEnchantmentsComponent ench = stack.getEnchantments();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : ench.getEnchantmentEntries()) {
            String name = Enchantment.getName(e.getKey(), e.getIntValue()).getString();
            // "Protection IV" -> "Prot IV"
            String[] parts = name.split(" ");
            String base = parts[0];
            if (base.length() > 4) base = base.substring(0, 4);
            String lvl = parts.length > 1 ? parts[parts.length - 1] : "";
            out.add((base + " " + lvl).trim());
        }
        return out;
    }
}
