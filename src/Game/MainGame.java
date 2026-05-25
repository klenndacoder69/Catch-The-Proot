package Game;

import javafx.animation.*;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

public class MainGame {
    static double windowXSize = 1280;
    private static final double windowYSize = 720;

    private final Stage       primaryStage;
    private AnchorPane        root;
    private Scene             scene;
    private Player            mainChar;
    private LivesSystem       livesSystem;
    private Dropper           dropper;
    private GameLoop          gameLoop;
    private GameTimer         gameTimer;
    private ComboSystem       comboSystem;
    private EffectsManager    effects;

    private final Set<KeyCode> pressedKeys = new HashSet<>();

    // HUD text refs
    private Text scoreText;
    private Text timerText;
    private Text powerUpText;

    private boolean isPaused = false;
    private ScaleTransition timerPulse; // pulsing animation for low-time urgency

    // ── Double-tap dash detection ────────────────────────────────────────────
    private long lastLeftPressMs  = 0;
    private long lastRightPressMs = 0;
    private static final long DOUBLE_TAP_MS = 250; // window to detect double-tap

    // Level-tint colours per difficulty
    private static final Color[] LEVEL_TINTS = {
        Color.color(0.0, 0.55, 0.1,  0.13),  // L1 — green
        Color.color(1.0, 0.60, 0.0,  0.17),  // L2 — amber
        Color.color(0.42, 0.0, 0.65, 0.22)   // L3 — purple
    };

    public MainGame(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.root         = new AnchorPane();
        this.mainChar     = new Player();
        this.livesSystem  = new LivesSystem();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene builder
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * @param difficultyLevel  1-3, controls fruit mix and speed
     * @param minScore         score required to pass this level
     * @param onLevelComplete  called with the final score when player passes
     * @param onLevelFailed    called when player runs out of time with low score
     */
    public Scene initializeGame(int difficultyLevel, double minScore,
                                IntConsumer onLevelComplete, Runnable onLevelFailed) {
        // ── Background ──────────────────────────────────────────────────────
        Image bg = new Image("file:img/waterfall.gif", windowXSize, windowYSize, false, false);
        root.setBackground(new Background(new BackgroundImage(
                bg, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));

        // Level colour tint
        int tintIdx = Math.min(difficultyLevel - 1, LEVEL_TINTS.length - 1);
        Rectangle tint = new Rectangle(windowXSize, windowYSize);
        tint.setFill(LEVEL_TINTS[tintIdx]);
        tint.setMouseTransparent(true);
        root.getChildren().add(tint);

        // ── Ground visual (semi-transparent gradient strip) ─────────────────
        Rectangle ground = new Rectangle(windowXSize, 40);
        ground.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0, 0, 0, 0)),
                new Stop(0.4, Color.color(0, 0, 0, 0.20)),
                new Stop(1, Color.color(0, 0, 0, 0.45))));
        ground.setMouseTransparent(true);
        AnchorPane.setBottomAnchor(ground, 0.0);
        root.getChildren().add(ground);

        // ── Player ────────────────────────────────────────────────────────────
        root.getChildren().add(mainChar.getCharacter());

        // ── Systems ─────────────────────────────────────────────────────────
        effects     = new EffectsManager(root);
        mainChar.setEffectsManager(effects); // Enable Dash Trails
        effects.initChibi(); // Start the reactive chibi in the bottom right
        comboSystem = new ComboSystem(root);

        // ── Hearts display ──────────────────────────────────────────────────
        ImageView heartRender = livesSystem.heartsRender(mainChar.getHearts());
        root.getChildren().add(heartRender);

        // ── HUD panel (dark strip at top) ───────────────────────────────────
        buildHUD(difficultyLevel, minScore);

        // ── Dropper ─────────────────────────────────────────────────────────
        Runnable handleDeath = () -> {
            gameLoop.stop();
            gameTimer.stop();
            showLevelResult(false, (int) mainChar.getScore(), (int) minScore, onLevelComplete, onLevelFailed);
        };

        dropper = new Dropper(difficultyLevel, minScore, root,
                              heartRender, primaryStage,
                              scoreText, comboSystem, effects, handleDeath);
        dropper.startDroppingMechanism(root, mainChar);

        // ── GameTimer ───────────────────────────────────────────────────────
        gameTimer = new GameTimer(
            () -> {
                // Time up
                dropper.stopDropping();
                gameLoop.stop();
                int finalScore = (int) mainChar.getScore();
                if (finalScore >= (int) minScore) {
                    showLevelResult(true,  finalScore, (int) minScore, onLevelComplete, onLevelFailed);
                } else {
                    showLevelResult(false, finalScore, (int) minScore, onLevelComplete, onLevelFailed);
                }
            },
            remaining -> {
                timerText.setText(remaining + "s");
                if (remaining <= 10) {
                    timerText.setFill(Color.TOMATO);
                    // Start pulsing animation if not already active
                    if (timerPulse == null) {
                        timerPulse = new ScaleTransition(Duration.millis(500), timerText);
                        timerPulse.setFromX(1.0); timerPulse.setFromY(1.0);
                        timerPulse.setToX(1.15);  timerPulse.setToY(1.15);
                        timerPulse.setAutoReverse(true);
                        timerPulse.setCycleCount(ScaleTransition.INDEFINITE);
                        timerPulse.play();
                    }
                } else {
                    timerText.setFill(Color.WHITE);
                }
                updatePowerUpHUD();
            }
        );
        gameTimer.start();

        // ── GameLoop (smooth movement) ──────────────────────────────────────
        gameLoop = new GameLoop(mainChar, pressedKeys, this::updatePowerUpHUD);
        gameLoop.start();

        // ── Input ───────────────────────────────────────────────────────────
        scene = new Scene(root, windowXSize, windowYSize);
        scene.setOnKeyPressed(e -> {
            boolean isNewPress = !pressedKeys.contains(e.getCode()); // ignore key-repeat
            pressedKeys.add(e.getCode());
            if (e.getCode() == KeyCode.P || e.getCode() == KeyCode.ESCAPE) togglePause();

            // Double-tap → DASH
            if (isNewPress && !isPaused && mainChar != null) {
                long now = System.currentTimeMillis();
                if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.A) {
                    if (now - lastLeftPressMs < DOUBLE_TAP_MS) {
                        if (mainChar.triggerDash(false)) {
                            effects.dashTrail(mainChar.getCharXPosition(), mainChar.getCharYPosition(),
                                              mainChar.getWidthChar(), mainChar.getHeightChar(), false);
                        }
                    }
                    lastLeftPressMs = now;
                } else if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.D) {
                    if (now - lastRightPressMs < DOUBLE_TAP_MS) {
                        if (mainChar.triggerDash(true)) {
                            effects.dashTrail(mainChar.getCharXPosition(), mainChar.getCharYPosition(),
                                              mainChar.getWidthChar(), mainChar.getHeightChar(), true);
                        }
                    }
                    lastRightPressMs = now;
                }
            }
        });
        scene.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));

        // Fade in
        FadeTransition fadeIn = new FadeTransition(Duration.millis(500), root);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        fadeIn.play();

        return scene;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HUD
    // ─────────────────────────────────────────────────────────────────────────
    private void buildHUD(int difficultyLevel, double minScore) {
        // Dark strip background
        Rectangle hudBg = new Rectangle(windowXSize, 82);
        hudBg.setFill(Color.color(0, 0, 0, 0.55));
        hudBg.setMouseTransparent(true);
        AnchorPane.setTopAnchor(hudBg, 0.0);
        root.getChildren().add(hudBg);

        // Level label
        Text levelLbl = makeHudText("LEVEL  " + difficultyLevel, 20, FontWeight.BOLD);
        levelLbl.setFill(Color.LIGHTGRAY);
        AnchorPane.setTopAnchor(levelLbl, 14.0);
        AnchorPane.setLeftAnchor(levelLbl, 160.0);
        root.getChildren().add(levelLbl);

        // Goal label
        Text goalLbl = makeHudText("GOAL: " + (int) minScore, 14, FontWeight.NORMAL);
        goalLbl.setFill(Color.color(0.8, 0.8, 0.8, 1.0));
        AnchorPane.setTopAnchor(goalLbl, 42.0);
        AnchorPane.setLeftAnchor(goalLbl, 160.0);
        root.getChildren().add(goalLbl);

        // Timer (centre)
        timerText = makeHudText("60s", 36, FontWeight.EXTRA_BOLD);
        timerText.setFill(Color.WHITE);
        AnchorPane.setTopAnchor(timerText, 18.0);
        AnchorPane.setLeftAnchor(timerText, windowXSize / 2 - 28);
        root.getChildren().add(timerText);

        // Score (right)
        scoreText = makeHudText("Score: 0", 22, FontWeight.BOLD);
        scoreText.setFill(Color.WHITE);
        AnchorPane.setTopAnchor(scoreText, 14.0);
        AnchorPane.setRightAnchor(scoreText, 20.0);
        root.getChildren().add(scoreText);

        // Power-up status (below score)
        powerUpText = makeHudText("", 14, FontWeight.NORMAL);
        powerUpText.setFill(Color.CYAN);
        AnchorPane.setTopAnchor(powerUpText, 44.0);
        AnchorPane.setRightAnchor(powerUpText, 20.0);
        root.getChildren().add(powerUpText);

        // Pause + dash hint
        Text pauseHint = makeHudText("[P] Pause  ·  Double-tap ←→ to DASH", 12, FontWeight.NORMAL);
        pauseHint.setFill(Color.color(0.7, 0.7, 0.7, 1));
        AnchorPane.setTopAnchor(pauseHint, 56.0);
        AnchorPane.setLeftAnchor(pauseHint, windowXSize / 2 - 100);
        root.getChildren().add(pauseHint);
    }

    private Text makeHudText(String content, double size, FontWeight weight) {
        Text t = new Text(content);
        t.setFont(Font.font("Arial", weight, size));
        t.setMouseTransparent(true);
        // Subtle shadow so HUD text is readable on any background
        DropShadow ds = new DropShadow(4, Color.color(0, 0, 0, 0.65));
        ds.setOffsetY(1);
        t.setEffect(ds);
        return t;
    }

    private void updatePowerUpHUD() {
        if (mainChar == null || powerUpText == null) return;
        StringBuilder sb = new StringBuilder();
        if (mainChar.isSpeedBoostActive())
            sb.append("⚡ ").append(mainChar.getSpeedBoostRemainingMs()   / 1000 + 1).append("s  ");
        if (mainChar.isMagnetActive())
            sb.append("🧲 ").append(mainChar.getMagnetRemainingMs() / 1000 + 1).append("s  ");
        if (mainChar.isDoublePointsActive())
            sb.append("⭐ ×2 ").append(mainChar.getDoublePointsRemainingMs() / 1000 + 1).append("s  ");
        if (mainChar.isShieldActive())
            sb.append("🛡 Shield  ");
        if (!mainChar.isDashAvailable()) {
            long rem = mainChar.getDashCooldownRemainingMs();
            sb.append("💨 DASH ").append(String.format("%.1f", rem / 1000.0)).append("s");
        }
        powerUpText.setText(sb.toString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pause
    // ─────────────────────────────────────────────────────────────────────────
    private Rectangle pauseOverlay;
    private Text      pauseTitle;
    private Text      pauseResume;
    private Text      pauseMenu;

    private void togglePause() {
        isPaused = !isPaused;
        gameLoop.setPaused(isPaused);
        dropper.setPaused(isPaused);
        gameTimer.setPaused(isPaused);

        if (isPaused) showPauseOverlay();
        else          hidePauseOverlay();
    }

    private void showPauseOverlay() {
        pauseOverlay = new Rectangle(windowXSize, windowYSize);
        pauseOverlay.setFill(Color.color(0, 0, 0, 0.65));

        pauseTitle  = styledText("PAUSED", 64, FontWeight.EXTRA_BOLD, Color.WHITE,   250.0, null, null, 0.0);
        pauseResume = styledText("▶  Resume",  32, FontWeight.BOLD,       Color.WHITE,   350.0, null, null, 0.0);
        pauseMenu   = styledText("Main Menu",  22, FontWeight.NORMAL,     Color.LIGHTGRAY, 420.0, null, null, 0.0);

        pauseResume.setOnMouseClicked(e -> togglePause());
        pauseResume.setOnMouseEntered(e -> pauseResume.setFill(Color.LIMEGREEN));
        pauseResume.setOnMouseExited(e  -> pauseResume.setFill(Color.WHITE));

        pauseMenu.setOnMouseClicked(e -> switchToMainMenu());
        pauseMenu.setOnMouseEntered(e -> pauseMenu.setFill(Color.WHITE));
        pauseMenu.setOnMouseExited(e  -> pauseMenu.setFill(Color.LIGHTGRAY));

        root.getChildren().addAll(pauseOverlay, pauseTitle, pauseResume, pauseMenu);
    }

    private void hidePauseOverlay() {
        root.getChildren().removeAll(pauseOverlay, pauseTitle, pauseResume, pauseMenu);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Level result overlay
    // ─────────────────────────────────────────────────────────────────────────
    private void showLevelResult(boolean passed, int score, int goal,
                                 IntConsumer onComplete, Runnable onFailed) {
        Rectangle overlay = new Rectangle(windowXSize, windowYSize);
        overlay.setFill(Color.color(0, 0, 0, 0.85));
        root.getChildren().add(overlay);

        // Load custom font
        Font customFont = Font.loadFont("file:img/Salmon Typewriter 9 Regular.ttf", 58);
        if (customFont == null) customFont = Font.font("Arial", FontWeight.BOLD, 52);
        Font customFontSmall = Font.loadFont("file:img/Salmon Typewriter 9 Regular.ttf", 30);
        if (customFontSmall == null) customFontSmall = Font.font("Arial", FontWeight.BOLD, 26);

        // Result Container Panel
        Rectangle panel = new Rectangle(800, 480);
        panel.setFill(Color.color(0.12, 0.12, 0.12, 0.95));
        panel.setArcWidth(25); panel.setArcHeight(25);
        panel.setStroke(passed ? Color.LIMEGREEN : Color.TOMATO);
        panel.setStrokeWidth(5);
        AnchorPane.setTopAnchor(panel, (windowYSize - 480) / 2.0);
        AnchorPane.setLeftAnchor(panel, (windowXSize - 800) / 2.0);

        // Chibi reaction (standing on the left side of the panel)
        String chibiImg = passed ? "file:img/chibi_celebrate.png" : "file:img/chibi_flustered.png";
        javafx.scene.image.ImageView chibi = new javafx.scene.image.ImageView(new javafx.scene.image.Image(chibiImg));
        chibi.setFitWidth(320);
        chibi.setPreserveRatio(true);
        AnchorPane.setBottomAnchor(chibi, (windowYSize - 480) / 2.0); 
        AnchorPane.setLeftAnchor(chibi, (windowXSize - 800) / 2.0 - 160); // Hanging off the left side

        Color headCol = passed ? Color.LIMEGREEN : Color.TOMATO;
        String headline = passed ? "LEVEL COMPLETE!" : "LEVEL FAILED";

        Text title = new Text(headline);
        title.setFont(customFont);
        title.setFill(headCol);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setWrappingWidth(800); // match panel width
        AnchorPane.setTopAnchor(title, (windowYSize - 480) / 2.0 + 50);
        AnchorPane.setLeftAnchor(title, (windowXSize - 800) / 2.0); // match panel left

        Text info = new Text("Score: " + score + "   /   Goal: " + goal);
        info.setFont(customFontSmall);
        info.setFill(Color.WHITE);
        info.setTextAlignment(TextAlignment.CENTER);
        info.setWrappingWidth(800); // match panel width
        AnchorPane.setTopAnchor(info, (windowYSize - 480) / 2.0 + 150);
        AnchorPane.setLeftAnchor(info, (windowXSize - 800) / 2.0); // match panel left

        // Score entry field
        TextField nameField = new TextField();
        nameField.setPromptText("Enter initials (3 letters)");
        nameField.setMaxWidth(300);
        nameField.setStyle("-fx-font-size: 22px; -fx-background-color: rgba(255,255,255,0.15);" +
                           "-fx-text-fill: white; -fx-prompt-text-fill: lightgray; -fx-alignment: center;");
        AnchorPane.setTopAnchor(nameField, (windowYSize - 480) / 2.0 + 220);
        AnchorPane.setLeftAnchor(nameField, windowXSize / 2.0 - 150);

        String btnLabel = passed ? "CONTINUE \u2192" : "TRY AGAIN \u21BA";
        Text actionBtn = new Text(btnLabel);
        actionBtn.setFont(customFontSmall);
        actionBtn.setFill(Color.WHITE);
        actionBtn.setTextAlignment(TextAlignment.CENTER);
        actionBtn.setWrappingWidth(800); // match panel width
        AnchorPane.setTopAnchor(actionBtn, (windowYSize - 480) / 2.0 + 310);
        AnchorPane.setLeftAnchor(actionBtn, (windowXSize - 800) / 2.0); // match panel left
        actionBtn.setStyle("-fx-cursor: hand;");
        actionBtn.setOnMouseEntered(e -> actionBtn.setFill(headCol));
        actionBtn.setOnMouseExited(e  -> actionBtn.setFill(Color.WHITE));
        actionBtn.setOnMouseClicked(e -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) ScoreManager.saveScore(name, score);
            if (passed) onComplete.accept(score);
            else        onFailed.run();
        });

        Text menuBtn = new Text("Main Menu");
        menuBtn.setFont(Font.font("Arial", FontWeight.NORMAL, 22));
        menuBtn.setFill(Color.LIGHTGRAY);
        menuBtn.setTextAlignment(TextAlignment.CENTER);
        menuBtn.setWrappingWidth(800); // match panel width
        AnchorPane.setTopAnchor(menuBtn, (windowYSize - 480) / 2.0 + 390);
        AnchorPane.setLeftAnchor(menuBtn, (windowXSize - 800) / 2.0); // match panel left
        menuBtn.setStyle("-fx-cursor: hand;");
        menuBtn.setOnMouseEntered(e -> menuBtn.setFill(Color.WHITE));
        menuBtn.setOnMouseExited(e  -> menuBtn.setFill(Color.LIGHTGRAY));
        menuBtn.setOnMouseClicked(e -> switchToMainMenu());

        // Group everything in a pane for animation
        AnchorPane resultGroup = new AnchorPane();
        resultGroup.setPrefSize(windowXSize, windowYSize);
        resultGroup.getChildren().addAll(panel, chibi, title, info, nameField, actionBtn, menuBtn);
        root.getChildren().add(resultGroup);

        // Pop-in animation with overshoot for a juicy feel
        ScaleTransition st = new ScaleTransition(Duration.millis(400), resultGroup);
        st.setFromX(0.1); st.setFromY(0.1);
        st.setToX(1.0); st.setToY(1.0);
        // Custom mathematical overshoot interpolator
        st.setInterpolator(new Interpolator() {
            @Override
            protected double curve(double t) {
                double tension = 1.5;
                t -= 1.0;
                return t * t * ((tension + 1) * t + tension) + 1.0;
            }
        });
        st.play();

        // Juice: Heavy Title Slam!
        TranslateTransition titleDrop = new TranslateTransition(Duration.millis(500), title);
        titleDrop.setFromY(-600);
        titleDrop.setToY(0);
        titleDrop.setInterpolator(Interpolator.EASE_IN);
        titleDrop.setOnFinished(e -> effects.heavyShake());
        titleDrop.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — creates centred anchored text
    // ─────────────────────────────────────────────────────────────────────────
    private Text styledText(String content, double size, FontWeight weight,
                            Color fill, Double top, Double bottom, Double left, double rightOrCenter) {
        Text t = new Text(content);
        t.setFont(Font.font("Arial", weight, size));
        t.setFill(fill);
        t.setTextAlignment(TextAlignment.CENTER);
        if (top    != null) AnchorPane.setTopAnchor(t,    top);
        if (bottom != null) AnchorPane.setBottomAnchor(t, bottom);
        // Always centre horizontally
        AnchorPane.setLeftAnchor(t,  0.0);
        AnchorPane.setRightAnchor(t, 0.0);
        return t;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────
    public void switchToMainMenu() {
        if (gameLoop  != null) gameLoop.stop();
        if (gameTimer != null) gameTimer.stop();
        if (dropper   != null) dropper.stopDropping();
        ViewGame vg = new ViewGame();
        vg.setStage(primaryStage);
    }
}
