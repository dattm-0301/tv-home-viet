package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class UserOptionsTest {
    @Test public void coordinatesAcceptGlobalBounds() {
        assertEquals(-90, UserOptions.coordinate(-90,-90,90),0);
        assertEquals(180, UserOptions.coordinate(180,-180,180),0);
    }
    @Test(expected=IllegalArgumentException.class) public void nanCannotBecomeAPlace() { UserOptions.coordinate(Double.NaN,-90,90); }
    @Test(expected=IllegalArgumentException.class) public void infiniteCannotBecomeAPlace() { UserOptions.coordinate(Double.POSITIVE_INFINITY,-180,180); }
    @Test(expected=IllegalArgumentException.class) public void latitudeOutOfRange() { UserOptions.coordinate(91,-90,90); }
    @Test public void validZonesAndIntegerRanges() {
        assertEquals("Asia/Tokyo",UserOptions.zone("Asia/Tokyo"));
        assertNotNull(UserOptions.zone("system"));
        assertEquals(0,UserOptions.integer(0,0,1440));
        assertEquals(8,UserOptions.integer(8,4,8));
    }
    @Test(expected=java.time.DateTimeException.class) public void unknownZoneDoesNotFallBackSilently() { UserOptions.zone("Wrong/City"); }
    @Test(expected=IllegalArgumentException.class) public void fractionsAreNotMinutes() { UserOptions.integer(5.5,5,240); }
}
