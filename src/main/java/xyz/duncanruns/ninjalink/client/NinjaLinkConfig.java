package xyz.duncanruns.ninjalink.client;

import com.google.gson.Gson;
import xyz.duncanruns.ninjalink.util.FileUtil;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class NinjaLinkConfig {
    private static final Path CONFIG_FILE = Paths.get("ninja-link-config.json");

    public String ip = "";
    public String nickname = "";
    public boolean guiPinned = false;
    public int fontSize = 16;
    public Rectangle bounds = new Rectangle(100, 100, 480, 270);

    public static boolean configFileExists() {
        return Files.exists(CONFIG_FILE);
    }

    public static Optional<NinjaLinkConfig> tryLoad() {
        try {
            return Optional.ofNullable(FileUtil.readJson(CONFIG_FILE, NinjaLinkConfig.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public synchronized boolean trySave() {
        try {
            FileUtil.writeString(CONFIG_FILE, toJson());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String toJson() {
        return new Gson().toJson(this);
    }
}
