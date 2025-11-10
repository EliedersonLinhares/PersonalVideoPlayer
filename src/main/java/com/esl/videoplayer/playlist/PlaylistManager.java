package com.esl.videoplayer.playlist;

import com.esl.videoplayer.VideoPlayer;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PlaylistManager {
    private List<PlaylistItem> playlist;
    private int currentIndex;
    private boolean shuffle;
    private boolean repeat;
    private boolean repeatOne;
    private List<Integer> shuffleOrder;
    private Random random;

    public PlaylistManager() {
        playlist = new ArrayList<>();
        currentIndex = -1;
        shuffle = false;
        repeat = false;
        repeatOne = false;
        shuffleOrder = new ArrayList<>();
        random = new Random();
    }

    // ===== Gerenciamento de itens =====

    public void addItem(PlaylistItem item) {
        playlist.add(item);
        updateShuffleOrder();
    }

    public void addItems(List<PlaylistItem> items) {
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

    public void markCurrentAsPlayed() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            playlist.get(currentIndex).setPlayed(true);
            System.out.println("Música marcada como tocada: " + playlist.get(currentIndex).getDisplayName());

            // Verificar se todas foram tocadas
            if (allTracksPlayed()) {
                System.out.println("Todas as músicas foram tocadas! Desmarcando todas...");
                resetAllPlayedMarks();
            }
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

    public PlaylistItem next() {
        if (playlist.isEmpty()) return null;

        if (repeatOne) {
            return getCurrentItem();
        }

        if (shuffle) {
            PlaylistItem nextItem = getNextUnplayedInShuffle();
            if (nextItem != null) {
                return nextItem;
            }

            if (repeat) {
                resetAllPlayedMarks();
                updateShuffleOrder();
                currentIndex = shuffleOrder.get(0);
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

        return getCurrentItem();
    }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < playlist.size()) {
            currentIndex = index;
        }
    }

    // ===== Modos de reprodução =====

    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;

        if (!shuffle) {
            System.out.println("Shuffle desativado - resetando marcações");
            resetAllPlayedMarks();
        } else {
            System.out.println("Shuffle ativado - sistema de marcação ativo");
        }

        updateShuffleOrder();
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
        if (repeat) {
            this.repeatOne = false;
        }
    }

    public void setRepeatOne(boolean repeatOne) {
        this.repeatOne = repeatOne;
        if (repeatOne) {
            this.repeat = false;
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

    public void saveM3U(String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("#EXTM3U\n");

            for (PlaylistItem item : playlist) {
                writer.write("#EXTINF:" + item.getDuration() + "," + item.getDisplayName() + "\n");
                writer.write(item.getFilePath() + "\n");
            }
        }
    }

    public void loadM3U(String filePath) throws IOException {
        clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            String currentTitle = null;
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
                } else if (!line.startsWith("#")) {
                    File file = new File(line);
                    if (file.exists()) {
                        PlaylistItem item = new PlaylistItem(line);
                        if (currentTitle != null) {
                            item.setDisplayName(currentTitle);
                        }
                        if (currentDuration > 0) {
                            item.setDuration(currentDuration);
                        }
                        addItem(item);
                    } else {
                        System.err.println("Arquivo não encontrado: " + line);
                    }

                    currentTitle = null;
                    currentDuration = 0;
                }
            }
        }
    }
}
