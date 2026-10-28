package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class InputStatusTest {
    @Test public void standbyIsNeverConnectedOrDisconnected() {
        assertEquals(R.string.input_standby_uncertain, InputStatus.text(1));
        assertEquals(R.string.connected, InputStatus.text(0));
        assertEquals(R.string.not_connected, InputStatus.text(2));
        assertEquals(0, InputStatus.text(-1));
    }
}
