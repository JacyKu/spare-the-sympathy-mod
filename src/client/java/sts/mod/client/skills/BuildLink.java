package sts.mod.client.skills;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sts.mod.api.BuildSkills;
import sts.mod.api.StsApiClient;

/**
 * Resolves the skill portion of a build link for the in-game skill picker.
 * Accepts a v1_ builder token (decoded locally, including the current v8
 * layout), a /b/<id> short link (fetched from the public build API), or a
 * legacy query-style builder token.
 */
public final class BuildLink {
	private static final Pattern TOKEN = Pattern.compile("v1_[A-Za-z0-9_-]+");
	private static final Pattern SHORT_LINK = Pattern.compile("/b(?:/v\\d+)?/([A-Za-z0-9_-]+)");
	private static final Pattern RAW_ID = Pattern.compile("^[A-Za-z0-9_-]{4,64}$");

	private BuildLink() {
	}

	public static BuildSkills load(String input) throws IOException {
		String text = input == null ? "" : input.trim();
		if (text.isEmpty()) throw new IOException("Paste a build link first.");

		Matcher tokenMatcher = TOKEN.matcher(text);
		if (tokenMatcher.find()) {
			return decodeToken(tokenMatcher.group());
		}

		Matcher shortMatcher = SHORT_LINK.matcher(text);
		if (shortMatcher.find()) {
			return StsApiClient.fetchBuildSkills(shortMatcher.group(1));
		}

		if (text.contains("cl=")) {
			return decodeLegacy(text);
		}

		if (RAW_ID.matcher(text).matches()) {
			return StsApiClient.fetchBuildSkills(text);
		}

		throw new IOException("Not a build link or token.");
	}

	/** Decodes the compact binary token produced by the site and the mod. */
	public static BuildSkills decodeToken(String token) throws IOException {
		if (token == null || !token.startsWith("v1_")) throw new IOException("Not a v1 build token.");
		byte[] bytes;
		try {
			bytes = Base64.getUrlDecoder().decode(token.substring(3));
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid build token.", e);
		}
		if (bytes.length < 25) throw new IOException("Build token is too short.");
		int version = bytes[0] & 0xFF;
		if (version < 2 || version > 8) throw new IOException("Unsupported build token version.");

		Cursor cursor = new Cursor(bytes, 25); // version + six item hashes
		cursor.readText(); // charm
		cursor.readText(); // build name
		String className = cursor.readText();
		Map<String, Integer> skills = cursor.readSkills();

		if (version >= 6) {
			cursor.skip(6); // tenacity..region single bytes
			cursor.readVarint(); // health
		} else {
			cursor.skip(7); // health..region single bytes
		}

		String specName = null;
		Map<String, Integer> specSkills = new LinkedHashMap<>();
		Set<String> enhancements = new LinkedHashSet<>();
		if (cursor.hasRemaining()) {
			specName = cursor.readText();
			specSkills = cursor.readSkills();
			enhancements = cursor.readTextSet();
		}

		return new BuildSkills(
			className == null || className.isEmpty() ? null : className.toLowerCase(Locale.ROOT),
			specName == null || specName.isEmpty() ? null : specName,
			skills,
			specSkills,
			enhancements
		);
	}

	/**
	 * Best-effort support for the human-readable legacy query token
	 * ({@code cl=Cleric&sk=...}). Compressed legacy tokens are not needed for
	 * the skill picker, which points users at current links.
	 */
	private static BuildSkills decodeLegacy(String input) {
		String query = input;
		int question = query.indexOf('?');
		if (question >= 0) query = query.substring(question + 1);
		int hash = query.indexOf('#');
		if (hash >= 0) query = query.substring(0, hash);
		String decoded = URLDecoder.decode(query, StandardCharsets.UTF_8);
		Map<String, String> params = new LinkedHashMap<>();
		for (String part : decoded.split("&")) {
			int equals = part.indexOf('=');
			if (equals > 0) params.put(part.substring(0, equals), part.substring(equals + 1));
		}
		return new BuildSkills(
			lower(params.get("cl")),
			emptyToNull(params.get("sp")),
			parsePoints(params.get("sk")),
			parsePoints(params.get("ssk")),
			parseSet(params.get("en"))
		);
	}

	private static String lower(String value) {
		return value == null || value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
	}

	private static String emptyToNull(String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	private static Map<String, Integer> parsePoints(String raw) {
		Map<String, Integer> result = new LinkedHashMap<>();
		if (raw == null) return result;
		for (String entry : raw.split(",")) {
			int colon = entry.indexOf(':');
			if (colon <= 0) continue;
			try {
				int points = Integer.parseInt(entry.substring(colon + 1));
				if (points > 0) result.put(entry.substring(0, colon), points);
			} catch (NumberFormatException ignored) {
				// skip malformed entry
			}
		}
		return result;
	}

	private static Set<String> parseSet(String raw) {
		Set<String> result = new LinkedHashSet<>();
		if (raw == null) return result;
		for (String entry : raw.split(",")) {
			if (!entry.isEmpty()) result.add(entry);
		}
		return result;
	}

	private static final class Cursor {
		private final byte[] bytes;
		private int offset;

		private Cursor(byte[] bytes, int offset) throws IOException {
			if (offset < 0 || offset > bytes.length) throw new IOException("Invalid build token.");
			this.bytes = bytes;
			this.offset = offset;
		}

		private int readVarint() throws IOException {
			int value = 0;
			int shift = 0;
			while (offset < bytes.length && shift <= 28) {
				int b = bytes[offset++] & 0xFF;
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) return value;
				shift += 7;
			}
			throw new IOException("Invalid build token.");
		}

		private void skip(int count) throws IOException {
			if (count < 0 || offset + count > bytes.length) throw new IOException("Invalid build token.");
			offset += count;
		}

		private boolean hasRemaining() {
			return offset < bytes.length;
		}

		private String readText() throws IOException {
			int length = readVarint();
			if (length < 0 || length > 4096 || offset + length > bytes.length) {
				throw new IOException("Invalid build token.");
			}
			String value = new String(bytes, offset, length, StandardCharsets.UTF_8);
			offset += length;
			return value;
		}

		private Map<String, Integer> readSkills() throws IOException {
			Map<String, Integer> result = new LinkedHashMap<>();
			int count = readVarint();
			for (int i = 0; i < count; i++) {
				String id = readText();
				if (offset >= bytes.length) throw new IOException("Invalid build token.");
				int points = bytes[offset++] & 0xFF;
				if (!id.isEmpty() && points > 0) result.put(id, points);
			}
			return result;
		}

		private Set<String> readTextSet() throws IOException {
			Set<String> result = new LinkedHashSet<>();
			if (offset >= bytes.length) return result;
			int count = readVarint();
			for (int i = 0; i < count; i++) {
				String value = readText();
				if (!value.isEmpty()) result.add(value);
			}
			return result;
		}
	}
}
