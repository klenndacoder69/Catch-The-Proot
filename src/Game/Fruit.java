package Game;

import javafx.geometry.BoundingBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/*
The fruit class contains all the necessary attributes of a fruit. There are many kinds of fruit in this case we will be using three fruits
(Watermelon, Apple, Orange). These fruits have a pointing system where:
Apple - 20
Orange - 50
Watermelon - 100
 */
public class Fruit extends Sprite {
    //Attributes of a fruit:
    private double points; //indicates the number of points the player receive when caught.//the image of the fruit
    private int lives;
    public Fruit(String imagePath,double points, int lives){
        super(imagePath);
        this.points = points;
        this.lives = lives;
    }



    public double getPoints(){
        return points;
    }

//    public BoundingBox getBoundsInParent(){
//        double reducedHeight = fruit_image.getHeight() * 0.8;
//        double reducedWidth - fruit_image.getWidth() * 0.6;
//        return()
//    }

    public int getLives(){
        return lives;
    }
}

