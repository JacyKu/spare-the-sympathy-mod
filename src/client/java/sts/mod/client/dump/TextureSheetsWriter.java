package sts.mod.client.dump;

import com.mojang.blaze3d.platform.NativeImage;
import sts.mod.SpareTheSympathy;
import sts.mod.sheet.CssWriter;
import sts.mod.sheet.ManifestWriter;
import sts.mod.sheet.SheetPacker;
import sts.mod.sheet.Tokens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Partitions rendered item icons into the static main sheet and the animation
 * sheet, packs both, fits the rendered GUI captures into 64px cells, blits
 * them, and writes the PNGs plus the manifest, map, and CSS files.
 */
public final class TextureSheetsWriter {
	private static final String SHEET_MAIN = "main";
	private static final String SHEET_ANIM = "anim";
	private static final int CELL_SIZE = 64;

	private TextureSheetsWriter() {
	}

	public static void write(Path outputDir, List<DumpRunner.RenderedItem> items) throws IOException {
		List<TextureEntry> statics = new ArrayList<>();
		List<TextureEntry> animated = new ArrayList<>();
		for (DumpRunner.RenderedItem item : items) {
			if (item.animated() && !allFramesIdentical(item.frames())) {
				animated.add(fitToCell(item));
			} else {
				if (item.animated()) {
					SpareTheSympathy.LOGGER.info("[dump] collapsed {} to static ({} identical frames)", item.key(), item.frames().size());
					item = new DumpRunner.RenderedItem(item.key(), List.of(item.frames().get(0)), List.of(1), item.texturePath(), false, item.retry());
				}
				statics.add(fitToCell(item));
			}
		}
		Comparator<TextureEntry> byKey = Comparator.comparing(entry -> entry.key);
		statics.sort(byKey);
		animated.sort(byKey);

		List<ManifestWriter.SheetEntry> manifestEntries = new ArrayList<>();
		SheetPacker.PackResult main = SheetPacker.pack(statics.stream().map(TextureEntry::spec).toList());
		writeSheet(outputDir, "itemsheet.png", main, statics, manifestEntries, SHEET_MAIN);
		SheetPacker.PackResult anim = SheetPacker.pack(animated.stream().map(TextureEntry::spec).toList());
		writeSheet(outputDir, "itemsheet-anim.png", anim, animated, manifestEntries, SHEET_ANIM);

		ManifestWriter.write(outputDir, manifestEntries);
		Files.writeString(outputDir.resolve("_itemsheet.css"), CssWriter.generate(manifestEntries), StandardCharsets.UTF_8);
	}

	private record TextureEntry(String key, List<int[]> frames, List<Integer> dwells, String texturePath, int width, int height, int srcWidth, int srcHeight) {
		SheetPacker.EntrySpec spec() {
			return new SheetPacker.EntrySpec(width, height, frames.size());
		}
	}

	private static TextureEntry fitToCell(DumpRunner.RenderedItem item) {
		int captureSize = sts.mod.client.render.IconRenderer.CAPTURE_SIZE;
		// Uniform 64px cells: the captured content is measured, then scaled to
		// fit inside the cell with a small margin, centered.
		int minX = captureSize;
		int minY = captureSize;
		int maxX = 0;
		int maxY = 0;
		for (int[] frame : item.frames()) {
			for (int y = 0; y < captureSize; y++) {
				for (int x = 0; x < captureSize; x++) {
					if ((frame[y * captureSize + x] >>> 24) > 0) {
						if (x < minX) {
							minX = x;
						}
						if (x > maxX) {
							maxX = x;
						}
						if (y < minY) {
							minY = y;
						}
						if (y > maxY) {
							maxY = y;
						}
					}
				}
			}
		}
		List<int[]> frames = new ArrayList<>(item.frames().size());
		if (maxX < minX || maxY < minY) {
			// Fully transparent capture: keep the empty frame.
			for (int[] frame : item.frames()) {
				frames.add(new int[CELL_SIZE * CELL_SIZE]);
			}
			return new TextureEntry(item.key(), frames, item.dwells(), item.texturePath(), CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
		}

		int contentWidth = maxX - minX + 1;
		int contentHeight = maxY - minY + 1;
		// Integer scaling only: every rendered pixel stays a pixel. Content
		// larger than the cell is scaled down by an integer factor (a 128px
		// item lands at exactly 64px); smaller content is scaled up by an
		// integer factor so small icons keep crisp, chunky pixels.
		int maxContent = Math.max(contentWidth, contentHeight);
		int factor;
		if (maxContent > CELL_SIZE) {
			factor = (maxContent + CELL_SIZE - 1) / CELL_SIZE;
		} else {
			factor = Math.max(1, CELL_SIZE / maxContent);
		}
		int targetWidth = Math.max(1, (contentWidth + factor - 1) / factor);
		int targetHeight = Math.max(1, (contentHeight + factor - 1) / factor);
		int offsetX = (CELL_SIZE - targetWidth) / 2;
		int offsetY = (CELL_SIZE - targetHeight) / 2;

		for (int[] frame : item.frames()) {
			int[] fitted = new int[CELL_SIZE * CELL_SIZE];
			for (int y = 0; y < targetHeight; y++) {
				int srcY = minY + (y * contentHeight) / targetHeight;
				for (int x = 0; x < targetWidth; x++) {
					int srcX = minX + (x * contentWidth) / targetWidth;
					fitted[(offsetY + y) * CELL_SIZE + offsetX + x] = frame[srcY * captureSize + srcX];
				}
			}
			frames.add(fitted);
		}
		return new TextureEntry(item.key(), frames, item.dwells(), item.texturePath(), CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
	}

	private static boolean allFramesIdentical(List<int[]> frames) {
		if (frames.size() < 2) {
			return false;
		}
		int[] first = frames.get(0);
		for (int index = 1; index < frames.size(); index++) {
			if (!java.util.Arrays.equals(first, frames.get(index))) {
				return false;
			}
		}
		return true;
	}

	private static void writeSheet(Path outputDir, String fileName, SheetPacker.PackResult pack, List<TextureEntry> entries,
		List<ManifestWriter.SheetEntry> manifestEntries, String sheetName) throws IOException {
		if (entries.isEmpty()) {
			return;
		}
		try (NativeImage sheet = new NativeImage(pack.width(), pack.height(), true)) {
			for (int index = 0; index < entries.size(); index++) {
				TextureEntry entry = entries.get(index);
				SheetPacker.Packed packed = pack.entries().get(index);
				blit(sheet, entry, packed);
				manifestEntries.add(new ManifestWriter.SheetEntry(
					entry.key(),
					Tokens.tokenFor(entry.key()),
					sheetName,
					packed.x(),
					packed.y(),
					entry.width(),
					entry.height(),
					entry.srcWidth(),
					entry.srcHeight(),
					entry.frames().size(),
					entry.dwells(),
					packed.pitch(),
					packed.cols(),
					packed.rowPitch(),
					entry.texturePath()
				));
			}
			sheet.writeToFile(outputDir.resolve(fileName));
		}
	}

	/**
	 * Frames are placed exactly where {@link SheetPacker} promised. For a
	 * plain horizontal strip (cols == 1) every frame steps right by pitch; for
	 * a gridded strip frames wrap to the next row every {@code cols} frames.
	 */
	private static void blit(NativeImage sheet, TextureEntry entry, SheetPacker.Packed packed) {
		int framesPerRow = packed.cols() <= 1 ? entry.frames().size() : packed.cols();
		int pitch = packed.animated() ? packed.pitch() : 0;
		int rowPitch = packed.rowPitch();
		for (int frameIndex = 0; frameIndex < entry.frames().size(); frameIndex++) {
			int[] frame = entry.frames().get(frameIndex);
			int frameX = packed.x() + (frameIndex % framesPerRow) * pitch;
			int frameY = packed.y() + (frameIndex / framesPerRow) * rowPitch;
			for (int row = 0; row < entry.height(); row++) {
				int sourceRow = row * entry.width();
				for (int col = 0; col < entry.width(); col++) {
					sheet.setPixelRGBA(frameX + col, frameY + row, frame[sourceRow + col]);
				}
			}
		}
	}
}
