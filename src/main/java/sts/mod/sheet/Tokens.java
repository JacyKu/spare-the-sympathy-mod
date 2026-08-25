package sts.mod.sheet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Tokens {
	private Tokens() {
	}

	/**
	 * Stable CSS token for a texture key (e.g. "minecraft/item/celestial_gem"):
	 * a sanitized base plus an 8-hex SHA-1 of the full key, mirroring the
	 * existing site sheet naming ({@code <base>_<hash>}).
	 */
	public static String tokenFor(String key) {
		String base = key.toLowerCase()
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("_+", "_")
			.replaceAll("^_+|_+$", "");
		return base + "_" + sha1Prefix(key);
	}

	private static String sha1Prefix(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(8);
			for (int index = 0; index < 4; index++) {
				builder.append(String.format("%02x", hash[index]));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
