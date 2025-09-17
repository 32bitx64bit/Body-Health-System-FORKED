package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

@Mixin(PlayerEntity.class)
public class SaveBodyMixin {
    @Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
    public void serializeBodyParts(NbtCompound tag, CallbackInfo ci) {
        ((BodyProvider)this).getBody().writeToNbt(tag);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
    public void deserializeBodyParts(NbtCompound tag, CallbackInfo ci) {
        BodyProvider pe = (BodyProvider)this;
        var body = pe.getBody();
        body.readFromNbt(tag);
        // Re-evaluate overall/derived state after loading parts so downed persists across relogs
        if (!((PlayerEntity)(Object)this).getWorld().isClient) {
            body.updateHealth();
            if ((Object)this instanceof ServerPlayerEntity spe) {
                ServerNetworking.syncBody(spe);
            }
        }
    }
}
