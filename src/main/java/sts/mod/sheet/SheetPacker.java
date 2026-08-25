package sts.mod.sheet;

import java.util.ArrayList;
import java.util.List;

/**
 * Shelf packs textures into sheets. Static textures are single cells; animated
 * textures are strips of equal-sized frames. Strips wider than
 * {@link #MAX_SHEET_WIDTH} (the browser GPU texture limit) have their frames
 * gridded into rows instead.
 */
public final class SheetPacker {
	public static final int MAX_SHEET_WIDTH = 16384;
	private static final int GAP = 2;

	public record EntrySpec(int width, int height, int frameCount) {
	}

	public record Packed(int x, int y, int pitch, int cols, int rowPitch) {
		public boolean animated() {
			return cols > 0;
		}
	}

	public record PackResult(int width, int height, List<Packed> entries) {
	}

	private SheetPacker() {
	}

	public static PackResult pack(List<EntrySpec> entries) {
		if (entries.isEmpty()) {
			return new PackResult(1, 1, List.of());
		}
		List<Packed> packed = new ArrayList<>(entries.size());
		int rowX = 0;
		int rowY = 0;
		int rowHeight = 0;
		int sheetWidth = 1;
		for (EntrySpec entry : entries) {
			int pitch = entry.width() + GAP;
			int cols = 1;
			int rowPitch = 0;
			int stripWidth = entry.frameCount() == 1 ? entry.width() : (entry.frameCount() - 1) * pitch + entry.width();
			if (stripWidth > MAX_SHEET_WIDTH) {
				cols = Math.max(1, (MAX_SHEET_WIDTH - entry.width()) / pitch + 1);
				rowPitch = entry.height() + GAP;
				stripWidth = (cols - 1) * pitch + entry.width();
			}
			int gridRows = (entry.frameCount() + cols - 1) / cols;
			int stripHeight = entry.height() + (gridRows - 1) * rowPitch;
			if (rowX > 0 && rowX + stripWidth > MAX_SHEET_WIDTH) {
				rowY += rowHeight + GAP;
				rowX = 0;
				rowHeight = 0;
			}
			packed.add(new Packed(rowX, rowY, entry.frameCount() == 1 ? 0 : pitch, entry.frameCount() == 1 ? 0 : cols, entry.frameCount() == 1 ? 0 : rowPitch));
			rowX += stripWidth + GAP;
			rowHeight = Math.max(rowHeight, stripHeight);
			sheetWidth = Math.max(sheetWidth, rowX - GAP);
		}
		int sheetHeight = rowY + rowHeight;
		return new PackResult(sheetWidth, sheetHeight, packed);
	}
}
