package club.nankai.mc.yukari.select;

import club.nankai.mc.yukari.Config;
import club.nankai.mc.yukari.data.PlayerDataManager;
import club.nankai.mc.yukari.util.ServerModCatalog;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.stream.Collectors;

public class ChatSelectionHelper {
    public static void sendSelection(ServerPlayer player) {
        var mods = ServerModCatalog.collectSelectableMods();
        Set<String> occupied = PlayerDataManager.getAllPlayers().stream()
                .map(PlayerDataManager::getSelectedMod)
                .filter(s -> s != null).collect(Collectors.toSet());
        boolean allowMulti = Config.ALLOW_MULTIPLE_SAME_MOD.get();
        player.sendSystemMessage(Component.literal("==== Select Mod (total " + mods.size() + ") ===="));
        for (var e : mods) {
            boolean occ = occupied.contains(e.modId());
            String label = occ && !allowMulti
                    ? "[Occupied] " + e.displayName() + " (" + e.modId() + ")"
                    : "[Select] " + e.displayName() + " (" + e.modId() + ")";
            player.sendSystemMessage(
                    Component.literal(label)
                            .setStyle(Style.EMPTY.withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/yukari_select " + e.modId())))
            );
        }
        player.sendSystemMessage(Component.literal("[Become Spectator]")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/yukari_spectate"))));
    }

    public static void feedback(ServerPlayer player, String msg) {
        player.sendSystemMessage(Component.literal("[Yukari] " + msg));
    }
}