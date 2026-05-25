package Game;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class TimelineExample extends Application {
    @Override
    public void start(Stage primaryStage) {
        Rectangle rectangle = new Rectangle(50, 50, Color.BLUE);
        StackPane root = new StackPane(rectangle);
        Scene scene = new Scene(root, 200, 200);

        // Create a Timeline with a KeyFrame
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
            // Define actions to be performed at this point in time (2 seconds)
            rectangle.setTranslateX(rectangle.getTranslateX() + 50); // Move the rectangle
        }));

        timeline.setCycleCount(Timeline.INDEFINITE); // Repeat indefinitely
        timeline.play(); // Start the animation

        primaryStage.setScene(scene);
        primaryStage.setTitle("Timeline Example");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}