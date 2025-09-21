package xyz.srgnis.bodyhealthsystem.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
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
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.config.MidnightConfig;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

//FIXME: this is a bit of a mess
//FIXME: null pointers
public class ClientNetworking {
    private static final Map<Integer, Double> LAST_BODY_TEMP_C = new ConcurrentHashMap<>();

    public static void initialize(){
        // Login-time config sync: server tells us if temperature system is required/enabled.
        ClientLoginNetworking.registerGlobalReceiver(id("temp_cfg"), (client, handler, buf, responseSender) -> {
            boolean serverEnabled = buf.readBoolean();
            // Mirror server setting clientside so UI/logic stays consistent
            Config.enableTemperatureSystem = serverEnabled;
            // Persist to disk so future sessions match the server automatically
            try { MidnightConfig.write(BHSMain.MOD_ID); } catch (Throwable ignored) {}
            PacketByteBuf reply = PacketByteBufs.create();
            reply.writeBoolean(Config.enableTemperatureSystem);
            return CompletableFuture.completedFuture(reply);
        });

        ClientPlayNetworking.registerGlobalReceiver(BHSMain.MOD_IDENTIFIER, ClientNetworking::handleHealthChange);
        ClientPlayNetworking.registerGlobalReceiver(id("data_request"), ClientNetworking::updateEntity);
        ClientPlayNetworking.registerGlobalReceiver(id("temp_sync"), (client, handler, buf, sender) -> {
            int entityId = buf.readInt();
            double tempC = buf.readDouble();
            client.execute(() -> LAST_BODY_TEMP_C.put(entityId, tempC));
        });
    }

    private static void updateEntity(MinecraftClient client, ClientPlayNetworkHandler clientPlayNetworkHandler, PacketByteBuf buf, PacketSender packetSender) {
        // Use handler world; client.player may not be initialized yet on join
        var world = clientPlayNetworkHandler.getWorld();
        int entityId = buf.readInt();
        Entity entity = world != null ? world.getEntityById(entityId) : null;
        if (entity == null) {
            // Drop if entity isn't available yet; server will keep us in sync via later broadcasts
            return;
        }
        // Read all parts first; remaining payload contains downed sync and temperature
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
                    if (!(entity instanceof BodyProvider bp)) return;
                    var part = bp.getBody().getPart(idf);
                    if (part == null) return;
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
            boolean revived = buf.readBoolean();
            client.execute(() -> {
                if (!(entity instanceof BodyProvider bp)) return;
                var body = bp.getBody();
                if (body == null) return;
                body.setDowned(downed);
                body.setBleedOutTicksRemaining(bleed);
                body.setBeingRevived(revived);
            });
        }
        // No trailing fields expected
    }

    public static void handleHealthChange(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender){
        // Read all parts; remaining payload contains optional downed sync and temperature
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
                    if (!(client.player instanceof BodyProvider bp)) return;
                    var part = bp.getBody().getPart(idf);
                    if (part == null) return;
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
            boolean revived = buf.readBoolean();
            client.execute(() -> {
                if (!(client.player instanceof BodyProvider bp)) return;
                var body = bp.getBody();
                if (body == null) return;
                body.setDowned(downed);
                body.setBleedOutTicksRemaining(bleed);
                body.setBeingRevived(revived);
            });
        }
        // No trailing fields expected
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

        ClientPlayNetworking.send(id("temp_request"), buf);
    }

    public static Double getLastBodyTempC(Entity entity) {
        if (entity == null) return null;
        return LAST_BODY_TEMP_C.get(entity.getId());
    }
}
