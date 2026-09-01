package sts.mod.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Shortens charm item names into the compact form the build token embeds
 * (e.g. {@code Keter Core} power 1 Generalist becomes
 * {@code Ket-r_Core-1-G}). Port of
 * {@code apps/sts/app/_src/utils/builder/charmShortener.js} - the site's
 * decoder resolves the short form back to the full item.
 */
public final class CharmShortener {
	private CharmShortener() {
	}

	/**
	 * @param charmName the charm item's name (from the item dictionary)
	 * @param power     the charm's power level
	 * @param className the charm's class (e.g. {@code "Generalist"})
	 */
	public static String shortenCharm(String charmName, int power, String className) {
		// JS replace() only replaces the first occurrence - mirror that.
		String base = charmName.replaceFirst(" Charm", "");
		List<String> parts = extractRelevantLetters(base, 6);
		return parts.get(0).replaceAll(" ", "_") + "-" + parts.get(1).replaceAll(" ", "_") + "-" + power + "-"
			+ className.charAt(0);
	}

	private static List<String> extractRelevantLetters(String charmName, int n) {
		List<String> parts = new ArrayList<>();
		parts.add(charmName.substring(0, 3));
		if (charmName.length() - 3 < n) {
			parts.add(charmName.substring(3));
		} else {
			parts.add(charmName.substring(charmName.length() - n));
		}
		return parts;
	}
}
