package sts.mod.client.mixin;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sts.mod.client.view.ViewedPlayersButtons;

/**
 * Adds the /ps, /pa and /vc status buttons (cached check/cross + Next) to
 * those three view screens only. Same lifecycle as ArmouryScreenMixin:
 * rebuilt on screen init (including resize), labels refreshed every frame.
 * Screens whose title is not one of the three get no buttons.
 */
@Mixin(Screen.class)
abstract class ViewedPlayersScreenMixin {
	@Shadow
	protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T child);

	@Shadow
	protected abstract void removeWidget(GuiEventListener child);

	@Unique
	private List<Button> sparethesympathy$viewButtons = null;

	@Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
	private void sparethesympathy$createViewButtons(Minecraft client, int width, int height, CallbackInfo ci) {
		if (this.sparethesympathy$viewButtons != null) {
			for (Button button : this.sparethesympathy$viewButtons) {
				this.removeWidget(button);
			}
			this.sparethesympathy$viewButtons = null;
		}
		if (client.screen == null) {
			return;
		}
		List<Button> created = ViewedPlayersButtons.create(client.screen, width, height);
		if (created.isEmpty()) {
			return;
		}
		this.sparethesympathy$viewButtons = created;
		for (Button button : created) {
			this.addRenderableWidget(button);
		}
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void sparethesympathy$refreshViewButtons(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		if (Minecraft.getInstance().screen == (Object) this && this.sparethesympathy$viewButtons != null) {
			ViewedPlayersButtons.refresh(this.sparethesympathy$viewButtons);
		}
	}
}
