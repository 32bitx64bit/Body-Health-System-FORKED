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
import xyz.srgnis.bodyhealthsystem.util.Utils;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public abstract class Body {
    protected final HashMap<Identifier, BodyPart> parts = new HashMap<>();
    protected HashMap<Identifier, BodyPart> noCriticalParts = new HashMap<>();
    protected LivingEntity entity;

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
        amount = Math.max(amount - entity.getAbsorptionAmount(), 0.0f);
        entity.setAbsorptionAmount(entity.getAbsorptionAmount() - (f - amount));
        float g = f - amount;
        if (g > 0.0f && g < 3.4028235E37f && source.getAttacker() instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity)source.getAttacker()).increaseStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(g * 10.0f));
        }
        if (amount == 0.0f) {
            return amount;
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
        if (!part.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD)) {
            evaluateBoneBreak(part, previousHealth, amount);
        }

        entity.getDamageTracker().onDamage(source, amount);
        entity.setAbsorptionAmount(entity.getAbsorptionAmount() - amount);
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

        // Base chance: 0% at 100% health, 30% at 50% health, scales linearly to 100% at 0
        float baseChance = 0.0f;
        if (healthRatio < 1.0f) {
            // Map 1.0 -> 0, 0.5 -> 0.3, 0.0 -> 1.0 (monotonic increasing as health decreases)
            if (healthRatio >= 0.5f) {
                // Between 50% and 100% health, interpolate 0.3 -> 0
                baseChance = (1.0f - healthRatio) * (0.3f / 0.5f); // at 0.5 -> 0.3, at 1.0 -> 0
            } else {
                // Below 50% health, ramp towards 100% as health goes to 0
                baseChance = 0.3f + (0.5f - healthRatio) * (0.7f / 0.5f); // at 0.5 -> 0.3, at 0 -> 1.0
            }
        }

        // Damage bonus: additional up to 15% based on incoming raw damage relative to max health
        float damageRatio = Math.min(rawDamage / max, 1.0f);
        float bonus = 0.15f * damageRatio; // 0..0.15

        float chance = Math.min(baseChance + bonus, 1.0f);

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

    public abstract void applyCriticalPartsEffect();

    // Apply bone-based negative effects and timers. Call this each tick on server.
    public void applyBrokenBonesEffects() {
        if (!(entity instanceof net.minecraft.entity.player.PlayerEntity player)) return;
        if (entity.getWorld().isClient) return; // server-side control

        // If no bones are broken, deactivate timers and clear bone-based negatives
        if (!anyBoneBroken()) {
            bonePenaltyActive = false;
            boneGraceTicksRemaining = 0;
            bonePenaltyTickCounter = 0;
            // Clear bone-based effects
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE);
            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS);
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


        // Slowness: each broken bone adds +1 amplifier level (amplifier is level-1)
        int brokenCount = brokenBonesCount();
        if (brokenCount > 0) {
            int amp = Math.max(0, brokenCount - 1);
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 40, amp, false, false));
        }

        // Arms: mining fatigue
        int brokenArms = countBrokenArms();
        if (brokenArms > 0) {
            int amp = Math.max(0, brokenArms - 1);
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE, 40, amp, false, false));
        }

        // Torso: weakness II when torso bone is broken
        if (isTorsoBroken()) {
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.WEAKNESS, 40, 1, false, false));
        }

        // Feet: if BOTH feet are broken, add Slowness I extra (stacks with overall slowness)
        if (bothFeetBroken()) {
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 40, 0, false, false));
        }

        // Periodic health penalty once grace elapsed
        if (bonePenaltyActive) {
            bonePenaltyTickCounter++;
            if (bonePenaltyTickCounter >= 200) { // every 10 seconds
                bonePenaltyTickCounter = 0;
                int stacks = brokenBonesCount();
                if (stacks > 0) {
                    float dmg = 0.5f * stacks;
                    // Prefer torso as systemic damage target; fallback to a random non-critical part
                    BodyPart target = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO);
                    if (target == null || target.getHealth() <= 0.0f) {
                        var list = getNoCriticalParts();
                        // Never target the head with periodic bone penalty
                        list.removeIf(p -> p.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD));
                        if (!list.isEmpty()) {
                            target = list.get(entity.getRandom().nextInt(list.size()));
                        }
                    }
                    if (target != null) {
                        // Apply internal damage to the body part instead of raw player HP
                        takeDamage(dmg, player.getDamageSources().generic(), target);
                        updateHealth();
                        // Sync to clients so their HUD reflects the tick damage
                        ServerNetworking.broadcastBody(player);
                    }
                }
            }
        }
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
            entity.setHealth(0);
            return;
        }

        // If torso is destroyed, enter/maintain downed state instead of dying
        if (torso != null && torso.getHealth() <= 0.0f) {
            startDowned();
            // Keep player barely alive while downed to allow revival
            if (entity.getHealth() < 1.0f) {
                entity.setHealth(1.0f);
            }
            return;
        }

        // Normal proportional health mapping, with downed threshold fallback
        float max_health = 0;
        float actual_health = 0;
        for( BodyPart part : this.getParts()){
            max_health += part.getMaxHealth();
            actual_health += part.getHealth();
        }
        if (max_health > 0) {
            float ratio = actual_health / max_health;
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

    protected int countBrokenArms() {
        int c = 0;
        BodyPart la = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_ARM);
        BodyPart ra = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_ARM);
        if (la != null && la.isBroken()) c++;
        if (ra != null && ra.isBroken()) c++;
        return c;
    }

    protected int countBrokenLegs() {
        int c = 0;
        BodyPart ll = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_LEG);
        BodyPart rl = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_LEG);
        if (ll != null && ll.isBroken()) c++;
        if (rl != null && rl.isBroken()) c++;
        return c;
    }

    protected boolean bothFeetBroken() {
        BodyPart lf = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.LEFT_FOOT);
        BodyPart rf = getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.RIGHT_FOOT);
        return lf != null && rf != null && lf.isBroken() && rf.isBroken();
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
        if (!beingRevived) {
            if (bleedOutTicksRemaining > 0) {
                bleedOutTicksRemaining--;
            }
            if (bleedOutTicksRemaining <= 0) {
                // Bleed out - ensure a proper vanilla death path that supports respawn
                if (entity.isAlive()) {
                    pendingDeath = true;
                    entity.damage(entity.getDamageSources().outOfWorld(), 1000.0f);
                }
                clearDowned();
            }
        }
    }

    public void applyRevival(int healPerPart, int bonesToFix) {
        if (!downed) return;
        // Heal all damaged body parts by healPerPart
        for (BodyPart p : getParts()) {
            if (p.getHealth() < p.getMaxHealth()) {
                p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + healPerPart));
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
}
