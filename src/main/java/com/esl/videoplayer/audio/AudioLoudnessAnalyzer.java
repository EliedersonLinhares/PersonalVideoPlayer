package com.esl.videoplayer.audio;

public  class AudioLoudnessAnalyzer {
    private float runningSum = 0.0f;
    private int sampleCount = 0;
    private float peakLevel = 0.0f;

    public void analyzeSamples(short[] samples) {
        float sum = 0.0f;
        float peak = 0.0f;

        for (short sample : samples) {
            float normalized = sample / 32768.0f; // Normalizar para -1.0 a 1.0
            sum += normalized * normalized; // RMS
            peak = Math.max(peak, Math.abs(normalized));
        }

        runningSum += sum;
        sampleCount += samples.length;
        peakLevel = Math.max(peakLevel, peak);
    }

    public float getRMS() {
        if (sampleCount == 0) return 0.0f;
        return (float) Math.sqrt(runningSum / sampleCount);
    }

    public float getPeakLevel() {
        return peakLevel;
    }

    public float getDbFS() {
        float rms = getRMS();
        if (rms <= 0.0f) return -100.0f;
        return 20.0f * (float) Math.log10(rms);
    }

    public void reset() {
        runningSum = 0.0f;
        sampleCount = 0;
        peakLevel = 0.0f;
    }
}
