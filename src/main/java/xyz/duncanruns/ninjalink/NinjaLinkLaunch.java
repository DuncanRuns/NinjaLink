package xyz.duncanruns.ninjalink;

import xyz.duncanruns.ninjalink.client.NinjaLinkClient;
import xyz.duncanruns.ninjalink.server.NinjaLinkServer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public final class NinjaLinkLaunch {
    private NinjaLinkLaunch() {
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException, IOException {
        if (args.length == 0) {
            NinjaLinkClient.main(args);
            return;
        }

        if (args[0].equals("server")) {
            NinjaLinkServer.main(Arrays.copyOfRange(args, 1, args.length));
        } else if (args[0].equals("client")) {
            NinjaLinkClient.main(Arrays.copyOfRange(args, 1, args.length));
        } else {
            NinjaLinkClient.main(args);
        }
    }
}
