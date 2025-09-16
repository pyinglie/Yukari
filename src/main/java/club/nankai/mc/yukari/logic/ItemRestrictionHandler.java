package club.nankai.mc.yukari.logic;

import club.nankai.mc.yukari.Config;
import club.nankai.mc.yukari.YukariMod;
import club.nankai.mc.yukari.data.PlayerDataManager;
import club.nankai.mc.yukari.game.GameController;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Before FINAL: allow items from the chosen mod namespace or the minecraft namespace.
 * After FINAL: allow everything.
 */
@EventBusSubscriber(modid = YukariMod.MOD_ID)
public class ItemRestrictionHandler {

    private static final Map<UUID, Long> LAST_WARN = new HashMap<>();

    private static boolean activeRestriction() {
        GameController gc = GameController.get();
        return gc != null && gc.getState() != GameController.State.FINAL;
    }

    private static boolean isAllowed(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (!activeRestriction()) return true;
        String chosen = PlayerDataManager.getSelectedMod(player.getUUID());
        if (chosen == null) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return false;
        String ns = key.getNamespace();
        return ns.equals("minecraft") || ns.equals(chosen);
    }

    private static void warn(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long cd = Config.RESTRICTION_MESSAGE_COOLDOWN_SEC.get() * 1000L;
        long last = LAST_WARN.getOrDefault(player.getUUID(), 0L);
        if (now - last >= cd) {
            player.sendSystemMessage(Component.translatable("yukari.msg.restriction_warning"));
            LAST_WARN.put(player.getUUID(), now);
        }
    }

    // Pickup
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre evt) {
        if (!(evt.getPlayer() instanceof ServerPlayer sp)) return;
        ItemEntity itemEntity = evt.getItemEntity();
        ItemStack stack = itemEntity.getItem();
        if (!isAllowed(sp, stack)) {
            evt.setCanPickup(TriState.FALSE);
            warn(sp);
        }
    }

    // Crafting
    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack out = evt.getCrafting();
        if (!isAllowed(sp, out)) {
            out.setCount(0);
            warn(sp);
        }
    }

    // Smelting
    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack out = evt.getSmelting();
        if (!isAllowed(sp, out)) {
            out.setCount(0);
            warn(sp);
        }
    }

    // Right-click using held item
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        if (!isAllowed(sp, evt.getItemStack())) {
            evt.setCanceled(true);
            evt.setCancellationResult(InteractionResult.FAIL);
            warn(sp);
        }
    }

    // Right-click block
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        if (!isAllowed(sp, evt.getItemStack())) {
            evt.setCanceled(true);
            evt.setCancellationResult(InteractionResult.FAIL);
            warn(sp);
        }
    }

    // Right-click entity
    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        if (!isAllowed(sp, evt.getItemStack())) {
            evt.setCanceled(true);
            evt.setCancellationResult(InteractionResult.FAIL);
            warn(sp);
        }
    }

    // Inventory cleanup
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        if (!activeRestriction()) return;
        String chosen = PlayerDataManager.getSelectedMod(sp.getUUID());
        if (chosen == null) {
            purgeInventory(sp, st -> !(st.isEmpty())); // If no selection, disallow any non-empty items
            return;
        }
        purgeInventory(sp, st -> {
            if (st.isEmpty()) return false;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(st.getItem());
            if (key == null) return true;
            String ns = key.getNamespace();
            return !(ns.equals("minecraft") || ns.equals(chosen));
        });
    }

    private static void purgeInventory(ServerPlayer sp, Predicate<ItemStack> removePredicate) {
        var inv = sp.getInventory();
        boolean removedAny = false;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack st = inv.getItem(slot);
            if (removePredicate.test(st)) {
                inv.setItem(slot, ItemStack.EMPTY);
                removedAny = true;
            }
        }
        if (removedAny) {
            warn(sp);
            sp.containerMenu.broadcastChanges();
        }
    }
}