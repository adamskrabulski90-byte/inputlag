package dev.inputlag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Prosty config JSON: .minecraft/config/inputlag.json */
public final class InputLagConfig {

    public enum ScrollDirection {
        /** Scroll w dół wypycha przedmioty do drugiego ekwipunku, w górę pobiera. */
        NORMAL,
        /** Odwrotnie niż NORMAL. */
        INVERTED,
        /** Scroll "w stronę" drugiego ekwipunku wysyła, "od niego" pobiera. */
        POSITION_AWARE
    }

    @SerializedName("RMBTweak")
    public boolean rmbTweak = true;
    @SerializedName("LMBTweakWithItem")
    public boolean lmbTweakWithItem = true;
    @SerializedName("LMBTweakWithoutItem")
    public boolean lmbTweakWithoutItem = true;
    @SerializedName("WheelTweak")
    public boolean wheelTweak = true;
    @SerializedName("WheelScrollDirection")
    public ScrollDirection wheelScrollDirection = ScrollDirection.POSITION_AWARE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static InputLagConfig instance = new InputLagConfig();

    public static InputLagConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("inputlag.json");
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                InputLagConfig loaded = GSON.fromJson(r, InputLagConfig.class);
                if (loaded != null) {
                    if (loaded.wheelScrollDirection == null) loaded.wheelScrollDirection = ScrollDirection.POSITION_AWARE;
                    instance = loaded;
                }
            } catch (Exception e) {
                System.err.println("[inputLag] Nie można wczytać configu, używam domyślnych: " + e);
            }
        }
        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            System.err.println("[inputLag] Nie można zapisać configu: " + e);
        }
    }
}
