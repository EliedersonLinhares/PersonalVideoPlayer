package com.esl.videoplayer.playlist;

import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import jnafilechooser.api.JnaFileChooser;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class PlayListExecution {

    private final PlaylistDialog playlistDialog;
    private final PlaylistManager playlistManager;


    public PlaylistManager getPlaylistManager() {
        return playlistManager;
    }

    public PlaylistDialog getPlaylistDialog() {
        return playlistDialog;
    }

    public PlayListExecution(MainPanel mainPanel, VideoPlayer videoPlayer, PlaylistManager playlistManager){
        this.playlistManager = playlistManager;
        this.playlistDialog = new PlaylistDialog(videoPlayer, playlistManager, new PlaylistDialog.PlaylistCallback() {
            @Override
            public void onPlayTrack(String filePath) {
                playFromPlaylist(filePath,videoPlayer);
            }

            @Override
            public void onAutoPlayRequested() {
                // Equivalente ao código: mainPanel.autoPlayItem.isSelected()
                if (mainPanel != null && mainPanel.getAutoPlayItem() != null) {
                    if (mainPanel.getAutoPlayItem().isSelected()) {
                        System.out.println("====Reativando o autoplay");
                        mainPanel.setAutoPlayNext(true);
                    }
                }
            }
        });
    }


    public void showPlaylistDialog() {
        if (playlistDialog == null) {
            playlistDialog.setVisible(false);
        }

        if (!playlistDialog.isVisible()) {
            playlistDialog.setVisible(true);
        } else {
            playlistDialog.toFront();
        }
    }

//    public void loadAndPlayPlaylistFromContext(MainPanel mainPanel, VideoPlayer videoPlayer, String filePath) {
//
//        if (mainPanel.getAutoPlayItem().isSelected()) {
//            System.out.println("====Reativando o autoplay");
//            mainPanel.setAutoPlayNext(true);
//        }
//        // Atualiza o dialog SEMPRE (mesmo invisível)
//        SwingUtilities.invokeLater(playlistDialog::refreshPlaylist);
//
//            try {
//                playlistManager.loadM3U(filePath);
//
//                // Atualizar dialog se estiver aberto
//                if (playlistDialog != null && playlistDialog.isVisible()) {
//                    playlistDialog.refreshPlaylist();
//
//                }
//
//                // Tocar primeira música automaticamente
//                if (playlistManager.size() > 0) {
//
//                    if(mainPanel.isPlayListFirstItemRandom()){
//                        playlistManager.setCurrentIndex(randomInt(playlistManager.getPlaylist()));
//                    }else {
//                        playlistManager.setCurrentIndex(0);
//                    }
//                    PlaylistItem firstItem = playlistManager.getCurrentItem();
//
//                    if (firstItem != null) {
//                        playFromPlaylist(firstItem.getFilePath(),videoPlayer);
//
//                        // Atualizar dialog se estiver aberto
//                        if (playlistDialog != null && playlistDialog.isVisible()) {
//                            playlistDialog.refreshPlaylist();
//                        }
//                    }
//                    playlistDialog.setVisible(true);
//                } else {
//                    JOptionPane.showMessageDialog(videoPlayer,
//                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.text"),
//                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.title"),
//                            JOptionPane.WARNING_MESSAGE);
//                }
//
//            } catch (IOException e) {
//                JOptionPane.showMessageDialog(videoPlayer,
//                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.text") + "\n" + e.getMessage(),
//                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.title"),
//                        JOptionPane.ERROR_MESSAGE);
//                e.printStackTrace();
//            }
//
//    }
//
//
//    // NOVO: Método para carregar e iniciar playlist direto
//    public void loadAndPlayPlaylist(MainPanel mainPanel, VideoPlayer videoPlayer) {
//        JnaFileChooser fc = new JnaFileChooser();
//        fc.addFilter("M3U Playlist", "m3u");
//        if (mainPanel.getAutoPlayItem().isSelected()) {
//            System.out.println("====Reativando o autoplay");
//            mainPanel.setAutoPlayNext(true);
//        }
//        // Atualiza o dialog SEMPRE (mesmo invisível)
//        SwingUtilities.invokeLater(() -> {
//            playlistDialog.refreshPlaylist();
//        });
//
//        if (fc.showOpenDialog(videoPlayer)) {
//            File file = fc.getSelectedFile();
//            try {
//                playlistManager.loadM3U(file.getAbsolutePath());
//
//                // Atualizar dialog se estiver aberto
//                if (playlistDialog != null && playlistDialog.isVisible()) {
//                    playlistDialog.refreshPlaylist();
//
//                }
//
//                // Tocar primeira música automaticamente
//                if (playlistManager.size() > 0) {
//                    if(mainPanel.isPlayListFirstItemRandom()){
//                        playlistManager.setCurrentIndex(randomInt(playlistManager.getPlaylist()));
//                    }else {
//                        playlistManager.setCurrentIndex(0);
//                    }
//
//                    PlaylistItem firstItem = playlistManager.getCurrentItem();
//
//                    if (firstItem != null) {
//                        System.out.println("Iniciando playlist: " + file.getName());
//                        System.out.println("Primeira música: " + firstItem.getDisplayName());
//                        playFromPlaylist(firstItem.getFilePath(),videoPlayer);
//
//                        // Atualizar dialog se estiver aberto
//                        if (playlistDialog != null && playlistDialog.isVisible()) {
//                            playlistDialog.refreshPlaylist();
//                        }
//                    }
//                    playlistDialog.setVisible(true);
//                } else {
//                    JOptionPane.showMessageDialog(videoPlayer,
//                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.text"),
//                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.title"),
//                            JOptionPane.WARNING_MESSAGE);
//                }
//
//            } catch (IOException e) {
//                JOptionPane.showMessageDialog(videoPlayer,
//                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.text") + "\n" + e.getMessage(),
//                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.title"),
//                        JOptionPane.ERROR_MESSAGE);
//                e.printStackTrace();
//            }
//        }
//    }

    public void loadAndPlayPlaylistFromContext(MainPanel mainPanel, VideoPlayer videoPlayer, String filePath) {

        if (mainPanel.getAutoPlayItem().isSelected()) {
            System.out.println("====Reativando o autoplay");
            mainPanel.setAutoPlayNext(true);
        }
        SwingUtilities.invokeLater(playlistDialog::refreshPlaylist);

        try {
            playlistManager.loadM3U(filePath);

            if (playlistDialog != null && playlistDialog.isVisible()) {
                playlistDialog.refreshPlaylist();
            }

            if (playlistManager.size() > 0) {
                startPlaylistPlayback(mainPanel, videoPlayer);
            } else {
                JOptionPane.showMessageDialog(videoPlayer,
                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.text"),
                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.title"),
                        JOptionPane.WARNING_MESSAGE);
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(videoPlayer,
                    I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.text") + "\n" + e.getMessage(),
                    I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.title"),
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public void loadAndPlayPlaylist(MainPanel mainPanel, VideoPlayer videoPlayer) {
        JnaFileChooser fc = new JnaFileChooser();
        fc.addFilter("M3U Playlist", "m3u");
        if (mainPanel.getAutoPlayItem().isSelected()) {
            System.out.println("====Reativando o autoplay");
            mainPanel.setAutoPlayNext(true);
        }
        SwingUtilities.invokeLater(() -> playlistDialog.refreshPlaylist());

        if (fc.showOpenDialog(videoPlayer)) {
            File file = fc.getSelectedFile();
            try {
                playlistManager.loadM3U(file.getAbsolutePath());

                if (playlistDialog != null && playlistDialog.isVisible()) {
                    playlistDialog.refreshPlaylist();
                }

                if (playlistManager.size() > 0) {
                    startPlaylistPlayback(mainPanel, videoPlayer);
                } else {
                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.text"),
                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog2.title"),
                            JOptionPane.WARNING_MESSAGE);
                }

            } catch (IOException e) {
                JOptionPane.showMessageDialog(videoPlayer,
                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.text") + "\n" + e.getMessage(),
                        I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog3.title"),
                        JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }


    private int randomInt(List<PlaylistItem> list) {
        Random random = new Random();
        return random.nextInt(list.size());
    }

    // Método para tocar do playlist (NÃO limpa playlist)
    public void playFromPlaylist(String filePath, VideoPlayer videoPlayer) {
        // IMPORTANTE: Não chamar clearPlaylistAndCloseDialog() aqui
        // Este método é usado pela playlist, então deve manter ela ativa


        if (filePath.toLowerCase().endsWith(".mp3") ||
                filePath.toLowerCase().endsWith(".wav") ||
                filePath.toLowerCase().endsWith(".flac") ||
                filePath.toLowerCase().endsWith(".ogg") ||
                filePath.toLowerCase().endsWith(".m4a") ||
                filePath.toLowerCase().endsWith(".aac")) {
            videoPlayer.getAudioExecution().loadAudioFromPlaylist(filePath); // Usar método especial
        } else {
            videoPlayer.getVideoExecution().loadVideoFromPlaylist(filePath); // Usar método especial
        }
    }


    // No VideoPlayer, adicionar método público:
    public void clearPlaylistAndCloseDialog(MainPanel mainPanel) {
        // Limpar playlist
        if (playlistManager != null) {
            playlistManager.clear();
            playlistDialog.refreshPlaylist();
            System.out.println("Playlist limpa");
        }

        // Fechar dialog se estiver aberto
        if (playlistDialog != null && playlistDialog.isVisible()) {
            playlistDialog.setVisible(false);
            System.out.println("Dialog de playlist fechado");
        }

        // Desabilitar auto-play
        mainPanel.setAutoPlayNext(false);
    }

    private boolean askContinueOrRestart(VideoPlayer videoPlayer) {
        Object[] options = {
                I18N.get("PlaylistExecution.resumeDialog.continueOption"),
                I18N.get("PlaylistExecution.resumeDialog.restartOption")
        };

        int choice = JOptionPane.showOptionDialog(
                videoPlayer,
                I18N.get("PlaylistExecution.resumeDialog.text"),
                I18N.get("PlaylistExecution.resumeDialog.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        return choice == 0; // true = continuar, false (ou fechar) = recomeçar
    }

    private void startPlaylistPlayback(MainPanel mainPanel, VideoPlayer videoPlayer) {
        boolean resumed = false;

        if (playlistManager.hasSavedStateForCurrentPlaylist()) {
            boolean wantsContinue = askContinueOrRestart(videoPlayer);
            if (wantsContinue) {
                playlistManager.applySavedState();
                resumed = true;
            } else {
                playlistManager.clearSavedStateForCurrentPlaylist();
            }
        }

        if (!resumed) {
            if (mainPanel.isPlayListFirstItemRandom()) {
                playlistManager.setCurrentIndex(randomInt(playlistManager.getPlaylist()));
            } else {
                playlistManager.setCurrentIndex(0);
            }
        }

        PlaylistItem currentItem = playlistManager.getCurrentItem();

        if (currentItem != null) {
            playFromPlaylist(currentItem.getFilePath(), videoPlayer);

            if (playlistDialog != null && playlistDialog.isVisible()) {
                playlistDialog.refreshPlaylist();
            }
        }
        playlistDialog.setVisible(true);
    }
}
