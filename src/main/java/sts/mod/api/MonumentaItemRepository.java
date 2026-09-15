package sts.mod.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import sts.mod.SpareTheSympathy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MonumentaItemRepository {
	private static final URI ITEMS_ENDPOINT = URI.create("https://api.playmonumenta.com/itemswithnbt");
	private static final Path CACHE_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("sparethesympathy")
		.resolve("monumenta-items-cache.json");
	// Transient API failures are retried before falling back to the disk
	// cache; a failed fetch is never cached in place of a retry.
	private static final int FETCH_ATTEMPTS = 3;
	private static final long FETCH_RETRY_DELAY_MS = 2_000;

	private MonumentaItemRepository() {
	}

	/**
	 * The Monumenta item dictionary: fresh from the API when reachable,
	 * otherwise the last cached response. Returns {@code null} when both the
	 * API (after retries) and the cache fail, so callers can retry later
	 * instead of treating an empty dictionary as loaded.
	 */
	public static List<MonumentaItemDefinition> getItems() {
		for (int attempt = 1; attempt <= FETCH_ATTEMPTS; attempt++) {
			try {
				String response = fetchRemoteJson();
				writeCache(response);
				List<MonumentaItemDefinition> items = parseItems(response);
				SpareTheSympathy.LOGGER.info("Loaded {} Monumenta items from API", items.size());
				return items;
			} catch (IOException | RuntimeException fetchFailure) {
				SpareTheSympathy.LOGGER.warn(
					"Monumenta items API attempt {}/{} failed: {}",
					attempt,
					FETCH_ATTEMPTS,
					fetchFailure.toString()
				);
				if (attempt < FETCH_ATTEMPTS) {
					try {
						Thread.sleep(FETCH_RETRY_DELAY_MS);
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		try {
			if (Files.exists(CACHE_PATH)) {
				List<MonumentaItemDefinition> items = parseItems(Files.readString(CACHE_PATH, StandardCharsets.UTF_8));
				SpareTheSympathy.LOGGER.info("Loaded {} Monumenta items from cache", items.size());
				return items;
			}
		} catch (IOException | RuntimeException cacheFailure) {
			SpareTheSympathy.LOGGER.error("Failed to read Monumenta cache", cacheFailure);
		}

		return null;
	}

	private static String fetchRemoteJson() throws IOException {
		HttpURLConnection connection = (HttpURLConnection) ITEMS_ENDPOINT.toURL().openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(15_000);
		connection.setReadTimeout(30_000);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("User-Agent", "sparethesympathy/1.0.0");

		int responseCode = connection.getResponseCode();
		if (responseCode < 200 || responseCode >= 300) {
			throw new IOException("Unexpected Monumenta API response: " + responseCode);
		}

		try (InputStream stream = connection.getInputStream(); Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			StringBuilder builder = new StringBuilder();
			char[] buffer = new char[8_192];
			int read;
			while ((read = reader.read(buffer)) != -1) {
				builder.append(buffer, 0, read);
			}
			return builder.toString();
		} finally {
			connection.disconnect();
		}
	}

	private static void writeCache(String json) {
		try {
			Files.createDirectories(CACHE_PATH.getParent());
			Files.writeString(CACHE_PATH, json, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			SpareTheSympathy.LOGGER.warn("Failed to write Monumenta cache", exception);
		}
	}

	private static List<MonumentaItemDefinition> parseItems(String json) {
		JsonElement rootElement = JsonParser.parseString(json);
		if (!rootElement.isJsonObject()) {
			throw new IllegalStateException("Monumenta items response is not a JSON object");
		}

		JsonObject root = rootElement.getAsJsonObject();
		Map<String, MonumentaItemDefinition> byKey = new LinkedHashMap<>(root.size());
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonObject()) {
				continue;
			}
			try {
				byKey.put(entry.getKey(), MonumentaItemDefinition.fromJson(entry.getKey(), value.getAsJsonObject()));
			} catch (Exception exception) {
				SpareTheSympathy.LOGGER.warn("Skipping Monumenta item '{}' due to parse error", entry.getKey());
			}
		}
		byKey = MonumentaItemDefinition.applyExaltedRenames(byKey);
		List<MonumentaItemDefinition> items = new ArrayList<>(byKey.values());
		items.sort(Comparator.comparing(MonumentaItemDefinition::key));
		return items;
	}
}
