package com.esl.videoplayer.Video;

import com.esl.videoplayer.VideoPlayer;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.awt.*;

public class ScreenMode {

    public void enterFullScreen(VideoPlayer videoPlayer, SourceDataLine audioLine, JPanel controlPanel, Rectangle normalBounds,
                                 FFmpegFrameGrabber grabber, boolean isPlaying, Thread playbackThread, String currentVideoPath) {
        System.out.println("Entrando em modo tela cheia...");

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (!gd.isFullScreenSupported()) {
            System.out.println("Tela cheia não suportada neste dispositivo");
            JOptionPane.showMessageDialog(videoPlayer,
                    "Tela cheia não é suportada neste dispositivo.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Salvar estado do vídeo atual
            videoPlayer.saveVideoState();

            // Salvar geometria da janela
            normalBounds = videoPlayer.getBounds();

            // Fechar vídeo atual
            if (grabber != null) {
                try {
                    if (isPlaying) {
                        isPlaying = false;
                        if (playbackThread != null) {
                            playbackThread.interrupt();
                            playbackThread.join(500);
                        }
                    }
                    grabber.stop();
                    grabber.release();
                    grabber = null;
                } catch (Exception e) {
                    System.err.println("Erro ao fechar grabber: " + e.getMessage());
                }
            }

            if (audioLine != null && audioLine.isOpen()) {
                audioLine.close();
                audioLine = null;
            }

            // Transição para tela cheia
            videoPlayer.dispose();
            videoPlayer.setUndecorated(true);
            gd.setFullScreenWindow(videoPlayer);
            videoPlayer.setVisible(true);
            controlPanel.setVisible(false);
            System.out.println("Modo tela cheia ativado");

            // Recarregar vídeo na posição salva
            if (currentVideoPath != null) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        Thread.sleep(200); // Pequeno delay para estabilizar
                        videoPlayer.restoreVideoState();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

        } catch (Exception e) {
            System.err.println("Erro ao entrar em tela cheia: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void exitFullScreen(VideoPlayer videoPlayer, SourceDataLine audioLine, JPanel controlPanel, Rectangle normalBounds,
                               FFmpegFrameGrabber grabber, boolean isPlaying, Thread playbackThread, String currentVideoPath) {
        System.out.println("Saindo do modo tela cheia...");

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        try {
            // Salvar estado do vídeo atual
            videoPlayer.saveVideoState();

            // Fechar vídeo atual
            if (grabber != null) {
                try {
                    if (isPlaying) {
                        isPlaying = false;
                        if (playbackThread != null) {
                            playbackThread.interrupt();
                            playbackThread.join(500);
                        }
                    }
                    grabber.stop();
                    grabber.release();
                    grabber = null;
                } catch (Exception e) {
                    System.err.println("Erro ao fechar grabber: " + e.getMessage());
                }
            }

            if (audioLine != null && audioLine.isOpen()) {
                audioLine.close();
                audioLine = null;
            }

            // Sair de tela cheia
            gd.setFullScreenWindow(null);
            videoPlayer.dispose();
            videoPlayer.setUndecorated(false);
            controlPanel.setVisible(true);
            // Restaurar geometria
            if (normalBounds != null) {
                videoPlayer.setBounds(normalBounds);
            }

            videoPlayer.setVisible(true);

            System.out.println("Modo tela cheia desativado");

            // Recarregar vídeo na posição salva
            if (currentVideoPath != null) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        Thread.sleep(200); // Pequeno delay para estabilizar
                        videoPlayer.restoreVideoState();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

        } catch (Exception e) {
            System.err.println("Erro ao sair de tela cheia: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
