package xyz.srgnis.bodyhealthsystem.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

//FIXME: this is a bit of a mess
//FIXME: null pointers
public class ClientNetworking {

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(BHSMain.MOD_IDENTIFIER, ClientNetworking::handleHealthChange);
        ClientPlayNetworking.registerGlobalReceiver(id("data_request"), ClientNetworking::updateEntity);
    }

    private static void updateEntity(MinecraftClient client, ClientPlayNetworkHandler clientPlayNetworkHandler, PacketByteBuf buf, PacketSender packetSender) {
        Entity entity = client.player.getWorld().getEntityById(buf.readInt());
        // Read all parts first; remaining payload contains downed sync
        while (buf.isReadable()) {
            // Peek: if next is boolean and not an Identifier, break to read state
            int readerIndex = buf.readerIndex();
            try {
                Identifier idf = buf.readIdentifier();
                float health = buf.readFloat();
                float maxhealth = buf.readFloat();
                boolean broken = buf.readBoolean();
                boolean hasHalf = buf.readBoolean();
                boolean topHalf = hasHalf && buf.readBoolean();
                client.execute(() -> {
                    var part = ((BodyProvider) entity).getBody().getPart(idf);
                    part.setMaxHealth(maxhealth);
                    part.setHealth(health);
                    part.setBroken(broken);
                    part.setBrokenTopHalf(hasHalf ? topHalf : null);
                });
            } catch (Exception ex) {
                buf.readerIndex(readerIndex);
                break;
            }
        }
        if (buf.isReadable()) {
            boolean downed = buf.readBoolean();
            int bleed = buf.readInt();
            client.execute(() -> {
                var body = ((BodyProvider) entity).getBody();
                body.setDowned(downed);
                body.setBleedOutTicksRemaining(bleed);
            });
        }
    }

    public static void handleHealthChange(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender){
        // Read all parts; remaining payload contains optional downed sync
        while (buf.isReadable()) {
            int readerIndex = buf.readerIndex();
            try {
                Identifier idf = buf.readIdentifier();
                float health = buf.readFloat();
                float maxhealth = buf.readFloat();
                boolean broken = buf.readBoolean();
                boolean hasHalf = buf.readBoolean();
                boolean topHalf = hasHalf && buf.readBoolean();
                client.execute(() -> {
                    var part = ((BodyProvider) client.player).getBody().getPart(idf);
                    part.setMaxHealth(maxhealth);
                    part.setHealth(health);
                    part.setBroken(broken);
                    part.setBrokenTopHalf(hasHalf ? topHalf : null);
                });
            } catch (Exception ex) {
                buf.readerIndex(readerIndex);
                break;
            }
        }
        if (buf.isReadable()) {
            boolean downed = buf.readBoolean();
            int bleed = buf.readInt();
            client.execute(() -> {
                var body = ((BodyProvider) client.player).getBody();
                body.setDowned(downed);
                body.setBleedOutTicksRemaining(bleed);
            });
        }
    }

    public static void useHealingItem(Entity entity, Identifier partID, ItemStack itemStack){
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(entity.getId());
        buf.writeIdentifier(partID);
        buf.writeItemStack(itemStack);

        ClientPlayNetworking.send(BHSMain.MOD_IDENTIFIER, buf);
    }

    public static void requestBodyData(LivingEntity entity){
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(entity.getId());

        ClientPlayNetworking.send(id("data_request"), buf);
    }
}
