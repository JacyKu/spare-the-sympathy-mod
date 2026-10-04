package sts.mod.api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonumentaItemDefinitionTest {
	private static MonumentaItemDefinition item(String key, String name, String masterwork) {
		return new MonumentaItemDefinition(
			key,
			name,
			"",
			"",
			"",
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

	@Test
	void renamesExaltedMasterworkVariants() {
		Map<String, MonumentaItemDefinition> byKey = new LinkedHashMap<>();
		byKey.put("Primordial Flames", item("Primordial Flames", "Primordial Flames", ""));
		byKey.put("Primordial Flames-2", item("Primordial Flames-2", "Primordial Flames", "2"));
		byKey.put("Primordial Flames-4", item("Primordial Flames-4", "Primordial Flames", "4"));
		byKey.put("God Tamer-1", item("God Tamer-1", "God Tamer", "1"));
		byKey.put("Ex Nihil", item("Ex Nihil", "Ex Nihil", ""));

		Map<String, MonumentaItemDefinition> result = MonumentaItemDefinition.applyExaltedRenames(byKey);

		assertEquals(5, result.size());

		MonumentaItemDefinition ex2 = result.get("EX Primordial Flames-2");
		assertEquals("EX Primordial Flames", ex2.name());
		assertEquals("EX Primordial Flames-2", ex2.key());

		MonumentaItemDefinition ex4 = result.get("EX Primordial Flames-4");
		assertEquals("EX Primordial Flames", ex4.name());
		assertEquals("EX Primordial Flames-4", ex4.key());

		assertEquals("Primordial Flames", result.get("Primordial Flames").name());
		assertEquals("God Tamer", result.get("God Tamer-1").name());
		assertEquals("Ex Nihil", result.get("Ex Nihil").name());

		assertTrue(result.containsKey("Primordial Flames"));
		assertTrue(result.containsKey("EX Primordial Flames-2"));
		assertTrue(result.containsKey("EX Primordial Flames-4"));
		assertTrue(result.containsKey("God Tamer-1"));
		assertTrue(result.containsKey("Ex Nihil"));
	}

	@Test
	void appliesSiteKeyRenames() {
		Map<String, MonumentaItemDefinition> byKey = new LinkedHashMap<>();
		byKey.put("Truest North-1 (compass)", item("Truest North-1 (compass)", "Truest North", ""));
		byKey.put("Truest North-1 (shears)", item("Truest North-1 (shears)", "Truest North", ""));
		byKey.put("Carcano 91/38", item("Carcano 91/38", "Carcano 91/38", ""));
		byKey.put("Primordial Flames", item("Primordial Flames", "Primordial Flames", ""));
		byKey.put("Primordial Flames-4", item("Primordial Flames-4", "Primordial Flames", "4"));

		Map<String, MonumentaItemDefinition> result = MonumentaItemDefinition.applySiteKeyRenames(byKey);

		assertEquals(4, result.size());
		assertEquals("Truest North", result.get("Truest North-1").name());
		assertEquals("Truest North-1", result.get("Truest North-1").key());
		assertFalse(result.containsKey("Truest North-1 (shears)"));
		assertEquals("Carcano 9138", result.get("Carcano 9138").key());
		assertFalse(result.containsKey("Carcano 91/38"));
		// Exalted renames still run after the key rewrites.
		assertEquals("EX Primordial Flames", result.get("EX Primordial Flames-4").name());
	}

	@Test
	void preservesOrderAndReusesUnchangedEntries() {
		Map<String, MonumentaItemDefinition> byKey = new LinkedHashMap<>();
		MonumentaItemDefinition base = item("Primordial Flames", "Primordial Flames", "");
		MonumentaItemDefinition ex2 = item("Primordial Flames-2", "Primordial Flames", "2");
		byKey.put("Primordial Flames", base);
		byKey.put("Primordial Flames-2", ex2);

		Map<String, MonumentaItemDefinition> result = MonumentaItemDefinition.applyExaltedRenames(byKey);

		assertEquals(List.of("Primordial Flames", "EX Primordial Flames-2"), List.copyOf(result.keySet()));
		assertSame(base, result.get("Primordial Flames"));
		assertEquals("EX Primordial Flames", result.get("EX Primordial Flames-2").name());
	}
}
