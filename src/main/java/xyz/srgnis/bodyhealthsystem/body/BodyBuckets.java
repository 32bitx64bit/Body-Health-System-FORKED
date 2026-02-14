package xyz.srgnis.bodyhealthsystem.body;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates per-part absorption and health-boost bucket logic.
 * Body acts as a facade and delegates to this helper to reduce bloat.
 * Uses ConcurrentHashMap for thread-safe bucket access during network sync.
 */
class BodyBuckets {
    private final Body body;
    private final ConcurrentHashMap<Identifier, Float> absorptionBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Identifier, Float> boostBuckets = new ConcurrentHashMap<>();
    private volatile float lastKnownMaxHealth = -1.0f;

    BodyBuckets(Body body) {
        this.body = body;
    }

    void onPartAdded(Identifier id) {
        absorptionBuckets.put(id, 0.0f);
        boostBuckets.put(id, 0.0f);
    }

    // ----- Absorption distribution -----
    void ensureAbsorptionBucketsUpToDate() {
        // Reclaim buckets from dead parts
        float reclaim = 0.0f;
        for (BodyPart p : body.getParts()) {
            if (p.getHealth() <= 0.0f) {
                Identifier id = p.getIdentifier();
                float b = absorptionBuckets.getOrDefault(id, 0.0f);
                if (b > 0.0f) {
                    reclaim += b;
                    absorptionBuckets.put(id, 0.0f);
                }
            }
        }
        if (reclaim > 0.0f) addAbsorptionToBuckets(reclaim);

        float totalBuckets = 0.0f;
        for (Float value : absorptionBuckets.values()) totalBuckets += value;
        float entityAbs = body.entity.getAbsorptionAmount();
        if (entityAbs > totalBuckets + 0.001f) {
            addAbsorptionToBuckets(entityAbs - totalBuckets);
        } else if (entityAbs + 0.001f < totalBuckets) {
            float factor = entityAbs <= 0.0f ? 0.0f : (entityAbs / Math.max(totalBuckets, 0.0001f));
            absorptionBuckets.replaceAll((id, val) -> val * factor);
        }
    }

    private void addAbsorptionToBuckets(float amount) {
        if (amount <= 0.0f) return;
        var HEAD = xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD;
        var TORSO = xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO;
        BodyPart head = body.getPart(HEAD);
        BodyPart torso = body.getPart(TORSO);
        if (head != null && head.getHealth() > 0.0f) {
            float current = absorptionBuckets.getOrDefault(HEAD, 0.0f);
            float add = Math.min(2.0f - current, amount);
            if (add > 0.0f) {
                absorptionBuckets.put(HEAD, current + add);
                amount -= add;
            }
        }
        if (amount > 0.0f && torso != null && torso.getHealth() > 0.0f) {
            float current = absorptionBuckets.getOrDefault(TORSO, 0.0f);
            float add = Math.min(2.0f - current, amount);
            if (add > 0.0f) {
                absorptionBuckets.put(TORSO, current + add);
                amount -= add;
            }
        }
        List<BodyPart> alive = new ArrayList<>();
        for (BodyPart p : body.getParts()) if (p.getHealth() > 0.0f) alive.add(p);
        if (amount > 0.0f && !alive.isEmpty()) {
            float share = amount / (float) alive.size();
            for (BodyPart p : alive) {
                Identifier id = p.getIdentifier();
                float current = absorptionBuckets.getOrDefault(id, 0.0f);
                absorptionBuckets.put(id, current + share);
            }
        }
    }

    float getAbsorptionBucket(BodyPart part) {
        if (part == null) return 0.0f;
        return absorptionBuckets.getOrDefault(part.getIdentifier(), 0.0f);
    }

    void consumeAbsorptionFromBucket(BodyPart part, float amount) {
        if (part == null || amount <= 0.0f) return;
        Identifier id = part.getIdentifier();
        float current = absorptionBuckets.getOrDefault(id, 0.0f);
        absorptionBuckets.put(id, Math.max(0.0f, current - amount));
    }

    // ----- Health Boost distribution -----
    void ensureBoostBucketsUpToDate() {
        // Reclaim from dead parts
        float reclaim = 0.0f;
        for (BodyPart p : body.getParts()) {
            if (p.getHealth() <= 0.0f) {
                Identifier id = p.getIdentifier();
                float b = boostBuckets.getOrDefault(id, 0.0f);
                if (b > 0.0f) {
                    reclaim += b;
                    boostBuckets.put(id, 0.0f);
                }
            }
        }
        if (reclaim > 0.0f) addBoostToBuckets(reclaim);

        float totalBuckets = 0.0f;
        for (Float value : boostBuckets.values()) totalBuckets += value;
        float extra = Math.max(0.0f, body.entity.getMaxHealth() - 20.0f);
        if (extra > totalBuckets + 0.001f) {
            addBoostToBuckets(extra - totalBuckets);
        } else if (extra + 0.001f < totalBuckets) {
            float factor = extra <= 0.0f ? 0.0f : (extra / Math.max(totalBuckets, 0.0001f));
            boostBuckets.replaceAll((id, val) -> val * factor);
        }
        clampAllPartsToEffectiveCap();
    }

    private void clampAllPartsToEffectiveCap() {
        for (BodyPart p : body.getParts()) {
            float boost = getBoostForPart(p.getIdentifier());
            float cap = p.getMaxHealth() + Math.max(0.0f, boost);
            if (p.getHealth() > cap) p.setHealth(cap);
        }
    }

    private void addBoostToBuckets(float amount) {
        if (amount <= 0.0f) return;
        var HEAD = xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD;
        var TORSO = xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO;
        BodyPart head = body.getPart(HEAD);
        BodyPart torso = body.getPart(TORSO);
        if (head != null && head.getHealth() > 0.0f) {
            float current = boostBuckets.getOrDefault(HEAD, 0.0f);
            float add = Math.min(2.0f - current, amount);
            if (add > 0.0f) {
                boostBuckets.put(HEAD, current + add);
                amount -= add;
            }
        }
        if (amount > 0.0f && torso != null && torso.getHealth() > 0.0f) {
            float current = boostBuckets.getOrDefault(TORSO, 0.0f);
            float add = Math.min(2.0f - current, amount);
            if (add > 0.0f) {
                boostBuckets.put(TORSO, current + add);
                amount -= add;
            }
        }
        List<BodyPart> alive = new ArrayList<>();
        for (BodyPart p : body.getParts()) if (p.getHealth() > 0.0f) alive.add(p);
        if (amount > 0.0f && !alive.isEmpty()) {
            float share = amount / (float) alive.size();
            for (BodyPart p : alive) {
                Identifier id = p.getIdentifier();
                float current = boostBuckets.getOrDefault(id, 0.0f);
                boostBuckets.put(id, current + share);
            }
        }
    }

    // Facade helpers exposed via Body
    void prepareBucketSync() {
        ensureAbsorptionBucketsUpToDate();
        ensureBoostBucketsUpToDate();
    }

    void clientSetAbsorptionBucket(Identifier id, float value) {
        absorptionBuckets.put(id, Math.max(0.0f, value));
    }
    void clientSetBoostBucket(Identifier id, float value) {
        boostBuckets.put(id, Math.max(0.0f, value));
    }
    float getAbsorptionForPart(Identifier id) { return absorptionBuckets.getOrDefault(id, 0.0f); }
    float getBoostForPart(Identifier id) { return boostBuckets.getOrDefault(id, 0.0f); }

    // Track and react to max-health changes from attributes/effects
    boolean syncBoostIfNeeded() {
        float current = (body.entity != null) ? body.entity.getMaxHealth() : 0.0f;
        if (lastKnownMaxHealth < 0.0f) {
            lastKnownMaxHealth = current;
            ensureBoostBucketsUpToDate();
            clampAllPartsToEffectiveCap();
            return true;
        }
        if (Math.abs(current - lastKnownMaxHealth) > 0.01f) {
            lastKnownMaxHealth = current;
            ensureBoostBucketsUpToDate();
            clampAllPartsToEffectiveCap();
            body.updateHealth();
            return true;
        }
        return false;
    }

    // Package-private accessors for Body.takeDamage
    float bucketFor(BodyPart part) { return getAbsorptionBucket(part); }
    void consumeAbsorption(BodyPart part, float amt) { consumeAbsorptionFromBucket(part, amt); }
}