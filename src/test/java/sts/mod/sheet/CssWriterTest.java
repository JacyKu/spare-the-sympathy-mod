package sts.mod.sheet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssWriterTest {
	private static ManifestWriter.SheetEntry entry(String token, int frameCount, List<Integer> dwells, int cols, int rowPitch) {
		return new ManifestWriter.SheetEntry("minecraft/item/x", token, "anim", 0, 0, 64, 64, 16, 16, frameCount, dwells, 66, cols, rowPitch, null);
	}

	@Test
	void uniformAnimationUsesSteps() {
		String css = CssWriter.generate(List.of(entry("x_1a2b3c4d", 3, List.of(1, 1, 1), 1, 0)));
		assertTrue(css.contains(".monumenta-x_1a2b3c4d {"));
		assertTrue(css.contains("background-image: url(\"./itemsheet-anim.png\");"));
		assertTrue(css.contains("animation: sts-anim-x_1a2b3c4d 150ms steps(3, end) infinite;"));
		assertTrue(css.contains("@keyframes sts-anim-x_1a2b3c4d {"));
		assertTrue(css.contains("from { background-position: -0px -0px }"));
		assertTrue(css.contains("to { background-position: -132px -0px }"));
	}

	@Test
	void variableDwellsUseMultiStopKeyframes() {
		String css = CssWriter.generate(List.of(entry("x_1a2b3c4d", 3, List.of(1, 4, 1), 1, 0)));
		assertTrue(css.contains("animation: sts-anim-x_1a2b3c4d 300ms infinite;"));
		assertTrue(css.contains("0% { background-position: -0px -0px; animation-timing-function: steps(1, end); }"));
		assertTrue(css.contains("16.67% { background-position: -66px -0px; animation-timing-function: steps(1, end); }"));
		assertTrue(css.contains("100% { background-position: -132px -0px }"));
	}

	@Test
	void gridStripsUseTwoDimensionalStops() {
		String css = CssWriter.generate(List.of(entry("x_1a2b3c4d", 4, List.of(1, 1, 1, 1), 2, 66)));
		assertTrue(css.contains("25% { background-position: -66px -0px; animation-timing-function: steps(1, end); }"));
		assertTrue(css.contains("50% { background-position: -0px -66px; animation-timing-function: steps(1, end); }"));
		assertTrue(css.contains("75% { background-position: -66px -66px; animation-timing-function: steps(1, end); }"));
		assertTrue(css.contains("100% { background-position: -66px -66px }"));
	}

	@Test
	void staticEntriesGetPositionOnly() {
		ManifestWriter.SheetEntry staticEntry = new ManifestWriter.SheetEntry("minecraft/item/x", "x_1a2b3c4d", "main", 100, 200, 64, 64, 16, 16, 1, List.of(1), 0, 0, 0, null);
		String css = CssWriter.generate(List.of(staticEntry));
		assertTrue(css.contains(".monumenta-x_1a2b3c4d {"));
		assertTrue(css.contains("background-position: -100px -200px;"));
		assertTrue(!css.contains("@keyframes"));
	}

	@Test
	void baseClassAndReducedMotionAreEmitted() {
		String css = CssWriter.generate(List.of(entry("x_1a2b3c4d", 2, List.of(1, 1), 1, 0)));
		assertTrue(css.startsWith(".monumenta-items {"));
		assertTrue(css.contains("@media (prefers-reduced-motion: reduce) {"));
		assertTrue(css.contains(".monumenta-x_1a2b3c4d { animation: none; }"));
	}
}
