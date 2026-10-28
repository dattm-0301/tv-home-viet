package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;

public class DataValidationTest {
    @Test public void coordinatesRejectInvalidNumbers() {
        assertEquals(21.0285, WeatherClient.coordinate(Double.NaN, -90, 90, 21.0285), 0);
        assertEquals(21.0285, WeatherClient.coordinate(91, -90, 90, 21.0285), 0);
        assertEquals(105.8542, WeatherClient.coordinate(Double.POSITIVE_INFINITY, -180, 180, 105.8542), 0);
        assertEquals(-90, WeatherClient.coordinate(-90, -90, 90, 21.0285), 0);
        assertEquals(0, WeatherClient.coordinate(0, -90, 90, 21.0285), 0);
    }
    @Test public void quoteTimesRespectOffsetsAndFractions() throws Exception {
        assertEquals(PriceClient.iso("2026-10-28T01:11:25.874Z"), PriceClient.iso("2026-10-28T08:11:25.874+07:00"));
        assertEquals(PriceClient.iso("2026-10-22T07:53:23.9Z"), PriceClient.iso("2026-10-22T07:53:23.900"));
    }
    @Test(expected=Exception.class) public void malformedTimeIsRejected() throws Exception {
        PriceClient.iso("not-a-time");
    }
}
