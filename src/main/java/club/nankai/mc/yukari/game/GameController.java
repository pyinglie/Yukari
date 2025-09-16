package club.nankai.mc.yukari.game;

import club.nankai.mc.yukari.Config;
import club.nankai.mc.yukari.YukariMod;
import club.nankai.mc.yukari.data.PlayerDataManager;
import club.nankai.mc.yukari.net.ModNetworking;
import club.nankai.mc.yukari.select.ChatSelectionHelper;
import club.nankai.mc.yukari.util.ServerModCatalog;
import club.nankai.mc.yukari.util.TeleportUtil;
import club.nankai.mc.yukari.util.WorldResetManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

public class GameController {

    private static final double FINAL_BORDER_DIAMETER = 256.0;
    private static final double DEFAULT_BORDER_SIZE = 59_999_968D;
    private static GameController INSTANCE;
    private final MinecraftServer server;
    private State state = State.IDLE;
    private long startTick = -1;
    private int lastTenMinuteSlot = -1;
    private boolean oneMinuteWarnSent = false;
    private boolean borderCaptured = false;
    private double originalBorderSize = 0;
    private double originalBorderCenterX = 0;
    private double originalBorderCenterZ = 0;
    private GameController(MinecraftServer server) {
        this.server = server;
    }

    public static void init(MinecraftServer server) {
        INSTANCE = new GameController(server);
    }

    public static GameController get() {
        return INSTANCE;
    }

    public State getState() {
        return state;
    }

    public void startGame() {
        if (state != State.IDLE) {
            broadcast("Game already started. You can continue spectating or use /combatin to select a mod and join.");
            return;
        }
        if (ServerModCatalog.collectSelectableMods().isEmpty()) {
            broadcast("No selectable mods found.");
        }
        state = State.RUNNING;
        startTick = server.getTickCount();
        lastTenMinuteSlot = -1;
        oneMinuteWarnSent = false;
        captureBorderIfNeeded();
        broadcast("Randomly teleporting players...");

        int range = Config.RANDOM_TELEPORT_RANGE.get();
        int minRange = Config.MIN_RANDOM_DISTANCE.get();
        int tries = Config.SAFE_SPOT_MAX_TRIES.get();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String mod = PlayerDataManager.getSelectedMod(p.getUUID());
            if (mod != null) {
                boolean ok = TeleportUtil.randomSafeTeleportRing(p, minRange, range, tries);
                if (!ok) {
                    YukariMod.LOGGER.warn("Ring TP failed -> fallback normal TP: {}", p.getGameProfile().getName());
                    TeleportUtil.randomSafeTeleport(p, range, tries);
                }
                p.setGameMode(GameType.SURVIVAL);
                p.setRespawnPosition(p.serverLevel().dimension(), p.blockPosition(), 0.0F, true, false);
            } else {
                p.setGameMode(GameType.SPECTATOR);
            }
        }
        broadcast("Game started. Time until final: " + (Config.FINAL_PHASE_DELAY_TICKS.get() / 1200) + " minutes. Good luck!");
    }

    public void tick() {
        if (state == State.RUNNING) {
            long now = server.getTickCount();
            long elapsed = now - startTick;
            long delay = Config.FINAL_PHASE_DELAY_TICKS.get();
            long remaining = delay - elapsed;

            int slot = (int) (elapsed / 12_000L);
            if (slot != lastTenMinuteSlot) {
                lastTenMinuteSlot = slot;
                long minutesElapsed = elapsed / 1200;
                long minutesRemain = Math.max(0, remaining / 1200);
                broadcast("Time until final: " + minutesRemain + " minutes.");
            }
            if (!oneMinuteWarnSent && remaining <= 1_200L && remaining > 0) {
                oneMinuteWarnSent = true;
                broadcast("Final phase will start in 1 minute. You will be teleported to the world center to duel other players. Prepare and carry important items with you!");
            }
            if (remaining <= 0) enterFinal();
        }
    }

    private void enterFinal() {
        if (state != State.RUNNING) return;
        state = State.FINAL;
        ServerLevel ow = server.overworld();
        BlockPos center = new BlockPos(0, ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0) + 1, 0);
        applyFinalWorldBorder(ow);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isAlive()) {
                p.teleportTo(ow, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, p.getYRot(), p.getXRot());
                p.setRespawnPosition(ow.dimension(), center, 0.0F, true, false);
                if (PlayerDataManager.getSelectedMod(p.getUUID()) != null) {
                    p.setGameMode(GameType.SURVIVAL);
                }
            }
        }
        broadcast("Final phase started. World border has shrunk around the center area. You may now obtain and use any items. Good luck!");
    }

    public void resetGame() {
        WorldResetManager.markWorldForReset(server);
        state = State.IDLE;
        startTick = -1;
        lastTenMinuteSlot = -1;
        oneMinuteWarnSent = false;
        PlayerDataManager.clearAll();

        WorldBorder wb = server.overworld().getWorldBorder();
        wb.setCenter(0, 0);
        wb.setSize(DEFAULT_BORDER_SIZE);
        borderCaptured = false;

        ServerLevel ow = server.overworld();
        var spawn = ow.getSharedSpawnPos();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.getInventory().clearContent();
            p.setGameMode(GameType.SURVIVAL);
            p.setRespawnPosition(ow.dimension(), spawn, 0.0F, true, false);
            p.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
        }
        broadcast("Game has been reset.");
    }

    public void handlePlayerChooseMod(ServerPlayer player, String modId, boolean joinIfRunning) {
        if (!Config.ALLOW_MULTIPLE_SAME_MOD.get()) {
            boolean occ = PlayerDataManager.getAllPlayers().stream()
                    .anyMatch(uuid -> modId.equals(PlayerDataManager.getSelectedMod(uuid)));
            if (occ && !modId.equals(PlayerDataManager.getSelectedMod(player.getUUID()))) {
                player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "The mod is already occupied by other players."));
                return;
            }
        }
        boolean reselect = PlayerDataManager.getSelectedMod(player.getUUID()) != null
                && !modId.equals(PlayerDataManager.getSelectedMod(player.getUUID()));
        PlayerDataManager.setSelectedMod(player.getUUID(), modId);

        int range = Config.RANDOM_TELEPORT_RANGE.get();
        int minRange = Config.MIN_RANDOM_DISTANCE.get();
        int tries = Config.SAFE_SPOT_MAX_TRIES.get();

        switch (state) {
            case IDLE -> {
                boolean ok = TeleportUtil.randomSafeTeleportRing(player, minRange, range, tries);
                if (!ok) TeleportUtil.randomSafeTeleport(player, range, tries);
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Selected: " + modId + ", waiting for start (teleported)."));
            }
            case RUNNING -> {
                if (reselect) player.getInventory().clearContent();
                boolean ok = TeleportUtil.randomSafeTeleportRing(player, minRange, range, tries);
                if (!ok) TeleportUtil.randomSafeTeleport(player, range, tries);
                player.setGameMode(GameType.SURVIVAL);
                player.setRespawnPosition(player.serverLevel().dimension(), player.blockPosition(), 0.0F, true, false);
                player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Selected: " + modId + ", joined the combat!"));
            }
            case FINAL ->
                    player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Cannot change during final phase."));
        }
    }

    public void handlePlayerBecomeSpectator(ServerPlayer player, boolean explicit) {
        player.setGameMode(GameType.SPECTATOR);
        if (explicit) player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "You are now a spectator."));
    }

    public void syncPlayerStateOnLogin(ServerPlayer player, String mod) {
        switch (state) {
            case IDLE -> {
                if (mod != null) {
                    player.setGameMode(GameType.SPECTATOR);
                    player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Selected: " + mod + ", waiting for start."));
                }
            }
            case RUNNING -> {
                if (mod != null) {
                    player.setGameMode(GameType.SURVIVAL);
                } else {
                    player.setGameMode(GameType.SPECTATOR);
                    player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Game in progress; use /combatin to join."));
                }
            }
            case FINAL -> {
                if (mod != null) player.setGameMode(GameType.SURVIVAL);
                else {
                    player.setGameMode(GameType.SPECTATOR);
                    player.sendSystemMessage(Component.literal(YukariMod.MSG_PREFIX + "Spectating final phase."));
                }
            }
        }
    }

    public void openSelectionScreen(ServerPlayer player) {
        if (Config.USE_CHAT_SELECTION.get()) {
            ChatSelectionHelper.sendSelection(player);
        } else {
            ModNetworking.sendOpenSelection(player);
        }
    }

    public long getRemainingTicksToFinal() {
        if (state != State.RUNNING) return -1;
        long now = server.getTickCount();
        long elapsed = now - startTick;
        return Math.max(0, Config.FINAL_PHASE_DELAY_TICKS.get() - elapsed);
    }

    public void broadcast(String msg) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(YukariMod.MSG_PREFIX + msg), false);
        YukariMod.LOGGER.info(msg);
    }

    private void captureBorderIfNeeded() {
        if (borderCaptured) return;
        var wb = server.overworld().getWorldBorder();
        originalBorderCenterX = wb.getCenterX();
        originalBorderCenterZ = wb.getCenterZ();
        originalBorderSize = wb.getSize();
        borderCaptured = true;
    }

    private void applyFinalWorldBorder(ServerLevel level) {
        var wb = level.getWorldBorder();
        wb.setCenter(0.0, 0.0);
        wb.setSize(FINAL_BORDER_DIAMETER);
    }

    public enum State {IDLE, RUNNING, FINAL}
}

