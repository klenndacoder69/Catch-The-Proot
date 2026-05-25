package Game;

import Levels.*;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import java.util.Random;

import java.util.List;

/**
 * Orchestrates the full game flow:
 *   Main Menu → Tutorial → Level 1 → Level 2 → Level 3 → Win Screen
 *
 * Also handles:
 * - About / Leaderboard overlays on the main menu
 * - Total score accumulation across levels
 * - Score entry and persistence via ScoreManager
 */
public class ViewGame {
    private Stage primaryStage;
    private int   currentLevelIndex = 0;
    private int   totalScore        = 0;
    private boolean tutorialDone    = false;

    private final Levels[] levels = { new level1(), new level2(), new level3() };

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────
    public void setStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Catch the Proot");
        primaryStage.setScene(buildMainMenu());
        primaryStage.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main menu
    // ─────────────────────────────────────────────────────────────────────────
    public Scene buildMainMenu() {
        AnchorPane root = new AnchorPane();

        // Background
        Image bgImg   = new Image("file:img/waterfall.gif", 1280, 720, false, false);
        Image logoImg = new Image("file:img/logo.png", 470, 394, false, false);
        ImageView logo = new ImageView(logoImg);

        root.setBackground(new Background(new BackgroundImage(
                bgImg, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));

        // Animated logo
        TranslateTransition bounce = new TranslateTransition(Duration.seconds(1.1), logo);
        bounce.setFromY(0); bounce.setToY(-12);
        bounce.setCycleCount(TranslateTransition.INDEFINITE);
        bounce.setAutoReverse(true);
        bounce.play();
        TranslateTransition sway = new TranslateTransition(Duration.seconds(1.6), logo);
        sway.setFromX(0); sway.setToX(10);
        sway.setCycleCount(TranslateTransition.INDEFINITE);
        sway.setAutoReverse(true);
        sway.play();

        // Buttons
        StackPane playBtn = makeButton(
                "file:img/Buttons Pixel Animation Pack/play/343px/play01.png",
                "file:img/Buttons Pixel Animation Pack/play/343px/play04.png",
                0.25, () -> startNewGame());
        StackPane aboutBtn = makeButton(
                "file:img/Buttons Pixel Animation Pack/about/343px/about01.png",
                "file:img/Buttons Pixel Animation Pack/about/343px/about04.png",
                0.25, () -> showAbout(root));
        StackPane lbBtn = makeButton(
                "file:img/Buttons Pixel Animation Pack/leaderboard/343px/leaderboard01.png",
                "file:img/Buttons Pixel Animation Pack/leaderboard/343px/leaderboard04.png",
                0.25, () -> showLeaderboard(root));

        VBox menu = new VBox(10, playBtn, aboutBtn, lbBtn);
        menu.setAlignment(Pos.BOTTOM_CENTER);

        // Continuous throb for the PLAY button!
        ScaleTransition playThrob = new ScaleTransition(Duration.millis(600), playBtn);
        playThrob.setFromX(1.0); playThrob.setFromY(1.0);
        playThrob.setToX(1.08);  playThrob.setToY(1.08);
        playThrob.setAutoReverse(true);
        playThrob.setCycleCount(Animation.INDEFINITE);
        playThrob.play();

        // ── Falling Fruit Background Showcase ──
        Fruit[] fruits = FruitFactory.getLevel1Fruits();
        Random rng = new Random();
        
        Timeline spawner = new Timeline(new KeyFrame(Duration.millis(400), e -> {
            Fruit f = fruits[rng.nextInt(fruits.length)];
            ImageView fv = new ImageView(f.getImageView().getImage());
            fv.setFitWidth(45); fv.setFitHeight(45);
            fv.setPreserveRatio(true);
            fv.setLayoutX(rng.nextDouble() * 1280);
            fv.setLayoutY(-50);
            
            // Push fruits BEHIND the UI (index 1 is behind logo and menu, in front of background)
            root.getChildren().add(1, fv);
            
            TranslateTransition fall = new TranslateTransition(Duration.seconds(3 + rng.nextDouble() * 2), fv);
            fall.setToY(800);
            fall.setOnFinished(ev -> root.getChildren().remove(fv));
            fall.play();
            
            RotateTransition rot = new RotateTransition(Duration.seconds(1), fv);
            rot.setByAngle(360);
            rot.setCycleCount(Animation.INDEFINITE);
            rot.play();
        }));
        spawner.setCycleCount(Animation.INDEFINITE);
        spawner.play();

        // ── Idle Player Character ──
        Player mainChar = new Player();
        mainChar.getCharacter().setLayoutX(100);
        mainChar.getCharacter().setLayoutY(450); // Standing on a platform!
        root.getChildren().add(mainChar.getCharacter());

        root.getChildren().addAll(menu, logo);
        AnchorPane.setBottomAnchor(menu, 50.0);
        AnchorPane.setLeftAnchor(menu, 0.0);
        AnchorPane.setRightAnchor(menu, 0.0);
        AnchorPane.setTopAnchor(logo, 0.0);
        AnchorPane.setBottomAnchor(logo, 0.0);
        AnchorPane.setLeftAnchor(logo, 375.0);

        // Window setup
        Image icon = new Image("file:img/icon.png");
        if (!primaryStage.getIcons().contains(icon)) primaryStage.getIcons().add(icon);
        primaryStage.setResizable(false);

        Scene scene = new Scene(root, 1280, 720);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), root);
        fadeIn.setFromValue(0); fadeIn.setToValue(1); fadeIn.play();
        return scene;
    }

    /** Legacy alias kept so MainGame.switchToMainMenu() still works. */
    public Scene initializeGame() { return buildMainMenu(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Game flow
    // ─────────────────────────────────────────────────────────────────────────
    private void startNewGame() {
        totalScore        = 0;
        currentLevelIndex = 0;

        if (!tutorialDone) {
            Tutorial t = new Tutorial(primaryStage, () -> {
                tutorialDone = true;
                startLevel(0);
            });
            primaryStage.setScene(t.startTutorial());
        } else {
            startLevel(0);
        }
    }

    private void startLevel(int index) {
        currentLevelIndex = index;

        // Animated slide-in transition
        Scene levelScene = levels[index].startLevel(
            primaryStage,
            score -> { totalScore += score; onLevelComplete(index); },
            ()    -> onLevelFailed(index)
        );

        primaryStage.setScene(levelScene);
    }

    private void onLevelComplete(int index) {
        if (index >= levels.length - 1) {
            showWinScreen(totalScore);
        } else {
            startLevel(index + 1);
        }
    }

    private void onLevelFailed(int index) {
        startLevel(index); // replay same level
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Win screen
    // ─────────────────────────────────────────────────────────────────────────
    private void showWinScreen(int finalScore) {
        AnchorPane root = new AnchorPane();
        Image bg = new Image("file:img/waterfall.gif", 1280, 720, false, false);
        root.setBackground(new Background(new BackgroundImage(
                bg, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));

        Rectangle dark = new Rectangle(1280, 720);
        dark.setFill(Color.color(0, 0, 0, 0.65));
        root.getChildren().add(dark);

        centredText(root, "\uD83C\uDFC6", 80, FontWeight.NORMAL, Color.WHITE, 100.0);
        centredText(root, "You Win!", 68, FontWeight.EXTRA_BOLD, Color.GOLD, 200.0);
        centredText(root, "Total Score: " + finalScore, 26, FontWeight.BOLD, Color.WHITE, 295.0);
        centredText(root, "You beat all 3 levels — amazing!", 20, FontWeight.NORMAL, Color.LIGHTGRAY, 335.0);

        // Name entry
        TextField nameField = new TextField();
        nameField.setPromptText("Enter your initials");
        nameField.setMaxWidth(220);
        nameField.setStyle("-fx-font-size: 18px; -fx-background-color: rgba(255,255,255,0.15);" +
                           "-fx-text-fill: white; -fx-prompt-text-fill: gray;");
        AnchorPane.setTopAnchor(nameField, 380.0);
        AnchorPane.setLeftAnchor(nameField, 1280.0 / 2 - 110);
        root.getChildren().add(nameField);

        Text saveBtn = centredText(root, "Save Score & Return to Menu",
                26, FontWeight.BOLD, Color.WHITE, 445.0);
        saveBtn.setStyle("-fx-cursor: hand;");
        saveBtn.setOnMouseEntered(e -> saveBtn.setFill(Color.GOLD));
        saveBtn.setOnMouseExited(e  -> saveBtn.setFill(Color.WHITE));
        saveBtn.setOnMouseClicked(e -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) ScoreManager.saveScore(name, finalScore);
            primaryStage.setScene(buildMainMenu());
        });

        FadeTransition fadeIn = new FadeTransition(Duration.seconds(1.0), root);
        fadeIn.setFromValue(0); fadeIn.setToValue(1); fadeIn.play();

        primaryStage.setScene(new Scene(root, 1280, 720));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // About overlay
    // ─────────────────────────────────────────────────────────────────────────
    private void showAbout(AnchorPane root) {
        // Add the dark overlay FIRST (behind the text nodes)
        Rectangle overlay = new Rectangle(1280, 720);
        overlay.setFill(Color.color(0, 0, 0, 0.78));
        root.getChildren().add(overlay);

        // centredText() adds each Text to root internally — do NOT addAll() again
        Text title = centredText(root, "About  Catch the Proot",
                34, FontWeight.BOLD, Color.WHITE, 160.0);
        Text body = centredText(root,
                "\u2190 \u2192  Move        Z + \u2190 / \u2192  Dash\n\n" +
                "Catch fruits to earn points. Avoid trash \u2014 it costs a life!\n" +
                "Miss a good fruit and you lose those points too.\n\n" +
                "\u26a1 Speed Boost   \uD83E\uDDF2 Magnet (wider catch area)   \u2764 Extra Life\n\n" +
                "Beat all 3 levels to win. Good luck!",
                18, FontWeight.NORMAL, Color.LIGHTGRAY, 240.0);
        body.setWrappingWidth(860);

        Text closeBtn = centredText(root, "Close", 24, FontWeight.BOLD, Color.WHITE, 530.0);
        closeBtn.setStyle("-fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setFill(Color.TOMATO));
        closeBtn.setOnMouseExited(e  -> closeBtn.setFill(Color.WHITE));
        closeBtn.setOnMouseClicked(e ->
            root.getChildren().removeAll(overlay, title, body, closeBtn));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Leaderboard overlay
    // ─────────────────────────────────────────────────────────────────────────
    private void showLeaderboard(AnchorPane root) {
        // Add the dark overlay FIRST
        Rectangle overlay = new Rectangle(1280, 720);
        overlay.setFill(Color.color(0, 0, 0, 0.78));
        root.getChildren().add(overlay);

        Text title = centredText(root, "\uD83C\uDFC6  Leaderboard",
                38, FontWeight.BOLD, Color.GOLD, 130.0);

        List<String[]> scores = ScoreManager.loadScores();
        StringBuilder sb = new StringBuilder();
        if (scores.isEmpty()) {
            sb.append("No scores yet \u2014 complete a run to set a record!");
        } else {
            String[] medals = {"\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49", "4.", "5."};
            for (int i = 0; i < scores.size(); i++) {
                String[] e = scores.get(i);
                sb.append(String.format("%-4s  %-12s  %s\n", medals[i], e[0], e[1]));
            }
        }

        Text board = centredText(root, sb.toString().trim(),
                24, FontWeight.NORMAL, Color.WHITE, 220.0);
        board.setLineSpacing(8);

        Text closeBtn = centredText(root, "Close", 24, FontWeight.BOLD, Color.WHITE, 560.0);
        closeBtn.setStyle("-fx-cursor: hand;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setFill(Color.GOLD));
        closeBtn.setOnMouseExited(e  -> closeBtn.setFill(Color.WHITE));
        closeBtn.setOnMouseClicked(e ->
            root.getChildren().removeAll(overlay, title, board, closeBtn));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────
    private Text centredText(AnchorPane root, String content, double size,
                             FontWeight weight, Color fill, double topAnchor) {
        Text t = new Text(content);
        t.setFont(Font.font("Arial", weight, size));
        t.setFill(fill);
        // wrappingWidth=1280 gives the Text a full-width bounding box so that
        // TextAlignment.CENTER actually centres the text horizontally on screen.
        t.setWrappingWidth(1280);
        t.setTextAlignment(TextAlignment.CENTER);
        t.setMouseTransparent(false);
        AnchorPane.setTopAnchor(t, topAnchor);
        AnchorPane.setLeftAnchor(t, 0.0);
        if (root != null) root.getChildren().add(t);
        return t;
    }

    private StackPane makeButton(String normalPath, String hoverPath,
                                 double scale, Runnable action) {
        StackPane pane = new StackPane();
        Image normal = new Image(normalPath);
        Image hover  = new Image(hoverPath);
        ImageView nv = new ImageView(normal);
        ImageView hv = new ImageView(hover);

        nv.setFitWidth(normal.getWidth() * scale);
        nv.setFitHeight(normal.getHeight() * scale);
        hv.setFitWidth(hover.getWidth() * scale);
        hv.setFitHeight(hover.getHeight() * scale);
        hv.setVisible(false);

        pane.getChildren().addAll(nv, hv);
        
        DropShadow glow = new DropShadow(15, Color.GOLD);
        glow.setSpread(0.4);
        
        pane.setOnMouseEntered(e -> { 
            hv.setVisible(true); nv.setVisible(false);
            pane.setScaleX(1.1); pane.setScaleY(1.1);
            pane.setEffect(glow);
        });
        pane.setOnMouseExited(e  -> { 
            hv.setVisible(false); nv.setVisible(true);
            pane.setScaleX(1.0); pane.setScaleY(1.0);
            pane.setEffect(null);
        });
        pane.setOnMouseClicked(e -> action.run());
        return pane;
    }
}
