package sts.mod.client.view;

import java.util.List;
import org.junit.jupiter.api.Test;
import sts.mod.api.BuildTokenEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
