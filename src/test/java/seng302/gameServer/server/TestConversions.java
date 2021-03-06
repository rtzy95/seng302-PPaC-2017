package seng302.gameServer.server;

import static junit.framework.TestCase.assertEquals;

import org.junit.Test;
import seng302.gameServer.messages.BoatLocationMessage;

/**
 * Test conversions used by the boat location messages
 */
public class TestConversions {
    @Test
    public void testLatLonConversion(){
        long binaryPacked = BoatLocationMessage.latLonToBinaryPackedLong(3232.323);
        double original = BoatLocationMessage.binaryPackedToLatLon(binaryPacked);

        assertEquals(3232.323, original, 0.01);
    }

    @Test
    public void testWindAngleConversion(){
        long binaryPacked = BoatLocationMessage.windAngleToBinaryPackedLong(3232.323);
        double original = BoatLocationMessage.binaryPackedWindAngleToDouble(binaryPacked);

        assertEquals(3232.323, original, 0.01);
    }

    @Test
    public void testHeadingConversion(){
        long binaryPacked = BoatLocationMessage.headingToBinaryPackedLong(3232.323);
        double original = BoatLocationMessage.binaryPackedHeadingToDouble(binaryPacked);

        assertEquals(3232.323, original, 0.01);
    }
}
