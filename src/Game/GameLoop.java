package Game;

import javafx.animation.AnimationTimer;
import javafx.scene.input.KeyCode;

import java.util.Set;

/**
 * 60fps game loop that drives smooth, velocity-based player movement.
 * Also fires an optional per-frame callback for HUD updates.
 *
 * Call start() to begin and stop() to halt.
 * Call setPaused(true/false) during pause-menu transitions.
 */
public class GameLoop extends AnimationTimer {
    private long lastNano = -1;
    private final Player player;
    private final Set<KeyCode> pressedKeys;
    private final Runnable frameCallback; // optional HUD/state updates per frame
    private volatile boolean paused = false;

    public GameLoop(Player player, Set<KeyCode> pressedKeys, Runnable frameCallback) {
        this.player = player;
        this.pressedKeys = pressedKeys;
        this.frameCallback = frameCallback;
    }

    @Override
    public void handle(long now) {
        if (paused) {
            lastNano = -1; // reset so no huge delta on resume
            return;
        }
        if (lastNano < 0) { lastNano = now; return; }

        // Delta time in seconds — cap at 50ms to avoid huge jumps on lag spikes
        double delta = Math.min((now - lastNano) / 1_000_000_000.0, 0.05);
        lastNano = now;

        boolean left  = pressedKeys.contains(KeyCode.LEFT)  || pressedKeys.contains(KeyCode.A);
        boolean right = pressedKeys.contains(KeyCode.RIGHT) || pressedKeys.contains(KeyCode.D);
        boolean boost = pressedKeys.contains(KeyCode.Z)     || pressedKeys.contains(KeyCode.SHIFT);

        player.updateMovement(delta, left, right, boost);

        if (frameCallback != null) frameCallback.run();
    }

    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused()             { return paused; }
}
