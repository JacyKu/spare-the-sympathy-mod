package sts.mod.client.dump;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureSheetsWriterTest {
	private static final int CAPTURE = sts.mod.client.render.IconRenderer.CAPTURE_SIZE;

	@TempDir
	Path tempDir;

	/** A capture with a colored square centered in the capture area. */
	private static int[] captureFrame(int centerColor) {
		int[] pixels = new int[CAPTURE * CAPTURE];
		int half = 48;
		for (int y = CAPTURE / 2 - half; y < CAPTURE / 2 + half; y++) {
			for (int x = CAPTURE / 2 - half; x < CAPTURE / 2 + half; x++) {
				pixels[y * CAPTURE + x] = centerColor;
			}
		}
		return pixels;
	}

	@Test
	void contentIsFittedIntoUniformCells() throws Exception {
		int red = 0xFFFF0000;
		int green = 0xFF00FF00;
		int blue = 0xFF0000FF;

		DumpRunner.RenderedItem animated = new DumpRunner.RenderedItem(
			"Outsider's Gaze", List.of(captureFrame(red), captureFrame(green)), List.of(3, 3), "minecraft/optifine/cit/outsiders_gaze", true, false);
		DumpRunner.RenderedItem staticItem = new DumpRunner.RenderedItem(
			"Cinnamon Sapling", List.of(captureFrame(blue)), List.of(1), null, false, false);

		TextureSheetsWriter.write(tempDir, List.of(animated, staticItem));

		assertTrue(Files.exists(tempDir.resolve("itemsheet-anim.png")));
		assertTrue(Files.exists(tempDir.resolve("itemsheet.png")));
		assertTrue(Files.exists(tempDir.resolve("itemsheet-manifest.json")));

		String manifest = Files.readString(tempDir.resolve("itemsheet-manifest.json"));
		assertTrue(manifest.contains("\"key\": \"Outsider\\u0027s Gaze\""));
		assertTrue(manifest.contains("\"sheet\": \"anim\""));
		assertTrue(manifest.contains("\"frameCount\": 2"));
		assertTrue(manifest.contains("\"width\": 64"));

		// All cells are 64x64; the colored content sits centered in the cell
		// (the 96px square is scaled by an integer factor of 2 to 48px,
		// centered at 32,32).
		try (NativeImage sheet = NativeImage.read(Files.readAllBytes(tempDir.resolve("itemsheet-anim.png")))) {
			assertEquals(red, sheet.getPixelRGBA(32, 32), "frame 0 color at cell center");
			assertEquals(0, sheet.getPixelRGBA(0, 0) >>> 24, "corner stays transparent");
			assertEquals(green, sheet.getPixelRGBA(66 + 32, 32), "frame 1 color at pitch");
		}

		try (NativeImage main = NativeImage.read(Files.readAllBytes(tempDir.resolve("itemsheet.png")))) {
			assertEquals(blue, main.getPixelRGBA(32, 32), "static texture at cell center");
		}

		String map = Files.readString(tempDir.resolve("itemsheet-map.json"));
		assertTrue(map.contains("\"Cinnamon Sapling\""));
	}
}
