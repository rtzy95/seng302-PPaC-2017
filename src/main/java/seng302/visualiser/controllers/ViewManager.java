package seng302.visualiser.controllers;

import com.jfoenix.controls.JFXDecorator;
import com.jfoenix.controls.JFXDialog;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seng302.gameServer.ServerAdvertiser;
import seng302.utilities.BonjourInstallChecker;
import seng302.utilities.Sounds;
import seng302.visualiser.GameClient;
import seng302.visualiser.controllers.dialogs.BoatCustomizeController;

import java.io.IOException;
import java.util.HashMap;

public class ViewManager {

    private static ViewManager instance;
    private GameClient gameClient;
    private JFXDecorator decorator;
    private HashMap<String, String> properties; //TODO is this the best way to do this??
    private ObservableList<String> playerList;
    private Logger logger = LoggerFactory.getLogger(ViewManager.class);

    public Stage getStage() {
        return stage;
    }

    private Stage stage;

    private ViewManager() {
        properties = new HashMap<>();
    }

    private FXMLLoader loadFxml(String fxmlLocation) {
        return new FXMLLoader(
            getClass().getResource(fxmlLocation)
        );
    }

    public static ViewManager getInstance() {
        if (instance == null) {
            instance = new ViewManager();
        }

        return instance;
    }

    /**
     * Initialize the start view in the given stage.
     */
    public void initialStartView(Stage stage) throws Exception {
        this.stage = stage;
        Parent root = FXMLLoader.load(getClass().getResource("/views/StartScreenView.fxml"));
        stage.setTitle("Party Parrots At Sea");

        JFXDecorator decorator = new JFXDecorator(stage, root, false, true, true);
        decorator.setCustomMaximize(true);
        decorator.applyCss();
        decorator.getStylesheets()
            .add(getClass().getResource("/css/master.css").toExternalForm());

        this.decorator = decorator;
        gameClient = new GameClient(decorator);

        stage.getIcons().add(new Image(getClass().getResourceAsStream("/PP.png")));
        Scene scene = new Scene(decorator, 1200, 800);
        stage.setMinHeight(800);
        stage.setMinWidth(1200);
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> closeAll());

        decorator.setOnCloseButtonAction(this::closeAll);

        // TODO Platform.runLater(this::checkCompatibility);

        Sounds.stopMusic();
        Sounds.playMenuMusic();

        decorator.setOnCloseButtonAction(() -> {
            try {
                ServerAdvertiser.getInstance().unregister();
            } catch (IOException e) {
                logger.warn("Couldn't unregister server");
            }

            gameClient.stopGame();
            System.exit(0);
        });
    }

    private void checkCompatibility() {
        if (BonjourInstallChecker.isBonjourSupported()) {
            BonjourInstallChecker.openInstallUrl();
        }
    }

    private void closeAll() {
        try {
            ServerAdvertiser.getInstance().unregister();
        } catch (IOException e1) {
            logger.warn("Could not un-register game");
        }

        System.exit(0);
    }

    public JFXDecorator getDecorator() {
        return decorator;
    }

    public void setScene(Node scene) {
        Platform.runLater(() -> decorator.setContent(scene));
    }

    /**
     * Create a new stage and re-initialize the start view in the new stage.
     */
    public void goToStartView() {
        try {
            this.stage.close();
            Stage stage = new Stage();
            initialStartView(stage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, String val) {
        properties.put(key, val);
    }

    public void setPlayerList(ObservableList<String> playerList) {
        this.playerList = playerList;
    }

    public ObservableList<String> getPlayerList() {
        return playerList;
    }

    public LobbyController goToLobby(Boolean disableReadyButton) {
        FXMLLoader loader = loadFxml("/views/LobbyView.fxml");

        try {
            setScene(loader.load());
        } catch (IOException e) {
            logger.error("Could not load lobby view");
        }

        if (disableReadyButton) {
            LobbyController lobbyController = loader.getController();
            lobbyController.disableReadyButton();
        }

        return loader.getController();
    }

    public RaceViewController loadRaceView() {
        FXMLLoader loader = loadFxml("/views/RaceView.fxml");

        // have to create a new stage and set the race view maximized as JFoenix decorator has
        // bug causes stage cannot be fully maximised.
        Platform.runLater(() -> {
            try {
                stage.close();
                stage = new Stage();

                JFXDecorator decorator = new JFXDecorator(stage, loader.load(), false, true, true);
                decorator.setCustomMaximize(true);
                decorator.applyCss();
                decorator.getStylesheets()
                    .add(getClass().getResource("/css/master.css").toExternalForm());

                Scene scene = new Scene(decorator);
                // set key press event to catch key stoke
                scene.setOnKeyPressed(gameClient::keyPressed);
                scene.setOnKeyReleased(gameClient::keyReleased);

                // uncomment to make it full screen
//                Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
//                stage.setX(visualBounds.getMinX());
//                stage.setY(visualBounds.getMinY());
//                stage.setWidth(visualBounds.getWidth());
//                stage.setHeight(visualBounds.getHeight());
//                stage.setMaximized(true);
//                stage.setFullScreen(true);

                stage.setMinHeight(500);
                stage.setMinWidth(800);
                stage.setOnCloseRequest(e -> closeAll());
                stage.setScene(scene);
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        while (loader.getController() == null){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return loader.getController();
    }

    public JFXDialog loadCustomizationDialog(StackPane parent, LobbyController lobbyController,
        Color playerColor, String name) {
        FXMLLoader dialog = loadFxml("/views/dialogs/BoatCustomizeDialog.fxml");

        JFXDialog customizationDialog = null;

        try {
            customizationDialog = new JFXDialog(parent, dialog.load(),
                JFXDialog.DialogTransition.CENTER);

        } catch (IOException e) {
            e.printStackTrace();
        }

        BoatCustomizeController controller = dialog.getController();

        controller.setParentController(lobbyController);
        controller.setPlayerColor(playerColor);
        controller.setPlayerName(name);
        controller.setServerThread(gameClient.getServerThread());
        controller.setPlayerColor(lobbyController.playersColor);

        return customizationDialog;
    }
}