package sts.mod.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StsConfigTest {
	@Test
	void defaultsToLocalhost3001() {
		assertEquals("http://localhost:3001", StsConfig.normalizeSiteUrl(null));
		assertEquals("http://localhost:3001", StsConfig.normalizeSiteUrl(""));
		assertEquals("http://localhost:3001", StsConfig.normalizeSiteUrl("   "));
	}

	@Test
	void keepsConfiguredUrl() {
		assertEquals("https://sts.deepa.cat", StsConfig.normalizeSiteUrl("https://sts.deepa.cat"));
		assertEquals("http://localhost:8080", StsConfig.normalizeSiteUrl("http://localhost:8080"));
	}

	@Test
	void stripsTrailingSlashes() {
		assertEquals("http://localhost:3001", StsConfig.normalizeSiteUrl("http://localhost:3001/"));
		assertEquals("https://sts.deepa.cat", StsConfig.normalizeSiteUrl("https://sts.deepa.cat///"));
	}

	@Test
	void clampsButtonOffsets() {
		assertEquals(0, StsConfig.clampOffset(0));
		assertEquals(400, StsConfig.clampOffset(400));
		assertEquals(400, StsConfig.clampOffset(9999));
		assertEquals(-400, StsConfig.clampOffset(-9999));
		assertEquals(-37, StsConfig.clampOffset(-37));
	}

	@Test
	void buildStealerMessageDefaultsWhenBlank() {
		assertEquals(StsConfig.DEFAULT_BUILD_STEALER_MESSAGE, StsConfig.normalizeBuildStealerMessage(null));
		assertEquals(StsConfig.DEFAULT_BUILD_STEALER_MESSAGE, StsConfig.normalizeBuildStealerMessage(""));
		assertEquals(StsConfig.DEFAULT_BUILD_STEALER_MESSAGE, StsConfig.normalizeBuildStealerMessage("   "));
		assertEquals(StsConfig.DEFAULT_BUILD_STEALER_MESSAGE, StsConfig.normalizeBuildStealerMessage("/"));
	}

	@Test
	void buildStealerMessageIsSingleLineAndTrimmed() {
		assertEquals("just stole your build!", StsConfig.normalizeBuildStealerMessage("  just stole your build!  "));
		assertEquals("you got robbed!", StsConfig.normalizeBuildStealerMessage("you got\nrobbed!"));
		assertEquals("nice build", StsConfig.normalizeBuildStealerMessage("/nice build"));
	}

	@Test
	void buildStealerMessageIsCapped() {
		String longMessage = "x".repeat(StsConfig.BUILD_STEALER_MESSAGE_MAX + 50);
		assertEquals(StsConfig.BUILD_STEALER_MESSAGE_MAX, StsConfig.normalizeBuildStealerMessage(longMessage).length());
	}
}
