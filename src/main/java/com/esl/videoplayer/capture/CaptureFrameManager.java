package com.esl.videoplayer.capture;

import com.esl.videoplayer.Video.VideoPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class CaptureFrameManager implements I18N.LanguageChangeListener {

    private JFileChooser folderChooser ;

    public CaptureFrameManager() {
        // IMPORTANTE: Atualizar textos pela primeira vez
        updateTexts();

        // IMPORTANTE: Registrar listener APÓS criar todos os componentes
        I18N.addLanguageChangeListener(this);
    }

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

    public void captureFrame(VideoPlayer videoPlayer, VideoPanel videoPanel, String customCapturePath, String videoFilePath, long currentFrame, boolean silentCapture) {

        // Verificar se há uma imagem atual no painel
        BufferedImage currentImage = videoPanel.getCurrentImage();
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
                int response = JOptionPane.showConfirmDialog(videoPlayer,  I18N.get("captureFrameManager.SuccessDialog.text1") + "\n" + I18N.get("captureFrameManager.SuccessDialog.text2")  + imageName + "\n" + I18N.get("captureFrameManager.SuccessDialog.text3") + targetDirectory + "\n\n" + I18N.get("captureFrameManager.SuccessDialog.text4"), I18N.get("captureFrameManager.SuccessDialog.title"), JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

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
            JOptionPane.showMessageDialog(videoPlayer,   I18N.get("captureFrameManager.IOException.text") + "\n" + ex.getMessage(), I18N.get("captureFrameManager.IOException.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void selectCaptureFolder(String customCapturePath, String videoFilePath, VideoPlayer videoPlayer) {
        folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);


        // Definir pasta inicial
        if (customCapturePath != null) {
            folderChooser.setCurrentDirectory(new File(customCapturePath));
        } else if (videoFilePath != null) {
            File videoFile = new File(videoFilePath);
            folderChooser.setCurrentDirectory(videoFile.getParentFile());
        }

        int result = folderChooser.showDialog(videoPlayer, I18N.get("captureFrameManager.folderChooser.ShowDialog.ApproveButtonText"));

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();
            customCapturePath = selectedFolder.getAbsolutePath();

            JOptionPane.showMessageDialog(videoPlayer, I18N.get("captureFrameManager.folderChooser.ShowDialogResult.text") + "\n" + customCapturePath, I18N.get("captureFrameManager.folderChooser.ShowDialogResult.title"), JOptionPane.INFORMATION_MESSAGE);

            System.out.println("Pasta de captura definida: " + customCapturePath);
        }
    }

    //
    public void resetCaptureFolder(VideoPlayer videoPlayer) {
        JOptionPane.showMessageDialog(videoPlayer, I18N.get("captureFrameManager.resetCapture.ShowDialog.text1") + "\n" + I18N.get("captureFrameManager.resetCapture.ShowDialog.text2"), I18N.get("captureFrameManager.resetCapture.ShowDialog.title"), JOptionPane.INFORMATION_MESSAGE);
        System.out.println("Pasta de captura restaurada para padrão");
    }

    //
//    // ========== CAPTURA EM LOTE ==========
//
    public void selectBatchCaptureFolder(String batchCapturePath, String videoFilePath, VideoPlayer videoPlayer) {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle(I18N.get("captureFrameManager.BatchCaptureFolder.folderChoose.title"));

        if (batchCapturePath != null) {
            folderChooser.setCurrentDirectory(new File(batchCapturePath));
        } else if (videoFilePath != null) {
            File videoFile = new File(videoFilePath);
            folderChooser.setCurrentDirectory(videoFile.getParentFile());
        }

        int result = folderChooser.showDialog(videoPlayer, I18N.get("captureFrameManager.BatchCaptureFolder.folderChoose.approvedButtonText"));

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();
            batchCapturePath = selectedFolder.getAbsolutePath();

            JOptionPane.showMessageDialog(videoPlayer, I18N.get("captureFrameManager.BatchCaptureFolder.ShowDialogResult.text1")+"\n" + batchCapturePath, I18N.get("captureFrameManager.BatchCaptureFolder.ShowDialogResult.title"), JOptionPane.INFORMATION_MESSAGE);

            System.out.println("Pasta de captura em lote definida: " + batchCapturePath);
        }
    }

//    public void resetBatchCaptureFolder(VideoPlayer videoPlayer) {
//        JOptionPane.showMessageDialog(videoPlayer, "A pasta de captura em lote foi redefinida.\n" + "As capturas serão salvas na pasta do vídeo.", "Configuração Restaurada", JOptionPane.INFORMATION_MESSAGE);
//        System.out.println("Pasta de captura em lote restaurada para padrão");
//    }

    public void batchCaptureFrames(FFmpegFrameGrabber grabber, VideoPlayer videoPlayer, boolean isPlaying, long totalFrames, int batchCaptureInterval, double frameRate, String batchCapturePath, String videoFilePath) {

        // Pausar o vídeo se estiver tocando
        if (isPlaying) {
            videoPlayer.pauseVideo();
        }

        // Calcular informações do vídeo
        long totalFramesToCapture = totalFrames / batchCaptureInterval;
        double durationSeconds = totalFrames / frameRate;
        int durationMinutes = (int) (durationSeconds / 60);

        // Obter resolução do vídeo
        int videoWidth = grabber.getImageWidth();
        int videoHeight = grabber.getImageHeight();
        boolean isHighRes = (videoWidth > 720 || videoHeight > 1080);
        boolean isLongVideo = durationMinutes > 15;

        // Construir mensagem de confirmação
        StringBuilder message = new StringBuilder();
        message.append(I18N.get("captureFrameManager.confirmationMessage.text1")).append("\n\n");
        message.append(I18N.get("captureFrameManager.confirmationMessage.text2")).append("\n");
        message.append(I18N.get("captureFrameManager.confirmationMessage.text3")).append(" ").append(totalFrames).append("\n");
        message.append(I18N.get("captureFrameManager.confirmationMessage.text4")).append(" ").append(totalFramesToCapture).append(I18N.get("captureFrameManager.confirmationMessage.text5")).append(batchCaptureInterval).append(" frames)\n");
        message.append(I18N.get("captureFrameManager.confirmationMessage.text6")).append(" ").append(durationMinutes).append(" min ").append((int) (durationSeconds % 60)).append(" seg\n");
        message.append(I18N.get("captureFrameManager.confirmationMessage.text7")).append(" ").append(videoWidth).append("x").append(videoHeight).append("\n\n");

        // Determinar pasta de destino
        String targetDirectory;
        if (batchCapturePath != null && !batchCapturePath.isEmpty()) {
            targetDirectory = batchCapturePath;
        } else {
            File videoFile = new File(videoFilePath);
            targetDirectory = videoFile.getParent();
        }
        message.append(I18N.get("captureFrameManager.confirmationMessage.text8")).append("\n").append(targetDirectory).append("\n\n");

        // Avisos
        if (isLongVideo || isHighRes) {
            message.append("⚠️").append(I18N.get("captureFrameManager.confirmationMessage.text9")).append(":\n");
            if (isLongVideo) {
                message.append(I18N.get("captureFrameManager.confirmationMessage.text10"));
            }
            if (isHighRes) {
                message.append(I18N.get("captureFrameManager.confirmationMessage.text11")).append("\n");
            }
            message.append("\n");
        }

        message.append(I18N.get("captureFrameManager.confirmationMessage.text12"));

        int response = JOptionPane.showConfirmDialog(videoPlayer, message.toString(), I18N.get("captureFrameManager.confirmationMessage.confirmDialog.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (response != JOptionPane.YES_OPTION) {
            if (isPlaying) {
                videoPlayer.playVideoOrAudio();
            }
            return;
        }
        // Iniciar captura em lote
        videoPlayer.startBatchCapture(targetDirectory, totalFramesToCapture, isPlaying);
    }


    // Método para atualizar todos os textos da interface
    private void updateTexts() {
        if(folderChooser != null) {
            folderChooser.setDialogTitle(I18N.get("captureFrameManager.folderChooser.title"));
        }
    }
    @Override
    public void onLanguageChanged(Locale newLocale) {
        System.out.println("VideoPlayer: Idioma mudou para: " + newLocale);
        updateTexts();
    }

}
