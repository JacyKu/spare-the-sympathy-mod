package sts.mod.api;

/** Build-name handling shared by the GUI naming prompt and the upload flows. */
public final class BuildNames {
	/** The site's build-name cap. */
	public static final int MAX_NAME = 50;

	private BuildNames() {
	}

	/**
	 * The name to use for a prompted build: the trimmed input, falling back
	 * to the (trimmed) default when the player cleared the field, capped at
	 * the site's limit.
	 */
	public static String resolve(String input, String fallback) {
		String name = input == null ? "" : input.trim();
		if (name.isEmpty()) {
			name = fallback == null ? "" : fallback.trim();
		}
		return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
	}
}
