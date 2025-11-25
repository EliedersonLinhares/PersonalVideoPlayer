package com.esl.videoplayer.audio;

public enum LoudnessPreset {
    STREAMING(-14.0f, "Streaming (Spotify/YouTube)"),
    BROADCAST(-23.0f, "Broadcast TV/Radio"),
    CINEMA(-11.0f, "Cinema"),
    LOUD(-11.0f, "Loud (Club/Party)"),
    QUIET(-18.0f, "Quiet (Background)");

    private final float dbFS;
    private final String description;

    LoudnessPreset(float dbFS, String description) {
        this.dbFS = dbFS;
        this.description = description;
    }

    public float getDbFS() {
        return dbFS;
    }

    public String getDescription() {
        return description;
    }
}
