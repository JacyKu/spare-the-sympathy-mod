package sts.mod.client.compat.mixin;

import java.awt.Rectangle;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compatibility fix for Unofficial Monumenta Mod (optional: only applied when
 * it is installed, see StsCompatMixinPlugin).
 * <p>
 * Belt and braces for {@link UmmScreenMixinCompatibility}: the calculator
 * widget's getDimension() casts the screen it was created for to UMM's
 * HandledScreenAccessor. A non-container screen (e.g. ReplayMod's resize
 * wrapper) is never a container screen, so answer with a harmless default
 * instead of throwing a ClassCastException.
 */
@Mixin(targets = "ch.njol.unofficialmonumentamod.features.calculator.CalculatorWidget", remap = false)
public abstract class UmmCalculatorWidgetCompatibility {
	@Shadow
	private Screen parent;

	@Inject(method = "getDimension", at = @At("HEAD"), cancellable = true, require = 0)
	private void sparethesympathy$guardNonContainerParent(CallbackInfoReturnable<Rectangle> cir) {
		if (!(parent instanceof AbstractContainerScreen)) {
			cir.setReturnValue(new Rectangle(0, 0, 20, 40));
		}
	}
}
