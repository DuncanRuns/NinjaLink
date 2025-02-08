package xyz.duncanruns.ninjalink;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class Constants {
    /**
     * The version of NinjaLink
     */
    public static final String VERSION = Optional.ofNullable(NinjaLinkLaunch.class.getPackage().getImplementationVersion()).orElse("DEV");
    /**
     * The current protocol version
     */
    public static final int PROTOCOL_VERSION = 1;
    /**
     * The protocol versions accepted by the server
     */
    public static final List<Integer> ACCEPTED_PROTOCOLS = Collections.singletonList(PROTOCOL_VERSION);

    private Constants() {
    }
}
