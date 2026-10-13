package sts.mod.client.compat.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compatibility fix for Unofficial Monumenta Mod (optional: only applied when
 * it is installed, see StsCompatMixinPlugin).
 * <p>
 * UMM's screen mixin initialises its calculator widget for every screen it is
 * mixed into, assuming the screen being initialised is the currently open one
 * (the widget casts it to a container screen). Wrapper screens break that
 * assumption - e.g. ReplayMod re-wraps the open screen into its own
 * VanillaGuiScreen while the window is being resized, and UMM then casts the
 * wrapper to its HandledScreenAccessor and crashes the game. Skip the widget
 * for any screen that is not the currently open one.
 */
@Mixin(targets = "ch.njol.unofficialmonumentamod.mixins.screen.ScreenMixin", remap = false)
public abstract class UmmScreenMixinCompatibility {
	@Inject(method = "initializeWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void sparethesympathy$skipForeignScreens(CallbackInfo ci) {
		if (Minecraft.getInstance().screen != (Object) this) {
			ci.cancel();
		}
	}
}
