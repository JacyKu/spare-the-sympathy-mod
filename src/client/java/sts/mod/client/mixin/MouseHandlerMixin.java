package sts.mod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sts.mod.client.armoury.ArmouryOverlay;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	private void sparethesympathy$armouryButtons(long windowPointer, int button, int action, int modifiers, CallbackInfo callbackInfo) {
		if (action != GLFW.GLFW_PRESS || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return;
		}
		if (windowPointer != this.minecraft.getWindow().getWindow() || this.minecraft.getOverlay() != null || this.minecraft.screen == null) {
			return;
		}
		double guiX = this.xpos * (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
		double guiY = this.ypos * (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();
		if (!ArmouryOverlay.handleClick(guiX, guiY)) {
			return;
		}
		Screen screen = this.minecraft.screen;
		screen.afterMouseAction();
		callbackInfo.cancel();
	}
}
