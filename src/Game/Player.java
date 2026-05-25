package Game;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.animation.Animation;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

/**
 * The player character — drives smooth movement via GameLoop (60fps)
 * and plays sprite-sheet animations through SpriteAnimator.
 *
 * Animation rules (set in updateMovement every frame):
 *   speed > 300 px/s AND boost key held  →  Run
 *   speed > 20 px/s                      →  Walk
 *   otherwise                            →  Idle
 *   fruit caught (Dropper calls triggerCatch()) → Catch (one-shot)
 *
 * Facing direction:
 *   The sprite sheet faces RIGHT by default.
 *   Moving left  → animator.setMirror(true)  (flipped)
 *   Moving right → animator.setMirror(false) (natural)
 */
public class Player {
    public int    hearts;
    public double score;

    private final SpriteAnimator animator;
    private double charXPosition;
    private double charYPosition;
    
    private EffectsManager effects;
    private long lastTrailMs = 0;
    
    private ScaleTransition breatheAnim;

    // ── Velocity / movement constants ────────────────────────────────────────
    private double velocityX = 0;
    private static final double MAX_SPEED    = 480;   // px/s normal
    private static final double BOOST_SPEED  = 750;   // px/s with Z / Shift
    private static final double ACCELERATION = 3200;  // px/s²
    private static final double FRICTION     = 12.0;  // deceleration multiplier

    // The sprite sheet has empty space at the bottom of each frame cell.
    // Shift the Y anchor up by that amount so the feet land on the floor.
    private static final double FOOT_OFFSET  = 0.125; // fraction of display height

    // ── Power-up state ────────────────────────────────────────────────────────
    private boolean speedBoostActive    = false;
    private long    speedBoostEndMs     = 0;
    private boolean magnetActive        = false;
    private long    magnetEndMs         = 0;
    private boolean doublePointsActive  = false;
    private long    doublePointsEndMs   = 0;
    private boolean shieldActive        = false;  // one-shot: consumed on next trash hit

    // ── Dash state ───────────────────────────────────────────────────────────
    private static final double DASH_SPEED       = 1400; // px/s burst
    private static final long   DASH_DURATION_MS = 150;  // burst lasts 150ms
    private static final long   DASH_COOLDOWN_MS = 750;  // cooldown between dashes
    private boolean isDashing        = false;
    private boolean dashRight        = true;
    private long    dashEndMs        = 0;
    private long    dashCooldownEndMs = 0;

    // ─────────────────────────────────────────────────────────────────────────
    public Player() {
        animator = new SpriteAnimator("file:img/character_sheet.png");

        double w = animator.getDisplayWidth();   // 100 px
        double h = animator.getDisplayHeight();  // 200 px

        // Centre horizontally; shift up so the character's feet sit on the floor
        // (the sprite frame has ~12.5% empty space at the bottom)
        charXPosition = (MainGame.windowXSize - w) / 2.0;
        charYPosition = 720 - h - 10 - (h * FOOT_OFFSET);

        AnchorPane.setLeftAnchor(animator.getView(), charXPosition);
        AnchorPane.setTopAnchor(animator.getView(),  charYPosition);

        hearts = 3;
        score  = 0;
        
        breatheAnim = new ScaleTransition(Duration.millis(900), animator.getView());
        breatheAnim.setFromY(1.0);
        breatheAnim.setToY(0.96);
        breatheAnim.setAutoReverse(true);
        breatheAnim.setCycleCount(Animation.INDEFINITE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Called every frame by GameLoop
    // ─────────────────────────────────────────────────────────────────────────
    public void updateMovement(double delta, boolean left, boolean right, boolean boost) {
        long now = System.currentTimeMillis();

        // Expire timed power-ups
        if (speedBoostActive   && now > speedBoostEndMs)   speedBoostActive   = false;
        if (magnetActive       && now > magnetEndMs)       magnetActive       = false;
        if (doublePointsActive && now > doublePointsEndMs) doublePointsActive = false;

        // ── Active dash: lock direction, skip normal input ─────────────────
        if (isDashing) {
            if (now >= dashEndMs) {
                isDashing = false;
                // After burst ends, clamp to normal top speed
                double topSpeedAfter = (speedBoostActive ? 1.5 : 1.0) * MAX_SPEED;
                velocityX = Math.signum(velocityX) * Math.min(Math.abs(velocityX), topSpeedAfter);
            }
            // Always play Run during dash regardless of normal speed
            animator.play(SpriteAnimator.State.RUN);
            if (velocityX < 0) moveLeft(-velocityX * delta);
            else if (velocityX > 0) moveRight(velocityX * delta);
            return; // skip normal movement update while dashing
        }

        double topSpeed = (speedBoostActive ? 1.5 : 1.0) * (boost ? BOOST_SPEED : MAX_SPEED);

        if (left && !right) {
            velocityX = Math.max(velocityX - ACCELERATION * delta, -topSpeed);
            animator.setMirror(true);   // face left
        } else if (right && !left) {
            velocityX = Math.min(velocityX + ACCELERATION * delta, topSpeed);
            animator.setMirror(false);  // face right (natural direction in sheet)
        } else {
            // Friction — slide to a stop
            double friction = FRICTION * Math.abs(velocityX) * delta;
            velocityX = Math.abs(velocityX) <= friction
                    ? 0
                    : velocityX - Math.signum(velocityX) * friction;
        }

        // ── Animation state ──────────────────────────────────────────────────
        double speed = Math.abs(velocityX);
        if (speed > 300 && boost) {
            animator.play(SpriteAnimator.State.RUN);
            breatheAnim.stop(); animator.getView().setScaleY(1.0);
            
            // Juice: Dash Trails
            if (effects != null && System.currentTimeMillis() - lastTrailMs > 80) {
                lastTrailMs = System.currentTimeMillis();
                effects.dashTrail(charXPosition, charYPosition, animator.getDisplayWidth(), animator.getDisplayHeight(), velocityX > 0);
            }
        } else if (speed > 20) {
            animator.play(SpriteAnimator.State.WALK);
            breatheAnim.stop(); animator.getView().setScaleY(1.0);
        } else {
            animator.play(SpriteAnimator.State.IDLE);
            if (breatheAnim.getStatus() != Animation.Status.RUNNING) breatheAnim.play();
        }

        // ── Apply movement ───────────────────────────────────────────────────
        if      (velocityX < 0) moveLeft(-velocityX * delta);
        else if (velocityX > 0) moveRight(velocityX * delta);
    }

    /** Called by Dropper when the player catches a fruit — plays the Catch animation once. */
    public void triggerCatch() {
        animator.triggerCatch();
    }

    /**
     * Triggers a directional dash burst if not on cooldown.
     * The burst lasts DASH_DURATION_MS ms at DASH_SPEED px/s.
     * Input is locked during the burst — the player flies in dashRight direction.
     *
     * @param right  true = dash right, false = dash left
     * @return true if the dash was accepted, false if still on cooldown
     */
    public boolean triggerDash(boolean right) {
        long now = System.currentTimeMillis();
        if (now < dashCooldownEndMs) return false;   // still on cooldown

        isDashing         = true;
        dashRight         = right;
        dashEndMs         = now + DASH_DURATION_MS;
        dashCooldownEndMs = now + DASH_COOLDOWN_MS;
        velocityX         = right ? DASH_SPEED : -DASH_SPEED;
        animator.setMirror(!right);  // face the dash direction
        return true;
    }

    public boolean isDashAvailable()            { return System.currentTimeMillis() >= dashCooldownEndMs; }
    public boolean isDashing()                  { return isDashing; }
    public long    getDashCooldownRemainingMs() { return Math.max(0, dashCooldownEndMs - System.currentTimeMillis()); }

    // ─────────────────────────────────────────────────────────────────────────
    // Movement helpers (boundary-aware)
    // ─────────────────────────────────────────────────────────────────────────
    public void moveLeft(double amount) {
        double newX = charXPosition - amount;
        double half = animator.getDisplayWidth() / 2.0;
        if (newX >= -half) {
            charXPosition = newX;
            AnchorPane.setLeftAnchor(animator.getView(), charXPosition);
        } else {
            velocityX = 0;
        }
    }

    public void moveRight(double amount) {
        double newX = charXPosition + amount;
        double half = animator.getDisplayWidth() / 2.0;
        if (newX + animator.getDisplayWidth() <= MainGame.windowXSize + half) {
            charXPosition = newX;
            AnchorPane.setLeftAnchor(animator.getView(), charXPosition);
        } else {
            velocityX = 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collision bounds — expanded when Magnet power-up is active
    // ─────────────────────────────────────────────────────────────────────────
    public Bounds getBoundsInParent() {
        double bonus = magnetActive ? 130 : 0;
        double w = animator.getDisplayWidth()  * 0.65 + bonus * 2;
        // Cover 85% of the sprite height (skip the ~10% empty top padding)
        double h = animator.getDisplayHeight() * 0.85;
        double yOffset = animator.getDisplayHeight() * 0.10;
        return new BoundingBox(charXPosition - bonus, charYPosition + yOffset, w, h);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Power-ups
    // ─────────────────────────────────────────────────────────────────────────
    public void applySpeedBoost(double durationSeconds) {
        speedBoostActive = true;
        speedBoostEndMs  = System.currentTimeMillis() + (long)(durationSeconds * 1000);
    }

    public void applyMagnet(double durationSeconds) {
        magnetActive = true;
        magnetEndMs  = System.currentTimeMillis() + (long)(durationSeconds * 1000);
    }

    public void applyDoublePoints(double durationSeconds) {
        doublePointsActive = true;
        doublePointsEndMs  = System.currentTimeMillis() + (long)(durationSeconds * 1000);
    }

    /** Activates the shield — next trash catch is blocked with no life lost. */
    public void applyShield() {
        shieldActive = true;
    }

    /**
     * Attempts to consume the shield to block a trash hit.
     * @return true if shield was active (and is now consumed), false if no shield
     */
    public boolean consumeShield() {
        if (shieldActive) {
            shieldActive = false;
            return true;
        }
        return false;
    }

    // ── Queries ───────────────────────────────────────────────────────────────
    public boolean isSpeedBoostActive()    { return speedBoostActive   && System.currentTimeMillis() < speedBoostEndMs; }
    public boolean isMagnetActive()        { return magnetActive       && System.currentTimeMillis() < magnetEndMs; }
    public boolean isDoublePointsActive()  { return doublePointsActive && System.currentTimeMillis() < doublePointsEndMs; }
    public boolean isShieldActive()        { return shieldActive; }

    public long getSpeedBoostRemainingMs()   { return Math.max(0, speedBoostEndMs   - System.currentTimeMillis()); }
    public long getMagnetRemainingMs()       { return Math.max(0, magnetEndMs       - System.currentTimeMillis()); }
    public long getDoublePointsRemainingMs() { return Math.max(0, doublePointsEndMs - System.currentTimeMillis()); }

    /** Returns the extra multiplier from the Double Points power-up (1.0 or 2.0). */
    public double getDoublePointsMultiplier() {
        return isDoublePointsActive() ? 2.0 : 1.0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────
    public int       getHearts()        { return hearts; }
    public double getScore() { return score; }

    public void setEffectsManager(EffectsManager effects) {
        this.effects = effects;
    }

    /** Returns the ImageView driven by SpriteAnimator — add this to the scene. */
    public ImageView getCharacter()     { return animator.getView(); }
    public double    getCharXPosition() { return charXPosition; }
    public double    getCharYPosition() { return charYPosition; }
    public double    getWidthChar()     { return animator.getDisplayWidth(); }
    public double    getHeightChar()    { return animator.getDisplayHeight(); }

    public void addPoints(double pts)    { score += pts; }
    public void removePoints(double pts) { score -= pts; }
    public void addLives(int n)          { hearts = Math.min(3, Math.max(0, hearts + n)); }

    /** Debug helper — shows the collision box as a red outline. */
    public Rectangle createBoundingBoxVisual() {
        Bounds b = getBoundsInParent();
        Rectangle r = new Rectangle(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        r.setStroke(Color.RED); r.setFill(Color.TRANSPARENT);
        return r;
    }
}
