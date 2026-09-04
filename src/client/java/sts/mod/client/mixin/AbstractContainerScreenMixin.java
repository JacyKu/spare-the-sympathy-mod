package sts.mod.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sts.mod.client.armoury.ArmouryButtons;

/**
 * Container screens override {@code mouseDragged} for slot/item dragging and
 * always consume the event (they never forward it to the focused widget), so
 * the armoury button widgets can never see a drag. This mixin intercepts the
 * drag at the screen level: when Ctrl is held and the cursor is over one of
 * the armoury buttons, the buttons are moved instead and the container's own
 * slot-drag logic is skipped.
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void sparethesympathy$dragArmouryButtons(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
		if (ArmouryButtons.dragIfOverButton(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			cir.cancel();
		}
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"))
	private void sparethesympathy$endArmouryButtonDrag(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		ArmouryButtons.endDrag();
	}
}