package Levels;

import Game.Fruit;

//dFruits class contain the different fruits with its corresponding points
class Apple extends Fruit {
    public Apple(){
        super("file:img/fruits/apple.png",20,  0);
    }
}

class Banana extends Fruit {
    public Banana(){
        super("file:img/fruits/banana.png",50,  0);
    }
}

class Cherry extends Fruit {
    public Cherry(){
        super("file:img/fruits/cherry.png",100,  0);
    }
}

class Trash extends Fruit {
    public Trash(){
        super("file:img/Standard Trash/trash_apple.png",-100,  -1);
    }
}
