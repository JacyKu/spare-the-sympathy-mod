package sts.mod.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StsConfigTest {
	@Test
	void defaultsToLocalhost3001() {
		assertEquals("http://localhost:3001", StsConfig.parse("{}"));
		assertEquals("http://localhost:3001", StsConfig.parse("{\"siteUrl\": \"\"}"));
	}

	@Test
	void readsConfiguredUrl() {
		assertEquals("https://sts.deepa.cat", StsConfig.parse("{\"siteUrl\": \"https://sts.deepa.cat\"}"));
	}

	@Test
	void stripsTrailingSlashes() {
		assertEquals("http://localhost:3001", StsConfig.parse("{\"siteUrl\": \"http://localhost:3001/\"}"));
	}

	@Test
	void ignoresUnknownKeys() {
		assertEquals("http://localhost:3001", StsConfig.parse("{\"dumpAllScreens\": false}"));
	}
}
