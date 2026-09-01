package sts.mod.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import sts.mod.SpareTheSympathy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mod's user-facing config, stored at
 * {@code config/sparethesympathy/config.json}:
 * <pre>
 * { "siteUrl": "http://localhost:3001" }
 * </pre>
 * Defaults to the local development site (port 3001). Point it at the real
 * site ({@code https://sts.deepa.cat}) for production use.
 */
public final class StsConfig {
	private static final String FILE_NAME = "config.json";
	private static final String DEFAULT_SITE_URL = "http://localhost:3001";

	private static volatile String siteUrl = DEFAULT_SITE_URL;

	private StsConfig() {
	}

	/** The configured site origin (no trailing slash). */
	public static String siteUrl() {
		return siteUrl;
	}

	/** Loads (or creates) the config file. Call once at startup. */
	public static void load() {
		Path configDir;
		try {
			configDir = FabricLoader.getInstance().getConfigDir().resolve("sparethesympathy");
		} catch (RuntimeException e) {
			SpareTheSympathy.LOGGER.warn("No config dir available, keeping default site URL", e);
			return;
		}
		Path configFile = configDir.resolve(FILE_NAME);
		if (Files.exists(configFile)) {
			try {
				siteUrl = parse(Files.readString(configFile, StandardCharsets.UTF_8));
			} catch (IOException | RuntimeException e) {
				SpareTheSympathy.LOGGER.warn("Could not read {}, keeping defaults", configFile, e);
			}
		} else {
			try {
				Files.createDirectories(configDir);
				Files.writeString(configFile,
					"{\n\t\"siteUrl\": \"" + DEFAULT_SITE_URL + "\"\n}\n",
					StandardCharsets.UTF_8);
				SpareTheSympathy.LOGGER.info("Wrote default config to {}", configFile);
			} catch (IOException e) {
				SpareTheSympathy.LOGGER.warn("Could not write default config to {}", configFile, e);
			}
		}
	}

	// Extracted for unit testing (FabricLoader is not available there).
	static String parse(String raw) {
		JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
		String url = json.has("siteUrl") && json.get("siteUrl").isJsonPrimitive()
			? json.get("siteUrl").getAsString()
			: DEFAULT_SITE_URL;
		if (url == null || url.isBlank()) {
			url = DEFAULT_SITE_URL;
		}
		return url.replaceAll("/+$", "");
	}
}
