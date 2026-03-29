package com.gamerduck.betterjoin;

import com.gamerduck.betterjoin.api.Config;
import com.gamerduck.betterjoin.commands.ReloadCommand;
import com.gamerduck.betterjoin.api.Colors;
import com.gamerduck.betterjoin.early.TransformerCompiler;
import com.hypixel.hytale.common.thread.HytaleForkJoinThreadFactory;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.connection.DisconnectType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.*;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.task.TaskRegistration;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.npc.util.Timer;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BetterJoinPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Path playersData = Path.of("universe").resolve("players");
    private final Path earlyPlugins = Path.of("earlyplugins");
    private final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(0);
    private final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public BetterJoinPlugin(@Nonnull JavaPluginInit init) throws IOException {
        super(init);
            // Must create/update config file BEFORE withConfig() triggers a framework read
            Config.createOrUpdateConfig(this);
            Config.initialize(this, this.withConfig("config", Config.CODEC))
                    .thenRun(() -> {
                        // TODO - Make the disabled jar a configuration value, for now have people manually rename
//                        if (Config.getConfig().isDisableLeaveMessages()
//                                && Files.exists(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar.disabled"))) {
//                            File file = new File(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar.disabled").toUri());
//                            file.renameTo(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar").toFile());
//                            file.delete();
//                        } else if (Files.exists(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar"))) {
//                            File file = new File(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar").toUri());
//                            file.renameTo(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar.disabled").toFile());
//                            file.delete();
//                        }
                    });

        if (Files.notExists(earlyPlugins)) {
            Files.createDirectories(earlyPlugins);
        }

        if (Files.notExists(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar"))
                && Files.notExists(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar.disabled"))) {
            try {
                TransformerCompiler compiler = new TransformerCompiler();

                compiler.createJarFromCompiledClass("earlyplugins/BetterJoinEarlyPlugin.jar");

                File file = new File(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar").toUri());
                file.renameTo(earlyPlugins.resolve("BetterJoinEarlyPlugin.jar.disabled").toFile());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(new ReloadCommand(this));

        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, e -> {
            if (Config.getConfig().isDisableJoinMessages()) {
                e.setJoinMessage(null); // null message = skip broadcast in World.onFinishPlayerJoining
            }
        });
        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

    }

    private void onPlayerConnect(PlayerConnectEvent e) {
        Path playerPath = playersData.resolve(e.getPlayerRef().getUuid() + ".json");
        AtomicBoolean playerNotExists = new AtomicBoolean(Files.notExists(playerPath));
        this.getTaskRegistry().registerTask(schedule(() -> {
            PlayerRef ref = e.getPlayerRef();
            if (ref.isValid()) {
                String message = playerNotExists.get() ? Config.getConfig().getWelcomeMessage() : Config.getConfig().getJoinMessage();
                message = message.replace("{player}", ref.getUsername()).replaceAll("[&§]([0-9a-fk-or])", "");
                if (Config.getConfig().isUseTitles()) {
                    splitAndSendTitle(message);
                } else {
                    Universe.get().sendMessage(Colors.formatColorCodes(message.replace("{player}", ref.getUsername())));
                }
            }
        }, 1, TimeUnit.SECONDS));
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent e) {
        if (e.getDisconnectReason().getClientDisconnectType() != null) {
            String message = Config.getConfig().getLeaveMessage().replace("{player}", e.getPlayerRef().getUsername()).replaceAll("[&§]([0-9a-fk-or])", "");
            if (Config.getConfig().isUseTitles()) {
                splitAndSendTitle(message);
            } else {
                Universe.get().sendMessage(Colors.formatColorCodes(message.replace("{player}", e.getPlayerRef().getUsername())));
            }
        }
    }

    private void splitAndSendTitle(String message) {
        Iterator<String> iterator = message.lines().iterator();
        Message topLine = Message.raw(iterator.next());
        StringBuilder rest = new StringBuilder();
        iterator.forEachRemaining(s -> {
            rest.append(s);
            if (iterator.hasNext()) rest.append("\n");
        });
        Message restLines = Message.raw(rest.toString());
        Universe.get().getPlayers().forEach((playerRef) -> {
            if (playerRef.getWorldUuid() != null) {
                World world = Universe.get().getWorld(playerRef.getWorldUuid());
                world.execute(() -> {
                    EventTitleUtil.showEventTitleToPlayer(playerRef, restLines, topLine, false, null, 5, 1, 1);
                });
            }
        });
    }

    private ScheduledFuture<Void> schedule(Runnable command, long delay, TimeUnit unit){
        return (ScheduledFuture<Void>) SCHEDULED_EXECUTOR.schedule(()-> EXECUTOR.execute(command), delay, unit);
    }
}