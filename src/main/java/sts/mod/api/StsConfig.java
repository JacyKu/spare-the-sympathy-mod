package sts.mod.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.loader.api.FabricLoader;
import sts.mod.SpareTheSympathy;
import sts.mod.config.StsModConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Facade over the Cloth Config autoconfig holder for {@link StsModConfig}.
 * The config is stored by autoconfig at
 * {@code config/sparethesympathy.json}. On first run any legacy config at
 * {@code config/sparethesympathy/config.json} is migrated so users keep their
 * {@code siteUrl} setting.
 */
public final class StsConfig {
	private static final String DEFAULT_SITE_URL = "http://localhost:3001";

	private StsConfig() {
	}

	/** The configured site origin (no trailing slash). */
	public static String siteUrl() {
		return normalizeSiteUrl(config().siteUrl);
	}

	/**
	 * One-time legacy migration from {@code config/sparethesympathy/config.json}.
	 * Must be called after {@code AutoConfig.register(StsModConfig.class, ...)}.
	 * Never throws; problems are logged as warnings.
	 */
	public static void load() {
		try {
			Path configFile = FabricLoader.getInstance().getConfigDir()
				.resolve("sparethesympathy")
				.resolve("config.json");
			if (!Files.exists(configFile)) {
				return;
			}
			String url = readSiteUrl(Files.readString(configFile, StandardCharsets.UTF_8));
			config().siteUrl = normalizeSiteUrl(url);
			AutoConfig.getConfigHolder(StsModConfig.class).save();
			SpareTheSympathy.LOGGER.info("Migrated legacy config from {} into autoconfig", configFile);
		} catch (Exception e) {
			SpareTheSympathy.LOGGER.warn("Could not migrate legacy config, keeping defaults", e);
		}
	}

	/** Pure helper: null/blank -> default, otherwise trailing slashes stripped. */
	public static String normalizeSiteUrl(String url) {
		if (url == null || url.isBlank()) {
			return DEFAULT_SITE_URL;
		}
		return url.replaceAll("/+$", "");
	}

	private static StsModConfig config() {
		return AutoConfig.getConfigHolder(StsModConfig.class).getConfig();
	}

	private static String readSiteUrl(String raw) {
		try {
			JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
			JsonElement element = json.get("siteUrl");
			if (element == null || !element.isJsonPrimitive()) {
				return null;
			}
			return element.getAsString();
		} catch (RuntimeException e) {
			return null;
		}
	}
}