package sts.mod.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import sts.mod.SpareTheSympathy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The mod's per-device secret. It is generated on first use and stored next to
 * the mod config; its hash is bound to the Minecraft link when the player
 * confirms it in the browser, and every upload must prove possession of it.
 * This keeps a linked account safe even though Minecraft UUIDs are public.
 */
public final class ModIdentity {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{32,128}";
	private static String token;

	private ModIdentity() {
	}

	/** The persisted device token, created on first use. */
	public static synchronized String deviceToken() {
		if (token != null) {
			return token;
		}
		Path file = FabricLoader.getInstance().getConfigDir()
			.resolve("sparethesympathy")
			.resolve("device.json");
		try {
			if (Files.exists(file)) {
				JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject();
				String stored = json.has("token") ? json.get("token").getAsString() : "";
				if (stored.matches(TOKEN_PATTERN)) {
					token = stored;
					return token;
				}
			}
			String generated = randomToken();
			Files.createDirectories(file.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("token", generated);
			Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
			token = generated;
			return token;
		} catch (Exception e) {
			// Still usable this session; the player will have to re-link after
			// a restart because the server has a different hash on file.
			SpareTheSympathy.LOGGER.warn("Could not store the device token; uploads will need re-linking after a restart", e);
			token = randomToken();
			return token;
		}
	}

	private static String randomToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
