package club.nankai.mc.yukari.net.payload;

import club.nankai.mc.yukari.YukariMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: send a list of (modId, displayName) plus occupied list and allowMultiple flag
 */
public record OpenSelectionS2CPayload(List<Entry> mods,
                                      List<String> occupied,
                                      boolean allowMultiple) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(YukariMod.MOD_ID, "open_selection_s2c");
    public static final Type<OpenSelectionS2CPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, OpenSelectionS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                        buf.writeVarInt(pkt.mods.size());
                        for (Entry e : pkt.mods) {
                            buf.writeUtf(e.modId());
                            buf.writeUtf(e.displayName());
                        }
                        buf.writeVarInt(pkt.occupied.size());
                        for (String o : pkt.occupied) buf.writeUtf(o);
                        buf.writeBoolean(pkt.allowMultiple);
                    },
                    buf -> {
                        int msz = buf.readVarInt();
                        List<Entry> entries = new ArrayList<>();
                        for (int i = 0; i < msz; i++) {
                            String id = buf.readUtf();
                            String dn = buf.readUtf();
                            entries.add(new Entry(id, dn));
                        }
                        int osz = buf.readVarInt();
                        List<String> occ = new ArrayList<>();
                        for (int i = 0; i < osz; i++) occ.add(buf.readUtf());
                        boolean allow = buf.readBoolean();
                        return new OpenSelectionS2CPayload(entries, occ, allow);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String modId, String displayName) {
    }
}