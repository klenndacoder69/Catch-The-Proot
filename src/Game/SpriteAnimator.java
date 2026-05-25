package Game;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/**
 * Plays sprite-sheet animations from character_sheet.png.
 *
 * Sheet layout  : 4 columns × 4 rows, each frame 200 × 400 px
 * Row 0 — Idle  (4 frames)
 * Row 1 — Walk  (4 frames)
 * Row 2 — Run   (4 frames)
 * Row 3 — Catch (4 frames, plays once then returns to Idle)
 *
 * Default facing: RIGHT (as drawn in the sheet).
 * Use getView().setScaleX(-1) to mirror for leftward movement.
 */
public class SpriteAnimator {

    public enum State { IDLE, WALK, RUN, CATCH }

    // ── Sheet constants ───────────────────────────────────────────────────────
    private static final int    COLS          = 4;
    private static final int    FRAME_W       = 200;
    private static final int    FRAME_H       = 400;
    private static final double DISPLAY_SCALE = 0.5;   // 200×400 → 100×200 on screen

    // Row indices
    private static final int ROW_IDLE  = 0;
    private static final int ROW_WALK  = 1;
    private static final int ROW_RUN   = 2;
    private static final int ROW_CATCH = 3;

    // Frame rates (frames per second) per animation
    // Kept deliberately low for a 4-frame sheet — too high looks like a blur
    private static final double FPS_IDLE  = 5;
    private static final double FPS_WALK  = 6;
    private static final double FPS_RUN   = 9;
    private static final double FPS_CATCH = 7;

    // ── State ─────────────────────────────────────────────────────────────────
    private final ImageView view;
    private Scale           mirrorScale;   // fixed-pivot X flip — avoids layout jump
    private Timeline        timeline;
    private int             currentFrame = 0;
    private State           currentState = null;
    private boolean         catchPlaying = false;

    // ─────────────────────────────────────────────────────────────────────────
    public SpriteAnimator(String sheetPath) {
        // Strip the white canvas — pixels with R>0.9 & G>0.9 & B>0.9 → transparent.
        // The character's colours (red, dark, brown, skin) all have at least one
        // channel well below 0.9 so they are completely unaffected.
        Image sheet = removeWhiteBackground(sheetPath);
        view = new ImageView(sheet);
        view.setFitWidth(FRAME_W  * DISPLAY_SCALE);
        view.setFitHeight(FRAME_H * DISPLAY_SCALE);
        view.setPreserveRatio(false);

        // Use a Scale transform with a fixed pivot at the horizontal centre of
        // the sprite cell. This prevents the visual "jump" that occurs when
        // setScaleX(-1) is used directly and JavaFX recomputes the pivot from
        // the changing layout bounds during a frame switch.
        mirrorScale = new Scale(1, 1, FRAME_W * DISPLAY_SCALE / 2.0, 0);
        view.getTransforms().add(mirrorScale);

        // Show frame 0 immediately — no blank flash on first render
        view.setViewport(new Rectangle2D(0, 0, FRAME_W, FRAME_H));
        play(State.IDLE);
    }

    private static Image removeWhiteBackground(String path) {
        Image raw = new Image(path);
        int w = (int) raw.getWidth();
        int h = (int) raw.getHeight();
        WritableImage result = new WritableImage(w, h);
        PixelReader  pr = raw.getPixelReader();
        PixelWriter  pw = result.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = pr.getColor(x, y);
                if (c.getRed() > 0.9 && c.getGreen() > 0.9 && c.getBlue() > 0.9) {
                    pw.setColor(x, y, Color.TRANSPARENT);
                } else {
                    pw.setColor(x, y, c);
                }
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** The ImageView to add to the scene graph. */
    public ImageView getView() { return view; }

    public double getDisplayWidth()  { return FRAME_W  * DISPLAY_SCALE; }
    public double getDisplayHeight() { return FRAME_H * DISPLAY_SCALE; }

    /**
     * Flips the sprite horizontally using a fixed-pivot Scale transform.
     * @param mirrored  true = face left (scale X = -1), false = face right (natural)
     */
    public void setMirror(boolean mirrored) {
        mirrorScale.setX(mirrored ? -1 : 1);
    }

    /**
     * Switch to a looping animation state.
     * No-op if that state is already playing.
     * Ignored while a one-shot Catch animation is in progress.
     */
    public void play(State state) {
        if (catchPlaying)           return; // let Catch finish first
        if (state == currentState)  return; // already playing — don't restart
        startTimeline(state);
    }

    /**
     * Trigger a one-shot Catch animation (plays once then returns to Idle).
     * Safe to call while any other animation is active.
     */
    public void triggerCatch() {
        if (catchPlaying) return; // already catching
        catchPlaying = true;
        currentState = null;      // force restart
        startTimeline(State.CATCH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────
    private void startTimeline(State state) {
        currentState = state;
        currentFrame = 0;

        if (timeline != null) timeline.stop();

        int    row     = rowFor(state);
        double fps     = fpsFor(state);
        boolean oneShot = (state == State.CATCH);

        timeline = new Timeline(new KeyFrame(Duration.seconds(1.0 / fps), e -> {
            view.setViewport(new Rectangle2D(
                    currentFrame * FRAME_W,
                    row          * FRAME_H,
                    FRAME_W, FRAME_H));
            currentFrame = (currentFrame + 1) % COLS;
        }));

        timeline.setCycleCount(oneShot ? COLS : Timeline.INDEFINITE);

        if (oneShot) {
            timeline.setOnFinished(ev -> {
                catchPlaying = false;
                currentState = null;   // allow play() to pick up the next state
                play(State.IDLE);
            });
        }

        timeline.play();
    }

    private static int rowFor(State s) {
        return switch (s) {
            case IDLE  -> ROW_IDLE;
            case WALK  -> ROW_WALK;
            case RUN   -> ROW_RUN;
            case CATCH -> ROW_CATCH;
        };
    }

    private static double fpsFor(State s) {
        return switch (s) {
            case IDLE  -> FPS_IDLE;
            case WALK  -> FPS_WALK;
            case RUN   -> FPS_RUN;
            case CATCH -> FPS_CATCH;
        };
    }
}
