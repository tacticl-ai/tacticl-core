package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stateless utility that extracts PDLC role skip intent from natural language command text.
 *
 * <p>Keyword phrases are matched case-insensitively. The "just implement" / "implement only"
 * patterns are registered first in the {@link LinkedHashMap} so they take precedence when
 * combined with individual role-skip phrases.</p>
 *
 * <p>Usage: {@code Set<PdlcRole> toSkip = RoleSkipParser.parse(commandText);}</p>
 */
public final class RoleSkipParser {

	/** All roles except IMPLEMENTER — used by "just implement" / "implement only" patterns. */
	private static final Set<PdlcRole> ALL_EXCEPT_IMPLEMENTER;

	static {
		EnumSet<PdlcRole> set = EnumSet.allOf(PdlcRole.class);
		set.remove(PdlcRole.IMPLEMENTER);
		ALL_EXCEPT_IMPLEMENTER = Set.copyOf(set);
	}

	/**
	 * Ordered map of (compiled regex pattern → roles to skip).
	 *
	 * <p>Insertion order matters: "just implement" / "implement only" are first so they win
	 * priority during the short-circuit check that returns immediately on match.</p>
	 */
	private static final LinkedHashMap<Pattern, List<PdlcRole>> PATTERNS = buildPatterns();

	private static LinkedHashMap<Pattern, List<PdlcRole>> buildPatterns() {
		LinkedHashMap<Pattern, List<PdlcRole>> map = new LinkedHashMap<>();

		// High-priority composite patterns (must precede individual role patterns)
		map.put(compile("just\\s+implement|implement\\s+only"),
				List.copyOf(ALL_EXCEPT_IMPLEMENTER));

		// Individual role patterns
		map.put(compile("skip\\s+review|no\\s+review"),
				List.of(PdlcRole.REVIEWER));

		map.put(compile("skip\\s+tests?|no\\s+testing|don[''\u2019]?t\\s+test"),
				List.of(PdlcRole.TESTER));

		map.put(compile("skip\\s+security|no\\s+security\\s+check"),
				List.of(PdlcRole.SECURITY_ANALYST));

		map.put(compile("skip\\s+docs?|no\\s+documentation"),
				List.of(PdlcRole.TECHNICAL_WRITER));

		map.put(compile("skip\\s+planning"),
				List.of(PdlcRole.PM, PdlcRole.PLANNER));

		map.put(compile("no\\s+retro"),
				List.of(PdlcRole.RETRO_ANALYST));

		map.put(compile("skip\\s+design"),
				List.of(PdlcRole.DESIGNER));

		map.put(compile("skip\\s+research"),
				List.of(PdlcRole.RESEARCHER));

		map.put(compile("skip\\s+devops|no\\s+deploy"),
				List.of(PdlcRole.DEVOPS));

		return map;
	}

	private static Pattern compile(String regex) {
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	private RoleSkipParser() {
		// utility class — no instances
	}

	/**
	 * Parses {@code commandText} and returns the set of {@link PdlcRole}s that the user wants
	 * to skip. Returns an empty set when no skip intent is detected.
	 *
	 * @param commandText raw command text from the user (may be {@code null})
	 * @return immutable set of roles to skip; never {@code null}
	 */
	public static Set<PdlcRole> parse(String commandText) {
		if (commandText == null || commandText.isBlank()) {
			return Set.of();
		}

		EnumSet<PdlcRole> result = EnumSet.noneOf(PdlcRole.class);

		for (Map.Entry<Pattern, List<PdlcRole>> entry : PATTERNS.entrySet()) {
			if (entry.getKey().matcher(commandText).find()) {
				result.addAll(entry.getValue());
			}
		}

		return result.isEmpty() ? Set.of() : Set.copyOf(result);
	}
}
