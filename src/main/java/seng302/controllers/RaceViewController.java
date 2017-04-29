package seng302.controllers;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Duration;
import seng302.models.*;
import seng302.models.parsers.ConfigParser;
import seng302.models.parsers.StreamParser;

import java.io.IOException;
import java.util.*;

/**
 * Created by ptg19 on 29/03/17.
 */
public class RaceViewController extends Thread{
    @FXML
    private VBox positionVbox;
    @FXML
    private CheckBox toggleAnnotation, toggleFps;
    @FXML
    private Text timerLabel;
    @FXML
    private AnchorPane contentAnchorPane;
    @FXML
    private Text windArrowText, windDirectionText;
    @FXML
    private CanvasController includedCanvasController;

    private ArrayList<Boat> startingBoats = new ArrayList<>();
    private boolean displayAnnotations;
    private boolean displayFps;
    private Timeline timerTimeline;
    private Map<Boat, TimelineInfo> timelineInfos = new HashMap<>();
    private ArrayList<Boat> boatOrder = new ArrayList<>();
    private Race race;

    public void initialize() {

        RaceController raceController = new RaceController();
        raceController.initializeRace();
        race = raceController.getRace();
        for (Boat boat : race.getBoats()) {
            startingBoats.add(boat);
        }
//        try{
//            initializeTimelines();
//        }
//        catch (Exception e){
//            e.printStackTrace();
//        }

        includedCanvasController.setup(this);
        includedCanvasController.initializeCanvas();
        initializeTimer();
        initializeSettings();

        //set wind direction!!!!!!! can't find another place to put my code --haoming
        double windDirection = new ConfigParser("/config/config.xml").getWindDirection();
        windDirectionText.setText(String.format("%.1f°", windDirection));
        windArrowText.setRotate(windDirection);
        includedCanvasController.timer.start();
    }



    private void initializeSettings(){
        displayAnnotations = true;
        displayFps = true;

        toggleAnnotation.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                displayAnnotations = !displayAnnotations;
            }
        });
        toggleFps.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                displayFps = !displayFps;
            }
        });
    }

    private void initializeTimer(){
        timerTimeline = new Timeline();
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        // Run timer update every second
        timerTimeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(1),
                        event -> {
                            if (StreamParser.isRaceFinished()) {
                                timerLabel.setFill(Color.RED);
                                timerLabel.setText("Race Finished!");
                            } else {
                                timerLabel.setText(currentTimer());
                            }
                        })
        );

        // Start the timer
        timerTimeline.playFromStart();
    }

    /**
     * Generates time line for each boat, and stores time time into timelineInfos hash map
     */
    private void initializeTimelines() {
        HashMap<Boat, List> boat_events = race.getEvents();
        for (Boat boat : boat_events.keySet()) {
            startingBoats.add(boat);
//            // x, y are the real time coordinates
//            DoubleProperty x = new SimpleDoubleProperty();
//            DoubleProperty y = new SimpleDoubleProperty();
//
//            List<KeyFrame> keyFrames = new ArrayList<>();
//            List<Event> events = boat_events.get(boat);
//
//            // iterates all events and convert each event to keyFrame, then add them into a list
//            for (Event event : events) {
//                if (event.getIsFinishingEvent()) {
//                    keyFrames.add(
//                            new KeyFrame(Duration.seconds(event.getTime()),
//                                    onFinished -> {race.setBoatFinished(boat); handleEvent(event);},
//                                    new KeyValue(x, event.getThisMark().getLatitude()),
//                                    new KeyValue(y, event.getThisMark().getLongitude())
//                            )
//                    );
//                } else {
//                    keyFrames.add(
//                            new KeyFrame(Duration.seconds(event.getTime()),
//                                    onFinished ->{
//                                        handleEvent(event);
//                                        boat.setHeading(event.getBoatHeading());
//                                    },
//                                    new KeyValue(x, event.getThisMark().getLatitude()),
//                                    new KeyValue(y, event.getThisMark().getLongitude())
//                            )
//                    );
//                }
//            }
//            timelineInfos.put(boat, new TimelineInfo(new Timeline(keyFrames.toArray(new KeyFrame[keyFrames.size()])), x, y));
        }
        setRaceDuration();
    }

    private void setRaceDuration(){
        Double maxDuration = 0.0;
        Timeline maxTimeline = null;

        for (TimelineInfo timelineInfo : timelineInfos.values()) {

            Timeline timeline = timelineInfo.getTimeline();
            if (timeline.getTotalDuration().toMillis() >= maxDuration) {
                maxDuration = timeline.getTotalDuration().toMillis();
                maxTimeline = timeline;
            }

            // Timelines are paused by default
            timeline.play();
            timeline.pause();
        }

        maxTimeline.setOnFinished(event -> {
            race.setRaceFinished();
            loadRaceResultView();
        });
    }

    /**
     * Play each boats timerTimeline
     */
    public void playTimelines(){
        for (TimelineInfo timelineInfo : timelineInfos.values()){
            Timeline timeline = timelineInfo.getTimeline();

            if (timeline.getStatus() == Animation.Status.PAUSED){
                timeline.play();
            }
        }
    }

    /**
     * Pause each boats timerTimeline
     */
    public void pauseTimelines(){
        for (TimelineInfo timelineInfo : timelineInfos.values()){
            Timeline timeline = timelineInfo.getTimeline();

            if (timeline.getStatus() == Animation.Status.RUNNING){
                timeline.pause();
            }
        }
    }

    /**
     * Display the list of boats in the order they finished the race
     */
    private void loadRaceResultView() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/FinishView.fxml"));
        loader.setController(new RaceResultController(race));

        try {
            contentAnchorPane.getChildren().removeAll();
            contentAnchorPane.getChildren().clear();
            contentAnchorPane.getChildren().addAll((Pane) loader.load());

        } catch (javafx.fxml.LoadException e) {
            System.err.println(e.getCause());
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public void handleEvent(Event event) {
        Boat boat = event.getBoat();
        boatOrder.remove(boat);
        boat.setMarkLastPast(event.getMarkPosInRace());
        boatOrder.add(boat);
        boatOrder.sort(new Comparator<Boat>() {
            @Override
            public int compare(Boat b1, Boat b2) {
                return b2.getMarkLastPast() - b1.getMarkLastPast();
            }
        });
        showOrder();
    }

    private void showOrder() {
        positionVbox.getChildren().clear();
        positionVbox.getChildren().removeAll();

        for (Boat boat : boatOrder) {
            positionVbox.getChildren().add(new Text(boat.getShortName() + " " + boat.getSpeedInKnots() + " Knots"));
        }
    }

    /**
     * Convert seconds to a string of the format mm:ss
     *
     * @param time the time in seconds
     * @return a formatted string
     */
    public String convertTimeToMinutesSeconds(int time) {
        if (time < 0) {
            return String.format("-%02d:%02d", (time * -1) / 60, (time * -1) % 60);
        }
        return String.format("%02d:%02d", time / 60, time % 60);
    }

    private String currentTimer() {
        String timerString = "0:00 minutes";
        if (StreamParser.getTimeSinceStart() > 0 && StreamParser.getTimeSinceStart() % 10 == 0) {
            Long timerMinute = StreamParser.getTimeSinceStart() / 60;
            Long timerSecond = StreamParser.getTimeSinceStart() % 60;
            timerString = "-" + timerMinute + "." + timerSecond + " minutes";
        } else if (StreamParser.getTimeSinceStart() % 10 == 0) {
            Long timerMinute = -1 * StreamParser.getTimeSinceStart() / 60;
            Long timerSecond = -1 * StreamParser.getTimeSinceStart() % 60;
            timerString = timerMinute + "." + timerSecond + " minutes";
        }
        return timerString;
    }

    public void stopTimer() {
        timerTimeline.stop();
    }
    public void startTimer() {
        timerTimeline.play();
    }

    public boolean isDisplayFps() {
        return displayFps;
    }

    public boolean isDisplayAnnotations() {
        return displayAnnotations;
    }

    public Race getRace() {
        return race;
    }

    public Map<Boat, TimelineInfo> getTimelineInfos() {
        return timelineInfos;
    }

    public ArrayList<Boat> getStartingBoats(){
        return startingBoats;
    }

    @FXML
    private void toggleAnnotations () {
        for (RaceObject ro : includedCanvasController.getRaceObjects()) {
            ro.toggleAnnotations();
        }
    }
}