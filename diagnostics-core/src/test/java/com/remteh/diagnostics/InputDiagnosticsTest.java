package com.remteh.diagnostics;
import org.junit.Test;
import static org.junit.Assert.*;

public class InputDiagnosticsTest {
    @Test public void driftSurvivesWhenVisualDeadZoneHidesIt() {
        InputDiagnostics.Session session = new InputDiagnostics.Session();
        session.record(new float[]{.05f, -.03f, 0, 0, Float.NaN, 0}, 100, true);
        assertEquals(0, InputDiagnostics.filterStick(.05f, -1, 1, .08f), 0);
        assertEquals(.05, session.axis(0).last(), .00001);
        assertEquals(.05, session.restingAxis(0).mean(), .00001);
        assertEquals(0, session.axis(4).count());
        assertTrue(Double.isNaN(session.axis(4).mean()));
    }
    @Test public void rangesDescribeSamplesInsteadOfAssumedNeutral() {
        InputDiagnostics.Axis axis = new InputDiagnostics.Axis();
        axis.record(.2f); axis.record(.4f);
        assertEquals(.2, axis.min(), .00001);
        assertEquals(.4, axis.max(), .00001);
        assertEquals(.3, axis.mean(), .00001);
        assertEquals(.1, axis.deviation(), .00001);
    }
    @Test public void movementDoesNotEnterTheExplicitRestTest() {
        InputDiagnostics.Session s = new InputDiagnostics.Session();
        s.record(new float[]{1,1,1,1,1,1}, 100, false);
        s.record(new float[]{.02f,0,0,0,0,0}, 108, true);
        assertEquals(.02, s.restingAxis(0).mean(), .00001);
        assertEquals(1, s.restingAxis(0).count());
    }
    @Test public void pausesAndClockChangesAreNotReportedAsLatency() {
        InputDiagnostics.Session s = new InputDiagnostics.Session();
        float[] frame = new float[6];
        for (long time : new long[]{100, 108, 116, 5000, 50}) s.record(frame,time,false);
        assertEquals(2, s.intervals().count());
        assertEquals(8, s.intervals().mean(), 0);
        s.pause(); s.record(frame, 60, false);
        assertEquals(2, s.intervals().count());
    }
    @Test public void nonStandardRangesAndBoundsAreHandled() {
        assertEquals(0, InputDiagnostics.filterStick(128, 0, 256, .1f), 0);
        assertEquals(1, InputDiagnostics.filterStick(300, 0, 256, .1f), 0);
        assertEquals(0, InputDiagnostics.filterStick(Float.NaN, -1, 1, .1f), 0);
        assertEquals(0, InputDiagnostics.filterStick(.5f, -1, 1, 1), 0);
        assertEquals(.5, InputDiagnostics.normalizeTrigger(0, -1, 1), 0);
    }
}
