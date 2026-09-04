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
}