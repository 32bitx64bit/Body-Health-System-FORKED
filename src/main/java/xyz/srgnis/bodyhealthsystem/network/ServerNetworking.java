package xyz.srgnis.bodyhealthsystem.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.registry.ModItems;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

/**
 * Server-side network handler for Body Health System.
 * Handles all incoming packets from clients and broadcasts body state updates.
 */
public class ServerNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(BHSMain.MOD_ID + "/ServerNetworking");

    public static void initialize(){
        // Login-time config handshake: send required temperature setting and verify client supports it
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            PacketByteBuf cfg = PacketByteBufs.create();
            cfg.writeBoolean(Config.enableTemperatureSystem);
            cfg.writeBoolean(Config.enableBoneSystem);
            cfg.writeBoolean(Config.enableWoundingSystem);
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
            boolean clientTemp = false;
            try {
                if (buf.isReadable()) clientTemp = buf.readBoolean();
            } catch (Exception e) {
                LOGGER.debug("Failed to read client temp config: {}", e.getMessage());
            }
            // Consume optional client booleans for bones/wounds (server doesn't enforce)
            try { if (buf.isReadable()) buf.readBoolean(); } catch (Exception ignored) {}
            try { if (buf.isReadable()) buf.readBoolean(); } catch (Exception ignored) {}
            if (Config.enableTemperatureSystem && !clientTemp) {
                handler.disconnect(Text.literal("This server requires the temperature system to be enabled in Body Health System config."));
            }
            // We do not enforce bone/wound settings; server is authoritative and client UI will mirror
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
                try {
                    tempC = gavinx.temperatureapi.BodyTemperatureState.getC(spe);
                } catch (Exception e) {
                    LOGGER.debug("Failed to get body temperature: {}", e.getMessage());
                }
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
                // Block removal if player is currently using stitches
                if (player.isUsingItem() && player.getActiveItem() != null && player.getActiveItem().getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.STITCHES) {
                    return;
                }
                Entity e = player.getWorld().getEntityById(entityId);
                if (!(e instanceof BodyProvider)) return;
                var body = ((BodyProvider) e).getBody();
                BodyPart part = body.getPart(partId);
                if (part == null) return;
                if (!part.hasTourniquet()) return;
                // Toggle off this part
                part.setTourniquet(false);
                // Mirror if leg/foot
                var pid = part.getIdentifier();
                if (pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG) || pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT)){
                    BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG);
                    BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT);
                    if (leg != null) leg.setTourniquet(false);
                    if (foot != null) foot.setTourniquet(false);
                }
                if (pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG) || pid.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT)){
                    BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG);
                    BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT);
                    if (leg != null) leg.setTourniquet(false);
                    if (foot != null) foot.setTourniquet(false);
                }
                // Refund one Tourniquet item only if not using stitches item as a fallback
                if (!(player.isUsingItem() && player.getActiveItem() != null && player.getActiveItem().getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.STITCHES)) {
                    player.giveItemStack(new net.minecraft.item.ItemStack(xyz.srgnis.bodyhealthsystem.registry.ModItems.TOURNIQUET));
                }
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
        Entity entity = serverPlayNetworkHandler.player.getWorld().getEntityById(packetByteBuf.readInt());
        if (entity == null) {
            LOGGER.debug("Entity not found for sync request");
            return;
        }
        syncBody(entity, serverPlayNetworkHandler.player);
    }

    private static void syncBody(Entity entity, ServerPlayerEntity player) {
        // Validate entity is a valid target
        if (!(entity instanceof BodyProvider)) {
            LOGGER.debug("Entity {} is not a BodyProvider", entity.getId());
            return;
        }
        
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entity.getId());

        var body = ((BodyProvider)entity).getBody();
        if (body == null) {
            LOGGER.warn("Body is null for entity {}", entity.getId());
            return;
        }
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
            int s = part.getSmallWounds();
            int l = part.getLargeWounds();
            boolean tq = part.hasTourniquet();
            int tqTicks = part.getTourniquetTicks();
            int necState = part.getNecrosisState();
            float necScale = part.getNecrosisScale();
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
        if (!(self instanceof BodyProvider)) {
            LOGGER.warn("Player {} is not a BodyProvider", self.getName().getString());
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        var body = ((BodyProvider) self).getBody();
        if (body == null) {
            LOGGER.warn("Body is null for player {}", self.getName().getString());
            return;
        }
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
            int s = part.getSmallWounds();
            int l = part.getLargeWounds();
            boolean tq = part.hasTourniquet();
            int tqTicks = part.getTourniquetTicks();
            int necState = part.getNecrosisState();
            float necScale = part.getNecrosisScale();
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

    /**
     * Handle use of healing items from client.
     * Validates all inputs before processing to prevent exploits.
     */
    private static void handleUseHealingItem(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity, ServerPlayNetworkHandler serverPlayNetworkHandler, PacketByteBuf packetByteBuf, PacketSender packetSender) {
        // Read and validate entity ID
        int entityId = packetByteBuf.readInt();
        Entity entity = serverPlayerEntity.getWorld().getEntityById(entityId);
        if (entity == null) {
            LOGGER.debug("Entity {} not found for healing item use", entityId);
            return;
        }
        
        // Validate entity is a BodyProvider
        if (!(entity instanceof BodyProvider)) {
            LOGGER.debug("Entity {} is not a BodyProvider", entityId);
            return;
        }
        
        var body = ((BodyProvider) entity).getBody();
        if (body == null) {
            LOGGER.warn("Body is null for entity {}", entityId);
            return;
        }
        
        // Read and validate part ID
        Identifier partID = packetByteBuf.readIdentifier();
        BodyPart part = body.getPart(partID);
        if (part == null) {
            LOGGER.debug("Part {} not found on entity {}", partID, entityId);
            return;
        }
        
        // Read and validate item stack
        ItemStack itemStack = packetByteBuf.readItemStack();
        if (itemStack.isEmpty()) {
            LOGGER.debug("Empty item stack in healing request");
            return;
        }

        // Guard: ignore empty or unknown items to prevent unintended healing
        if (itemStack.isEmpty()) return;

        boolean isUpgraded = itemStack.getItem() == ModItems.UPGRADED_MEDKIT_ITEM;
        boolean isPlaster = itemStack.getItem() == ModItems.PLASTER_ITEM;
        boolean isStitches = itemStack.getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.STITCHES;
        boolean isTourniquet = itemStack.getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.TOURNIQUET;
        boolean isMedkit = itemStack.getItem() == ModItems.MEDKIT_ITEM;
        boolean isPrimitiveMedkit = itemStack.getItem() == ModItems.PRIMITIVE_MEDKIT_ITEM;

        if (isPlaster) {
            // Remove exactly 1 small wound on targeted limb; if no damaged parts remain, remove from random limb if any small wounds exist
            boolean removed = part.removeSmallWound();
            if (!removed) {
                // If all limbs at max health, remove from a random limb that has a small wound
                boolean allMax = true;
                for (BodyPart p : body.getParts()) {
                    if (p.getHealth() < p.getMaxHealth()) { allMax = false; break; }
                }
                if (allMax) {
                    java.util.List<BodyPart> candidates = new java.util.ArrayList<>();
                    for (BodyPart p : body.getParts()) {
                        if (p.getSmallWounds() > 0) candidates.add(p);
                    }
                    if (!candidates.isEmpty()) {
                        BodyPart pick = candidates.get(serverPlayerEntity.getRandom().nextInt(candidates.size()));
                        removed = pick.removeSmallWound();
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
            if (part.getLargeWounds() > 0) {
                part.removeLargeWound();
                did = true;
            } else if (part.getSmallWounds() > 0) {
                part.removeSmallWound();
                did = true;
            }
            if (did) {
                // Apply 50% max health debuff for 3 minutes to the treated limb
                part.beginStitchDebuff();
            }
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
            boolean newState = !part.hasTourniquet(); // apply when no tourniquet, remove when present
            part.setTourniquet(newState);
            // Mirror leg<->foot on same side
            if (isLeftLeg || isLeftFoot) {
                BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG);
                BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT);
                if (leg != null && leg != part) leg.setTourniquet(newState);
                if (foot != null && foot != part) foot.setTourniquet(newState);
            }
            if (isRightLeg || isRightFoot) {
                BodyPart leg = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG);
                BodyPart foot = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT);
                if (leg != null && leg != part) leg.setTourniquet(newState);
                if (foot != null && foot != part) foot.setTourniquet(newState);
            }
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

        if (isPrimitiveMedkit) {
            boolean didSomething = false;

            // Heal selected part a bit (up to boosted effective cap)
            float effMax = part.getMaxHealth() + Math.max(0.0f, body.getBoostForPart(partID));
            if (part.getHealth() < effMax) {
                body.healPart(1.5f, part);
                didSomething = true;
            }

            // Apply poultice status effect to target (refresh duration; no stacking)
            if (entity instanceof net.minecraft.entity.LivingEntity le) {
                le.addStatusEffect(new StatusEffectInstance(ModStatusEffects.HERBAL_POULTICE_EFFECT, 2 * 60 * 20, 0, false, true, true));
                didSomething = true;
            }

            if (didSomething) {
                if (serverPlayerEntity.getInventory().getMainHandStack().getItem() == itemStack.getItem()){
                    serverPlayerEntity.getInventory().getMainHandStack().decrement(1);
                }else{
                    int slot = serverPlayerEntity.getInventory().getSlotWithStack(itemStack);
                    if (slot >= 0) serverPlayerEntity.getInventory().getStack(slot).decrement(1);
                }
                if (entity instanceof PlayerEntity pe) syncBody(pe);
            }
            return;
        }

        if (isUpgraded) {
            boolean didSomething = false;

            boolean hadBroken = part.isBroken();
            boolean hadLargeWound = part.getLargeWounds() > 0;
            boolean hadSmallWound = part.getSmallWounds() > 0;
            boolean hadAnyWound = hadLargeWound || hadSmallWound;

            boolean healedBroken = false;
            boolean healedWound = false;

            // Fix bone if broken
            if (hadBroken) {
                part.setBroken(false);
                part.setBrokenTopHalf(null);
                part.setFractureLocked(false);
                part.setHealth(Math.max(1.0f, part.getHealth()));
                body.onBoneTreatmentApplied();
                healedBroken = true;
                didSomething = true;
            }

            // Fix wounds on selected part (prefer large)
            if (hadLargeWound) {
                if (part.removeLargeWound()) {
                    healedWound = true;
                    didSomething = true;
                }
            } else if (hadSmallWound) {
                if (part.removeSmallWound()) {
                    healedWound = true;
                    didSomething = true;
                }
            }

            // HP healing rules:
            // - No injuries -> heal to max
            // - If both a broken bone AND wound were present -> heal both but provide 0 HP
            // - Broken bone only -> +2 HP
            // - Small wound healed -> +3 HP
            // - Large wound healed -> +1.5 HP
            float effMax = part.getMaxHealth() + Math.max(0.0f, body.getBoostForPart(partID));
            if (part.getHealth() < effMax) {
                float hp = 0.0f;
                if (hadBroken && hadAnyWound) {
                    hp = 0.0f;
                } else if (hadBroken) {
                    hp = 2.0f;
                } else if (hadLargeWound) {
                    hp = 1.5f;
                } else if (hadSmallWound) {
                    hp = 3.0f;
                } else {
                    // no wound/bone: fully heal this part
                    part.setHealth(effMax);
                    didSomething = true;
                }

                if (hp > 0.0f) {
                    body.healPart(hp, part);
                    didSomething = true;
                }
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

        // Medkit behaviour:
        // - Heals small wounds
        // - Downgrades large wounds -> small
        // - If it treated a wound this use, HP heal is only 2 (instead of 4)
        if (isMedkit) {
            boolean didSomething = false;
            boolean treatedWound = false;

            // Wound treatment on selected part
            if (part.getLargeWounds() > 0) {
                if (part.removeLargeWound()) {
                    part.addSmallWound();
                    treatedWound = true;
                    didSomething = true;
                }
            } else if (part.getSmallWounds() > 0) {
                if (part.removeSmallWound()) {
                    treatedWound = true;
                    didSomething = true;
                }
            }

            // HP heal (do not heal bones)
            // If fracture is locked, block the HP heal portion (requires upgraded medkit)
            if (!part.isFractureLocked()) {
                float effMax = part.getMaxHealth() + Math.max(0.0f, body.getBoostForPart(partID));
                if (part.getHealth() < effMax) {
                    float hp = treatedWound ? 2.0f : 4.0f;
                    body.healPart(hp, part);
                    didSomething = true;
                }
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
