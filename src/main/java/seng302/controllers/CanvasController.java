package seng302.controllers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import javafx.animation.AnimationTimer;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import seng302.fxObjects.BoatGroup;
import seng302.models.Colors;
import seng302.models.Yacht;
import seng302.models.mark.GateMark;
import seng302.models.mark.Mark;
import seng302.fxObjects.MarkGroup;
import seng302.models.mark.MarkType;
import seng302.models.mark.SingleMark;
import seng302.models.stream.StreamParser;
import seng302.models.stream.XMLParser;
import seng302.models.stream.XMLParser.RaceXMLObject.Limit;
import seng302.models.stream.XMLParser.RaceXMLObject.Participant;
import seng302.models.stream.packets.BoatPositionPacket;

/**
 * Created by ptg19 on 15/03/17.
 * Modified by Haoming Yin (hyi25) on 20/3/2017.
 */
public class CanvasController {

    @FXML
    private AnchorPane canvasPane;

    private RaceViewController raceViewController;
    private ResizableCanvas canvas;
    private Group group;
    private GraphicsContext gc;

    private final int MARK_SIZE     = 10;
    private final int BUFFER_SIZE   = 50;
    private final int CANVAS_WIDTH  = 720;
    private final int CANVAS_HEIGHT = 720;
    private final int LHS_BUFFER    = BUFFER_SIZE;
    private final int RHS_BUFFER    = BUFFER_SIZE + MARK_SIZE / 2;
    private final int TOP_BUFFER    = BUFFER_SIZE;
    private final int BOT_BUFFER    = TOP_BUFFER + MARK_SIZE / 2;
    private boolean horizontalInversion = false;

    private double distanceScaleFactor;
    private ScaleDirection scaleDirection;
    private Mark minLatPoint;
    private Mark minLonPoint;
    private Mark maxLatPoint;
    private Mark maxLonPoint;
    private double referencePointX;
    private double referencePointY;

    private List<MarkGroup> markGroups = new ArrayList<>();
    private List<BoatGroup> boatGroups = new ArrayList<>();
    private Text FPSdisplay = new Text();
    private Polygon raceBorder = new Polygon();

    //FRAME RATE
    private Double frameRate = 60.0;
    private final long[] frameTimes = new long[30];
    private int frameTimeIndex = 0;
    private boolean arrayFilled = false;

    public AnimationTimer timer;

    private enum ScaleDirection {
        HORIZONTAL,
        VERTICAL
    }

    public void setup(RaceViewController raceViewController){
        this.raceViewController = raceViewController;
    }

    public void initialize() {
        raceViewController = new RaceViewController();
        canvas = new ResizableCanvas();
        group = new Group();

        canvasPane.getChildren().add(canvas);
        canvasPane.getChildren().add(group);
        // Bind canvas size to stack pane size.
        canvas.widthProperty().bind(new SimpleDoubleProperty(CANVAS_WIDTH));
        canvas.heightProperty().bind(new SimpleDoubleProperty(CANVAS_HEIGHT));
    }

    public void initializeCanvas (){

        gc = canvas.getGraphicsContext2D();
        gc.save();
        gc.setFill(Color.SKYBLUE);
        gc.fillRect(0,0, CANVAS_WIDTH, CANVAS_HEIGHT);
        gc.restore();
        fitMarksToCanvas();
        FPSdisplay.setLayoutX(5);
        FPSdisplay.setLayoutY(20);
        FPSdisplay.setStrokeWidth(2);
        group.getChildren().add(FPSdisplay);
        group.getChildren().add(raceBorder);


        // TODO: 1/05/17 wmu16 - Change this call to now draw the marks as from the xml
        initializeBoats();
        initializeMarks();
        timer = new AnimationTimer() {

            private long lastTime = 0;

            @Override
            public void handle(long now) {
                //fps stuff
                if (lastTime == 0) {
                   lastTime = now;
                } else {
                    if (now - lastTime >= (1e8 / 60)) { //Fix for framerate going above 60 when minimized
                        long oldFrameTime = frameTimes[frameTimeIndex] ;
                        frameTimes[frameTimeIndex] = now ;
                        frameTimeIndex = (frameTimeIndex + 1) % frameTimes.length ;
                        if (frameTimeIndex == 0) {
                            arrayFilled = true ;
                        }
                        long elapsedNanos;
                        if (arrayFilled) {
                            elapsedNanos = now - oldFrameTime ;
                            long elapsedNanosPerFrame = elapsedNanos / frameTimes.length ;
                            frameRate = 1_000_000_000.0 / elapsedNanosPerFrame ;
                            drawFps(frameRate.intValue());
                        }
                        updateGroups(frameRate);
                        if (StreamParser.isRaceFinished()) {
                            this.stop();
                        }
                        lastTime = now;
                    }
                }
            }
        };
    }


    /**
     * Adds border marks to the canvas, taken from the XML file
     *
     * NOTE: This is quite confusing as objects are grabbed from the XMLParser such as Mark and CompoundMark which are
     * named the same as those in the model package but are, however not the same, so they do not have things such as
     * a type and must be derived from the number of marks in a compound mark etc..
     */
    private void addRaceBorder() {
        XMLParser.RaceXMLObject raceXMLObject = StreamParser.getXmlObject().getRaceXML();
        ArrayList<Limit> courseLimits = raceXMLObject.getCourseLimit();
        raceBorder.setStroke(new Color(0.0f, 0.0f, 0.74509807f, 1));
        raceBorder.setStrokeWidth(3);
        raceBorder.setFill(new Color(0,0,0,0));
        List<Double> boundaryPoints = new ArrayList<>();
        for (Limit limit : courseLimits) {
            Point2D location = findScaledXY(limit.getLat(), limit.getLng());
            boundaryPoints.add(location.getX());
            boundaryPoints.add(location.getY());
        }
        raceBorder.getPoints().setAll(boundaryPoints);
    }

    private void updateGroups(double frameRate){
        for (BoatGroup boatGroup : boatGroups) {
            // some raceObjects will have multiple ID's (for instance gate marks)
            //checking if the current "ID" has any updates associated with it
            if (StreamParser.boatPositions.containsKey(boatGroup.getRaceId())) {
                if (boatGroup.isStopped()) {
                    updateBoatGroup(boatGroup);
                }
            }
            boatGroup.move();
        }
        for (MarkGroup markGroup : markGroups) {
            for (Long id : markGroup.getRaceIds()) {
                if (StreamParser.markPositions.containsKey(id)) {
                    updateMarkGroup(id, markGroup);
                }
            }
        }
        checkForCourseChanges();
    }

    private void checkForCourseChanges() {
        if (StreamParser.isNewRaceXmlReceived()){
            gc.setFill(Color.SKYBLUE);
            gc.fillRect(0,0, CANVAS_WIDTH, CANVAS_HEIGHT);
            gc.restore();
            addRaceBorder();
            canvas.toBack();
        }
    }

    private void updateBoatGroup(BoatGroup boatGroup) {
        PriorityBlockingQueue<BoatPositionPacket> movementQueue = StreamParser.boatPositions.get(boatGroup.getRaceId());
        // giving the movementQueue a 5 packet buffer to account for slightly out of order packets
        if (movementQueue.size() > 0){
            try {
                BoatPositionPacket positionPacket = movementQueue.take();
                Point2D p2d = findScaledXY(positionPacket.getLat(), positionPacket.getLon());
                double heading = 360.0 / 0xffff * positionPacket.getHeading();
                boatGroup.setDestination(p2d.getX(), p2d.getY(), heading, positionPacket.getGroundSpeed(), positionPacket.getTimeValid(), frameRate, boatGroup.getRaceId());
            } catch (InterruptedException e){
                e.printStackTrace();
            }
//            }
        }
    }

    void updateMarkGroup (long raceId, MarkGroup markGroup) {
        PriorityBlockingQueue<BoatPositionPacket> movementQueue = StreamParser.markPositions.get(raceId);
        if (movementQueue.size() > 0){
            try {
                BoatPositionPacket positionPacket = movementQueue.take();
                Point2D p2d = findScaledXY(positionPacket.getLat(), positionPacket.getLon());
                markGroup.moveMarkTo(p2d.getX(), p2d.getY(), raceId);
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Draws all the boats.
     */
    private void initializeBoats() {
        Map<Integer, Yacht> boats = StreamParser.getBoats();
        Group boatAnnotations = new Group();

        ArrayList<Participant> participants = StreamParser.getXmlObject().getRaceXML().getParticipants();
        ArrayList<Integer> participantIDs = new ArrayList<>();
        for (Participant p : participants) {
            participantIDs.add(p.getsourceID());
        }

        for (Yacht boat : boats.values()) {
            if (participantIDs.contains(boat.getSourceID())) {
                boat.setColour(Colors.getColor());
                BoatGroup boatGroup = new BoatGroup(boat, boat.getColour());
                boatGroups.add(boatGroup);
                boatAnnotations.getChildren().add(boatGroup.getLowPriorityAnnotations());
            }
        }
        group.getChildren().add(boatAnnotations);
        group.getChildren().addAll(boatGroups);
    }

    private void initializeMarks() {
        ArrayList<Mark> allMarks = StreamParser.getXmlObject().getRaceXML().getCompoundMarks();
        for (Mark mark : allMarks) {
            if (mark.getMarkType() == MarkType.SINGLE_MARK) {
                SingleMark sMark = (SingleMark) mark;

                MarkGroup markGroup = new MarkGroup(sMark, findScaledXY(sMark));
                markGroups.add(markGroup);
            } else {
                GateMark gMark = (GateMark) mark;

                MarkGroup markGroup = new MarkGroup(gMark, findScaledXY(gMark.getSingleMark1()), findScaledXY(gMark.getSingleMark2())); //should be 2 objects in the list.
                markGroups.add(markGroup);
            }
        }
        group.getChildren().addAll(markGroups);
    }

    class ResizableCanvas extends Canvas {

        ResizableCanvas() {
            // Redraw canvas when size changes.
            widthProperty().addListener(evt -> draw());
            heightProperty().addListener(evt -> draw());
        }

        private void draw() {
            double width = getWidth();
            double height = getHeight();

            GraphicsContext gc = getGraphicsContext2D();
            gc.clearRect(0, 0, width, height);
        }

        @Override
        public boolean isResizable() {
            return true;
        }

        @Override
        public double prefWidth(double height) {
            return getWidth();
        }
        @Override
        public double prefHeight(double width) {
            return getHeight();
        }

    }

    private void drawFps(int fps){
        if (raceViewController.isDisplayFps()){
            FPSdisplay.setVisible(true);
            FPSdisplay.setText(String.format("%d FPS", fps));
        } else {
            FPSdisplay.setVisible(false);
        }
    }

    /**
     * Calculates x and y location for every marker that fits it to the canvas the race will be drawn on.
     */
    private void fitMarksToCanvas() {
        //Check is called once to avoid unnecessarily change the course limits once the race is running
        StreamParser.isNewRaceXmlReceived();
        findMinMaxPoint();
        double minLonToMaxLon = scaleRaceExtremities();
        calculateReferencePointLocation(minLonToMaxLon);
        //givePointsXY();
        addRaceBorder();
    }


    /**
     * Sets the class variables minLatPoint, maxLatPoint, minLonPoint, maxLonPoint to the marker with the leftmost
     * marker, rightmost marker, southern most marker and northern most marker respectively.
     */
    private void findMinMaxPoint() {
        List<Limit> sortedPoints = new ArrayList<>();
        for (Limit limit : StreamParser.getXmlObject().getRaceXML().getCourseLimit()) {
            sortedPoints.add(limit);
        }
        sortedPoints.sort(Comparator.comparingDouble(Limit::getLat));
        Limit minLatMark = sortedPoints.get(0);
        Limit maxLatMark = sortedPoints.get(sortedPoints.size()-1);
        minLatPoint = new SingleMark(minLatMark.toString(), minLatMark.getLat(), minLatMark.getLng(), minLatMark.getSeqID());
        maxLatPoint = new SingleMark(maxLatMark.toString(), maxLatMark.getLat(), maxLatMark.getLng(), maxLatMark.getSeqID());

        sortedPoints.sort(Comparator.comparingDouble(Limit::getLng));
        //If the course is on a point on the earth where longitudes wrap around.
        Limit minLonMark = sortedPoints.get(0);
        Limit maxLonMark = sortedPoints.get(sortedPoints.size()-1);
        minLonPoint = new SingleMark(minLonMark.toString(), minLonMark.getLat(), minLonMark.getLng(), minLonMark.getSeqID());
        maxLonPoint = new SingleMark(maxLonMark.toString(), maxLonMark.getLat(), maxLonMark.getLng(), maxLonMark.getSeqID());
        if (maxLonPoint.getLongitude() - minLonPoint.getLongitude() > 180) {
            horizontalInversion = true;
        }
    }

    /**
     * Calculates the location of a reference point, this is always the point with minimum latitude, in relation to the
     * canvas.
     *
     * @param minLonToMaxLon The horizontal distance between the point of minimum longitude to maximum longitude.
     */
    private void calculateReferencePointLocation (double minLonToMaxLon) {
        Mark referencePoint = minLatPoint;
        double referenceAngle;

        if (scaleDirection == ScaleDirection.HORIZONTAL) {
            referenceAngle = Math.abs(Mark.calculateHeadingRad(referencePoint, minLonPoint));
            referencePointX = LHS_BUFFER + distanceScaleFactor * Math.sin(referenceAngle) * Mark.calculateDistance(referencePoint, minLonPoint);

            referenceAngle = Math.abs(Mark.calculateHeadingRad(referencePoint, maxLatPoint));
            referencePointY  = CANVAS_HEIGHT - (TOP_BUFFER + BOT_BUFFER);
            referencePointY -= distanceScaleFactor * Math.cos(referenceAngle) * Mark.calculateDistance(referencePoint, maxLatPoint);
            referencePointY  = referencePointY / 2;
            referencePointY += TOP_BUFFER;
            referencePointY += distanceScaleFactor * Math.cos(referenceAngle) * Mark.calculateDistance(referencePoint, maxLatPoint);
        } else {
            referencePointY = CANVAS_HEIGHT - BOT_BUFFER;

            referenceAngle = Math.abs(Mark.calculateHeadingRad(referencePoint, minLonPoint));
            referencePointX  = LHS_BUFFER;
            referencePointX += distanceScaleFactor * Math.sin(referenceAngle) * Mark.calculateDistance(referencePoint, minLonPoint);
            referencePointX += ((CANVAS_WIDTH - (LHS_BUFFER + RHS_BUFFER)) - (minLonToMaxLon * distanceScaleFactor)) / 2;
        }
        if(horizontalInversion) {
            referencePointX = CANVAS_WIDTH - RHS_BUFFER - (referencePointX - LHS_BUFFER);
        }
    }


    /**
     * Finds the scale factor necessary to fit all race markers within the onscreen map and assigns it to distanceScaleFactor
     * Returns the max horizontal distance of the map.
     */
    private double scaleRaceExtremities () {

        double vertAngle = Math.abs(Mark.calculateHeadingRad(minLatPoint, maxLatPoint));
        double vertDistance = Math.cos(vertAngle) * Mark.calculateDistance(minLatPoint, maxLatPoint);
        double horiAngle = Mark.calculateHeadingRad(minLonPoint, maxLonPoint);

        if (horiAngle <= (Math.PI / 2))
            horiAngle = (Math.PI / 2) - horiAngle;
        else
            horiAngle = horiAngle - (Math.PI / 2);
        double horiDistance = Math.cos(horiAngle) * Mark.calculateDistance(minLonPoint, maxLonPoint);

        double vertScale = (CANVAS_HEIGHT - (TOP_BUFFER + BOT_BUFFER)) / vertDistance;

        if ((horiDistance * vertScale) > (CANVAS_WIDTH - (RHS_BUFFER + LHS_BUFFER))) {
            distanceScaleFactor = (CANVAS_WIDTH - (RHS_BUFFER + LHS_BUFFER)) / horiDistance;
            scaleDirection = ScaleDirection.HORIZONTAL;
        } else {
            distanceScaleFactor = vertScale;
            scaleDirection = ScaleDirection.VERTICAL;
        }
        return horiDistance;
    }

    private Point2D findScaledXY (Mark unscaled) {
        return findScaledXY (unscaled.getLatitude(), unscaled.getLongitude());
    }

    private Point2D findScaledXY (double unscaledLat, double unscaledLon) {
        double distanceFromReference;
        double angleFromReference;
        int xAxisLocation = (int) referencePointX;
        int yAxisLocation = (int) referencePointY;

        angleFromReference = Mark.calculateHeadingRad(minLatPoint.getLatitude(), minLatPoint.getLongitude(), unscaledLat, unscaledLon);
        distanceFromReference = Mark.calculateDistance(minLatPoint.getLatitude(), minLatPoint.getLongitude(), unscaledLat, unscaledLon);
        if (angleFromReference >= 0 && angleFromReference <= Math.PI / 2) {
            xAxisLocation += (int) Math.round(distanceScaleFactor * Math.sin(angleFromReference) * distanceFromReference);
            yAxisLocation -= (int) Math.round(distanceScaleFactor * Math.cos(angleFromReference) * distanceFromReference);
        } else if (angleFromReference >= 0) {
            angleFromReference = angleFromReference - Math.PI / 2;
            xAxisLocation += (int) Math.round(distanceScaleFactor * Math.cos(angleFromReference) * distanceFromReference);
            yAxisLocation += (int) Math.round(distanceScaleFactor * Math.sin(angleFromReference) * distanceFromReference);
        } else if (angleFromReference < 0 && angleFromReference >= -Math.PI / 2) {
            angleFromReference = Math.abs(angleFromReference);
            xAxisLocation -= (int) Math.round(distanceScaleFactor * Math.sin(angleFromReference) * distanceFromReference);
            yAxisLocation -= (int) Math.round(distanceScaleFactor * Math.cos(angleFromReference) * distanceFromReference);
        } else {
            angleFromReference = Math.abs(angleFromReference) - Math.PI / 2;
            xAxisLocation -= (int) Math.round(distanceScaleFactor * Math.cos(angleFromReference) * distanceFromReference);
            yAxisLocation += (int) Math.round(distanceScaleFactor * Math.sin(angleFromReference) * distanceFromReference);
        }
        if(horizontalInversion) {
            xAxisLocation = CANVAS_WIDTH - RHS_BUFFER - (xAxisLocation - LHS_BUFFER);
        }
        return new Point2D(xAxisLocation, yAxisLocation);
    }

    List<BoatGroup> getBoatGroups() {
        return boatGroups;
    }
}