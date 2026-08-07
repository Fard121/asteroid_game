package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.data.GameData;
import dk.sdu.mmmi.cbse.common.data.GameKeys;
import dk.sdu.mmmi.cbse.common.data.GameState;
import dk.sdu.mmmi.cbse.common.data.World;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Renders the on-screen HUD text from shared, already-published game state
 * (GameData/World) - it does not compute or own any gameplay logic itself.
 *
 * Two elements are driven: {@code text} anchored top-left (regular stats,
 * and the detailed control list when Help is toggled on), and
 * {@code centerTextFlow} anchored in the middle of the screen (the
 * Start/Pause menu options, navigable with Up/Down - see
 * Game.menuSelectedIndex), with the currently selected option rendered in
 * a highlight color.
 */
class HUDRenderer {

    private static final String[] START_MENU_OPTIONS = {"Start", "Help", "Quit"};
    private static final String[] PAUSE_MENU_OPTIONS = {"Resume", "Help", "Quit"};

    private static final String CONTROLS_HELP =
            "Arrow keys - Move\nSPACE - Shoot\nP - Pause\nM - Mute\nR - Restart (after Game Over/Victory)";

    private static final Font MENU_FONT = Font.font("Arial", FontWeight.BOLD, 28);
    private static final Color MENU_COLOR = Color.WHITE;
    private static final Color HIGHLIGHT_COLOR = Color.web("#ffdd44");

    private final Text text;
    private final TextFlow centerTextFlow;
    private final int displayHeight;
    private boolean helpVisible = false;

    HUDRenderer(Text text, TextFlow centerTextFlow, int displayHeight) {
        this.text = text;
        this.centerTextFlow = centerTextFlow;
        this.displayHeight = displayHeight;
    }

    void toggleHelp() {
        helpVisible = !helpVisible;
    }

    void update(GameData gameData, World world, int menuSelectedIndex) {
        GameState state = gameData.getGameStateManager().getState();
        boolean menuOpen = state == GameState.START_MENU || state == GameState.PAUSED;

        if (menuOpen && gameData.getKeys().isPressed(GameKeys.HELP)) {
            toggleHelp();
        }
        if (!menuOpen) {
            // Help is a menu-only overlay - never let it linger into actual gameplay.
            helpVisible = false;
        }

        if (state == GameState.START_MENU) {
            showCenterMenu("ASTEROIDS", START_MENU_OPTIONS, menuSelectedIndex);
            text.setText(helpVisible ? CONTROLS_HELP : "Press H for help");
            return;
        }

        if (state == GameState.PAUSED) {
            showCenterMenu("PAUSED", PAUSE_MENU_OPTIONS, menuSelectedIndex);
        } else {
            hideCenterMenu();
        }

        StringBuilder hud = new StringBuilder();
        hud.append("Score: ").append(gameData.getScoreState().getScore());
        hud.append("   Lives: ").append(gameData.getPlayerState().getLives());
        hud.append("   Wave: ").append(gameData.getWaveState().getWaveNumber());
        hud.append("   Asteroids remaining: ").append(gameData.getAsteroidsRemaining());

        if (gameData.getPlayerState().isGameOver()) {
            hud.append("\n\nGAME OVER - press R to restart");
        } else if (state == GameState.VICTORY) {
            hud.append("\n\nYOU WIN - press R to restart");
        }

        if (state == GameState.PAUSED && helpVisible) {
            hud.append("\n\n").append(CONTROLS_HELP);
        }

        text.setText(hud.toString());
    }

    private void showCenterMenu(String title, String[] options, int selectedIndex) {
        centerTextFlow.getChildren().clear();

        Text titleText = new Text(title + "\n\n");
        titleText.setFont(MENU_FONT);
        titleText.setFill(MENU_COLOR);
        centerTextFlow.getChildren().add(titleText);

        for (int i = 0; i < options.length; i++) {
            boolean selected = i == selectedIndex;
            String line = (selected ? "> " : "   ") + options[i] + (i < options.length - 1 ? "\n" : "");
            Text optionText = new Text(line);
            optionText.setFont(MENU_FONT);
            optionText.setFill(selected ? HIGHLIGHT_COLOR : MENU_COLOR);
            centerTextFlow.getChildren().add(optionText);
        }

        double flowHeight = centerTextFlow.getBoundsInLocal().getHeight();
        centerTextFlow.setLayoutX(0);
        centerTextFlow.setLayoutY((displayHeight - flowHeight) / 2.0);
    }

    private void hideCenterMenu() {
        centerTextFlow.getChildren().clear();
    }
}
