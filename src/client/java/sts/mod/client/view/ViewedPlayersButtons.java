package sts.mod.client.view;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Buttons for the /ps, /pa and /vc view GUIs:
 * <ul>
 *     <li>one per view showing whether that part of the player's build has
 *     been cached (green check) or not (red cross); clicking opens it;</li>
 *     <li>Next opens the next view for the same player;</li>
 *     <li>Upload / Export act on the cached build (save to the account /
 *     generate a shareable link) and only appear once something has been
 *     cached.</li>
 * </ul>
 * They are only created for those three screens (the mixin asks, the title
 * decides) and never close the current GUI - the view commands open the next
 * one server-side.
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

	// The player the current buttons belong to, captured at creation (the tick
	// tracker may not have run yet when the screen initialises).
	private static String player;

	private ViewedPlayersButtons() {
	}

	public static List<Button> create(Screen screen, int width, int height) {
		String title = screen instanceof AbstractContainerScreen<?> container
			? container.getTitle().getString()
			: "";
		ViewedPlayers.Kind kind = ViewedPlayersTracker.kindForTitle(title);
		player = ViewedPlayersTracker.playerForScreen(screen);
		if (kind == null || player == null) {
			return List.of();
		}

		int statusTotal = 3 * STATUS_WIDTH + NEXT_WIDTH + 3 * GAP;
		int statusX = Math.max(4, (width - statusTotal) / 2);
		int y = buttonY(screen, height);

		Button ps = statusButton(ViewedPlayers.Kind.STATS, statusX, y);
		Button pa = statusButton(ViewedPlayers.Kind.ABILITIES, statusX + STATUS_WIDTH + GAP, y);
		Button vc = statusButton(ViewedPlayers.Kind.CHARMS, statusX + 2 * (STATUS_WIDTH + GAP), y);
		Button next = Button.builder(Component.literal("Next"), b -> openNext())
			.bounds(statusX + 3 * (STATUS_WIDTH + GAP), y, NEXT_WIDTH, HEIGHT)
			.build();

		// Build actions live on a second row; they stay hidden until the
		// player has something cached.
		int actionTotal = 2 * ACTION_WIDTH + GAP;
		int actionX = Math.max(4, (width - actionTotal) / 2);
		int actionY = y + HEIGHT + GAP;
		Button upload = Button.builder(Component.literal("Upload"), b -> ViewedPlayers.upload(player, player))
			.bounds(actionX, actionY, ACTION_WIDTH, HEIGHT)
			.build();
		Button export = Button.builder(
			Component.literal("Export"),
			b -> ViewedPlayers.export(player, player)
		)
			.bounds(actionX + ACTION_WIDTH + GAP, actionY, ACTION_WIDTH, HEIGHT)
			.build();

		return List.of(ps, pa, vc, next, upload, export);
	}

	/** Refreshes labels, tooltips and the cached-only Upload/Export buttons. */
	public static void refresh(List<Button> buttons) {
		if (buttons == null || buttons.size() != 6 || player == null) {
			return;
		}
		for (int i = 0; i < KINDS.length; i++) {
			ViewedPlayers.Kind view = KINDS[i];
			boolean viewed = ViewedPlayersTracker.isViewed(player, view);
			String tag = ViewedPlayersTracker.tagFor(view).toUpperCase(Locale.ROOT);
			Button button = buttons.get(i);
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
				? "Upload " + player + "'s cached build to your account (saved to your Discord profile)"
				: emptyHint
		)));
		Button export = buttons.get(5);
		export.visible = cached;
		export.active = cached;
		export.setTooltip(Tooltip.create(Component.literal(
			cached
				? "Generate a shareable link for " + player + "'s cached build (not attached to your account)"
				: emptyHint
		)));
	}

	private static Button statusButton(ViewedPlayers.Kind view, int x, int y) {
		String command = ViewedPlayersTracker.tagFor(view);
		return Button.builder(
			Component.literal(command.toUpperCase(Locale.ROOT)),
			b -> ViewedPlayersTracker.openView(command, player)
		)
			.bounds(x, y, STATUS_WIDTH, HEIGHT)
			.build();
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
}
