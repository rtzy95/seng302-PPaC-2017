package seng302.visualiser.controllers.dialogs;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.RequiredFieldValidator;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import seng302.gameServer.ServerDescription;
import seng302.utilities.Sounds;
import seng302.visualiser.MapMaker;
import seng302.visualiser.controllers.ServerListController.ServerCreationDialogListener;
import seng302.visualiser.controllers.ViewManager;
import seng302.visualiser.validators.FieldLengthValidator;
import seng302.visualiser.validators.ValidationTools;

public class ServerCreationController implements Initializable {

    //--------FXML BEGIN--------//
    @FXML
    private JFXTextField serverName;
    @FXML
    private JFXSlider maxPlayersSlider;
    @FXML
    private Label maxPlayersLabel;
    @FXML
    private JFXButton submitBtn;
    @FXML
    private Label closeLabel;
    @FXML
    private JFXButton nextMapButton;
    @FXML
    private JFXButton lastMapButton;
    @FXML
    private Label mapNameLabel;
    @FXML
    private JFXSlider legsSlider;
    @FXML
    private Label legsSliderLabel;
    @FXML
    private JFXCheckBox pickupsCheckBox;
    @FXML
    private AnchorPane mapHolder;

    private MapMaker mapMaker = MapMaker.getInstance();

    //---------FXML END---------//

    private List<ServerCreationDialogListener> serverCreationDialogListeners;

    public void initialize(URL location, ResourceBundle resources) {
        legsSlider.setMax(10);
        legsSlider.setValue(4);
        maxPlayersSlider.valueProperty().addListener(
            (observable, oldValue, newValue) -> updateMaxPlayerLabel()
        );
        legsSlider.valueProperty().addListener(
            (obs, oldVal, newVal) -> updateLegSliderLabel()
        );
        updateMaxPlayerLabel();
        updateLegSliderLabel();

        FieldLengthValidator fieldLengthValidator = new FieldLengthValidator(40);
        fieldLengthValidator.setMessage("Server name too long.");

        RequiredFieldValidator fieldRequiredValidator = new RequiredFieldValidator();
        fieldRequiredValidator.setMessage("Server name is required.");

        serverName.setValidators(fieldLengthValidator, fieldRequiredValidator);

        submitBtn.setOnMouseReleased(event -> {
            Sounds.playButtonClick();
            validateServerSettings();
        });

        nextMapButton.setOnMouseReleased(event -> {
            Sounds.playButtonClick();
            nextMap();
        });

        lastMapButton.setOnMouseReleased(event -> {
            Sounds.playButtonClick();
            lastMap();
        });

        mapHolder.getChildren().setAll(mapMaker.getCurrentGameView());
        mapNameLabel.setText(mapMaker.getCurrentRegatta().getCourseName());
        //closeLabel.setOnMouseClicked(event -> notifyListeners());
    }

    /**
     * Validates that a server has a valid name and creates the server.
     */
    private void validateServerSettings() {
        submitBtn.setText("CREATING...");
        if (ValidationTools.validateTextField(serverName)) {
            createServer();
        } else {
            submitBtn.setText("SUBMIT");
        }
    }

    /**
     * Creates a server with a given set of details.
     */
    private void createServer() {
        ServerDescription serverDescription = ViewManager.getInstance().getGameClient()
            .runAsHost("localhost", 4941, serverName.getText(), (int) maxPlayersSlider
                .getValue(), mapMaker.getCurrentRacePath(), (int) legsSlider.getValue());

        ViewManager.getInstance().setProperty("serverName", serverDescription.getName());
        ViewManager.getInstance().setProperty("mapName", serverDescription.getMapName());
    }

    /**
     * Updates a label as the user slides along the max players slider.
     */
    private void updateMaxPlayerLabel() {
        maxPlayersSlider.setValue(Math.floor(maxPlayersSlider.getValue()));
        maxPlayersLabel.setText(String.format("Max players: %.0f", maxPlayersSlider.getValue()));
    }

    private void updateLegSliderLabel() {
        legsSlider.setValue(Math.floor(legsSlider.getValue()));
        legsSliderLabel.setText(
            String.format("A section of the race will repeat %.0f times", legsSlider.getValue())
        );

    }

    public void playButtonHoverSound(MouseEvent mouseEvent) {
        Sounds.playHoverSound();
    }

    private void nextMap() {
        mapMaker.next();
        mapHolder.getChildren().setAll(mapMaker.getCurrentGameView());
        mapNameLabel.setText(mapMaker.getCurrentRegatta().getCourseName());
    }

    private void lastMap() {
        mapMaker.previous();
        mapHolder.getChildren().setAll(mapMaker.getCurrentGameView());
        mapNameLabel.setText(mapMaker.getCurrentRegatta().getCourseName());
    }

    public void setListener(List<ServerCreationDialogListener> serverCreationDialogListeners) {
        this.serverCreationDialogListeners = serverCreationDialogListeners;
    }

    public void notifyListeners() {
        for (ServerCreationDialogListener serverCreationDialogListener : serverCreationDialogListeners) {
            serverCreationDialogListener.notifyClosure();
        }
    }

}
