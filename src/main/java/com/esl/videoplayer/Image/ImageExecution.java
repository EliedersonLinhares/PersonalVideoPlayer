package com.esl.videoplayer.Image;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class ImageExecution {
    private VideoPlayer videoPlayer;

    public ImageExecution(VideoPlayer videoPlayer){
        this.videoPlayer = videoPlayer;
    }

    public void loadImage(String filepath) {
        videoPlayer.getMainPanel().clearFilteredImage();
        videoPlayer.setAudioOnly(false);

        // Salvar caminho do vídeo
        videoPlayer.setCurrentVideoPath(filepath);
        videoPlayer.setVideoFilePath(filepath);

        System.out.println("=== INÍCIO loadImage ===");
        // Limpar playlist e fechar dialog
        videoPlayer.playListExecution.clearPlaylistAndCloseDialog(videoPlayer.getMainPanel());

        // ADICIONAR: Registrar arquivo como recente
        videoPlayer.getRecentFilesManager().addRecentFile(filepath, false);

        loadImageBase(filepath);
    }

    private void loadImageBase(String filepath) {

        //Limpa os items setados anteriormente
        videoPlayer.cleanUpItems();

        // Carregar vídeo em thread separada
        Thread loaderThread = new Thread(() -> {
            try {

                videoPlayer.setGrabber(new FFmpegFrameGrabber(filepath));
                videoPlayer.getGrabber().start();
                videoPlayer.setCurrentFrame(0);

                int videoWidth = videoPlayer.getGrabber().getImageWidth();
                int videoHeight = videoPlayer.getGrabber().getImageHeight();
                int tempVideoWidth = videoWidth;
                int tempVideoHeight = videoHeight;
                if (videoWidth <= 500) {
                    tempVideoWidth = 1080;
                }
                if (videoHeight <= 500) {
                    tempVideoHeight = 720;
                }

                // Guardar dimensões para usar no SwingUtilities.invokeLater
                final int finalWidth = tempVideoWidth;
                final int finalHeight = tempVideoHeight;
                System.out.println("Resoluçao da Imagem: " + finalHeight + " : " + finalWidth);

                SwingUtilities.invokeLater(() -> {
                    // Redimensionar e centralizar a janela

                    videoPlayer.setSize(finalWidth, finalHeight);

                    // Se a resolução do video for igual ou maior que a resolução da tela maximizar
                    if (finalWidth >= videoPlayer.getScreenWidth() || finalHeight >= videoPlayer.getScreenHeight()) {
                        videoPlayer.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    }

                    videoPlayer.setLocationRelativeTo(null); // Centralizar após redimensionar
                    videoPlayer.setResizable(true); // Pode maximizar a janela

                    videoPlayer.getPlayPauseButton().setEnabled(false);
                    videoPlayer.getStopButton().setEnabled(false);
                    videoPlayer.getProgressSlider().setEnabled(false);
                    videoPlayer.getProgressSlider().setValue(0);
                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getRewindButton().setEnabled(false);
                    videoPlayer.getForwardButton().setEnabled(false);
                    videoPlayer.getNextFrameButton().setEnabled(false);
                    videoPlayer.getCaptureFrameButton().setEnabled(false);
                    videoPlayer.getCaptureAllFrameButton().setEnabled(false);
                    videoPlayer.getVolumeButton().setEnabled(false);
                    videoPlayer.getVolumeSlider().setEnabled(false);
                    videoPlayer.updateTimeLabel();

                    videoPlayer.setTitle("Media Player - " + new File(filepath).getName());

                    //Menu de contexto especifico para quando estiver usando imagem
                    videoPlayer.getMainPanel().setupImageContextMenu(videoPlayer,
                            videoPlayer.getGrabber());

                    playImage();


                });

                System.out.println("26. Thread de carregamento CONCLUÍDA");

            } catch (Exception e) {
                System.err.println("ERRO CRÍTICO na thread de carregamento:");
                e.printStackTrace();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("videoPlayer.ImageFileThread.error") + "\n" + e.getMessage(),
                            I18N.get("videoPlayer.ErrorLoadVideoWithAudioStream.title"), JOptionPane.ERROR_MESSAGE);

                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getPlayPauseButton().setEnabled(false);
                    videoPlayer.getStopButton().setEnabled(false);
                    videoPlayer.getVolumeButton().setEnabled(false);
                    videoPlayer.setTitle("Media Player");
                });
            }
        }, "VideoLoader");

        loaderThread.start();
    }

    public void playImage() {
        if (videoPlayer.getGrabber() == null || videoPlayer.isPlaying()) return;

        videoPlayer.setPlaying(true);
        videoPlayer.setStopped(false);


        videoPlayer.setPlaybackThread( new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                long frameDelay = (long) (1000.0 / videoPlayer.getFrameRate());
                long frameCount = 0;


                while (videoPlayer.isPlaying()) {
                    Frame frame = videoPlayer.getGrabber().grab();

                    if (frame != null && frame.image != null) {

                        // Repassa o frame original ao FilterManager na PRIMEIRA captura
                        // (currentFrame == 0 garante que só acontece uma vez por imagem)
                        if (videoPlayer.getCurrentFrame() == 0) {
                            videoPlayer.getMainPanel().setOriginalImageFrame(frame);
                        }

                        BufferedImage img = videoPlayer.getConverter().convert(frame);
                        if (img != null) {
                            if (videoPlayer.getFiltersManager().isFiltersEnabled()) {
                                if (videoPlayer.getCurrentFrame() % 2 == 0) {
                                    img = videoPlayer.getFiltersManager().applyImageFilters(img);
                                }
                            }
                            videoPlayer.getMainPanel().updateImage(img);
                        }

                        videoPlayer.setCurrentFrame(videoPlayer.getCurrentFrame() + 1);
                        frameCount++;


                        // **SINCRONIZAÇÃO DE TEMPO (ORIGINAL - MANTIDA)**
                        long expectedTime = startTime + (frameCount * frameDelay);
                        long currentTime = System.currentTimeMillis();
                        long sleepTime = expectedTime - currentTime;

                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        } else if (sleepTime < -frameDelay * 3) {
                            // Se estiver muito atrasado, reajustar referência de tempo
                            startTime = currentTime - (long) (frameCount * frameDelay * 0.5);
                        }
                    }
                }


            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    videoPlayer.setPlaying(false);
                    videoPlayer.getPlayPauseButton().setText("▶");
                });
            }
        }));

        videoPlayer.getPlaybackThread().start();
    }
}
