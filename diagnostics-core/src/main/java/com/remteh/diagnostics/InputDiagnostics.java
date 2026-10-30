package com.remteh.diagnostics;

/** Preserve OS API samples independently of the application's visual filter. */
public final class InputDiagnostics {
    private InputDiagnostics() {}
    public static final int SCHEMA = 1;
    public static final String[] AXES = {"left_x", "left_y", "right_x", "right_y", "left_trigger", "right_trigger"};
    public static float filterStick(float raw, float min, float max, float deadZone) {
        if (!finite(raw) || !finite(min) || !finite(max) || max <= min
                || !finite(deadZone) || deadZone < 0 || deadZone >= 1) return 0;
        float value = Math.max(-1, Math.min(1, 2 * (raw - min) / (max - min) - 1));
        if (Math.abs(value) <= deadZone) return 0;
        return Math.copySign((Math.abs(value) - deadZone) / (1 - deadZone), value);
    }
    public static float normalizeTrigger(float raw, float min, float max) {
        if (!finite(raw) || !finite(min) || !finite(max) || max <= min) return 0;
        return Math.max(0, Math.min(1, (raw - min) / (max - min)));
    }
    public static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
    public static final class Axis {
        private long samples;
        private double mean, m2, min, max, last;
        public void record(float raw) {
            if (!finite(raw)) return;
            if (samples == 0) min = max = raw;
            else { min = Math.min(min, raw); max = Math.max(max, raw); }
            samples++;
            double delta = raw - mean;
            mean += delta / samples;
            m2 += delta * (raw - mean);
            last = raw;
        }
        public long count() { return samples; }
        public double mean() { return samples == 0 ? Double.NaN : mean; }
        public double min() { return samples == 0 ? Double.NaN : min; }
        public double max() { return samples == 0 ? Double.NaN : max; }
        public double last() { return samples == 0 ? Double.NaN : last; }
        public double deviation() { return samples == 0 ? Double.NaN : Math.sqrt(m2 / samples); }
    }
    public static final class Session {
        private final Axis[] axes = new Axis[AXES.length];
        private final Axis[] resting = new Axis[4];
        private final Axis intervals = new Axis();
        private long previousMillis = -1;
        private long frames;
        public Session() {
            for (int i = 0; i < axes.length; i++) axes[i] = new Axis();
            for (int i = 0; i < resting.length; i++) resting[i] = new Axis();
        }
        public void record(float[] raw, long elapsedMillis, boolean restTest) {
            if (raw == null || raw.length != axes.length || elapsedMillis < 0)
                throw new IllegalArgumentException("Invalid input frame");
            for (int i = 0; i < axes.length; i++) axes[i].record(raw[i]);
            if (restTest) for (int i = 0; i < resting.length; i++) resting[i].record(raw[i]);
            if (previousMillis >= 0) {
                long delta = elapsedMillis - previousMillis;
                // Event intervals are not input-to-display latency; exclude user pauses.
                if (delta > 0 && delta <= 250) intervals.record(delta);
            }
            previousMillis = elapsedMillis;
            frames++;
        }
        public void pause() { previousMillis = -1; }
        public long frames() { return frames; }
        public Axis axis(int index) { return axes[index]; }
        public Axis restingAxis(int index) { return resting[index]; }
        public Axis intervals() { return intervals; }
    }
}
