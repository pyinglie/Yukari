package club.nankai.mc.yukari.net;

import club.nankai.mc.yukari.Config;
import club.nankai.mc.yukari.YukariMod;
import club.nankai.mc.yukari.data.PlayerDataManager;
import club.nankai.mc.yukari.game.GameController;
import club.nankai.mc.yukari.net.payload.OpenSelectionS2CPayload;
import club.nankai.mc.yukari.net.payload.SelectModC2SPayload;
import club.nankai.mc.yukari.util.ServerModCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = YukariMod.MOD_ID)
public class ModNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent evt) {
        var registrar = evt.registrar(YukariMod.MOD_ID).versioned("4");

        registrar.playToServer(SelectModC2SPayload.TYPE,
                SelectModC2SPayload.STREAM_CODEC,
                ModNetworking::handleSelectModC2S);

        registrar.playToClient(OpenSelectionS2CPayload.TYPE,
                OpenSelectionS2CPayload.STREAM_CODEC,
                ModNetworking::handleOpenSelectionS2C);
    }

    private static void handleSelectModC2S(SelectModC2SPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var controller = GameController.get();
            if (controller == null) {
                player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Controller not initialized"));
                return;
            }
            String sel = payload.selection();
            if (SelectModC2SPayload.SPECTATE.equals(sel)) {
                controller.handlePlayerBecomeSpectator(player, true);
                return;
            }
            if (!ServerModCatalog.isValidModId(sel)) {
                player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Invalid mod: " + sel));
                return;
            }
            if (!Config.ALLOW_MULTIPLE_SAME_MOD.get()) {
                boolean occupied = PlayerDataManager.getAllPlayers().stream()
                        .anyMatch(uuid -> sel.equals(PlayerDataManager.getSelectedMod(uuid)));
                if (occupied && !sel.equals(PlayerDataManager.getSelectedMod(player.getUUID()))) {
                    player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "This mod is already taken."));
                    return;
                }
            }
            controller.handlePlayerChooseMod(player, sel, true);
        });
    }

    private static void handleOpenSelectionS2C(OpenSelectionS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                try {
                    club.nankai.mc.yukari.client.ClientAccess.openSelectionScreen(payload);
                } catch (NoClassDefFoundError ignored) {
                }
            }
        });
    }

    public static void sendOpenSelection(ServerPlayer player) {
        var mods = ServerModCatalog.collectSelectableMods();
        List<OpenSelectionS2CPayload.Entry> entries = mods.stream()
                .map(m -> new OpenSelectionS2CPayload.Entry(m.modId(), m.displayName()))
                .toList();
        Set<String> occupied = PlayerDataManager.getAllPlayers().stream()
                .map(PlayerDataManager::getSelectedMod)
                .filter(s -> s != null)
                .collect(Collectors.toSet());
        boolean allowMulti = Config.ALLOW_MULTIPLE_SAME_MOD.get();
        player.connection.send(new OpenSelectionS2CPayload(entries, occupied.stream().toList(), allowMulti));
    }
}