package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class StormWingInputPacketTest {
    @Test
    void collapsesImpulsesToUnitAxes() {
        assertEquals(1, StormWingInputPacket.axis(0.98f));
        assertEquals(-1, StormWingInputPacket.axis(-0.3f));
        assertEquals(0, StormWingInputPacket.axis(0f));
        assertEquals(0, StormWingInputPacket.axis(Float.NaN));
        // A crafted byte can never scale the flight vector beyond one key press.
        assertEquals(1, StormWingInputPacket.axis(Byte.MAX_VALUE));
        assertEquals(-1, StormWingInputPacket.axis(Byte.MIN_VALUE));
    }

    @Test
    void codecRoundTripsBothAxes() {
        ByteBuf buf = Unpooled.buffer();
        StormWingInputPacket.STREAM_CODEC.encode(buf, StormWingInputPacket.of(1f, -1f));
        StormWingInputPacket decoded = StormWingInputPacket.STREAM_CODEC.decode(buf);
        assertEquals(1, decoded.forward());
        assertEquals(-1, decoded.left());
    }
}
