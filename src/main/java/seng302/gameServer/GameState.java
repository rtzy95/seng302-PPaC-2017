package seng302.gameServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seng302.gameServer.messages.BoatAction;
import seng302.gameServer.messages.BoatStatus;
import seng302.gameServer.messages.ChatterMessage;
import seng302.gameServer.messages.CustomizeRequestType;
import seng302.gameServer.messages.MarkRoundingMessage;
import seng302.gameServer.messages.MarkType;
import seng302.gameServer.messages.Message;
import seng302.gameServer.messages.RoundingBoatStatus;
import seng302.model.GeoPoint;
import seng302.model.Limit;
import seng302.model.Player;
import seng302.model.PolarTable;
import seng302.model.ServerYacht;
import seng302.model.mark.CompoundMark;
import seng302.model.mark.Mark;
import seng302.model.mark.MarkOrder;
import seng302.model.stream.xml.parser.RaceXMLData;
import seng302.model.token.Token;
import seng302.model.token.TokenType;
import seng302.utilities.GeoUtility;
import seng302.utilities.RandomSpawn;
import seng302.visualiser.fxObjects.assets_3D.BoatMeshType;

/**
 * A Static class to hold information about the current state of the game (model)
 * Also contains logic for updating itself on regular time intervals on its own thread
 * Created by wmu16 on 10/07/17.
 */
public class GameState implements Runnable {

    @FunctionalInterface
    interface NewMessageListener {
        void notify(Message message);
    }

    private static Logger logger = LoggerFactory.getLogger(GameState.class);


    private static final Integer STATE_UPDATES_PER_SECOND = 60;

    //Scheduling constants
    static final int WARNING_TIME = 10 * -1000;
    static final int PREPATORY_TIME = 5 * -1000;
    private static final int TIME_TILL_START = 10 * 1000;

    //Wind Constants
    private static final int MAX_WIND_SPEED = 12000;
    private static final int MIN_WIND_SPEED = 8000;

    //Rounding Constants
    private static final Double ROUNDING_DISTANCE = 50d; // TODO: 14/08/17 wmu16 - Look into this value further

    //Collision constants
    private static final Double MARK_COLLISION_DISTANCE = 15d;
    public static final Double YACHT_COLLISION_DISTANCE = 15.0;
    private static final Double BOUNCE_DISTANCE_MARK = 20.0;
    public static final Double BOUNCE_DISTANCE_YACHT = 30.0;
    private static final Double COLLISION_VELOCITY_PENALTY = 0.3;

    //Powerup Constants
    public static final Double VELOCITY_BOOST_MULTIPLIER = 2d;
    public static final Integer HANDLING_BOOST_MULTIPLIER = 2;
    private static final Double BAD_RANDOM_SPEED_PENALTY = 0.3;
    public static final Long BUMPER_DISABLE_TIME = 5_000L;
    private static final Long TOKEN_SPAWN_TIME = 30_000L;

    private static Long previousUpdateTime;
    public static Double windDirection;
    public static ReadOnlyDoubleWrapper windDirectionProperty = new ReadOnlyDoubleWrapper();
    private static Double windSpeed;
    private static Double serverSpeedMultiplier;

    private static Boolean customizationFlag; // dirty flag to tell if a player has customized their boat.
    private static Boolean playerHasLeftFlag;

    private static String hostIpAddress;
    private static List<Player> players;
    private static Map<Integer, ServerYacht> yachts;
    private static GameStages currentStage;
    private static MarkOrder markOrder;
    private static long startTime;
    private static Set<Mark> marks = new HashSet<>();
    private static List<Limit> courseLimit = new ArrayList<>();
    private static Integer maxPlayers = 12;

    private static List<Token> tokensInPlay;
    private static RandomSpawn randomSpawn;

    private static List<NewMessageListener> newMessageListeners;

    private static Map<Player, String> playerStringMap = new HashMap<>();
    private static boolean tokensEnabled = false;

    public GameState() {
        windDirection = 180d;
        windDirectionProperty.set(windDirection);
        windSpeed = 10000d;
        yachts = new HashMap<>();
        tokensInPlay = new CopyOnWriteArrayList<>();
        players = new ArrayList<>();
        customizationFlag = false;
        playerHasLeftFlag = false;
        serverSpeedMultiplier = 1.0;
        currentStage = GameStages.LOBBYING;
        previousUpdateTime = System.currentTimeMillis();
        newMessageListeners = new ArrayList<>();

        resetStartTime();

        new Thread(this, "GameState").start();   //Run the auto updates on the game state
    }

    public static void setRace(RaceXMLData raceXMLData) {
        markOrder = new MarkOrder(raceXMLData);
        for (CompoundMark compoundMark : raceXMLData.getCompoundMarks().values()){
            marks.addAll(compoundMark.getMarks());
        }
        randomSpawn = new RandomSpawn(markOrder.getOrderedUniqueCompoundMarks());
        courseLimit = raceXMLData.getCourseLimit();
    }

    public static List<Player> getPlayers() {
        return players;
    }

    public static List<Token> getTokensInPlay() {
        return tokensInPlay;
    }

    public static Set<Mark> getMarks() {
        return Collections.unmodifiableSet(marks);
    }

    public static void addPlayer(Player player) {
        players.add(player);
        String playerText = player.getYacht().getSourceId() + " " + player.getYacht().getBoatName()
            + " " + player.getYacht().getCountry();
        playerStringMap.put(player, playerText);
    }

    public static void removePlayer(Player player) {
        players.remove(player);
        playerStringMap.remove(player);
    }

    public static void addYacht(Integer sourceId, ServerYacht yacht) {
        yachts.put(sourceId, yacht);
    }

    public static void removeYacht(Integer yachtId) {
        yachts.remove(yachtId);
    }

    public static GameStages getCurrentStage() {
        return currentStage;
    }

    public static void setCurrentStage(GameStages currentStage) {
        GameState.currentStage = currentStage;
    }

    public static MarkOrder getMarkOrder() {
        return markOrder;
    }

    public static long getStartTime() {
        return startTime;
    }

    public static void resetStartTime(){
        startTime = System.currentTimeMillis() + TIME_TILL_START;
    }

    public static Double getWindDirection() {
        return windDirection;
    }

    public static void setWindDirection(Double newWindDirection) {
        windDirection = newWindDirection;
        windDirectionProperty.set(newWindDirection);
    }

    public static void setWindSpeed(Double newWindSpeed) {
        windSpeed = newWindSpeed;
    }

    public static Double getWindSpeedMMS() {
        return windSpeed;
    }

    public static Double getWindSpeedKnots() {
        return GeoUtility.mmsToKnots(windSpeed); // TODO: 26/07/17 cir27 - remove magic numbers
    }

    public static Map<Integer, ServerYacht> getYachts() {
        return yachts;
    }


    /**
     * Generates a new ID based off the size of current players + 1
     *
     * @return a playerID to be allocated to a new connetion
     */
    public static Integer getUniquePlayerID() {
        // TODO: 22/07/17 wmu16 - This may not be robust enough and may have to be improved on.
        return yachts.size() + 1;
    }


    /**
     * A thread to have the game state update itself at certain intervals
     */
    @Override
    public void run() {

        while (currentStage != GameStages.FINISHED) {
            try {
                Thread.sleep(1000 / STATE_UPDATES_PER_SECOND);
            } catch (InterruptedException e) {
                System.out.println("[GameState] interrupted exception");
            }
            if (currentStage == GameStages.PRE_RACE) {
                update();
                if (System.currentTimeMillis() > startTime) {
                    startSpawningTokens();
                    startUpdatingWind();
                    GameState.currentStage = GameStages.RACING;
                }
            }
            if (currentStage == GameStages.RACING) {
                update();
            }
        }
    }

    /**
     * Start spawning coins every 60s after the first minute
     */
    private void startSpawningTokens() {
        Timer timer = new Timer("Token Spawning Timer");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (tokensEnabled) {
                    spawnNewToken();
                    notifyMessageListeners(MessageFactory.getRaceXML());
                }
            }
        }, 0, TOKEN_SPAWN_TIME);
    }

    // TODO: 29/08/17 wmu16 - This sort of update should be in game state
    private static void startUpdatingWind() {
        Timer timer = new Timer("Wind Updating Timer");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateWind();
            }
        }, 0, 500);
    }


    private static void updateWind() {
        Integer direction = GameState.getWindDirection().intValue();
        Integer windSpeed = GameState.getWindSpeedMMS().intValue();

        Random random = new Random();

        if (Math.floorMod(random.nextInt(), 2) == 0) {
            direction += random.nextInt(4);
            windSpeed += random.nextInt(20) + 459;
        } else {
            direction -= random.nextInt(4);
            windSpeed -= random.nextInt(20) + 459;
        }

        direction = Math.floorMod(direction, 360);

        if (windSpeed > MAX_WIND_SPEED) {
            windSpeed -= random.nextInt(500);
        }

        if (windSpeed <= MIN_WIND_SPEED) {
            windSpeed += random.nextInt(500);
        }

        GameState.windSpeed = Double.valueOf(windSpeed);
        GameState.windDirection = direction.doubleValue();
    }


    public static void updateBoat(Integer sourceId, BoatAction actionType) {
        ServerYacht playerYacht = yachts.get(sourceId);
        switch (actionType) {
            case VMG:
                playerYacht.turnToVMG();
                break;
            case SAILS_IN:
                playerYacht.toggleSailIn();
                break;
            case SAILS_OUT:
                playerYacht.toggleSailIn();
                break;
            case TACK_GYBE:
                playerYacht.tackGybe(windDirection);
                break;
            case UPWIND:
                playerYacht.turnUpwind();
                break;
            case DOWNWIND:
                playerYacht.turnDownwind();
                break;
            case CONTINUOUSLY_TURNING:
                playerYacht.setContinuouslyTurning(true);
                break;
            case DEFAULT_TURNING:
                playerYacht.setContinuouslyTurning(false);
                break;
        }
    }

    /**
     * Randomly select a subset of tokensInPlay from a pre defined superset
     * Broadasts a new race status message to show this update
     */
    private void spawnNewToken() {
        tokensInPlay.clear();
        Token token = randomSpawn.getRandomToken();
//        token.assignType(TokenType.WIND_WALKER);
        logger.debug("Spawned token of type " + token.getTokenType());
        tokensInPlay.add(token);
        MessageFactory.updateTokens(tokensInPlay);
    }

    /**
     * Called periodically in this GameState thread to update the GameState values.
     * -Updates yachts velocity
     * -Updates locations
     * -Checks for collisions
     * -Checks for progression
     *
     * -Also checks things like the end of the race and race start time etc
     */
    public void update() {
        Boolean raceFinished = true;

        Double timeInterval = (System.currentTimeMillis() - previousUpdateTime) / 1000000.0;
        previousUpdateTime = System.currentTimeMillis();

        for (ServerYacht yacht : yachts.values()) {
            updateVelocity(yacht);
            yacht.runAutoPilot();
            yacht.updateLocation(timeInterval);
            preformTokenUpdates(yacht); //This update must be done before collision. Sorta hacky
            checkCollision(yacht);
            if (yacht.getBoatStatus() != BoatStatus.FINISHED) {
                checkForLegProgression(yacht);
                raceFinished = false;
            }
        }

        if (raceFinished) {
            currentStage = GameStages.FINISHED;
        }
    }


    /**
     * All token functionality entry points is taken care of here. So can be disabled and enabled
     * easily
     *
     * @param yacht The yacht to perform token checks on
     */
    private void preformTokenUpdates(ServerYacht yacht) {
        Token collidedToken = checkTokenPickUp(yacht);
        if (collidedToken != null) {
            tokensInPlay.remove(collidedToken);
            powerUpYacht(yacht, collidedToken);
            MessageFactory.updateTokens(tokensInPlay);
            notifyMessageListeners(MessageFactory.getRaceXML());
        }

        checkPowerUpTimeout(yacht);
        TokenType powerUp = yacht.getPowerUp();

        if (powerUp != null) {
            switch (powerUp) {
                case WIND_WALKER:
                    windWalk(yacht);
                    break;
                case BUMPER:
                    ServerYacht collidedYacht = checkYachtCollision(yacht, true);
                    if (collidedYacht != null) {
                        yacht.powerDown();
                        boatTempShutDown(collidedYacht);
                        notifyMessageListeners(MessageFactory.makePowerDownMessage(yacht));
                        notifyMessageListeners(
                            MessageFactory.makeStatusEffectMessage(collidedYacht, powerUp));
                    }
                    break;
                case RANDOM:
                    yacht.setPowerUpSpeedMultiplier(BAD_RANDOM_SPEED_PENALTY);
            }
        }
    }


    /**
     * Powers up a thisYacht with the given token type.
     *
     * @param thisYacht The yacht to be powered up
     * @param collidedToken The token which this thisYacht collided with
     */
    private void powerUpYacht(ServerYacht thisYacht, Token collidedToken) {
        //The random token has a 50% chance of becoming another token else becoming a speed detriment!
        if (collidedToken.getTokenType() == TokenType.RANDOM && new Random().nextBoolean()) {
            collidedToken.realiseRandom();
        }

        //If another yacht has the wind walker token, They should be powered down. Only one allowed!
        else if (collidedToken.getTokenType() == TokenType.WIND_WALKER) {
            for (ServerYacht otherYacht : yachts.values()) {
                if (otherYacht != thisYacht && otherYacht.getPowerUp() == TokenType.WIND_WALKER) {
                    powerDownYacht(otherYacht);
                }
            }
        }

        thisYacht.powerUp(collidedToken.getTokenType());
        String logMessage =
            thisYacht.getBoatName() + " has picked up a " + collidedToken.getTokenType().getName()
                + " token";
        notifyMessageListeners(
            MessageFactory.makeChatterMessage(thisYacht.getSourceId(), logMessage));
        notifyMessageListeners(MessageFactory.getRaceXML());
        notifyMessageListeners(MessageFactory.makePickupMessage(thisYacht, collidedToken));
        logger.debug(
            "Yacht: " + thisYacht.getShortName() + " got powerup " + collidedToken.getTokenType());
    }

    private void powerDownYacht(ServerYacht yacht) {
        String logMessage =
            yacht.getBoatName() + "'s " + yacht.getPowerUp().getName() + "  expired";
        notifyMessageListeners(
            MessageFactory.makeChatterMessage(yacht.getSourceId(), logMessage));
        notifyMessageListeners(MessageFactory.makePowerDownMessage(yacht));
        logger.debug("Yacht: " + yacht.getShortName() + " powered down!");

        yacht.powerDown();
    }

    // TODO: 23/09/17 wmu16 - This is a hacky way to have the boat power down. Need some sort of separation between token and status effect :/

    /**
     * Disables the given boat for BUMPER_DISABLE_TIME ms.
     *
     * @param yacht The yacht to disable
     */
    private void boatTempShutDown(ServerYacht yacht) {
        yacht.setPowerUpSpeedMultiplier(0d);
        Timer shutDownTimer = new Timer("Shutdown Timer");
        shutDownTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                yacht.powerDown();  //Note this actually resets the boat to normal.
            }
        }, BUMPER_DISABLE_TIME);
    }


    /**
     * Checks how long a powerup has been active for. If it has exceeded its timeout, it powers the
     * yacht down.
     *
     * @param yacht The yacht to check to power down
     */
    private void checkPowerUpTimeout(ServerYacht yacht) {
        if (yacht.getPowerUp() != null) {
            if (System.currentTimeMillis() - yacht.getPowerUpStartTime() > yacht.getPowerUp()
                .getTimeout()) {
                powerDownYacht(yacht);
            }
        }
    }


    /**
     * This function changes the wind to be at an angle that causes the yacht in question to be at
     * its fastest velocity
     *
     * @param yacht The yacht to fix the wind for
     */
    private void windWalk(ServerYacht yacht) {
        Double optimalAngle = PolarTable.getOptimalAngle();
        Double heading = yacht.getHeading();
        windDirection = (double) Math.floorMod(Math.round(heading + optimalAngle), 360L);
        windDirectionProperty.set(windDirection);
    }


    /**
     * Check if the yacht has crossed the course limit
     *
     * @param yacht the yacht to be tested
     * @return a boolean value of if there is a boundary collision
     */
    private static Boolean checkBoundaryCollision(ServerYacht yacht) {
        for (int i = 0; i < courseLimit.size() - 1; i++) {
            if (GeoUtility.checkCrossedLine(courseLimit.get(i), courseLimit.get(i + 1),
                yacht.getLastLocation(), yacht.getLocation()) != 0) {
                return true;
            }
        }

        if (GeoUtility.checkCrossedLine(courseLimit.get(courseLimit.size() - 1), courseLimit.get(0),
            yacht.getLastLocation(), yacht.getLocation()) != 0) {
            return true;
        }
        return false;
    }

    /**
     * Checks all tokensInPlay to see if a yacht has picked one up. If so, the yacht is powered up
     * in the appropriate way
     * @param yacht The yacht to check for collision with a token
     *
     * @return The token collided with
     */
    private Token checkTokenPickUp(ServerYacht yacht) {
        for (Token token : tokensInPlay) {
            Double distance = GeoUtility.getDistance(token, yacht.getLocation());
            if (distance < YACHT_COLLISION_DISTANCE) {
                return token;
            }
        }

        return null;
    }


    /**
     * Checks for collision with other in game objects for the given serverYacht. To be called each
     * update. If there is a collision, Notifies the server to send the appropriate messages out.
     * Checks for these items in turn:
     * - Other yachts
     * - Marks
     * - Boundary
     * - Tokens
     *
     * @param serverYacht The server yacht to check collisions with
     */
    public static void checkCollision(ServerYacht serverYacht) {
        //Yacht Collision
        ServerYacht collidedYacht = checkYachtCollision(serverYacht, false);
        Mark collidedMark = checkMarkCollision(serverYacht);

        if (collidedYacht != null) {
            GeoPoint originalLocation = serverYacht.getLocation();
            serverYacht.setLocation(
                calculateBounceBack(serverYacht, originalLocation, BOUNCE_DISTANCE_YACHT)
            );
            serverYacht.setCurrentVelocity(
                serverYacht.getCurrentVelocity() * COLLISION_VELOCITY_PENALTY
            );
            collidedYacht.setLocation(
                calculateBounceBack(collidedYacht, originalLocation, BOUNCE_DISTANCE_YACHT)
            );
            collidedYacht.setCurrentVelocity(
                collidedYacht.getCurrentVelocity() * COLLISION_VELOCITY_PENALTY
            );
            notifyMessageListeners(MessageFactory.makeCollisionMessage(serverYacht));
        }

        //Mark Collision
        else if (collidedMark != null) {
            serverYacht.setLocation(
                calculateBounceBack(serverYacht, collidedMark, BOUNCE_DISTANCE_MARK)
            );

            serverYacht.setCurrentVelocity(
                serverYacht.getCurrentVelocity() * COLLISION_VELOCITY_PENALTY
            );
            notifyMessageListeners(MessageFactory.makeCollisionMessage(serverYacht));
        }

        //Boundary Collision
        else if (checkBoundaryCollision(serverYacht)) {
            serverYacht.setLocation(
                calculateBounceBack(serverYacht, serverYacht.getLocation(),
                    BOUNCE_DISTANCE_YACHT)
            );

            serverYacht.setCurrentVelocity(
                serverYacht.getCurrentVelocity() * COLLISION_VELOCITY_PENALTY
            );
            notifyMessageListeners(MessageFactory.makeCollisionMessage(serverYacht));
        }
    }


    private void updateVelocity(ServerYacht yacht) {
        Double trueWindAngle = Math.abs(windDirection - yacht.getHeading());
        Double boatSpeedInKnots = PolarTable.getBoatSpeed(getWindSpeedKnots(), trueWindAngle);
        Double maxBoatSpeed =
            GeoUtility.knotsToMMS(boatSpeedInKnots) * serverSpeedMultiplier * yacht
                .getPowerUpSpeedMultiplier() * yacht.getBoatTypeSpeedMultiplier();

        Double currentVelocity = yacht.getCurrentVelocity();
        // TODO: 15/08/17 remove magic numbers from these equations.
        if (yacht.getSailIn()) {
            if (currentVelocity < maxBoatSpeed - 500) {
                yacht.changeVelocity(
                    (maxBoatSpeed / 100) * yacht.getBoatTypeAccelerationMultiplier());
            } else if (currentVelocity > maxBoatSpeed + 500) {
                yacht.changeVelocity(
                    (-currentVelocity / 200) * yacht.getBoatTypeAccelerationMultiplier());
            } else {
                yacht
                    .setCurrentVelocity((maxBoatSpeed) * yacht.getBoatTypeAccelerationMultiplier());
            }
        } else {
            if (currentVelocity > 3000) {
                yacht.changeVelocity(
                    (-currentVelocity / 200) * yacht.getBoatTypeAccelerationMultiplier());
            } else if (currentVelocity > 100) {
                yacht.changeVelocity(
                    (-currentVelocity / 50) * yacht.getBoatTypeAccelerationMultiplier());
            } else if (currentVelocity <= 100) {
                yacht.setCurrentVelocity(0d);
            }
        }
    }


    /**
     * Calculates the distance to the next mark (closest of the two if a gate mark). For purposes of
     * mark rounding
     *
     * @return A distance in metres. Returns -1 if there is no next mark
     * @throws IndexOutOfBoundsException If the next mark is null (ie the last mark in the race)
     * Check first using {@link seng302.model.mark.MarkOrder#isLastMark(Integer)}
     */
    private Double calcDistanceToCurrentMark(ServerYacht yacht) throws IndexOutOfBoundsException {
        Integer currentMarkSeqID = yacht.getCurrentMarkSeqID();
        CompoundMark currentMark = markOrder.getCurrentMark(currentMarkSeqID);
        GeoPoint location = yacht.getLocation();

        if (currentMark.isGate()) {
            Mark sub1 = currentMark.getSubMark(1);
            Mark sub2 = currentMark.getSubMark(2);
            Double distance1 = GeoUtility.getDistance(location, sub1);
            Double distance2 = GeoUtility.getDistance(location, sub2);
            if (distance1 < distance2) {
                yacht.setClosestCurrentMark(sub1);
                return distance1;
            } else {
                yacht.setClosestCurrentMark(sub2);
                return distance2;
            }
        } else {
            yacht.setClosestCurrentMark(currentMark.getSubMark(1));
            return GeoUtility.getDistance(location, currentMark.getSubMark(1));
        }
    }


    /**        lobbyController.setPlayerListSource(clientLobbyList);
     * 4 Different cases of progression in the race 1 - Passing the start line 2 - Passing any
     * in-race Gate 3 - Passing any in-race Mark 4 - Passing the finish line
     *
     * @param yacht the current yacht to check for progression
     */
    private void checkForLegProgression(ServerYacht yacht) {
        Integer currentMarkSeqID = yacht.getCurrentMarkSeqID();
        CompoundMark currentMark = markOrder.getCurrentMark(currentMarkSeqID);

        Boolean hasProgressed;
        if (currentMarkSeqID == 0) {
            hasProgressed = checkStartLineCrossing(yacht);
        } else if (markOrder.isLastMark(currentMarkSeqID)) {
            hasProgressed = checkFinishLineCrossing(yacht);
        } else if (currentMark.isGate()) {
            hasProgressed = checkGateRounding(yacht);
        } else {
            hasProgressed = checkMarkRounding(yacht);
        }

        if (hasProgressed) {
            if (currentMarkSeqID != 0 && !markOrder.isLastMark(currentMarkSeqID)) {

                String logMessage = yacht.getBoatName() + " passed leg " + yacht.getLegNumber();
                notifyMessageListeners(
                    MessageFactory.makeChatterMessage(yacht.getSourceId(), logMessage));
            }
            yacht.incrementLegNumber();
            sendMarkRoundingMessage(yacht);
            logMarkRounding(yacht);
            yacht.setHasPassedLine(false);
            yacht.setHasEnteredRoundingZone(false);
            yacht.setHasPassedThroughGate(false);
            if (!markOrder.isLastMark(currentMarkSeqID)) {
                yacht.incrementMarkSeqID();
            }
        }
    }


    /**
     * If we pass the start line gate in the correct direction, progress
     *
     * @param yacht The current yacht to check for
     */
    private Boolean checkStartLineCrossing(ServerYacht yacht) {
        long timeTillStart = System.currentTimeMillis() - this.getStartTime();
        if (timeTillStart < 0){
            return false;
        }
        Integer currentMarkSeqID = yacht.getCurrentMarkSeqID();
        CompoundMark currentMark = markOrder.getCurrentMark(currentMarkSeqID);
        GeoPoint lastLocation = yacht.getLastLocation();
        GeoPoint location = yacht.getLocation();

        Mark mark1 = currentMark.getSubMark(1);
        Mark mark2 = currentMark.getSubMark(2);
        CompoundMark nextMark = markOrder.getNextMark(currentMarkSeqID);

        Integer crossedLine = GeoUtility.checkCrossedLine(mark1, mark2, lastLocation, location);
        if (crossedLine > 0) {
            Boolean isClockwiseCross = GeoUtility.isClockwise(mark1, mark2, nextMark.getMidPoint());
            if (crossedLine == 2 && isClockwiseCross || crossedLine == 1 && !isClockwiseCross) {
                yacht.setClosestCurrentMark(mark1);
                yacht.setBoatStatus(BoatStatus.RACING);
                String logMessage = yacht.getBoatName() + " passed start line";
                notifyMessageListeners(
                    MessageFactory.makeChatterMessage(yacht.getSourceId(), logMessage));
                return true;
            }
        }

        return false;
    }


    /**
     * This algorithm checks for mark rounding. And increments the currentMarSeqID number attribute
     * of the yacht if so. A visual representation of this algorithm can be seen on the Wiki under
     * 'mark passing algorithm'
     *
     * @param yacht The current yacht to check for
     */
    private Boolean checkMarkRounding(ServerYacht yacht) {
        Integer currentMarkSeqID = yacht.getCurrentMarkSeqID();
        CompoundMark currentMark = markOrder.getCurrentMark(currentMarkSeqID);
        GeoPoint lastLocation = yacht.getLastLocation();
        GeoPoint location = yacht.getLocation();
        GeoPoint nextPoint = markOrder.getNextMark(currentMarkSeqID).getMidPoint();
        GeoPoint prevPoint = markOrder.getPreviousMark(currentMarkSeqID).getMidPoint();
        GeoPoint midPoint = GeoUtility.getDirtyMidPoint(nextPoint, prevPoint);

        if (calcDistanceToCurrentMark(yacht) < ROUNDING_DISTANCE) {
            yacht.setHasEnteredRoundingZone(true);
        }

        //In case current mark is a gate, loop through all marks just in case
        for (Mark thisCurrentMark : currentMark.getMarks()) {
            if (GeoUtility.isPointInTriangle(lastLocation, location, midPoint, thisCurrentMark)) {
                yacht.setHasPassedLine(true);
            }
        }

        return yacht.hasPassedLine() && yacht.hasEnteredRoundingZone();
    }


    /**
     * Checks if a gate line has been crossed and in the correct direction
     *
     * @param yacht The current yacht to check for
     */
    private Boolean checkGateRounding(ServerYacht yacht) {
        Integer currentMarkSeqID = yacht.getCurrentMarkSeqID();
        CompoundMark currentMark = markOrder.getCurrentMark(currentMarkSeqID);
        GeoPoint lastLocation = yacht.getLastLocation();
        GeoPoint location = yacht.getLocation();

        Mark mark1 = currentMark.getSubMark(1);
        Mark mark2 = currentMark.getSubMark(2);
        CompoundMark prevMark = markOrder.getPreviousMark(currentMarkSeqID);
        CompoundMark nextMark = markOrder.getNextMark(currentMarkSeqID);

        Integer crossedLine = GeoUtility.checkCrossedLine(mark1, mark2, lastLocation, location);

        //We have crossed the line
        if (crossedLine > 0) {
            Boolean isClockwiseCross = GeoUtility.isClockwise(mark1, mark2, prevMark.getMidPoint());

            //Check we cross the line in the correct direction
            if (crossedLine == 1 && isClockwiseCross || crossedLine == 2 && !isClockwiseCross) {
                yacht.setHasPassedThroughGate(true);
            }
        }

        Boolean prevMarkSide = GeoUtility.isClockwise(mark1, mark2, prevMark.getMidPoint());
        Boolean nextMarkSide = GeoUtility.isClockwise(mark1, mark2, nextMark.getMidPoint());

        if (yacht.hasPassedThroughGate()) {
            //Check if we need to round this gate after passing through
            if (prevMarkSide == nextMarkSide) {
                return checkMarkRounding(yacht);
            } else {
                return true;
            }
        }

        return false;
    }

    /**
     * If we pass the finish gate in the correct direction
     *
     * @param yacht The current yacht to check for
     */
    private Boolean checkFinishLineCrossing(ServerYacht yacht) {
        Integer currentMarkSeqID = yacht.getCurrentMarkSeqID();
        CompoundMark currentMark = markOrder.getCurrentMark(currentMarkSeqID);
        GeoPoint lastLocation = yacht.getLastLocation();
        GeoPoint location = yacht.getLocation();

        Mark mark1 = currentMark.getSubMark(1);
        Mark mark2 = currentMark.getSubMark(2);
        CompoundMark prevMark = markOrder.getPreviousMark(currentMarkSeqID);

        Integer crossedLine = GeoUtility.checkCrossedLine(mark1, mark2, lastLocation, location);
        if (crossedLine > 0) {
            Boolean isClockwiseCross = GeoUtility.isClockwise(mark1, mark2, prevMark.getMidPoint());
            if (crossedLine == 1 && isClockwiseCross || crossedLine == 2 && !isClockwiseCross) {
                yacht.setClosestCurrentMark(mark1);
                yacht.setBoatStatus(BoatStatus.FINISHED);

                String logMessage = yacht.getBoatName() + " passed finish line";
                notifyMessageListeners(
                    MessageFactory.makeChatterMessage(yacht.getSourceId(), logMessage));
                return true;
            }
        }

        return false;
    }

    /**
     * Handles player customization.
     *
     * @param playerID The ID of the player being modified.
     * @param requestType the type of player customization the player wants
     * @param customizeData the data related to the customization (color, name, shape)
     */
    public static void customizePlayer(long playerID, CustomizeRequestType requestType,
        byte[] customizeData) {
        ServerYacht playerYacht = yachts.get((int) playerID);

        if (requestType.equals(CustomizeRequestType.NAME)) {
            String name = new String(customizeData);
            playerYacht.setBoatName(name);
        } else if (requestType.equals(CustomizeRequestType.COLOR)) {
            //This low level stuff shouldnt be here alistair! In fact no logic LIKE THIS should! - wmu16
            int red = customizeData[0] & 0xFF;
            int green = customizeData[1] & 0xFF;
            int blue = customizeData[2] & 0xFF;
            Color yachtColor = Color.rgb(red, green, blue);
            playerYacht.setBoatColor(yachtColor);
        } else if (requestType.equals(CustomizeRequestType.SHAPE)) {
            String type = new String(customizeData);
            playerYacht.setBoatType(BoatMeshType.valueOf(type));
        }
    }

    private static Mark checkMarkCollision(ServerYacht yacht) {
        Set<Mark> marksInRace = new HashSet<>(marks);
        for (Mark mark : marksInRace) {
            if (GeoUtility.getDistance(yacht.getLocation(), mark)
                <= MARK_COLLISION_DISTANCE) {
                return mark;
            }
        }
        return null;
    }

    /**
     * Calculate the new position of the boat after it has had a collision
     *
     * @return The boats new position
     */
    private static GeoPoint calculateBounceBack(ServerYacht yacht, GeoPoint collidedWith,
        Double bounceDistance) {
        Double heading = GeoUtility.getBearing(yacht.getLastLocation(), collidedWith);
        // Invert heading
        heading -= 180;
        Integer newHeading = Math.floorMod(heading.intValue(), 360);
        return GeoUtility
            .getGeoCoordinate(yacht.getLocation(), newHeading.doubleValue(), bounceDistance);
    }

    /**
     * Collision detection which iterates through all the yachts and check if any yacht collided
     * with this yacht. Return collided yacht or null if no collision.
     *
     * UPDATE: HACK!!! wmu16 - forBumperCollision is (the goddamn dirtiest) dirty flag to fix a
     * weird bug where the bumper collision would not be registerd but the knock back collision would.
     * In other words, only set the 'forBumperCollision' flag true if used for the bumper power up.
     *
     * @return yacht to compare to all other yachts.
     */
    private static ServerYacht checkYachtCollision(ServerYacht yacht, Boolean forBumperCollision) {
        Double collisionDistance =
            (forBumperCollision) ? YACHT_COLLISION_DISTANCE + 2.5 : YACHT_COLLISION_DISTANCE;

        for (ServerYacht otherYacht : GameState.getYachts().values()) {
            if (otherYacht != yacht) {
                Double distance = GeoUtility
                    .getDistance(otherYacht.getLocation(), yacht.getLocation());
                ;
                if (distance < collisionDistance) {
                    return otherYacht;
                }
            }
        }
        return null;
    }

    private void sendMarkRoundingMessage(ServerYacht yacht) {
        Integer sourceID = yacht.getSourceId();
        Integer currentMarkSeqID = yacht.getCurrentMarkSeqID();
        CompoundMark currentMark = markOrder.getCurrentMark(currentMarkSeqID);
        MarkType markType = (currentMark.isGate()) ? MarkType.GATE : MarkType.ROUNDING_MARK;
        Mark roundingMark = yacht.getClosestCurrentMark();

        // TODO: 13/8/17 figure out the rounding side, rounded mark source ID and boat status.
        Message markRoundingMessage = new MarkRoundingMessage(0, 0,
            sourceID, RoundingBoatStatus.RACING, roundingMark.getRoundingSide(), markType,
            currentMarkSeqID + 1);

        notifyMessageListeners(markRoundingMessage);
    }

    private static void notifyMessageListeners(Message message) {
        for (NewMessageListener ml : newMessageListeners) {
            ml.notify(message);
        }
    }

    private void logMarkRounding(ServerYacht yacht) {
        Mark roundingMark = yacht.getClosestCurrentMark();
        logger.debug(
            String.format("Yacht srcID(%d) passed Mark srcID(%d)", yacht.getSourceId(),
                roundingMark.getSourceID()));
    }

    public static void processChatter(ChatterMessage chatterMessage, boolean isHost) {
        String chatterText = chatterMessage.getMessage();
        String[] words = chatterText.split("\\s+");
        if (words.length > 2 && isHost) {
            switch (words[2].trim()) {
                case "/speed":
                    try {
                        serverSpeedMultiplier = Double.valueOf(words[3]);
                        String logMessage = "Speed modifier set to x" + words[3];
                        notifyMessageListeners(MessageFactory
                            .makeChatterMessage(chatterMessage.getMessageType(), logMessage));
                    } catch (Exception e) {
                        Logger logger = LoggerFactory.getLogger(GameState.class);
                        logger.error("cannot parse >speed value");
                    }
                    return;
                case "/finish":
                    String logMessage = "Game will now finish";
                    notifyMessageListeners(MessageFactory
                        .makeChatterMessage(chatterMessage.getMessageType(), logMessage));
                    endRace();
                    return;
            }
        }
        notifyMessageListeners(chatterMessage);
    }

    public static void addMessageEventListener(NewMessageListener listener) {
        newMessageListeners.add(listener);
    }

    public static void setCustomizationFlag() {
        customizationFlag = true;
    }

    public static Boolean getCustomizationFlag() {
        return customizationFlag;
    }

    public static void resetCustomizationFlag() {
        customizationFlag = false;
    }

    public static void setPlayerHasLeftFlag(Boolean flag) {
        playerHasLeftFlag = flag;
    }

    public static Boolean getPlayerHasLeftFlag() {
        return playerHasLeftFlag;
    }

    public static Integer getNumberOfPlayers(){
        Integer numPlayers = 1;

        for(Player p : getPlayers()){
            if(p.getSocket().isConnected()){
                numPlayers++;
            }
        }

        return numPlayers;
    }

    public static Integer getCapacity(){
        return maxPlayers;
    }

    public static void setMaxPlayers(Integer newMax){
        maxPlayers = newMax;

        try {
            ServerAdvertiser.getInstance().setCapacity(newMax);
        } catch (IOException e) {
            logger.warn("Couldn't update max players");
        }
    }

    public static void endRace () {
        yachts.forEach((id, yacht) -> yacht.setBoatStatus(BoatStatus.FINISHED));
        currentStage = GameStages.FINISHED;
    }

    public static double getServerSpeedMultiplier() {
        return serverSpeedMultiplier;
    }

    public static void setTokensEnabled (boolean tokensEnabled) {
        GameState.tokensEnabled = tokensEnabled;
    }

    public static ReadOnlyDoubleWrapper getWindDirectionProperty() {
        return windDirectionProperty;
    }
}
