package club.nankai.mc.yukari.client.overlay;

import club.nankai.mc.yukari.YukariMod;
import club.nankai.mc.yukari.client.screen.ModSelectScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * After a Screen has finished rendering (including possible third-party blurs),
 * re-render our selection list and background to ensure clarity.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = YukariMod.MOD_ID, value = Dist.CLIENT)
public class SelectionScreenPostRenderer {

    @SubscribeEvent
    public static void afterRender(ScreenEvent.Render.Post evt) {
        if (evt.getScreen() instanceof ModSelectScreen screen) {
            // redraw on top
            screen.repaintAfterBlur(evt.getGuiGraphics(), evt.getMouseX(), evt.getMouseY(), evt.getPartialTick());
        }
    }
}