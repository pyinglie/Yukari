package club.nankai.mc.yukari;

import club.nankai.mc.yukari.data.PlayerDataManager;
import club.nankai.mc.yukari.game.GameController;
import club.nankai.mc.yukari.logic.ItemRestrictionHandler;
import club.nankai.mc.yukari.net.ModNetworking;
import club.nankai.mc.yukari.select.ChatSelectionHelper;
import club.nankai.mc.yukari.util.WorldResetManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(YukariMod.MOD_ID)
public class YukariMod {
    public static final String MOD_ID = "yukari";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MSG_PREFIX = "[Yukari] ";

    public YukariMod(IEventBus modEventBus) {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(this::commonSetup);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ServerEvents.class);
        LOGGER.debug("ItemRestrictionHandler loaded: {}", ItemRestrictionHandler.class.getName());
        LOGGER.info("{} mod constructor complete.", MOD_ID);
    }

    private void commonSetup(FMLCommonSetupEvent evt) {
        LOGGER.info("{} commonSetup complete.", MOD_ID);
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static class ServerEvents {

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent evt) {
            WorldResetManager.onServerStarting(evt.getServer());
            GameController.init(evt.getServer());
            PlayerDataManager.init(evt.getServer());
            LOGGER.info("{} server started: controller and data initialized.", MOD_ID);
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent evt) {
            CommandHandler.register(evt.getDispatcher());
            LOGGER.info("{} commands registered.", MOD_ID);
        }

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent evt) {
            if (!(evt.getEntity() instanceof ServerPlayer player)) return;
            if (GameController.get() == null) GameController.init(player.server);
            if (PlayerDataManager.isUninitialized()) PlayerDataManager.init(player.server);

            var gc = GameController.get();
            String selected = PlayerDataManager.getSelectedMod(player.getUUID());
            if (selected == null) {
                if (Config.USE_CHAT_SELECTION.get()) {
                    ChatSelectionHelper.sendSelection(player);
                } else {
                    ModNetworking.sendOpenSelection(player);
                }
                player.setGameMode(GameType.SPECTATOR);
            } else {
                gc.syncPlayerStateOnLogin(player, selected);
            }
        }

        // FINAL-phase death -> immediately set to spectator
        @SubscribeEvent
        public static void onPlayerDeath(LivingDeathEvent evt) {
            if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
            var gc = GameController.get();
            if (gc == null) return;
            if (gc.getState() == GameController.State.FINAL) {
                sp.setGameMode(GameType.SPECTATOR);
            }
        }

        // Extra safety for the respawn event
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerRespawnEvent evt) {
            if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
            var gc = GameController.get();
            if (gc == null) return;
            if (gc.getState() == GameController.State.FINAL) {
                if (sp.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    sp.setGameMode(GameType.SPECTATOR);
                }
            }
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post evt) {
            var c = GameController.get();
            if (c != null) c.tick();
        }
    }
}