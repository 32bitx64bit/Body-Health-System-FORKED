package xyz.srgnis.bodyhealthsystem.body;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Encapsulates per-part absorption and health-boost bucket logic.
 * Body acts as a facade and delegates to this helper to reduce bloat.
 */
class BodyBuckets {
    private final Body body;
    private final HashMap<Identifier, Float> absorptionBuckets = new HashMap<>();
    private final HashMap<Identifier, Float> boostBuckets = new HashMap<>();
    private float lastKnownMaxHealth = -1.0f;

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
        // Optimized: use view to avoid ArrayList creation
        for (BodyPart p : body.getPartsView()) {
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
        for (Identifier id : absorptionBuckets.keySet()) totalBuckets += absorptionBuckets.get(id);
        float entityAbs = body.entity.getAbsorptionAmount();
        if (entityAbs > totalBuckets + 0.001f) {
            addAbsorptionToBuckets(entityAbs - totalBuckets);
        } else if (entityAbs + 0.001f < totalBuckets) {
            float factor = entityAbs <= 0.0f ? 0.0f : (entityAbs / Math.max(totalBuckets, 0.0001f));
            for (Identifier id : new ArrayList<>(absorptionBuckets.keySet())) {
                absorptionBuckets.put(id, absorptionBuckets.get(id) * factor);
            }
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
        // Optimized: use view to avoid ArrayList creation
        for (BodyPart p : body.getPartsView()) if (p.getHealth() > 0.0f) alive.add(p);
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
        // Optimized: use view to avoid ArrayList creation
        for (BodyPart p : body.getPartsView()) {
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
        for (Identifier id : boostBuckets.keySet()) totalBuckets += boostBuckets.get(id);
        float extra = Math.max(0.0f, body.entity.getMaxHealth() - 20.0f);
        if (extra > totalBuckets + 0.001f) {
            addBoostToBuckets(extra - totalBuckets);
        } else if (extra + 0.001f < totalBuckets) {
            float factor = extra <= 0.0f ? 0.0f : (extra / Math.max(totalBuckets, 0.0001f));
            for (Identifier id : new ArrayList<>(boostBuckets.keySet())) {
                boostBuckets.put(id, boostBuckets.get(id) * factor);
            }
        }
        clampAllPartsToEffectiveCap();
    }

    private void clampAllPartsToEffectiveCap() {
        // Optimized: use view to avoid ArrayList creation
        for (BodyPart p : body.getPartsView()) {
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
        // Optimized: use view to avoid ArrayList creation
        for (BodyPart p : body.getPartsView()) if (p.getHealth() > 0.0f) alive.add(p);
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