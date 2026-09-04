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
import sts.mod.client.armoury.ArmouryButtons;
import sts.mod.client.armoury.ArmouryLoadoutReader;

/**
 * Adds the STS armoury action buttons (Export Link / Save to Profile / Link
 * Account) to the open Mechanical Armory container screen as real screen
 * widgets, instead of hand-drawn HUD rectangles + manual click routing.
 * <p>
 * The buttons are created whenever a Mechanical Armory screen initialises
 * (vanilla re-runs init on resize too, so they reposition themselves) and
 * their active state is refreshed on every rendered frame - the export/save
 * buttons only enable once a loadout is actually on screen. Old button sets
 * are removed from the screen before a new set is added, so re-initialising
 * the screen (e.g. on resize) never leaves sticky duplicates behind.
 */
@Mixin(Screen.class)
abstract class ArmouryScreenMixin {
	@Shadow
	protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T child);

	@Shadow
	protected abstract void removeWidget(GuiEventListener child);

	@Unique
	private List<Button> sparethesympathy$armouryButtons = null;

	@Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
	private void sparethesympathy$createArmouryButtons(Minecraft client, int width, int height, CallbackInfo ci) {
		if (this.sparethesympathy$armouryButtons != null) {
			for (Button button : this.sparethesympathy$armouryButtons) {
				this.removeWidget(button);
			}
			this.sparethesympathy$armouryButtons = null;
		}
		if (client.screen == null || !ArmouryLoadoutReader.isArmouryScreen(client.screen)) {
			return;
		}
		this.sparethesympathy$armouryButtons = ArmouryButtons.create(width, height);
		for (Button button : this.sparethesympathy$armouryButtons) {
			this.addRenderableWidget(button);
		}
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void sparethesympathy$refreshArmouryButtons(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		Screen self = (Screen) (Object) this;
		if (client.screen == self && this.sparethesympathy$armouryButtons != null) {
			ArmouryButtons.refresh(this.sparethesympathy$armouryButtons);
		}
	}
}