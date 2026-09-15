package sts.mod.client.armoury;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import sts.mod.api.StsConfig;
import sts.mod.config.ArmouryButton;
import sts.mod.config.ArmourySide;

/**
 * Real screen buttons for the armoury actions (Export Link / Save to
 * Profile / Link Account), added to the open Mechanical Armory screen as
 * proper widgets. Buttons are rebuilt
 * every time the screen initialises (including on resize, which vanilla
 * handles by re-running init) and their labels/activity refresh each render
 * frame, mirroring the dictionary mod's floating screen button pattern.
 * <p>
 * Each button anchors to the left or right edge of the Mechanical Armory
 * window (configurable per button) with its own x/y offset. Holding
 * <b>Ctrl</b> while dragging a button moves just that button and writes its
 * new offsets back to the mod's autoconfig, so the config screen (Mod Menu ->
 * Spare the Sympathy) and the in-game drag edit the same values.
 */
public final class ArmouryButtons {
	private static final int BUTTON_WIDTH = 96;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 6;
	private static final int MARGIN = 8;
	private static final ArmouryButton[] BUTTONS = ArmouryButton.values();

	private static final Component EXPORT = Component.translatable("sts.armoury.export");
	private static final Component SAVE = Component.translatable("sts.armoury.save");
	private static final Component LINK = Component.translatable("sts.armoury.link");
	private static final Component LINKED = Component.translatable("sts.armoury.linked");

	private static final Component EXPORT_TOOLTIP = Component.translatable("sts.armoury.export.tooltip");
	private static final Component SAVE_TOOLTIP = Component.translatable("sts.armoury.save.tooltip");
	private static final Component LINK_TOOLTIP = Component.translatable("sts.armoury.link.tooltip");

	// Layout state. The anchor* arrays are recomputed from the inventory window
	// on every init; OFFSET_* is the per-button config position on top of it.
	private static int anchorY;
	private static final int[] ANCHOR_X = new int[BUTTONS.length];
	private static final int[] OFFSET_X = new int[BUTTONS.length];
	private static final int[] OFFSET_Y = new int[BUTTONS.length];
	private static boolean positionDirty;
	private static List<Button> currentButtons;

	// Drag tracking: the dragged button follows the cursor exactly by
	// remembering where the drag started and re-deriving its offset from the
	// absolute mouse position on every frame (accumulating deltas drifts).
	private static int draggingIndex = -1;
	private static double dragStartMouseX;
	private static double dragStartMouseY;
	private static int dragStartOffsetX;
	private static int dragStartOffsetY;

	private ArmouryButtons() {
	}

	public static List<Button> create(Screen screen, int scaledWidth, int scaledHeight) {
		// Anchor to the inventory window when possible (the Mechanical Armory
		// is a container screen); fall back to the whole screen otherwise.
		int left = 0;
		int top = 0;
		int width = scaledWidth;
		int height = scaledHeight;
		if (screen instanceof AbstractContainerScreen<?> container) {
			left = container.leftPos;
			top = container.topPos;
			width = container.imageWidth;
			height = container.imageHeight;
		}
		// Anchors are strictly relative to the inventory window (no clamping to
		// the screen): the buttons then keep the same position next to the
		// inventory when the window is resized, and the player's offsets stay
		// meaningful at any size.
		anchorY = top + (height - stackHeight()) / 2;

		for (int i = 0; i < BUTTONS.length; i++) {
			ArmouryButton id = BUTTONS[i];
			ANCHOR_X[i] = StsConfig.armouryButtonSide(id) == ArmourySide.LEFT
				? left - MARGIN - BUTTON_WIDTH
				: left + width + MARGIN;
			OFFSET_X[i] = StsConfig.armouryButtonX(id);
			OFFSET_Y[i] = StsConfig.armouryButtonY(id);
		}

		Button export = new DraggableArmouryButton(positionX(0), positionY(0),
			BUTTON_WIDTH, BUTTON_HEIGHT, EXPORT, (button) -> ArmouryTracker.exportToClipboard());
		Button save = new DraggableArmouryButton(positionX(1), positionY(1),
			BUTTON_WIDTH, BUTTON_HEIGHT, SAVE, (button) -> ArmouryTracker.saveBuild());
		Button link = new DraggableArmouryButton(positionX(2), positionY(2),
			BUTTON_WIDTH, BUTTON_HEIGHT, LINK, (button) -> {
				if (Boolean.TRUE.equals(ArmouryTracker.isLinked())) {
					ArmouryTracker.openAccountPage();
				} else {
					ArmouryTracker.linkAccount();
				}
			});

		export.setTooltip(Tooltip.create(EXPORT_TOOLTIP));
		save.setTooltip(Tooltip.create(SAVE_TOOLTIP));
		link.setTooltip(Tooltip.create(LINK_TOOLTIP));

		currentButtons = List.of(export, save, link);
		return currentButtons;
	}

	/**
	 * Refreshes activity + dynamic labels every frame. Position is left to the
	 * player's Ctrl+drag (see {@link DraggableArmouryButton}), never reset.
	 */
	public static void refresh(List<Button> buttons) {
		if (buttons == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean hasLoadout = ArmouryTracker.currentLoadout() != null;
		boolean onLoadoutView = ArmouryLoadoutReader.isLoadoutView(mc.screen);
		boolean busy = ArmouryTracker.isBusy();
		for (Button button : buttons) {
			button.active = onLoadoutView && hasLoadout && !busy;
		}
		if (buttons.size() == 3) {
			Button link = buttons.get(2);
			Boolean linked = ArmouryTracker.isLinked();
			boolean linkedState = Boolean.TRUE.equals(linked);
			link.setMessage(linkedState ? LINKED : LINK);
		}
	}

	/**
	 * Handles a Ctrl+drag that starts over one of the armoury buttons. Called
	 * from a mixin on {@code AbstractContainerScreen.mouseDragged} because the
	 * container screen swallows every drag event and never forwards it to the
	 * button widgets. Only the button under the cursor moves. Returns true when
	 * the drag was consumed.
	 */
	public static boolean dragIfOverButton(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		if (currentButtons == null || currentButtons.isEmpty()) {
			return false;
		}
		if (!ArmouryLoadoutReader.isArmouryScreen(Minecraft.getInstance().screen)) {
			return false;
		}
		if (!Screen.hasControlDown()) {
			draggingIndex = -1;
			return false;
		}
		if (draggingIndex < 0) {
			draggingIndex = indexAt(mouseX, mouseY);
			if (draggingIndex < 0) {
				return false;
			}
			dragStartMouseX = mouseX;
			dragStartMouseY = mouseY;
			dragStartOffsetX = OFFSET_X[draggingIndex];
			dragStartOffsetY = OFFSET_Y[draggingIndex];
		}
		OFFSET_X[draggingIndex] = dragStartOffsetX + (int) Math.round(mouseX - dragStartMouseX);
		OFFSET_Y[draggingIndex] = dragStartOffsetY + (int) Math.round(mouseY - dragStartMouseY);
		applyPosition(draggingIndex);
		positionDirty = true;
		return true;
	}

	/** Persists the dragged button's position once the mouse is released. */
	public static void endDrag() {
		if (draggingIndex < 0) {
			return;
		}
		int index = draggingIndex;
		draggingIndex = -1;
		if (positionDirty) {
			positionDirty = false;
			StsConfig.setArmouryButtonPosition(BUTTONS[index], OFFSET_X[index], OFFSET_Y[index]);
			// Keep the in-memory offsets in the config's clamped range so the
			// next drag starts from the position that was actually saved.
			OFFSET_X[index] = StsConfig.armouryButtonX(BUTTONS[index]);
			OFFSET_Y[index] = StsConfig.armouryButtonY(BUTTONS[index]);
			applyPosition(index);
		}
	}

	private static int indexAt(double mouseX, double mouseY) {
		for (int i = 0; i < currentButtons.size(); i++) {
			Button candidate = currentButtons.get(i);
			if (mouseX >= candidate.getX() && mouseX <= candidate.getX() + candidate.getWidth()
				&& mouseY >= candidate.getY() && mouseY <= candidate.getY() + candidate.getHeight()) {
				return i;
			}
		}
		return -1;
	}

	private static int stackHeight() {
		return BUTTONS.length * BUTTON_HEIGHT + (BUTTONS.length - 1) * GAP;
	}

	private static int positionX(int index) {
		return ANCHOR_X[index] + OFFSET_X[index];
	}

	private static int positionY(int index) {
		return anchorY + OFFSET_Y[index];
	}

	private static void applyPosition(int index) {
		if (currentButtons == null || index < 0 || index >= currentButtons.size()) {
			return;
		}
		Button button = currentButtons.get(index);
		button.setX(positionX(index));
		button.setY(positionY(index));
	}

	/**
	 * A button whose click action only fires without Ctrl; holding Ctrl turns
	 * the press into a drag (handled at the screen level, see
	 * {@link #dragIfOverButton}).
	 */
	private static final class DraggableArmouryButton extends Button {
		private DraggableArmouryButton(int x, int y, int width, int height, Component message, OnPress onPress) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			if (Screen.hasControlDown()) {
				return;
			}
			super.onClick(mouseX, mouseY);
		}
	}
}
