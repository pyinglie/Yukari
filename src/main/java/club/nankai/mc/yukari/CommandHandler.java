package club.nankai.mc.yukari;

import club.nankai.mc.yukari.data.PlayerDataManager;
import club.nankai.mc.yukari.game.GameController;
import club.nankai.mc.yukari.util.ServerModCatalog;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class CommandHandler {

    public static void register(CommandDispatcher<CommandSourceStack> d) {

        d.register(Commands.literal("combatstart")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    var gc = GameController.get();
                    if (gc == null) {
                        ctx.getSource().sendFailure(Component.translatable("yukari.msg.controller_not_initialized"));
                        return 0;
                    }
                    gc.startGame();
                    return 1;
                }));

        d.register(Commands.literal("combatrun")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    var gc = GameController.get();
                    if (gc == null) {
                        ctx.getSource().sendFailure(Component.translatable("yukari.msg.controller_not_initialized"));
                        return 0;
                    }
                    gc.resetGame();
                    return 1;
                }));

        d.register(Commands.literal("combattime")
                .executes(ctx -> {
                    var gc = GameController.get();
                    if (gc == null) {
                        ctx.getSource().sendFailure(Component.translatable("yukari.msg.controller_not_initialized"));
                        return 0;
                    }
                    if (gc.getState() == GameController.State.FINAL) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("yukari.msg.final_started"), false);
                        return 1;
                    }
                    if (gc.getState() != GameController.State.RUNNING) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("yukari.msg.not_started"), false);
                        return 1;
                    }
                    long ticks = gc.getRemainingTicksToFinal();
                    long sec = ticks / 20;
                    long m = sec / 60;
                    long s = sec % 60;
                    ctx.getSource().sendSuccess(() ->
                            Component.translatable("yukari.msg.time_until_final", m, s), false);
                    return 1;
                }));

        d.register(Commands.literal("combatin")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                        ctx.getSource().sendFailure(Component.translatable("yukari.msg.must_be_player"));
                        return 0;
                    }
                    var gc = GameController.get();
                    if (gc == null) {
                        player.sendSystemMessage(Component.translatable("yukari.msg.controller_not_initialized"));
                        return 0;
                    }
                    if (gc.getState() == GameController.State.FINAL) {
                        player.sendSystemMessage(Component.translatable("yukari.msg.cannot_join_final"));
                        return 0;
                    }
                    gc.openSelectionScreen(player);
                    return 1;
                }));

        d.register(Commands.literal("combattp")
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer spectator)) {
                                ctx.getSource().sendFailure(Component.translatable("yukari.msg.must_be_player"));
                                return 0;
                            }
                            if (spectator.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                                spectator.sendSystemMessage(Component.translatable("yukari.msg.only_spectators"));
                                return 0;
                            }
                            String targetName = StringArgumentType.getString(ctx, "target");
                            ServerPlayer target = spectator.server.getPlayerList().getPlayerByName(targetName);
                            if (target == null) {
                                spectator.sendSystemMessage(Component.translatable("yukari.msg.player_not_found", targetName));
                                return 0;
                            }
                            String mod = PlayerDataManager.getSelectedMod(target.getUUID());
                            if (mod == null || target.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                                spectator.sendSystemMessage(Component.translatable("yukari.msg.target_not_in_combat"));
                                return 0;
                            }
                            // teleport (cross-dimension)
                            spectator.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                            spectator.sendSystemMessage(Component.translatable("yukari.msg.teleported_to", targetName));
                            return 1;
                        }))
        );

        d.register(Commands.literal("yukari_select")
                .then(Commands.argument("mod", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
                                return 0;
                            var gc = GameController.get();
                            if (gc == null) {
                                player.sendSystemMessage(Component.translatable("yukari.msg.controller_not_initialized"));
                                return 0;
                            }
                            if (gc.getState() == GameController.State.FINAL) {
                                player.sendSystemMessage(Component.translatable("yukari.msg.cannot_change_final"));
                                return 0;
                            }
                            String modId = StringArgumentType.getString(ctx, "mod");
                            if (!ServerModCatalog.isValidModId(modId)) {
                                player.sendSystemMessage(Component.translatable("yukari.msg.invalid_mod", modId));
                                return 0;
                            }
                            gc.handlePlayerChooseMod(player, modId, true);
                            return 1;
                        })));

        d.register(Commands.literal("yukari_spectate")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
                        return 0;
                    var gc = GameController.get();
                    if (gc == null) {
                        player.sendSystemMessage(Component.translatable("yukari.msg.controller_not_initialized"));
                        return 0;
                    }
                    gc.handlePlayerBecomeSpectator(player, true);
                    return 1;
                }));
    }
}