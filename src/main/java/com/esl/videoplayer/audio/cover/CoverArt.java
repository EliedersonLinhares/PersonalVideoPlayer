package com.esl.videoplayer.audio.cover;

import com.esl.videoplayer.Video.VideoPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Manages the extraction and storage of cover art (album art) from audio files
 * using an external FFmpeg process.
 *
 * The extraction process runs on a separate thread to avoid blocking the
 * Event Dispatch Thread (EDT) or main application flow.
 *
 * @author Eliederson Linhares
 * @version 1.0
 * @since 2025-11-25
 */
public class CoverArt {

    /**
     * Stores the extracted audio cover art as a BufferedImage.
     * This value is null if no cover art was found or extracted successfully.
     */
    private BufferedImage audioCoverArt = null;

    /**
     * Retrieves the currently loaded audio cover art.
     *
     * @return The {@link BufferedImage} representing the cover art, or {@code null} if not available.
     */
    public BufferedImage getAudioCoverArt() {
        return audioCoverArt;
    }

    /**
     * Sets the audio cover art image.
     *
     * @param audioCoverArt The {@link BufferedImage} to set as the current cover art.
     */
    public void setAudioCoverArt(BufferedImage audioCoverArt) {
        this.audioCoverArt = audioCoverArt;
    }

    /**
     * Initiates the extraction of cover art from a specified audio file using FFmpeg.
     *
     * The extraction runs asynchronously on a new thread. Upon completion or failure,
     * it updates the provided {@link VideoPanel} on the Event Dispatch Thread (EDT).
     *
     * @param audioFilePath The absolute path to the input audio file (e.g., MP3, FLAC).
     * @param ffmpegPath The absolute path to the FFmpeg executable.
     * @param videoPanel The {@link VideoPanel} instance that needs to be updated with the extracted cover art.
     * @throws NullPointerException if {@code audioFilePath}, {@code ffmpegPath}, or {@code videoPanel} is {@code null}.
     */
    public void extractCoverArt(String audioFilePath, String ffmpegPath, VideoPanel videoPanel) {
        System.out.println("=== Tentando extrair cover art ===");

        // Resetar cover anterior (garantia adicional)
        audioCoverArt = null;

        new Thread(() -> {
            try {
                // Create temporary file for the cover art
                File tempCover = File.createTempFile("cover_", ".jpg");
                tempCover.deleteOnExit();

                System.out.println("Extraindo cover art para: " + tempCover.getAbsolutePath());

                ProcessBuilder pb = new ProcessBuilder(
                        ffmpegPath,
                        "-i", audioFilePath,
                        "-an",              // Without áudio
                        "-vcodec", "copy",  // Copy video stream image(cover) without recode
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
                //If there's an error, don`t use cover image
                SwingUtilities.invokeLater(() -> {
                    videoPanel.setCoverArt(null);
                    videoPanel.repaint();
                });
            }
        }, "CoverArtExtractor").start();
    }
}
