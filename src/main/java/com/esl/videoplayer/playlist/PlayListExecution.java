package com.esl.videoplayer.playlist;

import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import jnafilechooser.api.JnaFileChooser;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

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

    // NOVO: Método para carregar e iniciar playlist direto
    public void loadAndPlayPlaylist(MainPanel mainPanel, VideoPlayer videoPlayer) {
        JnaFileChooser fc = new JnaFileChooser();
        fc.addFilter("M3U Playlist", "m3u");
        if (mainPanel.getAutoPlayItem().isSelected()) {
            System.out.println("====Reativando o autoplay");
            mainPanel.setAutoPlayNext(true);
        }
        // Atualiza o dialog SEMPRE (mesmo invisível)
        SwingUtilities.invokeLater(() -> {
            playlistDialog.refreshPlaylist();
        });

        if (fc.showOpenDialog(videoPlayer)) {
            File file = fc.getSelectedFile();
            try {
                playlistManager.loadM3U(file.getAbsolutePath());

                // Atualizar dialog se estiver aberto
                if (playlistDialog != null && playlistDialog.isVisible()) {
                    playlistDialog.refreshPlaylist();

                }

                // Tocar primeira música automaticamente
                if (playlistManager.size() > 0) {
                    playlistManager.setCurrentIndex(0);
                    PlaylistItem firstItem = playlistManager.getCurrentItem();

                    if (firstItem != null) {
                        System.out.println("Iniciando playlist: " + file.getName());
                        System.out.println("Primeira música: " + firstItem.getDisplayName());
                        playFromPlaylist(firstItem.getFilePath(),videoPlayer);

                        // Atualizar dialog se estiver aberto
                        if (playlistDialog != null && playlistDialog.isVisible()) {
                            playlistDialog.refreshPlaylist();
                        }
                    }

                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog1.text1") + " " + playlistManager.size() + " "
                                    + I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog1.text2") + "\n"
                                    + I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog1.text3") + " " + firstItem.getDisplayName(),
                            I18N.get("videoPlayer.loadAndPlayPlaylist.showMessageDialog1.title"),
                            JOptionPane.INFORMATION_MESSAGE);

                    playlistDialog.setVisible(true);
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
}
