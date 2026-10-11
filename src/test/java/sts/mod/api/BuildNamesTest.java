package sts.mod.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildNamesTest {
	@Test
	void trimsAndFallsBackWhenBlank() {
		assertEquals("My Build", BuildNames.resolve("  My Build  ", "Default"));
		assertEquals("Armoury Loadout", BuildNames.resolve("", "Armoury Loadout"));
		assertEquals("Armoury Loadout", BuildNames.resolve("   ", "Armoury Loadout"));
		assertEquals("Default", BuildNames.resolve(null, "Default"));
	}

	@Test
	void fallsBackToEmptyWhenNothingIsGiven() {
		assertEquals("", BuildNames.resolve(null, null));
		assertEquals("", BuildNames.resolve("  ", "  "));
	}

	@Test
	void capsAtTheSitesLimit() {
		assertEquals(50, BuildNames.resolve("x".repeat(80), "Default").length());
		assertEquals(50, BuildNames.resolve("y".repeat(50), "Default").length());
	}
}
