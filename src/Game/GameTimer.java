package Game;

import javafx.animation.AnimationTimer;

import java.util.function.Consumer;

/**
 * 60-second countdown timer.
 *
 * Callbacks:
 *   onTick   — fired every second with the remaining time (for UI)
 *   onTimeUp — fired once when the countdown reaches zero
 *
 * Supports pausing via setPaused(true/false).
 */
public class GameTimer extends AnimationTimer {
    private long startNano    = -1;
    private long lastTickNano = -1;
    private long pausedAtNano = -1;
    private long totalPausedNanos = 0;
    private boolean finished  = false;
    private boolean paused    = false;

    private final Runnable        onTimeUp;
    private final Consumer<Long>  onTick;

    public GameTimer(Runnable onTimeUp, Consumer<Long> onTick) {
        this.onTimeUp = onTimeUp;
        this.onTick   = onTick;
    }

    /** Legacy no-arg constructor kept for compatibility. */
    public GameTimer() {
        this.onTimeUp = null;
        this.onTick   = null;
    }

    @Override
    public void handle(long now) {
        if (paused || finished) return;

        if (startNano < 0) {
            startNano    = now;
            lastTickNano = now;
            return;
        }

        long elapsed   = now - startNano - totalPausedNanos;
        long remaining = 60 - elapsed / 1_000_000_000L;

        if (remaining <= 0) {
            finished = true;
            stop();
            if (onTimeUp != null) onTimeUp.run();
            return;
        }

        long sinceLastTick = now - lastTickNano;
        if (sinceLastTick >= 1_000_000_000L) {
            lastTickNano = now;
            if (onTick != null) onTick.accept(remaining);
        }
    }

    public void setPaused(boolean p) {
        if (p && !paused) {
            paused      = true;
            pausedAtNano = System.nanoTime();
        } else if (!p && paused) {
            paused = false;
            if (pausedAtNano > 0) {
                totalPausedNanos += System.nanoTime() - pausedAtNano;
                pausedAtNano = -1;
            }
        }
    }

    /** @deprecated Use the AnimationTimer lifecycle (start/stop) instead. */
    public void update() {}
}