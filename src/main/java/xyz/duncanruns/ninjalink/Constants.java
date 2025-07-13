package xyz.duncanruns.ninjalink;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Constants {
    /**
     * The version of NinjaLink.
     */
    public static final String VERSION = Optional.ofNullable(NinjaLinkLaunch.class.getPackage().getImplementationVersion()).orElse("DEV");
    /**
     * The current protocol version
     * 1: Has most base stuff.
     * 2: Added F3C ClientData. Deprecated PING, but it is now ignored not denied. Therefore, superset of 1.
     */
    public static final int PROTOCOL_VERSION = 2;
    /**
     * The protocol versions accepted by the server.
     */
    public static final List<Integer> ACCEPTED_PROTOCOLS = Arrays.asList(1, PROTOCOL_VERSION);
    /**
     * Simple alphanumeric and underscore word pattern.
     */
    public static final Pattern NAME_PATTERN = Pattern.compile("^\\w+$");
    /**
     * Simple pattern for alphanumeric, underscore, and special symbols: @, !, $, %, ^, &, *, +, #.
     * <p>
     * Can be any length including zero.
     */
    public static final Pattern PASSWORD_PATTERN = Pattern.compile("^[\\w@!$%^&*+#]*$");

    /**
     * The default address to connect to.
     */
    public static final String DEFAULT_ADDRESS = "ws://ninjalink.duncanruns.xyz:52534";

    private Constants() {
    }
}
