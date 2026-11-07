package dev.toaru.timebubble;

import com.mojang.logging.LogUtils;
import dev.toaru.timebubble.net.Net;
import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.Presentation;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.TreePosition;
import io.github.pinchan4273.reacademycraft.api.forge.RegisterAbilitiesEvent;
import java.util.List;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Time Bubble: a one-skill category ("Temporal") for Re:AcademyCraft 1.5.0, registered through the public API only.
 * The skill is level 2 because its cost (35 CP by default) is affordable at that level.
 */
@Mod(TimeBubbleMod.MOD_ID)
public final class TimeBubbleMod {
    public static final String MOD_ID = "timebubble";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    /** The item that gives the Temporal category (used in the Ability Developer like the built-in factors). */
    public static final RegistryObject<Item> TEMPORAL_FACTOR = ITEMS.register("temporal_factor", () -> new Item(new Item.Properties().stacksTo(16)));

    public static AbilityId id(String path) { return AbilityId.parse(MOD_ID + ":" + path); }
    public static final AbilityId CATEGORY = id("temporal"), TIME_BUBBLE = id("time_bubble");

    public TimeBubbleMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::registerAbilities);
        bus.addListener(this::creativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Cfg.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) { event.enqueueWork(Net::register); }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(TEMPORAL_FACTOR);
    }

    /** Re:AcademyCraft fires this once during common setup; registration is only possible here. */
    private void registerAbilities(RegisterAbilitiesEvent event) {
        var abilities = event.registrar(MOD_ID);
        // The only skill sits at level 2, so level 1 has no skills of its own.
        abilities.category(new CategoryDefinition(CATEGORY, id("temporal_factor"), List.of(0, 1),
                new Presentation(MOD_ID + ".ability.temporal", null, null)));
        abilities.skill(new SkillDefinition(TIME_BUBBLE, CATEGORY, 2, List.of(), ActivationKind.TOGGLE,
                new Presentation(MOD_ID + ".skill.time_bubble", MOD_ID + ".skill.time_bubble.desc", null),
                new TreePosition(110, 55, null)));
        event.behaviors(MOD_ID).behavior(TIME_BUBBLE, new TimeBubble());
    }

    /** Clean-up of the add-on's own state (Re:AcademyCraft already stops running sessions in these situations). */
    @Mod.EventBusSubscriber(modid = MOD_ID)
    public static final class Cleanup {
        private Cleanup() { }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { TimeBubble.forget(event.getEntity().getUUID()); }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) { TimeBubble.clearAll(); }
    }
}