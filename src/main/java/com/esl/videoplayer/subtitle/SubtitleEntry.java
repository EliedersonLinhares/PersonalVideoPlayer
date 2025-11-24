package com.esl.videoplayer.subtitle;

public class SubtitleEntry {
    long startTime; // em milissegundos
    long endTime;
    String text;

    public SubtitleEntry(long start, long end, String txt) {
        this.startTime = start;
        this.endTime = end;
        this.text = txt;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
