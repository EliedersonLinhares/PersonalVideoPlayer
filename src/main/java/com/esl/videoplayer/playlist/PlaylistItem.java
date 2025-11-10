package com.esl.videoplayer.playlist;

import java.io.File;

public class PlaylistItem {
    private String filePath;
    private String displayName;
    private long duration; // em segundos
    private boolean played; // Marca se já foi tocada

    public PlaylistItem(String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        this.displayName = file.getName();
        this.duration = 0;
        this.played = false;
    }

    public PlaylistItem(String filePath, String displayName, long duration) {
        this.filePath = filePath;
        this.displayName = displayName;
        this.duration = duration;
        this.played = false;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isPlayed() {
        return played;
    }

    public void setDisplayName(String name) {
        this.displayName = name;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setPlayed(boolean played) {
        this.played = played;
    }

    @Override
    public String toString() {
        String prefix = played ? "X - " : "";
        if (duration > 0) {
            return prefix + displayName + " [" + formatDuration(duration) + "]";
        }
        return prefix + displayName;
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }
}
