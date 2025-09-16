package club.nankai.mc.yukari;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALLOW_MULTIPLE_SAME_MOD =
            B.comment("Allow multiple players selecting the same mod.")
                    .define("allowMultipleSameMod", true);

    public static final ModConfigSpec.BooleanValue USE_CHAT_SELECTION =
            B.comment("If true (or GUI fails), use chat clickable selection.")
                    .define("useChatSelection", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CLASSIC_MODS =
            B.comment("Fallback classic mods if dynamic scan yields empty list.")
                    .defineListAllowEmpty("classicMods",
                            List.of("IndustrialCraft2", "BuildCraft", "Thaumcraft", "Botania"),
                            o -> o instanceof String s && !s.isBlank());

    // Expected distances in tens of thousands of blocks -> default max radius 50,000
    public static final ModConfigSpec.IntValue RANDOM_TELEPORT_RANGE =
            B.comment("Max horizontal range (radius) for random safe teleport (e.g. 50000 = 50k blocks).")
                    .defineInRange("randomTeleportRange", 50_000, 5_000, 200_000);

    // Minimum radius 30,000 to ensure distance from spawn
    public static final ModConfigSpec.IntValue MIN_RANDOM_DISTANCE =
            B.comment("Minimum horizontal distance (radius) from spawn for random safe teleport.")
                    .defineInRange("minRandomDistance", 30_000, 0, 190_000);

    public static final ModConfigSpec.IntValue SAFE_SPOT_MAX_TRIES =
            B.comment("Max tries when searching a safe random teleport point.")
                    .defineInRange("safeSpotMaxTries", 1200, 64, 20000);

    public static final ModConfigSpec.LongValue FINAL_PHASE_DELAY_TICKS =
            B.comment("Ticks until final phase (72000 = 1 hour).")
                    .defineInRange("finalPhaseDelayTicks", 72_000L, 1_000L, 10_000_000L);

    public static final ModConfigSpec.IntValue RESTRICTION_MESSAGE_COOLDOWN_SEC =
            B.comment("Cooldown (seconds) before sending restriction warning again.")
                    .defineInRange("restrictionMessageCooldownSec", 8, 1, 300);

    public static final ModConfigSpec SPEC = B.build();
}