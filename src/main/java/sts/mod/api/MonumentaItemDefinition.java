package sts.mod.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MonumentaItemDefinition(
	String key,
	String name,
	String baseItemName,
	String rawNbt,
	String type,
	String tier,
	String region,
	String location,
	String className,
	String releaseStatus,
	String masterwork,
	int power,
	List<String> lore,
	List<String> mmLore,
	Map<String, MonumentaStat> stats
) {
	/**
	 * Rewrites the raw API dictionary the way the site's processed dictionary
	 * does, so every key the mod exports resolves there (an unresolvable key
	 * is dropped by the site's sanitizer and turns the whole token into a
	 * legacy one): Truest North compass keys lose their suffix, the shears
	 * variants are dropped, and the Carcano's "/38" spelling is removed.
	 * Exalted (masterwork) variants are renamed after that, matching the
	 * site's order.
	 */
	public static Map<String, MonumentaItemDefinition> applySiteKeyRenames(Map<String, MonumentaItemDefinition> byKey) {
		Map<String, MonumentaItemDefinition> result = new LinkedHashMap<>(byKey.size());
		for (Map.Entry<String, MonumentaItemDefinition> entry : byKey.entrySet()) {
			String key = entry.getKey();
			MonumentaItemDefinition def = entry.getValue();
			if (key.startsWith("Truest North-") && key.endsWith(" (compass)")) {
				String bare = key.substring(0, key.length() - " (compass)".length());
				result.put(bare, withKey(def, bare));
				continue;
			}
			if (key.startsWith("Truest North-") && key.endsWith(" (shears)")) {
				continue;
			}
			if (key.equals("Carcano 91/38")) {
				result.put("Carcano 9138", withKey(def, "Carcano 9138"));
				continue;
			}
			result.put(key, def);
		}
		return applyExaltedRenames(result);
	}

	public static Map<String, MonumentaItemDefinition> applyExaltedRenames(Map<String, MonumentaItemDefinition> byKey) {
		Map<String, MonumentaItemDefinition> result = new LinkedHashMap<>(byKey.size());
		for (Map.Entry<String, MonumentaItemDefinition> entry : byKey.entrySet()) {
			String key = entry.getKey();
			MonumentaItemDefinition def = entry.getValue();
			String masterwork = def.masterwork();
			if (masterwork != null && !masterwork.isEmpty()
				&& !def.name().equals(key)
				&& byKey.containsKey(def.name())) {
				// The site's processed dictionary rewrites masterwork variants
				// to "EX <name>-<level>" (display name and key), and build
				// tokens hash the key - so the dictionary has to expose the
				// same key or exported EX items never resolve on the site.
				String exName = "EX " + def.name();
				String exKey = exName + "-" + masterwork;
				result.put(exKey, new MonumentaItemDefinition(
					exKey,
					exName,
					def.baseItemName(),
					def.rawNbt(),
					def.type(),
					def.tier(),
					def.region(),
					def.location(),
					def.className(),
					def.releaseStatus(),
					def.masterwork(),
					def.power(),
					def.lore(),
					def.mmLore(),
					def.stats()
				));
			} else {
				result.put(key, def);
			}
		}
		return result;
	}

	private static MonumentaItemDefinition withKey(MonumentaItemDefinition def, String key) {
		return new MonumentaItemDefinition(
			key,
			def.name(),
			def.baseItemName(),
			def.rawNbt(),
			def.type(),
			def.tier(),
			def.region(),
			def.location(),
			def.className(),
			def.releaseStatus(),
			def.masterwork(),
			def.power(),
			def.lore(),
			def.mmLore(),
			def.stats()
		);
	}

	public static MonumentaItemDefinition fromJson(String key, JsonObject json) {
		String name = readString(json, "name");
		String baseItemName = readString(json, "base_item");
		String rawNbt = readString(json, "nbt");
		String type = readString(json, "type");
		String tier = readString(json, "tier");
		String region = readString(json, "region");
		String location = readString(json, "location");
		String className = readString(json, "class_name");
		String releaseStatus = readString(json, "release_status");
		String masterwork = readString(json, "masterwork");
		int power = readInt(json, "power");
		List<String> lore = readLore(json, "lore");
		List<String> mmLore = readStringList(json, "mmlore");
		Map<String, MonumentaStat> stats = readStats(json.get("stats"));

		return new MonumentaItemDefinition(
			key,
			name.isEmpty() ? key : name,
			baseItemName,
			rawNbt,
			type,
			tier,
			region,
			location,
			className,
			releaseStatus,
			masterwork,
			power,
			List.copyOf(lore),
			List.copyOf(mmLore),
			Map.copyOf(stats)
		);
	}

	private static int readInt(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return 0;
		}
		try {
			return element.getAsInt();
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String readString(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString().trim();
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			List<String> values = new ArrayList<>(array.size());
			for (JsonElement entry : array) {
				if (entry != null && entry.isJsonPrimitive()) {
					String value = entry.getAsString().trim();
					if (!value.isEmpty()) {
						values.add(value);
					}
				}
			}
			return String.join(", ", values);
		}
		return "";
	}

	private static List<String> readLore(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (element.isJsonPrimitive()) {
			String value = element.getAsString().trim();
			if (value.isEmpty()) {
				return List.of();
			}
			String[] lines = value.split("\\R");
			List<String> parsed = new ArrayList<>(lines.length);
			for (String line : lines) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					parsed.add(trimmed);
				}
			}
			return parsed;
		}
		return readStringList(json, key);
	}

	private static List<String> readStringList(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (element.isJsonPrimitive()) {
			String value = element.getAsString().trim();
			return value.isEmpty() ? List.of() : List.of(value);
		}
		if (!element.isJsonArray()) {
			return List.of();
		}

		List<String> values = new ArrayList<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			if (entry != null && entry.isJsonPrimitive()) {
				String value = entry.getAsString().trim();
				if (!value.isEmpty()) {
					values.add(value);
				}
			}
		}
		return values;
	}

	private static Map<String, MonumentaStat> readStats(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonObject()) {
			return Map.of();
		}

		Map<String, MonumentaStat> stats = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			MonumentaStat stat = MonumentaStat.fromJson(entry.getValue());
			if (stat != null) {
				stats.put(entry.getKey(), stat);
			}
		}
		return stats;
	}
}
