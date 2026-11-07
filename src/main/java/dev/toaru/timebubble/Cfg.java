package dev.toaru.timebubble;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

/**
 * Every balance number of the ability. The file is created at config/timebubble-common.toml the first time the game (or
 * server) starts; edit it there and restart. Seconds are real seconds, ranges are blocks.
 */
public final class Cfg {
    private Cfg() { }
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static DoubleValue d(String name, double def, double min, double max) { return B.defineInRange(name, def, min, max); }
    private static IntValue i(String name, int def, int min, int max) { return B.defineInRange(name, def, min, max); }

    public static final IntValue COOLDOWN = i("cooldown_seconds", 30, 0, 3600);
    public static final DoubleValue CP = d("cp_cost", 35, 0, 100000);
    public static final DoubleValue RADIUS = d("bubble_radius", 8, 1, 32);
    public static final DoubleValue DURATION = d("bubble_duration_seconds", 5, 1, 60);
    public static final DoubleValue ENTITY_SLOW = d("entity_slow_factor", 0.15, 0, 1);
    public static final DoubleValue PROJECTILE_SLOW = d("projectile_slow_factor", 0.05, 0, 1);
    public static final DoubleValue FORMATION_SECONDS = d("formation_seconds", 0.5, 0.1, 5);

    public static final ForgeConfigSpec SPEC = B.build();

    public static int ticks(DoubleValue seconds) { return (int) Math.round(seconds.get() * 20.0); }
    public static int ticks(IntValue seconds) { return (int) Math.round(seconds.get() * 20.0); }
}