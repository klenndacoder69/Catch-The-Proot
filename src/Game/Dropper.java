package Game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Drops fruits, trash, and power-ups from the top of the screen at random positions.
 *
 * Difficulty behaviour (drop interval / fall speed):
 *   1 = Level 1 — 1100ms interval, fall 4-9s
 *   2 = Level 2 — 750ms  interval, fall 3-7s  + trash
 *   3 = Level 3 — 500ms  interval, fall 2-5s  + more trash
 *
 * Every 3rd tick a second object drops simultaneously (multi-drop).
 * After 30 seconds elapsed the fall speed increases by 35%.
 *
 * Power-ups drop at an 18% chance (up from 8%) so they appear often.
 * All 7 power-up types are in the pool.
 *
 * Active falls are tracked in thread-safe lists so the Bomb and Freeze
 * power-ups can interact with all currently-falling items.
 */
public class Dropper {
    private volatile boolean gameOver = false;
    private volatile boolean paused   = false;
    private volatile boolean floorSweepActive = false;

    private final int         difficultyLevel;
    private final double      minScore;
    private final AnchorPane  root;
    private final Stage       primaryStage;
    private final Text        scoreText;
    private final ComboSystem comboSystem;
    private final EffectsManager effects;

    private static final double W = 1280, H = 720;
    private final ImageView movableDropper;
    private Fruit[]   fruits;
    private Fruit[]   trashPool;
    private Rectangle bottomRect;
    private ImageView heartRender;
    private Player    mainChar;
    private Timeline  dropTimeline;

    // ── Drop pacing ───────────────────────────────────────────────────────────
    private static final double POWER_UP_CHANCE = 0.18; // 18% — appears frequently
    private static final double FRUIT_SIZE      = 55;
    private static final double POWERUP_SIZE    = 65;

    private int  dropCount = 0;       // incremented each tick; every 3rd tick = multi-drop
    private long startTimeMs = 0;     // used to compute escalating fall speed

    // ── Active-fall tracking (for Bomb clear and Freeze) ──────────────────────
    // CopyOnWriteArrayList keeps reads safe from the polling threads.
    private final CopyOnWriteArrayList<ImageView>           activeFruitViews = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TranslateTransition> activeFalls      = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RotateTransition>    activeWobbles    = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ScaleTransition>     activePulses     = new CopyOnWriteArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    private final Runnable onDeath;

    public Dropper(int difficultyLevel, double minScore, AnchorPane root,
                   ImageView heartRender, Stage primaryStage,
                   Text scoreText, ComboSystem comboSystem, EffectsManager effects,
                   Runnable onDeath) {
        this.difficultyLevel = difficultyLevel;
        this.minScore        = minScore;
        this.root            = root;
        this.heartRender     = heartRender;
        this.primaryStage    = primaryStage;
        this.scoreText       = scoreText;
        this.comboSystem     = comboSystem;
        this.effects         = effects;
        this.onDeath         = onDeath;

        this.movableDropper = new ImageView(new Image("file:img/dropper_image.png"));
        initializePools();
        this.bottomRect = createBottomRect();
        root.getChildren().add(this.bottomRect);
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Fruit / trash pools per difficulty
    // ─────────────────────────────────────────────────────────────────────────
    private void initializePools() {
        fruits = (difficultyLevel == 0) ? FruitFactory.getTutorialFruits() : FruitFactory.getLevel1Fruits();

        trashPool = switch (difficultyLevel) {
            case 0, 1 -> new Fruit[0];
            case 2    -> FruitFactory.getLevel2Trash();
            default   -> FruitFactory.getLevel3Trash();
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────
    public void startDroppingMechanism(AnchorPane root, Player mainChar) {
        this.mainChar  = mainChar;
        this.startTimeMs = System.currentTimeMillis();

        movableDropper.setLayoutX(randomX());
        movableDropper.setLayoutY(-50);
        movableDropper.setVisible(false);
        root.getChildren().add(movableDropper);

        // Faster intervals — roughly half the original values
        double intervalMs = switch (difficultyLevel) {
            case 0 -> 2000;
            case 1 -> 1100;
            case 2 -> 750;
            default -> 500;
        };

        dropTimeline = new Timeline(new KeyFrame(Duration.millis(intervalMs), e -> {
            if (!gameOver && !paused) {
                dropCount++;
                
                if (comboSystem.isFeverActive()) {
                    // Fever Time: Rain bananas everywhere!
                    Random r = new Random();
                    for (int i = 0; i < 4; i++) {
                        movableDropper.setLayoutX(20 + r.nextDouble() * (W - 100));
                        dropObject(root, mainChar, true);
                    }
                } else {
                    movableDropper.setLayoutX(randomX());
                    dropObject(root, mainChar, false);

                    // Multi-drop: every 3rd tick, throw a second item (disabled for tutorial)
                    if (difficultyLevel > 0 && dropCount % 3 == 0) {
                        movableDropper.setLayoutX(randomX());
                        dropObject(root, mainChar, false);
                    }
                }
            }
        }));
        dropTimeline.setCycleCount(Timeline.INDEFINITE);
        dropTimeline.play();
    }

    public void stopDropping() {
        gameOver = true;
        if (dropTimeline != null) dropTimeline.stop();
    }

    public void setPaused(boolean p) {
        this.paused = p;
        if (p) {
            dropTimeline.stop();
            // Also pause all active falls
            for (TranslateTransition fall   : activeFalls)   fall.pause();
            for (RotateTransition    wobble : activeWobbles)  wobble.pause();
        } else if (!gameOver) {
            dropTimeline.play();
            for (TranslateTransition fall   : activeFalls)   fall.play();
            for (RotateTransition    wobble : activeWobbles)  wobble.play();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drop one object (fruit, trash, or power-up)
    // ─────────────────────────────────────────────────────────────────────────
    private void dropObject(AnchorPane root, Player mainChar, boolean feverMode) {
        Random rng = new Random();

        // Decide what to drop
        Fruit item;
        boolean isPowerUp = false;
        
        if (feverMode) {
            item = new Banana(); // Fever drops only bananas!
        } else {
            isPowerUp = (difficultyLevel > 0) && (rng.nextDouble() < POWER_UP_CHANCE);
            if (isPowerUp) {
                PowerUp.Type[] types = PowerUp.Type.values();
                item = new PowerUp(types[rng.nextInt(types.length)]);
            } else {
                boolean dropTrash = trashPool.length > 0 && rng.nextDouble() < trashRatio();
                item = dropTrash
                    ? trashPool[rng.nextInt(trashPool.length)]
                    : fruits[rng.nextInt(fruits.length)];
            }
        }

        final Fruit   fruit      = item;
        final boolean isMagicItem = isPowerUp;
        final boolean isBomb     = isPowerUp && ((PowerUp) item).getType() == PowerUp.Type.BOMB;

        // Create falling image — fixed display size so all items look consistent
        ImageView iv = new ImageView(fruit.getImageView().getImage());
        double displaySize = isPowerUp ? POWERUP_SIZE : FRUIT_SIZE;
        iv.setFitWidth(displaySize);
        iv.setFitHeight(displaySize);
        iv.setPreserveRatio(true);
        iv.setLayoutX(movableDropper.getLayoutX());
        iv.setLayoutY(movableDropper.getLayoutY());
        iv.setUserData(fruit); // Store reference to determine if it's trash later

        // Visual treatment
        if (isPowerUp) {
            Color glowCol = isBomb ? Color.ORANGERED : Color.GOLD;
            DropShadow glow = new DropShadow(20, glowCol);
            glow.setSpread(0.65);
            iv.setEffect(glow);
        } else {
            DropShadow ds = new DropShadow(6, Color.color(0, 0, 0, 0.45));
            ds.setOffsetY(3);
            iv.setEffect(ds);
        }

        root.getChildren().add(iv);

        // Fall speed — faster as the game progresses (escalating difficulty)
        double elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
        double accel      = (difficultyLevel > 0 && elapsedSec > 30) ? 0.65 : 1.0; // 35% faster after 30s
        if (feverMode) accel *= 0.65; // Even faster during fever!

        double minSpd = switch (difficultyLevel) { case 0 -> 5.0;  case 1 -> 4.0; case 2 -> 3.0; default -> 2.0; };
        double maxSpd = switch (difficultyLevel) { case 0 -> 15.0; case 1 -> 9.0; case 2 -> 7.0; default -> 5.0; };
        double speed  = (minSpd + rng.nextDouble() * (maxSpd - minSpd)) * accel;

        TranslateTransition fall = new TranslateTransition(Duration.seconds(speed), iv);
        fall.setToY(H);

        // Wobble spin while falling
        RotateTransition wobble = new RotateTransition(
                Duration.seconds(0.55 + rng.nextDouble() * 0.55), iv);
        double wobbleAngle = 14 + rng.nextDouble() * 16;
        wobble.setFromAngle(-wobbleAngle);
        wobble.setToAngle(wobbleAngle);
        wobble.setAutoReverse(true);
        wobble.setCycleCount(Animation.INDEFINITE);
        wobble.setInterpolator(Interpolator.EASE_BOTH);
        wobble.play();
        
        // Pulse squish while falling
        ScaleTransition pulse = new ScaleTransition(
                Duration.seconds(0.35 + rng.nextDouble() * 0.2), iv);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.08);  pulse.setToY(0.92);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();

        // Register in active lists (for Bomb and Freeze)
        activeFruitViews.add(iv);
        activeFalls.add(fall);
        activeWobbles.add(wobble);
        activePulses.add(pulse);

        boolean[] hit    = {false};
        boolean[] scored = {false};
        boolean[] magnetized = {false};

        // Collision polling thread (80ms tick)
        new Thread(() -> {
            while (!hit[0] && !scored[0] && !gameOver && iv.isVisible()) {
                try { Thread.sleep(80); } catch (InterruptedException ignored) {}

                Platform.runLater(() -> {
                    if (gameOver || scored[0]) return;

                    // ── Magnet Pull Logic ────────────────────────────────────
                    // Only attract good fruits and non-bomb power-ups
                    if (mainChar.isMagnetActive() && !isBomb && fruit.getLives() >= 0 && !magnetized[0]) {
                        Bounds pBounds = mainChar.getBoundsInParent();
                        double px = pBounds.getMinX() + pBounds.getWidth() / 2;
                        double py = pBounds.getMinY() + pBounds.getHeight() / 2;
                        
                        double fx = iv.getLayoutX() + iv.getTranslateX() + iv.getFitWidth() / 2;
                        double fy = iv.getLayoutY() + iv.getTranslateY() + iv.getFitHeight() / 2;
                        
                        double dist = Math.hypot(px - fx, py - fy);
                        
                        // If it enters the magnetic field (350px radius)
                        if (dist < 350) {
                            magnetized[0] = true;
                            fall.stop(); // Stop normal falling!
                            
                            // Homing missile swoop!
                            AnimationTimer homing = new AnimationTimer() {
                                @Override
                                public void handle(long now) {
                                    if (gameOver || hit[0]) {
                                        this.stop();
                                        return;
                                    }
                                    Bounds pB = mainChar.getBoundsInParent();
                                    double px = pB.getMinX() + pB.getWidth() / 2 - iv.getFitWidth() / 2;
                                    double py = pB.getMinY() + pB.getHeight() / 2 - iv.getFitHeight() / 2;
                                    
                                    double cx = iv.getLayoutX() + iv.getTranslateX();
                                    double cy = iv.getLayoutY() + iv.getTranslateY();
                                    
                                    // Curve effect: Y moves faster than X to swoop down first, then in
                                    iv.setTranslateX(iv.getTranslateX() + (px - cx) * 0.08);
                                    iv.setTranslateY(iv.getTranslateY() + (py - cy) * 0.12);
                                }
                            };
                            homing.start();
                        }
                    }

                    Bounds player = mainChar.getBoundsInParent();
                    Bounds floor  = bottomRect.getBoundsInParent();
                    Bounds obj    = iv.getBoundsInParent();

                    // ── Player caught the object ──────────────────────────────
                    if (!hit[0] && player.intersects(obj)) {
                        hit[0] = scored[0] = true;
                        fall.stop();
                        wobble.stop();
                        root.getChildren().remove(iv);
                        activeFruitViews.remove(iv);
                        activeFalls.remove(fall);
                        activeWobbles.remove(wobble);
                        activePulses.remove(pulse);
                        pulse.stop();

                        // Actual on-screen position for popup/particles
                        Bounds objBounds = iv.getBoundsInParent();
                        double cx = objBounds.getMinX() + objBounds.getWidth()  / 2;
                        double cy = objBounds.getMinY() + objBounds.getHeight() / 2;

                        if (isMagicItem) {
                            handlePowerUp((PowerUp) fruit, mainChar, cx, cy);
                            effects.chibiReact("celebrate");
                        } else if (fruit.getLives() < 0) {
                            // Caught trash
                            if (mainChar.consumeShield()) {
                                // Shield absorbed the hit
                                effects.flashCyan();
                                showPopup(cx, cy, "\uD83D\uDEE1 Blocked!", Color.CYAN);
                                effects.chibiReact("talking");
                            } else {
                                mainChar.addLives(-1);
                                comboSystem.resetCombo();
                                refreshHearts();
                                effects.shakeScreen();
                                effects.flashRed();
                                effects.chibiReact("flustered");
                                showPopup(cx, cy - 30, "-1 \u2764", Color.RED);
                                if (mainChar.getHearts() <= 0) triggerGameOver();
                            }
                        } else {
                            // Caught a good fruit
                            double comboMult   = comboSystem.getMultiplier();
                            double doublesMult = mainChar.getDoublePointsMultiplier();
                            double pts = fruit.getPoints() * comboMult * doublesMult;
                            mainChar.addPoints(pts);
                            updateScoreText();
                            boolean feverTriggered = comboSystem.recordCatch();
                            if (feverTriggered) effects.startFeverEffect();
                            
                            mainChar.triggerCatch();
                            effects.particleBurst(cx, cy, Color.LIMEGREEN);
                            if (comboSystem.getMultiplier() >= 3) {
                                effects.flashGreen();
                                effects.chibiReact("celebrate");
                            } else {
                                effects.chibiReact("talking");
                            }
                            String label = buildScoreLabel((int) pts, comboMult, doublesMult);
                            
                            // ── Juice: Shake and Floating Text ────────
                            effects.microShake();
                            effects.floatingText(cx, cy, label, Color.LIMEGREEN);
                        }
                    }

                    // ── Fruit reached the floor ───────────────────────────────
                    else if (!hit[0] && floor.intersects(obj)) {
                        if (floorSweepActive && fruit.getLives() >= 0) return; // Freeze active! Only good fruits sit on the floor!
                        
                        hit[0] = scored[0] = true;
                        fall.stop();
                        wobble.stop();
                        root.getChildren().remove(iv);
                        activeFruitViews.remove(iv);
                        activeFalls.remove(fall);
                        activeWobbles.remove(wobble);
                        activePulses.remove(pulse);
                        pulse.stop();

                        if (!isMagicItem && fruit.getLives() >= 0) {
                            mainChar.removePoints(fruit.getPoints());
                            updateScoreText();
                            comboSystem.resetCombo();
                            effects.chibiReact("flustered");
                            showPopup(iv.getLayoutX(), H - 80,
                                    "-" + (int) fruit.getPoints(), Color.ORANGERED);
                        }
                        // Missed trash → no penalty
                    }
                });
            }
        }).start();

        fall.setOnFinished(e -> {
            wobble.stop();
            pulse.stop();
            root.getChildren().remove(iv);
            activeFruitViews.remove(iv);
            activeFalls.remove(fall);
            activeWobbles.remove(wobble);
            activePulses.remove(pulse);
        });
        fall.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Power-up handling
    // ─────────────────────────────────────────────────────────────────────────
    private void handlePowerUp(PowerUp pu, Player player, double cx, double cy) {
        switch (pu.getType()) {
            case SPEED_BOOST -> {
                player.applySpeedBoost(5.0);
                player.triggerCatch();
                effects.particleBurst(cx, cy, Color.YELLOW);
                effects.flashCyan();
                showPopup(cx, cy, pu.getDisplayText(), Color.YELLOW);
            }
            case MAGNET -> {
                player.applyMagnet(4.0);
                player.triggerCatch();
                effects.particleBurst(cx, cy, Color.CYAN);
                effects.flashCyan();
                showPopup(cx, cy, pu.getDisplayText(), Color.CYAN);
            }
            case EXTRA_LIFE -> {
                if (player.getHearts() < 3) {
                    player.addLives(1);
                    refreshHearts();
                }
                player.triggerCatch();
                effects.particleBurst(cx, cy, Color.HOTPINK);
                effects.flashCyan();
                showPopup(cx, cy, pu.getDisplayText(), Color.HOTPINK);
            }
            case BOMB -> {
                // Snapshot and clear all active fruits
                List<ImageView>           toClear   = new ArrayList<>(activeFruitViews);
                List<TranslateTransition> toStopF   = new ArrayList<>(activeFalls);
                List<RotateTransition>    toStopW   = new ArrayList<>(activeWobbles);
                List<ScaleTransition>     toStopP   = new ArrayList<>(activePulses);
                activeFruitViews.clear();
                activeFalls.clear();
                activeWobbles.clear();
                activePulses.clear();

                int bonus = toClear.size() * 50;
                player.addPoints(bonus);
                updateScoreText();

                for (int i = 0; i < toClear.size(); i++) {
                    ImageView fv = toClear.get(i);
                    if (i < toStopF.size()) toStopF.get(i).stop();
                    if (i < toStopW.size()) toStopW.get(i).stop();
                    if (i < toStopP.size()) toStopP.get(i).stop();
                    // Blast each fruit upward and fade it out
                    TranslateTransition blast = new TranslateTransition(Duration.millis(280), fv);
                    blast.setToY(-220);
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(280), fv);
                    fadeOut.setFromValue(1.0); fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(ev -> root.getChildren().remove(fv));
                    blast.play(); fadeOut.play();
                }

                effects.bombExplosion(cx, cy);
                
                // Heavy Hit Stop
                try { Thread.sleep(150); } catch (Exception ignored) {}
                
                String bombLabel = bonus > 0
                    ? "\uD83D\uDCA3 BOOM! +" + bonus
                    : "\uD83D\uDCA3 BOOM!";
                effects.floatingText(cx, cy, bombLabel, Color.ORANGERED);
            }
            case DOUBLE_POINTS -> {
                player.applyDoublePoints(8.0);
                player.triggerCatch();
                effects.particleBurst(cx, cy, Color.GOLD);
                effects.flashGreen();
                showPopup(cx, cy, pu.getDisplayText(), Color.GOLD);
            }
            case FREEZE -> {
                // ── Freeze Overhaul: Slam fruits to the floor! ───────────────
                effects.flashBlue();
                effects.particleBurst(cx, cy, Color.CYAN);
                effects.floatingText(cx, cy, pu.getDisplayText(), Color.CYAN);

                floorSweepActive = true;

                // Drop all currently active fruits to the ground instantly
                for (int i = 0; i < activeFruitViews.size(); i++) {
                    ImageView fv = activeFruitViews.get(i);
                    Fruit fData = (Fruit) fv.getUserData();
                    // Skip if it's trash! We only want to freeze good fruits!
                    if (fData != null && fData.getLives() < 0) continue;
                    
                    if (i < activeFalls.size()) activeFalls.get(i).stop();
                    if (i < activeWobbles.size()) activeWobbles.get(i).stop();
                    if (i < activePulses.size()) activePulses.get(i).stop();

                    TranslateTransition slam = new TranslateTransition(Duration.millis(150), fv);
                    slam.setToY(585); // Sit exactly flush on the floor (640 - 55)
                    slam.setInterpolator(Interpolator.EASE_IN);
                    slam.play();
                }

                // Restore game after 4 seconds (any fruits left on floor will instantly miss!)
                PauseTransition restore = new PauseTransition(Duration.seconds(4));
                restore.setOnFinished(ev -> floorSweepActive = false);
                restore.play();
            }
            case SHIELD -> {
                player.applyShield();
                player.triggerCatch();
                effects.particleBurst(cx, cy, Color.LIGHTBLUE);
                effects.flashCyan();
                showPopup(cx, cy, pu.getDisplayText(), Color.LIGHTBLUE);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Score label builder
    // ─────────────────────────────────────────────────────────────────────────
    private String buildScoreLabel(int pts, double comboMult, double doublesMult) {
        StringBuilder sb = new StringBuilder("+" + pts);
        if (comboMult > 1) sb.append(" \u00d7").append((int) comboMult);
        if (doublesMult > 1) sb.append(" \u2B50");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Game over
    // ─────────────────────────────────────────────────────────────────────────
    private void triggerGameOver() {
        gameOver = true;
        stopDropping();
        if (onDeath != null) {
            onDeath.run();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Floating popup text
    // ─────────────────────────────────────────────────────────────────────────
    private void showPopup(double x, double y, String msg, Color color) {
        Text popup = new Text(msg);
        popup.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 28));
        popup.setFill(color);
        popup.setStroke(Color.color(0, 0, 0, 0.6));
        popup.setStrokeWidth(1.5);
        popup.setLayoutX(Math.min(Math.max(x, 10), W - 160));
        popup.setLayoutY(Math.max(y, 90));
        popup.setMouseTransparent(true);

        DropShadow shadow = new DropShadow(8, Color.color(0, 0, 0, 0.7));
        shadow.setOffsetY(2);
        popup.setEffect(shadow);

        root.getChildren().add(popup);

        ScaleTransition popIn = new ScaleTransition(Duration.millis(120), popup);
        popIn.setFromX(0.4); popIn.setFromY(0.4);
        popIn.setToX(1.0);   popIn.setToY(1.0);
        popIn.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition rise = new TranslateTransition(Duration.millis(1000), popup);
        rise.setToY(-65);

        FadeTransition fade = new FadeTransition(Duration.millis(1000), popup);
        fade.setFromValue(1.0); fade.setToValue(0.0);
        fade.setOnFinished(e -> root.getChildren().remove(popup));

        popIn.play(); rise.play(); fade.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void refreshHearts() {
        root.getChildren().remove(heartRender);
        heartRender = LivesSystem.heartsRender(mainChar.getHearts());
        root.getChildren().add(heartRender);
        
        // ── Juice: Heart Damage Jiggle ──────────────────────────────────────
        ScaleTransition heartPop = new ScaleTransition(Duration.millis(150), heartRender);
        heartPop.setFromX(1.5); heartPop.setFromY(1.5);
        heartPop.setToX(1.0); heartPop.setToY(1.0);
        
        RotateTransition heartJiggle = new RotateTransition(Duration.millis(40), heartRender);
        heartJiggle.setFromAngle(-15);
        heartJiggle.setToAngle(15);
        heartJiggle.setAutoReverse(true);
        heartJiggle.setCycleCount(6);
        heartJiggle.setOnFinished(e -> heartRender.setRotate(0));
        
        heartPop.play();
        heartJiggle.play();
    }

    private void updateScoreText() {
        if (scoreText != null) {
            scoreText.setText("Score: " + (int) mainChar.getScore());
            
            // ── Juice: Score Bump ───────────────────────────────────────────
            ScaleTransition bump = new ScaleTransition(Duration.millis(120), scoreText);
            bump.setFromX(1.0); bump.setFromY(1.0);
            bump.setToX(1.25); bump.setToY(1.25);
            bump.setAutoReverse(true);
            bump.setCycleCount(2);
            bump.play();
        }
    }

    private double trashRatio() {
        return switch (difficultyLevel) {
            case 2 -> 0.25;
            default -> 0.40;
        };
    }

    private Rectangle createBottomRect() {
        Rectangle r = new Rectangle(W, 16);
        r.setFill(Color.TRANSPARENT);
        AnchorPane.setBottomAnchor(r, 0.0);
        return r;
    }

    private double randomX() {
        if (mainChar == null) return new Random().nextDouble(W - 80);
        
        // Smart Drop: Use a Gaussian (bell curve) distribution centered around the player.
        // This makes fruits more likely to drop near the player (fair), while still 
        // occasionally dropping further away to force the player to dash (challenging).
        Random r = new Random();
        double playerCenterX = mainChar.getCharXPosition() + 50; 
        double standardDeviation = 380.0; 
        
        double x = playerCenterX + r.nextGaussian() * standardDeviation;
        
        // Clamp to ensure it doesn't spawn off-screen
        return Math.max(20, Math.min(W - 100, x));
    }
}