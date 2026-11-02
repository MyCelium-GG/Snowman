package my.celium.org.snowmen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the rotate key was pressed. {@code back} turns the other way (sneaking). */
public record RotatePacket(boolean back) implements CustomPacketPayload {
    public static final Type<RotatePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SnowmenMod.ID, "rotate"));
    public static final StreamCodec<FriendlyByteBuf, RotatePacket> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeBoolean(packet.back),
            buf -> new RotatePacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
