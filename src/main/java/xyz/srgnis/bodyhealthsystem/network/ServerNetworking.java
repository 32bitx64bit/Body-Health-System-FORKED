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

        // Remove tourniquet request (UI-triggered without holding the item)
        ServerPlayNetworking.registerGlobalReceiver(id("remove_tourniquet"), (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            Identifier partId = buf.readIdentifier();
            server.execute(() -> {
                Entity e = player.getWorld().getEntityById(entityId);
                if (!(e instanceof BodyProvider)) return;
                var body = ((BodyProvider) e).getBody();
                BodyPart part = body.getPart(partId);
                if (part == null) return;
                try {
                    boolean has = (boolean) part.getClass().getMethod("hasTourniquet").invoke(part);
                    if (!has) return;
                    // Toggle off this part
                    part.getClass().getMethod("setTourniquet", boolean.class).invoke(part, false);
                    // Mirror if leg/foot
                    var pid = part.getIdentifier();
                    if (pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG) || pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT)){
                        BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG);
                        BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT);
                        if (leg != null) leg.getClass().getMethod("setTourniquet", boolean.class).invoke(leg, false);
                        if (foot != null) foot.getClass().getMethod("setTourniquet", boolean.class).invoke(foot, false);
                    }
                    if (pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG) || pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT)){
                        BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG);
                        BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT);
                        if (leg != null) leg.getClass().getMethod("setTourniquet", boolean.class).invoke(leg, false);
                        if (foot != null) foot.getClass().getMethod("setTourniquet", boolean.class).invoke(foot, false);
                    }
                } catch (Throwable ignored) {}
                // Refund one Tourniquet item
                player.giveItemStack(new net.minecraft.item.ItemStack(xyz.srgnis.bodyhealthsystem.registry.ModItems.TOURNIQUET));
                // Sync
                if (e instanceof PlayerEntity pe) syncBody(pe);
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
        body.prepareBucketSync();
        for (BodyPart part : body.getParts()) {
            Identifier idf = part.getIdentifier();
            buf.writeIdentifier(idf);
            buf.writeFloat(part.getHealth());
            buf.writeFloat(part.getMaxHealth());
            // bone state
            buf.writeBoolean(part.isBroken());
            boolean hasHalf = part.getBrokenTopHalf() != null;
            buf.writeBoolean(hasHalf);
            if (hasHalf) buf.writeBoolean(part.getBrokenTopHalf());
            // fracture locked flag
            buf.writeBoolean(part.isFractureLocked());
            // per-part buckets
            buf.writeFloat(body.getAbsorptionForPart(idf));
            buf.writeFloat(body.getBoostForPart(idf));
            // wounds/tourniquet/necrosis extra payload
            int s = 0, l = 0, necState = 0, tqTicks = 0; boolean tq = false; float necScale = 1.0f;
            try {
                s = (int) part.getClass().getMethod("getSmallWounds").invoke(part);
                l = (int) part.getClass().getMethod("getLargeWounds").invoke(part);
                tq = (boolean) part.getClass().getMethod("hasTourniquet").invoke(part);
                tqTicks = (int) part.getClass().getMethod("getTourniquetTicks").invoke(part);
                necState = (int) part.getClass().getMethod("getNecrosisState").invoke(part);
                necScale = (float) part.getClass().getMethod("getNecrosisScale").invoke(part);
            } catch (Throwable ignored) {}
            buf.writeInt(s);
            buf.writeInt(l);
            buf.writeBoolean(tq);
            buf.writeInt(tqTicks);
            buf.writeInt(necState);
            buf.writeFloat(necScale);
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
        body.prepareBucketSync();
        for (BodyPart part : body.getParts()) {
            Identifier idf = part.getIdentifier();
            buf.writeIdentifier(idf);
            buf.writeFloat(part.getHealth());
            buf.writeFloat(part.getMaxHealth());
            buf.writeBoolean(part.isBroken());
            boolean hasHalf = part.getBrokenTopHalf() != null;
            buf.writeBoolean(hasHalf);
            if (hasHalf) buf.writeBoolean(part.getBrokenTopHalf());
            buf.writeBoolean(part.isFractureLocked());
            buf.writeFloat(body.getAbsorptionForPart(idf));
            buf.writeFloat(body.getBoostForPart(idf));
            int s = 0, l = 0, necState = 0, tqTicks = 0; boolean tq = false; float necScale = 1.0f;
            try {
                s = (int) part.getClass().getMethod("getSmallWounds").invoke(part);
                l = (int) part.getClass().getMethod("getLargeWounds").invoke(part);
                tq = (boolean) part.getClass().getMethod("hasTourniquet").invoke(part);
                tqTicks = (int) part.getClass().getMethod("getTourniquetTicks").invoke(part);
                necState = (int) part.getClass().getMethod("getNecrosisState").invoke(part);
                necScale = (float) part.getClass().getMethod("getNecrosisScale").invoke(part);
            } catch (Throwable ignored) {}
            buf.writeInt(s);
            buf.writeInt(l);
            buf.writeBoolean(tq);
            buf.writeInt(tqTicks);
            buf.writeInt(necState);
            buf.writeFloat(necScale);
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

        // Guard: ignore empty or unknown items to prevent unintended healing
        if (itemStack.isEmpty()) return;

        boolean isUpgraded = itemStack.getItem() == ModItems.UPGRADED_MEDKIT_ITEM;
        boolean isPlaster = itemStack.getItem() == ModItems.PLASTER_ITEM;
        boolean isStitches = itemStack.getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.STITCHES;
        boolean isTourniquet = itemStack.getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.TOURNIQUET;
        boolean isMedkit = itemStack.getItem() == ModItems.MEDKIT_ITEM;

        if (isPlaster) {
            // Remove exactly 1 small wound on targeted limb; if no damaged parts remain, remove from random limb if any small wounds exist
            boolean removed = false;
            try {
                removed = (boolean) part.getClass().getMethod("removeSmallWound").invoke(part);
            } catch (Throwable ignored) {}
            if (!removed) {
                // If all limbs at max health, remove from a random limb that has a small wound
                boolean allMax = true;
                for (BodyPart p : body.getParts()) {
                    if (p.getHealth() < p.getMaxHealth()) { allMax = false; break; }
                }
                if (allMax) {
                    java.util.List<BodyPart> candidates = new java.util.ArrayList<>();
                    for (BodyPart p : body.getParts()) {
                        try { if ((int) p.getClass().getMethod("getSmallWounds").invoke(p) > 0) candidates.add(p); } catch (Throwable ignored) {}
                    }
                    if (!candidates.isEmpty()) {
                        BodyPart pick = candidates.get(serverPlayerEntity.getRandom().nextInt(candidates.size()));
                        try { removed = (boolean) pick.getClass().getMethod("removeSmallWound").invoke(pick); } catch (Throwable ignored) {}
                    }
                }
            }
            if (removed) {
                // decrement
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

        if (isStitches) {
            boolean did = false;
            try {
                int lw = (int) part.getClass().getMethod("getLargeWounds").invoke(part);
                if (lw > 0) {
                    part.getClass().getMethod("removeLargeWound").invoke(part);
                    did = true;
                } else {
                    int sw = (int) part.getClass().getMethod("getSmallWounds").invoke(part);
                    if (sw > 0) {
                        part.getClass().getMethod("removeSmallWound").invoke(part);
                        did = true;
                    }
                }
                if (did) {
                    // Apply 50% max health debuff for 3 minutes to the treated limb
                    part.getClass().getMethod("beginStitchDebuff").invoke(part);
                }
            } catch (Throwable ignored) {}
            if (did) {
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

        if (isTourniquet) {
            // Toggle tourniquet on allowed parts; leg <-> foot mirrored both ways
            var pid = part.getIdentifier();
            boolean isLeftArm = pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_ARM);
            boolean isRightArm = pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_ARM);
            boolean isLeftLeg = pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG);
            boolean isRightLeg = pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG);
            boolean isLeftFoot = pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT);
            boolean isRightFoot = pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT);
            boolean isHead = pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD);
            boolean allowed = isLeftArm || isRightArm || isLeftLeg || isRightLeg || isLeftFoot || isRightFoot || isHead;
            if (!allowed) return;
            boolean newState = false;
            try {
                boolean has = (boolean) part.getClass().getMethod("hasTourniquet").invoke(part);
                newState = !has; // apply when no tourniquet, remove when present
                part.getClass().getMethod("setTourniquet", boolean.class).invoke(part, newState);
                // Mirror leg<->foot on same side
                if (isLeftLeg || isLeftFoot) {
                    BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG);
                    BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT);
                    if (leg != null && leg != part) leg.getClass().getMethod("setTourniquet", boolean.class).invoke(leg, newState);
                    if (foot != null && foot != part) foot.getClass().getMethod("setTourniquet", boolean.class).invoke(foot, newState);
                }
                if (isRightLeg || isRightFoot) {
                    BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG);
                    BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT);
                    if (leg != null && leg != part) leg.getClass().getMethod("setTourniquet", boolean.class).invoke(leg, newState);
                    if (foot != null && foot != part) foot.getClass().getMethod("setTourniquet", boolean.class).invoke(foot, newState);
                }
            } catch (Throwable ignored) {}
            if (newState) {
                // Applying tourniquet consumes one
                if (serverPlayerEntity.getInventory().getMainHandStack().getItem() == itemStack.getItem()){
                    serverPlayerEntity.getInventory().getMainHandStack().decrement(1);
                }else{
                    int slot = serverPlayerEntity.getInventory().getSlotWithStack(itemStack);
                    if (slot >= 0) serverPlayerEntity.getInventory().getStack(slot).decrement(1);
                }
            } else {
                // Removing tourniquet returns the item
                serverPlayerEntity.giveItemStack(new ItemStack(xyz.srgnis.bodyhealthsystem.registry.ModItems.TOURNIQUET));
            }
            syncBody((PlayerEntity) entity);
            return;
        }

        if (isUpgraded) {
            boolean didSomething = false;
            // Fix bone if broken
            if (part.isBroken()) {
                part.setBroken(false);
                part.setBrokenTopHalf(null);
                part.setFractureLocked(false);
                part.setHealth(Math.max(1.0f, part.getHealth()));
                body.onBoneTreatmentApplied();
                didSomething = true;
            }
            // Heal some HP (consider boosted cap as baseline)
            float effMax = part.getMaxHealth() + Math.max(0.0f, body.getBoostForPart(partID));
            if (part.getHealth() < effMax) {
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
        if (isMedkit && part.isDamaged()) {
            // Do not allow healing if fracture has locked (requires upgraded medkit)
            if (part.isFractureLocked()) {
                return;
            }
            body.healPart(4, partID);
            // decrement from inventory (inline to avoid missing method)
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
