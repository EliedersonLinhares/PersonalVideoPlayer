package com.esl.videoplayer.audio.cover;

import com.esl.videoplayer.Video.VideoPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class CoverArt {

    private BufferedImage audioCoverArt = null;

    public BufferedImage getAudioCoverArt() {
        return audioCoverArt;
    }

    public void setAudioCoverArt(BufferedImage audioCoverArt) {
        this.audioCoverArt = audioCoverArt;
    }

    public void extractCoverArt(String audioFilePath, String ffmpegPath, VideoPanel videoPanel) {
        System.out.println("=== Tentando extrair cover art ===");

        // Resetar cover anterior (garantia adicional)
        audioCoverArt = null;

        new Thread(() -> {
            try {
                // Criar arquivo temporário para a capa
                File tempCover = File.createTempFile("cover_", ".jpg");
                tempCover.deleteOnExit();

                System.out.println("Extraindo cover art para: " + tempCover.getAbsolutePath());

                ProcessBuilder pb = new ProcessBuilder(
                        ffmpegPath,
                        "-i", audioFilePath,
                        "-an",              // Sem áudio
                        "-vcodec", "copy",  // Copiar stream de vídeo (capa) sem recodificar
                        "-y",
                        tempCover.getAbsolutePath()
                );

                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean hasAttachedPic = false;

                while ((line = reader.readLine()) != null) {
                    System.out.println("FFmpeg: " + line);

                    if (line.contains("attached pic") || line.contains("Video:")) {
                        hasAttachedPic = true;
                    }
                }

                int exitCode = process.waitFor();

                System.out.println("FFmpeg terminou com código: " + exitCode);
                System.out.println("Arquivo existe: " + tempCover.exists());
                System.out.println("Tamanho do arquivo: " + tempCover.length() + " bytes");

                if (exitCode == 0 && tempCover.exists() && tempCover.length() > 0) {
                    System.out.println("Cover art extraída com sucesso!");

                    // Carregar imagem
                    BufferedImage coverImage = ImageIO.read(tempCover);

                    if (coverImage != null) {
                        audioCoverArt = coverImage;

                        SwingUtilities.invokeLater(() -> {
                            // Atualizar painel para mostrar a capa
                            videoPanel.setCoverArt(audioCoverArt);
                            videoPanel.repaint();
                            System.out.println("Cover art carregada: " + coverImage.getWidth() + "x" + coverImage.getHeight());
                        });
                    } else {
                        System.out.println("Não foi possível decodificar a imagem da capa");
                    }

                } else if (!hasAttachedPic) {
                    System.out.println("Este arquivo não possui cover art (attached pic)");
                    // GARANTIR que cover art está null
                    SwingUtilities.invokeLater(() -> {
                        videoPanel.setCoverArt(null);
                        videoPanel.repaint();
                    });
                } else {
                    System.out.println("Falha ao extrair cover art (código: " + exitCode + ")");
                    SwingUtilities.invokeLater(() -> {
                        videoPanel.setCoverArt(null);
                        videoPanel.repaint();
                    });
                }

            } catch (Exception e) {
                System.err.println("Erro ao extrair cover art: " + e.getMessage());
                e.printStackTrace();
                // Em caso de erro, garantir que não há cover art
                SwingUtilities.invokeLater(() -> {
                    videoPanel.setCoverArt(null);
                    videoPanel.repaint();
                });
            }
        }, "CoverArtExtractor").start();
    }
}
