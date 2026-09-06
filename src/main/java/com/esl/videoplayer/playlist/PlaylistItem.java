package com.esl.videoplayer.playlist;

import java.io.File;
import java.util.Set;

public class PlaylistItem {
    private String filePath;
    private String displayName; // Nome do arquivo (fallback) ou título da música
    private String artist;      // Autor/Cantor
    private long duration;      // em segundos
    private boolean played;
    private boolean audio;      // true = arquivo de áudio

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "ac3", "aiff"
    );

    public PlaylistItem(String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        this.displayName = file.getName();
        this.artist = "";
        this.duration = 0;
        this.played = false;
        this.audio = isAudioFile(filePath);
    }

    public PlaylistItem(String filePath, String displayName, long duration) {
        this.filePath = filePath;
        this.displayName = displayName;
        this.artist = "";
        this.duration = duration;
        this.played = false;
        this.audio = isAudioFile(filePath);
    }

    private static boolean isAudioFile(String filePath) {
        String lower = filePath.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return false;
        return AUDIO_EXTENSIONS.contains(lower.substring(dot + 1));
    }

    // ===== getters/setters já existentes (mantidos) =====

    public String getFilePath() { return filePath; }
    public String getDisplayName() { return displayName; }
    public long getDuration() { return duration; }
    public boolean isPlayed() { return played; }
    public void setDisplayName(String name) { this.displayName = name; }
    public void setDuration(long duration) { this.duration = duration; }
    public void setPlayed(boolean played) { this.played = played; }

    // ===== novos =====
    public String getArtist() { return artist == null ? "" : artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public boolean isAudio() { return audio; }

    /**
     * Formata o texto exibido na lista.
     * - Vídeo: mantém o formato atual (nome + duração).
     * - Áudio: [posição] - [nome] - [autor] - [duração], deixando em branco
     *   o que não estiver disponível.
     */
    public String toDisplayString(int position) {
        String prefix = played ? "X - " : "";

        if (!audio) {
            if (duration > 0) {
                return prefix + displayName + " [" + formatDuration(duration) + "]";
            }
            return prefix + displayName;
        }

        String name = (displayName != null && !displayName.isBlank()) ? displayName : "";
        String art = (artist != null && !artist.isBlank()) ? artist : "";
        String dur = duration > 0 ? formatDuration(duration) : "";

        return prefix + position + " - " + name + " - " + art + " - " + dur;
    }

    @Override
    public String toString() {
        // Mantido como fallback (sem número de posição, que só o JList conhece)
        return toDisplayString(0);
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