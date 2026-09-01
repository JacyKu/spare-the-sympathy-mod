package sts.mod.client.armoury;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the armoury action buttons down the right edge of the screen and
 * routes clicks to the tracker actions. Drawn as HUD content on top of the
 * Mechanical Armory GUI; clicks are intercepted by the MouseHandler mixin.
 */
public final class ArmouryOverlay {
	private static final int BUTTON_WIDTH = 96;
	private static final int BUTTON_HEIGHT = 18;
	private static final int GAP = 6;
	private static final int RIGHT_MARGIN = 8;
	private static final int BG = 0xE61B1B1B;
	private static final int BG_HOVER = 0xE6333333;
	private static final int BORDER = 0xFF9C59D1;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFAAAAAA;
	private static final int TOOLTIP_BG = 0xF0101010;
	private static final int TOOLTIP_BORDER = 0xFF555555;

	private static final String[] LABELS = { "Export Link", "Save to Profile", "Link Account" };

	private ArmouryOverlay() {
	}

	private enum Button {
		EXPORT(0),
		SAVE(1),
		LINK(2);

		final int index;

		Button(int index) {
			this.index = index;
		}
	}

	/**
	 * Renders the armoury action buttons down the right edge of the screen.
	 * Called from ContainerScreenMixin at the RETURN of the container render.
	 * <p>
	 * GuiGraphics buffers quads and flushes them per render type, in the order
	 * each type was first used - the overlay's fills/text reuse render types
	 * the screen already started, so they would flush BEFORE the item buffers
	 * and end up under the items. The fix is to flush the screen's content
	 * first, then draw, then flush again while the depth test is disabled.
	 */
	public static void render(GuiGraphics graphics, double mouseX, double mouseY) {
		if (!ArmouryTracker.isArmouryOpen() || ArmouryTracker.currentLoadout() == null) {
			return;
		}
		graphics.flush();
		com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
		try {
			renderImpl(graphics, mouseX, mouseY);
		} finally {
			graphics.flush();
			com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
		}
	}

	private static void renderImpl(GuiGraphics graphics, double mouseX, double mouseY) {
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		int width = window.getGuiScaledWidth();
		int height = window.getGuiScaledHeight();

		int x = width - BUTTON_WIDTH - RIGHT_MARGIN;
		int[] ys = new int[3];
		for (int i = 0; i < 3; i++) {
			ys[i] = height / 2 - 40 + i * (BUTTON_HEIGHT + GAP);
		}

		int hovered = hoveredButton(mouseX, mouseY, x, ys);

		for (int i = 0; i < 3; i++) {
			String label = LABELS[i];
			int color = TEXT;
			if (i == Button.SAVE.index && !Boolean.TRUE.equals(ArmouryTracker.isLinked())) {
				color = MUTED;
			}
			if (i == Button.LINK.index && Boolean.TRUE.equals(ArmouryTracker.isLinked())) {
				label = "Linked";
				color = 0xFF6BCF7F;
			}
			drawButton(graphics, x, ys[i], label, color, hovered == i);
		}

		// Tooltip for the hovered button.
		if (hovered >= 0) {
			String[] lines = tooltip(Button.values()[hovered]);
			drawTooltip(graphics, x, ys[hovered], lines);
		}

		// Transient feedback (e.g. "Build saved ...").
		String feedback = ArmouryTracker.feedback();
		if (feedback != null && !feedback.isEmpty()) {
			int textWidth = mc.font.width(feedback);
			int fx = Math.max(4, width - textWidth - 16);
			graphics.fill(fx - 4, height - 30, width - 4, height - 12, BG);
			graphics.drawString(mc.font, feedback, fx, height - 26, TEXT);
		}
	}

	/** True when the click was consumed by a button. */
	public static boolean handleClick(double mouseX, double mouseY) {
		if (!ArmouryTracker.isArmouryOpen() || ArmouryTracker.currentLoadout() == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		int width = window.getGuiScaledWidth();
		int x = width - BUTTON_WIDTH - RIGHT_MARGIN;
		int[] ys = new int[3];
		for (int i = 0; i < 3; i++) {
			ys[i] = window.getGuiScaledHeight() / 2 - 40 + i * (BUTTON_HEIGHT + GAP);
		}
		int hovered = hoveredButton(mouseX, mouseY, x, ys);
		if (hovered < 0 || ArmouryTracker.isBusy()) {
			return false;
		}
		switch (Button.values()[hovered]) {
			case EXPORT -> ArmouryTracker.exportToClipboard();
			case SAVE -> ArmouryTracker.saveBuild();
			case LINK -> {
				if (Boolean.TRUE.equals(ArmouryTracker.isLinked())) {
					ArmouryTracker.openAccountPage();
				} else {
					ArmouryTracker.linkAccount();
				}
			}
		}
		return true;
	}

	private static int hoveredButton(double mouseX, double mouseY, int x, int[] ys) {
		for (int i = 0; i < 3; i++) {
			if (mouseX >= x && mouseX <= x + BUTTON_WIDTH && mouseY >= ys[i] && mouseY <= ys[i] + BUTTON_HEIGHT) {
				return i;
			}
		}
		return -1;
	}

	private static void drawButton(GuiGraphics graphics, int x, int y, String label, int color, boolean hovered) {
		graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, hovered ? BG_HOVER : BG);
		graphics.fill(x, y, x + BUTTON_WIDTH, y + 1, BORDER);
		graphics.fill(x, y + BUTTON_HEIGHT - 1, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, BORDER);
		graphics.fill(x, y, x + 1, y + BUTTON_HEIGHT, BORDER);
		graphics.fill(x + BUTTON_WIDTH - 1, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, BORDER);
		Minecraft mc = Minecraft.getInstance();
		graphics.drawString(mc.font, label, x + 7, y + 5, color);
	}

	private static String[] tooltip(Button button) {
		Boolean linked = ArmouryTracker.isLinked();
		return switch (button) {
			case EXPORT -> new String[] { "Copy the loadout link to your clipboard.", "Works without an account." };
			case SAVE -> linked == null
				? new String[] { "Save this loadout to your STS profile.", "Checking your Discord link..." }
				: linked
					? new String[] { "Save this loadout to your STS profile", "and copy the link." }
					: new String[] { "Save to profile requires a linked Discord account.", "Not linked yet: the link is copied instead.", "Run /stsmod link in chat to connect." };
			case LINK -> linked == null
				? new String[] { "Link your Discord account to this Minecraft profile.", "Checking..." }
				: linked
					? new String[] { "This Minecraft profile is linked to your", "Discord account. Open your STS account page." }
					: new String[] { "Link your Discord account to this Minecraft profile", "so builds save to your profile." };
		};
	}

	private static void drawTooltip(GuiGraphics graphics, int x, int y, String[] lines) {
		Minecraft mc = Minecraft.getInstance();
		int maxWidth = 0;
		for (String line : lines) {
			maxWidth = Math.max(maxWidth, mc.font.width(line));
		}
		int boxX = x - maxWidth - 14;
		int boxY = y;
		int boxW = maxWidth + 8;
		int boxH = lines.length * 9 + 6;
		if (boxX < 2) {
			boxX = x + BUTTON_WIDTH + 6;
		}
		graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, TOOLTIP_BG);
		graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, TOOLTIP_BORDER);
		graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, TOOLTIP_BORDER);
		graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, TOOLTIP_BORDER);
		graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, TOOLTIP_BORDER);
		for (int i = 0; i < lines.length; i++) {
			graphics.drawString(mc.font, lines[i], boxX + 4, boxY + 4 + i * 9, 0xFFFFFFFF);
		}
	}
}
