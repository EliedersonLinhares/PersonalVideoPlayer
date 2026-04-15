package com.esl.videoplayer.capture;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.Frame;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BatchCaptureExecution implements I18N.LanguageChangeListener {
    private Thread batchCaptureThread = null;
    private volatile boolean batchCapturePaused = false;
    private volatile boolean batchCaptureCancelled = false;
    private VideoPlayer videoPlayer;

    public BatchCaptureExecution(VideoPlayer videoPlayer){
        this.videoPlayer = videoPlayer;
    }



    public void startBatchCapture(String targetDirectory, long totalFramesToCapture, boolean wasPlaying) {
        // Resetar flags de controle
        batchCapturePaused = false;
        batchCaptureCancelled = false;

        // Criar janela de progresso
        JDialog progressDialog = new JDialog(videoPlayer, I18N.get("videoPlayer.BatchCapture.Title"), true);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setSize(500, 200);
        progressDialog.setLocationRelativeTo(videoPlayer);
        progressDialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Label de status
        JLabel statusLabel = new JLabel(I18N.get("videoPlayer.BatchCapture.StatusLabel"), SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(statusLabel, BorderLayout.NORTH);

        // Barra de progresso
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(450, 30));
        panel.add(progressBar, BorderLayout.CENTER);

        // Painel de botões
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton pauseButton = new JButton(I18N.get("videoPlayer.BatchCapture.PauseButton"));
        JButton cancelButton = new JButton(I18N.get("videoPlayer.BatchCapture.CancelButon"));

        pauseButton.addActionListener(e -> {
            if (batchCapturePaused) {
                batchCapturePaused = false;
                pauseButton.setText(I18N.get("videoPlayer.BatchCapture.PauseButton"));
                statusLabel.setText(I18N.get("videoPlayer.BatchCapture.statusLabel1"));
            } else {
                batchCapturePaused = true;
                pauseButton.setText(I18N.get("videoPlayer.BatchCapture.statusLabel2"));
                statusLabel.setText(I18N.get("videoPlayer.BatchCapture.PauseButton2"));
            }
        });

        cancelButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(progressDialog,
                    I18N.get("videoPlayer.BatchCapture.CancelButon.showConfirmDialog.text"),
                    I18N.get("videoPlayer.BatchCapture.CancelButon.showConfirmDialog.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                batchCaptureCancelled = true;
                statusLabel.setText(I18N.get("videoPlayer.BatchCapture.StatusLabel2"));
                pauseButton.setEnabled(false);
                cancelButton.setEnabled(false);
            }
        });

        buttonPanel.add(pauseButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        progressDialog.add(panel);

        // Thread de captura
        batchCaptureThread = new Thread(() -> {
            try {
                // Salvar posição atual
                long savedFrame = videoPlayer.getCurrentFrame();

                // Ir para o início
                videoPlayer.getGrabber().setFrameNumber(0);
                videoPlayer.setCurrentFrame(0);

                File videoFile = new File(videoPlayer.getVideoFilePath());
                String videoName = videoFile.getName();
                int lastDotIndex = videoName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    videoName = videoName.substring(0, lastDotIndex);
                }

                // Criar subpasta para a captura em lote
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String batchFolderName = videoName + "_batch_" + timestamp;
                File batchFolder = new File(targetDirectory, batchFolderName);

                if (!batchFolder.exists()) {
                    batchFolder.mkdirs();
                }

                long capturedCount = 0;
                long frameIndex = 0;

                while (frameIndex < videoPlayer.getTotalFrames() && !batchCaptureCancelled) {
                    // Verificar se está pausado
                    while (batchCapturePaused && !batchCaptureCancelled) {
                        Thread.sleep(100);
                    }

                    if (batchCaptureCancelled) break;

                    // Capturar apenas frames no intervalo definido
                    if (frameIndex % videoPlayer.getMainPanel().getBatchCaptureInterval() == 0) {
                        org.bytedeco.javacv.Frame frame = videoPlayer.getGrabber().grabImage();

                        if (frame != null && frame.image != null) {
                            BufferedImage img = videoPlayer.getConverter().convert(frame);

                            if (img != null) {
                                // Salvar imagem
                                String imageName = String.format("%s_frame_%06d.jpg", videoName, frameIndex);
                                File outputFile = new File(batchFolder, imageName);
                                ImageIO.write(img, "jpg", outputFile);

                                capturedCount++;

                                // Atualizar UI
                                final long currentCaptured = capturedCount;
                                final long currentFrameIndex = frameIndex;
                                SwingUtilities.invokeLater(() -> {
                                    int progress = (int) ((currentFrameIndex * 100) / videoPlayer.getTotalFrames());
                                    progressBar.setValue(progress);
                                    statusLabel.setText(String.format(  I18N.get("videoPlayer.BatchCapture.StatusLabel.status") +
                                                    " %d / %d frames (%d%%)",
                                            currentCaptured, totalFramesToCapture, (progress)));
                                });
                            }
                        }
                    } else {
                        // Pular frame sem decodificar imagem
                        videoPlayer.getGrabber().grabImage();
                    }

                    frameIndex++;
                }

                // Criar variável final para uso na lambda
                final long finalCapturedCount = capturedCount;

                // Finalizar
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);

                    if (batchCaptureCancelled) {
                        statusLabel.setText(I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.statusLabel"));
                        JOptionPane.showMessageDialog(progressDialog,
                                I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog.text1") + "\n" +
                                        I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog.text2") + " " + finalCapturedCount + "\n" +
                                        I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog.text3") + " " + batchFolder.getAbsolutePath(),
                                I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText(I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.statusLabel2"));
                        int openFolder = JOptionPane.showConfirmDialog(progressDialog,
                                I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog2.text1") + "\n" +
                                        I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog2.text2") + " " + finalCapturedCount + "\n" +
                                        I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog.text3") + " " + batchFolder.getAbsolutePath() + "\n\n" +
                                        I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog2.text3") ,
                                I18N.get("videoPlayer.BatchCapture.batchCaptureCancelled.showMessageDialog2.title") ,
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE);

                        if (openFolder == JOptionPane.YES_OPTION) {
                            try {
                                Desktop.getDesktop().open(batchFolder);
                            } catch (Exception ex) {
                                System.err.println("Erro ao abrir pasta: " + ex.getMessage());
                            }
                        }
                    }

                    // Restaurar posição do vídeo
                    try {
                        videoPlayer.getGrabber().setFrameNumber((int) savedFrame);
                        Frame frame = videoPlayer.getGrabber().grabImage();
                        if (frame != null && frame.image != null) {
                            BufferedImage img = videoPlayer.getConverter().convert(frame);
                            if (img != null) {
                                videoPlayer.getMainPanel().updateImage(img);
                            }
                        }
                        videoPlayer.setCurrentFrame(savedFrame);
                        videoPlayer.updateTimeLabel();

                        if (wasPlaying) {
                            videoPlayer.playVideoOrAudio();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    progressDialog.dispose();
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(progressDialog,
                            I18N.get("videoPlayer.BatchCapture.showMessageDialog.Error.text") + "\n" + e.getMessage(),
                            I18N.get("videoPlayer.BatchCapture.showMessageDialog.Error.title"),
                            JOptionPane.ERROR_MESSAGE);
                    progressDialog.dispose();
                });
            }
        });

        batchCaptureThread.start();
        progressDialog.setVisible(true);
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {

    }
}
