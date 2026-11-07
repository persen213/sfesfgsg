package dev.toaru.timebubble.client;

import dev.toaru.timebubble.TimeBubbleMod;
import dev.toaru.timebubble.net.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side screen effects when inside the Time Bubble:
 * - Subtle vignette/color shift
 * - Faint blue outline on affected entities (rendered in world)
 * - Time distortion shader-like effect on screen edges
 */
@Mod.EventBusSubscriber(modid = TimeBubbleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScreenFx {
    private ScreenFx() { }

    private static boolean wasInside = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        LocalPlayer player = mc.player;
        Vec3 playerPos = player.position().add(0, player.getEyeHeight(), 0);

        boolean inside = false;
        for (ClientFx.Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.type != FxType.TIME_BUBBLE) continue;
            double r = domeRadius(fx, fx.age(mc.level.getGameTime(), 0));
            if (playerPos.distanceTo(fx.pos) <= r * 1.05) {
                inside = true;
                break;
            }
        }

        if (inside && !wasInside) {
            // Entered bubble - could play a sound or trigger screen effect
        } else if (!inside && wasInside) {
            // Exited bubble
        }
        wasInside = inside;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        LocalPlayer player = mc.player;
        Vec3 playerPos = player.position().add(0, player.getEyeHeight(), 0);

        // Check if player is inside any active time bubble
        for (ClientFx.Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.type != FxType.TIME_BUBBLE) continue;
            double r = domeRadius(fx, fx.age(mc.level.getGameTime(), event.getPartialTick()));
            if (playerPos.distanceTo(fx.pos) <= r * 1.05) {
                renderScreenEffect(event.getGuiGraphics(), fx, r, event.getPartialTick());
                break;
            }
        }
    }

    private static double domeRadius(ClientFx.Effect fx, double age) {
        double form = Math.max(0, Math.min(1, age / Math.max(1.0, fx.aux.y)));
        double total = fx.duration;
        double activeStart = fx.aux.y;
        double activeEnd = activeStart + (total - activeStart) * 0.8;
        double collapseStart = activeEnd;

        if (age < activeStart) {
            return fx.aux.x * form * form * (3.0 - 2.0 * form);
        } else if (age < activeEnd) {
            double pulse = Math.sin((age - activeStart) * 0.15) * 0.05 + 1.0;
            return fx.aux.x * pulse;
        } else if (age < total) {
            double collapseProgress = Math.max(0, Math.min(1, (age - collapseStart) / Math.max(1, total - collapseStart)));
            double ease = 1.0 - collapseProgress * collapseProgress * (3.0 - 2.0 * collapseProgress);
            return fx.aux.x * ease * ease;
        }
        return 0;
    }

    private static void renderScreenEffect(GuiGraphics guiGraphics, ClientFx.Effect fx, double radius, float partialTick) {
        // Subtle vignette - darken edges with blue tint
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        // Draw a faint blue vignette at screen edges using fill (fillGradient signature changed)
        for (int i = 0; i < 4; i++) {
            float alpha = 0.03f * (1.0f - i * 0.2f);
            int color = (int) (alpha * 255) << 24 | 0x3060FF;
            guiGraphics.fill(i, i, width - i, height - i, color);
        }

        // Subtle time distortion lines at edges (very faint)
        double time = (fx.age(0, partialTick) * 0.01) % 1.0;
        for (int i = 0; i < 3; i++) {
            float y = (float) ((time + i * 0.33) % 1.0) * height;
            int alpha = (int) (15 * (1.0 - Math.abs(y - height * 0.5) / (height * 0.5)));
            int color = (alpha << 24) | 0x4080FF;
            guiGraphics.fill(0, (int) y, width, (int) y + 1, color);
        }
    }
}