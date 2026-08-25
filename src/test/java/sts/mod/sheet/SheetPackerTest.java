package sts.mod.sheet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheetPackerTest {
	@Test
	void emptySheetIsOnePixel() {
		SheetPacker.PackResult result = SheetPacker.pack(List.of());
		assertEquals(1, result.width());
		assertEquals(1, result.height());
		assertTrue(result.entries().isEmpty());
	}

	@Test
	void staticsShelfPackSideBySide() {
		SheetPacker.PackResult result = SheetPacker.pack(List.of(
			new SheetPacker.EntrySpec(64, 64, 1),
			new SheetPacker.EntrySpec(64, 64, 1)));
		assertEquals(130, result.width());
		assertEquals(64, result.height());
		assertEquals(new SheetPacker.Packed(0, 0, 0, 0, 0), result.entries().get(0));
		assertEquals(new SheetPacker.Packed(66, 0, 0, 0, 0), result.entries().get(1));
	}

	@Test
	void staticsWrapRows() {
		SheetPacker.PackResult result = SheetPacker.pack(List.of(
			new SheetPacker.EntrySpec(12000, 64, 1),
			new SheetPacker.EntrySpec(12000, 64, 1),
			new SheetPacker.EntrySpec(64, 64, 1)));
		// The second 12000px cell would cross the sheet limit, so it wraps;
		// the small cell then extends the second row's width.
		assertEquals(12066, result.width());
		assertEquals(130, result.height());
		assertEquals(new SheetPacker.Packed(0, 66, 0, 0, 0), result.entries().get(1));
		assertEquals(new SheetPacker.Packed(12002, 66, 0, 0, 0), result.entries().get(2));
	}

	@Test
	void animatedStripsGetPitchAndShelfPackBesideStatics() {
		SheetPacker.PackResult result = SheetPacker.pack(List.of(
			new SheetPacker.EntrySpec(64, 64, 3),
			new SheetPacker.EntrySpec(64, 64, 1)));
		SheetPacker.Packed strip = result.entries().get(0);
		assertEquals(66, strip.pitch());
		assertEquals(1, strip.cols());
		assertEquals(0, strip.rowPitch());
		assertEquals(0, strip.x());
		assertEquals(0, strip.y());
		assertEquals(new SheetPacker.Packed(198, 0, 0, 0, 0), result.entries().get(1));
		assertEquals(262, result.width());
		assertEquals(64, result.height());
	}

	@Test
	void overWideStripsAreGridded() {
		// 2000 frames of 64x64 would be 2000*66-2 px wide; must grid instead.
		SheetPacker.PackResult result = SheetPacker.pack(List.of(
			new SheetPacker.EntrySpec(64, 64, 2000)));
		SheetPacker.Packed packed = result.entries().get(0);
		assertTrue(packed.cols() > 1);
		assertEquals(66, packed.rowPitch());
		assertTrue(result.width() <= SheetPacker.MAX_SHEET_WIDTH);
		assertTrue(result.height() > 64);
	}

	@Test
	void bigTexturesKeepNaturalSizes() {
		SheetPacker.PackResult result = SheetPacker.pack(List.of(
			new SheetPacker.EntrySpec(128, 128, 1),
			new SheetPacker.EntrySpec(250, 250, 2)));
		assertEquals(new SheetPacker.Packed(0, 0, 0, 0, 0), result.entries().get(0));
		SheetPacker.Packed strip = result.entries().get(1);
		assertEquals(130, strip.x());
		assertEquals(252, strip.pitch());
		assertEquals(0, strip.y());
	}
}
