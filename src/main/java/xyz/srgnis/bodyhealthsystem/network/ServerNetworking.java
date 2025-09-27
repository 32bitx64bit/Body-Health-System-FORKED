package xyz.srgnis.bodyhealthsystem.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.registry.ModItems;
import xyz.srgnis.bodyhealthsystem.registry.ScreenHandlers;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

//FIXME: this is a bit of a mess
//FIXME: null pointers
public class ServerNetworking {

    public static void initialize(){
        // Login-time config handshake: send required temperature setting and verify client supports it
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            PacketByteBuf cfg = PacketByteBufs.create();
            cfg.writeBoolean(Config.enableTemperatureSystem);
            sender.sendPacket(id("temp_cfg"), cfg);
        });
        ServerLoginNetworking.registerGlobalReceiver(id("temp_cfg"), (server, handler, understood, buf, synchronizer, responseSender) -> {
            if (!understood) {
                // Old client without our handshake; if server requires temperature, refuse join
                if (Config.enableTemperatureSystem) {
                    handler.disconnect(Text.literal("This server requires Body Health System with temperature sync support. Please update your client mod."));
                }
                return;
            }
            boolean clientEnabled = buf.readBoolean();
            if (Config.enableTemperatureSystem && !clientEnabled) {
                handler.disconnect(Text.literal("This server requires the temperature system to be enabled in Body Health System config."));
            }
        });

        ServerPlayConnectionEvents.JOIN.register(ServerNetworking::syncBody);
        ServerPlayNetworking.registerGlobalReceiver(BHSMain.MOD_IDENTIFIER, ServerNetworking::handleUseHealingItem);
        ServerPlayNetworking.registerGlobalReceiver(id("data_request"), ServerNetworking::syncBody);
        ServerPlayNetworking.registerGlobalReceiver(id("give_up"), (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (!(player instanceof BodyProvider)) return;
                var body = ((BodyProvider) player).getBody();
                if (body != null && body.isDowned()) {
                    body.forceGiveUp();
                }
            });
        });
        // Temperature request/response
        ServerPlayNetworking.registerGlobalReceiver(id("temp_request"), (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            server.execute(() -> {
                Entity e = player.getWorld().getEntityById(entityId);
                if (!(e instanceof ServerPlayerEntity spe)) return;
                double tempC = 0.0;
                try { tempC = gavinx.temperatureapi.BodyTemperatureState.getC(spe); } catch (Throwable ignored) {}
                PacketByteBuf out = PacketByteBufs.create();
                out.writeInt(entityId);
                out.writeDouble(tempC);
                ServerPlayNetworking.send(player, id("temp_sync"), out);
            });
        });

        // AC mode toggle from client UI
        ServerPlayNetworking.registerGlobalReceiver(id("ac_mode"), (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean regulate = buf.readBoolean();
            server.execute(() -> {
                var world = player.getWorld();
                var be = world.getBlockEntity(pos);
                if (be instanceof xyz.srgnis.bodyhealthsystem.block.AirConditionerBlockEntity ac) {
                    ac.setRegulating(regulate);
                    var state = world.getBlockState(pos);
                    if (state.getBlock() instanceof xyz.srgnis.bodyhealthsystem.block.AirConditionerBlock) {
                        if (state.contains(xyz.srgnis.bodyhealthsystem.block.AirConditionerBlock.REGULATE)
                                && state.get(xyz.srgnis.bodyhealthsystem.block.AirConditionerBlock.REGULATE) != regulate) {
                            world.setBlockState(pos, state.with(xyz.srgnis.bodyhealthsystem.block.AirConditionerBlock.REGULATE, regulate), 3);
                        }
                    }
                }
            });
        });

        // Space Heater mode toggle
        ServerPlayNetworking.registerGlobalReceiver(id("heater_mode"), (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean regulate = buf.readBoolean();
            server.execute(() -> {
                var world = player.getWorld();
                var be = world.getBlockEntity(pos);
                if (be instanceof xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlockEntity sh) {
                    sh.setRegulating(regulate);
                    var state = world.getBlockState(pos);
                    if (state.getBlock() instanceof xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock) {
                        if (state.contains(xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock.REGULATE)
                                && state.get(xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock.REGULATE) != regulate) {
                            world.setBlockState(pos, state.with(xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock.REGULATE, regulate), 3);
                        }
                    }
                }
            });
        });
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
        buf.writeBoolean(body.isBeingRevived());
        //Handled by ClientNetworking.updateEntity
        ServerPlayNetworking.send(player, id("data_request"), buf);
    }

    public static void broadcastBody(Entity entity) {
        if (!(entity instanceof BodyProvider)) return;
        // Send to the entity itself if it's a player, using the self channel (no entity lookup client-side)
        if (entity instanceof ServerPlayerEntity self) {
            syncSelf(self);
        }
        // Send to all tracking players (they need the entity id)
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(entity)) {
            syncBody(entity, watcher);
        }
    }

    private static void syncSelf(ServerPlayerEntity self) {
        PacketByteBuf buf = PacketByteBufs.create();
        var body = ((BodyProvider) self).getBody();
        for (BodyPart part : body.getParts()) {
            buf.writeIdentifier(part.getIdentifier());
            buf.writeFloat(part.getHealth());
            buf.writeFloat(part.getMaxHealth());
            buf.writeBoolean(part.isBroken());
            boolean hasHalf = part.getBrokenTopHalf() != null;
            buf.writeBoolean(hasHalf);
            if (hasHalf) buf.writeBoolean(part.getBrokenTopHalf());
        }
        buf.writeBoolean(body.isDowned());
        buf.writeInt(body.getBleedOutTicksRemaining());
        buf.writeBoolean(body.isBeingRevived());
        ServerPlayNetworking.send(self, BHSMain.MOD_IDENTIFIER, buf);
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
        // On join, schedule next-tick sync to ensure client world/entities are ready
        ServerPlayerEntity self = spnh.player;
        server.execute(() -> {
            syncSelf(self);
            syncBody(self, self);
        });
    }

    public static void syncBody(PlayerEntity pe){
        if (pe instanceof ServerPlayerEntity self) {
            syncSelf(self);
        }
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(pe)) {
            syncBody(pe, watcher);
        }
    }
}
