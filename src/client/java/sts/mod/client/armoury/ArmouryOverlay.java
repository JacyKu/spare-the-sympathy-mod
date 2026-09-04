package sts.mod.client.armoury;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the transient feedback toast (e.g. "Build saved ...") while the
 * Mechanical Armory screen is open. The armoury action buttons themselves
 * are real screen widgets now (see ArmouryButtons + ArmouryScreenMixin), so
 * this overlay only handles the ephemeral status message.
 */
public final class ArmouryOverlay {
	private static final int BG = 0xE61B1B1B;

	private ArmouryOverlay() {
	}

	/**
	 * Called from ContainerScreenMixin at the RETURN of the container render.
	 * <p>
	 * GuiGraphics buffers quads and flushes them per render type, in the order
	 * each type was first used - the overlay's fills/text reuse render types
	 * the screen already started, so they would flush BEFORE the item buffers
	 * and end up under the items. The fix is to flush the screen's content
	 * first, then draw, then flush again while the depth test is disabled.
	 */
	public static void render(GuiGraphics graphics, double mouseX, double mouseY) {
		if (!ArmouryTracker.isArmouryOpen()) {
			return;
		}
		String feedback = ArmouryTracker.feedback();
		if (feedback == null || feedback.isEmpty()) {
			return;
		}
		graphics.flush();
		com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
		try {
			Minecraft mc = Minecraft.getInstance();
			Window window = mc.getWindow();
			int width = window.getGuiScaledWidth();
			int height = window.getGuiScaledHeight();
			int textWidth = mc.font.width(feedback);
			int fx = Math.max(4, width - textWidth - 16);
			graphics.fill(fx - 4, height - 30, width - 4, height - 12, BG);
			graphics.drawString(mc.font, feedback, fx, height - 26, 0xFFFFFFFF);
		} finally {
			graphics.flush();
			com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
		}
	}
}
