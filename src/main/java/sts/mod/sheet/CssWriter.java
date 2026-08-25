package sts.mod.sheet;

import java.util.List;

/**
 * Generates {@code _itemsheet.css}: the base sprite class, one class per
 * texture token with its sheet position, and CSS keyframes for animated
 * textures with dwells taken from the pack mcmeta (1 tick = 50 ms).
 *
 * Frame stepping matches what the browser applies: a timing function on a
 * keyframe governs the segment from it to the NEXT keyframe
 * (css-animations-1 §4.3), so every stop but the final 100% one carries
 * {@code steps(1, end)}.
 */
public final class CssWriter {
	public static final int TICK_MS = 50;
	private static final String BASE_CLASS = "monumenta-items";

	private CssWriter() {
	}

	public static String generate(List<ManifestWriter.SheetEntry> entries) {
		StringBuilder css = new StringBuilder();
		css.append('.').append(BASE_CLASS).append(" {\n")
			.append("\tbackground-image: url(\"./itemsheet.png\");\n")
			.append("\tbackground-repeat: no-repeat;\n")
			.append("\tdisplay: inline-block;\n")
			.append("\tvertical-align: middle;\n")
			.append("\twidth: 64px;\n")
			.append("\theight: 64px;\n")
			.append("}\n\n");

		for (ManifestWriter.SheetEntry entry : entries) {
			appendEntry(css, entry);
			if (entry.frameCount() > 1) {
				appendKeyframes(css, entry);
			}
		}
		return css.toString();
	}

	private static void appendEntry(StringBuilder css, ManifestWriter.SheetEntry entry) {
		String className = classFor(entry.token());
		css.append('.').append(className).append(" {\n")
			.append("\tbackground-position: ").append(position(entry.x(), entry.y())).append(";\n");
		if (entry.frameCount() > 1) {
			css.append("\tbackground-image: url(\"./itemsheet-anim.png\");\n");
		}
		if (entry.width() != 64 || entry.height() != 64) {
			css.append("\twidth: ").append(entry.width()).append("px;\n")
				.append("\theight: ").append(entry.height()).append("px;\n");
		}
		if (entry.frameCount() > 1) {
			int totalMs = entry.dwells().stream().mapToInt(Integer::intValue).sum() * TICK_MS;
			css.append("\tanimation: ").append(keyframesName(entry.token())).append(' ').append(totalMs).append("ms ");
			if (entry.cols() <= 1 && isUniform(entry)) {
				css.append("steps(").append(entry.frameCount()).append(", end) ");
			}
			css.append("infinite;\n");
		}
		css.append("}\n\n");
	}

	private static void appendKeyframes(StringBuilder css, ManifestWriter.SheetEntry entry) {
		String name = keyframesName(entry.token());
		int x = entry.x();
		int y = entry.y();
		int pitch = entry.pitch();
		int rowPitch = entry.rowPitch();
		int cols = entry.cols();
		List<Integer> dwells = entry.dwells();
		int totalTicks = dwells.stream().mapToInt(Integer::intValue).sum();

		css.append("@keyframes ").append(name).append(" {\n");
		if (cols > 1) {
			appendGridKeyframes(css, x, y, pitch, rowPitch, cols, dwells, totalTicks);
		} else if (isUniform(entry)) {
			css.append("\tfrom { background-position: ").append(position(x, y)).append(" }\n")
				.append("\tto { background-position: ").append(position(x + (dwells.size() - 1) * pitch, y)).append(" }\n");
		} else {
			appendVariableKeyframes(css, x, y, pitch, dwells, totalTicks);
		}
		css.append("}\n\n");

		css.append("@media (prefers-reduced-motion: reduce) {\n")
			.append("\t.").append(classFor(entry.token())).append(" { animation: none; }\n")
			.append("}\n\n");
	}

	private static void appendGridKeyframes(StringBuilder css, int x, int y, int pitch, int rowPitch, int cols, List<Integer> dwells, int totalTicks) {
		css.append('\t').append("0%").append(" { background-position: ")
			.append(position(x, y)).append("; animation-timing-function: steps(1, end); }\n");
		int cumulative = 0;
		String previous = null;
		for (int index = 0; index < dwells.size() - 1; index++) {
			cumulative += dwells.get(index);
			String stop = percentage(cumulative, totalTicks);
			if (stop.equals(previous)) {
				continue;
			}
			previous = stop;
			css.append('\t').append(stop).append("% { background-position: ")
				.append(position(frameX(x, pitch, cols, index + 1), frameY(y, rowPitch, cols, index + 1)))
				.append("; animation-timing-function: steps(1, end); }\n");
		}
		int last = dwells.size() - 1;
		css.append('\t').append("100%").append(" { background-position: ")
			.append(position(frameX(x, pitch, cols, last), frameY(y, rowPitch, cols, last))).append(" }\n");
	}

	private static void appendVariableKeyframes(StringBuilder css, int x, int y, int pitch, List<Integer> dwells, int totalTicks) {
		css.append('\t').append("0%").append(" { background-position: ")
			.append(position(x, y)).append("; animation-timing-function: steps(1, end); }\n");
		int cumulative = 0;
		String previous = null;
		for (int index = 0; index < dwells.size() - 1; index++) {
			cumulative += dwells.get(index);
			String stop = percentage(cumulative, totalTicks);
			if (stop.equals(previous)) {
				continue;
			}
			previous = stop;
			css.append('\t').append(stop).append("% { background-position: ")
				.append(position(x + (index + 1) * pitch, y))
				.append("; animation-timing-function: steps(1, end); }\n");
		}
		css.append('\t').append("100%").append(" { background-position: ")
			.append(position(x + (dwells.size() - 1) * pitch, y)).append(" }\n");
	}

	private static int frameX(int x, int pitch, int cols, int index) {
		return x + (index % cols) * pitch;
	}

	private static int frameY(int y, int rowPitch, int cols, int index) {
		return y + (index / cols) * rowPitch;
	}

	private static boolean isUniform(ManifestWriter.SheetEntry entry) {
		List<Integer> dwells = entry.dwells();
		if (dwells.isEmpty()) {
			return true;
		}
		int first = dwells.get(0);
		for (int dwell : dwells) {
			if (dwell != first) {
				return false;
			}
		}
		return true;
	}

	private static String position(int x, int y) {
		return "-" + x + "px -" + y + "px";
	}

	private static String percentage(int cumulativeTicks, int totalTicks) {
		double value = Math.round(cumulativeTicks * 10000.0 / totalTicks) / 100.0;
		if (value == Math.floor(value)) {
			return String.valueOf((long) value);
		}
		return String.valueOf(value);
	}

	private static String classFor(String token) {
		return "monumenta-" + token;
	}

	private static String keyframesName(String token) {
		return "sts-anim-" + token;
	}
}
