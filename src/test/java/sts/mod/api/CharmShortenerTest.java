package sts.mod.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharmShortenerTest {
	@Test
	void shortensKeterCore() {
		assertEquals("Ket-r_Core-1-G", CharmShortener.shortenCharm("Keter Core", 1, "Generalist"));
	}

	@Test
	void shortensNihiloCore() {
		assertEquals("Nih-o_Core-3-G", CharmShortener.shortenCharm("Nihilo Core", 3, "Generalist"));
	}

	@Test
	void stripsCharmSuffix() {
		// "Greater Charm" drops the " Charm" suffix (first occurrence only,
		// like the site's JS), leaving "Greater" -> "Gre-ater".
		assertEquals("Gre-ater-2-C", CharmShortener.shortenCharm("Greater Charm", 2, "Cleric"));
	}

	@Test
	void onlyRemovesFirstCharmSuffix() {
		assertEquals("Lesser of Tremors", "Lesser Charm of Tremors".replaceFirst(" Charm", ""));
	}
}
