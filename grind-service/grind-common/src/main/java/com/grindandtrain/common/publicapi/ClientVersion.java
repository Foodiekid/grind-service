package com.grindandtrain.common.publicapi;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The app build that sent a request, parsed from {@code X-Grind-Client} (for example {@code ios/1.4.2}).
 *
 * @author Dheeraj_Edupuganti
 */
public record ClientVersion(String platform, int major, int minor, int patch) implements Comparable<ClientVersion> {

    private static final Pattern HEADER = Pattern.compile("^(ios|android|web)/(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})$");
    private static final Pattern VERSION = Pattern.compile("^(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})$");

    public static Optional<ClientVersion> parse(String header) {
        if (header == null) {
            return Optional.empty();
        }
        Matcher m = HEADER.matcher(header);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ClientVersion(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4))));
    }

    /** A bare {@code major.minor.patch} for the given platform (configuration values). */
    public static ClientVersion of(String platform, String version) {
        Matcher m = VERSION.matcher(version);
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a version: " + version);
        }
        return new ClientVersion(platform, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)));
    }

    @Override
    public int compareTo(ClientVersion o) {
        int c = Integer.compare(major, o.major);
        if (c == 0) {
            c = Integer.compare(minor, o.minor);
        }
        return c != 0 ? c : Integer.compare(patch, o.patch);
    }

    @Override
    public String toString() {
        return platform + "/" + major + "." + minor + "." + patch;
    }
}
