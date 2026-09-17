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
	// How often a failed item-dictionary fetch is retried while the armoury
	// is open (a missing dictionary makes every item look like an unknown one,
	// so the loadout is not parsed until it exists).
	private static final long ITEMS_RETRY_INTERVAL_MS = 30_000;
	private static volatile long lastItemsAttempt;
	private static final long CLASSES_RETRY_INTERVAL_MS = 30_000;
	private static volatile long lastClassesAttempt;

	// Link status is re-checked periodically while the armoury is open, so the
	// Link Account button flips to Linked after the browser flow completes
	// without needing to close and reopen the screen.
	private static final long LINK_CHECK_INTERVAL_MS = 5000;
	private static volatile boolean linkCheckInFlight;
	private static volatile long lastLinkCheck;

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
			lastLinkCheck = 0;
			feedback = null;
		}
		ensureItemsAndClasses();
		ensureLinkStatus(mc);

		// The loadout is only parsed on the loadout view; on the overview page
		// the rows hold loadout icons which would parse as bogus equipment.
		// Parsing also waits for the item dictionary: without it every icon
		// would be recorded (and later uploaded) as an unknown item.
		boolean loadoutView = ArmouryLoadoutReader.isLoadoutView(screen);
		if (loadoutView && itemsReady && items != null && !items.isEmpty()) {
			ArmouryLoadoutReader.Loadout parsed = ArmouryLoadoutReader.read(screen, items(), classes());
			if (parsed != null) {
				loadout = parsed;
			}
		} else {
			// Overview page, or the dictionary is still loading / unavailable:
			// the action buttons must not be able to target a loadout.
			loadout = null;
		}
	}


	private static void closeArmoury() {
		if (armouryOpen) {
			armouryOpen = false;
			loadout = null;
			linked = null;
			lastLinkCheck = 0;
		}
	}

	/** Starts loading the item dictionary + class catalog if not loaded yet. */
	public static void ensureItemsAndClasses() {
		long now = System.currentTimeMillis();
		// A total failure (API after retries + no cache) leaves items null so
		// a later armory visit retries, throttled so a broken connection
		// doesn't hammer the API every tick.
		if (items == null && now - lastItemsAttempt >= ITEMS_RETRY_INTERVAL_MS) {
			lastItemsAttempt = now;
			itemsReady = false;
			items = List.of(); // placeholder while the fetch runs (also stops re-queueing)
			EXECUTOR.execute(() -> {
				try {
					List<MonumentaItemDefinition> loaded = MonumentaItemRepository.getItems();
					items = loaded; // null = API + cache both failed -> retry later
					if (loaded != null) {
						SpareTheSympathy.LOGGER.info("Loaded {} Monumenta items for the armoury", loaded.size());
					} else {
						SpareTheSympathy.LOGGER.warn("Monumenta items unavailable (API + cache) - retrying later");
					}
				} catch (RuntimeException e) {
					SpareTheSympathy.LOGGER.warn("Failed to load Monumenta items for the armoury: {}", e.toString());
					items = null; // allow a retry later
				} finally {
					itemsReady = true;
				}
			});
		}
		if (classes == null && now - lastClassesAttempt >= CLASSES_RETRY_INTERVAL_MS
			&& CLASSES_REQUESTED.compareAndSet(false, true)) {
			lastClassesAttempt = now;
			classesReady = false;
			EXECUTOR.execute(() -> {
				try {
					classes = StsApiClient.fetchSkills();
					SpareTheSympathy.LOGGER.info("Loaded {} classes for the armoury", classes.size());
				} catch (Exception e) {
					SpareTheSympathy.LOGGER.warn("Failed to load skill data for the armoury: {}", e.toString());
					// null, not an empty list: a later open (throttled above)
					// must retry, e.g. after the site URL was corrected.
					classes = null;
					CLASSES_REQUESTED.set(false);
				} finally {
					classesReady = true;
				}
			});
		}
	}

	private static void ensureLinkStatus(Minecraft mc) {
		if (mc.getUser() == null || mc.getUser().getProfileId() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (linkCheckInFlight || now - lastLinkCheck < LINK_CHECK_INTERVAL_MS) {
			return;
		}
		linkCheckInFlight = true;
		lastLinkCheck = now;
		String uuid = mc.getUser().getProfileId().toString();
		EXECUTOR.execute(() -> {
			try {
				boolean nowLinked = StsApiClient.isLinked(uuid);
				boolean wasLinked = Boolean.TRUE.equals(linked);
				linked = nowLinked;
				if (nowLinked && !wasLinked) {
					feedback = "Account linked - builds now save to your profile.";
				}
			} catch (Exception e) {
				linked = null;
			} finally {
				linkCheckInFlight = false;
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
		// Callers include the background executor; chat must be touched on the
		// client thread.
		mc.execute(() -> {
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal("[STS] " + message), false);
			}
		});
	}

	/** Prints a message in chat with the URL as a clickable hyperlink. */
	public static void showLinkMessage(String url, String prefix) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player == null) {
				return;
			}
			Component link = Component.literal(url).withStyle(style -> style
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
				.withUnderlined(true));
			mc.player.displayClientMessage(Component.literal("[STS] " + prefix).append(link), false);
		});
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
		com.google.gson.JsonObject basicInfusions =
			sts.mod.api.InfusionReader.basicInfusionsJson(current.basicInfusions());
		busy = true;
		feedback = null;
		EXECUTOR.execute(() -> {
			try {
				StsApiClient.SaveResult result =
					StsApiClient.saveBuild(null, token, buildName(current), infusions, null, basicInfusions);
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
		ArmouryLoadoutReader.Loadout current = loadout;
		if (current == null) {
			showMessage("No loadout to save.");
			return;
		}
		String token = encode(current);
		java.util.Map<String, String> infusions = new java.util.LinkedHashMap<>(current.delveInfusions());
		com.google.gson.JsonObject basicInfusions =
			sts.mod.api.InfusionReader.basicInfusionsJson(current.basicInfusions());
		String name = buildName(current);
		com.google.gson.JsonArray unknownItems = new com.google.gson.JsonArray();
		for (com.google.gson.JsonObject payload : current.unknownItemPayloads()) {
			unknownItems.add(payload);
		}
		saveToken(name, token, infusions, unknownItems, basicInfusions, false);
	}

	/**
	 * Saves a build token the way the armoury buttons do: to the player's
	 * profile when linked, anonymously otherwise, always copying the short
	 * link. Shared with /sts upload_build of cached viewed players.
	 */
	public static void saveToken(
		String name,
		String token,
		java.util.Map<String, String> infusions,
		com.google.gson.JsonArray unknownItems
	) {
		saveToken(name, token, infusions, unknownItems, null, false);
	}

	/**
	 * @param basicInfusions per-slot normal infusions ({@code slot: {name, level}})
	 * @param notifyChat     also post the result to chat - used by the /sts
	 *                       export_build and upload_build commands, whose
	 *                       feedback overlay isn't on screen
	 */
	public static void saveToken(
		String name,
		String token,
		java.util.Map<String, String> infusions,
		com.google.gson.JsonArray unknownItems,
		com.google.gson.JsonObject basicInfusions,
		boolean notifyChat
	) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getUser() == null || mc.getUser().getProfileId() == null) {
			// No Minecraft profile (offline mode): save anonymously.
			saveAnonymous(token, name, infusions, basicInfusions, notifyChat);
			return;
		}
		String uuid = mc.getUser().getProfileId().toString();
		busy = true;
		feedback = null;
		EXECUTOR.execute(() -> {
			try {
				StsApiClient.SaveResult result = StsApiClient.saveBuild(
					uuid, token, name, infusions, unknownItems, basicInfusions
				);
				copyToClipboard(StsApiClient.siteUrl() + result.url());
				String created = result.createdItems().isEmpty()
					? ""
					: " Created " + result.createdItems().size() + " custom item(s) for equipment the site didn't know.";
				if (result.linked() && result.saved()) {
					feedback = "Build saved to your profile and link copied to clipboard." + created;
					linked = true;
				} else {
					feedback = "Profile not linked - build saved without an account and the link copied. Run /sts link to save builds to your profile.";
				}
				if (notifyChat) {
					showMessage(feedback);
				}
			} catch (Exception e) {
				String detail = e.getMessage() == null ? "" : e.getMessage();
				if (detail.contains("duplicate")) {
					feedback = "A build with this name already exists - rename the build and try again.";
					showMessage(feedback);
				} else if (detail.contains("invalid device token")) {
					// The link's device hash no longer matches this install
					// (e.g. the account was re-linked elsewhere): the player
					// must re-confirm the link from this game.
					feedback = "This device isn't authorised for the linked account anymore - run /sts link again.";
					showMessage(feedback);
				} else {
					feedback = "Could not save the build (" + detail + ").";
					if (notifyChat) {
						showMessage(feedback);
					}
				}
			} finally {
				busy = false;
			}
		});
	}

	/**
	 * Saves a build without an account (never attached to the profile) and
	 * copies the short link - the /sts export_build path.
	 */
	public static void saveTokenAnonymously(
		String name,
		String token,
		java.util.Map<String, String> infusions,
		com.google.gson.JsonObject basicInfusions,
		boolean notifyChat
	) {
		saveAnonymous(token, name, infusions, basicInfusions, notifyChat);
	}

	private static void saveAnonymous(
		String token,
		String name,
		java.util.Map<String, String> infusions,
		com.google.gson.JsonObject basicInfusions,
		boolean notifyChat
	) {
		busy = true;
		feedback = null;
		EXECUTOR.execute(() -> {
			try {
				StsApiClient.SaveResult result = StsApiClient.saveBuild(null, token, name, infusions, null, basicInfusions);
				String url = StsApiClient.siteUrl() + result.url();
				copyToClipboard(url);
				feedback = "Build link generated and copied to clipboard.";
				if (notifyChat) {
					showLinkMessage(url, "Build link copied: ");
				}
			} catch (Exception e) {
				String url = StsApiClient.siteUrl() + "/builder/" + token;
				copyToClipboard(url);
				feedback = "Site unreachable - builder link copied instead (" + e.getMessage() + ").";
				if (notifyChat) {
					showLinkMessage(url, "Builder link copied: ");
				}
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
				// Let the periodic check pick the new status up as soon as the
				// player finishes the browser flow.
				lastLinkCheck = 0;
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
		// Basic (normal) infusion levels are the builder's stat inputs (it
		// sums them per type across the six slots); they ride in the token's
		// stat bytes. The region stays at the builder default (the armoury
		// view doesn't state one).
		java.util.Map<String, sts.mod.api.InfusionReader.BasicInfusion> basics = loadout.basicInfusions();
		int[] stats = {
			100,
			sts.mod.api.InfusionReader.basicLevelSum(basics, "Tenacity"),
			sts.mod.api.InfusionReader.basicLevelSum(basics, "Vitality"),
			sts.mod.api.InfusionReader.basicLevelSum(basics, "Vigor"),
			sts.mod.api.InfusionReader.basicLevelSum(basics, "Focus"),
			sts.mod.api.InfusionReader.basicLevelSum(basics, "Perspicacity"),
			3,
		};
		return BuildTokenEncoder.encode(
			List.of(loadout.itemKeys()),
			loadout.charmKeys().isEmpty() ? null : String.join(",", loadout.charmKeys()),
			buildName(loadout),
			loadout.className(),
			loadout.specName(),
			loadout.skills(),
			loadout.specSkills(),
			loadout.enhancements(),
			stats
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
