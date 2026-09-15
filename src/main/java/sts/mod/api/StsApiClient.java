package sts.mod.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal HTTP client for the STS site API. Linking works with just the
 * Minecraft UUID plus a pending-link code confirmed in the browser; from then
 * on every upload also presents this device's {@link ModIdentity} token, so a
 * public UUID alone can never write to a linked account.
 */
public final class StsApiClient {
	private static final String USER_AGENT = "sparethesympathy/1.0.0";
	private static final int CONNECT_TIMEOUT_MS = 10_000;
	// Generous: `next dev` compiles routes on demand, so the first request to
	// a fresh dev server can take a while.
	private static final int READ_TIMEOUT_MS = 30_000;

	private StsApiClient() {
	}

	public static String siteUrl() {
		return StsConfig.siteUrl();
	}

	public static String get(String path) throws IOException {
		return request("GET", path, null);
	}

	public static String post(String path, JsonElement body) throws IOException {
		return request("POST", path, body == null ? null : body.toString());
	}

	private static String request(String method, String path, String jsonBody) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(siteUrl() + path).openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setRequestMethod(method);
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setRequestProperty("Accept", "application/json");
		if (jsonBody != null) {
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setDoOutput(true);
			try (OutputStream out = connection.getOutputStream()) {
				out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
			}
		}
		int status = connection.getResponseCode();
		InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
		String response = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		connection.disconnect();
		if (status < 200 || status >= 300) {
			throw new IOException("HTTP " + status + " from " + method + " " + path + (response.isEmpty() ? "" : ": " + response));
		}
		return response;
	}

	/** Result of asking for a pending link. */
	public record LinkRequest(String url, String code) {
	}

	/** Requests a pending-link code for the given Minecraft UUID. */
	public static LinkRequest requestLink(String uuid) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		body.addProperty("deviceToken", ModIdentity.deviceToken());
		JsonObject json = JsonParser.parseString(post("/api/v2/mod/link", body)).getAsJsonObject();
		return new LinkRequest(json.get("url").getAsString(), json.get("code").getAsString());
	}

	/** Whether the UUID is currently linked to a Discord account. */
	public static boolean isLinked(String uuid) throws IOException {
		JsonObject json = JsonParser.parseString(get("/api/v2/mod/link/status?uuid=" + uuid)).getAsJsonObject();
		return json.has("linked") && json.get("linked").getAsBoolean();
	}

	/** Result of saving a build from the mod. */
	public record SaveResult(String url, boolean linked, boolean saved, List<String> createdItems) {
	}

	/**
	 * Saves a build; unlinked UUIDs are stored anonymously. The infusion map
	 * carries the armoury's "Preferred Delve Infusion" preferences (site's
	 * delve infusion state, shown in the builder's infusion dropdowns).
	 */
	public static SaveResult saveBuild(String uuid, String token, String name,
		java.util.Map<String, String> infusions) throws IOException {
		return saveBuild(uuid, token, name, infusions, null);
	}

	/**
	 * Saves a build and, for linked accounts, uploads equipment the site
	 * doesn't know about (`items`) so it can be created as custom items.
	 */
	public static SaveResult saveBuild(String uuid, String token, String name,
		java.util.Map<String, String> infusions, JsonArray items) throws IOException {
		return saveBuild(uuid, token, name, infusions, items, null);
	}

	/**
	 * Saves a build; {@code basicInfusions} is the per-slot normal infusion
	 * map ({@code slot: {name, level}}) stored with the build state.
	 */
	public static SaveResult saveBuild(String uuid, String token, String name,
		java.util.Map<String, String> infusions, JsonArray items, JsonObject basicInfusions) throws IOException {
		JsonObject body = new JsonObject();
		if (uuid != null && !uuid.isEmpty()) {
			body.addProperty("uuid", uuid);
			body.addProperty("deviceToken", ModIdentity.deviceToken());
		}
		body.addProperty("token", token);
		if (name != null && !name.isEmpty()) {
			body.addProperty("name", name);
		}
		if (infusions != null && !infusions.isEmpty()) {
			JsonObject object = new JsonObject();
			infusions.forEach(object::addProperty);
			body.add("infusions", object);
		}
		if (basicInfusions != null && basicInfusions.size() > 0) {
			body.add("basicInfusions", basicInfusions);
		}
		if (items != null && items.size() > 0) {
			body.add("items", items);
		}
		JsonObject json = JsonParser.parseString(post("/api/v2/mod/builds", body)).getAsJsonObject();
		return new SaveResult(json.get("url").getAsString(), json.get("linked").getAsBoolean(),
			json.get("saved").getAsBoolean(), readStringArray(json, "createdItems"));
	}

	// ---------- item uploads ----------

	/** Result of uploading items from the game. */
	public record UploadResult(List<String> created, List<String> skipped) {
	}

	/**
	 * Uploads in-game items as custom items on the linked account. Throws with
	 * the server's message when the UUID isn't linked ("not linked").
	 */
	public static UploadResult uploadItems(String uuid, JsonArray items) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		body.addProperty("deviceToken", ModIdentity.deviceToken());
		body.add("items", items);
		JsonObject json = JsonParser.parseString(post("/api/v2/mod/custom-items", body)).getAsJsonObject();
		List<String> skipped = new ArrayList<>();
		if (json.has("skipped") && json.get("skipped").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("skipped")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
					skipped.add(element.getAsJsonObject().get("name").getAsString());
				}
			}
		}
		return new UploadResult(readStringArray(json, "created"), skipped);
	}

	private static List<String> readStringArray(JsonObject json, String key) {
		List<String> values = new ArrayList<>();
		if (json.has(key) && json.get(key).isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray(key)) {
				if (element.isJsonPrimitive()) {
					values.add(element.getAsString());
				}
			}
		}
		return values;
	}

	// ---------- skills data ----------

	/** One ability as advertised by the site's /api/v2/skills. */
	public record Ability(String scoreboardId, String displayName) {
	}

	/** One spec (specialization) with its spec-only abilities. */
	public record Spec(String specName, List<Ability> specSkills) {
	}

	/** One class with its base abilities and specs. */
	public record GameClass(String className, List<Ability> skills, List<Spec> specs) {
	}

	/** Fetches the class/skill catalog the builder uses. */
	public static List<GameClass> fetchSkills() throws IOException {
		JsonObject json = JsonParser.parseString(get("/api/v2/skills")).getAsJsonObject();
		JsonArray classes = json.getAsJsonArray("classes");
		List<GameClass> result = new ArrayList<>();
		for (JsonElement element : classes) {
			JsonObject clazz = element.getAsJsonObject();
			String className = clazz.get("className").getAsString();
			List<Ability> skills = readAbilities(clazz.getAsJsonArray("skills"));
			List<Spec> specs = new ArrayList<>();
			for (JsonElement specElement : clazz.getAsJsonArray("specs")) {
				JsonObject spec = specElement.getAsJsonObject();
				JsonArray specSkills = spec.has("specSkills") ? spec.getAsJsonArray("specSkills") : new JsonArray();
				specs.add(new Spec(spec.get("specName").getAsString(), readAbilities(specSkills)));
			}
			result.add(new GameClass(className, skills, specs));
		}
		return result;
	}

	private static List<Ability> readAbilities(JsonArray array) {
		List<Ability> abilities = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject ability = element.getAsJsonObject();
			String scoreboardId = ability.has("scoreboardId") ? ability.get("scoreboardId").getAsString() : "";
			if (scoreboardId.isEmpty()) {
				continue;
			}
			String displayName = ability.has("displayName") && !ability.get("displayName").isJsonNull()
				? ability.get("displayName").getAsString()
				: (ability.has("name") && !ability.get("name").isJsonNull() ? ability.get("name").getAsString() : scoreboardId);
			abilities.add(new Ability(scoreboardId, displayName));
		}
		return abilities;
	}
}
