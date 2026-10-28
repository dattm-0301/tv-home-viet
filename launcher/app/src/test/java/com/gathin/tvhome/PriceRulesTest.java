package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PriceRulesTest {
    private static final long NOW = 10_000_000L, GRACE = 1_800_000L;
    @Test public void goldPrecisionDoesNotLoseTenThousandDongSteps() {
        assertEquals("139,75", PriceRules.goldMillions(139_750_000));
        assertEquals("142,7", PriceRules.goldMillions(142_700_000));
        assertEquals("142,0", PriceRules.goldMillions(142_000_000));
    }
    @Test public void acceptsActualPublisherNamesFromFeed() {
        assertTrue(PriceRules.publisherMatches("doji", "DOJI"));
        assertTrue(PriceRules.publisherMatches("pnj", "PNJ"));
        assertTrue(PriceRules.publisherMatches("phuquy", "Phú Quý"));
        assertTrue(PriceRules.publisherMatches("btmh", "btmh"));
        assertFalse(PriceRules.publisherMatches("doji", "PNJ"));
        assertFalse(PriceRules.publisherMatches("unknown", "unknown"));
        assertFalse(PriceRules.publisherMatches("doji", null));
    }
    @Test public void primaryObservationCanReplaceLaterFallbackCheckTime() {
        assertTrue(PriceRules.replaceGold(NOW-3_600_000L,false,NOW,true,NOW,NOW,GRACE));
    }
    @Test public void fallbackCannotOverwriteRecentlyConfirmedPrimary() {
        assertFalse(PriceRules.replaceGold(NOW,true,NOW-3_600_000L,false,NOW-300_000L,NOW,GRACE));
    }
    @Test public void fallbackCanServeWhenPrimaryCannotBeReconfirmed() {
        assertTrue(PriceRules.replaceGold(NOW,true,NOW-5_400_000L,false,NOW-GRACE-1,NOW,GRACE));
    }
    @Test public void comparableSourceTimesDoNotRegress() {
        assertFalse(PriceRules.replaceGold(NOW-1,false,NOW,false,NOW,NOW,GRACE));
        assertFalse(PriceRules.replaceGold(NOW-1,true,NOW,true,NOW,NOW,GRACE));
        assertTrue(PriceRules.replaceGold(NOW,false,NOW,false,NOW-300_000L,NOW,GRACE));
    }
    @Test public void checksResumeAfterDeviceClockRollsBack() {
        assertTrue(PriceRules.due(NOW,NOW+1,300_000L));
        assertTrue(PriceRules.due(NOW,0,300_000L));
        assertFalse(PriceRules.due(NOW,NOW-1,300_000L));
        assertFalse(PriceRules.fresh(NOW,NOW+1,300_000L));
        assertFalse(PriceRules.fresh(NOW,0,300_000L));
        assertTrue(PriceRules.fresh(NOW,NOW-1,300_000L));
    }
}
