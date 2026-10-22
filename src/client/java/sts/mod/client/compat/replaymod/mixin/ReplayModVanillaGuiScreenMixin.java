package sts.mod.client.compat.replaymod.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sts.mod.client.armoury.ArmouryButtons;
import sts.mod.client.skills.SkillPickerOverlay;
import sts.mod.client.view.ViewedPlayersButtons;

/**
 * ReplayMod wraps every vanilla screen in a jGui {@code VanillaGuiScreen}.
 * Its {@code EventHandler.mouseDown} forwards the click to the screen and then
 * returns true, which makes jGui cancel the vanilla {@code Mouse} method before
 * it records the active button. Container screens then never receive a
 * {@code mouseDragged} event, so the mod's Ctrl+drag buttons cannot move.
 * <p>
 * For a Ctrl+click over one of our buttons, skip ReplayMod's wrapper so the
 * vanilla mouse keeps its active button. The drag/release injections below are
 * a fallback for ReplayMod versions that route the event through the wrapper
 * instead of the screen.
 */
@Mixin(targets = "com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen$EventHandler", remap = false)
abstract class ReplayModVanillaGuiScreenMixin {
	@Inject(method = "mouseDown", at = @At("HEAD"), cancellable = true)
	private void sparethesympathy$allowButtonDrag(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button == 0
			&& Screen.hasControlDown()
			&& (ArmouryButtons.isOverButton(mouseX, mouseY)
				|| ViewedPlayersButtons.isOverButton(mouseX, mouseY)
				|| SkillPickerOverlay.isOverButton(mouseX, mouseY))) {
			cir.setReturnValue(false);
			cir.cancel();
		}
	}

	@Inject(method = "mouseDrag", at = @At("HEAD"), cancellable = true)
	private void sparethesympathy$dragButtons(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
		if (ArmouryButtons.dragIfOverButton(mouseX, mouseY, button)
			|| ViewedPlayersButtons.dragIfOverButton(mouseX, mouseY, button)
			|| SkillPickerOverlay.dragIfOver(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			cir.cancel();
		}
	}

	@Inject(method = "mouseUp", at = @At("HEAD"))
	private void sparethesympathy$endButtonDrag(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		ArmouryButtons.endDrag();
		ViewedPlayersButtons.endDrag();
		SkillPickerOverlay.endDrag();
	}
}
