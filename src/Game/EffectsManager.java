package Game;

import javafx.animation.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.Random;

/**
 * Centralises all screen effects:
 * - Screen shake (on life loss / bomb)
 * - Red flash overlay (on life loss)
 * - Green flash overlay (on high combo catch)
 * - Cyan flash overlay (on power-up catch)
 * - Blue flash overlay (on freeze)
 * - Orange flash overlay (on bomb)
 * - Particle burst (on any fruit catch)
 * - Bomb explosion (huge radial burst + shake)
 */
public class EffectsManager {
    private static final double W = 1280, H = 720;
    private final AnchorPane root;

    public EffectsManager(AnchorPane root) {
        this.root = root;
    }

    // ── Reactive Chibi HUD ───────────────────────────────────────────────────
    private javafx.scene.image.ImageView chibiView;
    private javafx.scene.image.Image chibiNormal, chibiCelebrate, chibiFlustered, chibiTalking;
    private PauseTransition chibiRevertTimer;

    public void initChibi() {
        chibiNormal    = new javafx.scene.image.Image("file:img/chibi_normal.png");
        chibiCelebrate = new javafx.scene.image.Image("file:img/chibi_celebrate.png");
        chibiFlustered = new javafx.scene.image.Image("file:img/chibi_flustered.png");
        chibiTalking   = new javafx.scene.image.Image("file:img/chibi_talking.png");

        chibiView = new javafx.scene.image.ImageView(chibiNormal);
        chibiView.setFitWidth(170); // Cute small size in the corner
        chibiView.setPreserveRatio(true);
        chibiView.setMouseTransparent(true);
        chibiView.setOpacity(0.90);

        AnchorPane.setBottomAnchor(chibiView, -5.0);
        AnchorPane.setRightAnchor(chibiView, 5.0); // Bottom-right corner
        root.getChildren().add(chibiView);

        chibiRevertTimer = new PauseTransition(Duration.seconds(1.5));
        chibiRevertTimer.setOnFinished(e -> chibiView.setImage(chibiNormal));
    }

    public void chibiReact(String mood) {
        if (chibiView == null) return;
        switch (mood) {
            case "celebrate" -> chibiView.setImage(chibiCelebrate);
            case "flustered" -> chibiView.setImage(chibiFlustered);
            case "talking"   -> chibiView.setImage(chibiTalking);
            default          -> chibiView.setImage(chibiNormal);
        }
        chibiRevertTimer.playFromStart();
    }

    /** Shakes the root pane left-right briefly. */
    public void shakeScreen() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(60), root);
        shake.setFromX(-10); shake.setToX(10);
        shake.setAutoReverse(true);
        shake.setCycleCount(5);
        shake.setOnFinished(e -> { root.setTranslateX(0); root.setTranslateY(0); });
        shake.play();
    }

    /** Micro shake for standard catches. */
    public void microShake() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(30), root);
        shake.setFromX(-4); shake.setToX(4);
        shake.setAutoReverse(true);
        shake.setCycleCount(3);
        shake.setOnFinished(e -> { root.setTranslateX(0); root.setTranslateY(0); });
        shake.play();
    }

    /** More intense shake for the bomb explosion. */
    public void heavyShake() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), root);
        shake.setFromX(-18); shake.setToX(18);
        shake.setAutoReverse(true);
        shake.setCycleCount(8);
        shake.setOnFinished(e -> { root.setTranslateX(0); root.setTranslateY(0); });
        shake.play();
    }

    /** Red semi-transparent overlay that fades out — signals life loss. */
    public void flashRed() {
        flashOverlay(Color.color(1.0, 0.0, 0.0, 0.40));
    }

    /** Green semi-transparent overlay — signals a big combo catch. */
    public void flashGreen() {
        flashOverlay(Color.color(0.0, 0.9, 0.2, 0.25));
    }

    /** Cyan overlay — signals a power-up collected. */
    public void flashCyan() {
        flashOverlay(Color.color(0.0, 0.8, 1.0, 0.25));
    }

    /** Blue overlay — signals freeze activated. */
    public void flashBlue() {
        flashOverlay(Color.color(0.1, 0.5, 1.0, 0.35));
    }

    /** Orange overlay — signals bomb explosion. */
    public void flashOrange() {
        flashOverlay(Color.color(1.0, 0.45, 0.0, 0.50));
    }

    /** 8-second pulsing golden hue for Fever Time */
    public void startFeverEffect() {
        Rectangle feverTint = new Rectangle(W, H);
        feverTint.setFill(Color.color(1.0, 0.84, 0.0, 0.25)); // Golden tint
        feverTint.setMouseTransparent(true);
        root.getChildren().add(feverTint);
        
        // Pulse it
        FadeTransition pulse = new FadeTransition(Duration.millis(300), feverTint);
        pulse.setFromValue(0.15);
        pulse.setToValue(0.35);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
        
        // Remove after 8 seconds
        PauseTransition pt = new PauseTransition(Duration.seconds(8));
        pt.setOnFinished(e -> {
            pulse.stop();
            FadeTransition out = new FadeTransition(Duration.millis(500), feverTint);
            out.setToValue(0.0);
            out.setOnFinished(ev -> root.getChildren().remove(feverTint));
            out.play();
        });
        pt.play();
        
        chibiReact("celebrate");
    }

    /**
     * Creates a directional afterimage trail for the Double Dash.
     * Four ghost streaks appear at the player's current position and slide
     * in the OPPOSITE direction of the dash, then fade out.
     *
     * @param charX      left-anchor X of the character
     * @param charY      top-anchor Y of the character
     * @param charWidth  display width of the character sprite
     * @param charHeight display height of the character sprite
     * @param dashRight  true if the character dashed to the right
     */
    public void dashTrail(double charX, double charY,
                          double charWidth, double charHeight,
                          boolean dashRight) {
        // Streaks slide backward (opposite of dash direction)
        double slideX = dashRight ? -70 : 70;

        for (int i = 0; i < 4; i++) {
            // Stagger each streak slightly behind the previous
            double offsetX = dashRight ? -i * 22.0 : i * 22.0;
            double alpha   = 0.55 - i * 0.10;

            javafx.scene.shape.Ellipse streak =
                    new javafx.scene.shape.Ellipse(38 - i * 4, 7 - i);
            streak.setFill(Color.color(0.65, 0.90, 1.0, alpha));
            streak.setLayoutX(charX + charWidth / 2 + offsetX);
            streak.setLayoutY(charY + charHeight * 0.55);
            streak.setMouseTransparent(true);
            root.getChildren().add(streak);

            int delay = i * 25;
            TranslateTransition move = new TranslateTransition(
                    Duration.millis(200 + delay), streak);
            move.setToX(slideX - i * 10);

            FadeTransition fade = new FadeTransition(
                    Duration.millis(220 + delay), streak);
            fade.setFromValue(alpha);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> root.getChildren().remove(streak));

            move.play();
            fade.play();
        }
    }

    private void flashOverlay(Color color) {
        Rectangle flash = new Rectangle(W, H);
        flash.setFill(color);
        flash.setMouseTransparent(true);
        root.getChildren().add(flash);

        FadeTransition fade = new FadeTransition(Duration.millis(450), flash);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(e -> root.getChildren().remove(flash));
        fade.play();
    }

    /**
     * Scatters small coloured dots from (x, y) — used on fruit catch.
     * Includes sparkle-white dots for a premium feel.
     *
     * @param x      layout X of the caught fruit
     * @param y      layout Y of the caught fruit
     * @param color  particle colour
     */
    public void particleBurst(double x, double y, Color color) {
        Random rng = new Random();
        int count = 40;
        
        Circle[] dots = new Circle[count];
        double[] dx = new double[count];
        double[] dy = new double[count];
        
        for (int i = 0; i < count; i++) {
            boolean isSpark = i >= 30;
            double radius = isSpark ? 2 : 4 + rng.nextDouble() * 5;
            Color pCol = isSpark ? Color.WHITE : color.deriveColor(0, 1, 0.9 + rng.nextDouble() * 0.1, 1.0);
            
            dots[i] = new Circle(radius, pCol);
            dots[i].setLayoutX(x);
            dots[i].setLayoutY(y);
            root.getChildren().add(dots[i]);
            
            // Explode upwards and outwards
            double angle = rng.nextDouble() * Math.PI * 2;
            double speed = 6 + rng.nextDouble() * 12;
            dx[i] = Math.cos(angle) * speed;
            dy[i] = -Math.abs(Math.sin(angle)) * speed - 5; // Bias upwards
        }
        
        AnimationTimer timer = new AnimationTimer() {
            int frames = 0;
            @Override
            public void handle(long now) {
                frames++;
                for (int i = 0; i < count; i++) {
                    dots[i].setLayoutX(dots[i].getLayoutX() + dx[i]);
                    dots[i].setLayoutY(dots[i].getLayoutY() + dy[i]);
                    dy[i] += 0.85; // Heavy gravity pull!
                    
                    // Fade out
                    dots[i].setOpacity(Math.max(0, 1.0 - (frames / 45.0)));
                }
                if (frames > 45) {
                    for (Circle d : dots) root.getChildren().remove(d);
                    this.stop();
                }
            }
        };
        timer.start();
    }

    /**
     * Massive radial explosion centred at (cx, cy) — used by the Bomb power-up.
     * Also triggers a heavy screen shake and orange flash.
     */
    public void bombExplosion(double cx, double cy) {
        flashOrange();
        heavyShake();

        Random rng = new Random();
        int count = 48;
        Color[] palette = {
            Color.ORANGERED, Color.GOLD, Color.YELLOW,
            Color.TOMATO, Color.WHITE, Color.ORANGE
        };

        for (int i = 0; i < count; i++) {
            double angle  = (360.0 / count) * i + rng.nextDouble() * 6;
            double radius = 5 + rng.nextDouble() * 7;
            Color  col    = palette[rng.nextInt(palette.length)];

            Circle dot = new Circle(radius, col);
            dot.setLayoutX(Math.min(Math.max(cx, 0), W));
            dot.setLayoutY(Math.min(Math.max(cy, 0), H));
            root.getChildren().add(dot);

            double dist = 90 + rng.nextDouble() * 180;
            TranslateTransition move = new TranslateTransition(
                    Duration.millis(400 + rng.nextInt(400)), dot);
            move.setToX(Math.cos(Math.toRadians(angle)) * dist);
            move.setToY(Math.sin(Math.toRadians(angle)) * dist);

            FadeTransition fade = new FadeTransition(
                    Duration.millis(500 + rng.nextInt(300)), dot);
            fade.setFromValue(1.0); fade.setToValue(0.0);
            fade.setOnFinished(e -> root.getChildren().remove(dot));

            move.play();
            fade.play();
        }
    }

    /** 
     * Floating Score Pop-Up
     * A colorful text that shoots out of a caught fruit and fades upwards!
     */
    public void floatingText(double x, double y, String text, Color color) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(text);
        t.setFont(javafx.scene.text.Font.font("Impact", javafx.scene.text.FontWeight.BOLD, 36));
        t.setFill(color);
        t.setStroke(Color.WHITE);
        t.setStrokeWidth(2.0);
        t.setLayoutX(x - 20);
        t.setLayoutY(y - 20);
        t.setMouseTransparent(true);
        root.getChildren().add(t);

        // Pop in
        ScaleTransition st = new ScaleTransition(Duration.millis(150), t);
        st.setFromX(0.1); st.setFromY(0.1);
        st.setToX(1.3); st.setToY(1.3);
        st.setAutoReverse(true);
        st.setCycleCount(2);

        // Float up
        TranslateTransition tt = new TranslateTransition(Duration.millis(800), t);
        tt.setToY(-120);

        // Fade out
        FadeTransition ft = new FadeTransition(Duration.millis(800), t);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);

        tt.setOnFinished(e -> root.getChildren().remove(t));

        st.play();
        tt.play();
        ft.play();
    }
}
