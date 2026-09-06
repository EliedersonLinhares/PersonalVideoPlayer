package com.esl.videoplayer.playlist;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.configuration.ConfigManager;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.*;
import java.util.*;

public class PlaylistManager {
    private List<PlaylistItem> playlist;
    private int currentIndex;
    private boolean shuffle;
    private boolean repeat;
    private boolean repeatOne;
    private List<Integer> shuffleOrder;
    private Random random;
    private VideoPlayer videoPlayer;

    private final PlaylistStateManager stateManager = new PlaylistStateManager();
    private String loadedPlaylistFilePath; // caminho do .m3u atualmente carregado

    public VideoPlayer getVideoPlayer() {
        return videoPlayer;
    }

//    public PlaylistManager(VideoPlayer videoPlayer) {
//        this.videoPlayer = videoPlayer;
//        playlist = new ArrayList<>();
//        currentIndex = -1;
//        shuffle = false;
//        repeat = false;
//        repeatOne = false;
//        shuffleOrder = new ArrayList<>();
//        random = new Random();
//    }
public PlaylistManager(VideoPlayer videoPlayer) {
    this.videoPlayer = videoPlayer;
    playlist = new ArrayList<>();
    currentIndex = -1;
    shuffleOrder = new ArrayList<>();
    random = new Random();

    // Carrega os estados salvos (se houver ConfigManager disponível)
    ConfigManager config = getConfigManager();
    if (config != null) {
        this.shuffle   = config.isSavedPlaylistShuffle();
        this.repeat    = config.isSavedPlaylistRepeat();
        this.repeatOne = config.isSavedPlaylistRepeatOne();
    } else {
        this.shuffle = false;
        this.repeat = false;
        this.repeatOne = false;
    }
}

    private ConfigManager getConfigManager() {
        return videoPlayer != null ? videoPlayer.getConfigManager() : null;
    }

    // ===== Gerenciamento de itens =====

//    public void addItem(PlaylistItem item) {
//        playlist.add(item);
//        updateShuffleOrder();
//    }
//
//    public void addItems(List<PlaylistItem> items) {
//        playlist.addAll(items);
//        updateShuffleOrder();
//    }

    public void addItem(PlaylistItem item) {
        if (item.isAudio()) {
            loadAudioMetadataIfMissing(item);
        }
        playlist.add(item);
        updateShuffleOrder();
    }

    public void addItems(List<PlaylistItem> items) {
        for (PlaylistItem item : items) {
            if (item.isAudio()) {
                loadAudioMetadataIfMissing(item);
            }
        }
        playlist.addAll(items);
        updateShuffleOrder();
    }

    public void removeItem(int index) {
        if (index >= 0 && index < playlist.size()) {
            playlist.remove(index);
            if (currentIndex >= playlist.size()) {
                currentIndex = playlist.size() - 1;
            }
            updateShuffleOrder();
        }
    }

    public void clear() {
        playlist.clear();
        currentIndex = -1;
        shuffleOrder.clear();
    }

    public void moveItem(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < playlist.size() &&
                toIndex >= 0 && toIndex < playlist.size()) {
            PlaylistItem item = playlist.remove(fromIndex);
            playlist.add(toIndex, item);

            // Ajustar currentIndex
            if (currentIndex == fromIndex) {
                currentIndex = toIndex;
            } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
                currentIndex--;
            } else if (fromIndex > currentIndex && toIndex <= currentIndex) {
                currentIndex++;
            }

            updateShuffleOrder();
        }
    }

    // ===== Gerenciamento de marcação =====

//    public void markCurrentAsPlayed() {
//        if (currentIndex >= 0 && currentIndex < playlist.size()) {
//            playlist.get(currentIndex).setPlayed(true);
//            System.out.println("Música marcada como tocada: " + playlist.get(currentIndex).getDisplayName());
//
//            // Verificar se todas foram tocadas
//            if (allTracksPlayed()) {
//                System.out.println("Todas as músicas foram tocadas! Desmarcando todas...");
//                resetAllPlayedMarks();
//            }
//        }
//    }
public void markCurrentAsPlayed() {
    if (currentIndex >= 0 && currentIndex < playlist.size()) {
        playlist.get(currentIndex).setPlayed(true);
        System.out.println("Música marcada como tocada: " + playlist.get(currentIndex).getDisplayName());

        if (allTracksPlayed()) {
            System.out.println("Todas as músicas foram tocadas! Desmarcando todas...");
            resetAllPlayedMarks();
        }

        saveCurrentState();
    }
}

    private boolean allTracksPlayed() {
        for (PlaylistItem item : playlist) {
            if (!item.isPlayed()) {
                return false;
            }
        }
        return true;
    }

    public void resetAllPlayedMarks() {
        for (PlaylistItem item : playlist) {
            item.setPlayed(false);
        }
        System.out.println("Todas as marcações foram resetadas");
    }

    public int getUnplayedCount() {
        int count = 0;
        for (PlaylistItem item : playlist) {
            if (!item.isPlayed()) {
                count++;
            }
        }
        return count;
    }

    // ===== Navegação =====

    public PlaylistItem getCurrentItem() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

//    public PlaylistItem next() {
//        if (playlist.isEmpty()) return null;
//
//        if (repeatOne) {
//            return getCurrentItem();
//        }
//
//        if (shuffle) {
//            PlaylistItem nextItem = getNextUnplayedInShuffle();
//            if (nextItem != null) {
//                return nextItem;
//            }
//
//            if (repeat) {
//                resetAllPlayedMarks();
//                updateShuffleOrder();
//                currentIndex = shuffleOrder.get(0);
//                return getCurrentItem();
//            }
//
//            return null;
//
//        } else {
//            currentIndex++;
//            if (currentIndex >= playlist.size()) {
//                if (repeat) {
//                    currentIndex = 0;
//                } else {
//                    currentIndex = playlist.size() - 1;
//                    return null;
//                }
//            }
//        }
//
//        return getCurrentItem();
//    }

    public PlaylistItem next() {
        if (playlist.isEmpty()) return null;

        if (repeatOne) {
            return getCurrentItem();
        }

        if (shuffle) {
            PlaylistItem nextItem = getNextUnplayedInShuffle();
            if (nextItem != null) {
                saveCurrentState();
                return nextItem;
            }

            if (repeat) {
                resetAllPlayedMarks();
                updateShuffleOrder();
                currentIndex = shuffleOrder.get(0);
                saveCurrentState();
                return getCurrentItem();
            }

            return null;

        } else {
            currentIndex++;
            if (currentIndex >= playlist.size()) {
                if (repeat) {
                    currentIndex = 0;
                } else {
                    currentIndex = playlist.size() - 1;
                    return null;
                }
            }
        }

        saveCurrentState();
        return getCurrentItem();
    }


    private PlaylistItem getNextUnplayedInShuffle() {
        List<Integer> unplayedIndices = new ArrayList<>();

        for (int i = 0; i < playlist.size(); i++) {
            if (!playlist.get(i).isPlayed() && i != currentIndex) {
                unplayedIndices.add(i);
            }
        }

        if (unplayedIndices.isEmpty()) {
            return null;
        }

        int randomIndex = random.nextInt(unplayedIndices.size());
        currentIndex = unplayedIndices.get(randomIndex);

        System.out.println("Shuffle: Escolhida música não tocada - " +
                playlist.get(currentIndex).getDisplayName() +
                " (restam " + unplayedIndices.size() + " não tocadas)");

        return getCurrentItem();
    }

//    public PlaylistItem previous() {
//        if (playlist.isEmpty()) return null;
//
//        if (repeatOne) {
//            return getCurrentItem();
//        }
//
//        if (shuffle) {
//            int currentShufflePos = shuffleOrder.indexOf(currentIndex);
//            if (currentShufflePos > 0) {
//                currentIndex = shuffleOrder.get(currentShufflePos - 1);
//            } else {
//                if (repeat) {
//                    currentIndex = shuffleOrder.get(shuffleOrder.size() - 1);
//                } else {
//                    return null;
//                }
//            }
//        } else {
//            currentIndex--;
//            if (currentIndex < 0) {
//                if (repeat) {
//                    currentIndex = playlist.size() - 1;
//                } else {
//                    currentIndex = 0;
//                    return null;
//                }
//            }
//        }
//
//        return getCurrentItem();
//    }

//    public void setCurrentIndex(int index) {
//        if (index >= 0 && index < playlist.size()) {
//            currentIndex = index;
//        }
//    }

    public PlaylistItem previous() {
        if (playlist.isEmpty()) return null;

        if (repeatOne) {
            return getCurrentItem();
        }

        if (shuffle) {
            int currentShufflePos = shuffleOrder.indexOf(currentIndex);
            if (currentShufflePos > 0) {
                currentIndex = shuffleOrder.get(currentShufflePos - 1);
            } else {
                if (repeat) {
                    currentIndex = shuffleOrder.get(shuffleOrder.size() - 1);
                } else {
                    return null;
                }
            }
        } else {
            currentIndex--;
            if (currentIndex < 0) {
                if (repeat) {
                    currentIndex = playlist.size() - 1;
                } else {
                    currentIndex = 0;
                    return null;
                }
            }
        }

        saveCurrentState();
        return getCurrentItem();
    }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < playlist.size()) {
            currentIndex = index;
            saveCurrentState();
        }
    }

    // ===== Modos de reprodução =====

//    public void setShuffle(boolean shuffle) {
//        this.shuffle = shuffle;
//
//        if (!shuffle) {
//            System.out.println("Shuffle desativado - resetando marcações");
//            resetAllPlayedMarks();
//        } else {
//            System.out.println("Shuffle ativado - sistema de marcação ativo");
//        }
//
//        updateShuffleOrder();
//    }
//
//    public void setRepeat(boolean repeat) {
//        this.repeat = repeat;
//        if (repeat) {
//            this.repeatOne = false;
//        }
//    }
//
//    public void setRepeatOne(boolean repeatOne) {
//        this.repeatOne = repeatOne;
//        if (repeatOne) {
//            this.repeat = false;
//        }
//    }

    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;

        if (!shuffle) {
            System.out.println("Shuffle desativado - resetando marcações");
            resetAllPlayedMarks();
        } else {
            System.out.println("Shuffle ativado - sistema de marcação ativo");
        }

        updateShuffleOrder();

        ConfigManager config = getConfigManager();
        if (config != null) {
            config.savedPlaylistShuffle(shuffle);
        }
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
        if (repeat) {
            this.repeatOne = false;
        }

        ConfigManager config = getConfigManager();
        if (config != null) {
            config.savedPlaylistRepeat(this.repeat);
            config.savedPlaylistRepeatOne(this.repeatOne);
        }
    }

    public void setRepeatOne(boolean repeatOne) {
        this.repeatOne = repeatOne;
        if (repeatOne) {
            this.repeat = false;
        }

        ConfigManager config = getConfigManager();
        if (config != null) {
            config.savedPlaylistRepeatOne(this.repeatOne);
            config.savedPlaylistRepeat(this.repeat);
        }
    }

    private void updateShuffleOrder() {
        shuffleOrder.clear();
        for (int i = 0; i < playlist.size(); i++) {
            shuffleOrder.add(i);
        }

        if (shuffle && !shuffleOrder.isEmpty()) {
            // Fisher-Yates shuffle
            for (int i = shuffleOrder.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                Collections.swap(shuffleOrder, i, j);
            }

            // Garantir que música atual não mude
            if (currentIndex >= 0 && currentIndex < playlist.size()) {
                int currentPos = shuffleOrder.indexOf(currentIndex);
                if (currentPos != 0) {
                    Collections.swap(shuffleOrder, 0, currentPos);
                }
            }
        }
    }

    // ===== Getters =====

    public List<PlaylistItem> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int size() {
        return playlist.size();
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public boolean isRepeatOne() {
        return repeatOne;
    }

    // ===== Salvar/Carregar M3U =====

//    public void saveM3U(String filePath) throws IOException {
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
//            writer.write("#EXTM3U\n");
//
//            for (PlaylistItem item : playlist) {
//                writer.write("#EXTINF:" + item.getDuration() + "," + item.getDisplayName() + "\n");
//                writer.write(item.getFilePath() + "\n");
//            }
//        }
//    }
public void saveM3U(String filePath) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
        writer.write("#EXTM3U\n");

        for (PlaylistItem item : playlist) {
            writer.write("#EXTINF:" + item.getDuration() + "," + item.getDisplayName() + "\n");

            if (item.getArtist() != null && !item.getArtist().isBlank()) {
                writer.write("#EXTART:" + item.getArtist() + "\n");
            }

            writer.write(item.getFilePath() + "\n");
        }
    }
}

//    public void loadM3U(String filePath) throws IOException {
//        clear();
//
//        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
//            String line;
//            String currentTitle = null;
//            long currentDuration = 0;
//
//            while ((line = reader.readLine()) != null) {
//                line = line.trim();
//
//                if (line.isEmpty() || line.equals("#EXTM3U")) {
//                    continue;
//                }
//
//                if (line.startsWith("#EXTINF:")) {
//                    String info = line.substring(8);
//                    int commaPos = info.indexOf(',');
//                    if (commaPos > 0) {
//                        try {
//                            currentDuration = Long.parseLong(info.substring(0, commaPos).trim());
//                        } catch (NumberFormatException e) {
//                            currentDuration = 0;
//                        }
//                        currentTitle = info.substring(commaPos + 1).trim();
//                    }
//                } else if (!line.startsWith("#")) {
//                    File file = new File(line);
//                    if (file.exists()) {
//                        PlaylistItem item = new PlaylistItem(line);
//                        if (currentTitle != null) {
//                            item.setDisplayName(currentTitle);
//                        }
//                        if (currentDuration > 0) {
//                            item.setDuration(currentDuration);
//                        }
//                        addItem(item);
//                    } else {
//                        System.err.println("Arquivo não encontrado: " + line);
//                    }
//
//                    currentTitle = null;
//                    currentDuration = 0;
//                }
//            }
//        }
//    }

    public void loadM3U(String filePath) throws IOException {
        clear();
        this.loadedPlaylistFilePath = filePath; // <-- necessário para localizar/salvar o estado

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            String currentTitle = null;
            String currentArtist = null;
            long currentDuration = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.equals("#EXTM3U")) {
                    continue;
                }

                if (line.startsWith("#EXTINF:")) {
                    String info = line.substring(8);
                    int commaPos = info.indexOf(',');
                    if (commaPos > 0) {
                        try {
                            currentDuration = Long.parseLong(info.substring(0, commaPos).trim());
                        } catch (NumberFormatException e) {
                            currentDuration = 0;
                        }
                        currentTitle = info.substring(commaPos + 1).trim();
                    }
                } else if (line.startsWith("#EXTART:")) {
                    currentArtist = line.substring(8).trim();
                } else if (!line.startsWith("#")) {
                    File file = new File(line);
                    if (file.exists()) {
                        PlaylistItem item = new PlaylistItem(line);
                        if (currentTitle != null && !currentTitle.isBlank()) {
                            item.setDisplayName(currentTitle);
                        }
                        if (currentArtist != null && !currentArtist.isBlank()) {
                            item.setArtist(currentArtist);
                        }
                        if (currentDuration > 0) {
                            item.setDuration(currentDuration);
                        }
                        addItem(item);
                    } else {
                        System.err.println("Arquivo não encontrado: " + line);
                    }

                    currentTitle = null;
                    currentArtist = null;
                    currentDuration = 0;
                }
            }
        }
    }

    /**
     * Lê título/autor/duração do arquivo via FFmpegFrameGrabber,
     * apenas se essas informações ainda não foram definidas
     * (ex.: quando o item vem de um .m3u que já tinha os dados salvos).
     */
    private void loadAudioMetadataIfMissing(PlaylistItem item) {
        boolean needsTitle = item.getDisplayName() == null
                || item.getDisplayName().equals(new File(item.getFilePath()).getName());
        boolean needsArtist = item.getArtist() == null || item.getArtist().isBlank();
        boolean needsDuration = item.getDuration() <= 0;

        if (!needsTitle && !needsArtist && !needsDuration) {
            return; // já tem tudo (provavelmente veio de um .m3u salvo)
        }

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(item.getFilePath());
        try {
            grabber.start();

            Map<String, String> metadata = grabber.getMetadata();
            if (metadata != null) {
                if (needsTitle) {
                    String title = getMetadataValue(metadata, "title");
                    if (title != null) item.setDisplayName(title);
                }
                if (needsArtist) {
                    String artist = getMetadataValue(metadata, "artist", "author", "album_artist");
                    if (artist != null) item.setArtist(artist);
                }
            }

            if (needsDuration) {
                long durationMicros = grabber.getLengthInTime();
                if (durationMicros > 0) {
                    item.setDuration(durationMicros / 1_000_000);
                }
            }
        } catch (Exception e) {
            System.out.println("Não foi possível ler metadados de: " + item.getFilePath() + " (" + e.getMessage() + ")");
        } finally {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception ignored) {
            }
        }
    }

    private String getMetadataValue(Map<String, String> metadata, String... keys) {
        for (String key : keys) {
            String value = metadata.get(key);
            if (value != null && !value.isBlank()) return value;
            value = metadata.get(key.toUpperCase());
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }



    // ===== Estado de reprodução (novo) =====

    /** Verifica se existe progresso salvo para a playlist atualmente carregada. */
    public boolean hasSavedStateForCurrentPlaylist() {
        return loadedPlaylistFilePath != null && stateManager.hasSavedState(loadedPlaylistFilePath);
    }

    /**
     * Aplica o progresso salvo: marca como tocadas as faixas já ouvidas
     * e posiciona o índice atual na última faixa tocada.
     */
    public void applySavedState() {
        if (loadedPlaylistFilePath == null) return;

        PlaylistStateManager.PlaylistState state = stateManager.loadState(loadedPlaylistFilePath);

        for (PlaylistItem item : playlist) {
            item.setPlayed(state.playedTrackPaths.contains(item.getFilePath()));
        }

        if (state.currentTrackPath != null) {
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getFilePath().equals(state.currentTrackPath)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        updateShuffleOrder();
    }

    /** Descarta o progresso salvo (usado quando o usuário escolhe "recomeçar"). */
    public void clearSavedStateForCurrentPlaylist() {
        if (loadedPlaylistFilePath != null) {
            stateManager.clearState(loadedPlaylistFilePath);
        }
    }

    /** Persiste a faixa atual e as faixas já tocadas no disco. */
    public void saveCurrentState() {
        if (loadedPlaylistFilePath == null) return;

        PlaylistStateManager.PlaylistState state = new PlaylistStateManager.PlaylistState();

        PlaylistItem current = getCurrentItem();
        if (current != null) {
            state.currentTrackPath = current.getFilePath();
        }
        for (PlaylistItem item : playlist) {
            if (item.isPlayed()) {
                state.playedTrackPaths.add(item.getFilePath());
            }
        }

        stateManager.saveState(loadedPlaylistFilePath, state);
    }

    public String getLoadedPlaylistFilePath() {
        return loadedPlaylistFilePath;
    }

}
