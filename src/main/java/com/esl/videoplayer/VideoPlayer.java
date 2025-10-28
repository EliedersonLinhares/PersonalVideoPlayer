package com.esl.videoplayer;


import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;
import jnafilechooser.api.JnaFileChooser;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.*;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class VideoPlayer extends JFrame {
    private VideoPanel videoPanel;
    private JButton playPauseButton;
    private JButton stopButton;
    private JSlider progressSlider;
    private JSlider volumeSlider;
    private JLabel timeLabel;
    private JLabel volumeLabel;
    private JButton openButton;
    private JCheckBox hwAccelCheckbox;

    private FFmpegFrameGrabber grabber;
    private Java2DFrameConverter converter;
    private Thread playbackThread;
    private volatile boolean isPlaying = false;
    private volatile boolean isStopped = true;
    private volatile boolean isSeeking = false;

    private SourceDataLine audioLine;
    private int audioChannels;
    private int sampleRate;
    private float volume = 1.0f;

    private long totalFrames;
    private double frameRate;
    private long currentFrame = 0;

    // Painel customizado com double buffering
    static class VideoPanel extends JPanel {
        private BufferedImage currentImage;

        public VideoPanel() {
            setBackground(Color.BLACK);
            setDoubleBuffered(true);

        }

        public void updateImage(BufferedImage img) {
            this.currentImage = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (currentImage == null) {
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 16));
                String msg = "Clique em 'Abrir Vídeo' para começar";
                FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = getHeight() / 2;
                g.drawString(msg, x, y);
                return;
            }

            // Calcular dimensões mantendo aspect ratio
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            int imgWidth = currentImage.getWidth();
            int imgHeight = currentImage.getHeight();

            double panelRatio = (double)panelWidth / panelHeight;
            double imgRatio = (double)imgWidth / imgHeight;

            int drawWidth;
            int drawHeight;
            int x;
            int y;

            if (panelRatio > imgRatio) {
                drawHeight = panelHeight;
                drawWidth = (int)(imgWidth * ((double)panelHeight / imgHeight));
                x = (panelWidth - drawWidth) / 2;
                y = 0;
            } else {
                drawWidth = panelWidth;
                drawHeight = (int)(imgHeight * ((double)panelWidth / imgWidth));
                x = 0;
                y = (panelHeight - drawHeight) / 2;
            }

            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            g2d.drawImage(currentImage, x, y, drawWidth, drawHeight, null);
        }
    }

    public VideoPlayer() {
        setTitle("Video Player - JavaCV");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        converter = new Java2DFrameConverter();
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        videoPanel = new VideoPanel();
        add(videoPanel, BorderLayout.CENTER);

        // Painel de controles
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Botões
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        openButton = new JButton("Abrir Vídeo");
        openButton.addActionListener(e -> openVideo());

        playPauseButton = new JButton("▶ Play");
        playPauseButton.setEnabled(false);
        playPauseButton.addActionListener(e -> togglePlayPause());

        stopButton = new JButton("■ Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopVideo());

        hwAccelCheckbox = new JCheckBox("Aceleração GPU", false);
        hwAccelCheckbox.setToolTipText("Ativar aceleração por hardware (requer drivers atualizados)");

        // Controle de volume
        volumeLabel = new JLabel("🔊 100%");
        volumeSlider = new JSlider(0, 100, 100);
        volumeSlider.setPreferredSize(new Dimension(100, 20));
        volumeSlider.addChangeListener(e -> {
            int vol = volumeSlider.getValue();
            volume = vol / 100.0f;
            volumeLabel.setText("🔊 " + vol + "%");
        });

        buttonPanel.add(openButton);
        buttonPanel.add(playPauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(hwAccelCheckbox);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(volumeLabel);
        buttonPanel.add(volumeSlider);

        // Barra de progresso
        JPanel progressPanel = new JPanel(new BorderLayout(5, 0));
        progressSlider = new JSlider(0, 100, 0);
        progressSlider.setEnabled(false);
        progressSlider.addChangeListener(e -> {
            if (progressSlider.getValueIsAdjusting() && grabber != null) {
                isSeeking = true;
            } else if (isSeeking) {
                seekToPosition(progressSlider.getValue());
                isSeeking = false;
            }
        });

        timeLabel = new JLabel("00:00 / 00:00");
        timeLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        progressPanel.add(progressSlider, BorderLayout.CENTER);
        progressPanel.add(timeLabel, BorderLayout.EAST);

        controlPanel.add(buttonPanel, BorderLayout.NORTH);
        controlPanel.add(progressPanel, BorderLayout.CENTER);

        add(controlPanel, BorderLayout.SOUTH);
    }

    private void openVideo() {
        if (isPlaying) {
            pauseVideo();
        }
        JnaFileChooser fc = new JnaFileChooser();
        fc.addFilter("Arquivos de Vídeo (*.mp4, *.avi, *.mkv, *.mov, *.flv)", "mp4", "avi", "mkv", "mov", "flv", "webm", "gif", "wmv", "mov","3gp");
        if (fc.showOpenDialog(this)) {
            File f = fc.getSelectedFile();
            loadVideo(f.getAbsolutePath());
        }
    }

    private void loadVideo(String filepath) {
        try {
            // Fechar vídeo anterior se existir
            if (grabber != null) {
                stopVideo();
                grabber.stop();
                grabber.release();
            }

            if (audioLine != null && audioLine.isOpen()) {
                audioLine.close();
            }

            grabber = new FFmpegFrameGrabber(filepath);

            // Aplicar aceleração por hardware se checkbox marcado
            if (hwAccelCheckbox.isSelected()) {
                tryEnableHardwareAcceleration(grabber);
            }
            // Opções para melhorar performance em WMV
            String extension = filepath.substring(filepath.lastIndexOf('.') + 1).toLowerCase();
            if (extension.equals("wmv")) {
                try {
                    grabber.setOption("threads", "auto");
                    grabber.setOption("fflags", "nobuffer");
                    grabber.setOption("flags", "low_delay");
                    System.out.println("Opções de otimização WMV aplicadas");
                } catch (Exception e) {
                    System.out.println("Não foi possível aplicar opções WMV: " + e.getMessage());
                }
            }

            grabber.start();

            totalFrames = grabber.getLengthInVideoFrames();
            frameRate = grabber.getVideoFrameRate();
            currentFrame = 0;

            // Detectar extensão do arquivo
            String extension2 = extension;

            // Correção para FLV com FPS incorreto (1000 fps)
            if (extension2.equals("flv") && frameRate > 100) {
                // FLV geralmente usa 29.97 ou 30 fps, mas FFmpeg reporta 1000
                // Usar o tbr (time base rate) que é mais confiável
                double tbr = grabber.getFrameRate(); // tbr do metadata
                if (tbr > 0 && tbr < 100) {
                    frameRate = tbr;
                    System.out.println("FLV detectado - usando FPS corrigido: " + frameRate);
                } else {
                    // Fallback para 29.97 (padrão NTSC)
                    frameRate = 29.97;
                    System.out.println("FLV detectado - usando FPS padrão: 29.97");
                }
            }

            // Correção para WMV com FPS incorreto
            if (extension.equals("wmv") && frameRate > 100) {
                // WMV reporta tbn alto (1000), usar tbr que é o real
                double tbr = grabber.getFrameRate();
                if (tbr > 0 && tbr < 100) {
                    frameRate = tbr;
                    System.out.println("WMV detectado - usando FPS corrigido: " + frameRate);
                } else {
                    frameRate = 30.0;
                    System.out.println("WMV detectado - usando FPS padrão: 30");
                }
            }

            // Correção para GIF
            if (extension.equals("gif")) {
                // GIF pode ter FPS diferente entre fps e tbr
                double tbr = grabber.getFrameRate();
                if (tbr > 0 && Math.abs(tbr - frameRate) > 1) {
                    frameRate = tbr; // Preferir tbr para GIF
                    System.out.println("GIF detectado - usando FPS (tbr): " + frameRate);
                }
                // GIF não tem áudio, desabilitar
                audioLine = null;
            }

            // Validação geral: se FPS for muito alto ou inválido
            if (frameRate > 120 || frameRate < 1) {
                System.out.println("FPS inválido (" + frameRate + "), usando 30 fps como padrão");
                frameRate = 30.0;
            }

            System.out.println("FPS final: " + frameRate + ", Total frames: " + totalFrames);

            // Configurar áudio
            audioChannels = grabber.getAudioChannels();
            sampleRate = grabber.getSampleRate();

            // Não configurar áudio para formatos sem áudio (GIF)
            if (audioChannels > 0 && sampleRate > 0 && !extension.equals("gif")) {
                try {
                    AudioFormat audioFormat = new AudioFormat(
                            sampleRate,
                            16,
                            audioChannels,
                            true,
                            true
                    );

                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                    audioLine = (SourceDataLine) AudioSystem.getLine(info);

                    // Buffer de áudio maior para WMV (evitar bloqueios)
                    int bufferSize = sampleRate * audioChannels * 2;
                    if (extension.equals("wmv")) {
                        bufferSize *= 4; // 4x maior para WMV
                        System.out.println("Usando buffer de áudio aumentado para WMV");
                    }

                    audioLine.open(audioFormat, bufferSize);

                    System.out.println("Áudio configurado: " + sampleRate + "Hz, " + audioChannels + " canais, buffer: " + bufferSize + " bytes");
                } catch (Exception audioEx) {
                    System.err.println("Não foi possível configurar áudio: " + audioEx.getMessage());
                    audioLine = null;
                }
            } else {
                System.out.println("Sem áudio ou formato não suportado");
            }

            // Habilitar controles
            playPauseButton.setEnabled(true);
            stopButton.setEnabled(true);
            progressSlider.setEnabled(true);
            progressSlider.setValue(0);

            updateTimeLabel();

            // Exibir primeiro frame
            Frame firstFrame = grabber.grabImage();
            if (firstFrame != null && firstFrame.image != null) {
                BufferedImage img = converter.convert(firstFrame);
                if (img != null) {
                    videoPanel.updateImage(img);
                }
            }
            grabber.setFrameNumber(0);

            setTitle("Video Player - " + new java.io.File(filepath).getName());
            playVideo();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao abrir vídeo: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void tryEnableHardwareAcceleration(FFmpegFrameGrabber grabber) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                // Windows - tentar auto primeiro, depois específicos
                grabber.setVideoOption("hwaccel", "auto");
                grabber.setVideoOption("hwaccel_output_format", "auto");
                System.out.println("Aceleração GPU habilitada (Windows - auto)");
            } else if (os.contains("mac")) {
                grabber.setVideoOption("hwaccel", "videotoolbox");
                System.out.println("Aceleração GPU habilitada (macOS)");
            } else if (os.contains("linux")) {
                grabber.setVideoOption("hwaccel", "vaapi");
                grabber.setVideoOption("hwaccel_device", "/dev/dri/renderD128");
                System.out.println("Aceleração GPU habilitada (Linux)");
            }

            // Opções adicionais para HEVC
            grabber.setVideoOption("threads", "auto");

        } catch (Exception e) {
            System.out.println("Não foi possível habilitar aceleração GPU: " + e.getMessage());
        }
    }

    private void togglePlayPause() {
        if (isPlaying) {
            pauseVideo();
        } else {
            playVideo();
        }
    }

    private void playVideo() {
        if (grabber == null || isPlaying) return;

        isPlaying = true;
        isStopped = false;
        playPauseButton.setText("⏸ Pause");

        if (audioLine != null && !audioLine.isRunning()) {
            audioLine.start();
        }

        playbackThread = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                long frameDelay = (long)(1000.0 / frameRate);
                long frameCount = 0;
                long lastUpdateTime = startTime;
                double avgFrameTime = frameDelay;

                // Estatísticas para debug
                long totalDecodeTime = 0;
                long totalRenderTime = 0;
                int statsCounter = 0;

                while (isPlaying) {
                    long frameStartTime = System.currentTimeMillis();

                    // Medir tempo de decodificação
                    long decodeStart = System.currentTimeMillis();
                    Frame frame = grabber.grab();
                    long decodeTime = System.currentTimeMillis() - decodeStart;

                    if (frame == null) {
                        // Fim do vídeo
                        SwingUtilities.invokeLater(() -> stopVideo());
                        break;
                    }

                    // Processar frame de vídeo
                    if (frame.image != null) {
                        long renderStart = System.currentTimeMillis();

                        BufferedImage img = converter.convert(frame);
                        if (img != null) {
                            videoPanel.updateImage(img);
                        }

                        long renderTime = System.currentTimeMillis() - renderStart;

                        // Acumular estatísticas
                        totalDecodeTime += decodeTime;
                        totalRenderTime += renderTime;
                        statsCounter++;

                        // Mostrar estatísticas a cada 100 frames
                        if (statsCounter >= 100) {
                            long avgDecode = totalDecodeTime / statsCounter;
                            long avgRender = totalRenderTime / statsCounter;
                            System.out.println("Performance - Decode: " + avgDecode + "ms, Render: " + avgRender + "ms, Total: " + (avgDecode + avgRender) + "ms, Target: " + frameDelay + "ms");
                            totalDecodeTime = 0;
                            totalRenderTime = 0;
                            statsCounter = 0;
                        }

                        currentFrame++;
                        frameCount++;

                        // Calcular tempo médio entre frames (adaptativo)
                        long timeSinceLastUpdate = frameStartTime - lastUpdateTime;
                        if (frameCount > 10) {
                            avgFrameTime = (avgFrameTime * 0.9) + (timeSinceLastUpdate * 0.1);
                        }
                        lastUpdateTime = frameStartTime;

                        // Atualizar UI periodicamente
                        if (currentFrame % 5 == 0) {
                            SwingUtilities.invokeLater(() -> {
                                if (!isSeeking && totalFrames > 0) {
                                    int progress = (int)((currentFrame * 100) / totalFrames);
                                    progressSlider.setValue(progress);
                                    updateTimeLabel();
                                }
                            });
                        }

                        // Sincronização baseada no tempo esperado
                        long expectedTime = startTime + (frameCount * frameDelay);
                        long currentTime = System.currentTimeMillis();
                        long sleepTime = expectedTime - currentTime;

                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        } else if (sleepTime < -frameDelay * 3) {
                            // Muito atrasado (mais de 3 frames), ajustar tempo de início
                            System.out.println("Sistema atrasado em " + (-sleepTime) + "ms, reajustando...");
                            startTime = currentTime - (long)(frameCount * frameDelay * 0.5);
                        }
                    }

                    // Processar frame de áudio
                    if (frame.samples != null && audioLine != null) {
                        try {
                            ShortBuffer channelSamples = (ShortBuffer) frame.samples[0];
                            channelSamples.rewind();

                            // Verificar se há dados suficientes
                            if (channelSamples.remaining() > 0) {
                                ByteBuffer outBuffer = ByteBuffer.allocate(channelSamples.remaining() * 2);

                                while (channelSamples.hasRemaining()) {
                                    short val = channelSamples.get();
                                    // Aplicar volume
                                    val = (short)(val * volume);
                                    outBuffer.putShort(val);
                                }

                                // CORREÇÃO: Escrever áudio de forma não-bloqueante
                                byte[] audioData = outBuffer.array();
                                int bytesToWrite = outBuffer.position();
                                int written = 0;
                                int maxAttempts = 5;
                                int attempts = 0;

                                while (written < bytesToWrite && attempts < maxAttempts) {
                                    int available = audioLine.available();
                                    if (available > 0) {
                                        int toWrite = Math.min(bytesToWrite - written, available);
                                        int result = audioLine.write(audioData, written, toWrite);
                                        written += result;
                                    } else {
                                        // Buffer de áudio cheio, não bloquear
                                        attempts++;
                                        if (attempts < maxAttempts) {
                                            Thread.sleep(1);
                                        }
                                    }
                                }

                                // Se não conseguiu escrever tudo, pular este áudio
                                if (written < bytesToWrite) {
                                    System.out.println("Buffer de áudio cheio, pulando samples");
                                }
                            }
                        } catch (Exception audioEx) {
                            // Ignorar erros de áudio para não travar o vídeo
                            System.err.println("Erro ao processar áudio: " + audioEx.getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    isPlaying = false;
                    playPauseButton.setText("▶ Play");
                });
            }
        });

        playbackThread.start();
    }

    private void pauseVideo() {
        isPlaying = false;
        playPauseButton.setText("▶ Play");

        if (audioLine != null && audioLine.isRunning()) {
            audioLine.stop();
        }
    }

    private void stopVideo() {
        isPlaying = false;
        isStopped = true;
        playPauseButton.setText("▶ Play");

        if (audioLine != null && audioLine.isRunning()) {
            audioLine.stop();
            audioLine.flush();
        }

        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        currentFrame = 0;
        progressSlider.setValue(0);
        updateTimeLabel();

        if (grabber != null) {
            try {
                grabber.setFrameNumber(0);
                Frame firstFrame = grabber.grabImage();
                if (firstFrame != null && firstFrame.image != null) {
                    BufferedImage img = converter.convert(firstFrame);
                    if (img != null) {
                        videoPanel.updateImage(img);
                    }
                }
                grabber.setFrameNumber(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void seekToPosition(int percentage) {
        if (grabber == null || totalFrames == 0) return;

        boolean wasPlaying = isPlaying;
        if (wasPlaying) {
            pauseVideo();
        }

        try {
            long targetFrame = (totalFrames * percentage) / 100;
            grabber.setFrameNumber((int)targetFrame);

            Frame frame = grabber.grabImage();
            if (frame != null && frame.image != null) {
                BufferedImage img = converter.convert(frame);
                if (img != null) {
                    videoPanel.updateImage(img);
                }
                currentFrame = targetFrame;
            }

            if (audioLine != null) {
                audioLine.flush();
            }

            updateTimeLabel();

            if (wasPlaying) {
                Thread.sleep(100); // Pequeno delay antes de retomar
                playVideo();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTimeLabel() {
        if (grabber == null || frameRate == 0) return;

        long currentSeconds = (long)(currentFrame / frameRate);
        long totalSeconds = (long)(totalFrames / frameRate);

        String currentTime = formatTime(currentSeconds);
        String totalTime = formatTime(totalSeconds);

        timeLabel.setText(currentTime + " / " + totalTime);
    }

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    @Override
    public void dispose() {
        stopVideo();

        if (audioLine != null && audioLine.isOpen()) {
            audioLine.close();
        }

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatDraculaIJTheme.setup();
            VideoPlayer player = new VideoPlayer();
            player.setVisible(true);
        });
    }
}