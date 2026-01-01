package xyz.srgnis.bodyhealthsystem.body;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.world.event.GameEvent;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.mixin.ModifyAppliedDamageInvoker;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;
import xyz.srgnis.bodyhealthsystem.util.Utils;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public abstract class Body {
    protected final HashMap<Identifier, BodyPart> parts = new HashMap<>();
    protected HashMap<Identifier, BodyPart> noCriticalParts = new HashMap<>();
    // Per-part absorption buckets to support "extra hearts" distribution
    protected final HashMap<Identifier, Float> absorptionBuckets = new HashMap<>();
    // Per-part health-boost buckets to support direct max-health increases distribution
    protected final HashMap<Identifier, Float> boostBuckets = new HashMap<>();
    protected LivingEntity entity;

    private final BodyBuckets buckets = new BodyBuckets(this);

    // When true, skip bone-break evaluation for the current damage application
    protected boolean suppressBoneBreakEvaluation = false;
    // When true, skip wound evaluation (used for bleed/necrosis damage applications)
    protected boolean suppressWoundEvaluation = false;


    // Bone system timers (server authoritative, not persisted)
    protected int boneGraceTicksRemaining = 0; // counts down after a break
    protected boolean bonePenaltyActive = false; // once grace hits 0, periodic health loss active
    protected int bonePenaltyTickCounter = 0; // counts to 10s windows

    // Downed / revival state (server authoritative, not persisted)
    protected boolean downed = false;
    protected int bleedOutTicksRemaining = 0;
    protected boolean beingRevived = false;
    protected java.util.UUID reviverUuid = null;
    protected boolean pendingDeath = false; // when true, a vanilla death is being forced

    public abstract void initParts();

    public void addPart(Identifier identifier, BodyPart part){
        parts.put(identifier, part);
        // Initialize absorption/boost buckets for this part
        absorptionBuckets.put(identifier, 0.0f);
        boostBuckets.put(identifier, 0.0f);
        buckets.onPartAdded(identifier);
    }

    public BodyPart getPart(Identifier identifier){
        return parts.get(identifier);
    }

    public void removePart(Identifier identifier){
        parts.remove(identifier);
    }

    public ArrayList<BodyPart> getParts(){
        return new ArrayList<>(parts.values());
    }
    public ArrayList<Identifier> getPartsIdentifiers(){
        return new ArrayList<>(parts.keySet());
    }
    public ArrayList<BodyPart> getNoCriticalParts(){
        return new ArrayList<>(noCriticalParts.values());
    }
    public ArrayList<Identifier> getNoCriticalIdentifiers(){
        return new ArrayList<>(noCriticalParts.keySet());
    }

    public void writeToNbt (NbtCompound nbt){
        NbtCompound new_nbt = new NbtCompound();
        for(BodyPart part : getParts()){
            part.writeToNbt(new_nbt);
        }
        // Persist downed-related state so behavior continues after relog
        new_nbt.putBoolean("downed", this.downed);
        new_nbt.putInt("bleedOutTicksRemaining", this.bleedOutTicksRemaining);
        nbt.put(BHSMain.MOD_ID, new_nbt);
    }

    //TODO: Is this the best way of handling not found parts?
    public void readFromNbt (NbtCompound nbt) {
        NbtCompound bodyNbt = nbt.getCompound(BHSMain.MOD_ID);
        if (!bodyNbt.isEmpty()) {
            noCriticalParts.clear();
            for (Identifier partId : getPartsIdentifiers()) {
                if(!bodyNbt.getCompound(partId.toString()).isEmpty()) {
                    BodyPart part = getPart(partId);
                    part.readFromNbt(bodyNbt.getCompound(partId.toString()));
                    if(part.getHealth()>0){
                        noCriticalParts.put(part.getIdentifier(), part);
                    }
                }
            }
            // Restore downed-related state
            if (bodyNbt.contains("downed")) {
                this.downed = bodyNbt.getBoolean("downed");
            }
            if (bodyNbt.contains("bleedOutTicksRemaining")) {
                this.bleedOutTicksRemaining = bodyNbt.getInt("bleedOutTicksRemaining");
            } else if (this.downed && this.bleedOutTicksRemaining <= 0) {
                // Fallback to a default bleedout timer if missing
                this.bleedOutTicksRemaining = isTorsoBroken() ? (40 * 20) : (80 * 20);
            }
        }
    }

    @Override
    public String toString(){
        StringBuilder s = new StringBuilder("Body of " + entity.getName().getString() + "\n");
        for (BodyPart p : getParts()) {
            s.append(p.toString());
        }
        return s.toString();
    }

    public void healAll(){
        for(BodyPart part : getParts()){
            part.heal();
        }
    }

    public void heal(float amount){
        if(amount > 0) {
            ArrayList<BodyPart> parts_l = getParts();
            Collections.shuffle(parts_l);
            for (BodyPart part : parts_l) {
                if (amount <= 0) {
                    break;
                }
                amount = part.heal(amount);
            }
        }
    }
    public void healPart(int amount, Identifier partID) {
        healPart(amount, getPart(partID));
    }
    public void healPart(float amount, BodyPart part){
        part.heal(amount);
    }

    public void applyDamageBySource(float amount, DamageSource source){
        //Here we se the default way
        applyDamageLocalRandom(amount, source);
    }

    //Applies the damage to a single part
    public void applyDamageLocal(float amount, DamageSource source, BodyPart part){
        takeDamage(amount, source, part);
    }

    //Applies the damage to a random part
    public void applyDamageLocalRandom(float amount, DamageSource source){
        takeDamage(amount, source, getNoCriticalParts().get(entity.getRandom().nextInt(noCriticalParts.size())) );
    }

    //Splits the damage into all parts
    public void applyDamageGeneral(float amount, DamageSource source){ applyDamageList(amount, source, getParts()); }

    //Randomly splits the damage into all parts
    public void applyDamageGeneralRandom(float amount, DamageSource source){ applyDamageListRandom(amount, source, getParts()); }

    //Splits the damage into list of parts
    public void applyDamageList(float amount, DamageSource source, List<BodyPart> parts){
        float split_amount = amount/parts.size();

        for(BodyPart bodyPart : parts){
            takeDamage(split_amount, source, bodyPart);
        }
    }

    //Randomly splits the damage into list of parts
    public void applyDamageListRandom(float amount, DamageSource source, List<BodyPart> parts){
        List<Float> damages = Utils.n_random(amount, parts.size());

        int i = 0;
        for(BodyPart bodyPart : parts){
            takeDamage(damages.get(i), source, bodyPart);
            i++;
        }
    }

    //Splits the damage into a random list of parts
    public void applyDamageRandomList(float amount, DamageSource source){
        List<BodyPart> randomlist = Utils.random_sublist(getNoCriticalParts(), entity.getRandom().nextInt(getNoCriticalParts().size() + 1));
        applyDamageList(amount, source, randomlist);
    }

    //Randomly splits the damage into a random list of parts
    public void applyDamageFullRandom(float amount, DamageSource source){
        List<BodyPart> randomlist = Utils.random_sublist(getNoCriticalParts(), entity.getRandom().nextInt(getNoCriticalParts().size() + 1));
        applyDamageListRandom(amount, source, randomlist);
    }

    public float takeDamage(float amount, DamageSource source, BodyPart part){

        amount = applyArmorToDamage(source, amount, part);
        float f = amount = ((ModifyAppliedDamageInvoker)entity).invokeModifyAppliedDamage(source, amount);

        // Distributed absorption: allocate from the bucket of the hit part
        ensureAbsorptionBucketsUpToDate();
        float currentAbs = entity.getAbsorptionAmount();
        float bucket = getAbsorptionBucket(part);
        float consumed = Math.min(amount, bucket);
        if (consumed > 0.0f) {
            consumeAbsorptionFromBucket(part, consumed);
            // Reduce entity absorption by the consumed amount and award attacker stat
            entity.setAbsorptionAmount(Math.max(0.0f, currentAbs - consumed));
            if (source.getAttacker() instanceof ServerPlayerEntity spe) {
                spe.increaseStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(consumed * 10.0f));
            }
            if (entity instanceof net.minecraft.entity.player.PlayerEntity selfPlayer) {
                selfPlayer.increaseStat(Stats.DAMAGE_ABSORBED, Math.round(consumed * 10.0f));
            }
            amount -= consumed;
        }
        // Health Boost is NOT a consumable shield. It increases max health but should not reduce damage here.
        if (amount == 0.0f) {
            return 0.0f;
        }

        float previousHealth = part.getHealth();
        float remaining;
        //TODO: This could mistake other magic damage as poison, is a better way of doing this?
        if(source.isOf(DamageTypes.MAGIC) && entity.getStatusEffect(StatusEffects.POISON) != null) {
            remaining = part.damageWithoutKill(amount);
        }else{
            remaining = part.damage(amount);
        }

        // After damage is applied, evaluate bone break chance (except skull)
        if (!part.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD)
                && !suppressBoneBreakEvaluation
                && !suppressWoundEvaluation
                && xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem) {
            evaluateBoneBreak(part, previousHealth, amount);
        }

        if (entity instanceof net.minecraft.entity.player.PlayerEntity player) {
            player.addExhaustion(source.getExhaustion());
            if (amount < 3.4028235E37f) {
                player.increaseStat(Stats.DAMAGE_TAKEN, Math.round(amount * 10.0f));
            }
        }

        entity.getDamageTracker().onDamage(source, amount);
        entity.emitGameEvent(GameEvent.ENTITY_DAMAGE);

        return remaining;
    }

    private void evaluateBoneBreak(BodyPart part, float previousHealth, float rawDamage) {
        float max = part.getMaxHealth();
        float newHealth = part.getHealth();

        // If this hit would reduce the part to 0, always break
        if (newHealth <= 0.0f) {
            part.setBroken(true);
            if (isArm(part)) assignArmBrokenHalf(part);
            return;
        }

        // Health ratio BEFORE the hit
        float healthRatio = previousHealth / max; // 0..1

        // Exponential scaling: low chance above 60% HP; rapidly rising near 0
        // Target: ~15% at 60% HP, up to 50% cap near 0% HP
        float baseChance;
        if (healthRatio >= 0.6f) {
            baseChance = 0.0f;
        } else {
            float t = (0.6f - healthRatio) / 0.6f; // 0..1 as health goes from 60% to 0%
            baseChance = 0.15f * (float)Math.pow(t, 2.0); // quadratic ramp up to ~0.15 near 0%
        }
        // Damage bonus: modest increase up to +0.10 when taking heavy hits
        float damageRatio = Math.min(rawDamage / max, 1.0f);
        float bonus = 0.10f * damageRatio; // 0..0.10
        float chance = Math.min(0.50f, baseChance + bonus);

        if (entity.getRandom().nextFloat() < chance) {
            part.setBroken(true);
            if (isArm(part)) assignArmBrokenHalf(part);
        }
    }

    private boolean isArm(BodyPart part) {
        var id = part.getIdentifier();
        return id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_ARM)
                || id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_ARM);
    }

    private void assignArmBrokenHalf(BodyPart part) {
        if (part.getBrokenTopHalf() == null) {
            part.setBrokenTopHalf(entity.getRandom().nextBoolean());
        }
    }

    public abstract float applyArmorToDamage(DamageSource source, float amount, BodyPart part);



    // Apply bone-based negative effects and timers. Call this each tick on server.
    public void applyBrokenBonesEffects() {
        if (!(entity instanceof net.minecraft.entity.player.PlayerEntity player)) return;
        if (entity.getWorld().isClient) return; // server-side control
        if (!xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem) return;

        // Determine impairment: either bone broken OR limb destroyed (HP <= 0) counts as impaired
        int impairedArms = 0;
        int impairedLegsFeet = 0;
        for (BodyPart p : getParts()) {
            var id = p.getIdentifier();
            if (id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD)) continue;
            boolean impaired = p.isBroken() || p.getHealth() <= 0.0f;
            if (!impaired) continue;
            if (id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_ARM)
                    || id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_ARM)) impairedArms++;
            if (id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG)
                    || id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG)
                    || id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT)
                    || id.equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT)) impairedLegsFeet++;
        }
        boolean anyImpaired = (impairedArms + impairedLegsFeet) > 0;

        // If neither bones are broken nor any limb destroyed, deactivate timers and clear negatives
        if (!anyBoneBroken() && !anyImpaired) {
            bonePenaltyActive = false;
            boneGraceTicksRemaining = 0;
            bonePenaltyTickCounter = 0;
            // Clear bone-based effects
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS);
            // Clear bleeding indicator
            player.removeStatusEffect(ModStatusEffects.BLEEDING_EFFECT);
            return;
        }

        // Grace countdown before penalty starts
        if (!bonePenaltyActive) {
            if (boneGraceTicksRemaining > 0) {
                boneGraceTicksRemaining--;
            } else {
                bonePenaltyActive = true;
                bonePenaltyTickCounter = 0;
            }
        }


        // Tick per-bone timers and apply new Broken Bone status effect 15s after break
        int totalBroken = 0;
        boolean boneStateChanged = false;
        for (BodyPart p : getParts()) {
            if (p.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD)) continue;
            if (p.isBroken()) {
                p.tickBroken();
                // After ~2 minutes (2400 ticks), fully break and lock fracture (non-healable by simple items)
                if (p.getBrokenTicks() == 2400 && !p.isFractureLocked()) {
                    if (p.getHealth() > 0.0f) p.setHealth(0.0f);
                    p.setFractureLocked(true);
                    boneStateChanged = true;
                }
                totalBroken++;
            }
        }
        if (boneStateChanged) {
            // Sync new locked state to the player and watchers
            xyz.srgnis.bodyhealthsystem.network.ServerNetworking.broadcastBody(player);
        }
        // Apply Broken Bone status effect with amplifier = min(totalBroken, 3) - 1 (i.e., I..III caps at 3)
        if (totalBroken > 0) {
            int stacks = Math.min(3, totalBroken);
            // Only kick in after 15s since the first broken bone (use min brokenTicks across parts)
            int maxBrokenTicks = 0;
            for (BodyPart p : getParts()) {
                if (!p.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD) && p.isBroken()) {
                    maxBrokenTicks = Math.max(maxBrokenTicks, p.getBrokenTicks());
                }
            }
            if (maxBrokenTicks >= 300) { // 15 seconds at 20 tps
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        ModStatusEffects.BROKEN_BONE, 40, stacks - 1, false, true, true));
            } else {
                player.removeStatusEffect(ModStatusEffects.BROKEN_BONE);
            }
        } else {
            player.removeStatusEffect(ModStatusEffects.BROKEN_BONE);
        }

        // Apply vanilla debuffs based on impairment (broken or destroyed)
        // Arms impaired -> Weakness; Legs/Feet impaired -> Slowness; Any impaired -> Mining Fatigue
        int weakAmp = impairedArms >= 2 ? 1 : (impairedArms >= 1 ? 0 : -1);
        int slowAmp = impairedLegsFeet >= 2 ? 1 : (impairedLegsFeet >= 1 ? 0 : -1);
        int fatigueAmp = (impairedArms + impairedLegsFeet) >= 2 ? 0 : (impairedArms + impairedLegsFeet) >= 4 ? 1 : ((impairedArms + impairedLegsFeet) >= 1 ? 0 : -1);
        // Clamp and apply
        if (slowAmp >= 0) player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, slowAmp, false, false, true));
        else player.removeStatusEffect(StatusEffects.SLOWNESS);
        if (weakAmp >= 0) player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, weakAmp, false, false, true));
        else player.removeStatusEffect(StatusEffects.WEAKNESS);
        if (fatigueAmp >= 0) player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, fatigueAmp, false, false, true));
        else player.removeStatusEffect(StatusEffects.MINING_FATIGUE);

        // Retain BLEEDING_EFFECT for future use; do not manage/display it here anymore

        // Periodic health penalty once grace elapsed (no-op for now)
        if (bonePenaltyActive) {
            bonePenaltyTickCounter++;
            if (bonePenaltyTickCounter >= 200) {
                bonePenaltyTickCounter = 0;
            }
        }
    }

    private void applyBleedingDamageTo(net.minecraft.entity.player.PlayerEntity player, BodyPart target, float amount) {
        if (target == null || amount <= 0.0f) return;
        suppressBoneBreakEvaluation = true;
        suppressWoundEvaluation = true;
        try {
            takeDamage(amount, player.getDamageSources().generic(), target);
        } finally {
            suppressBoneBreakEvaluation = false;
            suppressWoundEvaluation = false;
        }
    }

    // Public helper to apply damage to a target body part without triggering bone breaks
    public void applyNonBreakingDamage(float amount, net.minecraft.entity.damage.DamageSource source, BodyPart target) {
        if (target == null || amount <= 0.0f) return;
        suppressBoneBreakEvaluation = true;
        suppressWoundEvaluation = true;
        try {
            takeDamage(amount, source, target);
        } finally {
            suppressBoneBreakEvaluation = false;
            suppressWoundEvaluation = false;
        }
    }

    // Apply bleeding damage with spillover rules
    // If torso is the bleeding source, prefer damaging limbs (arms/legs/feet) before torso.
    // Otherwise, apply to target first, then spill to limbs, then torso, then head last.
    public void applyBleedingWithSpill(float amount, BodyPart target) {
        if (!(entity instanceof net.minecraft.entity.player.PlayerEntity player)) return;
        if (amount <= 0.0f || target == null) return;

        BodyPart head = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD);
        BodyPart torso = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO);
        boolean targetIsTorso = (torso != null && target.getIdentifier().equals(torso.getIdentifier()));

        if (targetIsTorso) {
            // Route bleeding from torso to limbs first; only damage torso if no limbs remain or after limbs are exhausted
            java.util.List<BodyPart> limbs = new java.util.ArrayList<>();
            for (BodyPart p : getParts()) {
                if (p == torso) continue;
                if (p == head) continue;
                if (p.getHealth() <= 0.0f) continue;
                limbs.add(p);
            }
            net.minecraft.util.math.random.Random rnd = entity.getRandom();
            // Iteratively consume amount across random limbs
            while (amount > 0.0f && !limbs.isEmpty()) {
                BodyPart limb = limbs.get(rnd.nextInt(limbs.size()));
                float hpL = limb.getHealth();
                if (hpL <= 0.0f) { limbs.remove(limb); continue; }
                float apply = Math.min(amount, hpL);
                applyBleedingDamageTo(player, limb, apply);
                amount -= apply;
                if (apply >= hpL) limbs.remove(limb);
            }
            // If anything remains and torso is still alive, apply the rest to torso
            if (amount > 0.0f && torso != null && torso.getHealth() > 0.0f) {
                float apply = Math.min(amount, torso.getHealth());
                applyBleedingDamageTo(player, torso, apply);
                amount -= apply;
            }
            return;
        }

        // Default: apply to target first
        float hp = target.getHealth();
        if (hp > 0.0f) {
            float apply = Math.min(amount, hp);
            applyBleedingDamageTo(player, target, apply);
            amount -= apply;
        }
        if (amount <= 0.0f) return;
        // Choose spillover target: prefer non-head, non-torso limbs; only include torso if no other limbs available; head only as last resort
        java.util.List<BodyPart> limbCandidates = new java.util.ArrayList<>();
        java.util.List<BodyPart> torsoCandidate = new java.util.ArrayList<>();
        for (BodyPart p : getParts()) {
            if (p == target) continue;
            if (p.getHealth() <= 0.0f) continue;
            var id = p.getIdentifier();
            if (head != null && id.equals(head.getIdentifier())) continue;
            if (torso != null && id.equals(torso.getIdentifier())) {
                torsoCandidate.add(p);
            } else {
                limbCandidates.add(p);
            }
        }
        BodyPart spill = null;
        if (!limbCandidates.isEmpty()) {
            spill = limbCandidates.get(entity.getRandom().nextInt(limbCandidates.size()));
        } else if (!torsoCandidate.isEmpty()) {
            spill = torsoCandidate.get(0);
        } else if (head != null && head.getHealth() > 0.0f) {
            spill = head;
        }
        if (spill == null) return;
        applyBleedingDamageTo(player, spill, amount);
    }

    public void applyStatusEffectWithAmplifier(StatusEffect effect, int amplifier){
        if(amplifier >= 0){
            // Cap slowness at II when crawling is required (handled in PlayerBody.applyCriticalPartsEffect)
            StatusEffectInstance s = entity.getStatusEffect(effect);
            if(s == null){
                entity.addStatusEffect(new StatusEffectInstance(effect, 40, amplifier));
            }else if(s.getAmplifier() > amplifier || s.getDuration() <= 5 || s.getAmplifier() != amplifier){
                // Replace stronger/slower effects with our capped one, or refresh if near expiration
                entity.addStatusEffect(new StatusEffectInstance(effect, 40, amplifier));
            }
        }
    }

    public int getAmplifier(BodyPart part){
        if(part.getHealth() <= part.getCriticalThreshold()){
            return 1;
        }
        return 0;
    }

    public void updateHealth(){
        // Special lethal rule: if head is destroyed, die immediately (no downed state)
        BodyPart head = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD);
        BodyPart torso = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO);
        if (head != null && head.getHealth() <= 0.0f) {
            // Do NOT set health to 0 directly: that can bypass the normal vanilla death pipeline
            // (including inventory drops) because it may not be associated with a DamageSource.
            // Instead, request a vanilla death and let PlayerTickMixin apply the kill damage.
            if (!entity.getWorld().isClient) {
                pendingDeath = true;
            } else {
                entity.setHealth(0);
            }
            return;
        }

        // If torso is destroyed, enter/maintain downed state instead of dying
        if (torso != null && torso.getHealth() <= 0.0f) {
            startDowned();
            // Keep player barely alive while downed to allow revival
            entity.setHealth(1.0f);
            return;
        }

        // Normal proportional health mapping, with downed threshold fallback
        float max_effective = 0;
        float actual_health = 0;
        for (BodyPart part : this.getParts()) {
            float boost = getBoostForPart(part.getIdentifier());
            max_effective += part.getMaxHealth() + Math.max(0.0f, boost);
            actual_health += part.getHealth();
        }
        if (max_effective > 0) {
            float ratio = actual_health / max_effective;
            // If overall health is extremely low, enter downed state (head intact)
            if (ratio <= 0.01f) {
                startDowned();
                if (entity.getHealth() > 1.0f) entity.setHealth(1.0f);
                return;
            }
            entity.setHealth(entity.getMaxHealth() * ratio);
        }
    }

    public boolean shouldDie(){
        // Only the head being destroyed should cause immediate death; torso destruction leads to downed state
        BodyPart head = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD);
        return head != null && head.getHealth() <= 0.0f;
    }


    public void checkNoCritical(BodyPart part){
        if(part.getHealth() > 0) {
            noCriticalParts.putIfAbsent(part.getIdentifier(), part);
        }else{
            noCriticalParts.remove(part.getIdentifier());
        }
    }

    public void applyTotem(){
        clearDowned();
        for( BodyPart part : this.getParts()){
            if( part.getHealth() < 1.0F) part.setHealth(1.0F);
        }
        this.updateHealth();
    }

    // Called when a bone becomes broken (transition false -> true)
    public void onBoneBrokenEvent(BodyPart part) {
        // Start or accelerate the grace timer
        if (boneGraceTicksRemaining <= 0 && !bonePenaltyActive) {
            // 2 minutes at 20 tps
            boneGraceTicksRemaining = 2400;
        } else {
            // subtract 600 ticks, clamp at 0
            boneGraceTicksRemaining = Math.max(0, boneGraceTicksRemaining - 600);
        }
        if (entity instanceof net.minecraft.entity.player.PlayerEntity player && !entity.getWorld().isClient) {
            // Only cosmetic feedback on break
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.BLINDNESS, 100, 0, false, false));
        }
    }

    // Called when "treatment" is applied (e.g., splint), extends grace
    public void onBoneTreatmentApplied() {
        // Only extend while in grace (not strictly required)
        boneGraceTicksRemaining = Math.max(0, boneGraceTicksRemaining) + 320;
    }


    protected boolean isTorsoBroken() {
        BodyPart t = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO);
        return t != null && t.isBroken();
    }

    // Downed / revival helpers
    public boolean isDowned() { return downed; }
    public boolean isBeingRevived() { return beingRevived; }
    // Client/server sync helpers
    public void setDowned(boolean downed) { this.downed = downed; }
    public void setBeingRevived(boolean beingRevived) { this.beingRevived = beingRevived; }
    public int getBleedOutTicksRemaining() { return bleedOutTicksRemaining; }
    public void setBleedOutTicksRemaining(int ticks) { this.bleedOutTicksRemaining = ticks; }

    public boolean tryBeginRevive(net.minecraft.entity.player.PlayerEntity reviver) {
        if (!downed) return false;
        if (beingRevived) {
            return reviverUuid != null && reviverUuid.equals(reviver.getUuid());
        }
        beingRevived = true;
        reviverUuid = reviver.getUuid();
        return true;
    }

    public void endRevive(net.minecraft.entity.player.PlayerEntity reviver) {
        if (reviverUuid != null && reviverUuid.equals(reviver.getUuid())) {
            beingRevived = false;
            reviverUuid = null;
        }
    }

    public void startDowned() {
        if (downed) return;
        downed = true;
        // Bleed-out time: 80s normally, 40s if torso bone is broken
        bleedOutTicksRemaining = isTorsoBroken() ? (40 * 20) : (80 * 20);
    }

    public void clearDowned() {
        downed = false;
        beingRevived = false;
        reviverUuid = null;
        bleedOutTicksRemaining = 0;
    }

    public boolean isPendingDeath() { return pendingDeath; }

    public void onVanillaDeath() {
        clearDowned();
        pendingDeath = false;
    }

    // Force a give up -> immediate vanilla death pathway
    public void forceGiveUp() {
        if (entity == null || entity.getWorld().isClient) return;
        if (!downed) return;
        pendingDeath = true;
        if (entity.isAlive()) {
            entity.damage(entity.getDamageSources().outOfWorld(), 1000.0f);
        }
    }

    public void tickDowned() {
        if (!downed) return;
        if (entity.getWorld().isClient) return;
        if (pendingDeath) return;
        if (!beingRevived) {
            if (bleedOutTicksRemaining > 0) {
                bleedOutTicksRemaining--;
            }
            if (bleedOutTicksRemaining <= 0) {
                // Bleed out - ensure a proper vanilla death path that supports respawn
                pendingDeath = true;
                beingRevived = false;
            }
        }
    }

    public void applyRevival(int healPerPart, int bonesToFix) {
        if (!downed) return;
        // Heal all damaged body parts by healPerPart up to boosted effective cap
        for (BodyPart p : getParts()) {
            float boost = Math.max(0.0f, getBoostForPart(p.getIdentifier()));
            float effMax = p.getMaxHealth() + boost;
            if (p.getHealth() < effMax) {
                p.setHealth(Math.min(effMax, p.getHealth() + healPerPart));
            }
        }
        // Fix bones, preferring torso first (never head)
        fixBrokenBonesPreferTorso(bonesToFix);
        // Clear downed and update overall health
        clearDowned();
        pendingDeath = false;
        updateHealth();
    }

    public void applyRevival(float healPerPart, int bonesToFix) {
        if (!downed) return;
        // Heal all damaged body parts by healPerPart up to boosted effective cap
        for (BodyPart p : getParts()) {
            float boost = Math.max(0.0f, getBoostForPart(p.getIdentifier()));
            float effMax = p.getMaxHealth() + boost;
            if (p.getHealth() < effMax) {
                p.setHealth(Math.min(effMax, p.getHealth() + healPerPart));
            }
        }
        // Fix bones, preferring torso first (never head)
        fixBrokenBonesPreferTorso(bonesToFix);
        // Clear downed and update overall health
        clearDowned();
        pendingDeath = false;
        updateHealth();
    }

    private void fixBrokenBonesPreferTorso(int count) {
        if (count <= 0) return;
        java.util.List<BodyPart> candidates = new java.util.ArrayList<>();
        BodyPart torso = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO);
        // If torso is broken, fix it first
        if (torso != null && torso.isBroken()) {
            torso.setBroken(false);
            torso.setBrokenTopHalf(null);
            if (torso.getHealth() <= 0.0f) torso.setHealth(1.0f);
            count--;
        }
        if (count <= 0) return;
        // Gather other broken bones except head
        for (BodyPart p : getParts()) {
            if (p.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD)) continue;
            if (p.isBroken()) candidates.add(p);
        }
        java.util.Collections.shuffle(candidates, new java.util.Random());
        for (BodyPart p : candidates) {
            if (count <= 0) break;
            p.setBroken(false);
            p.setBrokenTopHalf(null);
            if (p.getHealth() <= 0.0f) p.setHealth(1.0f);
            count--;
        }
    }


    protected boolean anyBoneBroken() {
        for (BodyPart p : getParts()) {
            if (!p.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD) && p.isBroken()) {
                return true;
            }
        }
        return false;
    }

    protected int brokenBonesCount() {
        int c = 0;
        for (BodyPart p : getParts()) {
            if (!p.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD) && p.isBroken()) c++;
        }
        return c;
    }

    // ----- Absorption distribution logic -----
    // Ensure our per-part buckets reflect the entity's current absorption amount.
    // When absorption increases, we add to buckets following priority rules:
    // Head up to 4, Torso up to 4, then distribute any remainder evenly among alive limbs.
    protected void ensureAbsorptionBucketsUpToDate() {
        buckets.ensureAbsorptionBucketsUpToDate();
    }





    // ----- Health Boost distribution logic -----
    protected void ensureBoostBucketsUpToDate() {
        buckets.ensureBoostBucketsUpToDate();
    }





    protected float getAbsorptionBucket(BodyPart part) {
        return buckets.bucketFor(part);
    }

    protected void consumeAbsorptionFromBucket(BodyPart part, float amount) {
        buckets.consumeAbsorption(part, amount);
    }

    protected float getBoostBucket(BodyPart part) {
        if (part == null) return 0.0f;
        return boostBuckets.getOrDefault(part.getIdentifier(), 0.0f);
    }

    protected void consumeBoostFromBucket(BodyPart part, float amount) {
        if (part == null || amount <= 0.0f) return;
        Identifier id = part.getIdentifier();
        float current = boostBuckets.getOrDefault(id, 0.0f);
        float newVal = Math.max(0.0f, current - amount);
        boostBuckets.put(id, newVal);
    }

    // Expose for networking/UI
    public void prepareBucketSync() {
        buckets.prepareBucketSync();
    }

    public void clientSetAbsorptionBucket(Identifier id, float value) {
        buckets.clientSetAbsorptionBucket(id, value);
    }

    public void clientSetBoostBucket(Identifier id, float value) {
        buckets.clientSetBoostBucket(id, value);
    }

    public float getAbsorptionForPart(Identifier id) {
        return buckets.getAbsorptionForPart(id);
    }

    public float getBoostForPart(Identifier id) {
        return buckets.getBoostForPart(id);
    }

    // Called on server tick to react to max-health changes (effects/gear)
    public boolean syncBoostIfNeeded() {
        return buckets.syncBoostIfNeeded();
    }
}
