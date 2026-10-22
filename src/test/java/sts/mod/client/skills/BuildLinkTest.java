package sts.mod.client.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import sts.mod.api.BuildSkills;
import sts.mod.api.BuildTokenEncoder;

class BuildLinkTest {
	@Test
	void decodesClassAndSpecSkillsFromCurrentToken() throws Exception {
		String token = BuildTokenEncoder.encode(
			List.of("None", "None", "None", "None", "None", "None"),
			null,
			"Test build",
			"Cleric",
			"Paladin",
			List.of(new BuildTokenEncoder.Skill("DivineJustice", 2), new BuildTokenEncoder.Skill("HeavenlyBoon", 1)),
			List.of(new BuildTokenEncoder.Skill("HolyJavelin", 1)),
			List.of("Toughness"),
			new int[] { 100, 0, 0, 0, 0, 0, 3 }
		);

		BuildSkills skills = BuildLink.decodeToken(token);

		assertEquals("cleric", skills.className());
		assertEquals("Paladin", skills.specName());
		assertEquals(2, skills.skills().get("DivineJustice"));
		assertEquals(1, skills.skills().get("HeavenlyBoon"));
		assertEquals(1, skills.specSkills().get("HolyJavelin"));
		assertEquals(Set.of("Toughness"), skills.enhancements());
	}

	@Test
	void decodesLegacyQueryToken() throws Exception {
		BuildSkills skills = BuildLink.load("https://sts.deepa.cat/builder/?cl=Cleric&sp=Paladin&sk=DivineJustice:2,HolyJavelin:1");

		assertEquals("cleric", skills.className());
		assertEquals("Paladin", skills.specName());
		assertEquals(2, skills.skills().get("DivineJustice"));
		assertEquals(1, skills.skills().get("HolyJavelin"));
		assertNull(skills.specSkills().get("HolyJavelin"));
	}
}
