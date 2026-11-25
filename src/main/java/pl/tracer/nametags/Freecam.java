package pl.tracer.nametags;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Wolna kamera: gracz stoi w miejscu, kamera lata po mapie. */
public final class Freecam {
    public static boolean active = false;

    private static Vec3d pos = null;
    private static long lastNanos = 0;
    private static float savedYaw, savedPitch;

    private Freecam() {}

    public static void toggle(MinecraftClient mc) {
        if (mc.player == null) return;
        active = !active;
        pos = null;
        lastNanos = 0;
        if (active) {
            savedYaw = mc.player.getYaw();
            savedPitch = mc.player.getPitch();
        } else {
            // mysz obracała graczem w trakcie freecam - przywracamy
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
            KeyBinding.updatePressedStates();
        }
    }

    public static void forceOff() {
        active = false;
        pos = null;
    }

    /** Co tick: blokuje ruch prawdziwego gracza, gdy freecam jest włączony. */
    public static void freezePlayer(MinecraftClient mc) {
        if (!active || mc.player == null) return;
        GameOptions o = mc.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);
        o.attackKey.setPressed(false);
        o.useKey.setPressed(false);
    }

    /** Wołane co klatkę z Camera.update. Zwraca nową pozycję kamery. */
    public static Vec3d step(Vec3d current, float yawDeg, float pitchDeg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (pos == null) pos = current;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (dt > 0.1f) dt = 0.1f;

        if (mc.currentScreen == null) {
            long w = mc.getWindow().getHandle();
            double yaw = Math.toRadians(yawDeg);
            double pitch = Math.toRadians(pitchDeg);

            Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));

            Vec3d move = Vec3d.ZERO;
            if (down(w, GLFW.GLFW_KEY_W)) move = move.add(fwd);
            if (down(w, GLFW.GLFW_KEY_S)) move = move.subtract(fwd);
            if (down(w, GLFW.GLFW_KEY_D)) move = move.add(right);
            if (down(w, GLFW.GLFW_KEY_A)) move = move.subtract(right);
            if (down(w, GLFW.GLFW_KEY_SPACE)) move = move.add(0, 1, 0);
            if (down(w, GLFW.GLFW_KEY_LEFT_SHIFT)) move = move.add(0, -1, 0);

            if (move.lengthSquared() > 0) {
                double speed = TracerConfig.freecamSpeed * (down(w, GLFW.GLFW_KEY_LEFT_CONTROL) ? 3.0 : 1.0);
                pos = pos.add(move.normalize().multiply(speed * dt));
            }
        }
        return pos;
    }

    private static boolean down(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }
}
