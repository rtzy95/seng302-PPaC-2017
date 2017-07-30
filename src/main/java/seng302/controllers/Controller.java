//package seng302.controllers;
//
//import java.io.IOException;
//import java.net.URL;
//import java.util.ResourceBundle;
//import javafx.fxml.FXML;
//import javafx.fxml.FXMLLoader;
//import javafx.fxml.Initializable;
//import javafx.scene.Parent;
//import javafx.scene.input.KeyEvent;
//import javafx.scene.layout.AnchorPane;
//import seng302.client.ClientPacketParser;
//import seng302.client.ClientState;
//import seng302.client.ClientToServerThread;
//import seng302.server.messages.BoatActionMessage;
//import seng302.server.messages.BoatActionType;
//
//public class Controller implements Initializable {
//
//    @FXML
//    private AnchorPane contentPane;
//    private ClientToServerThread clientToServerThread;
//    private long lastSendingTime;
//    private int KEY_STROKE_SENDING_FREQUENCY = 50;
//
//    public Object setContentPane(String jfxUrl) {
//        try {
//            contentPane.getChildren().removeAll();
//            contentPane.getChildren().clear();
//            contentPane.getStylesheets().add(getClass().getResource("/css/master.css").toString());
//            FXMLLoader fxmlLoader = new FXMLLoader((getClass().getResource(jfxUrl)));
//            Parent view = fxmlLoader.load();
//            contentPane.getChildren().addAll(view);
//            return fxmlLoader.getController();
//        } catch (javafx.fxml.LoadException e) {
//            System.err.println(e.getCause());
//        } catch (IOException e) {
//            System.err.println(e);
//        }
//        return null;
//    }
//
//    @Override
//    public void initialize(URL location, ResourceBundle resources) {
//        setUpStartScreen();
//        lastSendingTime = System.currentTimeMillis();
//    }
//
//    void setUpStartScreen() {
//        contentPane.getChildren().removeAll();
//        contentPane.getChildren().clear();
//        contentPane.getStylesheets().add(getClass().getResource("/css/master.css").toString());
//        StartScreenController startScreenController = (StartScreenController) setContentPane("/views/StartScreenView.fxml");
//        startScreenController.setController(this);
//        ClientPacketParser.boatLocations.clear();
//    }
//
//
//    /** Handle the key-pressed event from the text field. */
//    public void keyPressed(KeyEvent e) {
//        BoatActionMessage boatActionMessage;
//        long currentTime = System.currentTimeMillis();
//        if (currentTime - lastSendingTime > KEY_STROKE_SENDING_FREQUENCY && ClientState.isRaceStarted()) {
//            lastSendingTime = currentTime;
//            switch (e.getCode()) {
//                case SPACE: // align with vmg
//                    boatActionMessage = new BoatActionMessage(BoatActionType.VMG);
//                    clientToServerThread.sendBoatActionMessage(boatActionMessage);
//                    break;
//                case PAGE_UP: // upwind
//                    boatActionMessage = new BoatActionMessage(BoatActionType.UPWIND);
//                    clientToServerThread.sendBoatActionMessage(boatActionMessage);
//                    break;
//                case PAGE_DOWN: // downwind
//                    boatActionMessage = new BoatActionMessage(BoatActionType.DOWNWIND);
//                    clientToServerThread.sendBoatActionMessage(boatActionMessage);
//                    break;
//                case ENTER: // tack/gybe
//                    boatActionMessage = new BoatActionMessage(BoatActionType.TACK_GYBE);
//                    clientToServerThread.sendBoatActionMessage(boatActionMessage);
//                    break;
//                //TODO Allow a zoom in and zoom out methods
//                case Z:  // zoom in
//                    System.out.println("Zoom in");
//                    break;
//                case X:  // zoom out
//                    System.out.println("Zoom out");
//                    break;
//            }
//        }
//    }
//
//    public void keyReleased(KeyEvent e) {
//        switch (e.getCode()) {
//            //TODO 12/07/17 Determine the sail state and send the appropriate packet (eg. if sails are in, send a sail out packet)
//            case SHIFT:  // sails in/sails out
//                BoatActionMessage boatActionMessage = new BoatActionMessage(BoatActionType.SAILS_IN);
//                clientToServerThread.sendBoatActionMessage(boatActionMessage);
//                break;
//        }
//    }
//
//    public void setClientToServerThread(ClientToServerThread ctt) {
//        clientToServerThread = ctt;
//    }
//}
