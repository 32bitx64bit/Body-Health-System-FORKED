package xyz.srgnis.bodyhealthsystem.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.registry.ModItems;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

//FIXME: this is a bit of a mess
//FIXME: null pointers
public class ServerNetworking {

    public static void initialize(){
        ServerPlayConnectionEvents.JOIN.register(ServerNetworking::syncBody);
        ServerPlayNetworking.registerGlobalReceiver(BHSMain.MOD_IDENTIFIER, ServerNetworking::handleUseHealingItem);
        ServerPlayNetworking.registerGlobalReceiver(id("data_request"), ServerNetworking::syncBody);
    }

    private static void syncBody(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity, ServerPlayNetworkHandler serverPlayNetworkHandler, PacketByteBuf packetByteBuf, PacketSender packetSender) {
        syncBody(serverPlayNetworkHandler.player.getWorld().getEntityById(packetByteBuf.readInt()),serverPlayNetworkHandler.player);
    }

    private static void syncBody(Entity entity, ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(entity.getId());

        var body = ((BodyProvider)entity).getBody();
        for (BodyPart part : body.getParts()) {
            buf.writeIdentifier(part.getIdentifier());
            buf.writeFloat(part.getHealth());
            buf.writeFloat(part.getMaxHealth());
            // bone state
            buf.writeBoolean(part.isBroken());
            boolean hasHalf = part.getBrokenTopHalf() != null;
            buf.writeBoolean(hasHalf);
            if (hasHalf) buf.writeBoolean(part.getBrokenTopHalf());
        }
        // Downed sync (server authoritative)
        buf.writeBoolean(body.isDowned());
        buf.writeInt(body.getBleedOutTicksRemaining());
        //Handled by ClientNetworking.updateEntity
        ServerPlayNetworking.send(player, id("data_request"), buf);
    }

    //TODO: to much logic here
    private static void handleUseHealingItem(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity, ServerPlayNetworkHandler serverPlayNetworkHandler, PacketByteBuf packetByteBuf, PacketSender packetSender) {
        Entity entity = serverPlayerEntity.getWorld().getEntityById(packetByteBuf.readInt());
        Identifier partID = packetByteBuf.readIdentifier();
        ItemStack itemStack = packetByteBuf.readItemStack();

        if (!(entity instanceof BodyProvider)) return;
        var body = ((BodyProvider) entity).getBody();
        BodyPart part = body.getPart(partID);
        if (part == null) return;

        boolean isUpgraded = itemStack.getItem() == ModItems.UPGRADED_MEDKIT_ITEM;

        if (isUpgraded) {
            boolean didSomething = false;
            // Fix bone if broken
            if (part.isBroken()) {
                part.setBroken(false);
                part.setBrokenTopHalf(null);
                part.setHealth(Math.max(1.0f, part.getHealth()));
                body.onBoneTreatmentApplied();
                didSomething = true;
            }
            // Heal some HP as base medkit
            if (part.getHealth() < part.getMaxHealth()) {
                body.healPart(4, partID);
                didSomething = true;
            }
            if (didSomething) {
                if (serverPlayerEntity.getInventory().getMainHandStack().getItem() == itemStack.getItem()){
                    serverPlayerEntity.getInventory().getMainHandStack().decrement(1);
                }else{
                    int slot = serverPlayerEntity.getInventory().getSlotWithStack(itemStack);
                    if (slot >= 0) serverPlayerEntity.getInventory().getStack(slot).decrement(1);
                }
                syncBody((PlayerEntity) entity);
            }
            return;
        }

        // Default medkit behaviour: heal only when damaged
        if (part.isDamaged()) {
            body.healPart(4, partID);
            if (serverPlayerEntity.getInventory().getMainHandStack().getItem() == itemStack.getItem()){
                serverPlayerEntity.getInventory().getMainHandStack().decrement(1);
            }else{
                int slot = serverPlayerEntity.getInventory().getSlotWithStack(itemStack);
                if (slot >= 0) serverPlayerEntity.getInventory().getStack(slot).decrement(1);
            }
            syncBody((PlayerEntity) entity);
        }
    }

    public static void syncBody(ServerPlayNetworkHandler spnh, PacketSender packetSender, MinecraftServer server){
        syncBody(spnh.player);
    }

    public static void syncBody(PlayerEntity pe){
        PacketByteBuf buf = PacketByteBufs.create();

        var body = ((BodyProvider)pe).getBody();
        for (BodyPart part : body.getParts()) {
            buf.writeIdentifier(part.getIdentifier());
            buf.writeFloat(part.getHealth());
            buf.writeFloat(part.getMaxHealth());
            // bone state
            buf.writeBoolean(part.isBroken());
            boolean hasHalf = part.getBrokenTopHalf() != null;
            buf.writeBoolean(hasHalf);
            if (hasHalf) buf.writeBoolean(part.getBrokenTopHalf());
        }
        // Also include downed state on player self-sync
        buf.writeBoolean(body.isDowned());
        buf.writeInt(body.getBleedOutTicksRemaining());
        //Handled by ClientNetworking.handleHealthChange
        ServerPlayNetworking.send( (ServerPlayerEntity) pe, BHSMain.MOD_IDENTIFIER, buf);
    }
}
