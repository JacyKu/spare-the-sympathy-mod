package sts.mod.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.loader.api.FabricLoader;
import sts.mod.SpareTheSympathy;
import sts.mod.config.ArmouryButton;
import sts.mod.config.ArmourySide;
import sts.mod.config.StsModConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Facade over the Cloth Config autoconfig holder for {@link StsModConfig}.
 * The config is stored by autoconfig at
 * {@code config/sparethesympathy.json}. On first run any legacy config at
 * {@code config/sparethesympathy/config.json} is migrated so users keep their
 * {@code siteUrl} setting, and the old armoury button position file is folded
 * into the per-button position fields.
 */
public final class StsConfig {
	/** Where the mod points when no site URL has been configured. */
	public static final String DEFAULT_SITE_URL = "https://sts.deepa.cat";

	/** How far each armoury button can be moved from its anchor. */
	public static final int ARMOURY_BUTTON_OFFSET_LIMIT = 400;

	/** Default /msg text for /buildstealer2000 when the option is enabled. */
	public static final String DEFAULT_BUILD_STEALER_MESSAGE = "just stole your build!";
	/** Upper bound for the configurable taunt (server chat line limits). */
	public static final int BUILD_STEALER_MESSAGE_MAX = 200;

	private StsConfig() {
	}

	/** The configured site origin (no trailing slash). */
	public static String siteUrl() {
		return normalizeSiteUrl(config().siteUrl);
	}

	public static ArmourySide armouryButtonSide(ArmouryButton button) {
		StsModConfig config = config();
		boolean onLeft = switch (button) {
			case EXPORT -> config.exportOnLeft;
			case SAVE -> config.saveOnLeft;
			case LINK -> config.linkOnLeft;
		};
		return onLeft ? ArmourySide.LEFT : ArmourySide.RIGHT;
	}

	public static int armouryButtonX(ArmouryButton button) {
		StsModConfig config = config();
		return clampOffset(switch (button) {
			case EXPORT -> config.exportX;
			case SAVE -> config.saveX;
			case LINK -> config.linkX;
		});
	}

	public static int armouryButtonY(ArmouryButton button) {
		StsModConfig config = config();
		return clampOffset(switch (button) {
			case EXPORT -> config.exportY;
			case SAVE -> config.saveY;
			case LINK -> config.linkY;
		});
	}

	/**
	 * Stores a new position for one button (Ctrl+dragging it in-game calls
	 * this), so the config screen reflects the position the player set.
	 */
	public static void setArmouryButtonPosition(ArmouryButton button, int x, int y) {
		StsModConfig config = config();
		int clampedX = clampOffset(x);
		int clampedY = clampOffset(y);
		switch (button) {
			case EXPORT -> {
				config.exportX = clampedX;
				config.exportY = clampedY;
			}
			case SAVE -> {
				config.saveX = clampedX;
				config.saveY = clampedY;
			}
			case LINK -> {
				config.linkX = clampedX;
				config.linkY = clampedY;
			}
		}
		AutoConfig.getConfigHolder(StsModConfig.class).save();
	}

	/**
	 * One-time migrations into autoconfig. Must be called after
	 * {@code AutoConfig.register(StsModConfig.class, ...)}.
	 * Never throws; problems are logged as warnings.
	 */
	public static void load() {
		migrateLegacySiteUrl();
		migrateLegacyButtonPosition();
	}

	/**
	 * Pure migration guard: the legacy file is only consulted when the
	 * autoconfig file does not exist yet (a true first run). Without this the
	 * migration re-ran on every launch, silently overwriting whatever the
	 * player had set in the config screen with the legacy value.
	 */
	public static boolean shouldMigrateLegacySiteUrl(boolean legacyFileExists, boolean autoconfigFileExists) {
		return legacyFileExists && !autoconfigFileExists;
	}

	private static void migrateLegacySiteUrl() {
		try {
			Path configDir = FabricLoader.getInstance().getConfigDir().resolve("sparethesympathy");
			Path configFile = configDir.resolve("config.json");
			if (!Files.exists(configFile)) {
				return;
			}
			Path autoconfigFile = FabricLoader.getInstance().getConfigDir().resolve("sparethesympathy.json");
			if (!shouldMigrateLegacySiteUrl(true, Files.exists(autoconfigFile))) {
				// The new config already exists and is the source of truth.
				// Drop the legacy file so a later launch can never overwrite
				// the player's setting again.
				Files.deleteIfExists(configFile);
				return;
			}
			String url = readSiteUrl(Files.readString(configFile, StandardCharsets.UTF_8));
			if (url != null && !url.isBlank()) {
				config().siteUrl = normalizeSiteUrl(url);
				AutoConfig.getConfigHolder(StsModConfig.class).save();
				SpareTheSympathy.LOGGER.info("Migrated legacy config from {} into autoconfig", configFile);
			}
			Files.deleteIfExists(configFile);
		} catch (Exception e) {
			SpareTheSympathy.LOGGER.warn("Could not migrate legacy config, keeping defaults", e);
		}
	}

	/**
	 * The armoury buttons used to share one position in
	 * {@code armoury-buttons.json}; split it across the per-button fields
	 * (unless the player already set positions in the config) and remove the
	 * file.
	 */
	private static void migrateLegacyButtonPosition() {
		try {
			Path positionFile = FabricLoader.getInstance().getConfigDir()
				.resolve("sparethesympathy")
				.resolve("armoury-buttons.json");
			if (!Files.exists(positionFile)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(positionFile, StandardCharsets.UTF_8))
				.getAsJsonObject();
			int x = json.has("offsetX") ? json.get("offsetX").getAsInt() : 0;
			int y = json.has("offsetY") ? json.get("offsetY").getAsInt() : 0;
			StsModConfig config = config();
			if (atDefaultPositions(config)) {
				// Keep the old 26px stacking between the buttons.
				config.exportX = clampOffset(x);
				config.exportY = clampOffset(y);
				config.saveX = clampOffset(x);
				config.saveY = clampOffset(y + 26);
				config.linkX = clampOffset(x);
				config.linkY = clampOffset(y + 52);
				AutoConfig.getConfigHolder(StsModConfig.class).save();
				SpareTheSympathy.LOGGER.info("Migrated armoury button positions into autoconfig");
			}
			Files.deleteIfExists(positionFile);
		} catch (Exception e) {
			SpareTheSympathy.LOGGER.warn("Could not migrate the legacy armoury button position", e);
		}
	}

	private static boolean atDefaultPositions(StsModConfig config) {
		return config.exportX == 0
			&& config.exportY == 0
			&& config.saveX == 0
			&& config.saveY == 26
			&& config.linkX == 0
			&& config.linkY == 52;
	}

	/** Pure helper: null/blank -> default, otherwise trailing slashes stripped. */
	public static String normalizeSiteUrl(String url) {
		if (url == null || url.isBlank()) {
			return DEFAULT_SITE_URL;
		}
		return url.replaceAll("/+$", "");
	}

	/** Whether the /buildstealer2000 alias /msgs the target player. */
	public static boolean buildStealerMessageEnabled() {
		return config().buildStealerMessageEnabled;
	}

	/** The configured /buildstealer2000 message (always non-empty). */
	public static String buildStealerMessage() {
		return normalizeBuildStealerMessage(config().buildStealerMessage);
	}

	/**
	 * Pure helper: collapses the taunt to one line, drops a leading slash
	 * (it is typed as a command argument) and falls back to the default when
	 * nothing usable is left; never longer than
	 * {@link #BUILD_STEALER_MESSAGE_MAX} characters.
	 */
	public static String normalizeBuildStealerMessage(String message) {
		if (message == null) {
			return DEFAULT_BUILD_STEALER_MESSAGE;
		}
		String clean = message.replaceAll("[\\r\\n]", " ").replaceAll("\\s+", " ").trim();
		while (clean.startsWith("/")) {
			clean = clean.substring(1).trim();
		}
		if (clean.isEmpty()) {
			return DEFAULT_BUILD_STEALER_MESSAGE;
		}
		return clean.length() > BUILD_STEALER_MESSAGE_MAX
			? clean.substring(0, BUILD_STEALER_MESSAGE_MAX)
			: clean;
	}

	/** Pure helper: keeps a button offset within the config slider range. */
	public static int clampOffset(int value) {
		return Math.max(-ARMOURY_BUTTON_OFFSET_LIMIT, Math.min(ARMOURY_BUTTON_OFFSET_LIMIT, value));
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
