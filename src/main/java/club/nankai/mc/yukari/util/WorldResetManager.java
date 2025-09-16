package club.nankai.mc.yukari.util;

import club.nankai.mc.yukari.YukariMod;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * World "full reset":
 * - /combatrun calls markWorldForReset(server) which writes a marker file .yukari_world_reset
 * - On next server startup onServerStarting detects the marker and performs:
 * 1. Back up region/poi/entities directories into backups/
 * 2. Delete those directories to restore to a fresh world (terrain will regenerate)
 */
public class WorldResetManager {
    private static final String MARK_FILE = ".yukari_world_reset";

    public static void markWorldForReset(MinecraftServer server) {
        Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path mark = root.resolve(MARK_FILE);
        try {
            Files.writeString(mark, "reset requested at " + LocalDateTime.now());
            YukariMod.LOGGER.info("World reset mark file created: {}", mark);
        } catch (IOException e) {
            YukariMod.LOGGER.error("Failed to create reset mark file", e);
        }
    }

    public static void onServerStarting(MinecraftServer server) {
        Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path mark = root.resolve(MARK_FILE);
        if (!Files.exists(mark)) return;

        YukariMod.LOGGER.warn("Detected world reset mark. Performing cleanup...");

        // Only process OVERWORLD's region/poi/entities
        Path overworld = root.resolve("region"); // old path may be world/region
        // 1.21 new layout: DIM* subdirectories under root; be conservative here: actual path may be server.overworld().convertable?
        // For simplicity: handle region, poi, entities under the world root if they exist
        cleanDir(root.resolve("region"), root);
        cleanDir(root.resolve("poi"), root);
        cleanDir(root.resolve("entities"), root);

        try {
            Files.deleteIfExists(mark);
        } catch (IOException e) {
            YukariMod.LOGGER.error("Failed to delete mark file", e);
        }

        YukariMod.LOGGER.warn("World reset process finished (regions deleted). New terrain will generate.");
    }

    private static void cleanDir(Path dir, Path worldRoot) {
        if (!Files.exists(dir)) return;
        try {
            Path backups = worldRoot.resolve("backups");
            Files.createDirectories(backups);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path backupDir = backups.resolve(dir.getFileName().toString() + "_" + stamp);
            Files.createDirectories(backupDir);

            // Move (rename) instead of copy+delete for speed
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Path target = backupDir.resolve(dir.relativize(p).toString());
                            if (Files.isDirectory(p)) {
                                Files.createDirectories(target);
                            } else {
                                Files.createDirectories(target.getParent());
                                Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException ex) {
                            YukariMod.LOGGER.error("Backup move failed for {}", p, ex);
                        }
                    });
            // Delete empty structure
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            YukariMod.LOGGER.error("Error cleaning directory {} for world reset", dir, e);
        }
    }
}