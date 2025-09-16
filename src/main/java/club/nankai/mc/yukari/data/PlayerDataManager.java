package club.nankai.mc.yukari.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistence: mapping from player UUID to selected modId.
 */
public class PlayerDataManager {
    private static boolean initialized = false;
    private static PlayerSelectionData DATA;

    public static void init(MinecraftServer server) {
        if (initialized) return;
        ServerLevel ow = server.overworld();
        DATA = ow.getDataStorage().computeIfAbsent(PlayerSelectionData.FACTORY, PlayerSelectionData.NAME);
        initialized = true;
    }

    public static boolean isUninitialized() {
        return !initialized;
    }

    public static String getSelectedMod(UUID uuid) {
        return DATA.map.get(uuid.toString());
    }

    public static void setSelectedMod(UUID uuid, String modId) {
        DATA.map.put(uuid.toString(), modId);
        DATA.setDirty();
    }

    public static void removeSelectedMod(UUID uuid) {
        DATA.map.remove(uuid.toString());
        DATA.setDirty();
    }

    public static void clearAll() {
        DATA.map.clear();
        DATA.setDirty();
    }

    public static Set<UUID> getAllPlayers() {
        return DATA.map.keySet().stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    public static class PlayerSelectionData extends SavedData {
        public static final String NAME = "yukari_player_selection";
        public static final Factory<PlayerSelectionData> FACTORY =
                new Factory<>(PlayerSelectionData::new, PlayerSelectionData::load, null);
        final Map<String, String> map = new HashMap<>();

        public PlayerSelectionData() {
        }

        public static PlayerSelectionData load(CompoundTag tag, HolderLookup.Provider provider) {
            PlayerSelectionData d = new PlayerSelectionData();
            ListTag list = tag.getList("entries", 8);
            for (int i = 0; i < list.size(); i++) {
                String line = list.getString(i);
                int s = line.indexOf('|');
                if (s > 0) {
                    d.map.put(line.substring(0, s), line.substring(s + 1));
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (Map.Entry<String, String> e : map.entrySet()) {
                list.add(StringTag.valueOf(e.getKey() + "|" + e.getValue()));
            }
            tag.put("entries", list);
            return tag;
        }
    }
}