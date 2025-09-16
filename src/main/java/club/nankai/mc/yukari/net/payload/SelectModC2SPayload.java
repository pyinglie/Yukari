package club.nankai.mc.yukari.net.payload;

import club.nankai.mc.yukari.YukariMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectModC2SPayload(String selection) implements CustomPacketPayload {
    public static final String SPECTATE = "__SPECTATE__";
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(YukariMod.MOD_ID, "select_mod_c2s");
    public static final Type<SelectModC2SPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SelectModC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeUtf(pkt.selection),
                    buf -> new SelectModC2SPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}