package sts.mod.api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

		MonumentaItemDefinition ex2 = result.get("Primordial Flames-2");
		assertEquals("EX Primordial Flames", ex2.name());
		assertEquals("Primordial Flames-2", ex2.key());

		MonumentaItemDefinition ex4 = result.get("Primordial Flames-4");
		assertEquals("EX Primordial Flames", ex4.name());
		assertEquals("Primordial Flames-4", ex4.key());

		assertEquals("Primordial Flames", result.get("Primordial Flames").name());
		assertEquals("God Tamer", result.get("God Tamer-1").name());
		assertEquals("Ex Nihil", result.get("Ex Nihil").name());

		assertTrue(result.containsKey("Primordial Flames"));
		assertTrue(result.containsKey("Primordial Flames-2"));
		assertTrue(result.containsKey("Primordial Flames-4"));
		assertTrue(result.containsKey("God Tamer-1"));
		assertTrue(result.containsKey("Ex Nihil"));
	}

	@Test
	void preservesOrderAndReusesUnchangedEntries() {
		Map<String, MonumentaItemDefinition> byKey = new LinkedHashMap<>();
		MonumentaItemDefinition base = item("Primordial Flames", "Primordial Flames", "");
		MonumentaItemDefinition ex2 = item("Primordial Flames-2", "Primordial Flames", "2");
		byKey.put("Primordial Flames", base);
		byKey.put("Primordial Flames-2", ex2);

		Map<String, MonumentaItemDefinition> result = MonumentaItemDefinition.applyExaltedRenames(byKey);

		assertEquals(List.of("Primordial Flames", "Primordial Flames-2"), List.copyOf(result.keySet()));
		assertSame(base, result.get("Primordial Flames"));
		assertEquals("EX Primordial Flames", result.get("Primordial Flames-2").name());
	}
}