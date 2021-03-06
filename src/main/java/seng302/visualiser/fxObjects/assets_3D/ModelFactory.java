package seng302.visualiser.fxObjects.assets_3D;

import com.interactivemesh.jfx.importer.col.ColModelImporter;
import com.interactivemesh.jfx.importer.stl.StlMeshImporter;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Circle;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;


/**
 * Factory class for creating 3D models of boatTypes.
 */
public class ModelFactory {

    public static BoatModel boatIconView(BoatMeshType boatType, Color primaryColour) {
        Group boatAssets = getUnmodifiedBoatModel(boatType, primaryColour);
        final Rotate animationRotate = new Rotate(0, new Point3D(0,0,1));
        boatAssets.getTransforms().addAll(
            new Scale(3.3, 3.3, 3.3),
            new Rotate(-70, new Point3D(1,0,0)),
            new Translate(13,50, 0),
            animationRotate
        );

        boatAssets.getTransforms().add(animationRotate);
        BoatModel bo = new BoatModel(boatAssets, null, boatType);
        bo.rotateSail(45);

        bo.setAnimation(new AnimationTimer() {
            double boatAngle = 0;
            Rotate rotate = animationRotate;
            @Override
            public void handle(long now) {
                boatAngle += 0.5;
                rotate.setAngle(boatAngle);
            }
        });
        boatAssets.getChildren().addAll(
            new AmbientLight()
        );
        return bo;
    }

    public static BoatModel boatCustomiseView(BoatMeshType boatType, Color primaryColour) {
        Group boatAssets = getUnmodifiedBoatModel(boatType, primaryColour);
        final Rotate animationRotate = new Rotate(0, new Point3D(0,0,1));
        boatAssets.getTransforms().addAll(
            new Scale(8.0, 8.0, 8.0),
            new Rotate(-70, new Point3D(1,0,0)),
            new Translate(16,50, 1),
            animationRotate
        );

        boatAssets.getTransforms().add(animationRotate);
        BoatModel bo = new BoatModel(boatAssets, null, boatType);
        bo.rotateSail(45);

        bo.setAnimation(new AnimationTimer() {
            double boatAngle = 0;
            Rotate rotate = animationRotate;
            @Override
            public void handle(long now) {
                boatAngle += 0.5;
                rotate.setAngle(boatAngle);
            }
        });
        boatAssets.getChildren().addAll(
            new AmbientLight()
        );
        return bo;
    }

    public static BoatModel boatGameView(BoatMeshType boatType, Color primaryColour) {
        Group boatAssets = getUnmodifiedBoatModel(boatType, primaryColour);
        boatAssets.getTransforms().setAll(
            new Scale(0.3, 0.3, 0.3)
        );
        return new BoatModel(boatAssets, null, boatType);
    }

    private static Group getUnmodifiedBoatModel(BoatMeshType boatType, Color primaryColour) {

        Group boatAssets = new Group();
        MeshView hull = importBoatSTL(boatType.hullFile);
        hull.setMaterial(new PhongMaterial(primaryColour));
        boatAssets.getChildren().add(hull);

        if (boatType.mastFile != null) {
            MeshView mast = importBoatSTL(boatType.mastFile);
            mast.setMaterial(new PhongMaterial(primaryColour));
            boatAssets.getChildren().add(mast);
        } else {
            boatAssets.getChildren().add(new MeshView());
        }

        MeshView sail = importBoatSTL(boatType.sailFile);
        sail.setMaterial(
            new PhongMaterial(boatType == BoatMeshType.PARROT ? Color.BLACK : Color.WHITE)
        );
        boatAssets.getChildren().add(sail);

        if (boatType.jibFile != null) {
            MeshView jib = importBoatSTL(boatType.jibFile);
            jib.setMaterial(
                new PhongMaterial(boatType == BoatMeshType.PARROT ? Color.DARKGRAY : Color.WHITE)
            );
            boatAssets.getChildren().add(jib);
        }

        return boatAssets;
    }

    private static MeshView importBoatSTL(String fileName) {
        return importSTL("boatSTLs/" + fileName);
    }

    public static MeshView importSTL(String fileName) {
        StlMeshImporter importer = new StlMeshImporter();
        importer.read(ModelFactory.class.getResource("/meshes/" + fileName));
        MeshView importedFile = new MeshView(importer.getImport());
        importedFile.setCache(true);
        importedFile.setCacheHint(CacheHint.SCALE_AND_ROTATE);
        return new MeshView(importer.getImport());
    }

    public static Model importModel(ModelType tokenType) {
        Group assets;
        if (tokenType.filename == null) {
            assets = new Group();
        } else {
            ColModelImporter importer = new ColModelImporter();
            importer.read(ModelFactory.class.getResource("/meshes/" + tokenType.filename));
            assets = new Group(importer.getImport());
            assets.setCache(true);
            assets.setCacheHint(CacheHint.SCALE_AND_ROTATE);
        }
        switch (tokenType) {
            case PLAYER_IDENTIFIER_TORUS:
                return makeIdentifierTorus(assets);
            case NEXT_MARK_INDICATOR:
                return makeNextMarkIndicator(assets);
            case VELOCITY_PICKUP:
            case BUMPER_PICKUP:
            case RANDOM_PICKUP:
            case HANDLING_PICKUP:
            case WIND_WALKER_PICKUP:
                return makeTokenPickup(assets);
            case FINISH_MARKER:
            case PLAIN_MARKER:
            case START_MARKER:
                return makeMarker(assets);
            case OCEAN:
                return makeOcean(assets);
            case BORDER_PYLON:
            case BORDER_BARRIER:
                return makeBarrier(assets);
            case FINISH_LINE:
            case START_LINE:
            case GATE_LINE:
                return makeGate(assets);
            case WAKE:
                return makeWake(assets);
            case TRAIL_SEGMENT:
                return makeTrail(assets);
            case PLAYER_IDENTIFIER:
                return makeIdentifierIcon(assets);
            case START_ARROW:
            case FINISH_ARROW:
            case PLAIN_ARROW:
                makeArrow(assets);
            default:
                return new Model(new Group(assets), null);
        }
    }

    private static Model makeIdentifierTorus(Group assets) {
//        assets.getChildren().add(new AmbientLight());
        return new Model(new Group(assets), null);
    }

    private static Model makeNextMarkIndicator(Group assets) {
//        assets.getChildren().add(new AmbientLight());
        return new Model(new Group(assets), null);
    }

    private static Model makeTokenPickup(Group assets) {
        Rotate animationRotate = new Rotate(0, new Point3D(0, 0, 1));
        assets.getTransforms().addAll(
            animationRotate,
            new Translate(0, 0, -1)
        );

        return new Model(new Group(assets), new AnimationTimer() {

            private double rotation = 0;
            private Rotate rotate = animationRotate;

            @Override
            public void handle(long now) {
                rotation += 1;
                rotate.setAngle(rotation);
            }
        });
    }

    private static Model makeMarker(Group marker) {
        ColModelImporter importer = new ColModelImporter();
        importer.read(ModelFactory.class.getResource("/meshes/" + ModelType.MARK_AREA.filename));
        Group area = new Group(importer.getImport());
        area.getChildren().add(marker);
        area.getTransforms().add(new Rotate(90, new Point3D(1, 0, 0)));
        return new Model(new Group(area), null);
    }

    private static Model makeOcean(Group group) {
        Circle ocean = new Circle(
            0,0,250, Color.SKYBLUE
        );
        ocean.setStroke(Color.TRANSPARENT);
        group.getChildren().add(ocean);
        return new Model(new Group(group), null);
    }

    private static Model makeBarrier(Group assets) {
        assets.getTransforms().addAll(
            new Rotate(90, new Point3D(1,0,0))
        );
        return new Model(new Group(assets), null);
    }

    private static Model makeGate(Group assets) {
        assets.getTransforms().addAll(
            new Rotate(90, new Point3D(1,0,0))
        );
        return new Model(new Group(assets), null);
    }

    private static Model makeWake(Group assets) {
        assets.getTransforms().setAll(
            new Rotate(-90, new Point3D(0,0,1)),
            new Rotate(90, new Point3D(1,0,0)),
            new Scale(0.5, 0.5, 0.5)
        );
        return new Model(new Group(assets), null);
    }

    private static Model makeTrail(Group trailPiece) {
        trailPiece.getTransforms().addAll(
            new Rotate(-90, new Point3D(0,0,1)),
            new Rotate(90, new Point3D(1,0,0))
        );
        return new Model(new Group(trailPiece), null);
    }

    private static Model makeIdentifierIcon(Group assets) {
        assets.getTransforms().addAll(
            new Rotate(90, new Point3D(1,0,0)),
            new Scale(0.5, 0.5, 0.5)
        );
        return new Model(assets, null);
    }

    private static Model makeArrow(Group assets) {
        assets.getTransforms().addAll(
            new Rotate(90, new Point3D(1,0,0))
        );
        return new Model(new Group(assets), null);
    }

    /**
     * Create a 3D wind arrow.
     *
     * @return 3D wind arrow object
     */
    public static Model makeWindArrow() {
        ColModelImporter importer = new ColModelImporter();
        importer.read(ModelFactory.class.getResource("/meshes/" + ModelType.WIND_ARROW.filename));
        Group assets = new Group(importer.getImport());
        assets.setCache(true);
        assets.setCacheHint(CacheHint.SCALE_AND_ROTATE);

        Rotate animationRotate = new Rotate(0, new Point3D(0, 1, 0));
        assets.getTransforms().addAll(
            new Translate(0, 0, 0),
            new Scale(5, 5, 5),
            new Rotate(270, new Point3D(1, 0, 0)),
            animationRotate
        );

        assets.getChildren().addAll(
            new AmbientLight()
        );

        return new Model(new Group(assets), null);
    }
}
