package xyz.srgnis.bodyhealthsystem.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

public final class TimerSync {
    private TimerSync() {}
    public static final Identifier ID_TIMERS = BHSMain.id("timers");

    public static void sendSelf(ServerPlayerEntity spe) {
        var body = ((BodyProvider) spe).getBody();
        PacketByteBuf buf = PacketByteBufs.create();
        for (BodyPart p : body.getParts()) {
            buf.writeIdentifier(p.getIdentifier());
            buf.writeInt(p.getTourniquetTicks());
            buf.writeInt(p.getNecrosisState());
            buf.writeFloat(p.getNecrosisScale());
        }
        ServerPlayNetworking.send(spe, ID_TIMERS, buf);
    }
}
