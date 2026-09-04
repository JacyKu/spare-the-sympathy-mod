package sts.mod.client.armoury;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Real screen buttons for the armoury actions (Export Link / Save to
 * Profile / Link Account), added to the open Mechanical Armory screen as
 * proper widgets instead of hand-drawn HUD rectangles. Buttons are rebuilt
 * every time the screen initialises (including on resize, which vanilla
 * handles by re-running init) and their labels/activity refresh each render
 * frame, mirroring the dictionary mod's floating screen button pattern.
 */
public final class ArmouryButtons {
	private static final int BUTTON_WIDTH = 96;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 6;
	private static final int RIGHT_MARGIN = 8;

	private static final Component EXPORT = Component.literal("Export Link");
	private static final Component SAVE = Component.literal("Save to Profile");
	private static final Component LINK = Component.literal("Link Account");
	private static final Component LINKED = Component.literal("Linked");

	private ArmouryButtons() {
	}

	public static List<Button> create(int scaledWidth, int scaledHeight) {
		Minecraft mc = Minecraft.getInstance();
		int x = scaledWidth - BUTTON_WIDTH - RIGHT_MARGIN;
		int startY = scaledHeight / 2 - 40;

		Button export = Button.builder(EXPORT, (button) -> ArmouryTracker.exportToClipboard())
			.size(BUTTON_WIDTH, BUTTON_HEIGHT)
			.bounds(x, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
			.build();
		Button save = Button.builder(SAVE, (button) -> ArmouryTracker.saveBuild())
			.size(BUTTON_WIDTH, BUTTON_HEIGHT)
			.bounds(x, startY + (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
			.build();
		Button link = Button.builder(LINK, (button) -> {
			if (Boolean.TRUE.equals(ArmouryTracker.isLinked())) {
				ArmouryTracker.openAccountPage();
			} else {
				ArmouryTracker.linkAccount();
			}
		})
			.size(BUTTON_WIDTH, BUTTON_HEIGHT)
			.bounds(x, startY + 2 * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
			.build();

		return List.of(export, save, link);
	}

	/**
	 * Refreshes activity + dynamic labels every frame. Called while a
	 * Mechanical Armory screen is open; other screens leave the (soon to be
	 * discarded) widgets alone.
	 */
	public static void refresh(List<Button> buttons) {
		if (buttons == null) {
			return;
		}
		boolean hasLoadout = ArmouryTracker.currentLoadout() != null;
		boolean busy = ArmouryTracker.isBusy();
		for (Button button : buttons) {
			button.active = hasLoadout && !busy;
		}
		if (buttons.size() == 3) {
			Button link = buttons.get(2);
			Boolean linked = ArmouryTracker.isLinked();
			boolean linkedState = Boolean.TRUE.equals(linked);
			link.setMessage(linkedState ? LINKED : LINK);
		}
	}
}
