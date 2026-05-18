package grill24.workingwolves.fabric;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import grill24.workingwolves.Config;
import grill24.workingwolves.WorkingWolves;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FabricConfig {
    private static final Path CONFIG_PATH = Path.of("config", "workingwolves.json");

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                if (obj.has("maxWolvesPerPlayer")) {
                    Config.maxWolvesPerPlayer = obj.get("maxWolvesPerPlayer").getAsInt();
                }
                if (obj.has("detectionRange")) {
                    Config.detectionRange = obj.get("detectionRange").getAsInt();
                }
                if (obj.has("expeditionDurationMinutesTier1")) {
                    Config.expeditionDurationMinutesTier1 = obj.get("expeditionDurationMinutesTier1").getAsInt();
                }
                if (obj.has("expeditionDurationMinutesTier2")) {
                    Config.expeditionDurationMinutesTier2 = obj.get("expeditionDurationMinutesTier2").getAsInt();
                }
                if (obj.has("expeditionDurationMinutesTier3")) {
                    Config.expeditionDurationMinutesTier3 = obj.get("expeditionDurationMinutesTier3").getAsInt();
                }
                if (obj.has("hunterScanRange")) {
                    Config.hunterScanRange = obj.get("hunterScanRange").getAsInt();
                }

                WorkingWolves.LOGGER.info("Loaded config from {}", CONFIG_PATH);
            } catch (IOException e) {
                WorkingWolves.LOGGER.warn("Failed to read config, using defaults", e);
                saveDefaults();
            }
        } else {
            saveDefaults();
        }
    }

    private static void saveDefaults() {
        JsonObject obj = new JsonObject();
        obj.addProperty("maxWolvesPerPlayer", Config.maxWolvesPerPlayer);
        obj.addProperty("detectionRange", Config.detectionRange);
        obj.addProperty("expeditionDurationMinutesTier1", Config.expeditionDurationMinutesTier1);
        obj.addProperty("expeditionDurationMinutesTier2", Config.expeditionDurationMinutesTier2);
        obj.addProperty("expeditionDurationMinutesTier3", Config.expeditionDurationMinutesTier3);
        obj.addProperty("hunterScanRange", Config.hunterScanRange);
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, new GsonBuilder().setPrettyPrinting().create().toJson(obj));
        } catch (IOException e) {
            WorkingWolves.LOGGER.warn("Failed to save default config", e);
        }
    }
}
