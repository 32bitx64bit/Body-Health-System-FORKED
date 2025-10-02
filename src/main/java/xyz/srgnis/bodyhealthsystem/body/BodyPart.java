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
    private float maxHealth;
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
        float cap = maxHealth + boost;
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
        setHealth(maxHealth + boost);
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
    public float getMaxHealth() {
        return maxHealth;
    }
    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
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
    }

    @Override
    public String toString() {
        return identifier.getPath() + " | MaxHP: " + maxHealth + " | HP " + health + "\n";
    }

    public boolean isDamaged() {
        float boost = 0.0f;
        try {
            if (body != null && identifier != null) {
                boost = Math.max(0.0f, body.getBoostForPart(identifier));
            }
        } catch (Throwable ignored) {}
        return health < (maxHealth + boost);
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
}
