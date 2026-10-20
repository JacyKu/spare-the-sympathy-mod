package sts.mod.client.view;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import sts.mod.api.StsConfig;
import sts.mod.client.ui.BuildNameScreen;
import sts.mod.config.ViewedButton;

/**
 * Buttons for the /ps, /pa and /vc view GUIs:
 * <ul>
 *     <li>one per view showing whether that part of the player's build has
 *     been cached (green check) or not (red cross); clicking opens it. The
 *     abilities button instead shows 0/2-2/2 progress (class abilities and
 *     the selected spec's page) and its tooltip says whether a spec is
 *     selected while its page is still missing;</li>
 *     <li>Next opens the next view for the same player;</li>
 *     <li>Upload / Export act on the cached build (save to the account /
 *     generate a shareable link) and only appear once something has been
 *     cached.</li>
 * </ul>
 * They are only created for those three screens (the mixin asks, the title
 * decides) and never close the current GUI - the view commands open the next
 * one server-side.
 * <p>
 * Like the armoury buttons, every button keeps its default layout spot as its
 * anchor and can be Ctrl+dragged away from it; the per-button offsets are
 * stored in the autoconfig (see {@link StsConfig#viewedButtonX}), so the
 * config screen and the in-game drag edit the same values.
 */
public final class ViewedPlayersButtons {
	private static final int STATUS_WIDTH = 46;
	private static final int NEXT_WIDTH = 84;
	private static final int ACTION_WIDTH = 84;
	private static final int HEIGHT = 20;
	private static final int GAP = 4;
	private static final ViewedPlayers.Kind[] KINDS = {
		ViewedPlayers.Kind.STATS,
		ViewedPlayers.Kind.ABILITIES,
		ViewedPlayers.Kind.CHARMS,
	};

	// Button order matches create() below and the ViewedButton enum.
	private static final ViewedButton[] BUTTONS = ViewedButton.values();
	private static final int[] ANCHOR_X = new int[BUTTONS.length];
	private static final int[] ANCHOR_Y = new int[BUTTONS.length];
	private static final int[] OFFSET_X = new int[BUTTONS.length];
	private static final int[] OFFSET_Y = new int[BUTTONS.length];
	private static boolean positionDirty;

	// The player the current buttons belong to, captured at creation (the tick
	// tracker may not have run yet when the screen initialises).
	private static String player;

	// Drag state; the buttons only drag while their own screen is open.
	private static Screen buttonScreen;
	private static List<Button> currentButtons;
	private static int draggingIndex = -1;
	private static double dragStartMouseX;
	private static double dragStartMouseY;
	private static int dragStartOffsetX;
	private static int dragStartOffsetY;

	private ViewedPlayersButtons() {
	}

	public static List<Button> create(Screen screen, int width, int height) {
		String title = screen instanceof AbstractContainerScreen<?> container
			? container.getTitle().getString()
			: "";
		ViewedPlayers.Kind kind = ViewedPlayersTracker.kindForTitle(title);
		String resolvedPlayer = ViewedPlayersTracker.playerForScreen(screen);
		if (resolvedPlayer == null && screen == buttonScreen && player != null) {
			// Returning to the same view screen (e.g. from the naming prompt)
			// after the command target aged out: keep the captured player.
			resolvedPlayer = player;
		}
		player = resolvedPlayer;
		if (kind == null || player == null) {
			return List.of();
		}

		int statusTotal = 3 * STATUS_WIDTH + NEXT_WIDTH + 3 * GAP;
		int statusX = Math.max(4, (width - statusTotal) / 2);
		int y = buttonY(screen, height);
		int actionTotal = 2 * ACTION_WIDTH + GAP;
		int actionX = Math.max(4, (width - actionTotal) / 2);
		int actionY = y + HEIGHT + GAP;

		// Default layout: the status row (three views + Next) on top, the
		// Upload / Export actions underneath. These stay the anchors; the
		// config offsets are applied on top.
		ANCHOR_X[0] = statusX;
		ANCHOR_Y[0] = y;
		ANCHOR_X[1] = statusX + STATUS_WIDTH + GAP;
		ANCHOR_Y[1] = y;
		ANCHOR_X[2] = statusX + 2 * (STATUS_WIDTH + GAP);
		ANCHOR_Y[2] = y;
		ANCHOR_X[3] = statusX + 3 * (STATUS_WIDTH + GAP);
		ANCHOR_Y[3] = y;
		ANCHOR_X[4] = actionX;
		ANCHOR_Y[4] = actionY;
		ANCHOR_X[5] = actionX + ACTION_WIDTH + GAP;
		ANCHOR_Y[5] = actionY;
		for (int i = 0; i < BUTTONS.length; i++) {
			OFFSET_X[i] = StsConfig.viewedButtonX(BUTTONS[i]);
			OFFSET_Y[i] = StsConfig.viewedButtonY(BUTTONS[i]);
		}

		Button ps = new DraggableViewedButton(positionX(0), positionY(0), STATUS_WIDTH, HEIGHT,
			Component.literal("PS"), b -> ViewedPlayersTracker.openView("ps", player));
		Button pa = new DraggableViewedButton(positionX(1), positionY(1), STATUS_WIDTH, HEIGHT,
			Component.literal("PA"), b -> ViewedPlayersTracker.openView("pa", player));
		Button vc = new DraggableViewedButton(positionX(2), positionY(2), STATUS_WIDTH, HEIGHT,
			Component.literal("VC"), b -> ViewedPlayersTracker.openView("vc", player));
		Button next = new DraggableViewedButton(positionX(3), positionY(3), NEXT_WIDTH, HEIGHT,
			Component.literal("Next"), b -> openNext());
		// Build actions live on a second row; they stay hidden until the
		// player has something cached. Both open the naming prompt first
		// (Shift+click skips it and uses the default name).
		Button upload = new DraggableViewedButton(positionX(4), positionY(4), ACTION_WIDTH, HEIGHT,
			Component.literal("Upload"),
			b -> openNamingOrRun(name -> ViewedPlayers.upload(player, name)));
		Button export = new DraggableViewedButton(positionX(5), positionY(5), ACTION_WIDTH, HEIGHT,
			Component.literal("Export"),
			b -> openNamingOrRun(name -> ViewedPlayers.export(player, name)));

		currentButtons = List.of(ps, pa, vc, next, upload, export);
		buttonScreen = screen;
		draggingIndex = -1;
		return currentButtons;
	}

	/**
	 * The naming prompt, unless Shift is held: Shift+clicking Upload/Export
	 * runs the action straight away with the default name (the viewed player's
	 * name, which is also the prompt's prefill).
	 */
	private static void openNamingOrRun(java.util.function.Consumer<String> action) {
		if (Screen.hasShiftDown()) {
			action.accept(player);
		} else {
			BuildNameScreen.open(player, action);
		}
	}

	/** Refreshes labels, tooltips and the cached-only Upload/Export buttons. */
	public static void refresh(List<Button> buttons) {
		if (buttons == null || buttons.size() != 6 || player == null) {
			return;
		}
		for (int i = 0; i < KINDS.length; i++) {
			ViewedPlayers.Kind view = KINDS[i];
			String tag = ViewedPlayersTracker.tagFor(view).toUpperCase(Locale.ROOT);
			Button button = buttons.get(i);
			if (view == ViewedPlayers.Kind.ABILITIES) {
				// The /pa view has two parts: the class abilities and the
				// (selected) spec's page. Show the progress; while the spec
				// page is missing, the tooltip tells whether a spec is
				// selected so it can be visited too.
				int progress = ViewedPlayersTracker.abilitiesProgress(player);
				button.setMessage(
					Component.literal(tag + " " + progress + "/2")
						.withStyle(
							progress >= 2
								? ChatFormatting.GREEN
								: progress == 1
									? ChatFormatting.YELLOW
									: ChatFormatting.RED
						)
				);
				button.setTooltip(Tooltip.create(Component.literal(
					abilitiesTooltip(progress) + " (/" + ViewedPlayersTracker.tagFor(view) + " " + player + ")"
				)));
				continue;
			}
			boolean viewed = ViewedPlayersTracker.isViewed(player, view);
			button.setMessage(
				Component.literal(tag + (viewed ? " \u2713" : " \u2717"))
					.withStyle(viewed ? ChatFormatting.GREEN : ChatFormatting.RED)
			);
			button.setTooltip(Tooltip.create(Component.literal(
				(viewed ? "Cached - click to open again" : "Not cached yet - click to open")
					+ " (/" + ViewedPlayersTracker.tagFor(view) + " " + player + ")"
			)));
		}
		ViewedPlayers.Kind next = ViewedPlayersTracker.nextKind();
		Button nextButton = buttons.get(3);
		nextButton.setMessage(
			Component.literal("Next: " + ViewedPlayersTracker.tagFor(next).toUpperCase(Locale.ROOT))
		);
		nextButton.setTooltip(Tooltip.create(Component.literal(
			"Open /" + ViewedPlayersTracker.tagFor(next) + " " + player
		)));

		boolean cached = ViewedPlayersTracker.hasCachedData(player);
		String emptyHint = "Nothing cached for " + player + " yet - view them with /ps, /pa and /vc first";
		Button upload = buttons.get(4);
		upload.visible = cached;
		upload.active = cached;
		upload.setTooltip(Tooltip.create(Component.literal(
			cached
				? "Upload " + player + "'s cached build to your account (saved to your Discord profile). Shift+click skips the name prompt."
				: emptyHint
		)));
		Button export = buttons.get(5);
		export.visible = cached;
		export.active = cached;
		export.setTooltip(Tooltip.create(Component.literal(
			cached
				? "Generate a shareable link for " + player + "'s cached build (not attached to your account). Shift+click skips the name prompt."
				: emptyHint
		)));
	}

	/**
	 * Tooltip for the /pa button at a given 0-2 progress. While the spec page
	 * is still missing, a selected spec is called out - its page has to be
	 * visited too before the build carries the spec skills.
	 */
	private static String abilitiesTooltip(int progress) {
		if (progress >= 2) {
			return "Cached - click to open again";
		}
		if (progress <= 0) {
			return "Not cached yet - click to open";
		}
		boolean specSeen = ViewedPlayersTracker.specSeen(player);
		String spec = ViewedPlayersTracker.viewedSpec(player);
		if (!specSeen && spec != null && !spec.isEmpty()) {
			return "Class abilities cached and " + spec + " is selected - open its page to cache the spec skills";
		}
		if (!specSeen) {
			return "Class abilities cached - the spec page has not been viewed yet";
		}
		return "Spec cached - the class ability pages are still missing";
	}

	private static void openNext() {
		ViewedPlayers.Kind next = ViewedPlayersTracker.nextKind();
		ViewedPlayersTracker.openView(ViewedPlayersTracker.tagFor(next), player);
	}

	/** Below the inventory window when there is room, above it otherwise. */
	private static int buttonY(Screen screen, int height) {
		int top = 0;
		int guiHeight = height;
		if (screen instanceof AbstractContainerScreen<?> container) {
			top = container.topPos;
			guiHeight = container.imageHeight;
		}
		int rows = 2 * HEIGHT + GAP;
		int below = top + guiHeight + 6;
		if (below + rows <= height - 2) {
			return below;
		}
		int above = top - rows - 6;
		if (above >= 2) {
			return above;
		}
		return Math.max(2, height - rows - 2);
	}

	/**
	 * Whether the cursor is over one of the current view buttons. Used by the
	 * ReplayMod compatibility mixin to let the vanilla mouse-down through so
	 * ReplayMod's GUI wrapper still starts a drag.
	 */
	public static boolean isOverButton(double mouseX, double mouseY) {
		if (currentButtons == null || currentButtons.isEmpty()) {
			return false;
		}
		if (buttonScreen == null || Minecraft.getInstance().screen != buttonScreen) {
			return false;
		}
		return indexAt(mouseX, mouseY) >= 0;
	}

	/**
	 * Handles a Ctrl+drag that starts over one of the view buttons. Called
	 * from a mixin on {@code AbstractContainerScreen.mouseDragged} (container
	 * screens swallow every drag and never forward it to widgets). Only the
	 * button under the cursor moves. Returns true when the drag was consumed.
	 */
	public static boolean dragIfOverButton(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		if (currentButtons == null || currentButtons.isEmpty()) {
			return false;
		}
		if (Minecraft.getInstance().screen != buttonScreen) {
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
			StsConfig.setViewedButtonPosition(BUTTONS[index], OFFSET_X[index], OFFSET_Y[index]);
			// Keep the in-memory offsets in the config's clamped range so the
			// next drag starts from the position that was actually saved.
			OFFSET_X[index] = StsConfig.viewedButtonX(BUTTONS[index]);
			OFFSET_Y[index] = StsConfig.viewedButtonY(BUTTONS[index]);
			applyPosition(index);
		}
	}

	private static int indexAt(double mouseX, double mouseY) {
		for (int i = 0; i < currentButtons.size(); i++) {
			Button candidate = currentButtons.get(i);
			if (!candidate.visible) {
				continue;
			}
			if (mouseX >= candidate.getX() && mouseX <= candidate.getX() + candidate.getWidth()
				&& mouseY >= candidate.getY() && mouseY <= candidate.getY() + candidate.getHeight()) {
				return i;
			}
		}
		return -1;
	}

	private static int positionX(int index) {
		return ANCHOR_X[index] + OFFSET_X[index];
	}

	private static int positionY(int index) {
		return ANCHOR_Y[index] + OFFSET_Y[index];
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
	private static final class DraggableViewedButton extends Button {
		private DraggableViewedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
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
