package Game;

/**
 * Public factory that lets classes outside the Game package (e.g. Levels.tDropper)
 * obtain pre-built Fruit arrays without needing direct access to the package-private
 * fruit subclasses defined in dFruits.java.
 *
 * Java rule: only one public class per .java file — so we expose fruit construction
 * here through static factory methods instead of making every subclass public.
 */
public class FruitFactory {

    /**
     * Fruits used in the Tutorial — all good fruits, no trash.
     * Gives a representative variety without overwhelming a new player.
     */
    public static Fruit[] getTutorialFruits() {
        return new Fruit[]{
            new Apple(), new Banana(), new Cherry(),
            new Lemon(), new Pear(),   new Orange(),
            new Strawberry(), new Peach()
        };
    }

    /** Level 1 — all good fruits, no trash. */
    public static Fruit[] getLevel1Fruits() {
        return new Fruit[]{
            new Apple(), new Banana(), new Cherry(),
            new Lemon(), new Pear(),   new Orange(),
            new Grape(), new Watermelon(), new Strawberry(),
            new Peach(), new Plum(), new Pineapple(), new Coconut()
        };
    }

    /** Level 2 — all fruits + a handful of trash types. */
    public static Fruit[] getLevel2Fruits() {
        return getLevel1Fruits();
    }

    public static Fruit[] getLevel2Trash() {
        return new Fruit[]{
            new Trash(), new TrashBottle(), new TrashBox(),
            new TrashBread(), new TrashCup(), new TrashLeaf()
        };
    }

    /** Level 3 — all fruits + full trash pool. */
    public static Fruit[] getLevel3Fruits() {
        return getLevel1Fruits();
    }

    public static Fruit[] getLevel3Trash() {
        return new Fruit[]{
            new Trash(), new TrashBone(), new TrashBottle(),
            new TrashBox(), new TrashBread(), new TrashCheese(),
            new TrashCup(), new TrashLeaf(), new TrashNail(), new TrashPlastic()
        };
    }
}
