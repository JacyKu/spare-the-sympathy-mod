package sts.mod.client.armoury;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import sts.mod.SpareTheSympathy;

/**
 * Real screen buttons for the armoury actions (Export Link / Save to
 * Profile / Link Account), added to the open Mechanical Armory screen as
 * proper widgets instead of hand-drawn HUD rectangles. Buttons are rebuilt
 * every time the screen initialises (including on resize, which vanilla
 * handles by re-running init) and their labels/activity refresh each render
 * frame, mirroring the dictionary mod's floating screen button pattern.
 * <p>
 * Holding <b>Ctrl</b> while dragging a button moves the whole stack; the
 * drag offset is applied on top of the default anchor and persisted to
 * {@code config/sparethesympathy/armoury-buttons.json}, so it survives
 * screen re-initialisation and game restarts.
 */
public final class ArmouryButtons {
	private static final int BUTTON_WIDTH = 96;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 6;
	private static final int MARGIN = 8;

	private static final Path POSITION_FILE = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("sparethesympathy")
		.resolve("armoury-buttons.json");

	private static final Component EXPORT = Component.literal("Export Link");
	private static final Component SAVE = Component.literal("Save to Profile");
	private static final Component LINK = Component.literal("Link Account");
	private static final Component LINKED = Component.literal("Linked");

	private static final Component EXPORT_TOOLTIP = Component.literal(
		"Copies a shareable STS builder link for the current loadout to your clipboard.");
	private static final Component SAVE_TOOLTIP = Component.literal(
		"Saves the current loadout to your STS profile (if linked) and copies its short link.");
	private static final Component LINK_TOOLTIP = Component.literal(
		"Links your Minecraft profile to your Discord account so builds save to your profile.");

	// Layout state. anchor* is recomputed from the window on every init,
	// offset* is the player's Ctrl+drag offset on top of it.
	private static int anchorX;
	private static int anchorY;
	private static int offsetX;
	private static int offsetY;
	private static boolean positionLoaded;
	private static boolean positionDirty;
	private static long lastPersistTime;
	private static List<Button> currentButtons;

	// Drag tracking: the buttons follow the cursor exactly by remembering
	// where the drag started and re-deriving the offset from the absolute
	// mouse position on every frame (accumulating deltas drifts).
	private static boolean dragging;
	private static double dragStartMouseX;
	private static double dragStartMouseY;
	private static int dragStartOffsetX;
	private static int dragStartOffsetY;

	private ArmouryButtons() {
	}

	public static List<Button> create(int scaledWidth, int scaledHeight) {
		loadSavedPosition();
		anchorX = scaledWidth - BUTTON_WIDTH - MARGIN;
		anchorY = scaledHeight / 2 - 40;

		Button export = new DraggableArmouryButton(anchorX + offsetX, anchorY + offsetY,
			BUTTON_WIDTH, BUTTON_HEIGHT, EXPORT, (button) -> ArmouryTracker.exportToClipboard());
		Button save = new DraggableArmouryButton(anchorX + offsetX, anchorY + offsetY + (BUTTON_HEIGHT + GAP),
			BUTTON_WIDTH, BUTTON_HEIGHT, SAVE, (button) -> ArmouryTracker.saveBuild());
		Button link = new DraggableArmouryButton(anchorX + offsetX, anchorY + offsetY + 2 * (BUTTON_HEIGHT + GAP),
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
	 * button widgets. Returns true when the drag was consumed.
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
			dragging = false;
			return false;
		}
		if (!dragging) {
			if (!isOverButton(mouseX, mouseY)) {
				return false;
			}
			dragging = true;
			dragStartMouseX = mouseX;
			dragStartMouseY = mouseY;
			dragStartOffsetX = offsetX;
			dragStartOffsetY = offsetY;
		}
		offsetX = dragStartOffsetX + (int) Math.round(mouseX - dragStartMouseX);
		offsetY = dragStartOffsetY + (int) Math.round(mouseY - dragStartMouseY);
		applyOffset();
		positionDirty = true;
		return true;
	}

	/** Persists the final drag position once the mouse button is released. */
	public static void endDrag() {
		dragging = false;
		if (positionDirty) {
			positionDirty = false;
			lastPersistTime = Util.getMillis();
			persist();
		}
	}

	private static boolean isOverButton(double mouseX, double mouseY) {
		for (Button candidate : currentButtons) {
			if (mouseX >= candidate.getX() && mouseX <= candidate.getX() + candidate.getWidth()
				&& mouseY >= candidate.getY() && mouseY <= candidate.getY() + candidate.getHeight()) {
				return true;
			}
		}
		return false;
	}

	/** Moves the whole button stack by the accumulated drag offset. */
	private static void applyOffset() {
		if (currentButtons == null) {
			return;
		}
		for (int i = 0; i < currentButtons.size(); i++) {
			Button button = currentButtons.get(i);
			button.setX(anchorX + offsetX);
			button.setY(anchorY + offsetY + i * (BUTTON_HEIGHT + GAP));
		}
	}

	private static void loadSavedPosition() {
		if (positionLoaded) {
			return;
		}
		positionLoaded = true;
		try {
			if (Files.exists(POSITION_FILE)) {
				JsonObject json = JsonParser.parseString(Files.readString(POSITION_FILE, StandardCharsets.UTF_8))
					.getAsJsonObject();
				offsetX = json.has("offsetX") ? json.get("offsetX").getAsInt() : 0;
				offsetY = json.has("offsetY") ? json.get("offsetY").getAsInt() : 0;
			}
		} catch (Exception e) {
			SpareTheSympathy.LOGGER.warn("Could not read armoury button position, using defaults", e);
		}
	}

	/** Persists the drag offset so it survives restarts. */
	private static void persist() {
		try {
			Files.createDirectories(POSITION_FILE.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("offsetX", offsetX);
			json.addProperty("offsetY", offsetY);
			Files.writeString(POSITION_FILE, json.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			SpareTheSympathy.LOGGER.warn("Could not save armoury button position", e);
		}
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