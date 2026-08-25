package sts.mod.sheet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the dump manifest and the name-to-token map. The manifest carries the
 * full per-entry detail (source size, frame count, dwells in 1/20s ticks, sheet
 * position, strip pitch/cols/rowPitch) so the import/verification side never
 * needs to guess; the map mirrors the existing site sheet format
 * ({@code {"key": "token"}}).
 */
public final class ManifestWriter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public record Manifest(String mainSheet, String animSheet, List<SheetEntry> entries) {
	}

	public record SheetEntry(
		String key,
		String token,
		String sheet,
		int x,
		int y,
		int width,
		int height,
		int srcWidth,
		int srcHeight,
		int frameCount,
		List<Integer> dwells,
		int pitch,
		int cols,
		int rowPitch,
		String texture
	) {
	}

	private ManifestWriter() {
	}

	public static void write(Path outputDir, List<SheetEntry> entries) throws java.io.IOException {
		Map<String, String> map = new LinkedHashMap<>();
		for (SheetEntry entry : entries) {
			map.put(entry.key(), entry.token());
		}
		Files.writeString(outputDir.resolve("itemsheet-map.json"), GSON.toJson(map), StandardCharsets.UTF_8);
		Manifest manifest = new Manifest("itemsheet.png", "itemsheet-anim.png", entries);
		Files.writeString(outputDir.resolve("itemsheet-manifest.json"), GSON.toJson(manifest), StandardCharsets.UTF_8);
	}
}
