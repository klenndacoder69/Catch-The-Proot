package Game;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/*
* The LivesSystem class shall contain how the game will handle the user's lives.
* When a character catches 'trash', he will get a minus in the lives.
* The class also implements how the image will be rendered in the game.
* */
public class LivesSystem {

    private static double heartSizePercentage = 0.15;

    public LivesSystem(){

    }

    public static ImageView heartsRender(int countHearts) {
        Image heart1 = new Image("file:img/1_heart.png");
        Image heart2 = new Image("file:img/2_hearts.png");
        Image heart3 = new Image("file:img/3_hearts.png");
        Image gameover = new Image("file:img/Buttons Pixel Animation Pack/pause/343px/pause01.png");
        ImageView gameoverView = new ImageView(gameover);
        gameoverView.setVisible(true);
        ImageView showHeart1 = setSizePercentage(heart1);
        ImageView showHeart2 = setSizePercentage(heart2);
        ImageView showHeart3 = setSizePercentage(heart3);

        if (countHearts == 3) {
            return showHeart3;
        }
        if (countHearts == 2) {
            return showHeart2;
        }
        if (countHearts == 1) {
            return showHeart1;
        }

        return gameoverView;
    }

    private static ImageView setSizePercentage(Image asset) {
        ImageView assetImage = new ImageView(asset);
        assetImage.setFitWidth(asset.getWidth() * heartSizePercentage);
        assetImage.setFitHeight(asset.getHeight() * heartSizePercentage);
        assetImage.setLayoutX(10.0);
        assetImage.setLayoutY(10.0);
        return assetImage;
    }
}
