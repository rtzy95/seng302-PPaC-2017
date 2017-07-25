package seng302.model;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.scene.paint.Color;
import seng302.model.mark.Mark;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Yacht class for the racing boat.
 *
 * Class created to store more variables (eg. boat statuses) compared to the XMLParser boat class,
 * also done outside Boat class because some old variables are not used anymore.
 */
public class Boat {

    // Used in boat group
    private Color colour = Color.BLACK;

    private String boatType;
    private Integer sourceID;
    private String hullID; //matches HullNum in the XML spec.
    private String shortName;
    private String boatName;
    private String country;

    // Boat status
    private Integer boatStatus;
    private Integer legNumber = 0;
    private Integer position = 0;
    private Long estimateTimeAtFinish;
    private Long markRoundTime;
    private Double lat;
    private Double lon;
    private Double heading;
    private ReadOnlyDoubleWrapper velocity = new ReadOnlyDoubleWrapper();
    private ReadOnlyLongWrapper timeTillNext = new ReadOnlyLongWrapper();
    private ReadOnlyLongWrapper timeSinceLastMark = new ReadOnlyLongWrapper();

    // Mark rounding
    private Mark lastMarkRounded;
    private Mark nextMark;

    public Boat(String boatType, Integer sourceID, String hullID, String shortName,
        String boatName, String country) {
        this.boatType = boatType;
        this.sourceID = sourceID;
        this.hullID = hullID;
        this.shortName = shortName;
        this.boatName = boatName;
        this.country = country;
    }

    public String getBoatType() {
        return boatType;
    }

    public Integer getSourceID() {
        return sourceID;
    }

    public String getHullID() {
        return hullID;
    }

    public String getShortName() {
        return shortName;
    }

    public String getBoatName() {
        return boatName;
    }

    public String getCountry() {
        return country;
    }

    public Integer getBoatStatus() {
        return boatStatus;
    }

    public void setBoatStatus(Integer boatStatus) {
        this.boatStatus = boatStatus;
    }

    public Integer getLegNumber() {
        return legNumber;
    }

    public void setLegNumber(Integer legNumber) {
        this.legNumber = legNumber;
    }

    public void setEstimateTimeTillNextMark(Long estimateTimeAtNextMark) {
        timeTillNext.set(estimateTimeAtNextMark);
    }

    public String getEstimateTimeAtFinish() {
        DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return format.format(estimateTimeAtFinish);
    }

    public void setEstimateTimeAtFinish(Long estimateTimeAtFinish) {
        this.estimateTimeAtFinish = estimateTimeAtFinish;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Color getColour() {
        return colour;
    }

    public void setColour(Color colour) {
        this.colour = colour;
    }

    public void setVelocity(double velocity) {
        this.velocity.set(velocity);
    }


    public void setMarkRoundingTime(Long markRoundingTime) {
        this.markRoundTime = markRoundingTime;
    }

    public ReadOnlyDoubleProperty getVelocityProperty() {
        return velocity.getReadOnlyProperty();
    }

    public ReadOnlyLongProperty timeTillNextProperty() {
        return timeTillNext.getReadOnlyProperty();
    }

    public Long getMarkRoundTime() {
        return markRoundTime;
    }

    public Mark getLastMarkRounded() {
        return lastMarkRounded;
    }

    public void setLastMarkRounded(Mark lastMarkRounded) {
        this.lastMarkRounded = lastMarkRounded;
    }

    public void setNextMark(Mark nextMark) {
            this.nextMark = nextMark;
      }

    public Mark getNextMark(){
        return nextMark;
      }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Double getHeading() {
        return heading;
    }

    public void setHeading(Double heading) {
        this.heading = heading;
    }

    @Override
    public String toString() {
        return boatName;
    }

    public void setTimeSinceLastMark (long timeSinceLastMark) {
        this.timeSinceLastMark.set(timeSinceLastMark);
    }

    public ReadOnlyLongProperty timeSinceLastMarkProperty () {
        return timeSinceLastMark.getReadOnlyProperty();
    }

}
