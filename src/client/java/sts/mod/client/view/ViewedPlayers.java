package sts.mod.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import sts.mod.SpareTheSympathy;
import sts.mod.api.BuildTokenEncoder;
import sts.mod.client.armoury.ArmouryTracker;

/**
 * Session cache of other players' builds, captured while browsing their GUIs:
 * <ul>
 *     <li>{@code /ps <player>} - Player Stats Calculator (equipment)</li>
 *     <li>{@code /pa <player>} - Class Selection GUI (class/spec/abilities)</li>
 *     <li>{@code /vc <player>} - &lt;player&gt;'s Charms (charms)</li>
 * </ul>
 * The command mixin remembers which player a command targeted; the tick
 * tracker parses the matching GUI into this cache. {@code /sts export_build}
 * turns a cached entry back into a build token and exports it as an anonymous
 * link; {@code /sts upload_build} saves it to the caller's account (like the
 * armoury Save button: to the profile when linked, anonymous otherwise).
 * Nothing is written to disk; the cache lives for the game session.
 */
public final class ViewedPlayers {
	/** Which GUI a captured command is about to open. */
	public enum Kind {
		STATS,
		ABILITIES,
		CHARMS
	}

	/**
	 * The target(s) of the last /ps, /pa or /vc command. For /ps the left set
	 * belongs to {@code left} and the right set to {@code right} (the GUI
	 * shows the viewer on the left and the requested player on the right).
	 */
	public record Pending(Kind kind, String left, String right, long at) {
	}

	private static final long PENDING_TTL_MS = 30_000;
	private static final Map<String, CachedPlayer> CACHE = new ConcurrentHashMap<>();
	private static volatile Pending pending;

	private ViewedPlayers() {
	}

	// ---------- command capture ----------

	/** Called by the ClientPacketListener mixin with the raw command text. */
	public static void onCommand(String raw) {
		if (raw == null) {
			return;
		}
		String command = raw.startsWith("/") ? raw.substring(1) : raw;
		String[] parts = command.trim().split("\\s+");
		if (parts.length == 0 || parts[0].isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		switch (parts[0].toLowerCase(Locale.ROOT)) {
			case "ps", "playerstats" -> {
				String first = argument(parts, 1);
				String second = argument(parts, 2);
				String left;
				String right;
				if (second != null) {
					left = first; // /ps p1 p2 -> left set is p1, right set is p2
					right = second;
				} else if (first != null) {
					left = selfName(); // /ps p -> left set is the viewer
					right = first;
				} else {
					left = selfName(); // /ps -> viewer only
					right = null;
				}
				pending = new Pending(Kind.STATS, left, right, now);
			}
			case "pa", "playerabilities" -> pending = new Pending(Kind.ABILITIES, argumentOrSelf(parts, 1), null, now);
			case "vc", "viewcharms" -> pending = new Pending(Kind.CHARMS, argumentOrSelf(parts, 1), null, now);
			default -> {
				// Not a view command; leave any pending target alone.
			}
		}
	}

	private static String argument(String[] parts, int index) {
		if (index >= parts.length) {
			return null;
		}
		String value = parts[index];
		// Player selectors (@p, @a, ...) can't be resolved client-side.
		if (value.isEmpty() || value.startsWith("@")) {
			return null;
		}
		return value;
	}

	private static String argumentOrSelf(String[] parts, int index) {
		String value = argument(parts, index);
		return value != null ? value : selfName();
	}

	private static String selfName() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null ? mc.player.getGameProfile().getName() : null;
	}

	/** The recent command target for a kind, or null when there is none. */
	public static Pending pending(Kind kind) {
		Pending current = pending;
		if (current == null || current.kind() != kind) {
			return null;
		}
		if (System.currentTimeMillis() - current.at() > PENDING_TTL_MS) {
			return null;
		}
		return current;
	}

	public static void clearPending() {
		pending = null;
	}

	// ---------- cache ----------

	public static CachedPlayer get(String name) {
		return name == null ? null : CACHE.get(name.toLowerCase(Locale.ROOT));
	}

	public static CachedPlayer cache(String name) {
		if (name == null) {
			return null;
		}
		return CACHE.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new CachedPlayer(name));
	}

	/** Cached player names, for command suggestions. */
	public static List<String> names() {
		List<String> names = new ArrayList<>();
		for (CachedPlayer player : CACHE.values()) {
			names.add(player.name());
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/**
	 * Generates a shareable builder link for one cached player's build (not
	 * attached to the account) under the given build name, copies it and posts
	 * it to chat as a clickable link.
	 */
	public static void export(String playerName, String buildName) {
		CachedPlayer player = get(playerName);
		if (player == null) {
			ArmouryTracker.showMessage(
				"Nothing cached for " + playerName + " - view them with /ps, /pa or /vc first."
			);
			return;
		}
		String name = cleanBuildName(buildName, "export_build");
		if (name == null) {
			return;
		}
		String token = buildToken(player, name);
		ArmouryTracker.showMessage("Generating " + player.name() + "'s build link as \"" + name + "\"...");
		ArmouryTracker.saveTokenAnonymously(name, token, player.delveInfusions(), basicInfusionsJson(player), true);
	}

	/**
	 * Uploads one cached player's build to the caller's account (linked saves
	 * land on the Discord profile; the site rejects a second build with the
	 * same name).
	 */
	public static void upload(String playerName, String buildName) {
		CachedPlayer player = get(playerName);
		if (player == null) {
			ArmouryTracker.showMessage(
				"Nothing cached for " + playerName + " - view them with /ps, /pa or /vc first."
			);
			return;
		}
		String name = cleanBuildName(buildName, "upload_build");
		if (name == null) {
			return;
		}
		String token = buildToken(player, name);
		JsonArray unknownItems = new JsonArray();
		for (JsonObject payload : player.unknownItems()) {
			unknownItems.add(payload);
		}
		ArmouryTracker.showMessage("Uploading " + player.name() + "'s cached build as \"" + name + "\"...");
		ArmouryTracker.saveToken(name, token, player.delveInfusions(), unknownItems, basicInfusionsJson(player), true);
	}

	/** Per-slot basic infusions as the site's state shape. */
	private static JsonObject basicInfusionsJson(CachedPlayer player) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, BasicInfusion> entry : player.basicInfusions().entrySet()) {
			JsonObject value = new JsonObject();
			value.addProperty("name", entry.getValue().name());
			value.addProperty("level", entry.getValue().level());
			object.add(entry.getKey(), value);
		}
		return object;
	}

	/** Trimmed/limited build name, or null (with a usage hint) when blank. */
	private static String cleanBuildName(String buildName, String command) {
		String name = buildName == null ? "" : buildName.trim();
		if (name.isEmpty()) {
			ArmouryTracker.showMessage("Give the build a name: /sts " + command + " <player> <build name>");
			return null;
		}
		return name.length() > 50 ? name.substring(0, 50) : name;
	}

	private static String buildToken(CachedPlayer player, String name) {
		List<String> itemKeys = new ArrayList<>();
		String[] keys = player.itemKeys();
		for (int i = 0; i < 6; i++) {
			itemKeys.add(keys[i] == null ? "None" : keys[i]);
		}
		String charm = player.charmKeys().isEmpty() ? null : String.join(",", player.charmKeys());
		// Basic infusion levels are the builder's stat inputs (it sums them
		// per type across the six slots); they ride in the token's stat bytes.
		int tenacity = 0;
		int vitality = 0;
		int vigor = 0;
		int focus = 0;
		int perspicacity = 0;
		for (BasicInfusion infusion : player.basicInfusions().values()) {
			switch (infusion.name().toLowerCase(Locale.ROOT)) {
				case "tenacity" -> tenacity += infusion.level();
				case "vitality" -> vitality += infusion.level();
				case "vigor" -> vigor += infusion.level();
				case "focus" -> focus += infusion.level();
				case "perspicacity" -> perspicacity += infusion.level();
				default -> {
					// Acumen has no stat byte.
				}
			}
		}
		int[] stats = { 100, tenacity, vitality, vigor, focus, perspicacity, player.region() };
		return BuildTokenEncoder.encode(
			itemKeys,
			charm,
			name,
			player.className(),
			player.spec(),
			player.skills(),
			player.specSkills(),
			player.enhancements(),
			stats
		);
	}

	/** One basic (normal) infusion applied to an item. */
	public record BasicInfusion(String name, int level) {
	}

	/** One player's captured build pieces; fields only change when new data arrives. */
	public static final class CachedPlayer {
		private final String name;
		private volatile String[] itemKeys = new String[6];
		private volatile List<JsonObject> unknownItems = List.of();
		private volatile List<String> charmKeys = List.of();
		private volatile String className;
		private volatile String spec;
		private volatile List<BuildTokenEncoder.Skill> skills = List.of();
		private volatile List<BuildTokenEncoder.Skill> specSkills = List.of();
		private volatile List<String> enhancements = List.of();
		private volatile int region = 3;
		private volatile Map<String, String> delveInfusions = Map.of();
		private volatile Map<String, BasicInfusion> basicInfusions = Map.of();
		private volatile long updatedAt;
		// Which view GUIs have been parsed for this player, and which have
		// already produced a "Cached ..." notification.
		private final Set<Kind> viewed = ConcurrentHashMap.newKeySet();
		private final Set<Kind> notified = ConcurrentHashMap.newKeySet();

		private CachedPlayer(String name) {
			this.name = name;
		}

		public String name() {
			return name;
		}

		public String[] itemKeys() {
			return itemKeys;
		}

		public List<JsonObject> unknownItems() {
			return unknownItems;
		}

		public List<String> charmKeys() {
			return charmKeys;
		}

		public String className() {
			return className;
		}

		public String spec() {
			return spec;
		}

		public List<BuildTokenEncoder.Skill> skills() {
			return skills;
		}

		public List<BuildTokenEncoder.Skill> specSkills() {
			return specSkills;
		}

		public List<String> enhancements() {
			return enhancements;
		}

		public int region() {
			return region;
		}

		/** Delve infusions by equipment slot (mainhand..boots). */
		public Map<String, String> delveInfusions() {
			return delveInfusions;
		}

		/** Basic (normal) infusions by equipment slot (mainhand..boots). */
		public Map<String, BasicInfusion> basicInfusions() {
			return basicInfusions;
		}

		public long updatedAt() {
			return updatedAt;
		}

		/** True when this view GUI has been parsed for this player. */
		public boolean isViewed(Kind kind) {
			return viewed.contains(kind);
		}

		public boolean markViewed(Kind kind) {
			return viewed.add(kind);
		}

		/** True the first time a "Cached ..." notification is sent for a view. */
		public boolean markNotified(Kind kind) {
			return notified.add(kind);
		}

		public boolean hasEquipment() {
			for (String key : itemKeys) {
				if (key != null && !"None".equals(key)) {
					return true;
				}
			}
			return !unknownItems.isEmpty();
		}

		public boolean hasAbilities() {
			// The class alone is not enough - the class/spec page needs actual
			// ability points or a spec to count as cached.
			return !skills.isEmpty() || !specSkills.isEmpty() || (spec != null && !spec.isEmpty());
		}

		public boolean hasCharms() {
			return !charmKeys.isEmpty();
		}

		/** True when nothing usable has been captured yet. */
		public boolean isEmpty() {
			for (String key : itemKeys) {
				if (key != null && !"None".equals(key)) {
					return false;
				}
			}
			return charmKeys.isEmpty()
				&& skills.isEmpty()
				&& specSkills.isEmpty()
				&& (className == null || className.isEmpty());
		}

		void mergeEquipment(String[] keys, List<JsonObject> unknown) {
			boolean anyItem = false;
			for (String key : keys) {
				if (key != null && !"None".equals(key)) {
					anyItem = true;
				}
			}
			if (!anyItem && unknown.isEmpty()) {
				return;
			}
			this.itemKeys = keys.clone();
			this.unknownItems = mergeUnknown(this.unknownItems, unknown);
			this.updatedAt = System.currentTimeMillis();
		}

		void mergeCharms(List<String> keys, List<JsonObject> unknown) {
			if (keys.isEmpty() && unknown.isEmpty()) {
				return;
			}
			this.charmKeys = List.copyOf(keys);
			this.unknownItems = mergeUnknown(this.unknownItems, unknown);
			this.updatedAt = System.currentTimeMillis();
		}

		/** Sets or clears the player's class (from the class-selection page). */
		void setClass(String className) {
			if (className == null) {
				if (this.className != null) {
					this.className = null;
					this.updatedAt = System.currentTimeMillis();
					SpareTheSympathy.LOGGER.info("[sts] {} has no class", name);
				}
				return;
			}
			if (!className.equalsIgnoreCase(this.className == null ? "" : this.className)) {
				this.className = className;
				this.updatedAt = System.currentTimeMillis();
				SpareTheSympathy.LOGGER.info("[sts] Cached class {} for {}", className, name);
			}
		}

		/** Sets or clears the player's spec (from the skill page's spec items). */
		void setSpec(String spec) {
			if (spec == null) {
				if (this.spec != null) {
					this.spec = null;
					this.updatedAt = System.currentTimeMillis();
					SpareTheSympathy.LOGGER.info("[sts] {} has no spec", name);
				}
				return;
			}
			if (!spec.equalsIgnoreCase(this.spec == null ? "" : this.spec)) {
				this.spec = spec;
				this.updatedAt = System.currentTimeMillis();
				SpareTheSympathy.LOGGER.info("[sts] Cached spec {} for {}", spec, name);
			}
		}

		void mergeAbilities(
			List<BuildTokenEncoder.Skill> skills,
			List<BuildTokenEncoder.Skill> specSkills,
			List<String> enhancements
		) {
			if (skills.isEmpty() && specSkills.isEmpty()) {
				return;
			}
			boolean changed = !this.skills.equals(skills) || !this.specSkills.equals(specSkills);
			if (!skills.isEmpty()) {
				this.skills = List.copyOf(skills);
			}
			if (!specSkills.isEmpty()) {
				this.specSkills = List.copyOf(specSkills);
			}
			if (!enhancements.isEmpty()) {
				this.enhancements = List.copyOf(enhancements);
			}
			this.updatedAt = System.currentTimeMillis();
			if (changed) {
				SpareTheSympathy.LOGGER.info(
					"[sts] Cached {} skill(s) and {} spec skill(s) for {}",
					skills.size(),
					specSkills.size(),
					name
				);
			}
		}

		void mergeRegion(int region) {
			if (region >= 1 && region <= 3) {
				this.region = region;
				this.updatedAt = System.currentTimeMillis();
			}
		}

		/** Adds/updates the delve infusions seen on the equipment tooltips. */
		void mergeDelveInfusions(Map<String, String> infusions) {
			if (infusions == null || infusions.isEmpty()) {
				return;
			}
			java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>(this.delveInfusions);
			merged.putAll(infusions);
			this.delveInfusions = java.util.Collections.unmodifiableMap(merged);
			this.updatedAt = System.currentTimeMillis();
		}

		/** Adds/updates the basic (normal) infusions seen on the tooltips. */
		void mergeBasicInfusions(Map<String, BasicInfusion> infusions) {
			if (infusions == null || infusions.isEmpty()) {
				return;
			}
			java.util.LinkedHashMap<String, BasicInfusion> merged = new java.util.LinkedHashMap<>(this.basicInfusions);
			merged.putAll(infusions);
			this.basicInfusions = java.util.Collections.unmodifiableMap(merged);
			this.updatedAt = System.currentTimeMillis();
		}

		private static List<JsonObject> mergeUnknown(List<JsonObject> existing, List<JsonObject> added) {
			if (added == null || added.isEmpty()) {
				return existing;
			}
			List<JsonObject> merged = new ArrayList<>(existing);
			for (JsonObject payload : added) {
				String name = payload.has("name") ? payload.get("name").getAsString() : "";
				boolean known = merged.stream().anyMatch(
					other -> other.has("name") && other.get("name").getAsString().equals(name)
				);
				if (!known) {
					merged.add(payload);
				}
			}
			return List.copyOf(merged);
		}
	}
}
