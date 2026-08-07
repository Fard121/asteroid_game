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
import dk.sdu.mmmi.cbse.common.data.World;
import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;
import dk.sdu.mmmi.cbse.common.services.IPostEntityProcessingService;
import dk.sdu.mmmi.cbse.common.sound.SoundManager;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
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
    private final List<IGamePluginService> gamePluginServices;
    private final List<IEntityProcessingService> entityProcessingServiceList;
    private final List<IPostEntityProcessingService> postEntityProcessingServices;
    private final ScoreClient scoreClient;
    private int lastPushedScore = -1;

    Game(List<IGamePluginService> gamePluginServices, List<IEntityProcessingService> entityProcessingServiceList, List<IPostEntityProcessingService> postEntityProcessingServices, ScoreClient scoreClient) {
        this.gamePluginServices = gamePluginServices;
        this.entityProcessingServiceList = entityProcessingServiceList;
        this.postEntityProcessingServices = postEntityProcessingServices;
        this.scoreClient = scoreClient;
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
    }

    public void render() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                frameCount++;

                if (gameData.getKeys().isPressed(GameKeys.MUTE)) {
                    SoundManager.toggleMute();
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
        return gamePluginServices;
    }

    public List<IEntityProcessingService> getEntityProcessingServices() {
        return entityProcessingServiceList;
    }

    public List<IPostEntityProcessingService> getPostEntityProcessingServices() {
        return postEntityProcessingServices;
    }

}
