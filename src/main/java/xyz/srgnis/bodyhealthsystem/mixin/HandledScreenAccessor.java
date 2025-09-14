package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x") int getX_BHS();
    @Accessor("y") int getY_BHS();
    @Accessor("backgroundWidth") int getBackgroundWidth_BHS();
    @Accessor("backgroundHeight") int getBackgroundHeight_BHS();
}
