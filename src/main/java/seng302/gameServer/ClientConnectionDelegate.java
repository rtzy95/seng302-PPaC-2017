package seng302.gameServer;

import seng302.models.Player;

public interface ClientConnectionDelegate {
    /**
     * A player has connected to the server
     * @param player The player that has connected
     */
    void clientConnected(Player player);

    /**
     * A player has disconnected from the server
     * @param player The player that has disconnected
     */
    void clientDisconnected(Player player);
}