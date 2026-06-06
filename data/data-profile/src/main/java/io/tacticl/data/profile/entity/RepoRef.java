package io.tacticl.data.profile.entity;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed GitHub repo reference: owner + name + canonical https URL. Parsing is the
 * dedup key for {@link UserRepo} (so {@code .git}, trailing slashes, and an embedded
 * {@code x-access-token@} credential all collapse to one canonical URL).
 */
public record RepoRef(String owner, String name, String canonicalUrl) {

    // Mirrors RepoCommand's accepted shape (owner/repo), but tolerant of an optional
    // .git suffix, trailing slash, and a userinfo (token) prefix on the host.
    private static final Pattern GH = Pattern.compile(
        "^https://(?:[^@/]+@)?github\\.com/([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?/?$");

    /** Parse a GitHub https URL → owner/name/canonical, or empty if it isn't one. */
    public static Optional<RepoRef> parse(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return Optional.empty();
        }
        Matcher m = GH.matcher(repoUrl.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String owner = m.group(1);
        String name = m.group(2);
        return Optional.of(new RepoRef(owner, name, "https://github.com/" + owner + "/" + name));
    }
}
