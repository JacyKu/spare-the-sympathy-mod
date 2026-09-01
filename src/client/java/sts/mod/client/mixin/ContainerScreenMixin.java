package sts.mod.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sts.mod.client.armoury.ArmouryOverlay;

/**
 * Container screens draw their slot contents and item tooltips AFTER
 * {@code Screen.render()} returns (in their own overridden render), so the
 * Fabric ScreenEvents.afterRender hook paints underneath them. Injecting at
 * the RETURN of {@code ContainerScreen.render} puts the armoury overlay on
 * top of everything.
 */
@Mixin(ContainerScreen.class)
abstract class ContainerScreenMixin {
	@Inject(method = "render", at = @At("RETURN"))
	private void sparethesympathy$armouryOverlay(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo callbackInfo) {
		ArmouryOverlay.render(graphics, mouseX, mouseY);
	}
}
