package com.gamerduck.betterjoin.api;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.function.FunctionCodec;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class Config {
    private static com.hypixel.hytale.server.core.util.Config<Config> config;
    private static final Path pluginsFolder = Path.of("mods");

    private static final FunctionCodec<String[], String> MULTI_LINED_MESSAGE_CODEC = new FunctionCodec<>(Codec.STRING_ARRAY,
            (arr) -> String.join("\n", arr),
            (str) -> str.split("\n"));
    public static final BuilderCodec<Config> CODEC;

    static {
        BuilderCodec.Builder<Config> builderBase = BuilderCodec.builder(Config.class, Config::new);
        builderBase.append(new KeyedCodec<>("WelcomeMessage", MULTI_LINED_MESSAGE_CODEC), (config, value, info) -> {
            config.welcomeMessage = value;
        }, (config, info) -> {
            return config.welcomeMessage;
        }).add();builderBase.append(new KeyedCodec<>("JoinMessage", MULTI_LINED_MESSAGE_CODEC), (config, value, info) -> {
            config.joinMessage = value;
        }, (config, info) -> {
            return config.joinMessage;
        }).add();
        builderBase.append(new KeyedCodec<>("LeaveMessage", MULTI_LINED_MESSAGE_CODEC), (config, value, info) -> {
            config.leaveMessage = value;
        }, (config, info) -> {
            return config.leaveMessage;
        }).add();
        builderBase.append(new KeyedCodec<>("DisableJoinMessages", Codec.BOOLEAN), (config, value, info) -> {
            config.disableJoinMessages = value;
        }, (config, info) -> {
            return config.disableJoinMessages;
        }).add();
//        builderBase.append(new KeyedCodec<>("DisableLeaveMessages", Codec.BOOLEAN), (config, value, info) -> {
//            config.disableLeaveMessages = value;
//        }, (config, info) -> {
//            return config.disableLeaveMessages;
//        }).add();

        builderBase.append(new KeyedCodec<>("UseTitles", Codec.BOOLEAN), (config, value, info) -> {
            config.useTitles = value;
        }, (config, info) -> {
            return config.useTitles;
        }).add();
        builderBase.append(new KeyedCodec<>("MessageReloaded", Codec.STRING), (config, value, info) -> {
            config.messageReloaded = value;
        }, (config, info) -> {
            return config.messageReloaded;
        }).add();
        builderBase.append(new KeyedCodec<>("NoPermission", Codec.STRING), (config, value, info) -> {
            config.noPermission = value;
        }, (config, info) -> {
            return config.noPermission;
        }).add();

        CODEC = builderBase.build();
    }

    private String welcomeMessage = "&6A new player has joined! Welcome &e{player}";
    private String joinMessage = "&a+&f {player}";
    private String leaveMessage = "&c-&f {player}";
    private boolean useTitles = false;
    private boolean disableJoinMessages = true;
    private boolean disableLeaveMessages = true;
    private String messageReloaded = "&aConfiguration reloaded successfully!";
    private String noPermission = "&cYou don't have permission to use this command!";

    public String getWelcomeMessage() {
        return this.welcomeMessage;
    }

    public String getJoinMessage() {
        return this.joinMessage;
    }

    public String getLeaveMessage() {
        return this.leaveMessage;
    }

    public boolean isDisableJoinMessages() {
        return this.disableJoinMessages;
    }

    public boolean isDisableLeaveMessages() {
        return this.disableLeaveMessages;
    }

    public boolean isUseTitles() {
        return this.useTitles;
    }

    public String getMessageReloaded() {
        return this.messageReloaded;
    }

    public String getNoPermission() {
        return this.noPermission;
    }

    public static Config getConfig() {
        return config.get();
    }

    public static CompletableFuture<Void> initialize(JavaPlugin plugin, com.hypixel.hytale.server.core.util.Config<Config> config) {
        Config.config = config;
        return CompletableFuture.completedFuture(null);
    }

    public static void reloadConfig(JavaPlugin plugin) throws IOException {
        createOrUpdateConfig(plugin);
        config.load();
        plugin.getLogger().atInfo().log(Colors.stripColorCodes((config.get()).getMessageReloaded()));
    }

    public static void createOrUpdateConfig(JavaPlugin plugin) throws IOException {
        String group = plugin.getManifest().getGroup();
        String name = plugin.getManifest().getName();
        Path pluginFolder = pluginsFolder.resolve(group + "_" + name);
        Path config = pluginFolder.resolve("config.json");
        if (Files.notExists(pluginFolder)) {
            Files.createDirectories(pluginFolder);
        }
        if (Files.notExists(config)) {
            createConfigFolder(config);
        } else {
            updateConfig(config);
        }
    }

    private static void updateConfig(Path config) throws IOException {
        BsonDocument document = BsonDocument.parse(Files.readString(config));
        Config newConfig = Config.CODEC.decode(document, new ExtraInfo());
        document = Config.CODEC.encode(newConfig, new ExtraInfo());
        writeToConfig(config, document);
    }

    private static void createConfigFolder(Path config) throws IOException {
        Files.createFile(config);
        Config emptyConfig = new Config();
        BsonDocument document = Config.CODEC.encode(emptyConfig, new ExtraInfo());
        writeToConfig(config, document);
    }

    private static void writeToConfig(Path config, BsonDocument document) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(config.toFile()))) {
            String json = document.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).indent(true).build());
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}