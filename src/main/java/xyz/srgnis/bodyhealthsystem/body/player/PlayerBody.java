package xyz.srgnis.bodyhealthsystem.body.player;

import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.Vec3d;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.BodySide;
import xyz.srgnis.bodyhealthsystem.body.player.parts.*;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.mixin.ModifyAppliedDamageInvoker;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;
import xyz.srgnis.bodyhealthsystem.util.ProjectileHitTracker;
import xyz.srgnis.bodyhealthsystem.util.Utils;

import static xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.*;

public class PlayerBody extends Body {

    public PlayerBody(PlayerEntity player) {
        this.entity = player;
    }
    
    public void initParts(){
        PlayerEntity player = ((PlayerEntity) entity);
        this.addPart(HEAD, new HeadBodyPart(player));
        this.addPart(TORSO, new TorsoBodyPart(player));
        this.addPart(LEFT_ARM, new ArmBodyPart(BodySide.LEFT,player));
        this.addPart(RIGHT_ARM, new ArmBodyPart(BodySide.RIGHT,player));
        this.addPart(LEFT_FOOT, new FootBodyPart(BodySide.LEFT,player));
        this.addPart(RIGHT_FOOT, new FootBodyPart(BodySide.RIGHT,player));
        this.addPart(LEFT_LEG, new LegBodyPart(BodySide.LEFT,player));
        this.addPart(RIGHT_LEG, new LegBodyPart(BodySide.RIGHT,player));
        this.noCriticalParts.putAll(this.parts);
    }
    //TODO: kill command don't kill;
    @Override
    public void applyDamageBySource(float amount, DamageSource source){
        if(source==null){
            super.applyDamageBySource(amount,source);
            return;
        }
        //TODO: handle more damage sources
        //TODO: starvation overpowered?
        if (source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.HOT_FLOOR)) {
            applyFallDamage(amount, source);
        } else if (source.isOf(DamageTypes.LIGHTNING_BOLT) || source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.FIREBALL) || source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION)) {
            applyDamageFullRandom(amount, source);
        } else if (source.isOf(DamageTypes.STARVE)) {
            applyDamageLocal(amount, source, this.getPart(TORSO));
        } else if (source.isOf(DamageTypes.DROWN)) {
            applyDamageLocal(Config.drowningDamage, source, this.getPart(TORSO));
        } else if (source.isOf(DamageTypes.FLY_INTO_WALL) || source.isOf(DamageTypes.FALLING_ANVIL) || source.isOf(DamageTypes.FALLING_BLOCK) || source.isOf(DamageTypes.FALLING_STALACTITE)) {
            applyDamageLocal(amount, source, this.getPart(HEAD));
        } else if (source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.MOB_PROJECTILE) || source.isOf(DamageTypes.TRIDENT) || source.getSource() instanceof net.minecraft.entity.projectile.PersistentProjectileEntity) {
            // Route projectile damage to the part indicated by the most recent hit
            Vec3d norm = ProjectileHitTracker.getLastHit((PlayerEntity) entity);
            BodyPart part = selectPartFromNormalized(norm);
            if (part != null) {
                applyDamageLocal(amount, source, part);
                applyWoundChances(part, true);
            } else {
                BodyPart p = getNoCriticalParts().get(entity.getRandom().nextInt(getNoCriticalParts().size()));
                applyDamageLocal(amount, source, p);
                applyWoundChances(p, true);
            }
            // Clear after consumption to avoid stale data
            ProjectileHitTracker.clear((PlayerEntity) entity);
        } else {
            BodyPart p = getNoCriticalParts().get(entity.getRandom().nextInt(getNoCriticalParts().size()));
            applyDamageLocal(amount, source, p);
            applyWoundChances(p, false);
        }

    }

    //Progressive application of the damage from foot to torso
    public void applyFallDamage(float amount, DamageSource source){
        amount = amount/2;
        float remaining;
        remaining = takeDamage(amount, source, this.getPart(RIGHT_FOOT));
        if(remaining > 0){remaining = takeDamage(remaining, source, this.getPart(RIGHT_LEG));}
        if(remaining > 0){takeDamage(remaining, source, this.getPart(TORSO));}

        remaining = takeDamage(amount, source, this.getPart(LEFT_FOOT));
        if(remaining > 0){remaining = takeDamage(remaining, source, this.getPart(LEFT_LEG));}
        if(remaining > 0){takeDamage(remaining, source, this.getPart(TORSO));}
    }

    //TODO: blindness on head critical?
    public void applyCriticalPartsEffect(){
        // Deprecated: Replaced by bone-based negative effects in Body.applyBrokenBonesEffects()
    }

    public boolean isCrawlingRequired() {
        BodyPart leftLeg = getPart(LEFT_LEG);
        BodyPart rightLeg = getPart(RIGHT_LEG);
        BodyPart leftFoot = getPart(LEFT_FOOT);
        BodyPart rightFoot = getPart(RIGHT_FOOT);
        boolean bothLegsBroken = leftLeg != null && rightLeg != null && leftLeg.isBroken() && rightLeg.isBroken();
        boolean bothFeetBroken = leftFoot != null && rightFoot != null && leftFoot.isBroken() && rightFoot.isBroken();
        // Require crawling only if both legs AND both feet are broken (use bone break state, not HP)
        return bothLegsBroken && bothFeetBroken;
    }

    @Override
    public float takeDamage(float amount, DamageSource source, BodyPart part){

        PlayerEntity player = (PlayerEntity)entity;
        //applyArmor
        amount = applyArmorToDamage(source, amount, part);
        float f = amount = ((ModifyAppliedDamageInvoker)entity).invokeModifyAppliedDamage(source, amount);

        // Distributed absorption: allocate from the bucket of the hit part
        ensureAbsorptionBucketsUpToDate();
        float currentAbs = entity.getAbsorptionAmount();
        float bucket = getAbsorptionBucket(part);
        float consumed = Math.min(amount, bucket);
        if (consumed > 0.0f) {
            consumeAbsorptionFromBucket(part, consumed);
            entity.setAbsorptionAmount(Math.max(0.0f, currentAbs - consumed));
            // Player stat for absorbed damage (self)
            player.increaseStat(Stats.DAMAGE_ABSORBED, Math.round(consumed * 10.0f));
            amount -= consumed;
        }
        // Health Boost is NOT a consumable shield. It increases max health but should not reduce damage here.
        if (amount == 0.0f) {
            return 0.0f;
        }

        player.addExhaustion(source.getExhaustion());
        player.getDamageTracker().onDamage(source, amount);
        if (amount < 3.4028235E37f) {
            player.increaseStat(Stats.DAMAGE_TAKEN, Math.round(amount * 10.0f));
        }

        float previousHealth = part.getHealth();
        float remaining = (source.isOf(DamageTypes.MAGIC) && entity.hasStatusEffect(StatusEffects.POISON))
                ? part.damageWithoutKill(amount)
                : part.damage(amount);

        // Evaluate bone break state (skull exempt). Delegate to base for consistency.
        if (!part.getIdentifier().equals(HEAD) && !suppressBoneBreakEvaluation) {
            // We call the base helper by mimicking Body.takeDamage flow where it runs evaluateBoneBreak
            // Here we just reproduce the call and event to avoid duplication issues.
            float max = part.getMaxHealth();
            float newHealth = part.getHealth();
            if (newHealth <= 0.0f) {
                boolean wasBroken = part.isBroken();
                part.setBroken(true);
                if (part.getIdentifier().equals(LEFT_ARM) || part.getIdentifier().equals(RIGHT_ARM)) {
                    if (part.getBrokenTopHalf() == null) part.setBrokenTopHalf(entity.getRandom().nextBoolean());
                }
                if (!wasBroken) this.onBoneBrokenEvent(part);
            } else {
                float healthRatio = previousHealth / max;
                float baseChance = 0.0f;
                if (healthRatio < 1.0f) {
                    if (healthRatio >= 0.5f) {
                        baseChance = (1.0f - healthRatio) * (0.3f / 0.5f);
                    } else {
                        baseChance = 0.3f + (0.5f - healthRatio) * (0.7f / 0.5f);
                    }
                }
                float damageRatio = Math.min(amount / max, 1.0f);
                float bonus = 0.15f * damageRatio;
                float chance = Math.min(baseChance + bonus, 1.0f);
                if (entity.getRandom().nextFloat() < chance) {
                    boolean wasBroken = part.isBroken();
                    part.setBroken(true);
                    if (part.getIdentifier().equals(LEFT_ARM) || part.getIdentifier().equals(RIGHT_ARM)) {
                        if (part.getBrokenTopHalf() == null) part.setBrokenTopHalf(entity.getRandom().nextBoolean());
                    }
                    if (!wasBroken) this.onBoneBrokenEvent(part);
                }
            }
        }
        return remaining;
    }

    public float applyArmorToDamage(DamageSource source, float amount, BodyPart part){
        if(part.getAffectedArmor().getItem() instanceof ArmorItem) {
            if (!source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
                PlayerEntity player = (PlayerEntity)entity;
                ArmorItem armorItem = ((ArmorItem) part.getAffectedArmor().getItem());
                player.getInventory().damageArmor(source,amount,new int[]{part.getArmorSlot()});
                amount = DamageUtil.getDamageLeft(amount, Utils.modifyProtection(armorItem, part.getArmorSlot()), Utils.modifyToughness(armorItem,part.getArmorSlot()));
            }
        }
        return amount;
    }

    // --- Wounds ---
    private void applyWoundChances(BodyPart part, boolean projectile) {
        if (suppressWoundEvaluation || part == null) return;
        double multiplier = 1.0;
        var id = part.getIdentifier();
        if (id.equals(LEFT_FOOT) || id.equals(RIGHT_FOOT)) multiplier = 0.75;
        float hp = Math.max(0.0f, part.getHealth());
        float nearDeadHP = 1.0f;
        float max = Math.max(nearDeadHP, part.getMaxHealth());
        float t = 1.0f - Math.min(1.0f, (hp - nearDeadHP) / Math.max(0.0001f, (max - nearDeadHP)));
        double smallBase = 0.35 + (0.80 - 0.35) * t;
        double largeBase = 0.15 + (0.65 - 0.15) * t;
        smallBase *= multiplier;
        largeBase *= multiplier;
        var rnd = entity.getRandom();
        if (part.hasWoundCapacity() && rnd.nextDouble() < largeBase) {
            part.addLargeWound();
        }
        if (part.hasWoundCapacity() && rnd.nextDouble() < smallBase) {
            part.addSmallWound();
        }
    }

    private BodyPart selectPartFromNormalized(Vec3d norm) {
        if (norm == null) return null;
        double x = norm.x; 
        double y = norm.y; 

        if (y < 0.18) {
            // Foot
            return this.getPart(x >= 0 ? LEFT_FOOT : RIGHT_FOOT);
        } else if (y < 0.50) {
            // Leg
            return this.getPart(x >= 0 ? LEFT_LEG : RIGHT_LEG);
        } else if (y < 0.88) {
            // Torso band. If very lateral in the upper torso, count it as an arm.
            if (y >= 0.60 && Math.abs(x) > 0.80) {
                return this.getPart(x >= 0 ? LEFT_ARM : RIGHT_ARM);
            }
            return this.getPart(TORSO);
        } else {
            // Head
            return this.getPart(HEAD);
        }
    }
}
