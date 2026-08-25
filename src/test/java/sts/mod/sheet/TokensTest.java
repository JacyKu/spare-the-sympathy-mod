package sts.mod.sheet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TokensTest {
	@Test
	void tokenSanitizesAndHashes() {
		String token = Tokens.tokenFor("minecraft/item/Celestial Gem");
		assertEquals("minecraft_item_celestial_gem", token.substring(0, token.length() - 9));
		assertEquals(8, token.substring(token.length() - 8).length());
	}

	@Test
	void tokensAreStableAndDistinct() {
		assertEquals(Tokens.tokenFor("minecraft/item/x"), Tokens.tokenFor("minecraft/item/x"));
		assertNotEquals(Tokens.tokenFor("minecraft/item/x"), Tokens.tokenFor("minecraft/item/y"));
		assertNotEquals(Tokens.tokenFor("minecraft/item/ab"), Tokens.tokenFor("minecraft/ab"));
	}
}
