package sts.mod.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Encodes a build into the site's compact binary {@code v1_} token format
 * (version 6). This is a faithful Java port of the encoder half of
 * {@code apps/sts/app/_src/utils/builder/buildUrlCodec.js}: byte-for-byte the
 * same layout, the same FNV-1a over UTF-16 code units, the same base64url.
 * <p>
 * Layout: {@code [version=6][6x FNV-1a32 item key, little endian]}
 * {@code [varint charmLen][charm][varint nameLen][name][varint classLen][class]}
 * {@code [varint skillCount][(varint idLen, id, points)*]}
 * {@code [6 stat bytes (tenacity, vitality, vigor, focus, perspicacity, region)]}
 * {@code [varint health][varint specLen][spec][varint specSkillCount][(...)*]}
 * {@code [varint enCount][varint czCount]}.
 */
public final class BuildTokenEncoder {
	private static final String PREFIX = "v1_";
	private static final int VERSION = 6;
	private static final String NONE = "None";

	private BuildTokenEncoder() {
	}

	/** One class/spec ability with the points spent in it. */
	public record Skill(String id, int points) {
	}

	/**
	 * @param itemKeys      the 6 equipment keys (mainhand, offhand, helmet,
	 *                      chestplate, leggings, boots); {@code "None"} or null
	 *                      for empty slots
	 * @param charm         comma-joined shortened charm keys, or null/empty
	 * @param name          build display name, or null
	 * @param gameClass     class name (e.g. {@code "Cleric"}), or null
	 * @param spec          spec name (e.g. {@code "Paladin"}), or null
	 * @param skills        class ability points (scoreboard ids)
	 * @param specSkills    spec ability points (scoreboard ids)
	 * @param enhancements  enhanced ability scoreboard ids, or null/empty
	 * @param statValues    7 stats in the site's order: health, tenacity,
	 *                      vitality, vigor, focus, perspicacity, region
	 *                      (defaults are 100, 0, 0, 0, 0, 0, 3)
	 */
	public static String encode(
		List<String> itemKeys,
		String charm,
		String name,
		String gameClass,
		String spec,
		List<Skill> skills,
		List<Skill> specSkills,
		List<String> enhancements,
		int[] statValues
	) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(VERSION);

		// 6 item key hashes (0 for empty slots).
		for (int i = 0; i < 6; i++) {
			String key = itemKeys != null && i < itemKeys.size() ? itemKeys.get(i) : null;
			int hash = (key == null || key.equals(NONE) || key.isEmpty()) ? 0 : fnv1a32(key);
			out.write(hash & 0xFF);
			out.write((hash >>> 8) & 0xFF);
			out.write((hash >>> 16) & 0xFF);
			out.write((hash >>> 24) & 0xFF);
		}

		writeText(out, charm != null && !charm.isEmpty() && !charm.equals(NONE) ? charm : null);
		writeText(out, name);
		writeText(out, gameClass);

		writeSkills(out, skills);

		// Extra stats. Six single bytes (tenacity..region) then health varint.
		int[] stats = statValues == null ? new int[] { 100, 0, 0, 0, 0, 0, 3 } : statValues;
		int[] defaults = new int[] { 100, 0, 0, 0, 0, 0, 3 };
		for (int i = 1; i < 7; i++) {
			int value = stats.length > i ? stats[i] : defaults[i];
			out.write(clampByte(value, defaults[i]));
		}
		int healthRaw = stats.length > 0 ? stats[0] : 100;
		int health = Double.isNaN(healthRaw) ? 100 : Math.max(1, healthRaw);
		writeVarint(out, health);

		writeText(out, spec);
		writeSkills(out, specSkills);

		// Enhanced abilities (v3 field), then no Celestial Zenith abilities.
		writeTextList(out, enhancements);
		writeVarint(out, 0);

		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
	}

	private static void writeTextList(ByteArrayOutputStream out, List<String> entries) {
		if (entries == null || entries.isEmpty()) {
			writeVarint(out, 0);
			return;
		}
		writeVarint(out, entries.size());
		for (String entry : entries) {
			byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
			writeVarint(out, bytes.length);
			out.writeBytes(bytes);
		}
	}

	private static int clampByte(int value, int fallback) {
		if (Double.isNaN(value)) value = fallback;
		return Math.max(0, Math.min(255, value));
	}

	private static void writeSkills(ByteArrayOutputStream out, List<Skill> skills) {
		if (skills == null || skills.isEmpty()) {
			writeVarint(out, 0);
			return;
		}
		writeVarint(out, skills.size());
		for (Skill skill : skills) {
			byte[] idBytes = skill.id().getBytes(StandardCharsets.UTF_8);
			writeVarint(out, idBytes.length);
			out.writeBytes(idBytes);
			out.write(Math.max(0, Math.min(255, skill.points())));
		}
	}

	private static void writeText(ByteArrayOutputStream out, String text) {
		if (text == null || text.isEmpty()) {
			writeVarint(out, 0);
			return;
		}
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		writeVarint(out, bytes.length);
		out.writeBytes(bytes);
	}

	// Standard LEB128-style varint, same as the site's writeVarint.
	private static void writeVarint(ByteArrayOutputStream out, int num) {
		int n = num;
		while ((n & 0xFFFFFF80) != 0) {
			out.write((n & 0x7F) | 0x80);
			n >>>= 7;
		}
		out.write(n);
	}

	// FNV-1a 32-bit over the UTF-16 code units (charCodeAt), matching the
	// site's hash so token bytes are identical. Overflow wraps like JS imul.
	static int fnv1a32(String value) {
		int hash = 0x811c9dc5;
		for (int i = 0; i < value.length(); i++) {
			hash ^= value.charAt(i);
			hash *= 0x01000193;
		}
		return hash;
	}
}
