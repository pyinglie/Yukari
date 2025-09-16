package club.nankai.mc.yukari.client;

import club.nankai.mc.yukari.client.screen.ModSelectScreen;
import club.nankai.mc.yukari.net.payload.OpenSelectionS2CPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientAccess {
    public static void openSelectionScreen(OpenSelectionS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        mc.setScreen(new ModSelectScreen(payload.mods(), payload.occupied(), payload.allowMultiple()));
    }
}