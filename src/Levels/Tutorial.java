package Levels;

import Game.GameLoop;
import Game.LivesSystem;
import Game.Player;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Tutorial {
    private static final double WINDOW_X = 1280;
    private static final double WINDOW_Y = 720;

    private final Set<KeyCode>   pressedKeys = new HashSet<>();
    private Scene                scene;
    private final AnchorPane     root;
    private final Player         mainChar;
    private final LivesSystem    livesSystem;
    private final List<String>   tutorialMessages;
    private int                  currentMessageIndex;
    private ImageView            heartRender;
    private final Stage          primaryStage;
    private final Runnable       onComplete;
    private GameLoop             gameLoop;     // smooth 60fps movement — same as main game
    public  Text                 scoreText;

    public Tutorial(Stage primaryStage, Runnable onComplete) {
        this.root             = new AnchorPane();
        this.mainChar         = new Player();
        this.livesSystem      = new LivesSystem();
        this.tutorialMessages = new ArrayList<>();
        this.currentMessageIndex = 0;
        this.heartRender      = livesSystem.heartsRender(mainChar.getHearts());
        this.primaryStage     = primaryStage;
        this.onComplete       = onComplete;
        createTutorialMessages(tutorialMessages);

        // Score display — styled to match the game HUD
        scoreText = new Text("Score: 0");
        scoreText.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        scoreText.setFill(Color.WHITE);
        scoreText.setLayoutX(WINDOW_X - 140);
        scoreText.setLayoutY(30);
    }

    public Scene startTutorial() {
        Image bg = new Image("file:img/waterfall.gif", WINDOW_X, WINDOW_Y, false, false);
        root.getChildren().add(mainChar.getCharacter());
        root.setBackground(new Background(new BackgroundImage(
                bg,
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));

        scene = new Scene(root, WINDOW_X, WINDOW_Y);

        // Key tracking — the GameLoop reads pressedKeys every frame for smooth movement
        scene.setOnKeyPressed(e  -> pressedKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));

        // Start the same GameLoop used in the actual game → identical feel
        gameLoop = new GameLoop(mainChar, pressedKeys, null);
        gameLoop.start();

        showTutorialText(scene);
        return scene;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tutorial message display
    // ─────────────────────────────────────────────────────────────────────────
    private void showTutorialText(Scene scene) {
        if (currentMessageIndex >= tutorialMessages.size()) return;

        // Determine which chibi portrait to use based on the message
        String portraitPath;
        switch (currentMessageIndex) {
            case 1:
            case 7:
            case 9:
                portraitPath = "file:img/chibi_celebrate.png";
                break;
            case 3:
            case 4:
            case 8:
                portraitPath = "file:img/chibi_flustered.png";
                break;
            case 0:
                portraitPath = "file:img/chibi_normal.png";
                break;
            default:
                portraitPath = "file:img/chibi_talking.png";
                break;
        }

        // 1. Draw the dialogue bubble FIRST so it stays in the background
        Rectangle bubble = new Rectangle(1200, 160);
        bubble.setFill(Color.color(0, 0, 0, 0.85)); // darker for better contrast
        bubble.setArcWidth(12); bubble.setArcHeight(12);
        AnchorPane.setBottomAnchor(bubble, 20.0);
        AnchorPane.setLeftAnchor(bubble, (WINDOW_X - 1200) / 2);

        // 2. Draw the portrait SECOND so it layers ON TOP of the bubble
        ImageView portrait = new ImageView(new Image(portraitPath));
        portrait.setFitWidth(400); // adjusted width to fit perfectly
        portrait.setPreserveRatio(true);
        AnchorPane.setBottomAnchor(portrait, 0.0);
        AnchorPane.setLeftAnchor(portrait, 20.0); // starts at 60, ends around 460

        // Load the custom font from the img directory
        Font customFont = Font.loadFont("file:img/Salmon Typewriter 9 Regular.ttf", 20);
        if (customFont == null) customFont = Font.font("Arial", FontWeight.BOLD, 20);

        Font smallCustomFont = Font.loadFont("file:img/Salmon Typewriter 9 Regular.ttf", 16);
        if (smallCustomFont == null) smallCustomFont = Font.font("Arial", 16);

        // 3. Draw the text right of the portrait
        Text msgText = new Text(tutorialMessages.get(currentMessageIndex));
        msgText.setFont(customFont);
        msgText.setFill(Color.WHITE);
        msgText.setTextAlignment(TextAlignment.LEFT);
        msgText.setWrappingWidth(800); 
        // Bubble top is at 720 - 20 (bottom anchor) - 160 (height) = 540
        AnchorPane.setTopAnchor(msgText, 575.0); // 35px padding from top of bubble
        AnchorPane.setLeftAnchor(msgText, 360.0); // reduced gap so it sits closer to the chibi

        Text hint = new Text("[ Click anywhere to continue ]");
        hint.setFont(smallCustomFont);
        hint.setFill(Color.color(0.8, 0.8, 0.8, 1));
        AnchorPane.setTopAnchor(hint, 665.0); // tucked in the bottom right corner of bubble
        AnchorPane.setRightAnchor(hint, 60.0);

        Text progress = new Text((currentMessageIndex + 1) + " / " + tutorialMessages.size());
        progress.setFont(smallCustomFont);
        progress.setFill(Color.LIGHTGRAY);
        AnchorPane.setTopAnchor(progress, 555.0); // tucked in the top right corner of bubble
        AnchorPane.setRightAnchor(progress, 60.0);

        // Add in order: bubble (bottom), portrait, texts (top)
        root.getChildren().addAll(bubble, portrait, msgText, hint, progress);

        scene.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() != MouseButton.PRIMARY) return;

            root.getChildren().removeAll(bubble, portrait, msgText, hint, progress);
            currentMessageIndex++;

            // Gradually reveal UI elements as tutorial progresses
            if (currentMessageIndex == 3) root.getChildren().add(scoreText);
            if (currentMessageIndex == 4) root.getChildren().add(heartRender);
            if (currentMessageIndex == 8) {
                Game.ComboSystem comboSystem = new Game.ComboSystem(root);
                Game.EffectsManager effects = new Game.EffectsManager(root);
                mainChar.setEffectsManager(effects); // Enable Dash Trails
                Runnable handleDeath = () -> {
                    if (onComplete != null) onComplete.run();
                };
                Game.Dropper dropper = new Game.Dropper(0, 0, root, heartRender, primaryStage, scoreText, comboSystem, effects, handleDeath);
                dropper.startDroppingMechanism(root, mainChar);
            }

            if (currentMessageIndex >= tutorialMessages.size()) {
                // Tutorial done — stop the loop and hand off to the game
                gameLoop.stop();
                if (onComplete != null) onComplete.run();
                return;
            }

            showTutorialText(scene);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Messages
    // ─────────────────────────────────────────────────────────────────────────
    private void createTutorialMessages(List<String> msgs) {
        msgs.add("Greetings! Click anywhere to start the tutorial.");
        msgs.add("Welcome to Catch the Proots!");
        msgs.add("You were tasked by your boss to catch certain kinds of fruits.");
        msgs.add("Unfortunately, there is a quota given to you, so you must take it seriously!");
        msgs.add("You are only given three lives so make it worth your time!");
        msgs.add("To move, try pressing the LEFT and RIGHT arrow keys.");
        msgs.add("To dash, double-tap the LEFT or RIGHT arrow keys.");
        msgs.add("Now you have learned the basics, try catching some of these fruits!");
        msgs.add("Each fruit has different points. Avoid spoiled ones — they cost a life!\n" +
                 "Missing a good fruit deducts its points from your score.");
        msgs.add("That is all for the tutorial. Wish you the best of luck!");
    }
}
