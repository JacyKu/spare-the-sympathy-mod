package sts.mod.api;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfusionReaderTest {
	private static Map<String, InfusionReader.BasicInfusion> infusions() {
		Map<String, InfusionReader.BasicInfusion> map = new LinkedHashMap<>();
		map.put("mainhand", new InfusionReader.BasicInfusion("Vitality", 4));
		map.put("chestplate", new InfusionReader.BasicInfusion("Vitality", 2));
		map.put("boots", new InfusionReader.BasicInfusion("Tenacity", 3));
		map.put("helmet", new InfusionReader.BasicInfusion("Acumen", 4));
		return map;
	}

	@Test
	void sumsBasicLevelsPerTypeAcrossSlots() {
		Map<String, InfusionReader.BasicInfusion> map = infusions();
		assertEquals(6, InfusionReader.basicLevelSum(map, "Vitality"));
		assertEquals(6, InfusionReader.basicLevelSum(map, "vitality"));
		assertEquals(3, InfusionReader.basicLevelSum(map, "Tenacity"));
		assertEquals(0, InfusionReader.basicLevelSum(map, "Focus"));
		assertEquals(0, InfusionReader.basicLevelSum(null, "Vitality"));
	}

	@Test
	void buildsTheSitesBasicInfusionStateShape() {
		JsonObject json = InfusionReader.basicInfusionsJson(infusions());
		assertEquals(4, json.size());
		JsonObject mainhand = json.getAsJsonObject("mainhand");
		assertEquals("Vitality", mainhand.get("name").getAsString());
		assertEquals(4, mainhand.get("level").getAsInt());
		assertEquals("Acumen", json.getAsJsonObject("helmet").get("name").getAsString());
		assertEquals(0, InfusionReader.basicInfusionsJson(null).size());
	}
}
