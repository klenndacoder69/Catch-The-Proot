package Game;

import javafx.animation.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;

/**
 * Tracks consecutive fruit catches and awards a score multiplier.
 * x1 (default) → x2 (3+ chain) → x3 (5+ chain) → x4 MAX (7+ chain)
 * Resets when the player misses a fruit or catches trash.
 */
public class ComboSystem {
    private int comboCount = 0;
    private long feverEndTime = 0;
    
    private double lastMult = 1.0;
    private ScaleTransition comboPulseAnim;
    
    private final AnchorPane root;
    private final Text comboLabel;
    private final Text feverLabel;

    public ComboSystem(AnchorPane root) {
        this.root = root;
        
        comboLabel = new Text();
        comboLabel.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 26));
        comboLabel.setTextAlignment(TextAlignment.CENTER);
        comboLabel.setMouseTransparent(true);
        AnchorPane.setTopAnchor(comboLabel, 108.0);
        AnchorPane.setLeftAnchor(comboLabel, 0.0);
        AnchorPane.setRightAnchor(comboLabel, 0.0);
        
        feverLabel = new Text();
        Font feverFont = Font.loadFont("file:img/Salmon Typewriter 9 Regular.ttf", 38);
        if (feverFont == null) feverFont = Font.font("Arial", FontWeight.BLACK, 32);
        feverLabel.setFont(feverFont);
        feverLabel.setOpacity(0.0);
        feverLabel.setMouseTransparent(true);
        root.getChildren().add(feverLabel);
        
        comboPulseAnim = new ScaleTransition(Duration.millis(350), comboLabel);
        comboPulseAnim.setFromX(1.0); comboPulseAnim.setFromY(1.0);
        comboPulseAnim.setToX(1.2);   comboPulseAnim.setToY(1.2);
        comboPulseAnim.setAutoReverse(true);
        comboPulseAnim.setCycleCount(Animation.INDEFINITE);
        
        feverLabel.setFill(Color.GOLD);
        feverLabel.setStroke(Color.ORANGERED);
        feverLabel.setStrokeWidth(3);
        AnchorPane.setTopAnchor(feverLabel, 200.0);
        AnchorPane.setLeftAnchor(feverLabel, 0.0);
        AnchorPane.setRightAnchor(feverLabel, 0.0);
        
        root.getChildren().addAll(comboLabel);
    }

    public boolean isFeverActive() {
        return System.currentTimeMillis() < feverEndTime;
    }

    /** Call when a good fruit is caught. Returns true if Fever just triggered. */
    public boolean recordCatch() {
        comboCount++;
        boolean feverJustTriggered = false;
        
        // Trigger Fever at exactly 12 combo
        if (comboCount == 12 && !isFeverActive()) {
            feverEndTime = System.currentTimeMillis() + 8000; // 8 seconds of chaos
            showFeverText();
            feverJustTriggered = true;
        }
        
        updateDisplay();
        return feverJustTriggered;
    }

    /** Call when a fruit is missed or trash is caught. */
    public void resetCombo() {
        if (isFeverActive()) {
            // Losing combo during fever doesn't end fever, but resetting count 
            // stops them from immediately re-triggering it.
            comboCount = 0; 
        } else {
            if (comboCount >= 3) {
                comboLabel.setText("COMBO LOST!");
                comboLabel.setFill(Color.ORANGERED);
                PauseTransition pt = new PauseTransition(Duration.millis(700));
                pt.setOnFinished(e -> comboLabel.setText(""));
                pt.play();
            } else {
                comboLabel.setText("");
            }
            comboCount = 0;
            lastMult = 1.0;
            comboPulseAnim.stop();
            comboLabel.setScaleX(1.0); comboLabel.setScaleY(1.0);
        }
    }

    /** Returns the current score multiplier based on combo count. */
    public double getMultiplier() {
        if (isFeverActive()) return 5.0;
        if (comboCount >= 7) return 4.0;
        if (comboCount >= 5) return 3.0;
        if (comboCount >= 3) return 2.0;
        return 1.0;
    }

    public int getComboCount() { return comboCount; }

    private void showFeverText() {
        feverLabel.setText("FEVER TIME!!! \n ×5 SCORE ");
        
        ScaleTransition st = new ScaleTransition(Duration.millis(400), feverLabel);
        st.setFromX(0.1); st.setFromY(0.1);
        st.setToX(1.0); st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);
        
        RotateTransition jiggle = new RotateTransition(Duration.millis(60), feverLabel);
        jiggle.setFromAngle(-8);
        jiggle.setToAngle(8);
        jiggle.setAutoReverse(true);
        jiggle.setCycleCount(32); // 32 * 60 = ~1.9s jiggle duration
        
        PauseTransition pt = new PauseTransition(Duration.seconds(2));
        
        FadeTransition ft = new FadeTransition(Duration.millis(500), feverLabel);
        ft.setFromValue(1.0); ft.setToValue(0.0);
        
        SequentialTransition seq = new SequentialTransition(st, pt, ft);
        seq.setOnFinished(e -> {
            feverLabel.setText("");
            feverLabel.setRotate(0);
        });
        feverLabel.setOpacity(1.0);
        seq.play();
        jiggle.setDelay(Duration.millis(400));
        jiggle.play();
    }

    private void updateDisplay() {
        double mult = getMultiplier();
        if (mult <= 1.0 && !isFeverActive()) { comboLabel.setText(""); return; }

        String txt;
        Color col;
        
        if (isFeverActive()) {
            txt = "\uD83D\uDD25  FEVER MULTIPLIER  ×5!  \uD83D\uDD25";
            col = Color.GOLD;
        } else {
            switch ((int) mult) {
                case 2 -> { txt = "★  COMBO  ×2!";         col = Color.GOLD;   }
                case 3 -> { txt = "★★  COMBO  ×3!  \uD83D\uDD25";  col = Color.ORANGE; }
                case 4 -> { txt = "\uD83D\uDD25  MAX COMBO  ×4!  \uD83D\uDD25"; col = Color.TOMATO; }
                default -> { return; }
            }
        }
        
        comboLabel.setText(txt);
        comboLabel.setFill(col);

        if (mult > lastMult && mult >= 2.0 && !isFeverActive()) {
            showMassiveComboPopup(txt, col);
        }
        lastMult = mult;

        if (comboPulseAnim.getStatus() != Animation.Status.RUNNING) {
            comboPulseAnim.play();
        }
    }

    private void showMassiveComboPopup(String text, Color col) {
        Text massive = new Text(text);
        massive.setFont(Font.font("Impact", FontWeight.BOLD, 90));
        massive.setFill(col);
        massive.setStroke(Color.WHITE);
        massive.setStrokeWidth(4.0);
        massive.setTextAlignment(TextAlignment.CENTER);
        
        // Center on screen
        massive.setLayoutX(1280 / 2.0 - 300); // Approximate centering
        massive.setLayoutY(720 / 2.0 + 30);
        massive.setMouseTransparent(true);
        
        DropShadow ds = new DropShadow(20, col);
        massive.setEffect(ds);
        
        root.getChildren().add(massive);
        
        ScaleTransition st = new ScaleTransition(Duration.millis(250), massive);
        st.setFromX(0.1); st.setFromY(0.1);
        st.setToX(1.2); st.setToY(1.2);
        st.setInterpolator(Interpolator.EASE_OUT);
        
        ScaleTransition st2 = new ScaleTransition(Duration.millis(150), massive);
        st2.setFromX(1.2); st2.setFromY(1.2);
        st2.setToX(1.0); st2.setToY(1.0);
        
        RotateTransition rt = new RotateTransition(Duration.millis(50), massive);
        rt.setFromAngle(-6); rt.setToAngle(6);
        rt.setAutoReverse(true);
        rt.setCycleCount(10);
        
        FadeTransition ft = new FadeTransition(Duration.millis(500), massive);
        ft.setFromValue(1.0); ft.setToValue(0.0);
        ft.setDelay(Duration.millis(600));
        
        SequentialTransition pop = new SequentialTransition(st, st2);
        
        ft.setOnFinished(e -> root.getChildren().remove(massive));
        
        pop.play();
        rt.play();
        ft.play();
    }

    /** Returns the combo label node so MainGame can layer it above HUD elements. */
    public Text getLabel() { return comboLabel; }
}
