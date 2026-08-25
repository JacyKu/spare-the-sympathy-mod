package sts.mod.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record MonumentaStat(double value, boolean locked) {
	public static MonumentaStat fromJson(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}

		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			return new MonumentaStat(element.getAsDouble(), false);
		}

		if (element.isJsonObject()) {
			JsonObject json = element.getAsJsonObject();
			JsonElement valueElement = json.get("value");
			if (valueElement != null && valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
				boolean locked = json.has("locked") && json.get("locked").getAsBoolean();
				return new MonumentaStat(valueElement.getAsDouble(), locked);
			}
		}

		return null;
	}

	public boolean isWholeNumber() {
		return Math.rint(value) == value;
	}

	public int intValue() {
		return (int) Math.round(value);
	}
}
