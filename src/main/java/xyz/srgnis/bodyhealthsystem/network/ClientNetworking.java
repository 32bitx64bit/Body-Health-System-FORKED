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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.config.Config;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

/**
 * Client-side network handler for Body Health System.
 * Handles incoming packets from server and sends requests.
 */
public class ClientNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(BHSMain.MOD_ID + "/ClientNetworking");
    private static final Map<Integer, Double> LAST_BODY_TEMP_C = new ConcurrentHashMap<>();
    
    // Rate limiting for packet sending (prevent spam)
    private static final long PACKET_COOLDOWN_MS = 50; // 50ms minimum between packets
    private static final AtomicLong lastHealPacketTime = new AtomicLong(0);
    private static final AtomicLong lastTourniquetPacketTime = new AtomicLong(0);

    public static void initialize(){
        // Login-time config sync: server tells us if temperature system is required/enabled.
        ClientLoginNetworking.registerGlobalReceiver(id("temp_cfg"), (client, handler, buf, responseSender) -> {
            boolean serverTemp = buf.readBoolean();
            boolean serverBones = buf.readBoolean();
            boolean serverWounds = buf.readBoolean();
            // Mirror server-required toggles clientside for UI/logic during this session only
            Config.enableTemperatureSystem = serverTemp;
            Config.enableBoneSystem = serverBones;
            Config.enableWoundingSystem = serverWounds;
            PacketByteBuf reply = PacketByteBufs.create();
            reply.writeBoolean(Config.enableTemperatureSystem);
            reply.writeBoolean(Config.enableBoneSystem);
            reply.writeBoolean(Config.enableWoundingSystem);
            return CompletableFuture.completedFuture(reply);
        });

        ClientPlayNetworking.registerGlobalReceiver(BHSMain.MOD_IDENTIFIER, ClientNetworking::handleHealthChange);
        ClientPlayNetworking.registerGlobalReceiver(id("data_request"), ClientNetworking::updateEntity);
        ClientPlayNetworking.registerGlobalReceiver(id("temp_sync"), (client, handler, buf, sender) -> {
            int entityId = buf.readInt();
            double tempC = buf.readDouble();
            client.execute(() -> LAST_BODY_TEMP_C.put(entityId, tempC));
        });

        // Timers-only sync for tooltip updates
        ClientPlayNetworking.registerGlobalReceiver(xyz.srgnis.bodyhealthsystem.network.TimerSync.ID_TIMERS, (client, handler, buf, sender) -> {
            // Read flat list of parts and minimal timer state
            while (buf.isReadable()) {
                Identifier idf = null;
                try {
                    idf = buf.readIdentifier();
                } catch (Exception ex) { break; }
                int tqTicks = buf.readInt();
                int necState = buf.readInt();
                float necScale = buf.readFloat();
                Identifier finalId = idf;
                client.execute(() -> {
                    var p = (client.player instanceof BodyProvider bp) ? bp.getBody().getPart(finalId) : null;
                    if (p != null) {
                        p.clientSetTourniquet(p.hasTourniquet(), tqTicks);
                        p.clientSetNecrosis(necState, necScale);
                    }
                });
            }
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
        readBodyPayload(client, entity, buf);
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
        readBodyPayload(client, client.player, buf);
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
        // Rate limit to prevent packet spam
        long now = System.currentTimeMillis();
        long last = lastHealPacketTime.get();
        if (now - last < PACKET_COOLDOWN_MS) {
            LOGGER.debug("Rate limiting heal packet");
            return;
        }
        lastHealPacketTime.set(now);
        
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entity.getId());
        buf.writeIdentifier(partID);
        buf.writeItemStack(itemStack);
        ClientPlayNetworking.send(BHSMain.MOD_IDENTIFIER, buf);
    }

    public static void removeTourniquet(LivingEntity entity, Identifier partId) {
        // Rate limit to prevent packet spam
        long now = System.currentTimeMillis();
        long last = lastTourniquetPacketTime.get();
        if (now - last < PACKET_COOLDOWN_MS) {
            LOGGER.debug("Rate limiting tourniquet packet");
            return;
        }
        lastTourniquetPacketTime.set(now);
        
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entity.getId());
        buf.writeIdentifier(partId);
        ClientPlayNetworking.send(BHSMain.id("remove_tourniquet"), buf);
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

    // Shared body payload reader for both full-state packet handlers
    private static void readBodyPayload(MinecraftClient client, Entity entity, PacketByteBuf buf) {
        while (buf.isReadable()) {
            int readerIndex = buf.readerIndex();
            try {
                Identifier idf = buf.readIdentifier();
                float health = buf.readFloat();
                float maxhealth = buf.readFloat();
                boolean broken = buf.readBoolean();
                boolean hasHalf = buf.readBoolean();
                boolean topHalf = hasHalf && buf.readBoolean();
                boolean fractureLocked = buf.readBoolean();
                float partAbs = buf.readFloat();
                float partBoost = buf.readFloat();
                int sWounds = buf.readInt();
                int lWounds = buf.readInt();
                boolean tq = buf.readBoolean();
                int tqTicks = buf.readInt();
                int necState = buf.readInt();
                float necScale = buf.readFloat();
                client.execute(() -> {
                    if (!(entity instanceof BodyProvider bp)) {
                        LOGGER.debug("Entity is not a BodyProvider during body payload read");
                        return;
                    }
                    var body = bp.getBody();
                    if (body == null) {
                        LOGGER.warn("Body is null during payload read");
                        return;
                    }
                    var part = body.getPart(idf);
                    if (part == null) {
                        LOGGER.debug("Part {} not found during payload read", idf);
                        return;
                    }
                    // Order matters: set base max, necrosis scale, then buckets, then health to avoid over/under clamp
                    part.setMaxHealth(maxhealth);
                    part.clientSetNecrosis(necState, necScale);
                    body.clientSetAbsorptionBucket(idf, partAbs);
                    body.clientSetBoostBucket(idf, partBoost);
                    part.setHealth(health);
                    part.setBroken(broken);
                    part.setBrokenTopHalf(hasHalf ? topHalf : null);
                    part.setFractureLocked(fractureLocked);
                    part.clientSetWounds(sWounds, lWounds);
                    part.clientSetTourniquet(tq, tqTicks);
                });
            } catch (Exception ex) {
                LOGGER.debug("Exception reading body payload: {}", ex.getMessage());
                buf.readerIndex(readerIndex);
                break;
            }
        }
    }
}
