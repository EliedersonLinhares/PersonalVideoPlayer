package com.esl.videoplayer.capture;

import com.esl.videoplayer.Video.VideoPanel;
import com.esl.videoplayer.VideoPlayer;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class CaptureFrameManager {

    private Thread batchCaptureThread = null;
    private volatile boolean batchCapturePaused = false;
    private volatile boolean batchCaptureCancelled = false;

    public Thread getBatchCaptureThread() {
        return batchCaptureThread;
    }

    public void setBatchCaptureThread(Thread batchCaptureThread) {
        this.batchCaptureThread = batchCaptureThread;
    }

    public boolean isBatchCapturePaused() {
        return batchCapturePaused;
    }

    public void setBatchCapturePaused(boolean batchCapturePaused) {
        this.batchCapturePaused = batchCapturePaused;
    }

    public boolean isBatchCaptureCancelled() {
        return batchCaptureCancelled;
    }

    public void setBatchCaptureCancelled(boolean batchCaptureCancelled) {
        this.batchCaptureCancelled = batchCaptureCancelled;
    }

    public void captureFrame(FFmpegFrameGrabber grabber, VideoPlayer videoPlayer, VideoPanel videoPanel, String customCapturePath, String videoFilePath, long currentFrame, boolean silentCapture) {
        // Verificar se há um vídeo carregado
        if (grabber == null) {
            JOptionPane.showMessageDialog(videoPlayer, "Nenhum vídeo carregado.\nAbra um vídeo primeiro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Verificar se há uma imagem atual no painel
        BufferedImage currentImage = videoPanel.getCurrentImage();
        if (currentImage == null) {
            JOptionPane.showMessageDialog(videoPlayer, "Nenhum frame disponível para captura.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Determinar o diretório de destino
            String targetDirectory;
            if (customCapturePath != null && !customCapturePath.isEmpty()) {
                targetDirectory = customCapturePath;
            } else {
                // Usar o diretório do vídeo
                File videoFile = new File(videoFilePath);
                targetDirectory = videoFile.getParent();
            }

            // Obter o nome do vídeo sem extensão
            File videoFile = new File(videoFilePath);
            String videoName = videoFile.getName();
            int lastDotIndex = videoName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                videoName = videoName.substring(0, lastDotIndex);
            }

            // Criar o nome do arquivo de imagem
            String imageName = String.format("%s_frame_%d.jpg", videoName, currentFrame);
            File outputFile = new File(targetDirectory, imageName);

            // Verificar se o arquivo já existe e criar um nome único se necessário
            int counter = 1;
            while (outputFile.exists()) {
                imageName = String.format("%s_frame_%d_(%d).jpg", videoName, currentFrame, counter);
                outputFile = new File(targetDirectory, imageName);
                counter++;
            }

            // Salvar a imagem em formato JPEG
            ImageIO.write(currentImage, "jpg", outputFile);

            System.out.println("Frame capturado: " + outputFile.getAbsolutePath());

            // Mostrar mensagem de sucesso (se não estiver em modo silencioso)
            if (!silentCapture) {
                int response = JOptionPane.showConfirmDialog(videoPlayer, "Frame capturado com sucesso!\n" + "Arquivo: " + imageName + "\n" + "Local: " + targetDirectory + "\n\n" + "Deseja abrir a pasta?", "Captura Realizada", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

                // Abrir a pasta se o usuário confirmar
                if (response == JOptionPane.YES_OPTION) {
                    try {
                        Desktop desktop = Desktop.getDesktop();
                        desktop.open(new File(targetDirectory));
                    } catch (Exception ex) {
                        System.err.println("Erro ao abrir pasta: " + ex.getMessage());
                    }
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(videoPlayer, "Erro ao salvar a imagem:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void selectCaptureFolder(String customCapturePath, String videoFilePath, VideoPlayer videoPlayer) {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle("Selecionar Pasta para Capturas");

        // Definir pasta inicial
        if (customCapturePath != null) {
            folderChooser.setCurrentDirectory(new File(customCapturePath));
        } else if (videoFilePath != null) {
            File videoFile = new File(videoFilePath);
            folderChooser.setCurrentDirectory(videoFile.getParentFile());
        }

        int result = folderChooser.showDialog(videoPlayer, "Selecionar");

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();
            customCapturePath = selectedFolder.getAbsolutePath();

            JOptionPane.showMessageDialog(videoPlayer, "Pasta de captura definida:\n" + customCapturePath, "Configuração Salva", JOptionPane.INFORMATION_MESSAGE);

            System.out.println("Pasta de captura definida: " + customCapturePath);
        }
    }

    //
    public void resetCaptureFolder(String customCapturePath, VideoPlayer videoPlayer) {
        customCapturePath = null;
        JOptionPane.showMessageDialog(videoPlayer, "A pasta de captura foi redefinida.\n" + "As capturas serão salvas na pasta do vídeo.", "Configuração Restaurada", JOptionPane.INFORMATION_MESSAGE);
        System.out.println("Pasta de captura restaurada para padrão");
    }

    //
//    // ========== CAPTURA EM LOTE ==========
//
    public void selectBatchCaptureFolder(String batchCapturePath, String videoFilePath, VideoPlayer videoPlayer) {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle("Selecionar Pasta para Captura em Lote");

        if (batchCapturePath != null) {
            folderChooser.setCurrentDirectory(new File(batchCapturePath));
        } else if (videoFilePath != null) {
            File videoFile = new File(videoFilePath);
            folderChooser.setCurrentDirectory(videoFile.getParentFile());
        }

        int result = folderChooser.showDialog(videoPlayer, "Selecionar");

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();
            batchCapturePath = selectedFolder.getAbsolutePath();

            JOptionPane.showMessageDialog(videoPlayer, "Pasta para captura em lote definida:\n" + batchCapturePath, "Configuração Salva", JOptionPane.INFORMATION_MESSAGE);

            System.out.println("Pasta de captura em lote definida: " + batchCapturePath);
        }
    }

    public void resetBatchCaptureFolder(String batchCapturePath, VideoPlayer videoPlayer) {
        batchCapturePath = null;
        JOptionPane.showMessageDialog(videoPlayer, "A pasta de captura em lote foi redefinida.\n" + "As capturas serão salvas na pasta do vídeo.", "Configuração Restaurada", JOptionPane.INFORMATION_MESSAGE);
        System.out.println("Pasta de captura em lote restaurada para padrão");
    }

    public void batchCaptureFrames(FFmpegFrameGrabber grabber, VideoPlayer videoPlayer, Thread batchCaptureThread, boolean isPlaying, long totalFrames, int batchCaptureInterval, double frameRate, String batchCapturePath, String videoFilePath) {
        // Verificar se há um vídeo carregado
        if (grabber == null) {
            JOptionPane.showMessageDialog(videoPlayer, "Nenhum vídeo carregado.\nAbra um vídeo primeiro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Verificar se já há uma captura em andamento
        if (batchCaptureThread != null && batchCaptureThread.isAlive()) {
            JOptionPane.showMessageDialog(videoPlayer, "Já existe uma captura em lote em andamento.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Pausar o vídeo se estiver tocando
        boolean wasPlaying = isPlaying;
        if (wasPlaying) {
            videoPlayer.pauseVideo();
        }

        // Calcular informações do vídeo
        long totalFramesToCapture = totalFrames / batchCaptureInterval;
        double durationSeconds = totalFrames / frameRate;
        int durationMinutes = (int) (durationSeconds / 60);

        // Obter resolução do vídeo
        int videoWidth = grabber.getImageWidth();
        int videoHeight = grabber.getImageHeight();
        boolean isHighRes = (videoWidth > 720 || videoHeight > 480);
        boolean isLongVideo = durationMinutes > 15;

        // Construir mensagem de confirmação
        StringBuilder message = new StringBuilder();
        message.append("Deseja capturar todos os frames do vídeo?\n\n");
        message.append("Informações:\n");
        message.append("• Total de frames: ").append(totalFrames).append("\n");
        message.append("• Frames a capturar: ").append(totalFramesToCapture).append(" (a cada ").append(batchCaptureInterval).append(" frames)\n");
        message.append("• Duração: ").append(durationMinutes).append(" min ").append((int) (durationSeconds % 60)).append(" seg\n");
        message.append("• Resolução: ").append(videoWidth).append("x").append(videoHeight).append("\n\n");

        // Determinar pasta de destino
        String targetDirectory;
        if (batchCapturePath != null && !batchCapturePath.isEmpty()) {
            targetDirectory = batchCapturePath;
        } else {
            File videoFile = new File(videoFilePath);
            targetDirectory = videoFile.getParent();
        }
        message.append("Pasta de destino:\n").append(targetDirectory).append("\n\n");

        // Avisos
        if (isLongVideo || isHighRes) {
            message.append("⚠️ AVISO:\n");
            if (isLongVideo) {
                message.append("• Vídeo longo (>15 min) - A captura pode demorar\n");
            }
            if (isHighRes) {
                message.append("• Alta resolução (>720x480) - Processo pode ser lento\n");
            }
            message.append("\n");
        }

        message.append("Continuar com a captura?");

        int response = JOptionPane.showConfirmDialog(videoPlayer, message.toString(), "Confirmar Captura em Lote", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (response != JOptionPane.YES_OPTION) {
            if (wasPlaying) {
                videoPlayer.playVideoOrAudio();
            }
            return;
        }

        // Iniciar captura em lote
        videoPlayer.startBatchCapture(targetDirectory, totalFramesToCapture, wasPlaying);
    }

}
