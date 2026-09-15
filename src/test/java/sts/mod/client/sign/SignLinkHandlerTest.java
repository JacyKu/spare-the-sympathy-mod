package sts.mod.client.sign;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignLinkHandlerTest {
	@Test
	void readsTheIdOnTheNextLine() {
		var result = SignLinkHandler.inspect(List.of("[STS]", "AbC123xy", "", ""));
		assertTrue(result.marker());
		assertEquals("AbC123xy", result.id());
	}

	@Test
	void ignoresContentAboveAndBelow() {
		var result = SignLinkHandler.inspect(List.of("My build here", "[STS]", "AbC123xy", "visit /base"));
		assertTrue(result.marker());
		assertEquals("AbC123xy", result.id());
	}

	@Test
	void skipsBlankLinesAfterTheMarker() {
		var result = SignLinkHandler.inspect(List.of("[STS]", "", "  ", "AbC123xy"));
		assertEquals("AbC123xy", result.id());
	}

	@Test
	void acceptsTheIdOnTheMarkerLine() {
		var result = SignLinkHandler.inspect(List.of("line one", "[STS] AbC123xy", "line three", ""));
		assertTrue(result.marker());
		assertEquals("AbC123xy", result.id());
	}

	@Test
	void markerIsCaseInsensitive() {
		var result = SignLinkHandler.inspect(List.of("[sts]", "AbC123xy"));
		assertEquals("AbC123xy", result.id());
	}

	@Test
	void rejectsAnInvalidIdLine() {
		var result = SignLinkHandler.inspect(List.of("[STS]", "hello world!", ""));
		assertTrue(result.marker());
		assertNull(result.id());
	}

	@Test
	void markerWithoutAnyFollowingLine() {
		var result = SignLinkHandler.inspect(List.of("", "", "", "[STS]"));
		assertTrue(result.marker());
		assertNull(result.id());
	}

	@Test
	void plainSignIsIgnored() {
		var result = SignLinkHandler.inspect(List.of("Welcome", "AbC123xy", "", ""));
		assertFalse(result.marker());
		assertNull(result.id());
	}
}
