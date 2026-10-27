package ru.memory.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;

public final class MemoryFxApplication extends Application {
    private static final String[] FONT_RESOURCES = {
            "/ru/memory/fx/fonts/GolosText-Regular.ttf",
            "/ru/memory/fx/fonts/GolosText-Medium.ttf",
            "/ru/memory/fx/fonts/GolosText-SemiBold.ttf",
            "/ru/memory/fx/fonts/GolosText-Bold.ttf"
    };

    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        loadFonts();
        FXMLLoader loader = new FXMLLoader(MemoryFxApplication.class.getResource("/ru/memory/fx/main-view.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(MemoryFxApplication.class.getResource("/ru/memory/fx/theme.css").toExternalForm());

        stage.setTitle("Память | Управление заказами");
        stage.setMinWidth(1120);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) controller.shutdown();
    }

    private static void loadFonts() {
        for (String resource : FONT_RESOURCES) {
            var url = MemoryFxApplication.class.getResource(resource);
            if (url != null) Font.loadFont(url.toExternalForm(), 14);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
