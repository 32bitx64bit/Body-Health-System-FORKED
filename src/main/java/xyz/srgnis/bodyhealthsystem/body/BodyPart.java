package xyz.srgnis.bodyhealthsystem.body;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

//TODO: Allow Override max health on write/read to nbt?
//TODO: Check LivingEntity.getEquippedStack(), could be alternative to the actual implementation for getting the armor
public abstract class BodyPart {
    private float maxHealth; // base max (unscaled)
    private float health;
    private boolean broken = false;
    // For arms: whether the broken overlay should be on the top half (true) or bottom half (false)
    private Boolean brokenTopHalf = null;
    // Sleep healing: accumulated bonus chance to heal this bone on wake-up
    private float sleepHealBonus = 0.0f;
    // Ticks since this bone became broken (server-authoritative); used for delayed broken-bone effect
    private int brokenTicks = 0;
    // After prolonged time broken, the fracture becomes severe: part cannot be healed by simple items
    private boolean fractureLocked = false;

    // Wound/bleeding system
    private int smallWounds = 0; // combined wound cap will be 1 (small or large)
    private int largeWounds = 0;
    private boolean tourniquet = false;
    private int tourniquetTicks = 0; // counts up while applied
    // Bleeding cadence per-limb: counts ticks since last bleed application for the current wound
    private int woundBleedTicks = 0;

    // Necrosis: 0=none, 1=active (reducing max), 2=perma-dead (max==0 until reset)
    private int necrosisState = 0;
    private int necrosisTicks = 0;
    private int recoveryTicks = 0; // counts down recovery to restore max
    private float necrosisScale = 1.0f; // scales base maxHealth (0..1)

    protected float criticalThreshold;
    private LivingEntity entity;
    private Identifier identifier;
    protected int armorSlot;
    protected Body body;
    //TODO: Add this to the mod config
    protected boolean isKillRequirement = false;

    protected DefaultedList<ItemStack> armorList;
    public BodyPart(float maxHealth, float health, LivingEntity entity, Identifier identifier) {
        this.maxHealth = maxHealth;
        this.health = health;
        this.entity = entity;
        this.identifier = identifier;
        this.body = ((BodyProvider)entity).getBody();
    }

    public void setHealth(float health) {
        float boost = 0.0f;
        try {
            if (body != null && identifier != null) {
                boost = Math.max(0.0f, body.getBoostForPart(identifier));
            }
        } catch (Throwable ignored) {}
        float cap = getMaxHealth() + boost;
        this.health = Math.min(Math.max(health, 0), cap);
        body.checkNoCritical(this);
    }

    public void heal(){
        float boost = 0.0f;
        try {
            if (body != null && identifier != null) {
                boost = Math.max(0.0f, body.getBoostForPart(identifier));
            }
        } catch (Throwable ignored) {}
        setHealth(getMaxHealth() + boost);
    }

    public float heal(float amount){
        float newHealth = health + amount;
        setHealth(newHealth);

        return newHealth - health;
    }

    public void damage(){setHealth(0);}

    public float damage(float amount){
        float newHealth = health - amount;
        setHealth(newHealth);

        return Math.max(0, -newHealth);
    }

    public float damageWithoutKill(float amount){
        float newHealth = health - amount;

        if(isKillRequirement) {
            setHealth(Math.max(1, newHealth));
        }else {
            setHealth(newHealth);
        }

        return Math.max(0, -newHealth);
    }

    public ItemStack getAffectedArmor(){
        return armorList.get(armorSlot);
    }

    public int getArmorSlot() {
        return armorSlot;
    }

    public float getHealth() {
        return health;
    }

    // Effective max (accounts for necrosis scale)
    public float getMaxHealth() {
        return Math.max(0.0f, maxHealth * Math.max(0.0f, Math.min(1.0f, necrosisScale)));
    }

    public float getBaseMaxHealth() { return maxHealth; }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
        // Clamp health to new effective cap
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }
    public LivingEntity getEntity() {
        return entity;
    }
    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }
    public Identifier getIdentifier() {
        return identifier;
    }
    public void setIdentifier(Identifier identifier) {
        this.identifier = identifier;
    }

    public float getCriticalThreshold() {
        return criticalThreshold;
    }

    public void writeToNbt(NbtCompound nbt){
        NbtCompound new_nbt = new NbtCompound();
        new_nbt.putFloat("health", health);
        new_nbt.putBoolean("broken", broken);
        if (brokenTopHalf != null) new_nbt.putBoolean("brokenTopHalf", brokenTopHalf);
        if (sleepHealBonus > 0.0f) new_nbt.putFloat("sleepHealBonus", sleepHealBonus);
        if (broken && brokenTicks > 0) new_nbt.putInt("brokenTicks", brokenTicks);
        if (fractureLocked) new_nbt.putBoolean("fractureLocked", true);
        // Wounds/tourniquet/necrosis
        if (smallWounds > 0) new_nbt.putInt("smallWounds", smallWounds);
        if (largeWounds > 0) new_nbt.putInt("largeWounds", largeWounds);
        if (tourniquet) new_nbt.putBoolean("tourniquet", true);
        if (tourniquetTicks > 0) new_nbt.putInt("tourniquetTicks", tourniquetTicks);
        if (necrosisState != 0) new_nbt.putInt("necrosisState", necrosisState);
        if (necrosisTicks > 0) new_nbt.putInt("necrosisTicks", necrosisTicks);
        if (recoveryTicks > 0) new_nbt.putInt("recoveryTicks", recoveryTicks);
        if (necrosisScale != 1.0f) new_nbt.putFloat("necrosisScale", necrosisScale);
        if (woundBleedTicks > 0) new_nbt.putInt("woundBleedTicks", woundBleedTicks);
        nbt.put(identifier.toString(), new_nbt);
    }

    public void readFromNbt(NbtCompound nbt){
        health = nbt.getFloat("health");
        if (nbt.contains("broken")) {
            broken = nbt.getBoolean("broken");
        } else {
            broken = false;
        }
        if (nbt.contains("brokenTopHalf")) {
            brokenTopHalf = nbt.getBoolean("brokenTopHalf");
        } else {
            brokenTopHalf = null;
        }
        if (nbt.contains("sleepHealBonus")) {
            sleepHealBonus = nbt.getFloat("sleepHealBonus");
        } else {
            sleepHealBonus = 0.0f;
        }
        if (broken && nbt.contains("brokenTicks")) {
            brokenTicks = nbt.getInt("brokenTicks");
        } else {
            brokenTicks = 0;
        }
        if (nbt.contains("fractureLocked")) {
            fractureLocked = nbt.getBoolean("fractureLocked");
        } else {
            fractureLocked = false;
        }
        // Wounds/tourniquet/necrosis
        smallWounds = nbt.contains("smallWounds") ? nbt.getInt("smallWounds") : 0;
        largeWounds = nbt.contains("largeWounds") ? nbt.getInt("largeWounds") : 0;
        tourniquet = nbt.contains("tourniquet") && nbt.getBoolean("tourniquet");
        tourniquetTicks = nbt.contains("tourniquetTicks") ? nbt.getInt("tourniquetTicks") : 0;
        necrosisState = nbt.contains("necrosisState") ? nbt.getInt("necrosisState") : 0;
        necrosisTicks = nbt.contains("necrosisTicks") ? nbt.getInt("necrosisTicks") : 0;
        recoveryTicks = nbt.contains("recoveryTicks") ? nbt.getInt("recoveryTicks") : 0;
        necrosisScale = nbt.contains("necrosisScale") ? nbt.getFloat("necrosisScale") : 1.0f;
        woundBleedTicks = nbt.contains("woundBleedTicks") ? nbt.getInt("woundBleedTicks") : 0;
        // Clamp invariants: wound cap = 1
        smallWounds = Math.max(0, Math.min(1, smallWounds));
        largeWounds = Math.max(0, Math.min(1, largeWounds));
        if (smallWounds + largeWounds > 1) {
            // prefer keeping large wound if both present
            if (largeWounds > 0) smallWounds = 0; else largeWounds = 0;
        }
        necrosisScale = Math.max(0.0f, Math.min(1.0f, necrosisScale));
    }

    @Override
    public String toString() {
        return identifier.getPath() + " | MaxHP: " + getMaxHealth() + " | HP " + health + "\n";
    }

    public boolean isDamaged() {
        float boost = 0.0f;
        try {
            if (body != null && identifier != null) {
                boost = Math.max(0.0f, body.getBoostForPart(identifier));
            }
        } catch (Throwable ignored) {}
        return health < (getMaxHealth() + boost);
    }

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
        if (!broken) {
            // Reset accumulated sleep bonus when healed
            this.sleepHealBonus = 0.0f;
            this.brokenTicks = 0;
            this.fractureLocked = false;
        } else {
            // Reset age on new break
            this.brokenTicks = 0;
        }
    }

    public Boolean getBrokenTopHalf() {
        return brokenTopHalf;
    }

    public void setBrokenTopHalf(Boolean brokenTopHalf) {
        this.brokenTopHalf = brokenTopHalf;
    }

    public float getSleepHealBonus() {
        return sleepHealBonus;
    }

    public int getBrokenTicks() { return brokenTicks; }
    public void tickBroken() { if (broken && brokenTicks < Integer.MAX_VALUE) brokenTicks++; }

    public boolean isFractureLocked() { return fractureLocked; }
    public void setFractureLocked(boolean locked) { this.fractureLocked = locked; }

    public void setSleepHealBonus(float bonus) {
        this.sleepHealBonus = Math.max(0.0f, Math.min(1.0f, bonus));
    }

    // ---- Wound helpers ----
    public int getSmallWounds() { return smallWounds; }
    public int getLargeWounds() { return largeWounds; }
    public int getTotalWounds() { return smallWounds + largeWounds; }
    public boolean hasWoundCapacity() { return getTotalWounds() < 1; }
    public boolean addSmallWound() {
        // Cap 1: if large already present, do nothing; if none, add small; reset bleed timer
        if (largeWounds > 0) return false;
        if (smallWounds > 0) return false;
        smallWounds = 1;
        woundBleedTicks = 0;
        return true;
    }
    public boolean addLargeWound() {
        // Cap 1: upgrade small->large; otherwise set large; reset bleed timer
        if (largeWounds > 0) return false;
        if (smallWounds > 0) smallWounds = 0;
        largeWounds = 1;
        woundBleedTicks = 0;
        return true;
    }
    public boolean removeSmallWound() {
        if (smallWounds <= 0) return false;
        smallWounds = 0;
        woundBleedTicks = 0;
        return true;
    }
    public boolean removeLargeWound() {
        if (largeWounds <= 0) return false;
        largeWounds = 0;
        woundBleedTicks = 0;
        return true;
    }

    // ---- Tourniquet/necrosis helpers ----
    public boolean hasTourniquet() { return tourniquet; }
    public int getTourniquetTicks() { return tourniquetTicks; }
    public void setTourniquet(boolean applied) {
        if (this.tourniquet == applied) return;
        this.tourniquet = applied;
        if (applied) {
            this.tourniquetTicks = 0;
        } else {
            // On removal: if in necrosis, begin recovery; if perma-dead, keep perma until notch apple
            if (necrosisState == 1) {
                // 5 minutes recovery
                this.recoveryTicks = 5 * 60 * 20;
            }
        }
    }
    public void tickTourniquet() { if (tourniquet && tourniquetTicks < Integer.MAX_VALUE) tourniquetTicks++; }
    public void tickWoundBleed() { if (getTotalWounds() > 0 && woundBleedTicks < Integer.MAX_VALUE) woundBleedTicks++; }
    public int getWoundBleedTicks() { return woundBleedTicks; }
    public void resetWoundBleedTicks() { woundBleedTicks = 0; }

    public int getNecrosisState() { return necrosisState; }
    public float getNecrosisScale() { return necrosisScale; }
    public void setPermaDead() {
        this.necrosisState = 2;
        this.necrosisTicks = 0;
        this.recoveryTicks = 0;
        this.necrosisScale = 0.0f;
        setHealth(0.0f);
    }
    public void beginNecrosis() {
        if (necrosisState == 0) {
            necrosisState = 1;
            necrosisTicks = 0;
        }
    }
    public void clearNecrosis() {
        necrosisState = 0;
        necrosisTicks = 0;
        // keep current necrosisScale; recovery logic will restore gradually
    }
    public void tickNecrosisLinear(float totalMinutes) {
        if (necrosisState != 1) return;
        necrosisTicks++;
        int totalTicks = Math.max(1, (int)(totalMinutes * 60.0f * 20.0f));
        float progress = Math.min(1.0f, necrosisTicks / (float) totalTicks);
        necrosisScale = Math.max(0.0f, 1.0f - progress);
        // Clamp current health to new cap
        if (health > getMaxHealth()) setHealth(getMaxHealth());
        if (progress >= 1.0f) {
            // caller decides if we move to perma-dead (for limbs) or death (for head)
        }
    }
    public void tickRecovery() {
        if (recoveryTicks > 0) {
            recoveryTicks--;
            float step = 1.0f / (5.0f * 60.0f * 20.0f); // per tick towards full
            necrosisScale = Math.min(1.0f, necrosisScale + step);
        }
    }
    public void forceStartRecovery() {
        recoveryTicks = 5 * 60 * 20;
    }

    // ---- Client sync setters ----
    public void clientSetWounds(int small, int large) {
        this.smallWounds = Math.max(0, Math.min(1, small));
        this.largeWounds = Math.max(0, Math.min(1, large));
        if (this.smallWounds + this.largeWounds > 1) {
            if (this.largeWounds > 0) this.smallWounds = 0; else this.largeWounds = 0;
        }
    }
    public void clientSetTourniquet(boolean applied, int ticks) {
        this.tourniquet = applied;
        this.tourniquetTicks = Math.max(0, ticks);
    }
    public void clientSetNecrosis(int state, float scale) {
        this.necrosisState = Math.max(0, Math.min(2, state));
        this.necrosisScale = Math.max(0.0f, Math.min(1.0f, scale));
        // Clamp current health to effective cap
        if (health > getMaxHealth()) setHealth(getMaxHealth());
    }
}
