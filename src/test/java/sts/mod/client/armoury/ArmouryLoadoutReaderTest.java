package sts.mod.client.armoury;

import org.junit.jupiter.api.Test;
import sts.mod.api.MonumentaItemDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmouryLoadoutReaderTest {
	private static MonumentaItemDefinition item(String key, String name, String baseItem, String type, String masterwork) {
		return new MonumentaItemDefinition(
			key,
			name,
			baseItem,
			"",
			type,
			"",
			"",
			"",
			"",
			"",
			masterwork,
			0,
			List.of(),
			List.of(),
			Map.of()
		);
	}

	private static List<MonumentaItemDefinition> crimsonSet() {
		return List.of(
			item("Primordial Flames", "Primordial Flames", "Blaze Powder", "Offhand", ""),
			item("EX Primordial Flames-2", "EX Primordial Flames", "Blaze Powder", "Offhand", "2"),
			item("EX Primordial Flames-3", "EX Primordial Flames", "Blaze Powder", "Offhand", "3"),
			item("EX Primordial Flames-4", "EX Primordial Flames", "Blaze Powder", "Offhand", "4")
		);
	}

	@Test
	void classifiesTheTooltipMasterworkMarkers() {
		assertEquals(0, ArmouryLoadoutReader.masterworkLevelFromLines(List.of()));
		assertEquals(0, ArmouryLoadoutReader.masterworkLevelFromLines(List.of("Primordial Flames", "Cloaked I")));
		assertEquals(2, ArmouryLoadoutReader.masterworkLevelFromLines(List.of("Masterwork : \u2605\u2605")));
		assertEquals(4, ArmouryLoadoutReader.masterworkLevelFromLines(List.of("Masterwork: \u2605\u2605\u2605\u2605")));
		assertEquals(-1, ArmouryLoadoutReader.masterworkLevelFromLines(List.of("Exalted version", "Preferred Infusion: any")));
		// The exact stars win when both markers are present.
		assertEquals(
			3,
			ArmouryLoadoutReader.masterworkLevelFromLines(List.of("Exalted version", "Masterwork : \u2605\u2605\u2605"))
		);
	}

	@Test
	void resolvesTheArmoryExaltedFlagToTheHighestVariant() {
		List<MonumentaItemDefinition> items = crimsonSet();
		assertEquals("EX Primordial Flames-4", ArmouryLoadoutReader.resolveItemKey("Primordial Flames", "Blaze Powder", -1, items));
	}

	@Test
	void resolvesBaseItemsToTheBaseEntry() {
		List<MonumentaItemDefinition> items = crimsonSet();
		assertEquals("Primordial Flames", ArmouryLoadoutReader.resolveItemKey("Primordial Flames", "Blaze Powder", 0, items));
	}

	@Test
	void resolvesExactMasterworkLevels() {
		List<MonumentaItemDefinition> items = crimsonSet();
		assertEquals("EX Primordial Flames-3", ArmouryLoadoutReader.resolveItemKey("Primordial Flames", "Blaze Powder", 3, items));
	}

	@Test
	void resolvesItemsWithoutABaseEntryToTheirLowestVariant() {
		List<MonumentaItemDefinition> items = List.of(
			item("God Tamer-1", "God Tamer", "Blaze Powder", "Offhand", "1"),
			item("God Tamer-2", "God Tamer", "Blaze Powder", "Offhand", "2"),
			item("God Tamer-3", "God Tamer", "Blaze Powder", "Offhand", "3"),
			item("God Tamer-4", "God Tamer", "Blaze Powder", "Offhand", "4")
		);
		assertEquals("God Tamer-1", ArmouryLoadoutReader.resolveItemKey("God Tamer", "Blaze Powder", 0, items));
		assertEquals("God Tamer-3", ArmouryLoadoutReader.resolveItemKey("God Tamer", "Blaze Powder", 3, items));
		assertEquals("God Tamer-4", ArmouryLoadoutReader.resolveItemKey("God Tamer", "Blaze Powder", -1, items));
	}

	@Test
	void fallsBackToNameOnlyMatchesWhenTheMaterialSpellingDiffers() {
		List<MonumentaItemDefinition> items = List.of(
			item("Deadair Purifier-3", "Deadair Purifier", "Iron Helmet", "Helmet", "3"),
			item("Deadair Purifier-4", "Deadair Purifier", "Iron Helmet", "Helmet", "4")
		);
		assertEquals("Deadair Purifier-3", ArmouryLoadoutReader.resolveItemKey("Deadair Purifier", "Carved Pumpkin", 0, items));
	}

	@Test
	void doesNotMatchUnknownNames() {
		assertEquals("None", ArmouryLoadoutReader.resolveItemKey("Unknown Item", "Blaze Powder", 0, crimsonSet()));
		assertEquals("None", ArmouryLoadoutReader.resolveItemKey("Primordial Flames", "Blaze Powder", 0, List.of()));
	}
}
