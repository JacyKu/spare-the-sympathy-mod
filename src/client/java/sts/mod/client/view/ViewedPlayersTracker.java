package sts.mod.client.view;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import sts.mod.SpareTheSympathy;
import sts.mod.api.BuildTokenEncoder;
import sts.mod.api.ItemUploader;
import sts.mod.api.MonumentaItemDefinition;
import sts.mod.api.StsApiClient;
import sts.mod.client.armoury.ArmouryLoadoutReader;
import sts.mod.client.armoury.ArmouryTracker;

/**
 * Watches the Monumenta GUIs that show another player's build and feeds them
 * into {@link ViewedPlayers}:
 * <ul>
 *     <li>Player Stats Calculator (/ps): the right equipment set is the
 *     requested player's gear (the left set is the viewer's).</li>
 *     <li>Class Selection GUI (/pa): ability icons whose lore says
 *     {@code Current Level: Level 1/2 [Enhanced]}.</li>
 *     <li>&lt;name&gt;'s Charms (/vc): the equipped charms, slots 45-51.</li>
 * </ul>
 * Parsing runs every client tick while one of those screens is open, so page
 * changes (class -> skills -> spec) merge in as they are browsed.
 */
public final class ViewedPlayersTracker {
	private static final String STATS_TITLE = "Player Stats Calculator";
	private static final String ABILITIES_TITLE = "Class Selection GUI";
	private static final String CHARMS_SUFFIX = "'s Charms";

	// PlayerItemStatsGUI.PSGUIEquipment: [mainhand, offhand, helmet, chestplate,
	// leggings, boots] on the left (viewer) and right (requested player) sides.
	private static final int[] STATS_LEFT_SLOTS = { 46, 19, 18, 27, 36, 45 };
	private static final int[] STATS_RIGHT_SLOTS = { 52, 25, 26, 35, 44, 53 };

	// ClassSelectionGui.ClassPage: class icons at rows 2-3, columns 1/3/5/7.
	private static final int[] CLASS_ITEM_SLOTS = { 19, 21, 23, 25, 28, 30, 32, 34 };
	// ClassSelectionGui.SkillPage: spec icons at the bottom row, columns 2/6.
	private static final int[] SPEC_ITEM_SLOTS = { 47, 51 };

	private static final int CHARM_START = 45;
	private static final int CHARM_END = 52;

	private static Screen activeScreen;
	private static ViewedPlayers.Pending activePending;
	private static ViewedPlayers.Kind activeKind;
	private static String activeName;

	private ViewedPlayersTracker() {
	}

	public static void onClientTick() {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.screen;
		if (screen != activeScreen) {
			activeScreen = screen;
			activePending = null;
			activeKind = null;
			activeName = null;
		}
		if (!(screen instanceof ContainerScreen container)) {
			return;
		}
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null || menu.slots.size() < 54) {
			return;
		}
		String title = container.getTitle().getString();
		ViewedPlayers.Kind kind = kindForTitle(title);
		if (kind == null) {
			return;
		}
		activeKind = kind;
		activeName = playerForScreen(screen);

		if (kind == ViewedPlayers.Kind.STATS) {
			ArmouryTracker.ensureItemsAndClasses();
			parseStats(menu);
		} else if (kind == ViewedPlayers.Kind.ABILITIES) {
			ArmouryTracker.ensureItemsAndClasses();
			parseAbilities(menu);
		} else {
			ArmouryTracker.ensureItemsAndClasses();
			parseCharms(menu, activeName);
		}
	}

	/** Which view a GUI title belongs to, or null for other screens. */
	public static ViewedPlayers.Kind kindForTitle(String title) {
		if (STATS_TITLE.equals(title)) {
			return ViewedPlayers.Kind.STATS;
		}
		if (ABILITIES_TITLE.equals(title)) {
			return ViewedPlayers.Kind.ABILITIES;
		}
		if (title != null && title.endsWith(CHARMS_SUFFIX)) {
			return ViewedPlayers.Kind.CHARMS;
		}
		return null;
	}

	/**
	 * The player whose build the open screen shows: the charms title carries
	 * the name; the stats/abilities screens use the /ps or /pa command target
	 * (the tracker's active name is the fallback when the command has aged
	 * out, e.g. after a resize).
	 */
	public static String playerForScreen(Screen screen) {
		if (!(screen instanceof ContainerScreen container)) {
			return null;
		}
		String title = container.getTitle().getString();
		ViewedPlayers.Kind kind = kindForTitle(title);
		if (kind == null) {
			return null;
		}
		if (kind == ViewedPlayers.Kind.CHARMS) {
			return title.substring(0, title.length() - CHARMS_SUFFIX.length());
		}
		ViewedPlayers.Pending pending = ViewedPlayers.pending(kind);
		if (pending != null) {
			if (kind == ViewedPlayers.Kind.STATS) {
				return pending.right() != null ? pending.right() : pending.left();
			}
			return pending.left();
		}
		return activeKind == kind ? activeName : null;
	}

	/** The player whose view is open (for the status buttons), or null. */
	public static String activeName() {
		return activeName;
	}

	public static ViewedPlayers.Kind activeKind() {
		return activeKind;
	}

	/** True when the player's view of this kind has been parsed this session. */
	public static boolean isViewed(String player, ViewedPlayers.Kind kind) {
		ViewedPlayers.CachedPlayer cached = ViewedPlayers.get(player);
		return cached != null && cached.isViewed(kind);
	}

	/** True when any part of the player's build has been cached. */
	public static boolean hasCachedData(String player) {
		ViewedPlayers.CachedPlayer cached = ViewedPlayers.get(player);
		return cached != null && (cached.hasEquipment() || cached.hasAbilities() || cached.hasCharms());
	}

	/** Runs the view command (ps/pa/vc) for a player, opening its GUI. */
	public static void openView(String command, String player) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.connection == null || player == null || player.isBlank()) {
			return;
		}
		mc.player.connection.sendCommand(command + " " + player);
	}

	/** The view after the currently open one, in collection order. */
	public static ViewedPlayers.Kind nextKind() {
		if (activeKind == ViewedPlayers.Kind.STATS) {
			return ViewedPlayers.Kind.ABILITIES;
		}
		if (activeKind == ViewedPlayers.Kind.ABILITIES) {
			return ViewedPlayers.Kind.CHARMS;
		}
		return ViewedPlayers.Kind.STATS;
	}

	public static String tagFor(ViewedPlayers.Kind kind) {
		return switch (kind) {
			case STATS -> "ps";
			case ABILITIES -> "pa";
			case CHARMS -> "vc";
		};
	}

	private static void notifyCached(ViewedPlayers.CachedPlayer cached, ViewedPlayers.Kind kind, String player) {
		if (player == null || !cached.markNotified(kind)) {
			return;
		}
		String label = switch (kind) {
			case STATS -> "equipment";
			case ABILITIES -> "abilities";
			case CHARMS -> "charms";
		};
		ArmouryTracker.showMessage("Cached " + player + "'s " + label + " (/" + tagFor(kind) + ").");
	}

	// ---------- /ps: equipment ----------

	private static void parseStats(AbstractContainerMenu menu) {
		ViewedPlayers.Pending pending = pending(ViewedPlayers.Kind.STATS);
		if (pending == null) {
			return;
		}
		List<MonumentaItemDefinition> items = ArmouryTracker.items();
		if (items.isEmpty()) {
			// The item dictionary is still loading; parsing now would upload
			// every icon as an unknown item.
			return;
		}
		if (pending.left() != null) {
			mergeEquipment(pending.left(), menu, STATS_LEFT_SLOTS, items);
		}
		if (pending.right() != null) {
			mergeEquipment(pending.right(), menu, STATS_RIGHT_SLOTS, items);
		}
		int region = parseRegion(menu.slots.get(4).getItem());
		if (region > 0) {
			if (pending.left() != null) {
				ViewedPlayers.cache(pending.left()).mergeRegion(region);
			}
			if (pending.right() != null) {
				ViewedPlayers.cache(pending.right()).mergeRegion(region);
			}
		}
		if (activeName != null) {
			ViewedPlayers.CachedPlayer cached = ViewedPlayers.cache(activeName);
			cached.markViewed(ViewedPlayers.Kind.STATS);
			if (cached.hasEquipment()) {
				notifyCached(cached, ViewedPlayers.Kind.STATS, activeName);
			}
		}
	}

	private static void mergeEquipment(
		String name,
		AbstractContainerMenu menu,
		int[] slots,
		List<MonumentaItemDefinition> items
	) {
		String[] keys = new String[6];
		List<JsonObject> unknown = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			ItemStack stack = menu.slots.get(slots[i]).getItem();
			if (stack.isEmpty() || isStatsPlaceholder(stack)) {
				keys[i] = "None";
				continue;
			}
			String key = ArmouryLoadoutReader.matchItemKey(stack, items);
			if ("None".equals(key)) {
				JsonObject payload = ItemUploader.buildPayload(stack);
				String displayName = payload.get("name").getAsString();
				if (!displayName.isEmpty()) {
					key = displayName;
					unknown.add(payload);
				}
			}
			keys[i] = key;
		}
		ViewedPlayers.cache(name).mergeEquipment(keys, unknown);
	}

	/** Empty-slot icons in the stats GUI ("Main Hand Slot" item frames). */
	private static boolean isStatsPlaceholder(ItemStack stack) {
		return stack.is(Items.ITEM_FRAME) || stack.getHoverName().getString().trim().endsWith(" Slot");
	}

	private static int parseRegion(ItemStack banner) {
		if (banner.isEmpty()) {
			return 0;
		}
		String name = banner.getHoverName().getString();
		if (name.contains("King's Valley")) {
			return 1;
		}
		if (name.contains("Celsian Isles")) {
			return 2;
		}
		if (name.contains("Architect's Ring")) {
			return 3;
		}
		return 0;
	}

	// ---------- /pa: class / spec abilities ----------

	/** Where an ability display name lives in the class catalog. */
	private record AbilityRef(String className, String specName, String scoreboardId) {
	}

	private static void parseAbilities(AbstractContainerMenu menu) {
		ViewedPlayers.Pending pending = pending(ViewedPlayers.Kind.ABILITIES);
		if (pending == null || pending.left() == null) {
			return;
		}
		List<StsApiClient.GameClass> classes = ArmouryTracker.classes();
		if (classes.isEmpty()) {
			return;
		}
		ViewedPlayers.CachedPlayer cached = ViewedPlayers.cache(pending.left());

		// The class page shows every class; the player's own class is the only
		// non-barrier icon (several non-barriers = no class chosen).
		ClassResult classResult = detectClass(menu, classes);
		if (classResult.known()) {
			cached.setClass(classResult.className());
		} else if (cached.className() == null) {
			// Class page not parsed yet: fall back to the open skill page.
			String header = headerClassName(menu, classes);
			if (header != null) {
				cached.setClass(header);
			}
		}
		if (classResult.known() && classResult.className() == null) {
			// Classless: there are no abilities to read.
			return;
		}

		// The skill page's bottom row shows both specs; the player's own spec
		// item is the non-barrier one (both non-barrier = no spec chosen).
		SpecResult specResult = detectSpec(menu, classes);
		if (specResult.known()) {
			cached.setSpec(specResult.spec());
		}

		Map<String, AbilityRef> byName = new HashMap<>();
		for (StsApiClient.GameClass gameClass : classes) {
			for (StsApiClient.Ability ability : gameClass.skills()) {
				byName.putIfAbsent(
					normalize(ability.displayName()),
					new AbilityRef(gameClass.className(), null, ability.scoreboardId())
				);
			}
			for (StsApiClient.Spec spec : gameClass.specs()) {
				for (StsApiClient.Ability ability : spec.specSkills()) {
					byName.putIfAbsent(
						normalize(ability.displayName()),
						new AbilityRef(gameClass.className(), spec.specName(), ability.scoreboardId())
					);
				}
			}
		}

		String gameClass = cached.className();
		String gameSpec = cached.spec();
		List<BuildTokenEncoder.Skill> skills = new ArrayList<>();
		List<BuildTokenEncoder.Skill> specSkills = new ArrayList<>();
		List<String> enhancements = new ArrayList<>();

		Minecraft mc = Minecraft.getInstance();
		for (int slot = 0; slot < menu.slots.size(); slot++) {
			ItemStack stack = menu.slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String level = abilityLevel(stack, mc);
			if (level == null) {
				continue;
			}
			AbilityRef ref = byName.get(normalize(stack.getHoverName().getString()));
			if (ref == null) {
				SpareTheSympathy.LOGGER.info(
					"[sts] Ability icon '{}' is not in the skill catalog while caching {}",
					stack.getHoverName().getString().trim(),
					pending.left()
				);
				continue;
			}
			// Browsing another class or spec in the read-only GUI must not
			// overwrite the player's own build.
			if (gameClass != null && !ref.className().equalsIgnoreCase(gameClass)) {
				continue;
			}
			if (ref.specName() != null && (gameSpec == null || !ref.specName().equalsIgnoreCase(gameSpec))) {
				continue;
			}
			int points = 0;
			if (level.startsWith("Level 1")) {
				points = 1;
			} else if (level.startsWith("Level 2")) {
				points = 2;
			}
			if (points <= 0) {
				continue;
			}
			if (level.toLowerCase(Locale.ROOT).contains("enhanced")) {
				enhancements.add(ref.scoreboardId());
			}
			(ref.specName() != null ? specSkills : skills).add(
				new BuildTokenEncoder.Skill(ref.scoreboardId(), points)
			);
		}

		cached.markViewed(ViewedPlayers.Kind.ABILITIES);
		if (!skills.isEmpty() || !specSkills.isEmpty()) {
			cached.mergeAbilities(skills, specSkills, enhancements);
		}
		if (cached.hasAbilities()) {
			notifyCached(cached, ViewedPlayers.Kind.ABILITIES, activeName != null ? activeName : pending.left());
		}
	}

	/** The "Current Level:" value from an ability icon's lore, or null. */
	private static String abilityLevel(ItemStack stack, Minecraft mc) {
		// The line renders as "<arrow> Current Level: Level 1" (the description
		// builder prefixes stats with an arrow glyph), so the marker can appear
		// anywhere in the line.
		for (Component line : stack.getTooltipLines(mc.player, TooltipFlag.NORMAL)) {
			String text = line.getString();
			int marker = text.indexOf("Current Level:");
			if (marker >= 0) {
				return text.substring(marker + "Current Level:".length()).trim();
			}
		}
		return null;
	}

	private record ClassResult(boolean known, String className) {
	}

	/** The player's class from the class page (several non-barriers = classless). */
	private static ClassResult detectClass(AbstractContainerMenu menu, List<StsApiClient.GameClass> classes) {
		int present = 0;
		int selectable = 0;
		String found = null;
		for (int slot : CLASS_ITEM_SLOTS) {
			ItemStack stack = menu.slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String matched = matchClass(stack.getHoverName().getString(), classes);
			if (matched == null) {
				continue;
			}
			present++;
			if (!stack.is(Items.BARRIER)) {
				selectable++;
				found = matched;
			}
		}
		if (present < 2) {
			return new ClassResult(false, null);
		}
		if (selectable == 1) {
			return new ClassResult(true, found);
		}
		if (selectable > 1) {
			return new ClassResult(true, null); // classless
		}
		return new ClassResult(false, null);
	}

	private record SpecResult(boolean known, String spec) {
	}

	/** The player's spec from the skill page's spec items (both non-barriers = none). */
	private static SpecResult detectSpec(AbstractContainerMenu menu, List<StsApiClient.GameClass> classes) {
		int present = 0;
		int selectable = 0;
		String found = null;
		for (int slot : SPEC_ITEM_SLOTS) {
			ItemStack stack = menu.slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String matched = matchSpec(stack.getHoverName().getString(), classes);
			if (matched == null) {
				continue;
			}
			present++;
			if (!stack.is(Items.BARRIER)) {
				selectable++;
				found = matched;
			}
		}
		if (present < 2) {
			return new SpecResult(false, null);
		}
		if (selectable == 1) {
			return new SpecResult(true, found);
		}
		if (selectable > 1) {
			return new SpecResult(true, null); // no spec chosen
		}
		return new SpecResult(false, null); // viewing another class's skills
	}

	private static String matchClass(String name, List<StsApiClient.GameClass> classes) {
		String trimmed = name == null ? "" : name.trim();
		for (StsApiClient.GameClass gameClass : classes) {
			if (gameClass.className().equalsIgnoreCase(trimmed)) {
				return gameClass.className();
			}
		}
		return null;
	}

	private static String matchSpec(String name, List<StsApiClient.GameClass> classes) {
		String trimmed = name == null ? "" : name.trim();
		for (StsApiClient.GameClass gameClass : classes) {
			for (StsApiClient.Spec spec : gameClass.specs()) {
				if (spec.specName().equalsIgnoreCase(trimmed)) {
					return spec.specName();
				}
			}
		}
		return null;
	}

	/** The class shown in the GUI header (row 0, column 4), if it is one. */
	private static String headerClassName(AbstractContainerMenu menu, List<StsApiClient.GameClass> classes) {
		ItemStack header = menu.slots.get(4).getItem();
		return header.isEmpty() ? null : matchClass(header.getHoverName().getString(), classes);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	// ---------- /vc: charms ----------

	private static void parseCharms(AbstractContainerMenu menu, String name) {
		List<MonumentaItemDefinition> items = ArmouryTracker.items();
		if (items.isEmpty() || name.isBlank()) {
			return;
		}
		List<String> keys = new ArrayList<>();
		List<JsonObject> unknown = new ArrayList<>();
		for (int slot = CHARM_START; slot < CHARM_END && slot < menu.slots.size(); slot++) {
			ItemStack stack = menu.slots.get(slot).getItem();
			if (stack.isEmpty() || isCharmPlaceholder(stack)) {
				continue;
			}
			String key = ArmouryLoadoutReader.matchCharmKey(stack, items);
			if (key != null) {
				keys.add(key);
			} else {
				JsonObject payload = ItemUploader.buildPayload(stack);
				payload.addProperty("type", "Charm");
				if (!payload.get("name").getAsString().isEmpty()) {
					unknown.add(payload);
				}
			}
		}
		ViewedPlayers.CachedPlayer cached = ViewedPlayers.cache(name);
		cached.mergeCharms(keys, unknown);
		cached.markViewed(ViewedPlayers.Kind.CHARMS);
		if (cached.hasCharms()) {
			notifyCached(cached, ViewedPlayers.Kind.CHARMS, name);
		}
	}

	/** Charm GUI furniture: the glass-pane slot/power placeholders. */
	private static boolean isCharmPlaceholder(ItemStack stack) {
		if (stack.is(Items.YELLOW_STAINED_GLASS_PANE) || stack.is(Items.RED_STAINED_GLASS_PANE)) {
			return true;
		}
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains("charm slot") || name.contains("charm power");
	}

	// ---------- pending target bookkeeping ----------

	/**
	 * The command target for the screen that is currently open. Captured once
	 * per screen (so page navigation inside the GUI keeps the same target) and
	 * dropped when the screen changes.
	 */
	private static ViewedPlayers.Pending pending(ViewedPlayers.Kind kind) {
		if (activePending == null) {
			ViewedPlayers.Pending candidate = ViewedPlayers.pending(kind);
			if (candidate == null) {
				return null;
			}
			activePending = candidate;
		}
		return activePending.kind() == kind ? activePending : null;
	}
}
