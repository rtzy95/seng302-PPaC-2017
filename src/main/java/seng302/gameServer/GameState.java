package seng302.gameServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import seng302.gameServer.server.messages.BoatAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seng302.gameServer.server.messages.MarkRoundingMessage;
import seng302.gameServer.server.messages.Message;
import seng302.model.Player;
import seng302.model.Yacht;
import seng302.model.mark.MarkOrder;

/**
 * A Static class to hold information about the current state of the game (model)
 * Created by wmu16 on 10/07/17.
 */
public class GameState implements Runnable {

    @FunctionalInterface
    interface MarkPassingListener {

        void markPassing(Message message);
    }

    private Logger logger = LoggerFactory.getLogger(GameState.class);

    private static Integer STATE_UPDATES_PER_SECOND = 60;
    public static Integer MAX_PLAYERS = 8;

    private static Long previousUpdateTime;
    public static Double windDirection;
    private static Double windSpeed;

    private static String hostIpAddress;
    private static List<Player> players;
    private static Map<Integer, Yacht> yachts;
    private static Boolean isRaceStarted;
    private static GameStages currentStage;
    private static MarkOrder markOrder;
    private static long startTime;

    private static List<MarkPassingListener> markListeners;

    private static Map<Player, String> playerStringMap = new HashMap<>();
    /*
        Ideally I would like to make this class an object instantiated by the server and given to
        it's created threads if necessary. Outside of that I think the dependencies on it
        (atm only Yacht & GameClient) can be removed from most other classes. The observable list of
        players could be pulled directly from the server by the GameClient since it instantiates it
        and it is reasonable for it to pull data. The current setup of publicly available statics is
        pretty meh IMO because anything can change it making it unreliable and like people did with
        the old ServerParser class everything that needs shared just gets thrown in the static
        collections and things become a real mess.
     */

    public GameState(String hostIpAddress) {
        windDirection = 180d;
        windSpeed = 10000d;
        this.hostIpAddress = hostIpAddress;
        yachts = new HashMap<>();
        players = new ArrayList<>();
        GameState.hostIpAddress = hostIpAddress;
        ;
        currentStage = GameStages.LOBBYING;
        isRaceStarted = false;
        //set this when game stage changes to prerace
        previousUpdateTime = System.currentTimeMillis();
        markOrder = new MarkOrder(); //This could be instantiated at some point with a select map?
        markListeners = new ArrayList<>();

        new Thread(this).start();   //Run the auto updates on the game state
    }

    public static String getHostIpAddress() {
        return hostIpAddress;
    }

    public static List<Player> getPlayers() {
        return players;
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

    public static void addYacht(Integer sourceId, Yacht yacht) {
        yachts.put(sourceId, yacht);
    }

    public static void removeYacht(Integer yachtId) {
        yachts.remove(yachtId);
    }

    public static Boolean getIsRaceStarted() {
        return isRaceStarted;
    }

    public static GameStages getCurrentStage() {
        return currentStage;
    }

    public static void setCurrentStage(GameStages currentStage) {
        if (currentStage == GameStages.RACING){
            startTime = System.currentTimeMillis();
        }

        GameState.currentStage = currentStage;
    }

    public static MarkOrder getMarkOrder() {
        return markOrder;
    }

    public static long getStartTime(){
        return startTime;
    }

    public static Double getWindDirection() {
        return windDirection;
    }

    public static Double getWindSpeedMMS() {
        return windSpeed;
    }

    public static Double getWindSpeedKnots() {
        return windSpeed / 1000 * 1.943844492; // TODO: 26/07/17 cir27 - remove magic numbers
    }

    public static Map<Integer, Yacht> getYachts() {
        return yachts;
    }

    public static void updateBoat(Integer sourceId, BoatAction actionType) {
        Yacht playerYacht = yachts.get(sourceId);
//        System.out.println("-----------------------");
        switch (actionType) {
            case VMG:
                playerYacht.turnToVMG();
//                System.out.println("Snapping to VMG");
                break;
            case SAILS_IN:
                playerYacht.toggleSailIn();
//                System.out.println("Toggling Sails");
                break;
            case SAILS_OUT:
                playerYacht.toggleSailIn();
//                System.out.println("Toggling Sails");
                break;
            case TACK_GYBE:
                playerYacht.tackGybe(windDirection);
//                System.out.println("Tack/Gybe");
                break;
            case UPWIND:
                playerYacht.turnUpwind();
//                System.out.println("Moving upwind");
                break;
            case DOWNWIND:
                playerYacht.turnDownwind();
//                System.out.println("Moving downwind");
                break;
        }

//        printBoatStatus(playerYacht);
    }

    public void update() {
        Long timeInterval = System.currentTimeMillis() - previousUpdateTime;
        previousUpdateTime = System.currentTimeMillis();
        for (Yacht yacht : yachts.values()) {
            yacht.update(timeInterval);
        }
    }

    /**
     * Generates a new ID based off the size of current players + 1
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

        while(true) {
            try {
                Thread.sleep(1000 / STATE_UPDATES_PER_SECOND);
            } catch (InterruptedException e) {
                System.out.println("[GameState] interrupted exception");
            }
            if (currentStage == GameStages.PRE_RACE) {
                update();
            }

            //RACING
            if (currentStage == GameStages.RACING) {
                update();
            }
        }
    }

    private static void printBoatStatus(Yacht playerYacht) {
        System.out.println("-----------------------");
        System.out.println("Sails are in: " + playerYacht.getSailIn());
        System.out.println("Heading: " + playerYacht.getHeading());
        System.out.println("Velocity: " + playerYacht.getVelocityMMS() / 1000);
        System.out.println("Lat: " + playerYacht.getLocation().getLat());
        System.out.println("Lng: " + playerYacht.getLocation().getLng());
        System.out.println("-----------------------\n");
    }

    public static void addMarkPassListener(MarkPassingListener listener) {
        markListeners.add(listener);
    }

    public static void removeMarkPassListenr(MarkPassingListener listener) {
        markListeners.remove(listener);
    }
}
