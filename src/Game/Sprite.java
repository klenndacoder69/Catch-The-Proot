package Game;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public abstract class Sprite {
    private ImageView imageView;

    public Sprite(String imagePath) {
        this.imageView = new ImageView(new Image(imagePath));
    }

    public ImageView getImageView() {
        return imageView;
    }

    public Bounds getBoundsInParent() {
        return imageView.getBoundsInParent();
    }
}
