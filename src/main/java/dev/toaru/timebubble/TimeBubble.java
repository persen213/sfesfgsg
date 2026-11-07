package dev.toaru.timebubble;

import dev.toaru.timebubble.net.Fx;
import dev.toaru.timebubble.net.FxType;
import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.StopReason;
import io.github.pinchan4273.reacademycraft.api.mc.AcademySafety;
import io.github.pinchan4273.reacademycraft.api.mc.ActivationContext;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Time Bubble (level 2 area-control ability, toggle session):
 * 1. Formation (0.5 s): a small sphere forms above the hand, then a dome expands to 8 blocks.
 * 2. Active (5 s): hostile entities and projectiles inside are heavily slowed.
 *    Allies and the caster are unaffected.
 * 3. Collapse: when time expires or key pressed again, dome contracts to a point.
 * No Minecraft ticks are frozen - only entity movement is scaled.
 * Validates targets server-side. No effect stacking.
 */
public final class TimeBubble implements SkillBehavior {
    private static final class State {
        Vec3 centre;
        long fxKey;
        final Set<UUID> affectedEntities = new HashSet<>();
        final Set<UUID> affectedProjectiles = new HashSet<>();
        final Map<UUID, Vec3> originalMotions = new HashMap<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static int formationTicks() { return Cfg.ticks(Cfg.FORMATION_SECONDS); }
    private static int activeTicks() { return Cfg.ticks(Cfg.DURATION); }
    private static int totalTicks() { return formationTicks() + activeTicks(); }

    @Override public ActivationOutcome start(ActivationContext c) {
        ServerPlayer p = c.naturalUser();
        if (p == null) return ActivationOutcome.REFUSED_INVALID;
        // Centre on the player's position (hand level)
        Vec3 centre = p.position().add(0, p.getEyeHeight() * 0.85, 0);
        if (!c.pay(Cfg.CP.get().floatValue(), 0)) return ActivationOutcome.REFUSED_RESOURCE;

        ServerLevel level = p.serverLevel();
        State s = new State();
        s.centre = centre;
        s.fxKey = Fx.make(level, centre, FxType.TIME_BUBBLE, totalTicks()).range(220.0)
                .aux(new Vec3(Cfg.RADIUS.get(), formationTicks(), 0)).send();
        STATES.put(p.getUUID(), s);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.EVOKER_PREPARE_ATTACK, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.8f);
        return ActivationOutcome.STARTED;
    }

    @Override public boolean tick(ActivationContext c) {
        ServerPlayer p = c.naturalUser();
        State s = p == null ? null : STATES.get(p.getUUID());
        int age = c.age();
        if (p == null || s == null || age >= totalTicks()) return false;
        ServerLevel level = p.serverLevel();
        var actor = c.actor();
        Vec3 centre = s.centre;
        double radius = Cfg.RADIUS.get();
        double slowFactor = Cfg.ENTITY_SLOW.get();
        double projectileSlow = Cfg.PROJECTILE_SLOW.get();
        int formTicks = formationTicks();
        double ramp = Math.min(1.0, age / (double) Math.max(1, formTicks));
        double currentRadius = radius * ramp;

        // Formation phase: just expand the visual, no effects yet
        if (age < formTicks) {
            if (age % 10 == 0) {
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);
            }
            return true;
        }

        // Active phase: apply slowing
        int activeAge = age - formTicks;

        // Re-validate targets a few times per second
        if (activeAge % 4 == 0) {
            s.affectedEntities.clear();
            s.affectedProjectiles.clear();

            for (LivingEntity e : Targeting.livingWithin(level, centre, radius, p)) {
                if (Targeting.hostile(actor, e, TimeBubbleMod.TIME_BUBBLE) && !s.originalMotions.containsKey(e.getUUID())) {
                    s.affectedEntities.add(e.getUUID());
                    s.originalMotions.put(e.getUUID(), e.getDeltaMovement());
                }
            }

            for (Projectile pr : Targeting.projectilesWithin(level, centre, radius, p)) {
                if (!s.originalMotions.containsKey(pr.getUUID())) {
                    s.affectedProjectiles.add(pr.getUUID());
                    s.originalMotions.put(pr.getUUID(), pr.getDeltaMovement());
                }
            }
        }

        // Apply slow to hostile entities
        for (UUID id : s.affectedEntities) {
            Entity found = level.getEntity(id);
            if (!(found instanceof LivingEntity le) || !le.isAlive()) continue;
            Vec3 toCentre = centre.subtract(Targeting.centre(le));
            double d = toCentre.length();
            if (d > radius * 1.1) continue; // left the bubble

            // Apply slow factor to velocity
            Vec3 v = le.getDeltaMovement().scale(slowFactor);
            Targeting.setMotion(le, v);
        }

        // Apply slow to projectiles
        for (UUID id : s.affectedProjectiles) {
            Entity found = level.getEntity(id);
            if (!(found instanceof Projectile pr) || !pr.isAlive()) continue;
            Vec3 toCentre = centre.subtract(pr.position());
            double d = toCentre.length();
            if (d > radius * 1.1) continue;

            Vec3 v = pr.getDeltaMovement().scale(projectileSlow);
            Targeting.setMotion(pr, v);
        }

        // Periodic sound
        if (activeAge % 20 == 0) {
            level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.7f);
        }
        return true;
    }

    @Override public void stop(ActivationContext c, StopReason reason) {
        ServerPlayer p = c.naturalUser();
        State s = p == null ? null : STATES.remove(p.getUUID());
        if (p != null && s != null) {
            ServerLevel level = p.serverLevel();
            Fx.cancel(level, s.fxKey);

            // Restore original motion to all affected entities
            for (Map.Entry<UUID, Vec3> entry : s.originalMotions.entrySet()) {
                Entity e = level.getEntity(entry.getKey());
                if (e != null && e.isAlive()) {
                    Targeting.setMotion(e, entry.getValue());
                }
            }

            // Play collapse sound
            if (reason == StopReason.FINISHED || reason == StopReason.SWITCHED_OFF) {
                level.playSound(null, s.centre.x, s.centre.y, s.centre.z, SoundEvents.EVOKER_CAST_SPELL, net.minecraft.sounds.SoundSource.PLAYERS, 1.8f, 0.6f);
            }
        }
        c.setCooldown(Cfg.ticks(Cfg.COOLDOWN));
    }

    public static void forget(UUID player) { STATES.remove(player); }
    public static void clearAll() { STATES.clear(); }
}