package app.nook.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class EverywhereTest {
    @Test public void defaultsMatchTheSpecification(){
        assertEquals(Everywhere.Corner.BOTTOM_RIGHT,Everywhere.corner(null));
        assertEquals(Everywhere.Corner.BOTTOM_RIGHT,Everywhere.corner("NOWHERE"));
        assertEquals(Everywhere.Size.SMALL,Everywhere.size(null));
        assertEquals(30,Everywhere.opacity(Everywhere.DEFAULT_OPACITY));
        assertEquals(30,Everywhere.opacity(77));
        assertEquals(4,Everywhere.Corner.values().length);
    }
    @Test public void savedChoicesRoundTripByName(){
        for(Everywhere.Corner c:Everywhere.Corner.values())assertEquals(c,Everywhere.corner(c.name()));
        for(Everywhere.Size s:Everywhere.Size.values())assertEquals(s,Everywhere.size(s.name()));
        for(int o:Everywhere.OPACITIES)assertEquals(o,Everywhere.opacity(o));
        assertTrue(Everywhere.Corner.TOP_LEFT.label.equals("Top left")&&!Everywhere.Corner.TOP_LEFT.bottom&&!Everywhere.Corner.TOP_LEFT.right);
    }
    @Test public void neverTwoOrbsOnScreen(){
        assertTrue(Everywhere.showOverlay(true,true,true,false,false));
        assertFalse("launcher shows its own orb",Everywhere.showOverlay(true,true,true,true,false));
        assertFalse("panel shows its own orb",Everywhere.showOverlay(true,true,true,false,true));
        assertFalse("off",Everywhere.showOverlay(false,true,true,false,false));
        assertFalse("permission revoked",Everywhere.showOverlay(true,false,true,false,false));
        assertFalse("screen off pauses rendering",Everywhere.showOverlay(true,true,false,false,false));
    }
    @Test public void idleOrbDimsAndNeverVanishes(){
        assertEquals(.3f,Everywhere.alpha(30,true,0),1e-6);
        assertEquals(.15f,Everywhere.alpha(30,true,Everywhere.DIM_AFTER_MS),1e-6);
        assertEquals(.3f,Everywhere.alpha(30,false,Everywhere.DIM_AFTER_MS*10),1e-6);
        assertEquals(.075f,Everywhere.alpha(15,true,Everywhere.DIM_AFTER_MS),1e-6);
        assertTrue(Everywhere.alpha(15,true,Long.MAX_VALUE)>=.05f);
        assertEquals(1f,Everywhere.alpha(100,false,0),1e-6);
    }
    @Test public void burnInShiftStaysSmallAndInward(){
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(long t=0;t<Everywhere.SHIFT_EVERY_MS*20;t+=Everywhere.SHIFT_EVERY_MS){
            int[] s=Everywhere.shift(t);
            assertTrue(s[0]>=0&&s[0]<=4&&s[1]>=0&&s[1]<=4);
            seen.add(s[0]+","+s[1]);
        }
        assertTrue("the orb actually moves",seen.size()>=3);
        assertArrayEquals(Everywhere.shift(0),Everywhere.shift(Everywhere.SHIFT_EVERY_MS-1));
    }
    @Test public void overlayIdlesAtFiveFramesPerSecond(){
        assertEquals(200,Everywhere.overlayFrameDelay(true,0f));
        assertEquals(0,Everywhere.overlayFrameDelay(false,0f));
        assertEquals(0,Everywhere.overlayFrameDelay(true,.6f));
    }
}
