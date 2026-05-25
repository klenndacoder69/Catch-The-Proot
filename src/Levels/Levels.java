package Levels;

import Game.MainGame;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.function.IntConsumer;

/*
 * Base class for all game levels.
 *
 * Level 1: difficulty 1, minScore 2500 — basic fruits, no trash, slow drops
 * Level 2: difficulty 2, minScore 5000 — trash introduced, medium speed
 * Level 3: difficulty 3, minScore 7500 — heavy trash, fast drops
 */
public class Levels {
    private final double minScore;
    private final int    difficultyLevel;

    public Levels(int difficultyLevel, double minScore) {
        this.minScore       = minScore;
        this.difficultyLevel = difficultyLevel;
    }

    /**
     * Builds the game scene for this level.
     *
     * @param primaryStage   the main window
     * @param onComplete     receives the player's final score when they pass
     * @param onFailed       called when the player fails (score below minimum)
     */
    public Scene startLevel(Stage primaryStage, IntConsumer onComplete, Runnable onFailed) {
        MainGame level = new MainGame(primaryStage);
        return level.initializeGame(difficultyLevel, minScore, onComplete, onFailed);
    }

    public double getMinScore()      { return minScore; }
    public int    getDifficultyLevel(){ return difficultyLevel; }
}
