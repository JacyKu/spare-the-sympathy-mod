package sts.mod.api;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.Locale;
import java.util.Map;

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

	/**
	 * The delve infusion applied to an item, read from its tooltip, or null.
	 * Only the delve/special set is considered - the basic stat infusions use
	 * the same line format and are read by {@link #basicInfusionOn}.
	 */
	public static String delveInfusionOn(ItemStack stack, Player player) {
		for (Component line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
			String text = line.getString().trim();
			if (text.isEmpty()) {
				continue;
			}
			String first = text.split(" ")[0];
			String display = DELVE_INFUSIONS.get(first.toLowerCase(Locale.ROOT));
			if (display != null) {
				return display;
			}
		}
		return null;
	}

	/** The basic (normal) infusion applied to an item, or null. */
	public static BasicInfusion basicInfusionOn(ItemStack stack, Player player) {
		for (Component line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
			String text = line.getString().trim();
			if (text.isEmpty()) {
				continue;
			}
			String[] parts = text.split(" ");
			String display = BASIC_INFUSIONS.get(parts[0].toLowerCase(Locale.ROOT));
			if (display == null) {
				continue;
			}
			int level = parts.length > 1 ? ROMAN_LEVELS.getOrDefault(parts[1].toLowerCase(Locale.ROOT), 1) : 1;
			return new BasicInfusion(display, level);
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
