package sts.mod.client.view;

import java.util.List;
import org.junit.jupiter.api.Test;
import sts.mod.api.BuildTokenEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewedPlayersTest {
	private static BuildTokenEncoder.Skill skill(String id) {
		return new BuildTokenEncoder.Skill(id, 1);
	}

	@Test
	void tracksTheTwoPartsOfTheAbilitiesView() {
		ViewedPlayers.CachedPlayer player = ViewedPlayers.cache("AbilitiesProgressProbe");
		assertEquals(0, player.abilitiesProgress());

		// Viewing the class's ability pages alone: 1/2.
		player.mergeAbilities(List.of(skill("Whirlwind")), List.of(), List.of());
		assertEquals(1, player.abilitiesProgress());

		// Visiting the spec page (or its confirmed "no spec" row) completes it.
		player.markSpecSeen();
		assertEquals(2, player.abilitiesProgress());
	}

	@Test
	void viewingOnlyTheSpecPageIsStillPartial() {
		ViewedPlayers.CachedPlayer player = ViewedPlayers.cache("SpecProgressProbe");
		player.markSpecSeen();
		assertEquals(1, player.abilitiesProgress());
	}

	@Test
	void aMarkedAbilitiesViewCountsAsComplete() {
		ViewedPlayers.CachedPlayer player = ViewedPlayers.cache("ViewedProgressProbe");
		player.markViewed(ViewedPlayers.Kind.ABILITIES);
		assertEquals(2, player.abilitiesProgress());
	}

	@Test
	void aFreshViewReplacesOnlyItsOwnCapture() {
		ViewedPlayers.CachedPlayer player = ViewedPlayers.cache("FreshViewProbe");
		player.mergeEquipment(new String[] { "Sword", null, null, null, null, null }, List.of());
		player.markViewed(ViewedPlayers.Kind.STATS);
		player.mergeAbilities(List.of(skill("Whirlwind")), List.of(), List.of());
		player.markSpecSeen();
		player.mergeCharms(List.of("Abc-Xyz-1-A"), List.of());
		player.markViewed(ViewedPlayers.Kind.CHARMS);
		assertTrue(player.hasEquipment());
		assertTrue(player.hasAbilities());
		assertTrue(player.hasCharms());

		// A fresh /ps replaces only the equipment capture.
		player.resetStats();
		assertFalse(player.hasEquipment());
		assertFalse(player.isViewed(ViewedPlayers.Kind.STATS));
		assertTrue(player.hasAbilities());
		assertTrue(player.hasCharms());

		// A fresh /pa replaces only the class/spec/ability capture.
		player.resetAbilities();
		assertFalse(player.hasAbilities());
		assertFalse(player.isViewed(ViewedPlayers.Kind.ABILITIES));
		assertFalse(player.specSeen());
		assertTrue(player.hasCharms());

		// A fresh /vc replaces only the charm capture.
		player.resetCharms();
		assertFalse(player.hasCharms());
		assertFalse(player.isViewed(ViewedPlayers.Kind.CHARMS));
		assertTrue(player.isEmpty());
	}
}
