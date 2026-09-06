package com.esl.videoplayer.playlist;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Persiste o progresso de reprodução de cada playlist (.m3u) em disco,
 * permitindo retomar de onde o usuário parou da próxima vez que abrir
 * a mesma playlist.
 */
public class PlaylistStateManager {

    private static final String STATE_DIR_NAME = "playlist_states";
    private final File stateDir;

    public PlaylistStateManager() {
        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, ".videoplayer");
        this.stateDir = new File(appDir, STATE_DIR_NAME);
        if (!stateDir.exists()) {
            stateDir.mkdirs();
        }
    }

    public static class PlaylistState {
        public String currentTrackPath;
        public Set<String> playedTrackPaths = new HashSet<>();
    }

    private File getStateFile(String playlistFilePath) {
        return new File(stateDir, hash(playlistFilePath) + ".state");
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode()); // fallback improvável de ser necessário
        }
    }

    public boolean hasSavedState(String playlistFilePath) {
        File f = getStateFile(playlistFilePath);
        return f.exists() && f.length() > 0;
    }

    public PlaylistState loadState(String playlistFilePath) {
        PlaylistState state = new PlaylistState();
        File f = getStateFile(playlistFilePath);
        if (!f.exists()) return state;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Erro ao carregar estado da playlist: " + e.getMessage());
            return state;
        }

        state.currentTrackPath = props.getProperty("current", null);
        String playedRaw = props.getProperty("played", "");
        if (!playedRaw.isBlank()) {
            state.playedTrackPaths.addAll(Arrays.asList(playedRaw.split("\\|")));
        }
        return state;
    }

    public void saveState(String playlistFilePath, PlaylistState state) {
        File f = getStateFile(playlistFilePath);
        Properties props = new Properties();
        props.setProperty("playlist_path", playlistFilePath); // só para debug/rastreio
        if (state.currentTrackPath != null) {
            props.setProperty("current", state.currentTrackPath);
        }
        props.setProperty("played", String.join("|", state.playedTrackPaths));

        try (FileOutputStream out = new FileOutputStream(f)) {
            props.store(out, "Estado de reprodução da playlist - gerado automaticamente");
        } catch (IOException e) {
            System.err.println("Erro ao salvar estado da playlist: " + e.getMessage());
        }
    }

    public void clearState(String playlistFilePath) {
        File f = getStateFile(playlistFilePath);
        if (f.exists()) {
            f.delete();
        }
    }
}