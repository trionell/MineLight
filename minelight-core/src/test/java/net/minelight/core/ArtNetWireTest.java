package net.minelight.core;

import net.minelight.core.artnet.ArtNetServer;
import net.minelight.core.engine.ConsoleEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the ArtDmx byte layout.
 *
 * <p>Art-Net is mixed-endian: the Port-Address is sent low byte first while
 * the length two bytes later is sent high byte first. Getting that backwards
 * still produces a packet a console will accept — it simply files the data
 * under a different universe, so the desk sees the node, reports no error and
 * stays dark. Only a byte-level assertion catches it.</p>
 */
class ArtNetWireTest {

    private static byte[] build(int universe) throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-artnet"));
        ArtNetServer server = new ArtNetServer(engine);
        Method m = ArtNetServer.class.getDeclaredMethod("buildArtDmx", int.class, byte[].class);
        m.setAccessible(true);
        return (byte[]) m.invoke(server, universe, new byte[512]);
    }

    @Test
    void headerAndOpcodeMatchTheSpec() throws Exception {
        byte[] p = build(1);
        assertArrayEquals("Art-Net\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                java.util.Arrays.copyOfRange(p, 0, 8));
        assertEquals(0x00, p[8] & 0xFF, "OpCode low byte");
        assertEquals(0x50, p[9] & 0xFF, "OpCode high byte (OpDmx = 0x5000)");
        assertEquals(0, p[10] & 0xFF, "ProtVerHi");
        assertEquals(14, p[11] & 0xFF, "ProtVerLo");
        assertEquals(512, ((p[16] & 0xFF) << 8) | (p[17] & 0xFF), "length is high byte first");
        assertEquals(530, p.length);
    }

    @Test
    void portAddressIsSentLowByteFirst() throws Exception {
        byte[] p = build(1);
        assertEquals(0x01, p[14] & 0xFF, "SubUni carries subnet and universe");
        assertEquals(0x00, p[15] & 0xFF, "Net is the high byte");
    }

    @Test
    void netAndUniverseSplitAtTheRightBit() throws Exception {
        // port address 256 is Net 1, Universe 0 — the boundary the old code
        // straddled by writing the field the wrong way round
        byte[] p = build(256);
        assertEquals(0x00, p[14] & 0xFF);
        assertEquals(0x01, p[15] & 0xFF);
    }

    @Test
    void ourOwnReceiverAgreesWithOurOwnSender() throws Exception {
        for (int universe : new int[]{0, 1, 2, 15, 16, 255, 256, 4096}) {
            byte[] p = build(universe);
            int parsed = ((p[14] & 0xFF) | ((p[15] & 0xFF) << 8)) & 0x7FFF;
            assertEquals(universe, parsed, "round trip for universe " + universe);
        }
    }

    @Test
    void everyUniverseTheEngineDrivesGetsTransmitted() throws Exception {
        ConsoleEngine engine = new ConsoleEngine(Files.createTempDirectory("minelight-artnet"));
        ArtNetServer server = new ArtNetServer(engine);
        server.enableUniverse(1);
        engine.setDmx(4, 1, 255); // a block retargeted in game, never enabled here

        Method m = ArtNetServer.class.getDeclaredMethod("universesToSend");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        var sending = (java.util.Set<Integer>) m.invoke(server);
        assertTrue(sending.contains(1), "explicitly enabled");
        assertTrue(sending.contains(4), "driven by the engine");

        server.disableUniverse(4);
        @SuppressWarnings("unchecked")
        var after = (java.util.Set<Integer>) m.invoke(server);
        assertFalse(after.contains(4), "an explicit disable still wins");
    }
}
