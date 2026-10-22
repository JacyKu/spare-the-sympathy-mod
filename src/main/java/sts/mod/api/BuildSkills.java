package sts.mod.api;

import java.util.Map;
import java.util.Set;

/**
 * The skill portion of a build: class, spec, and the points spent in each
 * class/spec ability. Parsed either from a v1_ build token or from the site's
 * public-build skills endpoint.
 */
public record BuildSkills(
	String className,
	String specName,
	Map<String, Integer> skills,
	Map<String, Integer> specSkills,
	Set<String> enhancements
) {
}
