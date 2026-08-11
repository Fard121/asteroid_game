/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;
import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.GameKeys;
import dk.sdu.mmmi.cbse.common.data.GameState;
import dk.sdu.mmmi.cbse.common.data.GameStateManager;
import dk.sdu.mmmi.cbse.common.data.PlayerState;
import dk.sdu.mmmi.cbse.common.data.RuntimeObjectCategory;
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;
import dk.sdu.mmmi.cbse.common.services.IPostEntityProcessingService;
import dk.sdu.mmmi.cbse.common.sound.SoundManager;
import dk.sdu.mmmi.cbse.common.util.ServiceLocator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClientException;

/**
 *
 * @author jcs
 */
class Game {

    private final GameData gameData = new GameData();
    private final World world = new World();
    private final Map<Entity, Polygon> polygons = new ConcurrentHashMap<>();
    private final Map<Entity, Rectangle[]> healthBars = new ConcurrentHashMap<>();
    private static final double HEALTH_BAR_WIDTH = 20;
    private static final double HEALTH_BAR_HEIGHT = 3;
    private final Pane gameWindow = new Pane();
    private long frameCount = 0;
    private HUDRenderer hudRenderer;
    private int menuSelectedIndex = 0;
    private Polygon flamePolygon;
    private final Random random = new Random();
    private final ScoreClient scoreClient;
    private final ComponentRegistry componentRegistry;
    private int lastPushedScore = -1;

    // Commands arriving from the external command channel. Filled by the
    // socket thread, drained by the game loop at the top of a frame - see
    // drainPluginCommands().
    private final Queue<PluginCommandServer.PendingCommand> pluginCommands = new ConcurrentLinkedQueue<>();

    // Work handed in by background threads (currently the plugins/ folder
    // watcher) to be run on the game thread at the top of a frame.
    private final Queue<Runnable> gameThreadActions = new ConcurrentLinkedQueue<>();

    Game(List<IGamePluginService> gamePluginServices, List<IEntityProcessingService> entityProcessingServiceList, List<IPostEntityProcessingService> postEntityProcessingServices, ScoreClient scoreClient) {
        this.scoreClient = scoreClient;
        this.componentRegistry = new ComponentRegistry(gamePluginServices, entityProcessingServiceList, postEntityProcessingServices);
    }

    public void start(Stage window) throws Exception {
        gameWindow.setPrefSize(gameData.getDisplayWidth(), gameData.getDisplayHeight());
        gameWindow.setStyle("-fx-background-color: black;");
        addStarField();

        Text text = new Text(10, 20, "");
        text.setFill(Color.WHITE);
        gameWindow.getChildren().add(text);

        TextFlow centerTextFlow = new TextFlow();
        centerTextFlow.setTextAlignment(TextAlignment.CENTER);
        centerTextFlow.setPrefWidth(gameData.getDisplayWidth());
        centerTextFlow.setMaxWidth(gameData.getDisplayWidth());
        centerTextFlow.setMouseTransparent(true);
        gameWindow.getChildren().add(centerTextFlow);

        hudRenderer = new HUDRenderer(text, centerTextFlow, gameData.getDisplayHeight());

        flamePolygon = new Polygon(-4, 0, -12, -4, -12, 4);
        flamePolygon.setFill(Color.web("#ff9933"));
        flamePolygon.setStroke(Color.YELLOW);
        flamePolygon.setVisible(false);
        gameWindow.getChildren().add(flamePolygon);

        Scene scene = new Scene(gameWindow);

        // Scale the whole play field to fill the actual window size (e.g.
        // when maximized) while keeping every internal coordinate/layout
        // calculation (movement bounds, HUD centering, etc.) working
        // against the fixed 800x800 logical resolution - only the visual
        // presentation stretches, nothing about game logic changes.
        Scale responsiveScale = new Scale(1, 1, 0, 0);
        gameWindow.getTransforms().add(responsiveScale);
        responsiveScale.xProperty().bind(scene.widthProperty().divide(gameData.getDisplayWidth()));
        responsiveScale.yProperty().bind(scene.heightProperty().divide(gameData.getDisplayHeight()));

        scene.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.LEFT)) {
                gameData.getKeys().setKey(GameKeys.LEFT, true);
            }
            if (event.getCode().equals(KeyCode.RIGHT)) {
                gameData.getKeys().setKey(GameKeys.RIGHT, true);
            }
            if (event.getCode().equals(KeyCode.UP)) {
                gameData.getKeys().setKey(GameKeys.UP, true);
            }
            if (event.getCode().equals(KeyCode.DOWN)) {
                gameData.getKeys().setKey(GameKeys.DOWN, true);
            }
            if (event.getCode().equals(KeyCode.SPACE)) {
                gameData.getKeys().setKey(GameKeys.SPACE, true);
            }
            if (event.getCode().equals(KeyCode.R)) {
                gameData.getKeys().setKey(GameKeys.RESTART, true);
            }
            if (event.getCode().equals(KeyCode.P)) {
                gameData.getKeys().setKey(GameKeys.PAUSE, true);
            }
            if (event.getCode().equals(KeyCode.M)) {
                gameData.getKeys().setKey(GameKeys.MUTE, true);
            }
            if (event.getCode().equals(KeyCode.H)) {
                gameData.getKeys().setKey(GameKeys.HELP, true);
            }
            if (event.getCode().equals(KeyCode.Q)) {
                gameData.getKeys().setKey(GameKeys.QUIT, true);
            }
            if (event.getCode().equals(KeyCode.DIGIT1)) {
                gameData.getKeys().setKey(GameKeys.TOGGLE_PLAYER, true);
            }
            if (event.getCode().equals(KeyCode.DIGIT2)) {
                gameData.getKeys().setKey(GameKeys.TOGGLE_ENEMY, true);
            }
            if (event.getCode().equals(KeyCode.DIGIT3)) {
                gameData.getKeys().setKey(GameKeys.TOGGLE_WEAPON, true);
            }
        });
        scene.setOnKeyReleased(event -> {
            if (event.getCode().equals(KeyCode.LEFT)) {
                gameData.getKeys().setKey(GameKeys.LEFT, false);
            }
            if (event.getCode().equals(KeyCode.RIGHT)) {
                gameData.getKeys().setKey(GameKeys.RIGHT, false);
            }
            if (event.getCode().equals(KeyCode.UP)) {
                gameData.getKeys().setKey(GameKeys.UP, false);
            }
            if (event.getCode().equals(KeyCode.DOWN)) {
                gameData.getKeys().setKey(GameKeys.DOWN, false);
            }
            if (event.getCode().equals(KeyCode.SPACE)) {
                gameData.getKeys().setKey(GameKeys.SPACE, false);
            }
            if (event.getCode().equals(KeyCode.R)) {
                gameData.getKeys().setKey(GameKeys.RESTART, false);
            }
            if (event.getCode().equals(KeyCode.P)) {
                gameData.getKeys().setKey(GameKeys.PAUSE, false);
            }
            if (event.getCode().equals(KeyCode.M)) {
                gameData.getKeys().setKey(GameKeys.MUTE, false);
            }
            if (event.getCode().equals(KeyCode.H)) {
                gameData.getKeys().setKey(GameKeys.HELP, false);
            }
            if (event.getCode().equals(KeyCode.Q)) {
                gameData.getKeys().setKey(GameKeys.QUIT, false);
            }
            if (event.getCode().equals(KeyCode.DIGIT1)) {
                gameData.getKeys().setKey(GameKeys.TOGGLE_PLAYER, false);
            }
            if (event.getCode().equals(KeyCode.DIGIT2)) {
                gameData.getKeys().setKey(GameKeys.TOGGLE_ENEMY, false);
            }
            if (event.getCode().equals(KeyCode.DIGIT3)) {
                gameData.getKeys().setKey(GameKeys.TOGGLE_WEAPON, false);
            }

        });

        // Lookup all Game Plugins using ServiceLoader
        for (IGamePluginService iGamePlugin : getGamePluginServices()) {
            iGamePlugin.start(gameData, world);
        }
        for (Entity entity : world.getEntities()) {
            Polygon polygon = createPolygonFor(entity);
            polygons.put(entity, polygon);
            gameWindow.getChildren().add(polygon);
        }
        window.setScene(scene);
        window.setTitle("ASTEROIDS");
        window.show();

        // Opens the loopback command channel used by the `game plugin ...`
        // script. Purely additive: if it cannot bind, start() logs and the
        // game runs exactly as it always has.
        PluginCommandServer.start(PluginCommandServer.DEFAULT_PORT, pluginCommands);

        // The plugins/ folder drives what is running: delete a jar and that
        // plugin leaves the game, put it back and it returns - see
        // PluginFolderWatcher. Both callbacks run on the game thread.
        PluginFolderWatcher.start(gameThreadActions,
                moduleName -> {
                    System.out.println("[plugin] " + moduleName
                            + ": jar removed from plugins/ - unloading");
                    componentRegistry.unload(moduleName, gameData, world);
                },
                moduleName -> {
                    String loaded = componentRegistry.load(moduleName);
                    String enabled = componentRegistry.enable(moduleName, gameData, world);
                    System.out.println("[plugin] " + moduleName
                            + ": jar restored to plugins/ - " + loaded + "; " + enabled);
                });
    }

    public void render() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                frameCount++;

                // Runs before anything else in the frame, so a plugin is
                // always installed or removed between two updates rather
                // than in the middle of one.
                drainGameThreadActions();
                drainPluginCommands();

                if (gameData.getKeys().isPressed(GameKeys.MUTE)) {
                    SoundManager.toggleMute();
                }

                if (gameData.getKeys().isPressed(GameKeys.TOGGLE_PLAYER)) {
                    componentRegistry.toggle("Player", gameData, world);
                }
                if (gameData.getKeys().isPressed(GameKeys.TOGGLE_ENEMY)) {
                    componentRegistry.toggle("Enemy", gameData, world);
                }
                if (gameData.getKeys().isPressed(GameKeys.TOGGLE_WEAPON)) {
                    componentRegistry.toggle("Weapon", gameData, world);
                }

                GameStateManager stateManager = gameData.getGameStateManager();
                handleStateTransitions(stateManager);

                if (stateManager.getState() != GameState.START_MENU && stateManager.getState() != GameState.PAUSED) {
                    update();
                    if (stateManager.getState() == GameState.PLAYING) {
                        if (gameData.getPlayerState().isGameOver()) {
                            stateManager.gameOver();
                        } else if (gameData.getWaveState().hasReachedVictoryWave()) {
                            stateManager.victory();
                        }
                    }
                }

                draw();
                hudRenderer.update(gameData, world, menuSelectedIndex);
                if (gameData.getPlayerState().consumeDamagedThisFrame()) {
                    shakeScreen();
                }
                gameData.getKeys().update();
                // Runs last, deliberately: this is a hard dependency on the
                // Scoring microservice with no try/catch (see
                // docs/MICROSERVICE.md), so if it's unreachable this throws
                // on every frame the score changes - but only AFTER
                // rendering and key-state handling have already completed,
                // so an unreachable microservice can't freeze the game
                // itself, just the score sync.
                pushScoreIfChanged();
            }

        }.start();
    }

    /**
     * Executes every command the command channel has parked since the last
     * frame. Called from {@code handle()}, i.e. on the JavaFX application
     * thread and outside {@code update()}, which is what lets a plugin be
     * loaded or unloaded without racing the game loop.
     *
     * <p>A command that blows up is reported back to the shell that sent
     * it and logged - it never escapes into the animation timer, because a
     * throwing {@code handle()} would stop the timer and freeze the game.
     */
    /**
     * Runs work parked by background threads. Each action is isolated, so one
     * that fails is logged and the rest of the frame carries on - an
     * exception escaping here would stop the animation timer and freeze the
     * game.
     */
    private void drainGameThreadActions() {
        Runnable action;
        while ((action = gameThreadActions.poll()) != null) {
            try {
                action.run();
            } catch (RuntimeException e) {
                System.err.println("[plugin] background action failed: " + e);
            }
        }
    }

    private void drainPluginCommands() {
        PluginCommandServer.PendingCommand command;
        while ((command = pluginCommands.poll()) != null) {
            String result;
            try {
                result = executePluginCommand(command.commandLine, command);
            } catch (RuntimeException e) {
                System.err.println("[plugin] command '" + command.commandLine + "' failed: " + e);
                result = "command failed: " + e;
            }
            // A null result means the command answers for itself later, off
            // the game thread (see completeWhenClassLoaderReleased).
            if (result != null) {
                answer(command, result);
            }
        }
    }

    private void answer(PluginCommandServer.PendingCommand command, String result) {
        if (!result.endsWith(System.lineSeparator()) && !result.endsWith("\n")) {
            result = result + System.lineSeparator();
        }
        System.out.print("[plugin] " + command.commandLine + " -> " + result);
        command.complete(result);
    }

    /**
     * Finishes an {@code unload} on a background thread once the plugin's
     * class loader has actually been collected.
     *
     * <p>The bookkeeping half of an unload is already done at this point: the
     * game no longer references the plugin at all. What remains is outside the
     * program's control - the collector has to run before the plugin's jar
     * file handle is released, and until it does the jar cannot be deleted or
     * replaced on disk. Waiting for that on the game thread would stall the
     * loop, so the wait happens here instead and the shell simply gets its
     * reply a moment later, by which time replacing the jar is safe.
     */
    private void completeWhenClassLoaderReleased(PluginCommandServer.PendingCommand command,
            String moduleName, String message) {
        Thread releaser = new Thread(() -> {
            boolean released = ServiceLocator.INSTANCE.awaitClassLoaderRelease(moduleName, 3000);
            // The jar in plugins/ is replaceable either way, because plugins
            // are loaded from a staged copy - this only reports whether the
            // old classes have additionally been reclaimed yet.
            answer(command, message + (released
                    ? "; class loader released"
                    : "; classes not reclaimed yet (the jar is still free to replace)"));
        }, "plugin-unload-release");
        releaser.setDaemon(true);
        releaser.start();
    }

    private static final String PLUGIN_USAGE =
            "usage: plugin <list|load|enable|disable|unload|reload> [name]";

    /**
     * The whole external command surface. Deliberately tiny - it only maps
     * words onto the lifecycle operations {@link ComponentRegistry}
     * already implements.
     */
    private String executePluginCommand(String commandLine, PluginCommandServer.PendingCommand command) {
        String[] words = commandLine.trim().split("\\s+");
        int index = 0;
        // Tolerate a leading "game", so the script can forward its
        // arguments verbatim.
        if (index < words.length && words[index].equalsIgnoreCase("game")) {
            index++;
        }
        // Two command families: "plugin" manages whole components (their
        // classes are loaded and unloaded), "object" manages the runtime
        // object categories inside them (enemies, enemy bullets, player
        // bullets, asteroids) without touching any class.
        if (index < words.length && words[index].equalsIgnoreCase("object")) {
            return executeObjectCommand(words, index + 1);
        }
        if (index < words.length && words[index].equalsIgnoreCase("plugin")) {
            index++;
        }
        if (index >= words.length) {
            return PLUGIN_USAGE;
        }

        String action = words[index++].toLowerCase();
        String name = index < words.length ? words[index] : null;

        if (action.equals("list") || action.equals("status")) {
            return componentRegistry.list();
        }
        if (action.equals("help")) {
            return PLUGIN_USAGE;
        }
        if (name == null) {
            return "'" + action + "' needs a plugin name. " + PLUGIN_USAGE;
        }

        switch (action) {
            case "load":
                return componentRegistry.load(name);
            case "enable":
                return componentRegistry.enable(name, gameData, world);
            case "disable":
                return componentRegistry.disable(name, gameData, world);
            case "unload": {
                String message = componentRegistry.unload(name, gameData, world);
                completeWhenClassLoaderReleased(command, componentRegistry.moduleNameOf(name), message);
                return null; // answered off-thread, once the jar handle is free
            }
            case "reload":
                // Just the documented sequence, run back to back in a
                // single frame.
                return componentRegistry.unload(name, gameData, world)
                        + "; " + componentRegistry.load(name)
                        + "; " + componentRegistry.enable(name, gameData, world);
            default:
                return "unknown action '" + action + "'. " + PLUGIN_USAGE;
        }
    }

    private static final String OBJECT_USAGE =
            "usage: object <list|delete|restore> [Enemy|EnemyBullets|PlayerBullets|Asteroids]";

    /**
     * Runtime lifecycle control for the four object categories.
     *
     * <p>{@code delete} does two distinct things, and both are necessary:
     * it clears the entities of that category that are on the field right
     * now, and it marks the category inactive so its producer stops creating
     * new ones. Clearing alone would be pointless - an enemy would simply
     * fire again on its next cooldown.
     *
     * <p>Runs on the game thread at the top of a frame, so entities are never
     * removed while a processor is midway through iterating them.
     */
    private String executeObjectCommand(String[] words, int index) {
        if (index >= words.length) {
            return OBJECT_USAGE;
        }
        String action = words[index++].toLowerCase();

        if (action.equals("list") || action.equals("status")) {
            return describeObjectCategories();
        }
        if (action.equals("help")) {
            return OBJECT_USAGE;
        }
        if (index >= words.length) {
            return "'" + action + "' needs a category name. " + OBJECT_USAGE;
        }

        String name = words[index];
        RuntimeObjectCategory category = RuntimeObjectCategory.fromName(name);
        if (category == null) {
            return "unknown object category '" + name + "'. " + OBJECT_USAGE;
        }

        switch (action) {
            case "delete":
            case "disable":
            case "deactivate": {
                gameData.getRuntimeObjectState().setActive(category, false);
                int removed = removeEntitiesOf(category);
                return category.getDisplayName() + " deleted - " + removed
                        + " removed from the field, and no new ones will be created"
                        + System.lineSeparator();
            }
            case "restore":
            case "enable":
            case "activate": {
                gameData.getRuntimeObjectState().setActive(category, true);
                return category.getDisplayName() + " restored - new ones can be created again"
                        + System.lineSeparator();
            }
            default:
                return "unknown action '" + action + "'. " + OBJECT_USAGE;
        }
    }

    /**
     * Removes every entity of a category from the world. Iterates a snapshot,
     * and {@code World} is backed by a concurrent map, so this cannot disturb
     * a collection anyone else is reading. The renderer drops the matching
     * polygons on the next frame by its existing rule that a polygon whose
     * entity has left the world is removed.
     */
    private int removeEntitiesOf(RuntimeObjectCategory category) {
        int removed = 0;
        for (Entity entity : new java.util.ArrayList<>(world.getEntities())) {
            if (entity.getCategory() == category.getEntityCategory()) {
                world.removeEntity(entity);
                removed++;
            }
        }
        return removed;
    }

    private String describeObjectCategories() {
        StringBuilder out = new StringBuilder();
        out.append(String.format("%-15s %-10s %s%n", "CATEGORY", "STATE", "ON FIELD"));
        for (RuntimeObjectCategory category : RuntimeObjectCategory.values()) {
            int count = 0;
            for (Entity entity : world.getEntities()) {
                if (entity.getCategory() == category.getEntityCategory()) {
                    count++;
                }
            }
            out.append(String.format("%-15s %-10s %d%n",
                    category.getDisplayName(),
                    gameData.getRuntimeObjectState().isActive(category) ? "ACTIVE" : "INACTIVE",
                    count));
        }
        return out.toString();
    }

    private void handleStateTransitions(GameStateManager stateManager) {
        switch (stateManager.getState()) {
            case START_MENU:
                handleMenuNavigation();
                if (gameData.getKeys().isPressed(GameKeys.SPACE)) {
                    activateMenuOption(stateManager);
                }
                if (gameData.getKeys().isPressed(GameKeys.QUIT)) {
                    Platform.exit();
                }
                break;
            case PLAYING:
                if (gameData.getKeys().isPressed(GameKeys.PAUSE)) {
                    stateManager.togglePause();
                    menuSelectedIndex = 0;
                }
                break;
            case PAUSED:
                handleMenuNavigation();
                if (gameData.getKeys().isPressed(GameKeys.PAUSE)) {
                    stateManager.togglePause();
                }
                if (gameData.getKeys().isPressed(GameKeys.SPACE)) {
                    activateMenuOption(stateManager);
                }
                if (gameData.getKeys().isPressed(GameKeys.QUIT)) {
                    Platform.exit();
                }
                break;
            default:
                break;
        }
    }

    private void handleMenuNavigation() {
        if (gameData.getKeys().isPressed(GameKeys.UP)) {
            menuSelectedIndex = (menuSelectedIndex + 2) % 3;
            SoundManager.playMenuMove();
        }
        if (gameData.getKeys().isPressed(GameKeys.DOWN)) {
            menuSelectedIndex = (menuSelectedIndex + 1) % 3;
            SoundManager.playMenuMove();
        }
    }

    // Menu options are always [0]=Start/Resume, [1]=Help, [2]=Quit.
    private void activateMenuOption(GameStateManager stateManager) {
        switch (menuSelectedIndex) {
            case 0:
                if (stateManager.getState() == GameState.START_MENU) {
                    stateManager.startGame();
                } else {
                    stateManager.togglePause();
                }
                break;
            case 1:
                hudRenderer.toggleHelp();
                break;
            case 2:
                Platform.exit();
                break;
            default:
                break;
        }
    }

    private static final int SCORE_SYNC_RETRY_FRAMES = 180; // ~3s at 60 FPS

    private boolean scoreSyncUnreachable = false;
    private int scoreSyncRetryFramesRemaining = 0;

    // Mirrors the locally-tracked score to the Scoring microservice
    // whenever it changes - still a hard dependency (there is no local
    // fallback score path; the game keeps trying to reach the real
    // microservice, it just doesn't hammer it every frame). The first
    // failure logs once, then retries silently every
    // SCORE_SYNC_RETRY_FRAMES until it succeeds again, at which point it
    // logs once more and resumes syncing every frame the score changes.
    private void pushScoreIfChanged() {
        int currentScore = gameData.getScoreState().getScore();
        if (currentScore == lastPushedScore) {
            return;
        }
        if (scoreSyncUnreachable) {
            if (scoreSyncRetryFramesRemaining > 0) {
                scoreSyncRetryFramesRemaining--;
                return;
            }
        }
        try {
            scoreClient.push(currentScore);
            lastPushedScore = currentScore;
            if (scoreSyncUnreachable) {
                System.out.println("Scoring microservice reachable again - resuming score sync.");
                scoreSyncUnreachable = false;
            }
        } catch (RestClientException e) {
            if (!scoreSyncUnreachable) {
                System.err.println("Scoring microservice unreachable (" + e.getMessage()
                        + ") - will keep retrying quietly every " + (SCORE_SYNC_RETRY_FRAMES / 60) + "s.");
            }
            scoreSyncUnreachable = true;
            scoreSyncRetryFramesRemaining = SCORE_SYNC_RETRY_FRAMES;
        }
    }

    private void update() {
        for (IEntityProcessingService entityProcessorService : getEntityProcessingServices()) {
            entityProcessorService.process(gameData, world);
        }
        for (IPostEntityProcessingService postEntityProcessorService : getPostEntityProcessingServices()) {
            postEntityProcessorService.process(gameData, world);
        }
    }

    private void draw() {
        for (Entity polygonEntity : polygons.keySet()) {
            if (!world.getEntities().contains(polygonEntity)) {
                Polygon removedPolygon = polygons.get(polygonEntity);
                spawnExplosion(removedPolygon.getTranslateX(), removedPolygon.getTranslateY());
                if (polygonEntity.getCategory() == EntityCategory.ENEMY) {
                    SoundManager.playEnemyDeath();
                } else {
                    SoundManager.playExplosion();
                }
                polygons.remove(polygonEntity);
                gameWindow.getChildren().remove(removedPolygon);
                Rectangle[] removedBar = healthBars.remove(polygonEntity);
                if (removedBar != null) {
                    gameWindow.getChildren().removeAll(removedBar[0], removedBar[1]);
                }
            }
        }

        boolean playerVisibleThisFrame = true;

        for (Entity entity : world.getEntities()) {
            Polygon polygon = polygons.get(entity);
            if (polygon == null) {
                polygon = createPolygonFor(entity);
                polygons.put(entity, polygon);
                gameWindow.getChildren().add(polygon);
            }
            polygon.setTranslateX(entity.getX());
            polygon.setTranslateY(entity.getY());
            polygon.setRotate(entity.getRotation());

            if (entity.getCategory() == EntityCategory.PLAYER && gameData.getPlayerState().isInvulnerable()) {
                boolean blinkOn = (frameCount / 6) % 2 == 0;
                polygon.setVisible(blinkOn);
                playerVisibleThisFrame = blinkOn;
            } else {
                polygon.setVisible(true);
            }

            if (entity.getCategory() == EntityCategory.PLAYER) {
                updateFlame(entity, playerVisibleThisFrame);
                updateHealthBar(entity, gameData.getPlayerState().getLives() / (double) PlayerState.STARTING_LIVES,
                        playerVisibleThisFrame);
            } else if (entity.getCategory() == EntityCategory.ENEMY) {
                updateHealthBar(entity, entity.getHealth() / (double) entity.getMaxHealth(), true);
            }
        }

    }

    // Small bar drawn just above a Player/Enemy ship, filled proportionally
    // to remaining health (lives, for the player) and colored green/yellow/
    // red as it depletes - the visible "took damage" feedback the lab asks
    // for, on top of the numeric HUD "Lives" counter.
    private void updateHealthBar(Entity entity, double healthFraction, boolean visible) {
        Rectangle[] bar = healthBars.computeIfAbsent(entity, e -> {
            Rectangle background = new Rectangle(HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, Color.web("#333333"));
            Rectangle fill = new Rectangle(HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, Color.web("#33cc33"));
            gameWindow.getChildren().addAll(background, fill);
            return new Rectangle[]{background, fill};
        });

        double barX = entity.getX() - HEALTH_BAR_WIDTH / 2.0;
        double barY = entity.getY() - entity.getRadius() - 10;
        bar[0].setX(barX);
        bar[0].setY(barY);
        bar[1].setX(barX);
        bar[1].setY(barY);
        bar[1].setWidth(HEALTH_BAR_WIDTH * Math.max(0, healthFraction));
        bar[1].setFill(healthFraction > 0.66 ? Color.web("#33cc33")
                : healthFraction > 0.33 ? Color.web("#ffcc33")
                : Color.web("#ff4444"));
        bar[0].setVisible(visible);
        bar[1].setVisible(visible);
    }

    private void updateFlame(Entity player, boolean playerVisible) {
        flamePolygon.setTranslateX(player.getX());
        flamePolygon.setTranslateY(player.getY());
        flamePolygon.setRotate(player.getRotation());
        boolean playing = gameData.getGameStateManager().getState() == GameState.PLAYING;
        flamePolygon.setVisible(playerVisible && playing && gameData.getKeys().isDown(GameKeys.UP));
    }

    private void addStarField() {
        for (int i = 0; i < 100; i++) {
            double x = random.nextDouble() * gameData.getDisplayWidth();
            double y = random.nextDouble() * gameData.getDisplayHeight();
            double radius = 0.5 + random.nextDouble() * 1;
            Circle star = new Circle(x, y, radius, Color.WHITE);
            star.setOpacity(0.3 + random.nextDouble() * 0.7);
            gameWindow.getChildren().add(star);
        }
    }

    private Polygon createPolygonFor(Entity entity) {
        Polygon polygon = new Polygon(entity.getPolygonCoordinates());
        polygon.setFill(Color.TRANSPARENT);
        polygon.setStrokeWidth(2);
        polygon.setStroke(colorFor(entity.getCategory()));
        return polygon;
    }

    private Color colorFor(EntityCategory category) {
        if (category == null) {
            return Color.WHITE;
        }
        switch (category) {
            case PLAYER:
                return Color.WHITE;
            case ENEMY:
                return Color.web("#ff4444");
            case ASTEROID:
                return Color.web("#aaaaaa");
            case PLAYER_BULLET:
                return Color.web("#ffdd44");
            case ENEMY_BULLET:
                return Color.web("#ff8844");
            default:
                return Color.WHITE;
        }
    }

    private void spawnExplosion(double x, double y) {
        Circle burst = new Circle(x, y, 2, Color.TRANSPARENT);
        burst.setStroke(Color.web("#ffcc33"));
        burst.setStrokeWidth(2);
        gameWindow.getChildren().add(burst);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(burst.radiusProperty(), 2),
                        new KeyValue(burst.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(300),
                        new KeyValue(burst.radiusProperty(), 18),
                        new KeyValue(burst.opacityProperty(), 0))
        );
        timeline.setOnFinished(event -> gameWindow.getChildren().remove(burst));
        timeline.play();
    }

    private void shakeScreen() {
        Timeline timeline = new Timeline();
        int shakes = 6;
        for (int i = 0; i < shakes; i++) {
            double dx = (random.nextDouble() - 0.5) * 16;
            double dy = (random.nextDouble() - 0.5) * 16;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(i * 30),
                    new KeyValue(gameWindow.translateXProperty(), dx),
                    new KeyValue(gameWindow.translateYProperty(), dy)));
        }
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(shakes * 30),
                new KeyValue(gameWindow.translateXProperty(), 0),
                new KeyValue(gameWindow.translateYProperty(), 0)));
        timeline.play();
    }

    public List<IGamePluginService> getGamePluginServices() {
        return componentRegistry.getActivePlugins();
    }

    public List<IEntityProcessingService> getEntityProcessingServices() {
        return componentRegistry.getActiveProcessors();
    }

    public List<IPostEntityProcessingService> getPostEntityProcessingServices() {
        return componentRegistry.getActivePostProcessors();
    }

}
