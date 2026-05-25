package Game;

/**
 * A power-up that falls like a fruit.
 * When caught, it applies a temporary (or one-shot) benefit to the player.
 *
 * Types:
 *   SPEED_BOOST   — player moves 1.5× faster for 5 seconds
 *   MAGNET        — player hitbox widens by 130px each side for 4 seconds
 *   EXTRA_LIFE    — instantly restores one heart (capped at 3)
 *   BOMB          — blasts every currently-falling fruit off the screen (+50 pts each)
 *   DOUBLE_POINTS — all catches score 2× for 8 seconds (stacks with combo)
 *   FREEZE        — slows all falling items to 20% speed for 5 seconds
 *   SHIELD        — next trash catch deals no damage (one-shot block)
 */
public class PowerUp extends Fruit {

    public enum Type { SPEED_BOOST, MAGNET, EXTRA_LIFE, BOMB, DOUBLE_POINTS, FREEZE, SHIELD }

    private final Type type;

    public PowerUp(Type type) {
        super(imagePath(type), 0, 0);
        this.type = type;
    }

    public Type getType() { return type; }

    public String getDisplayText() {
        return switch (type) {
            case SPEED_BOOST   -> "\u26a1 Speed Boost!";
            case MAGNET        -> "\uD83E\uDDF2 Magnet!";
            case EXTRA_LIFE    -> "\u2764 Extra Life!";
            case BOMB          -> "\uD83D\uDCA3 BOOM!";
            case DOUBLE_POINTS -> "\u2B50 2\u00d7 Points!";
            case FREEZE        -> "\u2744 FREEZE!";
            case SHIELD        -> "\uD83D\uDEE1 Shield!";
        };
    }

    private static String imagePath(Type t) {
        return switch (t) {
            case SPEED_BOOST   -> "file:img/speed_boost.png";
            case MAGNET        -> "file:img/basket.png";
            case EXTRA_LIFE    -> "file:img/1_heart.png";
            case BOMB          -> "file:img/bomb.png";
            case DOUBLE_POINTS -> "file:img/double_points.png";
            case FREEZE        -> "file:img/freeze.png";
            case SHIELD        -> "file:img/shield.png";
        };
    }
}
