package sts.mod.sheet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestWriterTest {
	@TempDir
	Path tempDir;

	@Test
	void writesManifestAndMap() throws Exception {
		ManifestWriter.SheetEntry entry = new ManifestWriter.SheetEntry(
			"minecraft/item/x", "x_1a2b3c4d", "anim", 0, 0, 64, 64, 16, 32, 2, List.of(3, 3), 66, 1, 0, "minecraft/optifine/cit/x");
		ManifestWriter.write(tempDir, List.of(entry));

		String manifest = Files.readString(tempDir.resolve("itemsheet-manifest.json"));
		assertTrue(manifest.contains("\"mainSheet\": \"itemsheet.png\""));
		assertTrue(manifest.contains("\"animSheet\": \"itemsheet-anim.png\""));
		assertTrue(manifest.contains("\"key\": \"minecraft/item/x\""));
		assertTrue(manifest.contains("\"token\": \"x_1a2b3c4d\""));
		assertTrue(manifest.contains("\"dwells\": [\n        3,\n        3\n      ]"));

		String map = Files.readString(tempDir.resolve("itemsheet-map.json"));
		assertTrue(map.contains("\"minecraft/item/x\": \"x_1a2b3c4d\""));
	}
}
