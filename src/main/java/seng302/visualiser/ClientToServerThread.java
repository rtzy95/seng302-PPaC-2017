package seng302.visualiser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seng302.gameServer.messages.BoatAction;
import seng302.gameServer.messages.BoatActionMessage;
import seng302.gameServer.messages.ChatterMessage;
import seng302.gameServer.messages.ClientType;
import seng302.gameServer.messages.CustomizeRequestMessage;
import seng302.gameServer.messages.CustomizeRequestType;
import seng302.gameServer.messages.Message;
import seng302.gameServer.messages.RegistrationRequestMessage;
import seng302.gameServer.messages.RegistrationResponseStatus;
import seng302.gameServer.messages.XMLMessage;
import seng302.gameServer.messages.XMLMessageSubType;
import seng302.model.stream.packets.PacketType;
import seng302.model.stream.packets.StreamPacket;
import seng302.model.stream.xml.generator.RaceXMLTemplate;
import seng302.model.stream.xml.generator.RegattaXMLTemplate;
import seng302.utilities.XMLGenerator;
import seng302.utilities.XMLParser;


/**
 * A class describing a single connection to a Server for the purposes of sending and receiving on
 * its own thread.
 */
public class ClientToServerThread implements Runnable {

    private boolean isStarted = false;

    /**
     * Functional interface for receiving packets from client socket.
     */
    @FunctionalInterface
    public interface ClientSocketListener {
        void newPacket();
    }

    @FunctionalInterface
    public interface DisconnectedFromHostListener {
        void notifyDisconnection(String message);
    }

    @FunctionalInterface
    public interface ConnectionErrorListener {
        void notifyConnectionError(String message);
    }

    private class ByteReadException extends Exception {
        private ByteReadException(String message) {
            super(message);
        }
    }

    private Queue<StreamPacket> streamPackets = new ConcurrentLinkedQueue<>();
    private List<ClientSocketListener> listeners = new ArrayList<>();
    private List<DisconnectedFromHostListener> disconnectionListeners = new ArrayList<>();
    private ConnectionErrorListener connectionErrorListener = null;
    private Thread thread;

    private Socket socket;
    private InputStream is;

    private Logger logger = LoggerFactory.getLogger(ClientToServerThread.class);

    //Output stream
    private OutputStream os;
    private Timer upWindPacketTimer = new Timer();
    private Timer downWindPacketTimer = new Timer();
    private boolean upwindTimerFlag = false, downwindTimerFlag = false;
    public static final int PACKET_SENDING_INTERVAL_MS = 100;

    private int clientId = -1;

    private ByteArrayOutputStream crcBuffer;
    private boolean socketOpen = true;

    /**
     * Constructor for ClientToServerThread which takes in ipAddress and portNumber and attempts to
     * connect to the specified ipAddress and port.
     *
     * Upon successful socket connection, threeWayHandshake will be preformed and the instance will
     * be put on a thread and run immediately.
     *
     * @param ipAddress a string of ip address to be connected to
     * @param portNumber an integer port number
     * @throws IOException SocketConnection if fail to connect to ip address and port number
     * combination
     */
    public ClientToServerThread(String ipAddress, Integer portNumber) throws IOException {
        socket = new Socket(ipAddress, portNumber);
        is = socket.getInputStream();
        os = socket.getOutputStream();

        sendRegistrationRequest();

        thread = new Thread(this, "ClientToServer");
        thread.start();
    }

    /**
     * Perform the thread loop. It exits the loop if ClientState connected to host
     * variable is false.
     */
    public void run() {
        isStarted = true;

        int sync1;
        int sync2;
        // TODO: 14/07/17 wmu16 - Work out how to fix this while loop
        while(!socket.isClosed() && socket.isConnected() && socketOpen) {
            try {
                crcBuffer = new ByteArrayOutputStream();
                sync1 = readByte();
                sync2 = readByte();
                //checking if it is the start of the packet
                if (sync1 == 0x47 && sync2 == 0x83) {
                    int type = readByte();
                    //No. of milliseconds since Jan 1st 1970
                    long timeStamp = Message.bytesToLong(getBytes(6));
                    skipBytes(4);
                    long payloadLength = Message.bytesToLong(getBytes(2));
                    byte[] payload = getBytes((int) payloadLength);
                    Checksum checksum = new CRC32();
                    checksum.update(crcBuffer.toByteArray(), 0, crcBuffer.size());
                    long computedCrc = checksum.getValue();
                    long packetCrc = Message.bytesToLong(getBytes(4));
                    if (computedCrc == packetCrc) {
                        if (streamPackets.size() > 0) {
                            streamPackets.add(new StreamPacket(type, payloadLength, timeStamp, payload));
                        } else {
                            if (PacketType.RACE_REGISTRATION_RESPONSE == PacketType.assignPacketType(type, payload)){
                                processRegistrationResponse(new StreamPacket(type, payloadLength, timeStamp, payload));
                            }
                            else {
                                if (clientId == -1) continue; // Do not continue if not registered
                                streamPackets.add(new StreamPacket(type, payloadLength, timeStamp, payload));
                                synchronized (this) {
                                    for (ClientSocketListener csl : listeners)
                                        csl.newPacket();
                                }
                            }
                        }
                    } else {
                        logger.warn("Packet has been dropped", 1);
                    }
                }
            } catch (ByteReadException e) {
                logger.warn("Byte read exception on ClientToServerThread", 1);
                notifyDisconnectListeners("Connection to server was interrupted");
                closeSocket();
            }
        }
        logger.warn("Closed connection to server", 1);
        notifyDisconnectListeners("Connection to server was terminated");
        closeSocket();

        //thread.interrupt();

//        Platform.runLater(() -> {
//            ViewManager.getInstance().showErrorSnackBar("Server rejected connection.");
//            ViewManager.getInstance().goToStartView();
//        });
    }

    public void sendCustomizationRequest(CustomizeRequestType reqType, byte[] payload) {
        CustomizeRequestMessage requestMessage = new CustomizeRequestMessage(reqType, this.clientId, payload);
        try {
            os.write(requestMessage.getBuffer());
        } catch (IOException e) {
            logger.error("Could not send customization request");
            notifyDisconnectListeners("Could not communicate with server");
            closeSocket();
        }
    }

    private void notifyDisconnectListeners (String message) {
        if (socketOpen) {
            for (DisconnectedFromHostListener listener : disconnectionListeners) {
                listener.notifyDisconnection(message);
            }
        }
    }

    private void handleConnectionError(String message){
        if (connectionErrorListener != null){
            connectionErrorListener.notifyConnectionError(message);
        }
    }

    /**
     * Sends a request to the server asking for a source ID
     */
    private void sendRegistrationRequest() {
        RegistrationRequestMessage requestMessage = new RegistrationRequestMessage(ClientType.PLAYER, clientId);

        try {
            os.write(requestMessage.getBuffer());
        } catch (IOException e) {
            logger.error("Could not send registration request. Exiting");
            notifyDisconnectListeners("Failed to register with server");
            closeSocket();
        }
    }

    /**
     * Accepts a response to the registration request message, and updates the client OR quits
     * @param packet The registration requests packet
     */
    private void processRegistrationResponse(StreamPacket packet){
        int sourceId = (int) Message.bytesToLong(Arrays.copyOfRange(packet.getPayload(), 0, 4));
        int statusCode = (int) Message.bytesToLong(Arrays.copyOfRange(packet.getPayload(), 4,5));
        RegistrationResponseStatus status = RegistrationResponseStatus.getResponseStatus(statusCode);

        if (status.equals(RegistrationResponseStatus.SUCCESS_PLAYING)){
            clientId = sourceId;
            return;
        }

        logger.error("Server Denied Connection, Exiting");

        final String alertErrorText;

        if (status.equals(RegistrationResponseStatus.FAILURE_FULL)){
            alertErrorText = "Server is full";
        }
        else{
            alertErrorText = "Could not connect to server";
        }
        handleConnectionError("Server no longer available.");
        notifyDisconnectListeners(alertErrorText);

        System.out.println();
    }

    /**
     * Sends packets for the given boat action. Special cases are: \n
     * - DOWNWIND = Packets are sent every ClientToServerThread.PACKET_SENDING_INTERVAL_MS
     * - UPWIND = Packets are sent every ClientToServerThread.PACKET_SENDING_INTERVAL_MS
     * - MAINTAIN_HEADING = DOWNWIND and UPWIND packets stop being sent.
     * @param actionType The boat action that will dictate packets sent.
     */
    public void sendBoatAction(BoatAction actionType) {
        switch (actionType) {
            case MAINTAIN_HEADING:
                if (upwindTimerFlag) {
                    cancelTimer(upWindPacketTimer);
                    upwindTimerFlag = false;
                    upWindPacketTimer = new Timer();
                }
                if (downwindTimerFlag) {
                    cancelTimer(downWindPacketTimer);
                    downwindTimerFlag = false;
                    downWindPacketTimer = new Timer();
                }
                break;
            case DOWNWIND:
                if (!downwindTimerFlag) {
                    downwindTimerFlag = true;
                    downWindPacketTimer.scheduleAtFixedRate(
                        new TimerTask() {
                            @Override
                            public void run() {
                                sendBoatActionMessage(new BoatActionMessage(BoatAction.DOWNWIND, clientId));
                            }
                        }, 0, PACKET_SENDING_INTERVAL_MS
                    );
                }
                break;
            case UPWIND:
                if (!upwindTimerFlag) {
                    upwindTimerFlag = true;
                    upWindPacketTimer.scheduleAtFixedRate(
                        new TimerTask() {
                            @Override
                            public void run() {
                                sendBoatActionMessage(new BoatActionMessage(BoatAction.UPWIND, clientId));
                            }
                        }, 0, PACKET_SENDING_INTERVAL_MS
                    );
                }
                break;
            default:
                sendBoatActionMessage(new BoatActionMessage(actionType, clientId));
                break;
        }
    }

    /**
     * Cancels a packet sending timer.
     * @param timer The timer to cancel.
     */
    private void cancelTimer (Timer timer) {
        timer.cancel();
        timer.purge();
    }

    /**
     * Sends a boat action of the given message type.
     * @param message The given message type.
     */
    private void sendBoatActionMessage(BoatActionMessage message) {
        sendByteBuffer(message.getBuffer());
    }

    public void sendChatterMessage(String message) {
        sendByteBuffer(new ChatterMessage(clientId, message).getBuffer());
    }

    private void sendByteBuffer(byte[] bytes) {
        if (clientId != -1) {
            try {
                os.write(bytes);
            } catch (IOException e) {
                logger.warn("IOException on attempting to sendBoatAction from Client");
                notifyDisconnectListeners("Cannot communicate with server");
                closeSocket();
            }
        }
    }

    public void closeSocket() {
        try {
            socket.close();
            socketOpen = false;
        } catch (IOException e) {
            logger.warn("IOException on attempting to close ClientToServerSocket");
        }
    }

    public void setSocketToClose () {
        socketOpen = false;
    }

    public Queue<StreamPacket> getPacketQueue () {
        return streamPackets;
    }

    public void addStreamObserver (ClientSocketListener streamListener) {
        synchronized (this){
            listeners.add(streamListener);
        }
    }

    public void removeStreamObserver (ClientSocketListener streamListener) {
        listeners.remove(streamListener);
    }

    public void addDisconnectionListener (DisconnectedFromHostListener listener) {
        synchronized (this){
            disconnectionListeners.add(listener);
        }
    }

    public void removeDisconnectionListener (DisconnectedFromHostListener listener) {
        synchronized (this){
            disconnectionListeners.remove(listener);
        }
    }

    public void setConnectionErrorListener(ConnectionErrorListener listener){
        synchronized (this){
            connectionErrorListener = listener;
        }
    }

    private int readByte() throws ByteReadException {
        int currentByte = -1;
        try {
            currentByte = is.read();
            crcBuffer.write(currentByte);
        } catch (IOException e) {
            logger.warn("IOException on readByte Client side", 1);
            notifyDisconnectListeners("Cannot read from server.");
            closeSocket();
        }
        if (currentByte == -1) {
            notifyDisconnectListeners("Cannot read from server.");
            logger.warn("InputStream reach end of stream", 1);
            handleConnectionError("Could not connect to server. Server is no longer available.");
            closeSocket();
        }
        return currentByte;
    }

    private byte[] getBytes(int n) throws ByteReadException {
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = (byte) readByte();
        }
        return bytes;
    }

    private void skipBytes(long n) throws ByteReadException {
        for (int i = 0; i < n; i++) {
            readByte();
        }
    }

    public int getClientId () {
        return clientId;
    }

    public void sendXML(String path, String serverName, Integer legRepeats, Integer maxPlayers, Boolean tokensEnabled) {
        Pair<RegattaXMLTemplate, RaceXMLTemplate> regattaRace = XMLParser.parseRaceDef(
            path, serverName, legRepeats, maxPlayers, tokensEnabled
        );
        XMLGenerator xmlGenerator = new XMLGenerator();
        xmlGenerator.setRegattaTemplate(regattaRace.getKey());
        xmlGenerator.setRaceTemplate(regattaRace.getValue());
        String regatta = xmlGenerator.getRegattaAsXml();
        String race = xmlGenerator.getRaceAsXml();
        sendByteBuffer(
            new XMLMessage(
                regatta, XMLMessageSubType.REGATTA, regatta.length()
            ).getBuffer()
        );
        sendByteBuffer(
            new XMLMessage(
                race, XMLMessageSubType.RACE, race.length()
            ).getBuffer()
        );
    }

    public boolean hasStarted() {
        return isStarted;
    }
}
