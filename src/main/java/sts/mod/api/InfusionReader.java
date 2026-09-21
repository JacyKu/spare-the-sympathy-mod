package sts.mod.api;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the infusions applied to an item from its tooltip lore. The plugin
 * renders them as "&lt;Name&gt; &lt;Roman level&gt;" lines ("Understanding IV",
 * "Vitality II") in every equipment view - the /ps stats GUI and the
 * Mechanical Armory loadout alike - so the same reader feeds both.
 */
public final class InfusionReader {
	/** One basic (normal) infusion applied to an item. */
	public record BasicInfusion(String name, int level) {
	}

	private InfusionReader() {
	}

	/** Minecraft colour/format codes ("§a"), stripped before parsing. */
	private static final Pattern FORMAT_CODES = Pattern.compile("§.");

	/**
	 * The delve infusion applied to an item, read from its tooltip, or null.
	 * Only the delve/special set is considered - the basic stat infusions use
	 * the same line format and are read by {@link #basicInfusionOn}.
	 */
	public static String delveInfusionOn(ItemStack stack, Player player) {
		for (Component line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
			String display = parseDelveInfusionLine(line.getString());
			if (display != null) {
				return display;
			}
		}
		return null;
	}

	/** One tooltip line -> a delve infusion name, or null. */
	static String parseDelveInfusionLine(String raw) {
		String text = clean(raw);
		if (text.isEmpty()) {
			return null;
		}
		// The name can be preceded by an icon/glyph token, so look at the
		// first few tokens instead of assuming it is the very first one.
		String[] tokens = text.split("\\s+");
		for (int i = 0; i < Math.min(tokens.length, 3); i++) {
			String display = DELVE_INFUSIONS.get(trimEdges(tokens[i]).toLowerCase(Locale.ROOT));
			if (display != null) {
				return display;
			}
		}
		return null;
	}

	/** The basic (normal) infusion applied to an item, or null. */
	public static BasicInfusion basicInfusionOn(ItemStack stack, Player player) {
		for (Component line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
			BasicInfusion parsed = parseBasicInfusionLine(line.getString());
			if (parsed != null) {
				return parsed;
			}
		}
		return null;
	}

	/**
	 * Parses one tooltip line into a basic infusion, or null. The plugin's
	 * exact line shape can vary ("Vitality IV", "Vitality: IV", "VitalityIV",
	 * with or without a leading icon/glyph and colour codes), so this is
	 * deliberately tolerant: any name token followed by a level I-IV (or 1-4)
	 * counts, and a line that is only the name is treated as level I. The
	 * level is required otherwise, so an item that merely has "Vitality" in
	 * its name ("Boots of Vitality") is not mistaken for an infusion.
	 */
	static BasicInfusion parseBasicInfusionLine(String raw) {
		String text = clean(raw);
		if (text.isEmpty()) {
			return null;
		}
		String[] tokens = text.split("\\s+");
		for (int i = 0; i < tokens.length; i++) {
			String token = trimEdges(tokens[i]);
			if (token.isEmpty()) {
				continue;
			}
			String display = BASIC_INFUSIONS.get(token.toLowerCase(Locale.ROOT));
			if (display != null) {
				Integer level = i + 1 < tokens.length ? levelFromToken(trimEdges(tokens[i + 1])) : null;
				if (level != null) {
					return new BasicInfusion(display, level);
				}
				// Level I is rendered without a numeral on a name-only line.
				int meaningful = 0;
				for (String other : tokens) {
					if (!trimEdges(other).isEmpty()) {
						meaningful++;
					}
				}
				if (meaningful == 1) {
					return new BasicInfusion(display, 1);
				}
				return null;
			}
			// Glued form: the level directly follows the name ("VitalityIV").
			String lower = token.toLowerCase(Locale.ROOT);
			for (Map.Entry<String, String> entry : BASIC_INFUSIONS.entrySet()) {
				if (lower.length() > entry.getKey().length() && lower.startsWith(entry.getKey())) {
					Integer level = levelFromToken(trimEdges(token.substring(entry.getKey().length())));
					if (level != null) {
						return new BasicInfusion(entry.getValue(), level);
					}
				}
			}
		}
		return null;
	}

	/** Strips Minecraft colour/format codes and trims the line. */
	private static String clean(String raw) {
		return raw == null ? "" : FORMAT_CODES.matcher(raw).replaceAll("").trim();
	}

	/** Drops leading/trailing punctuation and glyphs ("Vitality:" -> "Vitality"). */
	private static String trimEdges(String token) {
		int start = 0;
		int end = token.length();
		while (start < end && !Character.isLetterOrDigit(token.charAt(start))) {
			start++;
		}
		while (end > start && !Character.isLetterOrDigit(token.charAt(end - 1))) {
			end--;
		}
		return token.substring(start, end);
	}

	private static Integer levelFromToken(String token) {
		if (token == null || token.isEmpty()) {
			return null;
		}
		String lower = token.toLowerCase(Locale.ROOT);
		Integer roman = ROMAN_LEVELS.get(lower);
		if (roman != null) {
			return roman;
		}
		if (lower.length() <= 2 && lower.chars().allMatch(Character::isDigit)) {
			int value = Integer.parseInt(lower);
			if (value >= 1 && value <= 4) {
				return value;
			}
		}
		return null;
	}

	/** Per-slot basic infusions as the site's state shape ({@code slot: {name, level}}). */
	public static JsonObject basicInfusionsJson(Map<String, BasicInfusion> infusions) {
		JsonObject object = new JsonObject();
		if (infusions == null) {
			return object;
		}
		for (Map.Entry<String, BasicInfusion> entry : infusions.entrySet()) {
			JsonObject value = new JsonObject();
			value.addProperty("name", entry.getValue().name());
			value.addProperty("level", entry.getValue().level());
			object.add(entry.getKey(), value);
		}
		return object;
	}

	/** Total levels of one basic infusion type across the slots. */
	public static int basicLevelSum(Map<String, BasicInfusion> infusions, String name) {
		if (infusions == null || name == null) {
			return 0;
		}
		int total = 0;
		for (BasicInfusion infusion : infusions.values()) {
			if (name.equalsIgnoreCase(infusion.name())) {
				total += infusion.level();
			}
		}
		return total;
	}

	/** Plugin display name per lowercased delve/special infusion name. */
	private static final Map<String, String> DELVE_INFUSIONS = Map.ofEntries(
		Map.entry("antigrav", "AntiGrav"),
		Map.entry("ardor", "Ardor"),
		Map.entry("aura", "Aura"),
		Map.entry("bloodlust", "Bloodlust"),
		Map.entry("carapace", "Carapace"),
		Map.entry("celerity", "Celerity"),
		Map.entry("celestial", "Celestial"),
		Map.entry("choler", "Choler"),
		Map.entry("decapitation", "Decapitation"),
		Map.entry("empowered", "Empowered"),
		Map.entry("energize", "Energize"),
		Map.entry("epoch", "Epoch"),
		Map.entry("execution", "Execution"),
		Map.entry("expedite", "Expedite"),
		Map.entry("fervor", "Fervor"),
		Map.entry("fueled", "Fueled"),
		Map.entry("galvanic", "Galvanic"),
		Map.entry("grace", "Grace"),
		Map.entry("mitosis", "Mitosis"),
		Map.entry("natant", "Natant"),
		Map.entry("nutriment", "Nutriment"),
		Map.entry("orbital", "Orbital"),
		Map.entry("pennate", "Pennate"),
		Map.entry("quench", "Quench"),
		Map.entry("reflection", "Reflection"),
		Map.entry("refresh", "Refresh"),
		Map.entry("soothing", "Soothing"),
		Map.entry("sturdy", "Sturdy"),
		Map.entry("understanding", "Understanding"),
		Map.entry("unyielding", "Unyielding"),
		Map.entry("usurper", "Usurper"),
		Map.entry("vengeful", "Vengeful")
	);

	/** Plugin display name per lowercased basic (normal) infusion name. */
	private static final Map<String, String> BASIC_INFUSIONS = Map.of(
		"tenacity", "Tenacity",
		"vitality", "Vitality",
		"vigor", "Vigor",
		"focus", "Focus",
		"perspicacity", "Perspicacity",
		"acumen", "Acumen"
	);

	private static final Map<String, Integer> ROMAN_LEVELS = Map.of(
		"i", 1,
		"ii", 2,
		"iii", 3,
		"iv", 4
	);
}
