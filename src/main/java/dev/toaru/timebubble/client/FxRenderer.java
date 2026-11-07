package dev.toaru.timebubble.client;

import dev.toaru.timebubble.TimeBubbleMod;
import dev.toaru.timebubble.client.ClientFx.Effect;
import dev.toaru.timebubble.net.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the Time Bubble as real 3D geometry inside the world:
 * - Formation: small sphere at hand with rotating violet rings
 * - Active: transparent spherical dome (8 blocks) with distortion, rotating rings
 * - Entities inside: faint blue outlines
 * - Collapse: dome contracts to bright geometric point
 * NO PARTICLES - all custom geometry, transparent meshes, animated textures.
 * Cost control: at most 8 effects exist, anything beyond 220 blocks is skipped, segment counts drop with distance.
 */
@Mod.EventBusSubscriber(modid = TimeBubbleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FxRenderer {
    private FxRenderer() { }

    private static final double MAX_DISTANCE = 220.0;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ClientFx.ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.isPaused() ? 0f : event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // Pass 1: Dark/opaque geometry (inner sphere, dome surface)
        Geo dark = new Geo(buffers.getBuffer(BhRenderTypes.DARK), pose, cam);
        for (Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.pos.distanceTo(cam) <= MAX_DISTANCE) drawDark(fx, fx.age(now, partial), dark, cam);
        }
        buffers.endBatch(BhRenderTypes.DARK);

        // Pass 2: Distortion effect (glass-like dome)
        Geo distortion = new Geo(buffers.getBuffer(BhRenderTypes.DISTORTION), pose, cam);
        for (Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.pos.distanceTo(cam) <= MAX_DISTANCE) drawDistortion(fx, fx.age(now, partial), distortion, cam);
        }
        buffers.endBatch(BhRenderTypes.DISTORTION);

        // Pass 3: Additive glow geometry (rings, lensing, collapse flash)
        Geo glow = new Geo(buffers.getBuffer(BhRenderTypes.GLOW), pose, cam);
        for (Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.pos.distanceTo(cam) <= MAX_DISTANCE) drawGlow(fx, fx.age(now, partial), glow, cam);
        }
        buffers.endBatch(BhRenderTypes.GLOW);

        // Pass 4: Entity outlines (faint blue glow around affected entities)
        // This would be rendered separately per entity - handled in ScreenFx for now
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }

    private static int segments(Effect fx, Vec3 cam) {
        double dist = fx.pos.distanceTo(cam);
        return dist < 50 ? 56 : (dist < 120 ? 36 : 24);
    }

    /** Current dome radius: grows during formation, stays at max during active, shrinks during collapse. */
    private static double domeRadius(Effect fx, double age) {
        double form = clamp01(age / Math.max(1.0, fx.aux.y));
        double total = fx.duration;
        double activeStart = fx.aux.y;
        double activeEnd = activeStart + (total - activeStart) * 0.8; // 80% of remaining time is active
        double collapseStart = activeEnd;

        if (age < activeStart) {
            // Formation: expand from 0 to full radius
            return fx.aux.x * smoothstep(form);
        } else if (age < activeEnd) {
            // Active: full radius with slight pulse
            double pulse = Math.sin((age - activeStart) * 0.15) * 0.05 + 1.0;
            return fx.aux.x * pulse;
        } else if (age < total) {
            // Collapse: shrink to point
            double collapseProgress = clamp01((age - collapseStart) / Math.max(1, total - collapseStart));
            double ease = 1.0 - smoothstep(collapseProgress);
            return fx.aux.x * ease * ease;
        }
        return 0;
    }

    private static void drawDark(Effect fx, double age, Geo g, Vec3 cam) {
        int seg = segments(fx, cam);
        Vec3 c = fx.pos;
        double r = domeRadius(fx, age);

        if (r < 0.02) return;

        switch (fx.type) {
            case TIME_BUBBLE -> {
                // Inner formation sphere at hand level (only during formation)
                if (age < fx.aux.y) {
                    double handSphereR = 0.3 * clamp01(age / Math.max(1.0, fx.aux.y * 0.5));
                    if (handSphereR > 0.02) {
                        g.color(0.2f, 0.3f, 0.8f, 0.4f).sphere(c.add(0, 1.5, 0), handSphereR, 8, seg / 2);
                    }
                }
                // Main dome surface (semi-transparent dark blue)
                g.color(0.1f, 0.15f, 0.4f, 0.15f).sphere(c, r * 1.02, 12, seg);
                g.color(0.05f, 0.1f, 0.35f, 0.25f).sphere(c, r, 16, seg);
            }
            case TIME_BUBBLE_COLLAPSE -> {
                // Collapse handled in glow pass
            }
        }
    }

    private static void drawDistortion(Effect fx, double age, Geo g, Vec3 cam) {
        int seg = segments(fx, cam);
        Vec3 c = fx.pos;
        double r = domeRadius(fx, age);

        if (r < 0.1) return;

        // Only render distortion during active phase
        double form = clamp01(age / Math.max(1.0, fx.aux.y));
        if (form < 1.0) return; // Wait for formation to complete

        double activeStart = fx.aux.y;
        double activeEnd = activeStart + (fx.duration - activeStart) * 0.8;
        if (age < activeStart || age > activeEnd) return;

        // Distortion dome - uses animated texture for glass-like effect
        double distortionAlpha = 0.18 + 0.12 * Math.sin(age * 0.08);
        g.color(0.3f, 0.4f, 1.0f, (float) distortionAlpha).sphere(c, r * 1.01, 12, seg);
    }

    private static void drawGlow(Effect fx, double age, Geo g, Vec3 cam) {
        int seg = segments(fx, cam);
        switch (fx.type) {
            case TIME_BUBBLE -> timeBubbleGlow(fx, age, g, cam, seg);
            case TIME_BUBBLE_COLLAPSE -> collapseGlow(fx, age, g, cam, seg);
        }
    }

    private static void timeBubbleGlow(Effect fx, double age, Geo g, Vec3 cam, int seg) {
        Vec3 c = fx.pos;
        double r = domeRadius(fx, age);
        double form = clamp01(age / Math.max(1.0, fx.aux.y));
        double ease = smoothstep(form);

        if (r < 0.02) return;

        // Formation phase: small sphere at hand with violet rings
        if (age < fx.aux.y) {
            double handR = 0.35 * ease;
            Vec3 handPos = c.add(0, 1.5, 0);

            // Central sphere
            g.color(0.4f, 0.5f, 1.0f, (float) (0.8 * ease)).sphere(handPos, handR, 8, seg / 2);

            // Violet geometric rings rotating around hand sphere
            for (int i = 0; i < 3; i++) {
                double ringPhase = age * 0.03 + i * 2.0;
                double tilt = Math.sin(ringPhase * 0.5) * 0.3;
                Vec3 normal = new Vec3(Math.sin(tilt), Math.cos(tilt), 0).normalize();
                double ringR = handR * (1.2 + i * 0.4);
                double thickness = handR * 0.08;
                float alpha = (float) (0.6 * ease * (1.0 - i * 0.2));
                g.color(0.6f, 0.3f, 1.0f, alpha).ring(handPos, normal, ringR, thickness, seg, ringPhase, false);
            }
            return;
        }

        // Active phase: large dome with concentric rotating rings
        double activeStart = fx.aux.y;
        double activeEnd = activeStart + (fx.duration - activeStart) * 0.8;
        double activeAge = age - activeStart;
        double activeProgress = clamp01(activeAge / Math.max(1, activeEnd - activeStart));

        // Photon ring at dome surface (always faces camera - lensing effect)
        Vec3 toCam = cam.subtract(c);
        toCam = toCam.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : toCam.normalize();
        g.color(0.5f, 0.6f, 1.0f, (float) (0.7 * (1.0 - activeProgress * 0.3))).ring(c, toCam, r * 1.01, r * 0.02, seg, 0, false);
        g.color(0.7f, 0.4f, 1.0f, (float) (0.4 * (1.0 - activeProgress * 0.3))).ring(c, toCam, r * 1.15, r * 0.03, seg, 0, false);

        // Large concentric rings rotating around dome surface
        for (int i = 0; i < 4; i++) {
            double ringPhase = activeAge * (0.015 + i * 0.003) + i * 1.5;
            double tilt = 0.4 + i * 0.2;
            Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(i * 1.2), Math.cos(tilt), Math.sin(tilt) * Math.sin(i * 1.2)).normalize();
            double ringR = r * (1.02 + i * 0.08);
            double thickness = r * (0.015 + i * 0.01);
            float alpha = (float) (0.35 * (1.0 - i * 0.15) * (1.0 - activeProgress * 0.2));
            g.color(0.4f + i * 0.1f, 0.3f + i * 0.1f, 1.0f, alpha).ring(c, normal, ringR, thickness, seg, ringPhase, true);
        }

        // Inner vertical rings (hourglass-like)
        for (int i = 0; i < 2; i++) {
            double ringPhase = activeAge * 0.02 + i * 3.14;
            Vec3 normal = new Vec3(0, 1, 0);
            double ringR = r * (0.3 + i * 0.4);
            double thickness = r * 0.025;
            g.color(0.3f, 0.5f, 1.0f, 0.25f).ring(c, normal, ringR, thickness, seg, ringPhase, true);
        }

        // Collapse phase: bright implosion
        if (age >= activeEnd) {
            double collapseProgress = clamp01((age - activeEnd) / Math.max(1, fx.duration - activeEnd));
            double implosionR = r * (1.0 - collapseProgress * 0.9);
            float flashAlpha = (float) (1.0 - collapseProgress);
            if (implosionR > 0.02) {
                g.color(1.0f, 0.9f, 0.7f, (float) (flashAlpha * 0.9)).sphere(c, implosionR * 1.5, 8, 16);
                g.color(0.5f, 0.3f, 1.0f, (float) (flashAlpha * 0.6)).sphere(c, implosionR, 12, seg);
                // Outward expanding rings
                for (int i = 0; i < 3; i++) {
                    double ringR = implosionR + collapseProgress * r * (0.5 + i * 0.3);
                    g.color(0.6f, 0.4f, 1.0f, (float) ((1.0 - collapseProgress) * (0.4 - i * 0.1)))
                            .ring(c, new Vec3(0, 1, 0), ringR, r * 0.05, seg, 0, false);
                }
            }
        }
    }

    private static void collapseGlow(Effect fx, double age, Geo g, Vec3 cam, int seg) {
        // Separate collapse effect type - bright geometric point expanding and fading
        double t = clamp01(age / Math.max(1, fx.duration));
        Vec3 c = fx.pos;
        double maxR = fx.aux.x;

        if (t < 0.3) {
            // Intense flash at center
            double flashR = maxR * 0.4 * (1.0 - t / 0.3);
            g.color(1.0f, 0.95f, 0.8f, (float) ((1.0 - t / 0.3) * 0.9)).sphere(c, flashR, 8, 16);
        }

        // Expanding shockwave shell
        double shellR = maxR * t * 1.2;
        float fade = (float) (1.0 - t);
        g.color(0.4f, 0.5f, 1.0f, 0.15f * fade).sphere(c, shellR, 12, seg / 2);

        double band = 0.1 + 0.4 * fade;
        g.color(0.5f, 0.3f, 1.0f, 0.7f * fade).ring(c, new Vec3(0, 1, 0), shellR, band, seg, 0, false);
        g.color(0.3f, 0.6f, 1.0f, 0.5f * fade).ring(c, new Vec3(1, 0, 0), shellR, band * 0.7, seg, 0, false);
        g.color(0.3f, 0.6f, 1.0f, 0.5f * fade).ring(c, new Vec3(0, 0, 1), shellR, band * 0.7, seg, 0, false);
    }
}