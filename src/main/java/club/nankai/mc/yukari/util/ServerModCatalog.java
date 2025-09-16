package club.nankai.mc.yukari.util;

import club.nankai.mc.yukari.Config;
import club.nankai.mc.yukari.YukariMod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

import java.util.*;

/**
 * Dynamically collect server-selectable mods (excluding minecraft / neoforge / yukari).
 * If the result is empty, fall back to the configured classicMods.
 */
public class ServerModCatalog {

    private static final Set<String> EXCLUDE = Set.of("minecraft", "neoforge", YukariMod.MOD_ID);

    public static List<ModEntry> collectSelectableMods() {
        List<ModEntry> list = new ArrayList<>();
        for (var info : ModList.get().getMods()) {
            if (info instanceof ModInfo mi) {
                String id = mi.getModId();
                if (EXCLUDE.contains(id)) continue;
                String disp = (mi.getDisplayName() == null || mi.getDisplayName().isBlank()) ? id : mi.getDisplayName();
                list.add(new ModEntry(id, disp));
            }
        }
        if (list.isEmpty()) {
            for (String fb : Config.CLASSIC_MODS.get()) {
                list.add(new ModEntry(fb.toLowerCase(Locale.ROOT), fb));
            }
        }
        list.sort(Comparator.comparing(ModEntry::modId));
        return list;
    }

    public static boolean isValidModId(String modId) {
        return collectSelectableMods().stream().anyMatch(e -> e.modId().equals(modId));
    }

    public record ModEntry(String modId, String displayName) {
    }
}