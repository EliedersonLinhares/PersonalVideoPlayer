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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoPlayer extends JFrame {
    private VideoPanel videoPanel;
    private JButton playPauseButton;
    private JButton stopButton;
    private JSlider progressSlider;
    private JSlider volumeSlider;
    private JLabel timeLabel;
    private JLabel timeLabelPassed;
    private JLabel volumeLabel;
    private JButton openButton;

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
    private int currentAudioStream = 0;
    private int totalAudioStreams = 0;

    private long totalFrames;
    private double frameRate;
    private long currentFrame = 0;


    // Legendas
    private List<SubtitleEntry> subtitles = new ArrayList<>();
    private String currentSubtitleText = "";
    private int currentSubtitleStream = -1; // -1 = desabilitado
    private int totalSubtitleStreams = 0;
    private Map<Integer, String> audioStreamNames = new HashMap<>();
    private Map<Integer, String> subtitleStreamNames = new HashMap<>();
    private String videoFilePath;
    // Modificar as variáveis de instância
    private int baseSubtitleFontSize = 24; // Tamanho base configurado pelo usuário
    private Color subtitleColor = Color.WHITE;

    // Variáveis de instância para salvar estado completo
    private Rectangle normalBounds = null;
    private String currentVideoPath = null;
    private long savedFramePosition = 0;
    private boolean savedPlayingState = false;
    private List<SubtitleEntry> savedSubtitles = null;
    private String savedSubtitleText = "";
    private int savedSubtitleStream = -1;
    private int savedAudioStream = 0; // NOVO: Salvar stream de áudio

    // Adicionar variável de instância
    private Map<Integer, String> savedAudioStreamNames = new HashMap<>();
    private Map<Integer, String> savedSubtitleStreamNames = new HashMap<>();

    // Adicionar no início da classe, junto com outras variáveis de instância
    private Map<Integer, Integer> logicalToPhysicalAudioStream = new HashMap<>();

    // Adicionar no início da classe, junto com outras variáveis de instância
    private boolean hardwareAccelerationEnabled = false;

    // No início da classe, junto com outras variáveis:
    private JButton rewindButton;
    private JButton forwardButton;
    private JButton nextFrameButton;
    private JButton captureFrameButton;

    // No início da classe, junto com outras variáveis:
    private int framesToSkip = 1; // Quantidade de frames para avançar


public VideoPlayer() {
    setTitle("Video Player - JavaCV");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(800, 600);
    setLocationRelativeTo(null);

    converter = new Java2DFrameConverter();
    initComponents();

    // Adicionar listener de teclado no painel de vídeo (não no frame)
    videoPanel.setFocusable(true);
    videoPanel.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                if (gd.getFullScreenWindow() == VideoPlayer.this) {
                    exitFullScreen();
                }
            } else if (e.getKeyCode() == KeyEvent.VK_F11) {
                toggleFullScreen();
            } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                togglePlayPause();
            }
        }
    });
    // No construtor VideoPlayer(), adicionar após initComponents():
    videoPanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                // Click esquerdo - pausar/continuar
                if (grabber != null && (isPlaying || !isStopped)) {
                    togglePlayPause();
                }
            }
            videoPanel.requestFocusInWindow();
        }
    });

    // Garantir que o painel receba foco ao clicar
    videoPanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            videoPanel.requestFocusInWindow();
        }
    });
}

    private void toggleFullScreen() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (gd.getFullScreenWindow() == this) {
            exitFullScreen();
        } else {
            enterFullScreen();
        }
    }

    private void enterFullScreen() {
        System.out.println("Entrando em modo tela cheia...");

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (!gd.isFullScreenSupported()) {
            System.out.println("Tela cheia não suportada neste dispositivo");
            JOptionPane.showMessageDialog(this,
                    "Tela cheia não é suportada neste dispositivo.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Salvar estado do vídeo atual
            saveVideoState();

            // Salvar geometria da janela
            normalBounds = getBounds();

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
            dispose();
            setUndecorated(true);
            gd.setFullScreenWindow(this);
            setVisible(true);

            System.out.println("Modo tela cheia ativado");

            // Recarregar vídeo na posição salva
            if (currentVideoPath != null) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        Thread.sleep(200); // Pequeno delay para estabilizar
                        restoreVideoState();
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

    private void exitFullScreen() {
        System.out.println("Saindo do modo tela cheia...");

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        try {
            // Salvar estado do vídeo atual
            saveVideoState();

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
            dispose();
            setUndecorated(false);

            // Restaurar geometria
            if (normalBounds != null) {
                setBounds(normalBounds);
            }

            setVisible(true);

            System.out.println("Modo tela cheia desativado");

            // Recarregar vídeo na posição salva
            if (currentVideoPath != null) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        Thread.sleep(200); // Pequeno delay para estabilizar
                        restoreVideoState();
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

private void saveVideoState() {
    if (videoFilePath != null) {
        currentVideoPath = videoFilePath;
        savedFramePosition = currentFrame;
        savedPlayingState = isPlaying;

        // Salvar legendas
        savedSubtitles = new ArrayList<>(subtitles);
        savedSubtitleText = currentSubtitleText;
        savedSubtitleStream = currentSubtitleStream;

        // Salvar stream de áudio e SEMPRE salvar nomes e total
        savedAudioStream = currentAudioStream;
        savedAudioStreamNames = new HashMap<>(audioStreamNames);
        savedSubtitleStreamNames = new HashMap<>(subtitleStreamNames);

        // IMPORTANTE: Salvar totalAudioStreams também
        int savedTotal = totalAudioStreams;

        System.out.println("Estado salvo - Vídeo: " + currentVideoPath);
        System.out.println("Frame: " + savedFramePosition + ", Tocando: " + savedPlayingState);
        System.out.println("Legendas: " + savedSubtitles.size() + " entradas, Stream: " + savedSubtitleStream);
        System.out.println("Áudio Stream: " + savedAudioStream);
        System.out.println("Total Audio Streams: " + savedTotal);
        System.out.println("Streams de áudio salvas: " + savedAudioStreamNames.size());

        // Se não tem nomes salvos, criar nomes padrão baseado no total
        if (savedAudioStreamNames.isEmpty() && savedTotal > 0) {
            for (int i = 0; i < savedTotal; i++) {
                savedAudioStreamNames.put(i, "Faixa de Áudio " + (i + 1));
            }
            System.out.println("Nomes de áudio criados automaticamente: " + savedAudioStreamNames.size());
        }
    }
}

private void restoreVideoState() {
    if (currentVideoPath == null) {
        System.out.println("Nenhum vídeo para restaurar");
        return;
    }

    System.out.println("Restaurando vídeo: " + currentVideoPath);
    System.out.println("Posição: frame " + savedFramePosition);
    System.out.println("Stream de áudio: " + savedAudioStream);

    // Recarregar vídeo com stream de áudio salva
    loadVideoWithAudioStream(currentVideoPath, savedAudioStream);

    // Aguardar carregamento e então buscar posição
    new Thread(() -> {
        try {
            // Aguardar até o vídeo estar carregado
            int attempts = 0;
            while (grabber == null && attempts < 50) {
                Thread.sleep(100);
                attempts++;
            }

            if (grabber == null) {
                System.err.println("Timeout ao aguardar carregamento do vídeo");
                return;
            }

            System.out.println("Vídeo carregado, restaurando estado...");

            // Restaurar legendas primeiro
            if (savedSubtitles != null && !savedSubtitles.isEmpty()) {
                subtitles = new ArrayList<>(savedSubtitles);
                currentSubtitleStream = savedSubtitleStream;
                System.out.println("Legendas restauradas: " + subtitles.size() + " entradas");
            }

            // Restaurar nomes das streams
            if (!savedAudioStreamNames.isEmpty()) {
                audioStreamNames = new HashMap<>(savedAudioStreamNames);
                System.out.println("Nomes de streams de áudio restaurados: " + audioStreamNames.size());
            }

            if (!savedSubtitleStreamNames.isEmpty()) {
                subtitleStreamNames = new HashMap<>(savedSubtitleStreamNames);
                totalSubtitleStreams = subtitleStreamNames.size();
                System.out.println("Nomes de streams de legendas restaurados: " + subtitleStreamNames.size());
                System.out.println("totalSubtitleStreams definido: " + totalSubtitleStreams);
            }

            // Restaurar stream de áudio
            currentAudioStream = savedAudioStream;
            System.out.println("Stream de áudio restaurada: " + savedAudioStream);

            // Buscar posição salva
            SwingUtilities.invokeLater(() -> {
                try {
                    if (savedFramePosition > 0 && totalFrames > 0) {
                        // Ir para o frame salvo
                        grabber.setFrameNumber((int)savedFramePosition);
                        currentFrame = savedFramePosition;

                        // Capturar e exibir frame atual
                        Frame frame = grabber.grabImage();
                        if (frame != null && frame.image != null) {
                            BufferedImage img = converter.convert(frame);
                            if (img != null) {
                                videoPanel.updateImage(img);
                            }
                        }

                        // Resetar para posição correta (após grab)
                        grabber.setFrameNumber((int)savedFramePosition);
                        currentFrame = savedFramePosition;

                        // Atualizar slider de progresso
                        int progress = (int)((savedFramePosition * 100) / totalFrames);
                        progressSlider.setValue(progress);

                        // Atualizar label de tempo
                        updateTimeLabel();

                        // Atualizar legenda para o tempo atual
                        long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
                        updateSubtitle(currentTimeMs);

                        System.out.println("Posição restaurada: frame " + savedFramePosition);
                        System.out.println("Slider atualizado para: " + progress + "%");
                        System.out.println("Estado completamente restaurado:");
                        System.out.println("  - totalAudioStreams: " + totalAudioStreams);
                        System.out.println("  - currentAudioStream: " + currentAudioStream);
                        System.out.println("  - audioStreamNames size: " + audioStreamNames.size());
                        System.out.println("  - totalSubtitleStreams: " + totalSubtitleStreams);
                        System.out.println("  - subtitleStreamNames size: " + subtitleStreamNames.size());

                        // Retomar reprodução se estava tocando
                        if (savedPlayingState) {
                            new Thread(() -> {
                                try {
                                    Thread.sleep(300); // Delay para estabilizar
                                    SwingUtilities.invokeLater(() -> {
                                        playVideo();
                                        System.out.println("Reprodução retomada");
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }

                    } else if (savedPlayingState) {
                        // Se estava tocando do início
                        playVideo();
                    }

                } catch (Exception e) {
                    System.err.println("Erro ao restaurar posição: " + e.getMessage());
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            System.err.println("Erro ao restaurar estado: " + e.getMessage());
            e.printStackTrace();
        }
    }, "VideoStateRestorer").start();
}

private void loadVideoWithAudioStream(String filepath, int audioStream) {
    // Salvar informações das streams ANTES de fechar
    int savedTotalAudioStreams = totalAudioStreams;
    Map<Integer, String> savedAudioNames = new HashMap<>(audioStreamNames);
    Map<Integer, String> savedSubtitleNames = new HashMap<>(subtitleStreamNames);
    int savedTotalSubtitleStreams = totalSubtitleStreams;
    Map<Integer, Integer> savedAudioMapping = new HashMap<>(logicalToPhysicalAudioStream); // NOVO

    // Fechar recursos atuais
    if (grabber != null) {
        try {
            grabber.stop();
            grabber.release();
        } catch (Exception e) {
            System.out.println("Erro ao fechar grabber anterior: " + e.getMessage());
        }
        grabber = null;
    }

    if (audioLine != null && audioLine.isOpen()) {
        audioLine.close();
        audioLine = null;
    }

    // Limpar estado (mas preservar informações de streams)
    subtitles.clear();
    currentSubtitleText = "";
    currentSubtitleStream = -1;
    currentFrame = 0;

    // Desabilitar controles durante carregamento
    playPauseButton.setEnabled(false);
    stopButton.setEnabled(false);
    progressSlider.setEnabled(false);
    openButton.setEnabled(false);

    setTitle("Video Player - Carregando...");

    // Carregar vídeo em thread separada
    new Thread(() -> {
        try {
            videoFilePath = filepath;
            currentVideoPath = filepath;

            System.out.println("Abrindo arquivo com stream de áudio lógico " + audioStream + ": " + filepath);

            grabber = new FFmpegFrameGrabber(filepath);

            // Converter índice lógico para físico
            int physicalStreamIndex = savedAudioMapping.getOrDefault(audioStream, audioStream);

            // IMPORTANTE: SEMPRE definir stream de áudio usando índice físico
            grabber.setAudioStream(physicalStreamIndex);
            System.out.println("Stream de áudio selecionada: lógico=" + audioStream + ", físico=" + physicalStreamIndex);

            String extension = filepath.substring(filepath.lastIndexOf('.') + 1).toLowerCase();

            if (hardwareAccelerationEnabled) {
                tryEnableHardwareAcceleration(grabber);
            }

            if (extension.equals("wmv")) {
                try {
                    grabber.setOption("threads", "auto");
                    grabber.setOption("fflags", "nobuffer");
                    grabber.setOption("flags", "low_delay");
                } catch (Exception e) {
                    System.out.println("Não foi possível aplicar opções WMV: " + e.getMessage());
                }
            }

            try {
                grabber.setOption("analyzeduration", "2000000");
                grabber.setOption("probesize", "2000000");
                grabber.setOption("fflags", "+genpts");
            } catch (Exception e) {
                System.out.println("Não foi possível aplicar opções de análise rápida");
            }

            System.out.println("Iniciando grabber...");
            grabber.start();
            System.out.println("Grabber iniciado com sucesso!");

            // IMPORTANTE: Usar total de streams salvo (não re-detectar)
            totalAudioStreams = savedTotalAudioStreams;
            currentAudioStream = audioStream;

            // Restaurar mapeamento
            logicalToPhysicalAudioStream = new HashMap<>(savedAudioMapping);
            System.out.println("Mapeamento de áudio restaurado: " + logicalToPhysicalAudioStream);

            // Restaurar nomes das streams
            audioStreamNames = new HashMap<>(savedAudioNames);
            subtitleStreamNames = new HashMap<>(savedSubtitleNames);
            totalSubtitleStreams = savedTotalSubtitleStreams;

            System.out.println("Total de faixas de áudio (restaurado): " + totalAudioStreams);
            System.out.println("Stream de áudio atual: " + currentAudioStream);
            System.out.println("Nomes de áudio restaurados: " + audioStreamNames.size());
            System.out.println("Nomes de legendas restaurados: " + subtitleStreamNames.size());
            System.out.println("Total de legendas: " + totalSubtitleStreams);

            // Só detectar legendas se ainda não temos informação
            if (subtitleStreamNames.isEmpty()) {
                new Thread(() -> {
                    try {
                        detectEmbeddedSubtitles(filepath);
                    } catch (Exception e) {
                        System.out.println("Não foi possível detectar legendas embutidas: " + e.getMessage());
                    }
                }, "SubtitleDetector").start();
            }

            totalFrames = grabber.getLengthInVideoFrames();
            frameRate = grabber.getVideoFrameRate();
            currentFrame = 0;

            System.out.println("Total frames bruto: " + totalFrames + ", FPS bruto: " + frameRate);

            if (frameRate > 120 || frameRate < 1) {
                System.out.println("FPS inválido, corrigindo para 30");
                frameRate = 30.0;
            }

            System.out.println("Configurando áudio...");
            audioChannels = grabber.getAudioChannels();
            sampleRate = grabber.getSampleRate();
            System.out.println("Canais: " + audioChannels + ", SampleRate: " + sampleRate);

            if (audioChannels > 0 && sampleRate > 0 && !extension.equals("gif")) {
                System.out.println("Criando audioLine...");
                try {
                    int outputChannels = audioChannels > 2 ? 2 : audioChannels;

                    AudioFormat audioFormat = new AudioFormat(sampleRate, 16, outputChannels, true, true);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                    audioLine = (SourceDataLine) AudioSystem.getLine(info);

                    int bufferSize = sampleRate * outputChannels * 2;
                    if (extension.equals("wmv")) bufferSize *= 4;

                    audioLine.open(audioFormat, bufferSize);
                    System.out.println("AudioLine configurado com sucesso");
                } catch (Exception audioEx) {
                    System.err.println("Erro ao configurar áudio: " + audioEx.getMessage());
                    audioLine = null;
                }
            } else {
                System.out.println("Sem áudio");
            }

            System.out.println("Procurando legendas externas...");
            searchExternalSubtitles(filepath);
            System.out.println("Busca de legendas concluída");

            System.out.println("Vídeo carregado! Habilitando UI...");

            SwingUtilities.invokeLater(() -> {
                playPauseButton.setEnabled(true);
                stopButton.setEnabled(true);
                progressSlider.setEnabled(true);
                progressSlider.setValue(0);
                openButton.setEnabled(true);

                updateTimeLabel();

                setTitle("Video Player - " + new java.io.File(filepath).getName());

                System.out.println("UI HABILITADA - Pronto para reproduzir!");
                System.out.println("Estado final:");
                System.out.println("  totalAudioStreams: " + totalAudioStreams);
                System.out.println("  currentAudioStream: " + currentAudioStream);
                System.out.println("  audioStreamNames: " + audioStreamNames);
                System.out.println("  logicalToPhysicalAudioStream: " + logicalToPhysicalAudioStream);
                System.out.println("  totalSubtitleStreams: " + totalSubtitleStreams);
                System.out.println("  subtitleStreamNames: " + subtitleStreamNames);
            });

        } catch (Exception e) {
            System.err.println("ERRO ao carregar vídeo:");
            e.printStackTrace();

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Erro ao abrir vídeo:\n" + e.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);

                openButton.setEnabled(true);
                playPauseButton.setEnabled(false);
                stopButton.setEnabled(false);
                setTitle("Video Player - JavaCV");
            });
        }
    }, "VideoLoader").start();
}


    // Modificar o método detectAudioStreamNames
    private void detectAudioStreamNames(String filepath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_streams",
                    "-select_streams", "a",
                    filepath
            );

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();

            String json = output.toString();
            parseAudioStreamsFromJson(json);

        } catch (Exception e) {
            System.out.println("Não foi possível detectar nomes de streams de áudio: " + e.getMessage());
            // Usar nomes padrão e mapeamento 1:1
            for (int i = 0; i < totalAudioStreams; i++) {
                if (!audioStreamNames.containsKey(i)) {
                    audioStreamNames.put(i, "Faixa de Áudio " + (i + 1));
                }
                logicalToPhysicalAudioStream.put(i, i);
            }
        }
    }


private void parseAudioStreamsFromJson(String json) {
    int audioLogicalIndex = 0;

    Pattern indexPattern = Pattern.compile("\"index\"\\s*:\\s*(\\d+)");
    Pattern langPattern = Pattern.compile("\"(?:language|TAG:language)\"\\s*:\\s*\"([^\"]+)\"");
    Pattern titlePattern = Pattern.compile("\"(?:TAG:)?title\"\\s*:\\s*\"([^\"]+)\"");
    Pattern codecPattern = Pattern.compile("\"codec_type\"\\s*:\\s*\"audio\"");

    Matcher codecMatcher = codecPattern.matcher(json);

    while (codecMatcher.find()) {
        int matchStart = codecMatcher.start();
        int streamStart = json.lastIndexOf("{", matchStart);
        int streamEnd = findMatchingBrace(json, streamStart);

        if (streamStart != -1 && streamEnd > streamStart) {
            String streamData = json.substring(streamStart, streamEnd);

            // Extrair índice físico da stream no container
            int physicalIndex = audioLogicalIndex; // Default
            Matcher indexMatcher = indexPattern.matcher(streamData);
            if (indexMatcher.find()) {
                physicalIndex = Integer.parseInt(indexMatcher.group(1));
            }

            // Mapear índice lógico (0, 1, 2...) para físico (pode ser 1, 2, 3... no MKV)
            logicalToPhysicalAudioStream.put(audioLogicalIndex, physicalIndex);

            // Extrair idioma
            String lang = null;
            Matcher langMatcher = langPattern.matcher(streamData);
            if (langMatcher.find()) {
                lang = langMatcher.group(1);
            }

            // Extrair título
            String title = null;
            Matcher titleMatcher = titlePattern.matcher(streamData);
            if (titleMatcher.find()) {
                title = titleMatcher.group(1);
            }

            // Criar nome do stream
            String streamName;
            if (title != null && !title.isEmpty()) {
                streamName = title;
            } else if (lang != null && !lang.isEmpty()) {
                streamName = "Áudio " + lang.toUpperCase();
            } else {
                streamName = "Faixa de Áudio " + (audioLogicalIndex + 1);
            }

            audioStreamNames.put(audioLogicalIndex, streamName);
            System.out.println("Stream lógico " + audioLogicalIndex + " → físico " + physicalIndex + ": " + streamName);

            audioLogicalIndex++;
        }
    }
}

    // Entrada de legenda
    class SubtitleEntry {
        long startTime; // em milissegundos
        long endTime;
        String text;

        SubtitleEntry(long start, long end, String txt) {
            this.startTime = start;
            this.endTime = end;
            this.text = txt;
        }
    }

    // Painel customizado com double buffering e legendas
    class VideoPanel extends JPanel {
        private BufferedImage currentImage;

        public VideoPanel() {
            setBackground(Color.BLACK);
            setDoubleBuffered(true);
            setupContextMenu();
        }

private void setupContextMenu() {
    JPopupMenu contextMenu = new JPopupMenu();

    // Menu de áudio
    JMenu audioMenu = new JMenu("Faixa de Áudio");
    contextMenu.add(audioMenu);

    // Menu de legendas
    JMenu subtitleMenu = new JMenu("Legendas");
    JMenuItem noSubtitle = new JMenuItem("Desabilitado");
    noSubtitle.addActionListener(e -> {
        currentSubtitleStream = -1;
        currentSubtitleText = "";
        repaint();
    });
    subtitleMenu.add(noSubtitle);

    JMenuItem loadExternal = new JMenuItem("Carregar arquivo externo...");
    loadExternal.addActionListener(e -> loadExternalSubtitle());
    subtitleMenu.add(loadExternal);

    contextMenu.add(subtitleMenu);

    // Menu de configurações de legenda
    JMenu subtitleSettingsMenu = new JMenu("Configurações de Legenda");

    // Submenu de tamanho
    JMenu sizeMenu = new JMenu("Tamanho da Fonte");
    int[] sizes = {16, 20, 24, 28, 32, 36, 40, 48, 56, 64};
    ButtonGroup sizeGroup = new ButtonGroup();

    for (int size : sizes) {
        JRadioButtonMenuItem sizeItem = new JRadioButtonMenuItem(size + "px");
        sizeItem.setSelected(size == baseSubtitleFontSize);
        sizeItem.addActionListener(e -> {
            baseSubtitleFontSize = size;
            System.out.println("Tamanho base da legenda alterado para: " + size);
            repaint();
        });
        sizeGroup.add(sizeItem);
        sizeMenu.add(sizeItem);
    }

    subtitleSettingsMenu.add(sizeMenu);

    // Submenu de cor
    JMenu colorMenu = new JMenu("Cor da Legenda");
    ButtonGroup colorGroup = new ButtonGroup();

    JRadioButtonMenuItem whiteColor = new JRadioButtonMenuItem("Branco");
    whiteColor.setSelected(subtitleColor.equals(Color.WHITE));
    whiteColor.addActionListener(e -> {
        subtitleColor = Color.WHITE;
        System.out.println("Cor da legenda alterada para: Branco");
        repaint();
    });
    colorGroup.add(whiteColor);
    colorMenu.add(whiteColor);

    JRadioButtonMenuItem yellowColor = new JRadioButtonMenuItem("Amarelo");
    yellowColor.setSelected(subtitleColor.equals(Color.YELLOW));
    yellowColor.addActionListener(e -> {
        subtitleColor = Color.YELLOW;
        System.out.println("Cor da legenda alterada para: Amarelo");
        repaint();
    });
    colorGroup.add(yellowColor);
    colorMenu.add(yellowColor);

    subtitleSettingsMenu.add(colorMenu);

    contextMenu.add(subtitleSettingsMenu);

    // NOVO: Menu de Performance
    JMenu performanceMenu = new JMenu("Performance");

    JCheckBoxMenuItem hwAccelItem = new JCheckBoxMenuItem("Aceleração GPU");
    hwAccelItem.setSelected(hardwareAccelerationEnabled);
    hwAccelItem.addActionListener(e -> {
        hardwareAccelerationEnabled = hwAccelItem.isSelected();
        System.out.println("Aceleração GPU: " + (hardwareAccelerationEnabled ? "Habilitada" : "Desabilitada"));

        if (grabber != null) {
            JOptionPane.showMessageDialog(VideoPlayer.this,
                    "A aceleração GPU será aplicada ao recarregar o vídeo.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
        }
    });

    performanceMenu.add(hwAccelItem);

// NOVO: Submenu para configurar quantidade de frames
    performanceMenu.addSeparator();
    JMenu frameSkipMenu = new JMenu("Frames por Avanço");

    ButtonGroup frameSkipGroup = new ButtonGroup();
    int[] skipValues = {1, 2, 3, 5, 10, 15, 30};

    for (int skipValue : skipValues) {
        JRadioButtonMenuItem skipItem = new JRadioButtonMenuItem(skipValue + " frame" + (skipValue > 1 ? "s" : ""));
        skipItem.setSelected(skipValue == framesToSkip);

        final int value = skipValue;
        skipItem.addActionListener(e -> {
            framesToSkip = value;
            System.out.println("Frames por avanço alterado para: " + framesToSkip);

            // Atualizar tooltip do botão nextFrame
            if (nextFrameButton != null) {
                nextFrameButton.setToolTipText("Avançar " + framesToSkip + " frame" + (framesToSkip > 1 ? "s" : ""));
            }
        });

        frameSkipGroup.add(skipItem);
        frameSkipMenu.add(skipItem);
    }

    performanceMenu.add(frameSkipMenu);

    contextMenu.add(performanceMenu);

    // Separador
    contextMenu.addSeparator();

    // Opção de tela cheia
    JCheckBoxMenuItem fullscreenItem = new JCheckBoxMenuItem("Tela Cheia");
    fullscreenItem.addActionListener(e -> {
        toggleFullScreen();
    });
    contextMenu.add(fullscreenItem);

    // Atualizar estado ao abrir menu
    contextMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
        public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
            if (grabber == null) {
                JOptionPane.showMessageDialog(VideoPlayer.this,
                        "Nenhum vídeo carregado.\nAbra um vídeo primeiro.",
                        "Aviso", JOptionPane.INFORMATION_MESSAGE);
                SwingUtilities.invokeLater(() -> contextMenu.setVisible(false));
                return;
            }

            updateContextMenus(audioMenu, subtitleMenu);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            fullscreenItem.setSelected(gd.getFullScreenWindow() == VideoPlayer.this);
        }
        public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
        public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
    });

    // Adicionar menu ao painel
    addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    });
}
        // Novo método para calcular tamanho adaptativo
        private int getAdaptiveSubtitleSize() {
            int panelHeight = getHeight();

            // Tamanho de referência: 600px de altura = tamanho base
            // Escala proporcionalmente baseado na altura do painel
            double heightRatio = panelHeight / 600.0;

            // Aplicar escala ao tamanho base, com limites
            int adaptiveSize = (int) (baseSubtitleFontSize * heightRatio);

            // Limitar entre 12px e 120px para evitar extremos
            adaptiveSize = Math.max(12, Math.min(120, adaptiveSize));

            return adaptiveSize;
        }


// Modificar o método updateContextMenus:
private void updateContextMenus(JMenu audioMenu, JMenu subtitleMenu) {
    // Atualizar menu de áudio
    audioMenu.removeAll();
    if (totalAudioStreams > 1) {
        for (int i = 0; i < totalAudioStreams; i++) {
            final int streamIndex = i;

            // Tentar obter nome da stream
            String streamName = audioStreamNames.getOrDefault(i, "Faixa de Áudio " + (i + 1));

            JCheckBoxMenuItem item = new JCheckBoxMenuItem(streamName);
            item.setSelected(i == currentAudioStream);
            item.addActionListener(e -> switchAudioStream(streamIndex));
            audioMenu.add(item);
        }
    } else {
        JMenuItem noAudio = new JMenuItem("Apenas uma faixa disponível");
        noAudio.setEnabled(false);
        audioMenu.add(noAudio);
    }

    // Atualizar menu de legendas embutidas
    // IMPORTANTE: Contar quantos itens fixos existem (Desabilitado + Carregar externo)
    int fixedItemsCount = 2;

    // Remover apenas itens de legendas embutidas (manter os 2 primeiros fixos)
    while (subtitleMenu.getMenuComponentCount() > fixedItemsCount) {
        subtitleMenu.remove(fixedItemsCount);
    }

    // Se tem legendas embutidas, adicionar separador e as opções
    if (totalSubtitleStreams > 0) {
        // Adicionar separador após "Carregar arquivo externo"
        subtitleMenu.insertSeparator(fixedItemsCount);

        // Adicionar legendas embutidas
        for (int i = 0; i < totalSubtitleStreams; i++) {
            final int streamIndex = i;
            String streamName = subtitleStreamNames.getOrDefault(i, "Legenda " + (i + 1));
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(streamName);
            item.setSelected(i == currentSubtitleStream);
            item.addActionListener(e -> switchSubtitleStream(streamIndex));
            subtitleMenu.add(item);
        }
    }
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

            double panelRatio = (double) panelWidth / panelHeight;
            double imgRatio = (double) imgWidth / imgHeight;

            int drawWidth, drawHeight, x, y;

            if (panelRatio > imgRatio) {
                drawHeight = panelHeight;
                drawWidth = (int) (imgWidth * ((double) panelHeight / imgHeight));
                x = (panelWidth - drawWidth) / 2;
                y = 0;
            } else {
                drawWidth = panelWidth;
                drawHeight = (int) (imgHeight * ((double) panelWidth / imgWidth));
                x = 0;
                y = (panelHeight - drawHeight) / 2;
            }

            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            g2d.drawImage(currentImage, x, y, drawWidth, drawHeight, null);

            // Desenhar legendas
            if (!currentSubtitleText.isEmpty()) {
                drawSubtitles(g2d, panelWidth, panelHeight);
            }
        }

        // Modificar o método drawSubtitles()
        private void drawSubtitles(Graphics2D g2d, int panelWidth, int panelHeight) {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Usar tamanho adaptativo baseado no tamanho da janela
            int adaptiveFontSize = getAdaptiveSubtitleSize();
            Font subtitleFont = new Font("Arial", Font.BOLD, adaptiveFontSize);
            g2d.setFont(subtitleFont);

            // Dividir texto em linhas
            String[] lines = currentSubtitleText.split("\n");
            FontMetrics fm = g2d.getFontMetrics();

            int lineHeight = fm.getHeight();
            int totalHeight = lineHeight * lines.length;

            // Posição vertical: 10% da altura da tela acima da parte inferior
            int marginBottom = (int) (panelHeight * 0.10);
            int startY = panelHeight - totalHeight - marginBottom;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int textWidth = fm.stringWidth(line);
                int textX = (panelWidth - textWidth) / 2;
                int textY = startY + (i * lineHeight) + fm.getAscent();

                // Sombra preta (contorno proporcional ao tamanho)
                g2d.setColor(Color.BLACK);
                int shadowSize = Math.max(2, adaptiveFontSize / 12);

                for (int dx = -shadowSize; dx <= shadowSize; dx++) {
                    for (int dy = -shadowSize; dy <= shadowSize; dy++) {
                        if (dx != 0 || dy != 0) {
                            g2d.drawString(line, textX + dx, textY + dy);
                        }
                    }
                }

                // Texto com cor configurável
                g2d.setColor(subtitleColor);
                g2d.drawString(line, textX, textY);
            }
        }
    }


private void initComponents() {
    setLayout(new BorderLayout());

    videoPanel = new VideoPanel();
    add(videoPanel, BorderLayout.CENTER);

    // Painel de controles
    JPanel controlPanel = new JPanel(new BorderLayout());
    controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Barra de progresso (PRIMEIRO - no topo)
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

    Font mainFont = new Font("Segoe UI", Font.PLAIN, 14);

    timeLabel = new JLabel("00:00");
    timeLabelPassed = new JLabel("00:00");
    timeLabel.setFont(mainFont);
    timeLabelPassed.setFont(mainFont);

    progressPanel.add(timeLabelPassed, BorderLayout.WEST);
    progressPanel.add(progressSlider, BorderLayout.CENTER);
    progressPanel.add(timeLabel, BorderLayout.EAST);

    // Botões (SEGUNDO - embaixo do progressPanel)
    JPanel buttonPanel = new JPanel(new BorderLayout());

    // Painel central com controles principais (centralizado)
    JPanel centerButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));

    openButton = new JButton("Abrir");
    openButton.setPreferredSize(new Dimension(35, 35));
    openButton.setToolTipText("Abrir nova midia");
    openButton.addActionListener(e -> openVideo());

    rewindButton = new JButton("⏪");
    rewindButton.setEnabled(false);
    rewindButton.setPreferredSize(new Dimension(35, 35));
    rewindButton.setToolTipText("Retroceder 10 segundos");
    rewindButton.addActionListener(e -> rewind10Seconds());

    playPauseButton = new JButton("▶");
    playPauseButton.setEnabled(false);
    playPauseButton.setPreferredSize(new Dimension(50, 50)); // Maior que os outros
    playPauseButton.setToolTipText("Tocar/Pausar");
    playPauseButton.addActionListener(e -> togglePlayPause());

    forwardButton = new JButton("⏩");
    forwardButton.setEnabled(false);
    forwardButton.setPreferredSize(new Dimension(35, 35));
    forwardButton.setToolTipText("Avançar 10 segundos");
    forwardButton.addActionListener(e -> forward10Seconds());

    stopButton = new JButton("■");
    stopButton.setEnabled(false);
    stopButton.setPreferredSize(new Dimension(35, 35));
    stopButton.setToolTipText("Parar");
    stopButton.addActionListener(e -> stopVideo());

    nextFrameButton = new JButton("⏭");
    nextFrameButton.setEnabled(false);
    nextFrameButton.setPreferredSize(new Dimension(35, 35));
    nextFrameButton.setToolTipText("Avançar um frame");
    nextFrameButton.addActionListener(e -> nextFrame());

    captureFrameButton = new JButton("📷");
    captureFrameButton.setEnabled(false);
    captureFrameButton.setPreferredSize(new Dimension(35, 35));
    captureFrameButton.setToolTipText("Capturar frame atual");
  // captureFrameButton.addActionListener(e -> captureFrame()); // Implementar depois

    centerButtonPanel.add(openButton);
    centerButtonPanel.add(rewindButton);
    centerButtonPanel.add(playPauseButton);
    centerButtonPanel.add(forwardButton);
    centerButtonPanel.add(stopButton);
    centerButtonPanel.add(nextFrameButton);
    centerButtonPanel.add(captureFrameButton);

    // Painel direito com controle de volume
    JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 15));

    volumeLabel = new JLabel("🔊 100%");
    volumeSlider = new JSlider(0, 100, 100);
    volumeSlider.setPreferredSize(new Dimension(100, 20));
    volumeSlider.addChangeListener(e -> {
        int vol = volumeSlider.getValue();
        volume = vol / 100.0f;
        volumeLabel.setText("🔊 " + vol + "%");
    });

    rightButtonPanel.add(volumeLabel);
    rightButtonPanel.add(volumeSlider);

    // Montar painel de botões
    buttonPanel.add(centerButtonPanel, BorderLayout.CENTER);
    buttonPanel.add(rightButtonPanel, BorderLayout.EAST);

    // Adicionar ao painel de controles
    controlPanel.add(progressPanel, BorderLayout.NORTH);
    controlPanel.add(buttonPanel, BorderLayout.SOUTH);

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
    // Salvar caminho do vídeo
    currentVideoPath = filepath;
    videoFilePath = filepath;

    System.out.println("=== INÍCIO loadVideo ===");

    // Fechar vídeo anterior ANTES de iniciar nova thread
    if (isPlaying) {
        System.out.println("Parando reprodução anterior...");
        isPlaying = false;
        if (playbackThread != null) {
            try {
                playbackThread.interrupt();
                playbackThread.join(500);
            } catch (Exception e) {
                System.out.println("Erro ao parar thread de playback: " + e.getMessage());
            }
        }
    }

    if (grabber != null) {
        System.out.println("Fechando grabber anterior...");
        try {
            grabber.stop();
            grabber.release();
        } catch (Exception e) {
            System.out.println("Erro ao fechar grabber anterior: " + e.getMessage());
        }
        grabber = null;
    }

    if (audioLine != null && audioLine.isOpen()) {
        System.out.println("Fechando audioLine anterior...");
        audioLine.close();
        audioLine = null;
    }

    // Limpar estado
    System.out.println("Limpando estado...");
    subtitles.clear();
    currentSubtitleText = "";
    currentSubtitleStream = -1;
    currentFrame = 0;

    // Desabilitar controles durante carregamento
    System.out.println("Desabilitando controles...");
    playPauseButton.setEnabled(false);
    stopButton.setEnabled(false);
    progressSlider.setEnabled(false);
    openButton.setEnabled(false);

    System.out.println("Atualizando título...");
    setTitle("Video Player - Carregando...");

    System.out.println("Iniciando thread de carregamento...");

    // Carregar vídeo em thread separada
    Thread loaderThread = new Thread(() -> {
        System.out.println("Thread de carregamento INICIADA");
        try {
            System.out.println("1. videoFilePath definido: " + videoFilePath);

            System.out.println("2. Criando FFmpegFrameGrabber...");
            grabber = new FFmpegFrameGrabber(filepath);
            System.out.println("3. FFmpegFrameGrabber criado");

            String extension = filepath.substring(filepath.lastIndexOf('.') + 1).toLowerCase();
            System.out.println("4. Extensão detectada: " + extension);


            if (hardwareAccelerationEnabled) {
                System.out.println("5. Tentando habilitar aceleração GPU...");
                tryEnableHardwareAcceleration(grabber);
            } else {
                System.out.println("5. Aceleração GPU desabilitada");
            }

            // Opções para melhorar performance
            if (extension.equals("wmv")) {
                System.out.println("6. Aplicando opções WMV...");
                try {
                    grabber.setOption("threads", "auto");
                    grabber.setOption("fflags", "nobuffer");
                    grabber.setOption("flags", "low_delay");
                } catch (Exception e) {
                    System.out.println("Erro nas opções WMV: " + e.getMessage());
                }
            } else {
                System.out.println("6. Não é WMV, pulando opções específicas");
            }

            // Opções gerais
            System.out.println("7. Aplicando opções gerais...");
            try {
                grabber.setOption("analyzeduration", "2000000");
                grabber.setOption("probesize", "2000000");
                grabber.setOption("fflags", "+genpts");
                System.out.println("7. Opções aplicadas");
            } catch (Exception e) {
                System.out.println("Erro nas opções gerais: " + e.getMessage());
            }

            System.out.println("Iniciando pré-detecção de streams de áudio...");

            // NOVO: Detectar mapeamento ANTES de start() usando um grabber temporário
            try {
                FFmpegFrameGrabber tempGrabber = new FFmpegFrameGrabber(filepath);
                tempGrabber.start();
                totalAudioStreams = tempGrabber.getAudioStream();
                tempGrabber.stop();
                tempGrabber.release();

                System.out.println("Total de faixas de áudio pré-detectadas: " + totalAudioStreams);

                // Detectar nomes e mapeamento
                if (totalAudioStreams > 0) {
                    detectAudioStreamNames(filepath);
                    System.out.println("Mapeamento de áudio detectado: " + logicalToPhysicalAudioStream);
                }

                // Agora usar o mapeamento para stream lógica 0
                if (!logicalToPhysicalAudioStream.isEmpty()) {
                    int physicalStream0 = logicalToPhysicalAudioStream.get(0);
                    System.out.println("Definindo stream lógica 0 (física " + physicalStream0 + ")");
                    grabber.setAudioStream(physicalStream0);
                } else {
                    System.out.println("Mapeamento vazio, não definindo stream de áudio");
                }

            } catch (Exception e) {
                System.out.println("Erro na pré-detecção de áudio: " + e.getMessage());
                e.printStackTrace();
                // Continuar sem setAudioStream - usará stream padrão
            }

            System.out.println("8. Chamando grabber.start()...");
            grabber.start();
            System.out.println("9. grabber.start() CONCLUÍDO!");

            // Definir stream atual como 0 (primeira lógica)
            currentAudioStream = 0;
            System.out.println("10. Stream de áudio atual definida: " + currentAudioStream);

            // Detectar streams de legendas em thread separada
            System.out.println("11. Iniciando detecção de legendas em thread separada...");
            new Thread(() -> {
                try {
                    System.out.println("Thread de legendas INICIADA");
                    detectEmbeddedSubtitles(filepath);
                    System.out.println("Thread de legendas CONCLUÍDA");
                } catch (Exception e) {
                    System.out.println("Não foi possível detectar legendas embutidas: " + e.getMessage());
                }
            }, "SubtitleDetector").start();

            System.out.println("12. Obtendo totalFrames e frameRate...");
            totalFrames = grabber.getLengthInVideoFrames();
            frameRate = grabber.getVideoFrameRate();
            currentFrame = 0;
            System.out.println("13. Total frames: " + totalFrames + ", FPS: " + frameRate);

            // Correções de FPS
            System.out.println("14. Verificando correções de FPS...");

            if (extension.equals("flv") && frameRate > 100) {
                double tbr = grabber.getFrameRate();
                if (tbr > 0 && tbr < 100) {
                    frameRate = tbr;
                    System.out.println("FLV detectado - usando FPS corrigido: " + frameRate);
                } else {
                    frameRate = 29.97;
                    System.out.println("FLV detectado - usando FPS padrão: 29.97");
                }
            }

            if (extension.equals("wmv") && frameRate > 100) {
                double tbr = grabber.getFrameRate();
                if (tbr > 0 && tbr < 100) {
                    frameRate = tbr;
                    System.out.println("WMV detectado - usando FPS corrigido: " + frameRate);
                } else {
                    frameRate = 30.0;
                    System.out.println("WMV detectado - usando FPS padrão: 30");
                }
            }

            if (extension.equals("gif")) {
                double tbr = grabber.getFrameRate();
                if (tbr > 0 && Math.abs(tbr - frameRate) > 1) {
                    frameRate = tbr;
                    System.out.println("GIF detectado - usando FPS (tbr): " + frameRate);
                }
                audioLine = null;
            }

            if (frameRate > 120 || frameRate < 1) {
                System.out.println("FPS inválido, corrigindo para 30");
                frameRate = 30.0;
            }

            System.out.println("15. Configurando áudio...");
            audioChannels = grabber.getAudioChannels();
            sampleRate = grabber.getSampleRate();
            System.out.println("16. Canais: " + audioChannels + ", SampleRate: " + sampleRate);

            if (audioChannels > 0 && sampleRate > 0 && !extension.equals("gif")) {
                System.out.println("17. Criando audioLine...");
                try {
                    int outputChannels = audioChannels > 2 ? 2 : audioChannels;

                    if (audioChannels > 2) {
                        System.out.println("Áudio " + audioChannels + " canais detectado, fazendo downmix para estéreo");
                    }

                    AudioFormat audioFormat = new AudioFormat(sampleRate, 16, outputChannels, true, true);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                    audioLine = (SourceDataLine) AudioSystem.getLine(info);

                    int bufferSize = sampleRate * outputChannels * 2;
                    if (extension.equals("wmv")) bufferSize *= 4;

                    audioLine.open(audioFormat, bufferSize);
                    System.out.println("18. AudioLine configurado com sucesso");
                } catch (Exception audioEx) {
                    System.err.println("18. Erro ao configurar áudio: " + audioEx.getMessage());
                    audioLine = null;
                }
            } else {
                System.out.println("17-18. Sem áudio");
            }

            System.out.println("19. Procurando legendas externas...");
            searchExternalSubtitles(filepath);
            System.out.println("20. Busca de legendas concluída");

            System.out.println("21. Vídeo carregado! Habilitando UI...");

            SwingUtilities.invokeLater(() -> {
                System.out.println("22. SwingUtilities.invokeLater EXECUTANDO");
                playPauseButton.setEnabled(true);
                stopButton.setEnabled(true);
                progressSlider.setEnabled(true);
                progressSlider.setValue(0);
                openButton.setEnabled(true);
                rewindButton.setEnabled(true);      // NOVO
                forwardButton.setEnabled(true);     // NOVO
                nextFrameButton.setEnabled(true);   // NOVO
                captureFrameButton.setEnabled(true); // NOVO

                updateTimeLabel();

                setTitle("Video Player - " + new java.io.File(filepath).getName());

                System.out.println("23. UI HABILITADA - Pronto para reproduzir!");
            });

            System.out.println("24. Thread de carregamento CONCLUÍDA");

        } catch (Exception e) {
            System.err.println("ERRO CRÍTICO na thread de carregamento:");
            e.printStackTrace();

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Erro ao abrir vídeo:\n" + e.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);

                openButton.setEnabled(true);
                playPauseButton.setEnabled(false);
                stopButton.setEnabled(false);
                setTitle("Video Player - JavaCV");
            });
        }
    }, "VideoLoader");

    System.out.println("Thread criada, iniciando...");
    loaderThread.start();
    System.out.println("Thread iniciada! Aguardando conclusão...");
    System.out.println("=== FIM loadVideo (método principal) ===");
}

    private void rewind10Seconds() {
        if (grabber == null || totalFrames == 0) return;

        System.out.println("Retrocedendo 10 segundos...");

        // Calcular quantos frames correspondem a 10 segundos
        long framesTo10Seconds = (long)(frameRate * 10);
        long targetFrame = Math.max(0, currentFrame - framesTo10Seconds);

        boolean wasPlaying = isPlaying;
        if (wasPlaying) {
            pauseVideo();
        }

        try {
            grabber.setFrameNumber((int)targetFrame);
            currentFrame = targetFrame;

            // Capturar e exibir frame
            Frame frame = grabber.grabImage();
            if (frame != null && frame.image != null) {
                BufferedImage img = converter.convert(frame);
                if (img != null) {
                    videoPanel.updateImage(img);
                }
            }

            // Resetar posição
            grabber.setFrameNumber((int)targetFrame);

            // Atualizar UI
            updateTimeLabel();
            int progress = (int)((targetFrame * 100) / totalFrames);
            progressSlider.setValue(progress);

            // Atualizar legenda
            long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
            updateSubtitle(currentTimeMs);

            if (audioLine != null) {
                audioLine.flush();
            }

            System.out.println("Retrocedeu para frame: " + targetFrame);

            if (wasPlaying) {
                Thread.sleep(100);
                playVideo();
            }

        } catch (Exception e) {
            System.err.println("Erro ao retroceder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void forward10Seconds() {
        if (grabber == null || totalFrames == 0) return;

        System.out.println("Avançando 10 segundos...");

        // Calcular quantos frames correspondem a 10 segundos
        long framesTo10Seconds = (long)(frameRate * 10);
        long targetFrame = Math.min(totalFrames - 1, currentFrame + framesTo10Seconds);

        boolean wasPlaying = isPlaying;
        if (wasPlaying) {
            pauseVideo();
        }

        try {
            grabber.setFrameNumber((int)targetFrame);
            currentFrame = targetFrame;

            // Capturar e exibir frame
            Frame frame = grabber.grabImage();
            if (frame != null && frame.image != null) {
                BufferedImage img = converter.convert(frame);
                if (img != null) {
                    videoPanel.updateImage(img);
                }
            }

            // Resetar posição
            grabber.setFrameNumber((int)targetFrame);

            // Atualizar UI
            updateTimeLabel();
            int progress = (int)((targetFrame * 100) / totalFrames);
            progressSlider.setValue(progress);

            // Atualizar legenda
            long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
            updateSubtitle(currentTimeMs);

            if (audioLine != null) {
                audioLine.flush();
            }

            System.out.println("Avançou para frame: " + targetFrame);

            if (wasPlaying) {
                Thread.sleep(100);
                playVideo();
            }

        } catch (Exception e) {
            System.err.println("Erro ao avançar: " + e.getMessage());
            e.printStackTrace();
        }
    }


private void nextFrame() {
    if (grabber == null || totalFrames == 0) return;

    System.out.println("Avançando " + framesToSkip + " frame(s)...");

    // Se estiver tocando, pausar primeiro
    if (isPlaying) {
        pauseVideo();
    }

    // Verificar se não está no final
    if (currentFrame >= totalFrames - 1) {
        System.out.println("Já está no último frame");
        return;
    }

    try {
        int framesAdvanced = 0;

        // Avançar a quantidade configurada de frames
        while (framesAdvanced < framesToSkip) {
            Frame frame = grabber.grab();

            if (frame == null) {
                System.out.println("Chegou ao fim do vídeo");
                break;
            }

            // Se for frame de vídeo, contar
            if (frame.image != null) {
                framesAdvanced++;

                // Só exibir o último frame
                if (framesAdvanced == framesToSkip) {
                    BufferedImage img = converter.convert(frame);
                    if (img != null) {
                        videoPanel.updateImage(img);
                        currentFrame++;

                        // Atualizar UI
                        updateTimeLabel();
                        int progress = (int)((currentFrame * 100) / totalFrames);
                        progressSlider.setValue(progress);

                        // Atualizar legenda
                        long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
                        updateSubtitle(currentTimeMs);

                        System.out.println("Frame atual: " + currentFrame + " (avançou " + framesToSkip + " frames)");
                    }
                } else {
                    // Contar frame mas não exibir
                    currentFrame++;
                }
            }
            // Frames de áudio são ignorados automaticamente
        }

    } catch (Exception e) {
        System.err.println("Erro ao avançar frame: " + e.getMessage());
        e.printStackTrace();

        // Fallback: tentar com setFrameNumber
        try {
            System.out.println("Tentando fallback com setFrameNumber...");
            long targetFrame = Math.min(totalFrames - 1, currentFrame + framesToSkip);
            grabber.setFrameNumber((int)targetFrame);
            currentFrame = targetFrame;

            Frame frame = grabber.grabImage();
            if (frame != null && frame.image != null) {
                BufferedImage img = converter.convert(frame);
                if (img != null) {
                    videoPanel.updateImage(img);
                    updateTimeLabel();
                    int progress = (int)((targetFrame * 100) / totalFrames);
                    progressSlider.setValue(progress);

                    long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
                    updateSubtitle(currentTimeMs);
                }
            }
            grabber.setFrameNumber((int)targetFrame);
        } catch (Exception fallbackError) {
            System.err.println("Fallback também falhou: " + fallbackError.getMessage());
        }
    }
}


    private void searchExternalSubtitles(String videoPath) {
        System.out.println("21. Iniciando busca de legendas externas...");
        File videoFile = new File(videoPath);
        String baseName = videoFile.getName().replaceFirst("[.][^.]+$", "");
        File parentDir = videoFile.getParentFile();

        String[] subtitleExtensions = {".srt", ".sub", ".ass", ".ssa", ".vtt"};

        for (String ext : subtitleExtensions) {
            File subFile = new File(parentDir, baseName + ext);
            if (subFile.exists()) {
                System.out.println("Legenda externa encontrada: " + subFile.getName());
                System.out.println("NÃO carregando automaticamente - usuário pode carregar pelo menu");
                // NÃO CARREGAR AUTOMATICAMENTE - só informar
                // loadSubtitleFile(subFile); // <-- REMOVIDO
                return;
            }
        }

        System.out.println("Nenhuma legenda externa encontrada");
    }

private void loadSubtitleFile(File file) {
    System.out.println("Carregando legenda: " + file.getName());

    new Thread(() -> {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            System.out.println("Arquivo lido: " + content.length() + " caracteres");

            // Detectar formato
            String filename = file.getName().toLowerCase();
            if (filename.endsWith(".srt")) {
                System.out.println("Parseando SRT...");
                parseSRT(content);
            } else if (filename.endsWith(".vtt")) {
                System.out.println("Parseando VTT...");
                parseVTT(content);
            } else if (filename.endsWith(".ass") || filename.endsWith(".ssa")) {
                System.out.println("Parseando ASS/SSA...");
                parseASS(content);
            } else {
                System.out.println("Formato de legenda não suportado: " + file.getName());
                return;
            }

            currentSubtitleStream = -2;
            System.out.println("Legenda parseada: " + subtitles.size() + " entradas");

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Legenda carregada com sucesso!\n" + subtitles.size() + " entradas encontradas",
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            });

        } catch (Exception e) {
            System.err.println("Erro ao carregar legenda: " + e.getMessage());
            e.printStackTrace();

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Erro ao carregar legenda: " + e.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
            });
        }
    }, "SubtitleLoader").start();
}
    private String cleanSubtitleText(String text) {
        if (text == null || text.isEmpty()) return text;

        // Remover tags HTML/XML (incluindo ASS tags)
        // Exemplos: <font face="sans-serif" size="71">texto</font>, <i>texto</i>, <b>texto</b>
        text = text.replaceAll("<[^>]+>", "");

        // Remover códigos de formatação ASS (entre chaves)
        // Exemplos: {\an8}, {\pos(960,30)}, {\fad(300,300)}
        text = text.replaceAll("\\{[^}]+\\}", "");

        // Remover comandos de override ASS
        // Exemplos: {\i1}, {\b1}, {}
        text = text.replaceAll("\\\\[a-zA-Z]+\\d*", "");

        // Remover espaços múltiplos
        text = text.replaceAll("\\s+", " ");

        // Remover espaços no início e fim
        text = text.trim();

        return text;
    }

    private void parseASS(String content) {
        System.out.println("Iniciando parse ASS/SSA...");
        subtitles.clear();

        try {
            content = content.replace("\r\n", "\n").replace("\r", "\n");
            String[] lines = content.split("\n");

            boolean inEventsSection = false;
            int textColumnIndex = -1;
            int startColumnIndex = -1;
            int endColumnIndex = -1;

            for (String line : lines) {
                line = line.trim();

                // Detectar início da seção [Events]
                if (line.equalsIgnoreCase("[Events]")) {
                    inEventsSection = true;
                    continue;
                }

                // Detectar outra seção (sair de Events)
                if (line.startsWith("[") && !line.equalsIgnoreCase("[Events]")) {
                    inEventsSection = false;
                    continue;
                }

                if (!inEventsSection) continue;

                // Parsear formato das colunas
                if (line.startsWith("Format:")) {
                    String formatLine = line.substring(7).trim();
                    String[] columns = formatLine.split(",");

                    for (int i = 0; i < columns.length; i++) {
                        String col = columns[i].trim().toLowerCase();
                        if (col.equals("text")) textColumnIndex = i;
                        else if (col.equals("start")) startColumnIndex = i;
                        else if (col.equals("end")) endColumnIndex = i;
                    }
                    continue;
                }

                // Parsear diálogos
                if (line.startsWith("Dialogue:") && textColumnIndex != -1) {
                    String dialogueLine = line.substring(9).trim();
                    String[] parts = dialogueLine.split(",", textColumnIndex + 1);

                    if (parts.length > textColumnIndex) {
                        try {
                            String startTime = parts[startColumnIndex].trim();
                            String endTime = parts[endColumnIndex].trim();
                            String text = parts[textColumnIndex].trim();

                            // Limpar formatação
                            text = cleanSubtitleText(text);

                            if (!text.isEmpty()) {
                                long start = parseASSTimestamp(startTime);
                                long end = parseASSTimestamp(endTime);

                                subtitles.add(new SubtitleEntry(start, end, text));
                            }
                        } catch (Exception e) {
                            System.err.println("Erro ao parsear linha ASS: " + e.getMessage());
                        }
                    }
                }
            }

            System.out.println("ASS parseado com sucesso: " + subtitles.size() + " entradas");

        } catch (Exception e) {
            System.err.println("Erro crítico no parse ASS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private long parseASSTimestamp(String timestamp) {
        try {
            // Formato ASS: H:MM:SS.cc (onde cc são centésimos de segundo)
            timestamp = timestamp.trim();

            String[] parts = timestamp.split("[:.]");

            if (parts.length >= 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                int centiseconds = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;

                // Converter centésimos para milissegundos
                int millis = centiseconds * 10;

                return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis;
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Erro ao parsear timestamp ASS '" + timestamp + "': " + e.getMessage());
            return 0;
        }
    }

    private void detectEmbeddedSubtitles(String videoPath) {
        try {
            // Executar ffprobe para obter informações dos streams
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_streams",
                    videoPath
            );

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();

            // Parse JSON simples (sem biblioteca externa)
            String json = output.toString();
            parseSubtitleStreamsFromJson(json);

            System.out.println("Total de faixas de legendas embutidas: " + totalSubtitleStreams);

        } catch (Exception e) {
            System.out.println("ffprobe não disponível, tentando método alternativo...");
            // Fallback: analisar output do console do FFmpeg
            detectSubtitlesFromConsoleOutput();
        }
    }

    private void parseSubtitleStreamsFromJson(String json) {
        // Parse simples sem biblioteca JSON
        int streamIndex = 0;
        int subtitleIndex = 0;

        // Procurar por streams de legendas
        Pattern streamPattern = Pattern.compile("\"codec_type\"\\s*:\\s*\"subtitle\"");

        Matcher streamMatcher = streamPattern.matcher(json);

        while (streamMatcher.find()) {
            int matchStart = streamMatcher.start();

            // Procurar para trás até achar o início deste stream (último '{' antes do match)
            int streamStart = json.lastIndexOf("{", matchStart);

            // Procurar o fim do objeto (considerar objetos aninhados)
            int streamEnd = findMatchingBrace(json, streamStart);

            if (streamStart != -1 && streamEnd > streamStart) {
                String streamData = json.substring(streamStart, streamEnd);

                // Extrair índice do stream
                Pattern indexPattern = Pattern.compile("\"index\"\\s*:\\s*(\\d+)");
                Matcher indexMatcher = indexPattern.matcher(streamData);
                if (indexMatcher.find()) {
                    streamIndex = Integer.parseInt(indexMatcher.group(1));
                }

                // Extrair idioma (dentro de tags)
                String lang = null;
                Pattern langPattern = Pattern.compile("\"(?:language|TAG:language)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher langMatcher = langPattern.matcher(streamData);
                if (langMatcher.find()) {
                    lang = langMatcher.group(1);
                }

                // Extrair título (dentro de tags)
                String title = null;
                Pattern titlePattern = Pattern.compile("\"(?:TAG:)?title\"\\s*:\\s*\"([^\"]+)\"");
                Matcher titleMatcher = titlePattern.matcher(streamData);
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1);
                }

                // Criar nome do stream
                String streamName;
                if (title != null && !title.isEmpty()) {
                    streamName = title;
                    if (lang != null && !lang.isEmpty()) {
                        streamName += " (" + lang + ")";
                    }
                } else if (lang != null && !lang.isEmpty()) {
                    streamName = "Subtitle " + (subtitleIndex + 1) + " (" + lang + ")";
                } else {
                    streamName = "Subtitle " + (subtitleIndex + 1);
                }

                subtitleStreamNames.put(subtitleIndex, streamName);

                System.out.println("Legenda embutida encontrada: Stream " + streamIndex + " - " + streamName);

                subtitleIndex++;
            }
        }

        totalSubtitleStreams = subtitleIndex;
    }

    private int findMatchingBrace(String json, int openBraceIndex) {
        int count = 1;
        for (int i = openBraceIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                count++;
            } else if (c == '}') {
                count--;
                if (count == 0) {
                    return i + 1;
                }
            }
        }
        return json.length();
    }

    private void detectSubtitlesFromConsoleOutput() {
        // Método alternativo: capturar output do FFmpeg que já está no console
        // Nota: Isso já está sendo impresso pelo JavaCV, vamos apenas contar
        totalSubtitleStreams = 0;

        // Como último recurso, assumir que não há legendas
        System.out.println("Não foi possível detectar legendas automaticamente");
    }

    private void loadExternalSubtitle() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Selecionar Arquivo de Legenda");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(java.io.File f) {
                String name = f.getName().toLowerCase();
                return f.isDirectory() || name.endsWith(".srt") || name.endsWith(".sub") ||
                        name.endsWith(".ass") || name.endsWith(".ssa") || name.endsWith(".vtt");
            }
            public String getDescription() {
                return "Arquivos de Legenda (*.srt, *.sub, *.ass, *.vtt)";
            }
        });

        if (videoFilePath != null) {
            fileChooser.setCurrentDirectory(new File(videoFilePath).getParentFile());
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadSubtitleFile(fileChooser.getSelectedFile());
        }
    }

private void parseSRT(String content) {
    System.out.println("Iniciando parse SRT...");
    subtitles.clear();

    try {
        // Normalizar quebras de linha
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        // Split por blocos (separados por linha vazia dupla)
        String[] blocks = content.split("\n\n+");

        System.out.println("Total de blocos encontrados: " + blocks.length);

        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            String[] lines = block.split("\n");
            if (lines.length < 3) continue; // Precisa ter: número, timestamp, texto

            try {
                // Linha 0: número (ignorar)
                // Linha 1: timestamp
                String timeLine = lines[1];

                if (!timeLine.contains("-->")) continue;

                String[] times = timeLine.split("-->");
                if (times.length != 2) continue;

                long startTime = parseTimestamp(times[0].trim());
                long endTime = parseTimestamp(times[1].trim());

                // Linhas 2+: texto da legenda
                StringBuilder text = new StringBuilder();
                for (int i = 2; i < lines.length; i++) {
                    if (text.length() > 0) text.append("\n");
                    text.append(lines[i]);
                }

                // IMPORTANTE: Limpar tags HTML/ASS do texto
                String cleanedText = cleanSubtitleText(text.toString());

                if (!cleanedText.isEmpty()) {
                    subtitles.add(new SubtitleEntry(startTime, endTime, cleanedText));
                }

            } catch (Exception e) {
                // Pular entrada inválida
                System.err.println("Erro ao parsear bloco: " + e.getMessage());
            }
        }

        System.out.println("SRT parseado com sucesso: " + subtitles.size() + " entradas");

    } catch (Exception e) {
        System.err.println("Erro crítico no parse SRT: " + e.getMessage());
        e.printStackTrace();
    }
}

    private void parseVTT(String content) {
        System.out.println("Iniciando parse VTT...");
        subtitles.clear();

        try {
            // Remover header WEBVTT
            content = content.replaceFirst("WEBVTT[^\n]*\n+", "");

            // Normalizar quebras de linha
            content = content.replace("\r\n", "\n").replace("\r", "\n");

            // Split por blocos
            String[] blocks = content.split("\n\n+");

            System.out.println("Total de blocos VTT encontrados: " + blocks.length);

            for (String block : blocks) {
                block = block.trim();
                if (block.isEmpty()) continue;

                String[] lines = block.split("\n");
                if (lines.length < 2) continue;

                try {
                    // Encontrar linha de timestamp (contém -->)
                    String timeLine = null;
                    int textStartIndex = 0;

                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].contains("-->")) {
                            timeLine = lines[i];
                            textStartIndex = i + 1;
                            break;
                        }
                    }

                    if (timeLine == null) continue;

                    String[] times = timeLine.split("-->");
                    if (times.length != 2) continue;

                    // VTT usa ponto ao invés de vírgula
                    String startStr = times[0].trim().replace('.', ',');
                    String endStr = times[1].trim().split(" ")[0].replace('.', ','); // Remover opções após o tempo

                    long startTime = parseTimestamp(startStr);
                    long endTime = parseTimestamp(endStr);

                    // Texto da legenda
                    StringBuilder text = new StringBuilder();
                    for (int i = textStartIndex; i < lines.length; i++) {
                        if (text.length() > 0) text.append("\n");
                        text.append(lines[i]);
                    }

                    // Limpar tags HTML/VTT
                    String cleanedText = cleanSubtitleText(text.toString());

                    if (!cleanedText.isEmpty()) {
                        subtitles.add(new SubtitleEntry(startTime, endTime, cleanedText));
                    }

                } catch (Exception e) {
                    System.err.println("Erro ao parsear bloco VTT: " + e.getMessage());
                }
            }

            System.out.println("VTT parseado com sucesso: " + subtitles.size() + " entradas");

        } catch (Exception e) {
            System.err.println("Erro crítico no parse VTT: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private long parseTimestamp(String timestamp) {
        try {
            // Formato: HH:MM:SS,mmm ou MM:SS,mmm
            timestamp = timestamp.trim();

            // Remover qualquer coisa após espaço (alguns arquivos têm posição, etc)
            if (timestamp.contains(" ")) {
                timestamp = timestamp.split(" ")[0];
            }

            String[] parts = timestamp.split("[:,]");

            if (parts.length == 4) {
                // HH:MM:SS,mmm
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                int millis = Integer.parseInt(parts[3]);

                return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis;
            } else if (parts.length == 3) {
                // MM:SS,mmm
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                int millis = Integer.parseInt(parts[2]);

                return (minutes * 60000L) + (seconds * 1000L) + millis;
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Erro ao parsear timestamp '" + timestamp + "': " + e.getMessage());
            return 0;
        }
    }

    private void updateSubtitle(long currentTimeMs) {
        if (currentSubtitleStream == -1 || subtitles.isEmpty()) {
            currentSubtitleText = "";
            return;
        }

        // Buscar legenda correspondente ao tempo atual
        for (SubtitleEntry entry : subtitles) {
            if (currentTimeMs >= entry.startTime && currentTimeMs <= entry.endTime) {
                if (!currentSubtitleText.equals(entry.text)) {
                    currentSubtitleText = entry.text;
                    videoPanel.repaint();
                }
                return;
            }
        }

        // Nenhuma legenda no tempo atual
        if (!currentSubtitleText.isEmpty()) {
            currentSubtitleText = "";
            videoPanel.repaint();
        }
    }

    // Modificar switchAudioStream
    private void switchAudioStream(int streamIndex) {
        if (streamIndex == currentAudioStream) {
            System.out.println("Já está na stream de áudio " + streamIndex);
            return;
        }

        if (videoFilePath == null) {
            System.out.println("Nenhum vídeo carregado");
            return;
        }

        System.out.println("Trocando para stream de áudio lógico: " + streamIndex);

        // Salvar TOTAL de streams ANTES de fechar
        int totalStreamsBeforeClose = totalAudioStreams;
        Map<Integer, String> audioNamesBeforeClose = new HashMap<>(audioStreamNames);
        Map<Integer, Integer> audioMappingBeforeClose = new HashMap<>(logicalToPhysicalAudioStream);

        // Salvar estado atual
        saveVideoState();

        // Garantir que o total de streams está salvo corretamente
        if (savedAudioStreamNames.isEmpty() && totalStreamsBeforeClose > 0) {
            for (int i = 0; i < totalStreamsBeforeClose; i++) {
                String name = audioNamesBeforeClose.getOrDefault(i, "Faixa de Áudio " + (i + 1));
                savedAudioStreamNames.put(i, name);
            }
            System.out.println("Forçado salvamento de " + totalStreamsBeforeClose + " streams de áudio");
        }

        // Fechar recursos atuais
        try {
            if (isPlaying) {
                isPlaying = false;
                if (playbackThread != null) {
                    playbackThread.interrupt();
                    playbackThread.join(500);
                }
            }

            if (audioLine != null && audioLine.isOpen()) {
                audioLine.close();
                audioLine = null;
            }

            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }

        } catch (Exception e) {
            System.err.println("Erro ao fechar recursos: " + e.getMessage());
        }

        // Definir nova stream de áudio
        currentAudioStream = streamIndex;

        // Recarregar vídeo com nova stream de áudio
        new Thread(() -> {
            try {
                Thread.sleep(100);

                SwingUtilities.invokeLater(() -> {
                    try {
                        grabber = new FFmpegFrameGrabber(videoFilePath);

                        // Converter índice lógico para físico
                        int physicalStreamIndex = audioMappingBeforeClose.getOrDefault(streamIndex, streamIndex);

                        // SEMPRE definir stream de áudio explicitamente usando índice físico
                        grabber.setAudioStream(physicalStreamIndex);
                        System.out.println("Stream de áudio definida: lógico=" + streamIndex + ", físico=" + physicalStreamIndex);

                        String extension = videoFilePath.substring(videoFilePath.lastIndexOf('.') + 1).toLowerCase();

                        // Por:
                        if (hardwareAccelerationEnabled) {
                            tryEnableHardwareAcceleration(grabber);
                        }

                        if (extension.equals("wmv")) {
                            try {
                                grabber.setOption("threads", "auto");
                                grabber.setOption("fflags", "nobuffer");
                                grabber.setOption("flags", "low_delay");
                            } catch (Exception e) {
                                System.out.println("Erro nas opções WMV: " + e.getMessage());
                            }
                        }

                        try {
                            grabber.setOption("analyzeduration", "2000000");
                            grabber.setOption("probesize", "2000000");
                            grabber.setOption("fflags", "+genpts");
                        } catch (Exception e) {
                            System.out.println("Erro nas opções gerais: " + e.getMessage());
                        }

                        grabber.start();
                        System.out.println("Grabber reiniciado com sucesso");

                        // Restaurar informações salvas
                        totalAudioStreams = totalStreamsBeforeClose;
                        logicalToPhysicalAudioStream = new HashMap<>(audioMappingBeforeClose);
                        System.out.println("Total de faixas de áudio (do backup): " + totalAudioStreams);
                        System.out.println("Mapeamento de áudio restaurado: " + logicalToPhysicalAudioStream);

                        if (!savedAudioStreamNames.isEmpty()) {
                            audioStreamNames = new HashMap<>(savedAudioStreamNames);
                            System.out.println("Nomes das streams de áudio restaurados: " + audioStreamNames.size());
                        }

                        if (!savedSubtitleStreamNames.isEmpty()) {
                            subtitleStreamNames = new HashMap<>(savedSubtitleStreamNames);
                            System.out.println("Nomes das streams de legendas restaurados: " + subtitleStreamNames.size());
                        }

                        totalFrames = grabber.getLengthInVideoFrames();
                        frameRate = grabber.getVideoFrameRate();

                        audioChannels = grabber.getAudioChannels();
                        sampleRate = grabber.getSampleRate();

                        System.out.println("Áudio detectado após start: Canais=" + audioChannels + ", SampleRate=" + sampleRate);

                        if (audioChannels > 0 && sampleRate > 0) {
                            int outputChannels = audioChannels > 2 ? 2 : audioChannels;

                            if (audioChannels > 2) {
                                System.out.println("Áudio " + audioChannels + " canais detectado, fazendo downmix para estéreo");
                            }

                            AudioFormat audioFormat = new AudioFormat(sampleRate, 16, outputChannels, true, true);
                            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                            audioLine = (SourceDataLine) AudioSystem.getLine(info);

                            int bufferSize = sampleRate * outputChannels * 2;
                            if (extension.equals("wmv")) bufferSize *= 4;

                            audioLine.open(audioFormat, bufferSize);
                            System.out.println("Novo áudio configurado: " + sampleRate + "Hz, " + audioChannels + " canais → " + outputChannels + " canais");
                            System.out.println("AudioLine criado e aberto com sucesso");
                        } else {
                            System.err.println("AVISO: Nenhum canal de áudio detectado! Canais=" + audioChannels + ", SampleRate=" + sampleRate);
                        }

                        restoreVideoStateAfterAudioSwitch();

                    } catch (Exception e) {
                        System.err.println("Erro ao trocar stream de áudio: " + e.getMessage());
                        e.printStackTrace();

                        JOptionPane.showMessageDialog(this,
                                "Erro ao trocar faixa de áudio.\n" + e.getMessage(),
                                "Erro", JOptionPane.ERROR_MESSAGE);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "AudioStreamSwitcher").start();
    }

    private void restoreVideoStateAfterAudioSwitch() {
        new Thread(() -> {
            try {
                // Aguardar estabilização
                Thread.sleep(200);

                System.out.println("Restaurando estado após troca de áudio...");

                // Restaurar legendas
                if (savedSubtitles != null && !savedSubtitles.isEmpty()) {
                    subtitles = new ArrayList<>(savedSubtitles);
                    currentSubtitleStream = savedSubtitleStream;
                    System.out.println("Legendas restauradas: " + subtitles.size() + " entradas");
                }

                // Garantir que totalSubtitleStreams está correto
                if (totalSubtitleStreams == 0 && !subtitleStreamNames.isEmpty()) {
                    totalSubtitleStreams = subtitleStreamNames.size();
                    System.out.println("Total de legendas embutidas corrigido: " + totalSubtitleStreams);
                }

                // Buscar posição salva
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (savedFramePosition > 0 && totalFrames > 0) {
                            // Ir para o frame salvo
                            grabber.setFrameNumber((int)savedFramePosition);
                            currentFrame = savedFramePosition;

                            // Capturar e exibir frame atual
                            Frame frame = grabber.grabImage();
                            if (frame != null && frame.image != null) {
                                BufferedImage img = converter.convert(frame);
                                if (img != null) {
                                    videoPanel.updateImage(img);
                                }
                            }

                            // Resetar para posição correta
                            grabber.setFrameNumber((int)savedFramePosition);
                            currentFrame = savedFramePosition;

                            // Atualizar UI
                            int progress = (int)((savedFramePosition * 100) / totalFrames);
                            progressSlider.setValue(progress);
                            updateTimeLabel();

                            // Atualizar legenda
                            long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
                            updateSubtitle(currentTimeMs);

                            System.out.println("Posição restaurada após troca de áudio: frame " + savedFramePosition);

                            // Retomar reprodução se estava tocando
                            if (savedPlayingState) {
                                Thread.sleep(300);
                                SwingUtilities.invokeLater(() -> {
                                    playVideo();
                                    System.out.println("Reprodução retomada com novo áudio");
                                });
                            }

                        } else if (savedPlayingState) {
                            playVideo();
                        }

                        // Mostrar mensagem de sucesso
                        String streamName = audioStreamNames.getOrDefault(currentAudioStream, "Faixa " + (currentAudioStream + 1));
                        JOptionPane.showMessageDialog(this,
                                "Faixa de áudio alterada:\n" + streamName,
                                "Áudio Alterado", JOptionPane.INFORMATION_MESSAGE);

                    } catch (Exception e) {
                        System.err.println("Erro ao restaurar posição após troca de áudio: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                System.err.println("Erro ao restaurar estado após troca de áudio: " + e.getMessage());
                e.printStackTrace();
            }
        }, "AudioStateRestorer").start();
    }


private void switchSubtitleStream(int streamIndex) {
    currentSubtitleStream = streamIndex;
    System.out.println("Trocando para legenda embutida stream: " + streamIndex);

    if (videoFilePath == null) {
        System.err.println("Caminho do vídeo não disponível");
        return;
    }

    // Extrair legenda embutida usando FFmpeg em thread separada
    new Thread(() -> {
        try {
            // Sempre extrair como SRT para normalizar formato
            File tempSubtitle = File.createTempFile("subtitle_", ".srt");
            tempSubtitle.deleteOnExit();

            System.out.println("Extraindo legenda para: " + tempSubtitle.getAbsolutePath());
            System.out.println("Comando: ffmpeg -i \"" + videoFilePath + "\" -map 0:s:" + streamIndex + " \"" + tempSubtitle.getAbsolutePath() + "\"");

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", videoFilePath,
                    "-map", "0:s:" + streamIndex,
                    "-c:s", "srt", // Converter para SRT
                    "-y",
                    tempSubtitle.getAbsolutePath()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean hasError = false;

            while ((line = reader.readLine()) != null) {
                System.out.println("FFmpeg: " + line);

                if (line.toLowerCase().contains("error") || line.toLowerCase().contains("invalid")) {
                    hasError = true;
                    System.err.println("ERRO: " + line);
                }
            }

            int exitCode = process.waitFor();

            System.out.println("FFmpeg terminou com código: " + exitCode);
            System.out.println("Arquivo existe: " + tempSubtitle.exists());
            System.out.println("Tamanho do arquivo: " + tempSubtitle.length() + " bytes");

            if (exitCode == 0 && tempSubtitle.exists() && tempSubtitle.length() > 0) {
                System.out.println("Legenda extraída com sucesso!");

                try (BufferedReader br = new BufferedReader(new FileReader(tempSubtitle))) {
                    System.out.println("=== Primeiras linhas da legenda ===");
                    for (int i = 0; i < 5 && br.ready(); i++) {
                        System.out.println(br.readLine());
                    }
                    System.out.println("=================================");
                }

                SwingUtilities.invokeLater(() -> {
                    loadSubtitleFile(tempSubtitle);
                    if (!subtitles.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "Legenda carregada com sucesso!\n" + subtitles.size() + " entradas",
                                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                    }
                });

            } else {
                throw new Exception("Falha ao extrair legenda (código: " + exitCode + ", tamanho: " + tempSubtitle.length() + ")");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Erro detalhado: " + e.getMessage());

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Não foi possível carregar a legenda embutida.\n" +
                                "Verifique o console para mais detalhes.\n\n" +
                                "Possíveis causas:\n" +
                                "- FFmpeg não está instalado ou não está no PATH\n" +
                                "- Stream de legenda incompatível\n" +
                                "- Formato de legenda não suportado\n\n" +
                                "Erro: " + e.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
            });
        }
    }).start();
}
    private void tryEnableHardwareAcceleration(FFmpegFrameGrabber grabber) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
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
        playPauseButton.setText("⏸");

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

                        totalDecodeTime += decodeTime;
                        totalRenderTime += renderTime;
                        statsCounter++;

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

                        // Atualizar legenda
                        long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
                        updateSubtitle(currentTimeMs);

                        long timeSinceLastUpdate = frameStartTime - lastUpdateTime;
                        if (frameCount > 10) {
                            avgFrameTime = (avgFrameTime * 0.9) + (timeSinceLastUpdate * 0.1);
                        }
                        lastUpdateTime = frameStartTime;

                        if (currentFrame % 5 == 0) {
                            SwingUtilities.invokeLater(() -> {
                                if (!isSeeking && totalFrames > 0) {
                                    int progress = (int)((currentFrame * 100) / totalFrames);
                                    progressSlider.setValue(progress);
                                    updateTimeLabel();
                                }
                            });
                        }

                        long expectedTime = startTime + (frameCount * frameDelay);
                        long currentTime = System.currentTimeMillis();
                        long sleepTime = expectedTime - currentTime;

                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        } else if (sleepTime < -frameDelay * 3) {
                            System.out.println("Sistema atrasado em " + (-sleepTime) + "ms, reajustando...");
                            startTime = currentTime - (long)(frameCount * frameDelay * 0.5);
                        }
                    }

                    // Processar frame de áudio
                    if (frame.samples != null && audioLine != null) {
                        try {
                            ShortBuffer channelSamples = (ShortBuffer) frame.samples[0];
                            channelSamples.rewind();

                            if (channelSamples.remaining() > 0) {
                                ByteBuffer outBuffer;

                                if (audioChannels > 2) {
                                    int totalSamples = channelSamples.remaining();
                                    int framesCount = totalSamples / audioChannels;
                                    outBuffer = ByteBuffer.allocate(framesCount * 2 * 2);

                                    for (int i = 0; i < framesCount; i++) {
                                        int baseIndex = i * audioChannels;
                                        float left = 0, right = 0;

                                        if (audioChannels == 6) {
                                            short fl = channelSamples.get(baseIndex + 0);
                                            short fr = channelSamples.get(baseIndex + 1);
                                            short center = channelSamples.get(baseIndex + 2);
                                            short lfe = channelSamples.get(baseIndex + 3);
                                            short rl = channelSamples.get(baseIndex + 4);
                                            short rr = channelSamples.get(baseIndex + 5);

                                            left = fl + (center * 0.707f) + (rl * 0.707f) + (lfe * 0.5f);
                                            right = fr + (center * 0.707f) + (rr * 0.707f) + (lfe * 0.5f);

                                            float maxVal = Math.max(Math.abs(left), Math.abs(right));
                                            if (maxVal > 32767) {
                                                float scale = 32767.0f / maxVal;
                                                left *= scale;
                                                right *= scale;
                                            }

                                        } else if (audioChannels == 8) {
                                            short fl = channelSamples.get(baseIndex + 0);
                                            short fr = channelSamples.get(baseIndex + 1);
                                            short center = channelSamples.get(baseIndex + 2);
                                            short lfe = channelSamples.get(baseIndex + 3);
                                            short rl = channelSamples.get(baseIndex + 4);
                                            short rr = channelSamples.get(baseIndex + 5);
                                            short sl = channelSamples.get(baseIndex + 6);
                                            short sr = channelSamples.get(baseIndex + 7);

                                            left = fl + (center * 0.707f) + (rl * 0.5f) + (sl * 0.5f) + (lfe * 0.5f);
                                            right = fr + (center * 0.707f) + (rr * 0.5f) + (sr * 0.5f) + (lfe * 0.5f);

                                            float maxVal = Math.max(Math.abs(left), Math.abs(right));
                                            if (maxVal > 32767) {
                                                float scale = 32767.0f / maxVal;
                                                left *= scale;
                                                right *= scale;
                                            }

                                        } else {
                                            int leftSum = 0, rightSum = 0;
                                            int leftCount = 0, rightCount = 0;

                                            for (int ch = 0; ch < audioChannels; ch++) {
                                                short sample = channelSamples.get(baseIndex + ch);
                                                if (ch % 2 == 0) {
                                                    leftSum += sample;
                                                    leftCount++;
                                                } else {
                                                    rightSum += sample;
                                                    rightCount++;
                                                }
                                            }

                                            left = (leftCount > 0 ? leftSum / leftCount : 0);
                                            right = (rightCount > 0 ? rightSum / rightCount : 0);
                                        }

                                        left *= volume;
                                        right *= volume;

                                        left = Math.max(-32768, Math.min(32767, left));
                                        right = Math.max(-32768, Math.min(32767, right));

                                        outBuffer.putShort((short)left);
                                        outBuffer.putShort((short)right);
                                    }
                                } else {
                                    outBuffer = ByteBuffer.allocate(channelSamples.remaining() * 2);

                                    while (channelSamples.hasRemaining()) {
                                        short val = channelSamples.get();
                                        val = (short)(val * volume);
                                        outBuffer.putShort(val);
                                    }
                                }

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
                                        attempts++;
                                        if (attempts < maxAttempts) {
                                            Thread.sleep(1);
                                        }
                                    }
                                }

                                if (written < bytesToWrite) {
                                    System.out.println("Buffer de áudio cheio, pulando samples");
                                }
                            }
                        } catch (Exception audioEx) {
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
        playPauseButton.setText("▶");

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
        currentSubtitleText = "";
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

                // Atualizar legenda para nova posição
                long currentTimeMs = (long)((currentFrame / frameRate) * 1000);
                updateSubtitle(currentTimeMs);
            }

            if (audioLine != null) {
                audioLine.flush();
            }

            updateTimeLabel();

            if (wasPlaying) {
                Thread.sleep(100);
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

        timeLabel.setText(totalTime);
        timeLabelPassed.setText(currentTime);
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
            UIManager.put( "Button.arc", 999 );
            VideoPlayer player = new VideoPlayer();
            player.setVisible(true);
        });
    }
}