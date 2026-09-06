package sts.mod.client.armoury;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import sts.mod.SpareTheSympathy;
import sts.mod.api.BuildTokenEncoder;
import sts.mod.api.MonumentaItemDefinition;
import sts.mod.api.MonumentaItemRepository;
import sts.mod.api.StsApiClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives the armoury feature: every client tick it checks whether the
 * Mechanical Armory loadout view is open, re-reads the loadout when it
 * changes, and exposes the actions the overlay buttons trigger. All network
 * work runs on a background thread; state is published through volatiles that
 * the render thread only reads.
 */
public final class ArmouryTracker {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SpareTheSympathy-Armoury");
		thread.setDaemon(true);
		return thread;
	});

	// UI state (volatile, written on the executor or tick, read on render).
	private static volatile boolean armouryOpen;
	private static volatile ArmouryLoadoutReader.Loadout loadout;
	private static volatile Boolean linked; // null = unknown
	private static volatile boolean busy;
	private static volatile String feedback;

	// Lazily fetched data.
	private static volatile List<MonumentaItemDefinition> items;
	private static volatile List<StsApiClient.GameClass> classes;
	private static final AtomicBoolean CLASSES_REQUESTED = new AtomicBoolean(false);
	private static volatile boolean itemsReady;
	private static volatile boolean classesReady;

	private static volatile boolean requestedLinkStatus;

	private ArmouryTracker() {
	}

	/** Called every client tick. */
	public static void onClientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			closeArmoury();
			return;
		}
		Screen screen = mc.screen;
		if (screen == null || !ArmouryLoadoutReader.isArmouryScreen(screen)) {
			closeArmoury();
			return;
		}

		if (!armouryOpen) {
			armouryOpen = true;
			requestedLinkStatus = false;
			feedback = null;
		}
		ensureItemsAndClasses();
		ensureLinkStatus(mc);

		// The loadout is only parsed on the loadout view; on the overview page
		// the rows hold loadout icons which would parse as bogus equipment.
		boolean loadoutView = ArmouryLoadoutReader.isLoadoutView(screen);
		if (loadoutView) {
			ArmouryLoadoutReader.Loadout parsed = ArmouryLoadoutReader.read(screen, items(), classes());
			if (parsed != null) {
				loadout = parsed;
			}
		} else {
			// Overview page: no loadout is on display, so the action buttons
			// must not be able to target a loadout.
			loadout = null;
		}
	}


	private static void closeArmoury() {
		if (armouryOpen) {
			armouryOpen = false;
			loadout = null;
			linked = null;
			requestedLinkStatus = false;
		}
	}

	private static void ensureItemsAndClasses() {
		if (items == null) {
			itemsReady = false;
			EXECUTOR.execute(() -> {
				try {
					items = MonumentaItemRepository.getItems();
					SpareTheSympathy.LOGGER.info("Loaded {} Monumenta items for the armoury", items.size());
				} catch (RuntimeException e) {
					SpareTheSympathy.LOGGER.warn("Failed to load Monumenta items for the armoury: {}", e.toString());
					items = null; // allow a retry on the next open
				} finally {
					itemsReady = true;
				}
			});
			items = List.of(); // avoid re-queueing while the fetch runs
		}
		if (classes == null && CLASSES_REQUESTED.compareAndSet(false, true)) {
			classesReady = false;
			EXECUTOR.execute(() -> {
				try {
					classes = StsApiClient.fetchSkills();
					SpareTheSympathy.LOGGER.info("Loaded {} classes for the armoury", classes.size());
				} catch (Exception e) {
					SpareTheSympathy.LOGGER.warn("Failed to load skill data for the armoury: {}", e.toString());
					classes = List.of();
					CLASSES_REQUESTED.set(false); // allow a retry on the next open
				} finally {
					classesReady = true;
				}
			});
		}
	}

	private static void ensureLinkStatus(Minecraft mc) {
		if (requestedLinkStatus || mc.getUser() == null || mc.getUser().getProfileId() == null) {
			return;
		}
		requestedLinkStatus = true;
		String uuid = mc.getUser().getProfileId().toString();
		EXECUTOR.execute(() -> {
			try {
				linked = StsApiClient.isLinked(uuid);
			} catch (Exception e) {
				linked = null;
			}
		});
	}

	public static boolean isArmouryOpen() {
		return armouryOpen;
	}

	public static ArmouryLoadoutReader.Loadout currentLoadout() {
		return loadout;
	}

	public static Boolean isLinked() {
		return linked;
	}

	public static boolean isBusy() {
		return busy;
	}

	public static String feedback() {
		return feedback;
	}

	// ---------- actions ----------

	/** The shareable link for the current loadout, or null when nothing is shown. */
	public static String exportLink() {
		ArmouryLoadoutReader.Loadout current = loadout;
		if (current == null) {
			return null;
		}
		return StsApiClient.siteUrl() + "/builder/" + encode(current);
	}

	public static void copyToClipboard(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.keyboardHandler != null) {
			mc.keyboardHandler.setClipboard(text);
		}
	}

	public static void showMessage(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("[STS] " + message), false);
		}
	}

	/** Prints a message in chat with the URL as a clickable hyperlink. */
	public static void showLinkMessage(String url, String prefix) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Component link = Component.literal(url).withStyle(style -> style
			.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
			.withUnderlined(true));
		mc.player.displayClientMessage(Component.literal("[STS] " + prefix).append(link), false);
	}

	// Export goes through the API like every save: the token is stored (no
	// account attached) and the player gets a short link back - the raw token
	// is never handed out directly. Falls back to the raw builder link only
	// when the site is unreachable.
	public static void exportToClipboard() {
		ArmouryLoadoutReader.Loadout current = loadout;
		if (current == null) {
			showMessage("No loadout to export.");
			return;
		}
		String token = encode(current);
		java.util.Map<String, String> infusions = new java.util.LinkedHashMap<>(current.delveInfusions());
		busy = true;
		feedback = null;
		EXECUTOR.execute(() -> {
			try {
				StsApiClient.SaveResult result = StsApiClient.saveBuild(null, token, buildName(current), infusions);
				copyToClipboard(StsApiClient.siteUrl() + result.url());
				feedback = "Build saved and short link copied to clipboard.";
			} catch (Exception e) {
				copyToClipboard(StsApiClient.siteUrl() + "/builder/" + token);
				feedback = "Site unreachable - raw link copied instead (" + e.getMessage() + ").";
			} finally {
				busy = false;
			}
		});
	}

	public static void saveBuild() {
		Minecraft mc = Minecraft.getInstance();
		ArmouryLoadoutReader.Loadout current = loadout;
		if (current == null) {
			showMessage("No loadout to save.");
			return;
		}
		String token = encode(current);
		java.util.Map<String, String> infusions = new java.util.LinkedHashMap<>(current.delveInfusions());
		String name = buildName(current);
		if (mc.getUser() == null || mc.getUser().getProfileId() == null) {
			// No Minecraft profile (offline mode): save anonymously.
			saveAnonymous(token, name, infusions);
			return;
		}
		String uuid = mc.getUser().getProfileId().toString();
		busy = true;
		feedback = null;
		EXECUTOR.execute(() -> {
			try {
				StsApiClient.SaveResult result = StsApiClient.saveBuild(uuid, token, name, infusions);
				copyToClipboard(StsApiClient.siteUrl() + result.url());
				if (result.linked() && result.saved()) {
					feedback = "Build saved to your profile and link copied to clipboard.";
					linked = true;
				} else {
					feedback = "Profile not linked - build saved without an account and the link copied. Run /sts link to save builds to your profile.";
				}
			} catch (Exception e) {
				String detail = e.getMessage() == null ? "" : e.getMessage();
				if (detail.contains("duplicate")) {
					feedback = "A build with this loadout's name already exists on your profile - rename the loadout and try again.";
					showMessage("A build with this loadout's name already exists on your profile - rename the loadout and try again.");
				} else {
					feedback = "Could not save the build (" + detail + ").";
				}
			} finally {
				busy = false;
			}
		});
	}

	private static void saveAnonymous(String token, String name, java.util.Map<String, String> infusions) {
		busy = true;
		feedback = null;
		EXECUTOR.execute(() -> {
			try {
				StsApiClient.SaveResult result = StsApiClient.saveBuild(null, token, name, infusions);
				copyToClipboard(StsApiClient.siteUrl() + result.url());
				feedback = "Build saved and short link copied to clipboard.";
			} catch (Exception e) {
				copyToClipboard(StsApiClient.siteUrl() + "/builder/" + token);
				feedback = "Site unreachable - raw link copied instead (" + e.getMessage() + ").";
			} finally {
				busy = false;
			}
		});
	}

	public static void linkAccount() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getUser() == null || mc.getUser().getProfileId() == null) {
			showMessage("No Minecraft profile found - make sure you are playing online.");
			return;
		}
		String uuid = mc.getUser().getProfileId().toString();
		busy = true;
		feedback = null;
		EXECUTOR.execute(() -> {
			try {
				StsApiClient.LinkRequest request = StsApiClient.requestLink(uuid);
				openBrowser(request.url());
				feedback = "Open the link in your browser to link your Discord account: " + request.url();
				showLinkMessage(request.url(), "Open this link in your browser to link your Discord account: ");
			} catch (Exception e) {
				feedback = "Could not start the linking flow (" + e.getMessage() + ").";
				showMessage("Could not start the linking flow: " + e.getMessage());
			} finally {
				busy = false;
			}
		});
	}

	public static void openAccountPage() {
		openBrowser(StsApiClient.siteUrl() + "/account");
	}

	private static void openBrowser(String url) {
		Minecraft mc = Minecraft.getInstance();
		try {
			net.minecraft.Util.getPlatform().openUri(url);
		} catch (Exception e) {
			copyToClipboard(url);
			feedback = "Could not open a browser - the link was copied to clipboard instead.";
		}
	}

	private static String encode(ArmouryLoadoutReader.Loadout loadout) {
		return BuildTokenEncoder.encode(
			List.of(loadout.itemKeys()),
			loadout.charmKeys().isEmpty() ? null : String.join(",", loadout.charmKeys()),
			buildName(loadout),
			loadout.className(),
			loadout.specName(),
			loadout.skills(),
			loadout.specSkills(),
			loadout.enhancements(),
			null
		);
	}

	private static String buildName(ArmouryLoadoutReader.Loadout loadout) {
		// The loadout's own name (the icon's custom name) is the best title;
		// fall back to the class/spec combo when it has none.
		if (loadout.name() != null && !loadout.name().isEmpty()) {
			return loadout.name();
		}
		String name = loadout.className();
		if (loadout.specName() != null) {
			name = name == null ? loadout.specName() : name + " " + loadout.specName();
		}
		return name == null ? "Armoury Loadout" : name;
	}

	/** The loaded Monumenta item dictionary (may be empty while loading). */
	public static List<MonumentaItemDefinition> items() {
		return items == null ? List.of() : items;
	}

	/** True once the item dictionary fetch finished (even on failure). */
	public static boolean itemsReady() {
		return itemsReady;
	}

	/** The loaded skill/class catalog (may be empty while loading). */
	public static List<StsApiClient.GameClass> classes() {
		return classes == null ? List.of() : classes;
	}

	/** True once the skill/class fetch finished (even on failure). */
	public static boolean classesReady() {
		return classesReady;
	}
}
