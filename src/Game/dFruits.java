package Game;

// ─── Good Fruits ─────────────────────────────────────────────────────────────

class Apple extends Fruit {
    public Apple() { super("file:img/fruits/apple.png",  20, 0); }
}

class Banana extends Fruit {
    public Banana() { super("file:img/fruits/banana.png", 30, 0); }
}

class Cherry extends Fruit {
    public Cherry() { super("file:img/fruits/cherry.png", 50, 0); }
}

class Lemon extends Fruit {
    public Lemon() { super("file:img/fruits/01.png", 25, 0); }
}

class Pear extends Fruit {
    public Pear() { super("file:img/fruits/02.png", 35, 0); }
}

class Orange extends Fruit {
    public Orange() { super("file:img/fruits/06.png", 40, 0); }
}

class Grape extends Fruit {
    public Grape() { super("file:img/fruits/07.png", 60, 0); }
}

class Watermelon extends Fruit {
    public Watermelon() { super("file:img/fruits/08.png", 100, 0); }
}

class Strawberry extends Fruit {
    public Strawberry() { super("file:img/fruits/09.png", 45, 0); }
}

class Peach extends Fruit {
    public Peach() { super("file:img/fruits/10.png", 35, 0); }
}

class Plum extends Fruit {
    public Plum() { super("file:img/fruits/11.png", 45, 0); }
}

class Pineapple extends Fruit {
    public Pineapple() { super("file:img/fruits/12.png", 80, 0); }
}

class Coconut extends Fruit {
    public Coconut() { super("file:img/fruits/13.png", 70, 0); }
}

// ─── Trash (loses 1 life on catch) ───────────────────────────────────────────

class Trash extends Fruit {
    public Trash() { super("file:img/Standard Trash/trash_apple.png",  -20, -1); }
}

class TrashBone extends Fruit {
    public TrashBone() { super("file:img/Standard Trash/trash_bone.png",    -30, -1); }
}

class TrashBottle extends Fruit {
    public TrashBottle() { super("file:img/Standard Trash/trash_bottle.png", -25, -1); }
}

class TrashBox extends Fruit {
    public TrashBox() { super("file:img/Standard Trash/trash_box.png",     -15, -1); }
}

class TrashBread extends Fruit {
    public TrashBread() { super("file:img/Standard Trash/trash_bread.png",   -15, -1); }
}

class TrashCheese extends Fruit {
    public TrashCheese() { super("file:img/Standard Trash/trash_chese.png",   -25, -1); }
}

class TrashCup extends Fruit {
    public TrashCup() { super("file:img/Standard Trash/trash_cup.png",     -10, -1); }
}

class TrashLeaf extends Fruit {
    public TrashLeaf() { super("file:img/Standard Trash/trash_leaf.png",    -5,  -1); }
}

class TrashNail extends Fruit {
    public TrashNail() { super("file:img/Standard Trash/trash_nail.png",    -50, -1); }
}

class TrashPlastic extends Fruit {
    public TrashPlastic() { super("file:img/Standard Trash/trash_plastic.png", -20, -1); }
}
