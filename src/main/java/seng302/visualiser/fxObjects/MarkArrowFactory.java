package seng302.visualiser.fxObjects;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import seng302.visualiser.fxObjects.assets_3D.Model;
import seng302.visualiser.fxObjects.assets_3D.ModelFactory;
import seng302.visualiser.fxObjects.assets_3D.ModelType;

// TODO: 16/08/17 this class used to be well written... FeelsBadMan. Maybe lose the ternary operators.
/**
 * Factory class for making rounding arrows for mark objects out of JavaFX objects.
 */
public class MarkArrowFactory {

    /**
     * The side of the boat that will be closest to the mark.
     */
    public enum RoundingSide {
        PORT,
        STARBOARD,
    }

    public static final double MARK_ARROW_SEPARATION = 8;
    public static final double ARROW_LENGTH = 20;
    public static final double ARROW_HEAD_DEPTH = 5;
    public static final double ARROW_HEAD_WIDTH = 3;
    public static final double STROKE_WIDTH = 1;

    public static Model constructEntryArrow3D (
        RoundingSide roundingSide, double angle, ModelType type) {
        Model entryArrow = ModelFactory.importModel(type);

        double angleDeg = angle;
        angle = 180 - angle;
        angle = Math.toRadians(angle);

        int multiplier = roundingSide == RoundingSide.STARBOARD ? 1 : -1;
        double relativeX = multiplier * 5.7 * Math.sin(angle + Math.PI / 2);
        double relativeY = multiplier * 5.7 * Math.cos(angle + Math.PI / 2);
        double xStart = relativeX + -10 * Math.sin(angle);
        double yStart = relativeY + -10 * Math.cos(angle);
        entryArrow.getAssets().getTransforms().addAll(
            new Translate(xStart, yStart, 0),
            new Rotate(angleDeg, new Point3D(0,0,1))
        );
        return entryArrow;
    }

    public static Model constructExitArrow3D (
        RoundingSide roundingSide, double angle, ModelType type) {
        Model exitArrow = ModelFactory.importModel(type);

        double angleDeg = angle;
        angle = 180 - angle;
        angle = Math.toRadians(angle);

        int multiplier = roundingSide == RoundingSide.STARBOARD ? 1 : -1;
        double xStart = multiplier * 5.7 * Math.sin(angle + Math.PI / 2);
        double yStart = multiplier * 5.7 * Math.cos(angle + Math.PI / 2);

        exitArrow.getAssets().getTransforms().addAll(
            new Translate(xStart, yStart, 0),
            new Rotate(angleDeg, new Point3D(0,0,1))
        );

        return exitArrow;
    }


    /**
     * Creates an entry arrow group showing an arrow into and out of the rounding area. It is centered on (0, 0).
     * @param roundingSide The side of the boat that will be closest to the mark.
     * @param angleOfEntry The angle between this mark and the last one as a heading from north in degrees.
     * @param angleOfExit The angle between this mark and the next one as a heading from north in degrees.
     * @param colour The desired colour of the arrows.
     * @return The group containing all JavaFX objects.
     */
    public static Group constructEntryArrow (RoundingSide roundingSide, double angleOfEntry,
        double angleOfExit, Paint colour) {
        //Create regular exit arrow.
        Group arrow = new Group();
        Group exitSection = constructExitArrow(roundingSide, angleOfExit, colour);
        //Reverse angles to make arc
        angleOfEntry = 180 - angleOfEntry;
        angleOfExit = 180 - angleOfExit;
        //Maker the arc
        Arc roundSection = new Arc(
            0, 0, MARK_ARROW_SEPARATION, MARK_ARROW_SEPARATION,
            //Where to start drawing arc from
            (roundingSide == RoundingSide.PORT ? 180 + angleOfEntry : angleOfEntry),
            //Which way to go around the mark. (clockwise vs anticlockwise)
            roundingSide == RoundingSide.PORT ? Math.abs(angleOfExit - angleOfEntry) : -Math.abs(angleOfEntry - angleOfExit)
        );
        roundSection.setStrokeWidth(STROKE_WIDTH);
        roundSection.setType(ArcType.OPEN);
        roundSection.setStroke(colour);
        roundSection.setFill(new Color(0,0,0,0));
        //Revert angle to normal for line segment. Invert Port/Starboard since it is an entry arrow.
        Polygon entrySection = constructLineSegment(
            roundingSide == RoundingSide.PORT ? RoundingSide.STARBOARD : RoundingSide.PORT,
            180 + angleOfEntry, colour
        );
        arrow.getChildren().addAll(exitSection, roundSection, entrySection);
        return arrow;
    }

    /**
     * Make an arrow when the turning is not around the outside of the mark.
     *
     * @param roundingSide side to round on.
     * @param angleOfExit angle of entry
     * @param angleOfEntry angle of exit
     * @param colour colour of arrow
     * @return the arrow.
     */
    public static Group constructInteriorArrow(RoundingSide roundingSide, double angleOfExit,
        double angleOfEntry, Paint colour) {

        Group arrow = new Group();
        Polygon lineSegment;
        //Reverse angle of exit/entry to find position between them
        angleOfEntry = Math.toRadians(360 - angleOfEntry);
        angleOfExit = Math.toRadians(180 - angleOfExit);
        //Find start of entry arrow if it was a regular arrow.
        int multiplier = roundingSide == RoundingSide.STARBOARD ? -1 : 1;
        double xStart = multiplier * MARK_ARROW_SEPARATION * Math.sin(angleOfEntry + Math.PI / 2);
        double yStart = multiplier * MARK_ARROW_SEPARATION * Math.cos(angleOfEntry + Math.PI / 2);
        xStart = xStart + (ARROW_LENGTH * Math.sin(angleOfEntry));
        yStart = yStart + (ARROW_LENGTH * Math.cos(angleOfEntry));
        //Find of end exit arrow if it was a regular arrow.
        multiplier = roundingSide == RoundingSide.STARBOARD ? 1 : -1;
        double xEnd = multiplier * MARK_ARROW_SEPARATION * Math.sin(angleOfExit + Math.PI / 2);
        double yEnd = multiplier * MARK_ARROW_SEPARATION * Math.cos(angleOfExit + Math.PI / 2);
        xEnd = xEnd + (ARROW_LENGTH * Math.sin(angleOfExit));
        yEnd = yEnd + (ARROW_LENGTH * Math.cos(angleOfExit));
        //Make line between these points.
        lineSegment = new Polygon(
            xStart, yStart,
            xEnd, yEnd
        );
        lineSegment.setStroke(colour);
        lineSegment.setFill(Color.BLUE);
        lineSegment.setStrokeWidth(STROKE_WIDTH);
        lineSegment.setStrokeLineCap(StrokeLineCap.ROUND);
        //Make arrow head at the angle between these points.
        Polyline arrowHead = constructArrowHead(
            90 + Math.toDegrees(Math.atan2(yStart - yEnd, xEnd - xStart)),
            colour
        );
        arrowHead.setLayoutX(xEnd);
        arrowHead.setLayoutY(yEnd);
        //Construct arrow.
        arrow.getChildren().addAll(lineSegment, arrowHead);
        return arrow;
    }

    /**
     * Creates an exit arrow group pointing towards the next mark.
     * @param roundingSide The side of the boat that will be closest to the mark.
     * @param angle The angle to the next mark as a heading from north in degrees.
     * @param colour The colour of the arrow.
     * @return The group containing all the JavaFX objects.
     */
    public static Group constructExitArrow (RoundingSide roundingSide, double angle, Paint colour) {
        angle = 180 - angle;
        Group arrow = new Group();
        Polygon arrowBody = constructLineSegment(roundingSide, angle, colour);
        Polyline arrowHead = constructArrowHead(angle, colour);
        arrowHead.setLayoutX(arrowBody.getPoints().get(2));
        arrowHead.setLayoutY(arrowBody.getPoints().get(3));
        arrow.getChildren().addAll(arrowBody, arrowHead);
        return arrow;
    }

    /**
     * Constructs a line rotated to the correct angle and and in the correct position for a mark at
     * position 0,0. Note that a line segment is assumed to be facing away from the mark so for
     * entry Starboard make the RoundingSide Port and vice versa.
     * @param roundingSide Rounding side of an exit arrow. (Reversed for entry)
     * @param angle Angle of line segment.
     * @param colour the desired colour of the line.
     * @return Line segmented at correct rotation centered at (0,0)
     */
    private static Polygon constructLineSegment (RoundingSide roundingSide, double angle, Paint colour) {
        Polygon lineSegment;
        angle = Math.toRadians(angle);
        int multiplier = roundingSide == RoundingSide.STARBOARD ? 1 : -1;
        double xStart = multiplier * MARK_ARROW_SEPARATION * Math.sin(angle + Math.PI / 2);
        double yStart = multiplier * MARK_ARROW_SEPARATION * Math.cos(angle + Math.PI / 2);
        double xEnd = xStart + (ARROW_LENGTH * Math.sin(angle));
        double yEnd = yStart + (ARROW_LENGTH * Math.cos(angle));
        lineSegment = new Polygon(
            xStart, yStart,
            xEnd, yEnd
        );
        lineSegment.setStroke(colour);
        lineSegment.setFill(Color.BLUE);
        lineSegment.setStrokeWidth(STROKE_WIDTH);
        lineSegment.setStrokeLineCap(StrokeLineCap.ROUND);
        return lineSegment;
    }

    /**
     * Constructs a PolyLine in the shape of an arrow head.
     * @param rotation direction for the arrow head to point.
     * @param colour colour of the arrow head
     * @return the arrowhead shaped PolyLine.
     */
    private static Polyline constructArrowHead (double rotation, Paint colour) {
        Polyline arrow = new Polyline(
            -ARROW_HEAD_WIDTH, -ARROW_HEAD_DEPTH,
            0, 0,
            ARROW_HEAD_WIDTH, -ARROW_HEAD_DEPTH
        );
        arrow.getTransforms().add(new Rotate(-rotation));
        arrow.setStrokeLineCap(StrokeLineCap.ROUND);
        arrow.setStroke(colour);
        arrow.setStrokeWidth(STROKE_WIDTH);
        return arrow;
    }
}
