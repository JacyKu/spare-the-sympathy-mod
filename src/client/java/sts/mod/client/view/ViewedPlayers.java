package sts.mod.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * tracker parses the matching GUI into this cache; {@code /sts export <name>}
 * turns a cached entry back into a build token and saves it (like the
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
	 * Exports one cached player as a build (save + copy link) under the given
	 * build name. The name is required - the site rejects a second build with
	 * the same name, so exporting the same player twice needs a fresh name
	 * (re-exporting an unchanged build with the same name just updates it).
	 */
	public static void export(String playerName, String buildName) {
		CachedPlayer player = get(playerName);
		if (player == null) {
			ArmouryTracker.showMessage(
				"Nothing cached for " + playerName + " - view them with /ps, /pa or /vc first."
			);
			return;
		}
		String name = buildName == null ? "" : buildName.trim();
		if (name.isEmpty()) {
			ArmouryTracker.showMessage("Give the build a name: /sts export <player> <build name>");
			return;
		}
		if (name.length() > 30) {
			name = name.substring(0, 30);
		}
		List<String> itemKeys = new ArrayList<>();
		String[] keys = player.itemKeys();
		for (int i = 0; i < 6; i++) {
			itemKeys.add(keys[i] == null ? "None" : keys[i]);
		}
		String charm = player.charmKeys().isEmpty() ? null : String.join(",", player.charmKeys());
		int[] stats = { 100, 0, 0, 0, 0, 0, player.region() };
		String token = BuildTokenEncoder.encode(
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
		JsonArray unknownItems = new JsonArray();
		for (JsonObject payload : player.unknownItems()) {
			unknownItems.add(payload);
		}
		ArmouryTracker.showMessage("Exporting " + player.name() + "'s cached build as \"" + name + "\"...");
		ArmouryTracker.saveToken(name, token, null, unknownItems, true);
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
		private volatile long updatedAt;

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

		public long updatedAt() {
			return updatedAt;
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
