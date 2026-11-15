package app.nook.tv;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class RulesTest {
    @Test public void ltaOccupancyUsesCapacityColors(){
        assertEquals(Rules.Occupancy.SEATS,Rules.occupancy("SEA"));
        assertEquals(Rules.Occupancy.STANDING,Rules.occupancy("SDA"));
        assertEquals(Rules.Occupancy.UNKNOWN,Rules.occupancy("SDD"));
        assertEquals(Rules.Occupancy.LIMITED,Rules.occupancy("LSD"));
        assertEquals("Seats",Rules.occupancy("SEA").label);
        assertEquals("Standing",Rules.occupancy("SDA").label);
        assertEquals("Limited",Rules.occupancy("LSD").label);
        assertEquals(0xff4ade80,Rules.occupancy("SEA").color);
        assertEquals(0xfffb923c,Rules.occupancy("SDA").color);
        assertEquals(0xfff87171,Rules.occupancy("LSD").color);
        for(String load:new String[]{null,"","unexpected","SDD","SD","DD","BD"}){
            assertEquals(Rules.Occupancy.UNKNOWN,Rules.occupancy(load));
            assertEquals("",Rules.occupancy(load).label);
        }
    }
    @Test public void busCardsUseFreeSpaceWithoutMovingFavourites(){
        assertEquals(220,Rules.busCardHeight(144,250,220,12));
        assertEquals(238,Rules.busCardHeight(144,250,400,12));
        assertEquals(144,Rules.busCardHeight(144,144,400,12));
        assertEquals(144,Rules.busCardHeight(144,250,100,12));
        assertEquals(420,Rules.busCardHeight(180,500,420,12));
        for(int available=144;available<800;available+=17){
            int height=Rules.busCardHeight(144,available,600,12);
            assertTrue(height<=available);
            assertTrue(available-height>=Math.min(12,available-144));
        }
    }
    @Test public void settingsRoutesSkipTvPlaceholderActivities(){
        assertTrue(Rules.settingsPlaceholder("com.google.android.tv.frameworkpackagestubs","com.google.android.tv.frameworkpackagestubs.Stubs$SettingsStub"));
        assertTrue(Rules.settingsPlaceholder("com.android.tv.settings","com.android.tv.settings.EmptyStubActivity"));
        assertFalse(Rules.settingsPlaceholder("com.android.tv.settings","com.android.tv.settings.MainSettings"));
        assertFalse(Rules.settingsPlaceholder("com.google.android.permissioncontroller","com.android.packageinstaller.role.ui.HomeSettingsActivity"));
        assertFalse(Rules.settingsPlaceholder(null,null));
    }
    @Test public void serviceInputIsNormalisedAndDeduplicated(){assertEquals(Arrays.asList("10","20A","CT8"),Rules.services("10, 20a, 10, CT8"));}
    @Test(expected=IllegalArgumentException.class) public void rejectEmptyServices(){Rules.services(" , ");}
    @Test(expected=IllegalArgumentException.class) public void rejectTooManyServices(){Rules.services("1,2,3,4,5,6,7,8,9");}
    @Test(expected=IllegalArgumentException.class) public void rejectInvalidStopCodes(){Rules.stop("Sample stop","1234");}
    @Test public void displayTimezoneAndScheduledEstimates(){
        long noonUtc=1577880000000L;
        assertEquals("5 min",Rules.eta("2020-01-01T20:05:00+08:00",1,noonUtc));
        assertEquals("~5 min",Rules.eta("2020-01-01T12:05:00Z",0,noonUtc));
        assertEquals("Arriving",Rules.eta("2020-01-01T12:00:30Z",1,noonUtc));
    }
    @Test public void expiredOrMissingEstimatesNeverBecomeSampleNumbers(){
        long noonUtc=1577880000000L;
        assertEquals("—",Rules.eta("2020-01-01T11:58:00Z",1,noonUtc));
        assertEquals("—",Rules.eta("",1,noonUtc));assertEquals("—",Rules.eta("invalid",1,noonUtc));
    }
}
