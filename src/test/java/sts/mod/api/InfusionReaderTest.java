package sts.mod.api;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

	@Test
	void parsesTheTooltipLineShapes() {
		assertEquals("Vitality", InfusionReader.parseBasicInfusionLine("Vitality IV").name());
		assertEquals(4, InfusionReader.parseBasicInfusionLine("Vitality IV").level());
		assertEquals(2, InfusionReader.parseBasicInfusionLine("Tenacity: II").level());
		assertEquals(3, InfusionReader.parseBasicInfusionLine("\u00a7aFocus \u00a773").level());
		assertEquals(1, InfusionReader.parseBasicInfusionLine("Vitality").level());
		assertEquals(4, InfusionReader.parseBasicInfusionLine("\u2726 VitalityIV").level());
		assertEquals(2, InfusionReader.parseBasicInfusionLine("Acumen II - Gain 4% more experience").level());
		assertEquals("Perspicacity", InfusionReader.parseBasicInfusionLine("Perspicacity 1").name());
		assertEquals("Vigor", InfusionReader.parseBasicInfusionLine("  \u00a7e\u00a7lVigor   IV  ").name());
	}

	@Test
	void ignoresLinesThatAreNotInfusions() {
		assertNull(InfusionReader.parseBasicInfusionLine("Boots of Vitality"));
		assertNull(InfusionReader.parseBasicInfusionLine("Vitality Sword IV"));
		assertNull(InfusionReader.parseBasicInfusionLine("Gain 2% more experience"));
		assertNull(InfusionReader.parseBasicInfusionLine(""));
		assertNull(InfusionReader.parseBasicInfusionLine(null));
	}

	@Test
	void parsesDelveLinesWithLeadingGlyphs() {
		assertEquals("Choler", InfusionReader.parseDelveInfusionLine("\u2726 Choler IV"));
		assertEquals("Understanding", InfusionReader.parseDelveInfusionLine("Understanding"));
		assertNull(InfusionReader.parseDelveInfusionLine("Choleric Sword"));
		assertNull(InfusionReader.parseDelveInfusionLine(""));
	}

	@Test
	void parsesTheArmouryPreferredInfusionLine() {
		// The armoury icons carry no level, so the assumed one is used.
		assertEquals("Vitality", InfusionReader.parsePreferredBasicInfusionLine("Preferred Infusion: Vitality", 4).name());
		assertEquals(4, InfusionReader.parsePreferredBasicInfusionLine("Preferred Infusion: Vitality", 4).level());
		assertEquals(
			"Acumen",
			InfusionReader.parsePreferredBasicInfusionLine("  \u00a7fPreferred Infusion: \u00a7aAcumen  ", 3).name()
		);
		assertEquals(3, InfusionReader.parsePreferredBasicInfusionLine("Preferred Infusion: Tenacity", 3).level());
		// "any" and the delve line are not basic infusions.
		assertNull(InfusionReader.parsePreferredBasicInfusionLine("Preferred Infusion: any", 4));
		assertNull(InfusionReader.parsePreferredBasicInfusionLine("Preferred Delve Infusion: Aura", 4));
		assertNull(InfusionReader.parsePreferredBasicInfusionLine("Preferred Infusion:", 4));
		assertNull(InfusionReader.parsePreferredBasicInfusionLine("Vitality IV", 4));
		assertNull(InfusionReader.parsePreferredBasicInfusionLine(null, 4));
	}
}
