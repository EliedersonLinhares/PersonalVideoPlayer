package com.esl.videoplayer;


import com.esl.videoplayer.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.capture.CaptureFrameManager;
import com.esl.videoplayer.filters.FiltersManager;
import com.esl.videoplayer.playlist.PlaylistDialog;
import com.esl.videoplayer.playlist.PlaylistItem;
import com.esl.videoplayer.playlist.PlaylistManager;
import com.esl.videoplayer.subtitle.SubtitleEntry;
import com.esl.videoplayer.subtitle.SubtitleManager;
import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;
import jnafilechooser.api.JnaFileChooser;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1;


public class VideoPlayer extends JFrame {
    private PlaylistManager playlistManager;
    private PlaylistDialog playlistDialog;
    private SubtitleManager subtitleManager;
    private AudioSpectrumPanel audioSpectrumPanel;

    public VideoPanel videoPanel;
    private JButton playPauseButton;
    private JButton stopButton;
    private JSlider progressSlider;
    private JSlider volumeSlider;
    private JLabel timeLabel;
    private JLabel timeLabelPassed;
    private JLabel volumeLabel;
    private JButton openButton;
    private JButton volumeButton;
    private JButton loadPlaylistButton;

    public FFmpegFrameGrabber grabber;
    public Java2DFrameConverter converter;
    private Thread playbackThread;
    public volatile boolean isPlaying = false;
    private volatile boolean isStopped = true;
    private volatile boolean isSeeking = false;

    private SourceDataLine audioLine;
    private int audioChannels;
    private int sampleRate;
    private float volume = 1.0f;
    private float previousVolume = 1.0f; // Guardar volume anterior ao mutar
    private boolean isMuted = false;

    private int currentAudioStream = 0;
    private int totalAudioStreams = 0;

    private long totalFrames;
    private double frameRate;
    private long currentFrame = 0;

    private Map<Integer, String> audioStreamNames = new HashMap<>();
    public String videoFilePath;
    // Modificar as variáveis de instância
//    private int baseSubtitleFontSize = 24; // Tamanho base configurado pelo usuário
//    private Color subtitleColor = Color.WHITE;

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
    public boolean hardwareAccelerationEnabled = false;

    // No início da classe, junto com outras variáveis:
    private JButton rewindButton;
    private JButton forwardButton;
    private JButton nextFrameButton;
    private JButton captureFrameButton;
    private JButton captureAllFrameButton;

    // No início da classe, junto com outras variáveis:
    private int framesToSkip = 1; // Quantidade de frames para avançar

    // Adicionar no início da classe, junto com outras variáveis de instância
    private boolean silentCapture = false;
    private String customCapturePath = null;
    private String batchCapturePath = null;
    private int batchCaptureInterval = 2; // Capturar a cada N frames
    private Thread batchCaptureThread = null;
    private volatile boolean batchCapturePaused = false;
    private volatile boolean batchCaptureCancelled = false;

    String ffmpegPath = new File("lib/ffmpeg/bin/ffmpeg.exe").getAbsolutePath();
    String ffprobePath = new File("lib/ffmpeg/bin/ffprobe.exe").getAbsolutePath();

    // Filtros de vídeo
//    public double brightness = 0.0;  // -1.0 a 1.0
    public double contrast = 1.0;    // 0.0 a 2.0
    public double gamma = 1.0;       // 0.1 a 10.0
    public double saturation = 1.0;  // 0.0 a 3.0
    //public boolean filtersEnabled = false;

    // Adicionar variável de instância para controlar se é áudio ou vídeo
    private boolean isAudioOnly = false;

    // Variáveis para spectrum analyzer
    private float[] spectrumData = new float[64]; // 64 barras de frequência
    private final Object spectrumLock = new Object();

    // Variaveis para o cover art
    private BufferedImage audioCoverArt = null;

    //Variaveis para exiber a resoluçao atual da tela
    private int screenWidth;
    private int screenHeight;

    // Variaveis para uso no sistema de normalização de volume em audios
    private boolean audioNormalizationEnabled = false; // DESATIVADO por padrão
    private float targetLoudness = -18.0f; // MAIS BAIXO: -18 LUFS (era -14)
    private float normalizationGain = 1.0f; // Ganho calculado para normalização
    private float maxPeakLevel = 0.0f; // Pico máximo detectado
    private float averageRMS = 0.0f; // RMS médio
    private int normalizationSampleCount = 0;
    private final int NORMALIZATION_ANALYSIS_SAMPLES = 100; // Amostras antes de aplicar
    private boolean normalizationCalculated = false;

    // Buffer circular para análise contínua
    private float[] rmsHistory = new float[50];
    private int rmsHistoryIndex = 0;

    // NOVO: Ganho global adicional para controle fino
    private float globalAudioGain = 0.2f; // 50% do volume (ajustável de 0.1 a 1.0)

    private AudioLoudnessAnalyzer loudnessAnalyzer = new AudioLoudnessAnalyzer();

    // Variáveis de instância
    private boolean autoPlayNext = true; // Tocar próxima automaticamente

    private CaptureFrameManager captureFrameManager;
    private FiltersManager filtersManager;

    public VideoPlayer() {
        setTitle("Video Player - JavaCV");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(600, 300);
        setLocationRelativeTo(null);

        // CRIAR PLAYLIST MANAGER E DIALOG ANTES DE initComponents
        playlistManager = new PlaylistManager();
        // Criar o PlaylistDialog com callback
        playlistDialog = new PlaylistDialog(this, playlistManager, new PlaylistDialog.PlaylistCallback() {
            @Override
            public void onPlayTrack(String filePath) {
                playFromPlaylist(filePath);
            }

            @Override
            public void onAutoPlayRequested() {
                // Equivalente ao código: videoPanel.autoPlayItem.isSelected()
                if (videoPanel != null && videoPanel.autoPlayItem != null) {
                    if (videoPanel.autoPlayItem.isSelected()) {
                        System.out.println("====Reativando o autoplay");
                        autoPlayNext = true;
                    }
                }
            }
        });

        captureFrameManager = new CaptureFrameManager();
        filtersManager = new FiltersManager(VideoPlayer.this);
        subtitleManager = new SubtitleManager();
        audioSpectrumPanel = new AudioSpectrumPanel();

        // Get the default toolkit
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        // Get the screen dimensions
        Dimension screenSize = toolkit.getScreenSize();

        // Extract width and height
        screenWidth = screenSize.width;
        screenHeight = screenSize.height;

        // Print the screen resolution
        System.out.println("Screen Resolution: " + screenWidth + "x" + screenHeight);

        converter = new Java2DFrameConverter();
        initComponents();

        // NOVO: Adicionar KeyEventDispatcher global para capturar teclas em qualquer lugar
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                // Só processar quando a tecla for pressionada (não released)
                if (e.getID() != KeyEvent.KEY_PRESSED) {
                    return false;
                }

                // Não processar se estiver digitando em um campo de texto
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focusOwner instanceof JTextField || focusOwner instanceof JTextArea) {
                    return false;
                }

                // Processar atalhos globais
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                        if (gd.getFullScreenWindow() == VideoPlayer.this) {
                            exitFullScreen();
                            return true; // Consumir evento
                        }
                        break;

                    case KeyEvent.VK_F11:
                        toggleFullScreen();
                        return true;

                    case KeyEvent.VK_SPACE:
                        if (grabber != null) {
                            togglePlayPause();
                            return true;
                        }
                        break;

                    case KeyEvent.VK_LEFT:
                        if (grabber != null) {
                            rewind10Seconds();
                            return true;
                        }
                        break;

                    case KeyEvent.VK_RIGHT:
                        if (grabber != null) {
                            forward10Seconds();
                            return true;
                        }
                        break;

                    case KeyEvent.VK_X:
                        if (grabber != null) {
                            nextFrame();
                            return true;
                        }
                        break;

                    case KeyEvent.VK_S:
                        if (grabber != null) {
                            stopVideo();
                            return true;
                        }
                        break;
                    case KeyEvent.VK_C:
                        if (grabber != null) {
                            captureFrameManager.captureFrame(grabber, VideoPlayer.this, videoPanel,
                                    customCapturePath, videoFilePath,currentFrame, silentCapture);
                            return true;
                        }
                        break;
                    case KeyEvent.VK_V:
                        if (grabber != null) {
                            captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this,batchCaptureThread,isPlaying,
                            totalFrames,batchCaptureInterval,frameRate, batchCapturePath, videoFilePath);
                            return true;
                        }
                        break;
                }
                return false; // Não consumir o evento se não for um dos nossos atalhos
            }
        });

        // Adicionar listener de teclado no painel de vídeo (mantido como backup)
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
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    rewind10Seconds();
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    forward10Seconds();
                } else if (e.getKeyCode() == KeyEvent.VK_X) {
                    nextFrame();
                } else if (e.getKeyCode() == KeyEvent.VK_S) {
                    stopVideo();
                } else if (e.getKeyCode() == KeyEvent.VK_C) {
                    captureFrameManager.captureFrame(grabber, VideoPlayer.this, videoPanel,
                            customCapturePath, videoFilePath,currentFrame, silentCapture);
                } else if (e.getKeyCode() == KeyEvent.VK_V) {
                    captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this,batchCaptureThread,isPlaying,
                            totalFrames,batchCaptureInterval,frameRate, batchCapturePath, videoFilePath);
                }
            }
        });

        // Click esquerdo para pausar/continuar
        videoPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (grabber != null && (isPlaying || !isStopped)) {
                        togglePlayPause();
                    }
                }
                videoPanel.requestFocusInWindow();
            }
        });
    }

    // Método para abrir dialog de playlist
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
    public void loadAndPlayPlaylist() {
        JnaFileChooser fc = new JnaFileChooser();
        fc.addFilter("M3U Playlist", "m3u");
        if(videoPanel.autoPlayItem.isSelected()){
            System.out.println("====Reativando o autoplay");
            autoPlayNext = true;
        }
        // Atualiza o dialog SEMPRE (mesmo invisível)
        SwingUtilities.invokeLater(() -> {
            playlistDialog.refreshPlaylist();
        });

        if (fc.showOpenDialog(this)) {
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
                        playFromPlaylist(firstItem.getFilePath());

                        // Atualizar dialog se estiver aberto
                        if (playlistDialog != null && playlistDialog.isVisible()) {
                            playlistDialog.refreshPlaylist();
                        }
                    }

                    JOptionPane.showMessageDialog(this,
                            "Playlist carregada: " + playlistManager.size() + " música(s)\n" +
                                    "Reproduzindo: " + firstItem.getDisplayName(),
                            "Playlist Carregada",
                            JOptionPane.INFORMATION_MESSAGE);

                    playlistDialog.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "A playlist está vazia!",
                            "Aviso",
                            JOptionPane.WARNING_MESSAGE);
                }

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Erro ao carregar playlist:\n" + e.getMessage(),
                        "Erro",
                        JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    // Método para tocar do playlist (NÃO limpa playlist)
    public void playFromPlaylist(String filePath) {
        // IMPORTANTE: Não chamar clearPlaylistAndCloseDialog() aqui
        // Este método é usado pela playlist, então deve manter ela ativa

        if (filePath.toLowerCase().endsWith(".mp3") ||
                filePath.toLowerCase().endsWith(".wav") ||
                filePath.toLowerCase().endsWith(".flac") ||
                filePath.toLowerCase().endsWith(".ogg") ||
                filePath.toLowerCase().endsWith(".m4a") ||
                filePath.toLowerCase().endsWith(".aac")) {
            loadAudioFromPlaylist(filePath); // Usar método especial
        } else {
            loadVideoFromPlaylist(filePath); // Usar método especial
        }
    }

    // No VideoPlayer, adicionar método público:
    public void clearPlaylistAndCloseDialog() {
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
        autoPlayNext = false;
    }

    public void toggleFullScreen() {
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
            savedSubtitles = new ArrayList<>( subtitleManager.getSubtitles());
            savedSubtitleText =  subtitleManager.getCurrentSubtitleText();
            savedSubtitleStream = subtitleManager.getCurrentSubtitleStream();

            // Salvar stream de áudio e SEMPRE salvar nomes e total
            savedAudioStream = currentAudioStream;
            savedAudioStreamNames = new HashMap<>(audioStreamNames);
            savedSubtitleStreamNames = new HashMap<>( subtitleManager.getSubtitleStreamNames());

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
                    subtitleManager.setSubtitles(new ArrayList<>(savedSubtitles));
                   subtitleManager.setCurrentSubtitleStream(savedSubtitleStream);
                    System.out.println("Legendas restauradas: " + subtitleManager.getSubtitles().size() + " entradas");
                }

                // Restaurar nomes das streams
                if (!savedAudioStreamNames.isEmpty()) {
                    audioStreamNames = new HashMap<>(savedAudioStreamNames);
                    System.out.println("Nomes de streams de áudio restaurados: " + audioStreamNames.size());
                }

//                if (!savedSubtitleStreamNames.isEmpty()) {
//                    subtitleStreamNames = new HashMap<>(savedSubtitleStreamNames);
//                    totalSubtitleStreams = subtitleStreamNames.size();
//                    System.out.println("Nomes de streams de legendas restaurados: " + subtitleStreamNames.size());
//                    System.out.println("totalSubtitleStreams definido: " + totalSubtitleStreams);
//                }
                if (!savedSubtitleStreamNames.isEmpty()) {
                   subtitleManager.setSubtitleStreamNames( new HashMap<>(savedSubtitleStreamNames));
                   subtitleManager.setTotalSubtitleStreams(subtitleManager.getSubtitleStreamNames().size());
                    System.out.println("Nomes de streams de legendas restaurados: " + subtitleManager.getSubtitleStreamNames().size());
                    System.out.println("totalSubtitleStreams definido: " + subtitleManager.getTotalSubtitleStreams());
                }

                // Restaurar stream de áudio
                currentAudioStream = savedAudioStream;
                System.out.println("Stream de áudio restaurada: " + savedAudioStream);

                // Buscar posição salva
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (savedFramePosition > 0 && totalFrames > 0) {
                            // Ir para o frame salvo
                            grabber.setFrameNumber((int) savedFramePosition);
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
                            grabber.setFrameNumber((int) savedFramePosition);
                            currentFrame = savedFramePosition;

                            // Atualizar slider de progresso
                            int progress = (int) ((savedFramePosition * 100) / totalFrames);
                            progressSlider.setValue(progress);

                            // Atualizar label de tempo
                            updateTimeLabel();

                            // Atualizar legenda para o tempo atual
                            long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                            subtitleManager.updateSubtitle(currentTimeMs, videoPanel);

                            System.out.println("Posição restaurada: frame " + savedFramePosition);
                            System.out.println("Slider atualizado para: " + progress + "%");
                            System.out.println("Estado completamente restaurado:");
                            System.out.println("  - totalAudioStreams: " + totalAudioStreams);
                            System.out.println("  - currentAudioStream: " + currentAudioStream);
                            System.out.println("  - audioStreamNames size: " + audioStreamNames.size());
                            System.out.println("  - totalSubtitleStreams: " + subtitleManager.getTotalSubtitleStreams());
                            System.out.println("  - subtitleStreamNames size: " + subtitleManager.getSubtitleStreamNames().size());

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
        Map<Integer, String> savedSubtitleNames = new HashMap<>( subtitleManager.getSubtitleStreamNames());
        int savedTotalSubtitleStreams = subtitleManager.getTotalSubtitleStreams();
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
        subtitleManager.getSubtitleStreamNames().clear();
        subtitleManager.setCurrentSubtitleText("");
        subtitleManager.setCurrentSubtitleStream(-1);
        currentFrame = 0;

        // Desabilitar controles durante carregamento
        playPauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        progressSlider.setEnabled(false);
        openButton.setEnabled(false);
        volumeButton.setEnabled(false);

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
                        // grabber.setOption("threads", "auto");
                        grabber.setOption("fflags", "nobuffer");
                        // grabber.setOption("flags", "low_delay");
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
                subtitleManager.setSubtitleStreamNames(new HashMap<>(savedSubtitleNames));
                subtitleManager.setTotalSubtitleStreams(savedTotalSubtitleStreams);

                System.out.println("Total de faixas de áudio (restaurado): " + totalAudioStreams);
                System.out.println("Stream de áudio atual: " + currentAudioStream);
                System.out.println("Nomes de áudio restaurados: " + audioStreamNames.size());
                System.out.println("Nomes de legendas restaurados: " + subtitleManager.getSubtitleStreamNames().size());
                System.out.println("Total de legendas: " + subtitleManager.getTotalSubtitleStreams());

                // Só detectar legendas se ainda não temos informação
                if ( subtitleManager.getSubtitleStreamNames().isEmpty()) {
                    new Thread(() -> {
                        try {
                          subtitleManager.detectEmbeddedSubtitles(filepath,ffprobePath);
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
                subtitleManager.searchExternalSubtitles(filepath);
                System.out.println("Busca de legendas concluída");

                System.out.println("Vídeo carregado! Habilitando UI...");

                SwingUtilities.invokeLater(() -> {
                    playPauseButton.setEnabled(true);
                    stopButton.setEnabled(true);
                    progressSlider.setEnabled(true);
                    progressSlider.setValue(0);
                    openButton.setEnabled(true);
                    volumeButton.setEnabled(true);

                    updateTimeLabel();

                    setTitle("Video Player - " + new File(filepath).getName());

                    System.out.println("UI HABILITADA - Pronto para reproduzir!");
                    System.out.println("Estado final:");
                    System.out.println("  totalAudioStreams: " + totalAudioStreams);
                    System.out.println("  currentAudioStream: " + currentAudioStream);
                    System.out.println("  audioStreamNames: " + audioStreamNames);
                    System.out.println("  logicalToPhysicalAudioStream: " + logicalToPhysicalAudioStream);
                    System.out.println("  totalSubtitleStreams: " + subtitleManager.getTotalSubtitleStreams());
                    System.out.println("  subtitleStreamNames: " + subtitleManager.getSubtitleStreamNames());
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
                    volumeButton.setEnabled(false);
                    setTitle("Video Player - JavaCV");
                });
            }
        }, "VideoLoader").start();
    }


    // Modificar o método detectAudioStreamNames
    private void detectAudioStreamNames(String filepath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath,
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
            int streamEnd = subtitleManager.findMatchingBrace(json, streamStart);

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

    // Painel customizado com double buffering e legendas
    public class VideoPanel extends JPanel {
        private BufferedImage currentImage;
        private AudioSpectrumPanel spectrumPanel;
        private BufferedImage coverArt; // NOVO: Cover art do áudio
        JCheckBoxMenuItem autoPlayItem;

        public BufferedImage getCurrentImage() {
            return currentImage;
        }
        public VideoPanel() {

            setBackground(Color.BLACK);
            setDoubleBuffered(true);

//            setLayout(new BorderLayout());

            // NÃO usar BorderLayout aqui, usar null layout para controle manual
            setLayout(null);

            // Criar spectrum panel (inicialmente invisível)
            spectrumPanel = new AudioSpectrumPanel();
            spectrumPanel.setVisible(false);

            add(spectrumPanel, BorderLayout.CENTER);

            if (grabber == null) {
                setupContextMenu();
            }

            // Listener para redimensionar spectrum quando o VideoPanel mudar
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (spectrumPanel.isVisible()) {
                        centerSpectrumPanel();
                    }
                }
            });
        }

// ==================== MÉTODOS PÚBLICOS NA VideoPanel ====================
        public void setSpectrumColorMode(AudioSpectrumPanel.ColorMode mode) {
            if (spectrumPanel != null) {
                spectrumPanel.setColorMode(mode);
            }
        }

        public void setSpectrumCustomColors(Color bottom, Color middle, Color top) {
            if (spectrumPanel != null) {
                spectrumPanel.setCustomColors(bottom, middle, top);
            }
        }

        public void setSpectrumVisible(boolean visible) {
            if (spectrumPanel != null) {
                spectrumPanel.setSpectrumVisible(visible);
            }
        }

        // NOVO: Método para centralizar o spectrum panel
        private void centerSpectrumPanel() {
            int panelWidth = spectrumPanel.getPanelWidth();
            int panelHeight = spectrumPanel.getPanelHeight();
            int x = (getWidth() - panelWidth) / 2;
            int y = (getHeight() - panelHeight) / 2;
            spectrumPanel.setBounds(x, y, panelWidth, panelHeight);
        }

        // NOVO: Método para mostrar spectrum analyzer
        public void showSpectrum() {
            this.currentImage = null;
            if (spectrumPanel != null) {
                spectrumPanel.setVisible(true);
                centerSpectrumPanel(); // Centralizar ao mostrar
            }
            repaint();
        }

        // NOVO: Método para atualizar dados do spectrum
        public void updateSpectrum(float[] spectrum) {
            if (spectrumPanel != null) {
                spectrumPanel.updateSpectrum(spectrum);
            }
        }

        // NOVO: Método público para alterar tamanho do spectrum
        public void setSpectrumSize(int width, int height) {
            if (spectrumPanel != null) {
                spectrumPanel.setPanelSize(width, height);
                if (spectrumPanel.isVisible()) {
                    centerSpectrumPanel();
                }
            }
        }

        // Na classe VideoPanel, adicionar:
        public void setSpectrumReflection(boolean show) {
            if (spectrumPanel != null) {
                spectrumPanel.setShowReflection(show);
            }
        }

        public void setSpectrumReflectionHeight(float height) {
            if (spectrumPanel != null) {
                spectrumPanel.setReflectionHeight(height);
            }
        }

        public void setSpectrumReflectionAlpha(int alpha) {
            if (spectrumPanel != null) {
                spectrumPanel.setReflectionAlpha(alpha);
            }
        }

        private void setupContextMenu() {
            JPopupMenu contextMenu = new JPopupMenu();
            // === Submenu: Playlist ===
            JMenu playlistMenu = new JMenu("Playlist");
            playlistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            JMenuItem openPlaylistItem = new JMenuItem("📋 Nova Playlist...");
            openPlaylistItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
            openPlaylistItem.addActionListener(e -> showPlaylistDialog());

            autoPlayItem = new JCheckBoxMenuItem("Auto-play Próxima", true);
            autoPlayItem.addActionListener(e -> {
                autoPlayNext = autoPlayItem.isSelected();
            });

            playlistMenu.add(openPlaylistItem);
            playlistMenu.addSeparator();
            playlistMenu.add(autoPlayItem);

            contextMenu.add(playlistMenu);

            // Atualizar estado ao abrir menu
            contextMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    if (grabber == null) {
                        SwingUtilities.invokeLater(() -> contextMenu.setVisible(true));
                    }
                }

                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                }

                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                }
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

        private void setupAudioContextMenu() {
            JPopupMenu contextMenu = new JPopupMenu();

            JMenu spectrumMenu = new JMenu("Equalizador de áudio Animado");
            spectrumMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            // === Submenu: Modo de Cor ===
            JMenu colorModeMenu = new JMenu("Modo de Cor");

            ButtonGroup colorGroup1 = new ButtonGroup();

            JRadioButtonMenuItem defaultColorItem = new JRadioButtonMenuItem("Padrão (Verde-Amarelo-Vermelho)");
            defaultColorItem.addActionListener(e -> {
                videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.DEFAULT);
            });

            JRadioButtonMenuItem coverColorItem = new JRadioButtonMenuItem("Baseado na Capa", true);
            coverColorItem.addActionListener(e -> {
                videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.COVER_PALETTE);
            });

            JRadioButtonMenuItem customColorItem = new JRadioButtonMenuItem("Personalizado...");
            customColorItem.addActionListener(e -> {
                showCustomColorDialog();
            });

            colorGroup1.add(defaultColorItem);
            colorGroup1.add(coverColorItem);
            colorGroup1.add(customColorItem);

            colorModeMenu.add(defaultColorItem);
            colorModeMenu.add(coverColorItem);
            colorModeMenu.add(customColorItem);
            colorModeMenu.addSeparator();

            // Paletas pré-definidas
            JMenuItem synthwaveItem = new JMenuItem("Paleta: Synthwave");
            synthwaveItem.addActionListener(e -> {
                videoPanel.setSpectrumCustomColors(
                        new Color(100, 0, 200),
                        new Color(200, 0, 255),
                        new Color(255, 100, 255)
                );
                videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
                customColorItem.setSelected(true);
            });

            JMenuItem matrixItem = new JMenuItem("Paleta: Matrix");
            matrixItem.addActionListener(e -> {
                videoPanel.setSpectrumCustomColors(
                        new Color(0, 255, 0),
                        new Color(0, 255, 128),
                        new Color(128, 255, 255)
                );
                videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
                customColorItem.setSelected(true);
            });

            JMenuItem fireItem = new JMenuItem("Paleta: Fogo");
            fireItem.addActionListener(e -> {
                videoPanel.setSpectrumCustomColors(
                        new Color(255, 100, 0),
                        new Color(255, 200, 0),
                        new Color(255, 255, 100)
                );
                videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
                customColorItem.setSelected(true);
            });

            JMenuItem iceItem = new JMenuItem("Paleta: Gelo");
            iceItem.addActionListener(e -> {
                videoPanel.setSpectrumCustomColors(
                        new Color(0, 100, 200),
                        new Color(0, 200, 255),
                        new Color(200, 255, 255)
                );
                videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
                customColorItem.setSelected(true);
            });

            colorModeMenu.add(synthwaveItem);
            colorModeMenu.add(matrixItem);
            colorModeMenu.add(fireItem);
            colorModeMenu.add(iceItem);

            // === Checkboxes de Visibilidade ===
            spectrumMenu.add(colorModeMenu);
            spectrumMenu.addSeparator();

            JCheckBoxMenuItem showSpectrumItem = new JCheckBoxMenuItem("Mostrar Equalizador", true);
            showSpectrumItem.addActionListener(e -> {
                videoPanel.setSpectrumVisible(showSpectrumItem.isSelected());
            });

            JCheckBoxMenuItem showCoverItem = new JCheckBoxMenuItem("Mostrar Capa", true);
            showCoverItem.addActionListener(e -> {
                videoPanel.setCoverArtVisible(showCoverItem.isSelected());
            });

            spectrumMenu.add(showSpectrumItem);
            spectrumMenu.add(showCoverItem);

            spectrumMenu.addSeparator();

            // === Submenu: Reflexo ===
            JMenu reflectionMenu = new JMenu("Reflexo");

            JCheckBoxMenuItem enableReflectionItem = new JCheckBoxMenuItem("Ativar Reflexo", true);
            enableReflectionItem.addActionListener(e -> {
                videoPanel.setSpectrumReflection(enableReflectionItem.isSelected());
            });

            JMenu reflectionHeightMenu = new JMenu("Altura do Reflexo");
            String[] heightLabels = {"25%", "50%", "75%", "100%"};
            float[] heightValues = {0.25f, 0.5f, 0.75f, 1.0f};

            ButtonGroup heightGroup = new ButtonGroup();
            for (int i = 0; i < heightLabels.length; i++) {
                final float height = heightValues[i];
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(heightLabels[i], i == 1); // 50% selecionado
                item.addActionListener(e -> {
                    videoPanel.setSpectrumReflectionHeight(height);
                });
                heightGroup.add(item);
                reflectionHeightMenu.add(item);
            }

            JMenu reflectionOpacityMenu = new JMenu("Opacidade do Reflexo");
            String[] opacityLabels = {"25%", "50%", "75%", "100%"};
            int[] opacityValues = {64, 128, 192, 255};

            ButtonGroup opacityGroup = new ButtonGroup();
            for (int i = 0; i < opacityLabels.length; i++) {
                final int opacity = opacityValues[i];
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(opacityLabels[i], i == 1); // 50% selecionado
                item.addActionListener(e -> {
                    videoPanel.setSpectrumReflectionAlpha(opacity);
                });
                opacityGroup.add(item);
                reflectionOpacityMenu.add(item);
            }

            reflectionMenu.add(enableReflectionItem);
            reflectionMenu.add(reflectionHeightMenu);
            reflectionMenu.add(reflectionOpacityMenu);

            spectrumMenu.add(reflectionMenu);


            contextMenu.add(spectrumMenu);
            // ========== FIM: Menu de Modos de cor do Spectrum ==========


            JMenu audioMenu2 = new JMenu("Áudio");
            audioMenu2.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            // === Normalização ===
            JCheckBoxMenuItem enableNormalizationItem = new JCheckBoxMenuItem("Ativar Normalização", false);
            enableNormalizationItem.addActionListener(e -> {
                setAudioNormalizationEnabled(enableNormalizationItem.isSelected());
            });

            audioMenu2.add(enableNormalizationItem);
            audioMenu2.addSeparator();

            // === Submenu: Presets de Loudness ===
            JMenu loudnessMenu = new JMenu("Intensidade Sonora");

            ButtonGroup loudnessGroup = new ButtonGroup();

            JRadioButtonMenuItem streamingItem = new JRadioButtonMenuItem("Streaming (-14 LUFS)");
            streamingItem.setToolTipText("Spotify, YouTube, Apple Music");
            streamingItem.addActionListener(e -> {
                setTargetLoudness(-14.0f);
            });

            JRadioButtonMenuItem quietItem = new JRadioButtonMenuItem("Quiet (-18 LUFS)", true);
            quietItem.setToolTipText("Música ambiente, uso casual");
            quietItem.addActionListener(e -> {
                setTargetLoudness(-18.0f);
            });

            JRadioButtonMenuItem broadcastItem = new JRadioButtonMenuItem("Broadcast (-23 LUFS)");
            broadcastItem.setToolTipText("TV, Rádio");
            broadcastItem.addActionListener(e -> {
                setTargetLoudness(-23.0f);
            });

            JRadioButtonMenuItem cinemaItem = new JRadioButtonMenuItem("Cinema (-24 LUFS)");
            cinemaItem.setToolTipText("Padrão de cinema");
            cinemaItem.addActionListener(e -> {
                setTargetLoudness(-24.0f);
            });

            JRadioButtonMenuItem loudItem = new JRadioButtonMenuItem("Loud (-11 LUFS)");
            loudItem.setToolTipText("Festas, clubs");
            loudItem.addActionListener(e -> {
                setTargetLoudness(-11.0f);
            });

            JRadioButtonMenuItem customLoudnessItem = new JRadioButtonMenuItem("Personalizado...");
            customLoudnessItem.addActionListener(e -> {
                showCustomLoudnessDialog();
            });

            loudnessGroup.add(streamingItem);
            loudnessGroup.add(quietItem);
            loudnessGroup.add(broadcastItem);
            loudnessGroup.add(cinemaItem);
            loudnessGroup.add(loudItem);
            loudnessGroup.add(customLoudnessItem);

            loudnessMenu.add(streamingItem);
            loudnessMenu.add(quietItem);
            loudnessMenu.add(broadcastItem);
            loudnessMenu.add(cinemaItem);
            loudnessMenu.add(loudItem);
            loudnessMenu.addSeparator();
            loudnessMenu.add(customLoudnessItem);

            audioMenu2.add(loudnessMenu);
            audioMenu2.addSeparator();

            // === Submenu: Volume Global ===
            JMenu volumeGainMenu = new JMenu("Volume Global");

            ButtonGroup volumeGroup = new ButtonGroup();

            String[] volumeLabels = {"20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"};
            float[] volumeValues = {0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};

            for (int i = 0; i < volumeLabels.length; i++) {
                final float vol = volumeValues[i];
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(volumeLabels[i], i == 0); // 50% selecionado
                item.addActionListener(e -> {
                    setGlobalAudioGain(vol);
                });
                volumeGroup.add(item);
                volumeGainMenu.add(item);
            }

            audioMenu2.add(volumeGainMenu);
            audioMenu2.addSeparator();

            // === Informações ===
            JMenuItem infoItem = new JMenuItem("Informações de Áudio");
            infoItem.addActionListener(e -> {
                String info = getNormalizationInfo();
                JOptionPane.showMessageDialog(this,
                        info + "\n\n" +
                                "Normalização: " + (isAudioNormalizationEnabled() ? "Ativada" : "Desativada") + "\n" +
                                "Volume Global: " + (int) (getGlobalAudioGain() * 100) + "%",
                        "Informações de Áudio",
                        JOptionPane.INFORMATION_MESSAGE);
            });

            audioMenu2.add(infoItem);
            contextMenu.add(audioMenu2);

            // === Submenu: Playlist ===
            JMenu playlistMenu = new JMenu("Playlist");
            playlistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            JMenuItem openPlaylistItem = new JMenuItem("📋 Gerenciar Playlist...");
            openPlaylistItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
            openPlaylistItem.addActionListener(e -> showPlaylistDialog());

            JCheckBoxMenuItem autoPlayItem = new JCheckBoxMenuItem("Auto-play Próxima", true);
            autoPlayItem.addActionListener(e -> {
                autoPlayNext = autoPlayItem.isSelected();
            });

            playlistMenu.add(openPlaylistItem);
            playlistMenu.addSeparator();
            playlistMenu.add(autoPlayItem);

            contextMenu.add(playlistMenu);


            // Atualizar estado ao abrir menu
            contextMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    if (grabber == null) {
                        JOptionPane.showMessageDialog(VideoPlayer.this,
                                "Nenhum audio carregado.\nAbra um audio primeiro.",
                                "Aviso", JOptionPane.INFORMATION_MESSAGE);
                        SwingUtilities.invokeLater(() -> contextMenu.setVisible(false));
                        return;
                    }

                    spectrumMenu.setEnabled(true);
                    audioMenu2.setEnabled(true);

                }

                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                }

                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                }
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

        private void setupVideoContextMenu() {
            JPopupMenu contextMenu = new JPopupMenu();

            // Menu de áudio
            JMenu audioMenu = new JMenu("Faixa de Áudio");
            contextMenu.add(audioMenu);

            // Menu de legendas
            JMenu subtitleMenu = new JMenu("Legendas");
            JMenuItem noSubtitle = new JMenuItem("Desabilitado");
            noSubtitle.addActionListener(e -> {
                subtitleManager.setCurrentSubtitleStream(-1);
                subtitleManager.setCurrentSubtitleText("");
                repaint();
            });
            subtitleMenu.add(noSubtitle);

            JMenuItem loadExternal = new JMenuItem("Carregar arquivo externo...");
            loadExternal.addActionListener(e -> subtitleManager.loadExternalSubtitle(videoFilePath, VideoPlayer.this));
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
                sizeItem.setSelected(size == subtitleManager.getBaseSubtitleFontSize());
                sizeItem.addActionListener(e -> {
                    subtitleManager.setBaseSubtitleFontSize(size);
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
            whiteColor.setSelected(subtitleManager.getSubtitleColor().equals(Color.WHITE));
            whiteColor.addActionListener(e -> {
                subtitleManager.setSubtitleColor(Color.WHITE);
                System.out.println("Cor da legenda alterada para: Branco");
                repaint();
            });
            colorGroup.add(whiteColor);
            colorMenu.add(whiteColor);

            JRadioButtonMenuItem yellowColor = new JRadioButtonMenuItem("Amarelo");
            yellowColor.setSelected( subtitleManager.getSubtitleColor().equals(Color.YELLOW));
            yellowColor.addActionListener(e -> {
                subtitleManager.setSubtitleColor(Color.YELLOW);
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

            // ========== NOVO: Menu de Captura ==========
            JMenu captureMenu = new JMenu("Captura");

            // Opção de captura silenciosa
            JCheckBoxMenuItem silentCaptureItem = new JCheckBoxMenuItem("Captura Silenciosa");
            silentCaptureItem.setSelected(silentCapture);
            silentCaptureItem.addActionListener(e -> {
                silentCapture = silentCaptureItem.isSelected();
                System.out.println("Captura silenciosa: " + (silentCapture ? "Ativada" : "Desativada"));
            });
            captureMenu.add(silentCaptureItem);

            captureMenu.addSeparator();

            // Opção de definir pasta personalizada
            JMenuItem selectFolderItem = new JMenuItem("Definir Pasta...");
            selectFolderItem.addActionListener(e -> captureFrameManager.selectCaptureFolder(customCapturePath,videoFilePath, VideoPlayer.this));
            captureMenu.add(selectFolderItem);

            // Opção de resetar para pasta padrão
            JMenuItem resetFolderItem = new JMenuItem("Usar Pasta do Vídeo");
            resetFolderItem.addActionListener(e -> captureFrameManager.resetCaptureFolder(customCapturePath, VideoPlayer.this));
            captureMenu.add(resetFolderItem);

            captureMenu.addSeparator();

            // Mostrar pasta atual
            JMenuItem showCurrentFolder = new JMenuItem("Pasta Atual");
            showCurrentFolder.addActionListener(e -> {
                String currentFolder;
                if (customCapturePath != null && !customCapturePath.isEmpty()) {
                    currentFolder = customCapturePath;
                } else if (videoFilePath != null) {
                    File videoFile = new File(videoFilePath);
                    currentFolder = videoFile.getParent();
                } else {
                    currentFolder = "Nenhuma pasta definida (vídeo não carregado)";
                }

                JOptionPane.showMessageDialog(VideoPlayer.this,
                        "Pasta atual para capturas:\n" + currentFolder,
                        "Pasta de Captura",
                        JOptionPane.INFORMATION_MESSAGE);
            });
            captureMenu.add(showCurrentFolder);

            contextMenu.add(captureMenu);
            // ========== FIM: Menu de Captura ==========

            // ========== NOVO: Menu de Captura em Lote ==========
            JMenu batchCaptureMenu = new JMenu("Captura em Lote");

            // Intervalo de frames
            JMenu intervalMenu = new JMenu("Intervalo de Captura");
            ButtonGroup intervalGroup = new ButtonGroup();
            int[] intervals = {2, 3, 5, 10, 15, 30, 60, 120};

            for (int interval : intervals) {
                JRadioButtonMenuItem intervalItem = new JRadioButtonMenuItem("A cada " + interval + " frame" + (interval > 2 ? "s" : ""));
                intervalItem.setSelected(interval == batchCaptureInterval);

                final int value = interval;
                intervalItem.addActionListener(e -> {
                    batchCaptureInterval = value;
                    System.out.println("Intervalo de captura em lote alterado para: " + batchCaptureInterval);
                });

                intervalGroup.add(intervalItem);
                intervalMenu.add(intervalItem);
            }

            batchCaptureMenu.add(intervalMenu);

            batchCaptureMenu.addSeparator();

            // Definir pasta personalizada
            JMenuItem selectBatchFolderItem = new JMenuItem("Definir Pasta...");
            selectBatchFolderItem.addActionListener(e -> captureFrameManager.selectBatchCaptureFolder(batchCapturePath, videoFilePath, VideoPlayer.this));
            batchCaptureMenu.add(selectBatchFolderItem);

            // Resetar para pasta padrão
            JMenuItem resetBatchFolderItem = new JMenuItem("Usar Pasta do Vídeo");
            resetBatchFolderItem.addActionListener(e -> captureFrameManager.resetBatchCaptureFolder(batchCapturePath, VideoPlayer.this));
            batchCaptureMenu.add(resetBatchFolderItem);

            batchCaptureMenu.addSeparator();

            // Mostrar pasta atual
            JMenuItem showBatchFolder = new JMenuItem("Pasta Atual");
            showBatchFolder.addActionListener(e -> {
                String currentFolder;
                if (batchCapturePath != null && !batchCapturePath.isEmpty()) {
                    currentFolder = batchCapturePath;
                } else if (videoFilePath != null) {
                    File videoFile = new File(videoFilePath);
                    currentFolder = videoFile.getParent();
                } else {
                    currentFolder = "Nenhuma pasta definida (vídeo não carregado)";
                }

                JOptionPane.showMessageDialog(VideoPlayer.this,
                        "Pasta atual para captura em lote:\n" + currentFolder,
                        "Pasta de Captura em Lote",
                        JOptionPane.INFORMATION_MESSAGE);
            });
            batchCaptureMenu.add(showBatchFolder);

            contextMenu.add(batchCaptureMenu);
            // ========== FIM: Menu de Captura em Lote ==========

            // ========== NOVO: Menu de Filtros (apenas para resoluções <= 1280x720) ==========
            JMenu filtersMenu = new JMenu("Filtros");

            // Ajuste de brilho
            JMenuItem brightnessItem = new JMenuItem("Brilho...");
            brightnessItem.addActionListener(e -> filtersManager.showBrightnessDialog(VideoPlayer.this));
            filtersMenu.add(brightnessItem);

            // TODO: Adicionar outros filtros depois
            // JMenuItem contrastItem = new JMenuItem("Contraste...");
            // JMenuItem gammaItem = new JMenuItem("Gamma...");
            // JMenuItem saturationItem = new JMenuItem("Saturação...");

            filtersMenu.addSeparator();

            // Resetar todos os filtros
            JMenuItem resetFiltersItem = new JMenuItem("Resetar Filtros");
            resetFiltersItem.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(VideoPlayer.this,
                        "Deseja resetar todos os filtros aplicados?",
                        "Confirmar Reset",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (confirm == JOptionPane.YES_OPTION) {
                    filtersManager.resetFilters(VideoPlayer.this);
                }
            });
            filtersMenu.add(resetFiltersItem);

            filtersMenu.addSeparator();

            // Mostrar filtros ativos
            JMenuItem showFiltersItem = new JMenuItem("Filtros Ativos");
            showFiltersItem.addActionListener(e -> {
                StringBuilder info = new StringBuilder();
                info.append("Filtros atualmente aplicados:\n\n");

                if (!filtersManager.filtersEnabled || filtersManager.buildFilterString() == null) {
                    info.append("Nenhum filtro ativo");
                } else {
                    if (filtersManager.brightness != 0.0) {
                        info.append(String.format("• Brilho: %.2f\n", filtersManager.brightness));
                    }
                    if (contrast != 1.0) {
                        info.append(String.format("• Contraste: %.2f\n", contrast));
                    }
                    if (gamma != 1.0) {
                        info.append(String.format("• Gamma: %.2f\n", gamma));
                    }
                    if (saturation != 1.0) {
                        info.append(String.format("• Saturação: %.2f\n", saturation));
                    }

                    info.append("\nString FFmpeg:\n").append(filtersManager.buildFilterString());
                }

                JOptionPane.showMessageDialog(VideoPlayer.this,
                        info.toString(),
                        "Filtros Ativos",
                        JOptionPane.INFORMATION_MESSAGE);
            });
            filtersMenu.add(showFiltersItem);

            // Adicionar menu de filtros ao contexto (será habilitado/desabilitado dinamicamente)
            contextMenu.add(filtersMenu);
            // ========== FIM: Menu de Filtros ==========


            // === Submenu: Playlist ===
            JMenu playlistMenu = new JMenu("Playlist");
            playlistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            JMenuItem openPlaylistItem = new JMenuItem("📋 Gerenciar Playlist...");
            openPlaylistItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
            openPlaylistItem.addActionListener(e -> showPlaylistDialog());

            JCheckBoxMenuItem autoPlayItem = new JCheckBoxMenuItem("Auto-play Próxima", true);
            autoPlayItem.addActionListener(e -> {
                autoPlayNext = autoPlayItem.isSelected();
            });

            playlistMenu.add(openPlaylistItem);
            playlistMenu.addSeparator();
            playlistMenu.add(autoPlayItem);

            contextMenu.add(playlistMenu);

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

                    // Verificar resolução do vídeo e habilitar/desabilitar menu de filtros
                    int videoWidth = grabber.getImageWidth();
                    int videoHeight = grabber.getImageHeight();
                    boolean isHighResolution = (videoWidth > 1280 || videoHeight > 720);

                    if (isHighResolution) {
                        filtersMenu.setEnabled(false);
                        filtersMenu.setToolTipText(String.format(
                                "Filtros desabilitados para vídeos com resolução superior a 1280x720 (Atual: %dx%d)",
                                videoWidth, videoHeight
                        ));
                    } else {
                        filtersMenu.setEnabled(true);
                        filtersMenu.setToolTipText(null);
                    }

                    subtitleMenu.setEnabled(true);
                    filtersMenu.setEnabled(true);
                    audioMenu.setEnabled(true);
                    captureMenu.setEnabled(true);
                    batchCaptureMenu.setEnabled(true);
                    performanceMenu.setEnabled(true);
                    intervalMenu.setEnabled(true);
                    fullscreenItem.setEnabled(true);
                    subtitleSettingsMenu.setEnabled(true);

                }

                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                }

                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                }
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

        // Diálogo para escolher cores personalizadas
        private void showCustomColorDialog() {
            Color bottom = JColorChooser.showDialog(this,
                    "Escolha a cor de baixo (Bottom)",
                    new Color(0, 255, 0));

            if (bottom != null) {
                Color middle = JColorChooser.showDialog(this,
                        "Escolha a cor do meio (Middle)",
                        new Color(255, 255, 0));

                if (middle != null) {
                    Color top = JColorChooser.showDialog(this,
                            "Escolha a cor de cima (Top)",
                            new Color(255, 0, 0));

                    if (top != null) {
                        videoPanel.setSpectrumCustomColors(bottom, middle, top);
                        videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
                        JOptionPane.showMessageDialog(this,
                                "Cores personalizadas aplicadas!\n" +
                                        "Bottom: RGB(" + bottom.getRed() + "," + bottom.getGreen() + "," + bottom.getBlue() + ")\n" +
                                        "Middle: RGB(" + middle.getRed() + "," + middle.getGreen() + "," + middle.getBlue() + ")\n" +
                                        "Top: RGB(" + top.getRed() + "," + top.getGreen() + "," + top.getBlue() + ")",
                                "Cores Definidas",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        }

        // Diálogo para loudness personalizado
        private void showCustomLoudnessDialog() {
            String input = JOptionPane.showInputDialog(this,
                    "Digite o target loudness em dBFS:\n" +
                            "(Valores típicos: -30 a 0)\n" +
                            "Mais negativo = mais silencioso\n" +
                            "Menos negativo = mais alto",
                    "-18.0");

            if (input != null) {
                try {
                    float loudness = Float.parseFloat(input);
                    if (loudness >= -30.0f && loudness <= 0.0f) {
                        setTargetLoudness(loudness);
                        JOptionPane.showMessageDialog(this,
                                "Target loudness definido: " + loudness + " dBFS",
                                "Sucesso",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Valor deve estar entre -30.0 e 0.0 dBFS",
                                "Valor Inválido",
                                JOptionPane.WARNING_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Valor inválido! Digite um número decimal (ex: -18.0)",
                            "Erro",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
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
            if (subtitleManager.getTotalSubtitleStreams() > 0) {
                // Adicionar separador após "Carregar arquivo externo"
                subtitleMenu.insertSeparator(fixedItemsCount);

                // Adicionar legendas embutidas
                for (int i = 0; i < subtitleManager.getTotalSubtitleStreams(); i++) {
                    final int streamIndex = i;
                    String streamName = subtitleManager.getSubtitleStreamNames().getOrDefault(i, "Legenda " + (i + 1));
                    JCheckBoxMenuItem item = new JCheckBoxMenuItem(streamName);
                    item.setSelected(i == subtitleManager.getCurrentSubtitleStream());
                    item.addActionListener(e -> subtitleManager.switchSubtitleStream(streamIndex, videoFilePath, VideoPlayer.this,ffmpegPath));
                    subtitleMenu.add(item);
                }
            }
        }

        public void setCoverArtVisible(boolean visible) {
            if (spectrumPanel != null) {
                spectrumPanel.setCoverArtVisible(visible);
            }
        }

        public void updateImage(BufferedImage img) {
            this.currentImage = img;
            this.coverArt = null; // Limpar cover art ao mostrar vídeo
            // Quando há imagem de vídeo, esconder spectrum
            if (spectrumPanel != null) {
                spectrumPanel.setVisible(false);
            }
            repaint();
        }

        // Na classe VideoPanel, adicionar:
        public void setCoverOpacity(float opacity) {
            if (spectrumPanel != null) {
                spectrumPanel.setCoverOpacity(opacity);
            }
        }


        // NOVO: Método para definir cover art
        public void setCoverArt(BufferedImage cover) {
            this.coverArt = cover;
            if (spectrumPanel != null) {
                spectrumPanel.setCoverArt(cover);
            }
        }


        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Se spectrum estiver visível, ele se desenha sozinho
            if (spectrumPanel != null && spectrumPanel.isVisible()) {
                return;
            }

            if (currentImage == null) {
                // Mensagem padrão quando não há vídeo nem áudio
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
            if (!subtitleManager.getCurrentSubtitleText().isEmpty()) {
              subtitleManager.drawSubtitles(g2d, panelWidth, panelHeight, getHeight());
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
        JPanel centerButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 8));

        openButton = new JButton("\uD83D\uDCC1");
        openButton.setPreferredSize(new Dimension(35, 35));
        openButton.setToolTipText("Abrir nova midia");
        openButton.addActionListener(e -> {
            try {
                openVideo();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        //loadPlaylist()
        loadPlaylistButton = new JButton("📂");
        loadPlaylistButton.setEnabled(true);
        loadPlaylistButton.setPreferredSize(new Dimension(35, 35));
        loadPlaylistButton.setToolTipText("Abrir Playlist");
        loadPlaylistButton.addActionListener(e -> loadAndPlayPlaylist());

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
        captureFrameButton.addActionListener(e -> captureFrameManager.captureFrame(grabber, this, videoPanel,
                customCapturePath, videoFilePath,currentFrame, silentCapture));

        captureAllFrameButton = new JButton("\uD83D\uDCE6");
        captureAllFrameButton.setEnabled(false);
        captureAllFrameButton.setPreferredSize(new Dimension(35, 35));
        captureAllFrameButton.setToolTipText("Capturar todos os frames");
        captureAllFrameButton.addActionListener(e -> captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this, batchCaptureThread,isPlaying,
        totalFrames,batchCaptureInterval,frameRate,batchCapturePath,videoFilePath));

        centerButtonPanel.add(openButton);
        centerButtonPanel.add(loadPlaylistButton);
        centerButtonPanel.add(rewindButton);
        centerButtonPanel.add(playPauseButton);
        centerButtonPanel.add(forwardButton);
        centerButtonPanel.add(stopButton);
        centerButtonPanel.add(nextFrameButton);
        centerButtonPanel.add(captureFrameButton);
        centerButtonPanel.add(captureAllFrameButton);

        // Painel direito com controle de volume
        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 15));

        volumeButton = new JButton("🔊");
        volumeButton.setEnabled(false);
        volumeButton.setPreferredSize(new Dimension(35, 35));
        volumeButton.setToolTipText("Ativar/Desativar Som");

        // NOVO: Adicionar ActionListener para mute/unmute
        volumeButton.addActionListener(e -> toggleMute());

        volumeLabel = new JLabel("100%");
        volumeSlider = new JSlider(0, 100, 100);
        volumeSlider.setPreferredSize(new Dimension(100, 20));
        volumeSlider.addChangeListener(e -> {
            // Só processar se não estiver mutado ou se o usuário estiver arrastando o slider
            if (volumeSlider.getValueIsAdjusting() || !isMuted) {
                int vol = volumeSlider.getValue();
                volume = vol / 100.0f;
                volumeLabel.setText(vol + "%");

                // Se estava mutado e o usuário moveu o slider, desmutar
                if (isMuted && volumeSlider.getValueIsAdjusting() && vol > 0) {
                    isMuted = false;
                    updateVolumeButton();
                }

                // Se o volume foi para 0, considerar como mutado
                if (vol == 0 && !isMuted) {
                    isMuted = true;
                    previousVolume = 0.5f; // Definir um volume padrão para unmute
                    updateVolumeButton();
                }
            }
        });

        rightButtonPanel.add(volumeButton);
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

    // NOVO: Método para alternar mute/unmute
    private void toggleMute() {
        if (isMuted) {
            // Desmutar - restaurar volume anterior
            isMuted = false;
            volume = previousVolume;
            int volumePercent = (int) (volume * 100);
            volumeSlider.setValue(volumePercent);
            volumeLabel.setText(volumePercent + "%");
            System.out.println("Som ativado - Volume restaurado para: " + volumePercent + "%");
        } else {
            // Mutar - salvar volume atual e zerar
            isMuted = true;
            previousVolume = volume;
            volume = 0.0f;
            volumeSlider.setValue(0);
            volumeLabel.setText("0%");
            System.out.println("Som desativado - Volume anterior salvo: " + (int) (previousVolume * 100) + "%");
        }

        updateVolumeButton();
    }

    // NOVO: Método para atualizar o ícone do botão de volume
    private void updateVolumeButton() {
        if (isMuted || volume == 0.0f) {
            volumeButton.setText("🔇"); // Mudo
            volumeButton.setToolTipText("Ativar Som");
        } else if (volume < 0.33f) {
            volumeButton.setText("🔈"); // Volume baixo
            volumeButton.setToolTipText("Desativar Som");
        } else if (volume < 0.66f) {
            volumeButton.setText("🔉"); // Volume médio
            volumeButton.setToolTipText("Desativar Som");
        } else {
            volumeButton.setText("🔊"); // Volume alto
            volumeButton.setToolTipText("Desativar Som");
        }
    }

    public void startBatchCapture(String targetDirectory, long totalFramesToCapture, boolean wasPlaying) {
        // Resetar flags de controle
        batchCapturePaused = false;
        batchCaptureCancelled = false;

        // Criar janela de progresso
        JDialog progressDialog = new JDialog(this, "Captura em Lote", true);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setSize(500, 200);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Label de status
        JLabel statusLabel = new JLabel("Iniciando captura...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(statusLabel, BorderLayout.NORTH);

        // Barra de progresso
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(450, 30));
        panel.add(progressBar, BorderLayout.CENTER);

        // Painel de botões
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton pauseButton = new JButton("Pausar");
        JButton cancelButton = new JButton("Cancelar");

        pauseButton.addActionListener(e -> {
            if (batchCapturePaused) {
                batchCapturePaused = false;
                pauseButton.setText("Pausar");
                statusLabel.setText("Retomando captura...");
            } else {
                batchCapturePaused = true;
                pauseButton.setText("Retomar");
                statusLabel.setText("Captura pausada");
            }
        });

        cancelButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(progressDialog,
                    "Deseja realmente cancelar a captura em lote?",
                    "Confirmar Cancelamento",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                batchCaptureCancelled = true;
                statusLabel.setText("Cancelando...");
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
                long savedFrame = currentFrame;

                // Ir para o início
                grabber.setFrameNumber(0);
                currentFrame = 0;

                File videoFile = new File(videoFilePath);
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

                while (frameIndex < totalFrames && !batchCaptureCancelled) {
                    // Verificar se está pausado
                    while (batchCapturePaused && !batchCaptureCancelled) {
                        Thread.sleep(100);
                    }

                    if (batchCaptureCancelled) break;

                    // Capturar apenas frames no intervalo definido
                    if (frameIndex % batchCaptureInterval == 0) {
                        Frame frame = grabber.grabImage();

                        if (frame != null && frame.image != null) {
                            BufferedImage img = converter.convert(frame);

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
                                    int progress = (int) ((currentFrameIndex * 100) / totalFrames);
                                    progressBar.setValue(progress);
                                    statusLabel.setText(String.format("Capturando: %d / %d frames (%.1f%%)",
                                            currentCaptured, totalFramesToCapture, (progress)));
                                });
                            }
                        }
                    } else {
                        // Pular frame sem decodificar imagem
                        grabber.grabImage();
                    }

                    frameIndex++;
                }

                // Criar variável final para uso na lambda
                final long finalCapturedCount = capturedCount;

                // Finalizar
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);

                    if (batchCaptureCancelled) {
                        statusLabel.setText("Captura cancelada!");
                        JOptionPane.showMessageDialog(progressDialog,
                                "Captura em lote cancelada.\n" +
                                        "Frames capturados: " + finalCapturedCount + "\n" +
                                        "Pasta: " + batchFolder.getAbsolutePath(),
                                "Captura Cancelada",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText("Captura concluída!");
                        int openFolder = JOptionPane.showConfirmDialog(progressDialog,
                                "Captura em lote concluída!\n" +
                                        "Total de frames capturados: " + finalCapturedCount + "\n" +
                                        "Pasta: " + batchFolder.getAbsolutePath() + "\n\n" +
                                        "Deseja abrir a pasta?",
                                "Captura Concluída",
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
                        grabber.setFrameNumber((int) savedFrame);
                        Frame frame = grabber.grabImage();
                        if (frame != null && frame.image != null) {
                            BufferedImage img = converter.convert(frame);
                            if (img != null) {
                                videoPanel.updateImage(img);
                            }
                        }
                        currentFrame = savedFrame;
                        updateTimeLabel();

                        if (wasPlaying) {
                            playVideo();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    progressDialog.dispose();
                });

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(progressDialog,
                            "Erro durante a captura:\n" + e.getMessage(),
                            "Erro",
                            JOptionPane.ERROR_MESSAGE);
                    progressDialog.dispose();
                });
            }
        });

        batchCaptureThread.start();
        progressDialog.setVisible(true);
    }

    private void openVideo() {
        if (isPlaying) {
            pauseVideo();
        }
        JnaFileChooser fc = new JnaFileChooser();
        fc.addFilter("Arquivos de Vídeo (*.mp4, *.avi, *.mkv, *.mov, *.flv, *.webm, *.gif, *.wmv, *.mov, *.3gp)", "mp4", "avi", "mkv", "mov", "flv", "webm", "gif", "wmv", "mov", "3gp");
        fc.addFilter("Arquivos de Audio (*.mp3,*.flac, *.wav, *.ogg, *.m4a, *.aac )", "mp3", "flac", "wav", "ogg", "m4a", "aac");
        if (fc.showOpenDialog(this)) {
            File f = fc.getSelectedFile();
            if (f.getName().endsWith("mp3")
                    || f.getName().endsWith("flac")
                    || f.getName().endsWith("wav")
                    || f.getName().endsWith("ogg")
                    || f.getName().endsWith("m4a")
                    || f.getName().endsWith("aac")) {
                loadAudio(f.getAbsolutePath());
            } else {
                loadVideo(f.getAbsolutePath());
            }
        }
    }


    private void loadAudio(String filepath) {
        // Salvar caminho do áudio
        currentVideoPath = filepath;
        videoFilePath = filepath;

        System.out.println("=== INÍCIO loadAudio ===");

        // NOVO: Limpar playlist e fechar dialog
        clearPlaylistAndCloseDialog();

        // Fechar reprodução anterior ANTES de iniciar nova thread
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

        // NOVO: Limpar cover art anterior
        System.out.println("Limpando cover art anterior...");
        audioCoverArt = null;
        SwingUtilities.invokeLater(() -> {
            videoPanel.setCoverArt(null);
            videoPanel.repaint();
        });

        // Limpar estado
        System.out.println("Limpando estado...");
        subtitleManager.getSubtitles().clear();
        subtitleManager.setCurrentSubtitleText("");
        subtitleManager.setCurrentSubtitleStream(-1);
        currentFrame = 0;

        // Desabilitar controles durante carregamento
        System.out.println("Desabilitando controles...");
        playPauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        progressSlider.setEnabled(false);
        openButton.setEnabled(false);
        volumeButton.setEnabled(false);
        rewindButton.setEnabled(false);
        forwardButton.setEnabled(false);
        nextFrameButton.setEnabled(false);
        captureFrameButton.setEnabled(false);
        captureAllFrameButton.setEnabled(false);

        // Resetar filtros
        filtersManager.brightness = 0.0;
        contrast = 1.0;
        gamma = 1.0;
        saturation = 1.0;
        filtersManager.filtersEnabled  = false;

        System.out.println("Atualizando título...");
        setTitle("Video Player - Carregando áudio...");

        System.out.println("Iniciando thread de carregamento de áudio...");

        // Resetar normalização
        normalizationCalculated = false;
        normalizationSampleCount = 0;
        normalizationGain = 1.0f;
        maxPeakLevel = 0.0f;
        averageRMS = 0.0f;
        rmsHistoryIndex = 0;
        Arrays.fill(rmsHistory, 0.0f);
        loudnessAnalyzer.reset();


        // Carregar áudio em thread separada
        Thread loaderThread = new Thread(() -> {
            System.out.println("Thread de carregamento de áudio INICIADA");
            try {
                System.out.println("1. Criando FFmpegFrameGrabber para áudio...");
                grabber = new FFmpegFrameGrabber(filepath);
                System.out.println("2. FFmpegFrameGrabber criado");

                // Opções para melhorar performance de áudio
                System.out.println("3. Aplicando opções de áudio...");
                try {
                    grabber.setOption("analyzeduration", "2000000");
                    grabber.setOption("probesize", "2000000");
                    System.out.println("3. Opções aplicadas");
                } catch (Exception e) {
                    System.out.println("Erro nas opções: " + e.getMessage());
                }

                System.out.println("4. Chamando grabber.start()...");
                grabber.start();
                System.out.println("5. grabber.start() CONCLUÍDO!");

                // Marcar como áudio apenas
                isAudioOnly = true;

                System.out.println("11. Tentando extrair cover art...");
                extractCoverArt(filepath);

                System.out.println("6. Obtendo informações do áudio...");
                audioChannels = grabber.getAudioChannels();
                sampleRate = grabber.getSampleRate();
                System.out.println("7. Canais: " + audioChannels + ", SampleRate: " + sampleRate);

                // Calcular duração em "frames" baseado na taxa de amostragem
                // Para áudio, vamos simular frames a 30 FPS para controle de progresso
                frameRate = 30.0;
                double durationSeconds = grabber.getLengthInTime() / 1000000.0; // microsegundos para segundos
                totalFrames = (long) (durationSeconds * frameRate);
                currentFrame = 0;
                System.out.println("8. Duração: " + durationSeconds + "s, Frames simulados: " + totalFrames);

                // Configurar linha de áudio
                if (audioChannels > 0 && sampleRate > 0) {
                    System.out.println("9. Criando audioLine...");
                    try {
                        int outputChannels = audioChannels > 2 ? 2 : audioChannels;

                        if (audioChannels > 2) {
                            System.out.println("Áudio " + audioChannels + " canais detectado, fazendo downmix para estéreo");
                        }

                        AudioFormat audioFormat = new AudioFormat(
                                sampleRate,
                                16,                    // 16-bit samples
                                outputChannels,        // stereo ou mono
                                true,                  // signed
                                true                   // big-endian (Para audio tem que ser false)
                        );

                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        audioLine = (SourceDataLine) AudioSystem.getLine(info);

                        int bufferSize = sampleRate * outputChannels * 2;
                        audioLine.open(audioFormat, bufferSize);
                        System.out.println("10. AudioLine configurado com sucesso");

                        // Combinação default - deve refletir o que está no menu
                        setGlobalAudioGain(0.20f);          // 20% do volume
                        setTargetLoudness(-18.0f);         // Target mais baixo
                        setAudioNormalizationEnabled(false); // normalização desativada

                    } catch (Exception audioEx) {
                        System.err.println("10. Erro ao configurar áudio: " + audioEx.getMessage());
                        audioLine = null;
                    }
                }

                System.out.println("11. Áudio carregado! Habilitando UI...");

                SwingUtilities.invokeLater(() -> {

                    // Redimensionar e centralizar a janela
                    setSize(650, 500);
                    setLocationRelativeTo(null); // IMPORTANTE: Centralizar após redimensionar
                    setResizable(false);// Desabilta maximizar a janela
                    System.out.println("12. SwingUtilities.invokeLater EXECUTANDO");
                    playPauseButton.setEnabled(true);
                    stopButton.setEnabled(true);
                    progressSlider.setEnabled(true);
                    progressSlider.setValue(0);
                    openButton.setEnabled(true);
                    rewindButton.setEnabled(true);
                    forwardButton.setEnabled(true);
                    volumeButton.setEnabled(true);

                    // Desabilitar controles de vídeo
                    nextFrameButton.setEnabled(false);
                    captureFrameButton.setEnabled(false);
                    captureAllFrameButton.setEnabled(false);

                    updateTimeLabel();

                    // Limpar painel de vídeo e mostrar mensagem
//                    videoPanel.updateImage(null);
//                    videoPanel.repaint();
                    // Mostrar spectrum analyzer

                    // USAR O MÉTODO PÚBLICO EM VEZ DE ACESSAR spectrumPanel DIRETAMENTE
                    videoPanel.setSpectrumSize(600, 400);
                    // Ativar reflexo
                    videoPanel.setSpectrumReflection(true);

                    // Ajustar altura do reflexo (0.0 a 1.0)
                    videoPanel.setSpectrumReflectionHeight(1f); // 50% da altura original

                    // Ajustar transparência do reflexo (0 a 255)
                    videoPanel.setSpectrumReflectionAlpha(180); // Mais transparente

                    videoPanel.showSpectrum();

                    // Ajustar opacidade da capa (0.0 = invisível, 1.0 = opaco)
                    videoPanel.setCoverOpacity(0.5f); // 30% de opacidade (padrão)

                    // Para capa mais visível
                    // videoPanel.setCoverOpacity(0.5f);

                    // Para capa mais discreta
                    // videoPanel.setCoverOpacity(0.2f);


                    // ATIVAR MODO COVER_PALETTE se houver capa
                    videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.COVER_PALETTE);
                    setTitle("Video Player - " + new java.io.File(filepath).getName());
                    System.out.println("É somete audio? " + isAudioOnly);
                    System.out.println("É somete audio? " + isAudioOnly);

                    videoPanel.setupAudioContextMenu();

                    playVideo();
                    System.out.println("13. UI HABILITADA - Pronto para reproduzir áudio!");
                });

                System.out.println("14. Thread de carregamento de áudio CONCLUÍDA");

            } catch (Exception e) {
                System.err.println("ERRO CRÍTICO na thread de carregamento de áudio:");
                e.printStackTrace();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Erro ao abrir arquivo de áudio:\n" + e.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);

                    openButton.setEnabled(true);
                    playPauseButton.setEnabled(false);
                    stopButton.setEnabled(false);
                    volumeButton.setEnabled(false);
                    setTitle("Video Player - JavaCV");
                });
            }
        }, "AudioLoader");

        System.out.println("Thread criada, iniciando...");
        loaderThread.start();
        System.out.println("Thread iniciada! Aguardando conclusão...");
        System.out.println("=== FIM loadAudio (método principal) ===");
    }

    private void loadVideo(String filepath) {
        isAudioOnly = false;

        // Salvar caminho do vídeo
        currentVideoPath = filepath;
        videoFilePath = filepath;

        System.out.println("=== INÍCIO loadVideo ===");

        // **VERIFICAR SE É AV1 EM ALTA RESOLUÇÃO (BLOQUEAR SE SIM)**
        if (!checkAV1Resolution(filepath)) {
            System.out.println("Vídeo bloqueado - AV1 em alta resolução com decoder lento");
            return; // Sair sem carregar o vídeo
        }

        // Limpar playlist e fechar dialog
        clearPlaylistAndCloseDialog();

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

        // Limpar cover art ao carregar vídeo
        System.out.println("Limpando cover art...");
        audioCoverArt = null;
        SwingUtilities.invokeLater(() -> {
            videoPanel.setCoverArt(null);
            videoPanel.repaint();
        });

        // Limpar estado
        System.out.println("Limpando estado...");
        subtitleManager.getSubtitles().clear();
        subtitleManager.setCurrentSubtitleText("");
        subtitleManager.setCurrentSubtitleStream(-1);
        currentFrame = 0;

        // Desabilitar controles durante carregamento
        System.out.println("Desabilitando controles...");
        playPauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        progressSlider.setEnabled(false);
        openButton.setEnabled(false);
        volumeButton.setEnabled(false);

        filtersManager.brightness = 0.0;
        contrast = 1.0;
        gamma = 1.0;
        saturation = 1.0;
        filtersManager.filtersEnabled  = false;

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

                // **NOVO: Otimizar threads para todos os vídeos**
                System.out.println("5. Configurando threads...");
                try {
                    int numThreads = Runtime.getRuntime().availableProcessors();
                    // grabber.setOption("threads", String.valueOf(numThreads));
                    System.out.println("Threads configuradas: " + numThreads);
                } catch (Exception e) {
                    System.out.println("Erro ao configurar threads: " + e.getMessage());
                }

                // Aplicar aceleração GPU se habilitada
                if (hardwareAccelerationEnabled) {
                    System.out.println("6. Tentando habilitar aceleração GPU...");
                    tryEnableHardwareAcceleration(grabber);
                } else {
                    System.out.println("6. Aceleração GPU desabilitada");
                }

                // Opções para melhorar performance
                if (extension.equals("wmv")) {
                    System.out.println("7. Aplicando opções WMV...");
                    try {
                        //  grabber.setOption("threads", "auto");
                        grabber.setOption("fflags", "nobuffer");
                        //  grabber.setOption("flags", "low_delay");
                    } catch (Exception e) {
                        System.out.println("Erro nas opções WMV: " + e.getMessage());
                    }
                } else {
                    System.out.println("7. Não é WMV, pulando opções específicas");
                }

                // Opções gerais
                System.out.println("8. Aplicando opções gerais...");
                try {
                    grabber.setOption("analyzeduration", "2000000");
                    grabber.setOption("probesize", "2000000");
                    grabber.setOption("fflags", "+genpts");
                    System.out.println("8. Opções aplicadas");
                } catch (Exception e) {
                    System.out.println("Erro nas opções gerais: " + e.getMessage());
                }

                System.out.println("Iniciando pré-detecção de streams de áudio...");

                // Detectar mapeamento ANTES de start() usando um grabber temporário
                try {
                    FFmpegFrameGrabber tempGrabber = new FFmpegFrameGrabber(filepath);
                    tempGrabber.start();
                    tempGrabber.setOption("c:v", "libdav1d");
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

                System.out.println("9. Chamando grabber.start()...");

                grabber.start();
                System.out.println("10. grabber.start() CONCLUÍDO!");
                System.out.println(grabber.getVideoMetadata());
                // **DETECTAR E OTIMIZAR AV1**
                System.out.println("11. Detectando codec...");

                detectAndOptimizeAV1(grabber);
                // Definir stream atual como 0 (primeira lógica)
                currentAudioStream = 0;
                System.out.println("12. Stream de áudio atual definida: " + currentAudioStream);

                // Detectar streams de legendas em thread separada
                System.out.println("13. Iniciando detecção de legendas em thread separada...");
                new Thread(() -> {
                    try {
                        System.out.println("Thread de legendas INICIADA");
                        subtitleManager.detectEmbeddedSubtitles(filepath,ffprobePath);
                        System.out.println("Thread de legendas CONCLUÍDA");
                    } catch (Exception e) {
                        System.out.println("Não foi possível detectar legendas embutidas: " + e.getMessage());
                    }
                }, "SubtitleDetector").start();

                System.out.println("14. Obtendo totalFrames e frameRate...");
                totalFrames = grabber.getLengthInVideoFrames();
                frameRate = grabber.getVideoFrameRate();
                currentFrame = 0;
                System.out.println("15. Total frames: " + totalFrames + ", FPS: " + frameRate);

                // Correções de FPS
                System.out.println("16. Verificando correções de FPS...");

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

                System.out.println("17. Configurando áudio...");
                audioChannels = grabber.getAudioChannels();
                sampleRate = grabber.getSampleRate();
                System.out.println("18. Canais: " + audioChannels + ", SampleRate: " + sampleRate);

                if (audioChannels > 0 && sampleRate > 0 && !extension.equals("gif")) {
                    System.out.println("19. Criando audioLine...");
                    try {
                        int outputChannels = audioChannels > 2 ? 2 : audioChannels;

                        if (audioChannels > 2) {
                            System.out.println("Áudio " + audioChannels + " canais detectado, fazendo downmix para estéreo");
                        }

                        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, outputChannels, true, true);
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        audioLine = (SourceDataLine) AudioSystem.getLine(info);

                        // **BUFFER MAIOR para evitar travamentos**
                        int bufferSize = sampleRate * outputChannels * 4; // 4 bytes, 1 segundo de buffer

                        if (extension.equals("wmv")) {
                            bufferSize *= 2;
                        }

                        // **Para vídeos 4K (AV1), aumentar ainda mais o buffer**
                        int videoWidth = grabber.getImageWidth();
                        if (videoWidth >= 3840) {
                            bufferSize *= 3; // 3 segundos de buffer para 4K
                            System.out.println("Vídeo 4K detectado - usando buffer de áudio expandido");
                        }

                        audioLine.open(audioFormat, bufferSize);
                        System.out.println("20. AudioLine configurado com buffer de " + bufferSize + " bytes");
                    } catch (Exception audioEx) {
                        System.err.println("20. Erro ao configurar áudio: " + audioEx.getMessage());
                        audioLine = null;
                    }
                } else {
                    System.out.println("19-20. Sem áudio");
                }

                System.out.println("21. Procurando legendas externas...");
                subtitleManager.searchExternalSubtitles(filepath);
                System.out.println("22. Busca de legendas concluída");

                System.out.println("23. Vídeo carregado! Habilitando UI...");

                int videoWidth = grabber.getImageWidth();
                int videoHeight = grabber.getImageHeight();
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
                System.out.println("Resoluçao do video: " + finalHeight + " : " + finalWidth);

                SwingUtilities.invokeLater(() -> {
                    // Redimensionar e centralizar a janela

                    setSize(finalWidth, finalHeight);

                    // Se a resolução do video for igual ou maior que a resolução da tela maximizar
                    if (finalWidth >= screenWidth || finalHeight >= screenHeight) {
                        System.out.println("Excedeu ou é igual, maximizando ");
                        setExtendedState(JFrame.MAXIMIZED_BOTH);
                    }

                    setLocationRelativeTo(null); // Centralizar após redimensionar
                    setResizable(true); // Pode maximizar a janela

                    System.out.println("24. SwingUtilities.invokeLater EXECUTANDO");
                    playPauseButton.setEnabled(true);
                    stopButton.setEnabled(true);
                    progressSlider.setEnabled(true);
                    progressSlider.setValue(0);
                    openButton.setEnabled(true);
                    rewindButton.setEnabled(true);
                    forwardButton.setEnabled(true);
                    nextFrameButton.setEnabled(true);
                    captureFrameButton.setEnabled(true);
                    captureAllFrameButton.setEnabled(true);
                    volumeButton.setEnabled(true);
                    updateTimeLabel();

                    setTitle("Video Player - " + new java.io.File(filepath).getName());

                    videoPanel.setupVideoContextMenu();

                    playVideo();

                    System.out.println("25. UI HABILITADA - Pronto para reproduzir!");
                });

                System.out.println("26. Thread de carregamento CONCLUÍDA");

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
                    volumeButton.setEnabled(false);
                    setTitle("Video Player - JavaCV");
                });
            }
        }, "VideoLoader");

        System.out.println("Thread criada, iniciando...");
        loaderThread.start();
        System.out.println("Thread iniciada! Aguardando conclusão...");
        System.out.println("=== FIM loadVideo (método principal) ===");
    }
    private void detectAndOptimizeAV1(FFmpegFrameGrabber grabber) {
        try {
            String videoCodec = grabber.getVideoCodecName();
            System.out.println("Codec detectado: " + videoCodec);

            // Se for AV1 e estiver usando libaom-av1, avisar
            if (videoCodec != null && videoCodec.toLowerCase().contains("av1")) {
                System.out.println("=== VÍDEO AV1 DETECTADO ===");

                if (videoCodec.contains("libaom")) {
                    System.out.println("AVISO: Usando libaom-av1 (encoder como decoder)");
                    System.out.println("Isso é mais lento. Idealmente deveria usar libdav1d.");
                    System.out.println("Solução: Recompilar JavaCV com libdav1d ou usar build diferente.");

                    // Configurações específicas do libaom-av1
                    grabber.setOption("cpu-used", "8"); // Velocidade de codificação média (0=lento/qualidade, 8=rápido/baixa qualidade)
                    grabber.setOption("crf", "28");    // Qualidade (valor padrão é geralmente bom, menor é melhor qualidade)
                    grabber.setVideoBitrate(2000000); // Exemplo de definição de bitrate (2 Mbps)
                    grabber.setOption("g", "150");    // GOP size para melhor capacidade de busca
                    grabber.setOption("fps", "20");
                    grabber.setOption("c:v", "libdav1d");
                    grabber.setVideoCodec(AV_CODEC_ID_AV1);
                    // grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P10LE); // 10-bit color (opcional)
                } else if (videoCodec.contains("libdav1d") || videoCodec.contains("dav1d")) {
                    System.out.println("✓ Usando libdav1d - decoder otimizado!");
                }

                // Informações sobre a configuração
                int threads = Runtime.getRuntime().availableProcessors();
                System.out.println("Threads disponíveis: " + threads);
                System.out.println("Resolução: " + grabber.getImageWidth() + "x" + grabber.getImageHeight());
                System.out.println("FPS: " + grabber.getVideoFrameRate());

                // Para vídeos 4K AV1, sugerir desabilitar filtros
                if (grabber.getImageWidth() >= 3840) {
                    System.out.println("DICA: Para vídeo 4K, desabilite filtros para melhor performance");
                }
            }

        } catch (Exception e) {
            System.out.println("Erro ao detectar codec: " + e.getMessage());
        }
    }

    /**
     * Verifica se o vídeo é AV1 com resolução acima de 1080p e bloqueia reprodução
     * Retorna true se o vídeo pode ser reproduzido, false se deve ser bloqueado
     */
    private boolean checkAV1Resolution(String filepath) {
        FFmpegFrameGrabber tempGrabber = null;

        try {
            String extension = filepath.substring(filepath.lastIndexOf('.') + 1).toLowerCase();

            // Verificar apenas arquivos MP4
            if (!extension.equals("mp5")) {
                return true; // Permitir outros formatos
            }

            System.out.println("Verificando codec e resolução do MP4...");

            // Criar grabber temporário para verificação
            tempGrabber = new FFmpegFrameGrabber(filepath);
            tempGrabber.start();

            // Obter informações do vídeo
            String videoCodec = tempGrabber.getVideoCodecName();
            int width = tempGrabber.getImageWidth();
            int height = tempGrabber.getImageHeight();

            System.out.println("Codec: " + videoCodec);
            System.out.println("Resolução: " + width + "x" + height);

            // Verificar se é AV1
            boolean isAV1 = false;
            if (videoCodec != null) {
                String codecLower = videoCodec.toLowerCase();
                isAV1 = codecLower.contains("av1") || codecLower.contains("libaom");
            }

            // Verificar se está usando libaom-av1 (decoder lento)
            boolean isSlowDecoder = false;
            if (isAV1 && videoCodec != null && videoCodec.contains("libaom")) {
                isSlowDecoder = true;
                System.out.println("AVISO: Detectado libaom-av1 (decoder lento)");
            }

            // Verificar resolução
            boolean isHighRes = (width > 1920 || height > 1080);

            // Fechar grabber temporário
            tempGrabber.stop();
            tempGrabber.release();

            // Bloquear se for AV1 com libaom E alta resolução
            if (isAV1 && isSlowDecoder && isHighRes) {
                System.out.println("BLOQUEADO: AV1 com libaom-av1 em resolução " + width + "x" + height);

                // Mostrar modal de aviso
                SwingUtilities.invokeLater(() -> {
                    String message = String.format(
                            "Este vídeo não pode ser reproduzido:\n\n" +
                                    "• Codec: AV1 (libaom-av1)\n" +
                                    "• Resolução: %dx%d\n\n" +
                                    "O JavaCV está usando o decoder 'libaom-av1' que é muito lento\n" +
                                    "para vídeos AV1 em alta resolução (acima de 1920x1080).\n\n" +
                                    "Soluções:\n" +
                                    "1. Converter o vídeo para H.264/H.265\n" +
                                    "2. Reduzir a resolução para 1080p ou menos\n" +
                                    "3. Atualizar o JavaCV para versão com libdav1d\n" +
                                    "4. Reproduzir com player externo (VLC, MPV, etc.)",
                            width, height
                    );

                    JOptionPane.showMessageDialog(
                            this,
                            message,
                            "Vídeo AV1 em Alta Resolução Não Suportado",
                            JOptionPane.WARNING_MESSAGE
                    );

                    // Reabilitar botão para carregar outro vídeo
                    openButton.setEnabled(true);
                    playPauseButton.setEnabled(false);
                    stopButton.setEnabled(false);
                    volumeButton.setEnabled(false);
                    setTitle("Video Player - JavaCV");
                });

                return false; // Bloquear reprodução
            }

            // Se for AV1 mas com libdav1d, ou resolução baixa, permitir
            if (isAV1 && !isSlowDecoder) {
                System.out.println("✓ AV1 com decoder otimizado detectado - permitindo reprodução");
            } else if (isAV1 && !isHighRes) {
                System.out.println("✓ AV1 em resolução baixa (" + width + "x" + height + ") - permitindo reprodução");
            }

            return true; // Permitir reprodução

        } catch (Exception e) {
            System.err.println("Erro ao verificar codec/resolução: " + e.getMessage());
            e.printStackTrace();

            // Em caso de erro na verificação, permitir tentar reproduzir
            if (tempGrabber != null) {
                try {
                    tempGrabber.stop();
                    tempGrabber.release();
                } catch (Exception ex) {
                    // Ignorar erro ao fechar
                }
            }

            return true; // Permitir reprodução em caso de erro na verificação
        }
    }

    public void playVideo() {
        if (grabber == null || isPlaying) return;

        isPlaying = true;
        isStopped = false;
        playPauseButton.setText("⏸");

        if (audioLine != null && !audioLine.isRunning()) {
            audioLine.start();
            videoPanel.spectrumPanel.setPaused(false);
        }

        playbackThread = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                long frameDelay = (long) (1000.0 / frameRate);
                long frameCount = 0;

                // Se for áudio apenas, usar lógica simplificada
                if (isAudioOnly) {
                    System.out.println("Iniciando reprodução de áudio...");

                    // Posicionar no frame atual (para retomar de onde parou)
                    try {
                        long targetTimestamp = (long) ((currentFrame / frameRate) * 1000000);
                        grabber.setTimestamp(targetTimestamp);
                        System.out.println("Retomando áudio do frame: " + currentFrame);
                    } catch (Exception e) {
                        System.err.println("Erro ao posicionar áudio: " + e.getMessage());
                    }

                    Frame frame;
                    long lastUpdateTime = System.currentTimeMillis();
                    int samplesProcessed = 0;

                    while (isPlaying) {
                        frame = grabber.grabSamples();

                        if (frame == null) {
                            System.out.println("Fim do áudio alcançado");
                            handleVideoEnd();
                            break;
                        }

                        if (frame.samples != null && audioLine != null) {
                            try {
                                ShortBuffer channelSamples = (ShortBuffer) frame.samples[0];
                                channelSamples.rewind();

                                if (channelSamples.remaining() > 0) {
                                    byte[] audioBytes = processAudioSamples(channelSamples);

                                    if (audioBytes != null) {
                                        audioLine.write(audioBytes, 0, audioBytes.length);
                                        samplesProcessed++;
                                    }
                                }

                                long currentTime = System.currentTimeMillis();
                                long elapsedTime = currentTime - lastUpdateTime;

                                if (elapsedTime >= 100) {
                                    long framesElapsed = (long) ((elapsedTime / 1000.0) * frameRate);
                                    currentFrame += framesElapsed;

                                    if (currentFrame > totalFrames) {
                                        currentFrame = totalFrames;
                                    }

                                    lastUpdateTime = currentTime;

                                    SwingUtilities.invokeLater(() -> {
                                        if (!isSeeking && totalFrames > 0) {
                                            int progress = (int) ((currentFrame * 100) / totalFrames);
                                            progressSlider.setValue(progress);
                                            updateTimeLabel();
                                        }
                                    });
                                }

                            } catch (Exception audioEx) {
                                System.err.println("Erro ao processar áudio: " + audioEx.getMessage());
                            }
                        }
                    }

                    System.out.println("Reprodução de áudio finalizada");

                } else {
                    // ===== LÓGICA DE VÍDEO (ORIGINAL COM OTIMIZAÇÕES) =====
                    long lastUpdateTime = startTime;
                    double avgFrameTime = frameDelay;
                    long totalDecodeTime = 0;
                    long totalRenderTime = 0;
                    int statsCounter = 0;

                    // **NOVO: Detectar se é vídeo pesado (4K)**
                    boolean isHeavyVideo = (grabber.getImageWidth() >= 3840);
                    int uiUpdateInterval = isHeavyVideo ? 10 : 5; // Atualizar UI menos frequente em 4K

                    System.out.println("Iniciando reprodução - " +
                            (isHeavyVideo ? "Modo 4K (UI a cada 10 frames)" : "Modo normal (UI a cada 5 frames)"));

                    while (isPlaying) {
                        long frameStartTime = System.currentTimeMillis();
                        long decodeStart = System.currentTimeMillis();

                        Frame frame = grabber.grab();

                        long decodeTime = System.currentTimeMillis() - decodeStart;

                        if (frame == null) {
                            handleVideoEnd();
                            break;
                        }

                        // **PROCESSAR VÍDEO**
                        if (frame.image != null) {
                            long renderStart = System.currentTimeMillis();
                            BufferedImage img = converter.convert(frame);

                            if (img != null) {
                                // **OTIMIZAÇÃO: Pular filtros em vídeos pesados se enabled**
                                if (filtersManager.filtersEnabled ) {
                                    // Para 4K, aplicar filtros apenas a cada 2 frames
                                    if (!isHeavyVideo || currentFrame % 2 == 0) {
                                        img = filtersManager.applyImageFilters(img);
                                    }
                                }
                                videoPanel.updateImage(img);
                            }

                            long renderTime = System.currentTimeMillis() - renderStart;
                            totalDecodeTime += decodeTime;
                            totalRenderTime += renderTime;
                            statsCounter++;

                            // Log de performance a cada 100 frames
                            if (statsCounter >= 100) {
                                long avgDecode = totalDecodeTime / statsCounter;
                                long avgRender = totalRenderTime / statsCounter;
                                System.out.println("Performance - Decode: " + avgDecode + "ms, Render: " + avgRender + "ms");
                                totalDecodeTime = 0;
                                totalRenderTime = 0;
                                statsCounter = 0;
                            }

                            currentFrame++;
                            frameCount++;

                            // Atualizar legenda
                            long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                            subtitleManager.updateSubtitle(currentTimeMs, videoPanel);

                            // Calcular tempo médio de frame (para sincronização adaptativa)
                            long timeSinceLastUpdate = frameStartTime - lastUpdateTime;
                            if (frameCount > 10) {
                                avgFrameTime = (avgFrameTime * 0.9) + (timeSinceLastUpdate * 0.1);
                            }
                            lastUpdateTime = frameStartTime;

                            // **Atualizar UI com frequência adaptativa**
                            if (currentFrame % uiUpdateInterval == 0) {
                                SwingUtilities.invokeLater(() -> {
                                    if (!isSeeking && totalFrames > 0) {
                                        int progress = (int) ((currentFrame * 100) / totalFrames);
                                        progressSlider.setValue(progress);
                                        updateTimeLabel();
                                    }
                                });
                            }

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

                        // **PROCESSAR ÁUDIO (ORIGINAL - MANTIDO)**
                        if (frame.samples != null && audioLine != null) {
                            processVideoAudioFrame(frame);
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    isPlaying = false;
                    playPauseButton.setText("▶");
                });
            }
        });

        playbackThread.start();
    }

    // **MÉTODO AUXILIAR PARA FIM DE VÍDEO**
    private void handleVideoEnd() {
        if (playlistManager.size() > 0 && playlistManager.isShuffle()) {
            playlistManager.markCurrentAsPlayed();
        }

        if (autoPlayNext && playlistManager.size() > 0) {
            SwingUtilities.invokeLater(() -> {
                PlaylistItem next = playlistManager.next();
                playlistDialog.refreshPlaylist();
                if (next != null) {
                    System.out.println("Auto-play: " + next.getDisplayName());
                    playFromPlaylist(next.getFilePath());
                } else {
                    stopVideo();
                }
            });
        } else {
            SwingUtilities.invokeLater(() -> stopVideo());
        }
    }

    private void loadAudioFromPlaylist(String filepath) {
        // Salvar caminho do áudio
        currentVideoPath = filepath;
        videoFilePath = filepath;

        System.out.println("=== INÍCIO loadAudioFromPlaylist  ===");

        // Fechar reprodução anterior ANTES de iniciar nova thread
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

        // NOVO: Limpar cover art anterior
        System.out.println("Limpando cover art anterior...");
        audioCoverArt = null;
        SwingUtilities.invokeLater(() -> {
            videoPanel.setCoverArt(null);
            videoPanel.repaint();
        });

        // Limpar estado
        System.out.println("Limpando estado...");
        subtitleManager.getSubtitles().clear();
        subtitleManager.setCurrentSubtitleText("");
        subtitleManager.setCurrentSubtitleStream(-1);
        currentFrame = 0;

        // Desabilitar controles durante carregamento
        System.out.println("Desabilitando controles...");
        playPauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        progressSlider.setEnabled(false);
        openButton.setEnabled(false);
        volumeButton.setEnabled(false);
        rewindButton.setEnabled(false);
        forwardButton.setEnabled(false);
        nextFrameButton.setEnabled(false);
        captureFrameButton.setEnabled(false);
        captureAllFrameButton.setEnabled(false);

        // Resetar filtros
        filtersManager.brightness = 0.0;
        contrast = 1.0;
        gamma = 1.0;
        saturation = 1.0;
        filtersManager.filtersEnabled  = false;

        System.out.println("Atualizando título...");
        setTitle("Video Player - Carregando áudio...");

        System.out.println("Iniciando thread de carregamento de áudio...");

        // Resetar normalização
        normalizationCalculated = false;
        normalizationSampleCount = 0;
        normalizationGain = 1.0f;
        maxPeakLevel = 0.0f;
        averageRMS = 0.0f;
        rmsHistoryIndex = 0;
        Arrays.fill(rmsHistory, 0.0f);
        loudnessAnalyzer.reset();


        // Carregar áudio em thread separada
        Thread loaderThread = new Thread(() -> {
            System.out.println("Thread de carregamento de áudio INICIADA");
            try {
                System.out.println("1. Criando FFmpegFrameGrabber para áudio...");
                grabber = new FFmpegFrameGrabber(filepath);
                System.out.println("2. FFmpegFrameGrabber criado");

                // Opções para melhorar performance de áudio
                System.out.println("3. Aplicando opções de áudio...");
                try {
                    grabber.setOption("analyzeduration", "2000000");
                    grabber.setOption("probesize", "2000000");
                    System.out.println("3. Opções aplicadas");
                } catch (Exception e) {
                    System.out.println("Erro nas opções: " + e.getMessage());
                }

                System.out.println("4. Chamando grabber.start()...");
                grabber.start();
                System.out.println("5. grabber.start() CONCLUÍDO!");

                // Marcar como áudio apenas
                isAudioOnly = true;

                System.out.println("11. Tentando extrair cover art...");
                extractCoverArt(filepath);

                System.out.println("6. Obtendo informações do áudio...");
                audioChannels = grabber.getAudioChannels();
                sampleRate = grabber.getSampleRate();
                System.out.println("7. Canais: " + audioChannels + ", SampleRate: " + sampleRate);

                // Calcular duração em "frames" baseado na taxa de amostragem
                // Para áudio, vamos simular frames a 30 FPS para controle de progresso
                frameRate = 30.0;
                double durationSeconds = grabber.getLengthInTime() / 1000000.0; // microsegundos para segundos
                totalFrames = (long) (durationSeconds * frameRate);
                currentFrame = 0;
                System.out.println("8. Duração: " + durationSeconds + "s, Frames simulados: " + totalFrames);

                // Configurar linha de áudio
                if (audioChannels > 0 && sampleRate > 0) {
                    System.out.println("9. Criando audioLine...");
                    try {
                        int outputChannels = audioChannels > 2 ? 2 : audioChannels;

                        if (audioChannels > 2) {
                            System.out.println("Áudio " + audioChannels + " canais detectado, fazendo downmix para estéreo");
                        }

                        AudioFormat audioFormat = new AudioFormat(
                                sampleRate,
                                16,                    // 16-bit samples
                                outputChannels,        // stereo ou mono
                                true,                  // signed
                                true                   // big-endian (Para audio tem que ser false)
                        );

                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        audioLine = (SourceDataLine) AudioSystem.getLine(info);

                        int bufferSize = sampleRate * outputChannels * 2;
                        audioLine.open(audioFormat, bufferSize);
                        System.out.println("10. AudioLine configurado com sucesso");

                        // Combinação default - deve refletir o que está no menu
                        setGlobalAudioGain(0.20f);          // 20% do volume
                        setTargetLoudness(-18.0f);         // Target mais baixo
                        setAudioNormalizationEnabled(false); // normalização desativada

                    } catch (Exception audioEx) {
                        System.err.println("10. Erro ao configurar áudio: " + audioEx.getMessage());
                        audioLine = null;
                    }
                }

                System.out.println("11. Áudio carregado! Habilitando UI...");

                SwingUtilities.invokeLater(() -> {

                    // Redimensionar e centralizar a janela
                    setSize(650, 500);
                    setLocationRelativeTo(null); // IMPORTANTE: Centralizar após redimensionar
                    setResizable(false);// Desabilta maximizar a janela
                    System.out.println("12. SwingUtilities.invokeLater EXECUTANDO");
                    playPauseButton.setEnabled(true);
                    stopButton.setEnabled(true);
                    progressSlider.setEnabled(true);
                    progressSlider.setValue(0);
                    openButton.setEnabled(true);
                    rewindButton.setEnabled(true);
                    forwardButton.setEnabled(true);
                    volumeButton.setEnabled(true);

                    // Desabilitar controles de vídeo
                    nextFrameButton.setEnabled(false);
                    captureFrameButton.setEnabled(false);
                    captureAllFrameButton.setEnabled(false);

                    updateTimeLabel();

                    // Limpar painel de vídeo e mostrar mensagem
//                    videoPanel.updateImage(null);
//                    videoPanel.repaint();
                    // Mostrar spectrum analyzer

                    // USAR O MÉTODO PÚBLICO EM VEZ DE ACESSAR spectrumPanel DIRETAMENTE
                    videoPanel.setSpectrumSize(600, 400);
                    // Ativar reflexo
                    videoPanel.setSpectrumReflection(true);

                    // Ajustar altura do reflexo (0.0 a 1.0)
                    videoPanel.setSpectrumReflectionHeight(1f); // 50% da altura original

                    // Ajustar transparência do reflexo (0 a 255)
                    videoPanel.setSpectrumReflectionAlpha(180); // Mais transparente

                    videoPanel.showSpectrum();

                    // Ajustar opacidade da capa (0.0 = invisível, 1.0 = opaco)
                    videoPanel.setCoverOpacity(0.5f); // 30% de opacidade (padrão)

                    // Para capa mais visível
                    // videoPanel.setCoverOpacity(0.5f);

                    // Para capa mais discreta
                    // videoPanel.setCoverOpacity(0.2f);


                    // ATIVAR MODO COVER_PALETTE se houver capa
                    videoPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.COVER_PALETTE);
                    setTitle("Video Player - " + new java.io.File(filepath).getName());

                    videoPanel.setupAudioContextMenu();

                    playVideo();
                    System.out.println("13. UI HABILITADA - Pronto para reproduzir áudio!");
                });

                System.out.println("14. Thread de carregamento de áudio CONCLUÍDA");

            } catch (Exception e) {
                System.err.println("ERRO CRÍTICO na thread de carregamento de áudio:");
                e.printStackTrace();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Erro ao abrir arquivo de áudio:\n" + e.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);

                    openButton.setEnabled(true);
                    playPauseButton.setEnabled(false);
                    stopButton.setEnabled(false);
                    volumeButton.setEnabled(false);
                    setTitle("Video Player - JavaCV");
                });
            }
        }, "AudioLoader");

        System.out.println("Thread criada, iniciando...");
        loaderThread.start();
        System.out.println("Thread iniciada! Aguardando conclusão...");
        System.out.println("=== FIM loadAudio (método principal) ===");
    }

    private void loadVideoFromPlaylist(String filepath) {
        isAudioOnly = false;

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

        // NOVO: Limpar cover art ao carregar vídeo
        System.out.println("Limpando cover art...");
        audioCoverArt = null;
        SwingUtilities.invokeLater(() -> {
            videoPanel.setCoverArt(null);
            videoPanel.repaint();
        });

        // Limpar estado
        System.out.println("Limpando estado...");
        subtitleManager.getSubtitles().clear();
        subtitleManager.setCurrentSubtitleText("");
        subtitleManager.setCurrentSubtitleStream(-1);
        currentFrame = 0;

        // Desabilitar controles durante carregamento
        System.out.println("Desabilitando controles...");
        playPauseButton.setEnabled(false);
        stopButton.setEnabled(false);
        progressSlider.setEnabled(false);
        openButton.setEnabled(false);
        volumeButton.setEnabled(false);

        filtersManager.brightness = 0.0;
        contrast = 1.0;
        gamma = 1.0;
        saturation = 1.0;
        filtersManager.filtersEnabled  = false;

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
                        //   grabber.setOption("threads", "auto");
                        grabber.setOption("fflags", "nobuffer");
                        // grabber.setOption("flags", "low_delay");
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
                        subtitleManager.detectEmbeddedSubtitles(filepath,ffprobePath);
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
                subtitleManager.searchExternalSubtitles(filepath);
                System.out.println("20. Busca de legendas concluída");

                System.out.println("21. Vídeo carregado! Habilitando UI...");


                int videoWidth = grabber.getImageWidth();
                int videoHeight = grabber.getImageHeight();
                int tempVideoWidth = videoWidth;
                if (videoWidth <= 500) {
                    tempVideoWidth = 600;
                }
                // Guardar dimensões para usar no SwingUtilities.invokeLater
                final int finalWidth = tempVideoWidth;
                final int finalHeight = videoHeight;

                SwingUtilities.invokeLater(() -> {

                    // Redimensionar e centralizar a janela
                    setSize(finalWidth, finalHeight);

                    //Se a resolução do video for igual ou maior que a resolução da tela maximizar, para evitar
                    //que parte do video fique de fora tela
                    if (finalWidth >= screenWidth || finalHeight >= screenHeight) {
                        System.out.println("Excedeu ou é igual, maximizando ");
                        setExtendedState(JFrame.MAXIMIZED_BOTH);

                    }
                    setLocationRelativeTo(null); // IMPORTANTE: Centralizar após redimensionar
                    setResizable(true);// Pode maximizar a janela

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
                    captureAllFrameButton.setEnabled(true); // NOVO
                    volumeButton.setEnabled(true);
                    updateTimeLabel();

                    setTitle("Video Player - " + new java.io.File(filepath).getName());

                    videoPanel.setupVideoContextMenu();

                    playVideo();

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
                    volumeButton.setEnabled(false);
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
        long framesTo10Seconds = (long) (frameRate * 10);
        long targetFrame = Math.max(0, currentFrame - framesTo10Seconds);

        boolean wasPlaying = isPlaying;
        if (wasPlaying) {
            pauseVideo();
        }

        try {
            currentFrame = targetFrame;

            if (isAudioOnly) {
                // Para áudio, usar timestamp
                long targetTimestamp = (long) ((currentFrame / frameRate) * 1000000);
                grabber.setTimestamp(targetTimestamp);
                System.out.println("Retrocedeu áudio para frame: " + targetFrame);
            } else {
                // Para vídeo, usar frame number
                grabber.setFrameNumber((int) targetFrame);

                // Capturar e exibir frame
                Frame frame = grabber.grabImage();
                if (frame != null && frame.image != null) {
                    BufferedImage img = converter.convert(frame);
                    if (img != null) {
                        videoPanel.updateImage(img);
                    }
                }

                // Resetar posição
                grabber.setFrameNumber((int) targetFrame);

                // Atualizar legenda
                long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                subtitleManager.updateSubtitle(currentTimeMs, videoPanel);
            }

            // Atualizar UI
            updateTimeLabel();
            int progress = (int) ((targetFrame * 100) / totalFrames);
            progressSlider.setValue(progress);

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
        long framesTo10Seconds = (long) (frameRate * 10);
        long targetFrame = Math.min(totalFrames - 1, currentFrame + framesTo10Seconds);

        boolean wasPlaying = isPlaying;
        if (wasPlaying) {
            pauseVideo();
        }

        try {
            currentFrame = targetFrame;

            if (isAudioOnly) {
                // Para áudio, usar timestamp
                long targetTimestamp = (long) ((currentFrame / frameRate) * 1000000);
                grabber.setTimestamp(targetTimestamp);
                System.out.println("Avançou áudio para frame: " + targetFrame);
            } else {
                // Para vídeo, usar frame number
                grabber.setFrameNumber((int) targetFrame);

                // Capturar e exibir frame
                Frame frame = grabber.grabImage();
                if (frame != null && frame.image != null) {
                    BufferedImage img = converter.convert(frame);
                    if (img != null) {
                        videoPanel.updateImage(img);
                    }
                }

                // Resetar posição
                grabber.setFrameNumber((int) targetFrame);

                // Atualizar legenda
                long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                subtitleManager.updateSubtitle(currentTimeMs, videoPanel);
            }

            // Atualizar UI
            updateTimeLabel();
            int progress = (int) ((targetFrame * 100) / totalFrames);
            progressSlider.setValue(progress);

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
                            int progress = (int) ((currentFrame * 100) / totalFrames);
                            progressSlider.setValue(progress);

                            // Atualizar legenda
                            long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                            subtitleManager.updateSubtitle(currentTimeMs, videoPanel);

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
                grabber.setFrameNumber((int) targetFrame);
                currentFrame = targetFrame;

                Frame frame = grabber.grabImage();
                if (frame != null && frame.image != null) {
                    BufferedImage img = converter.convert(frame);
                    if (img != null) {
                        videoPanel.updateImage(img);
                        updateTimeLabel();
                        int progress = (int) ((targetFrame * 100) / totalFrames);
                        progressSlider.setValue(progress);

                        long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                        subtitleManager.updateSubtitle(currentTimeMs, videoPanel);
                    }
                }
                grabber.setFrameNumber((int) targetFrame);
            } catch (Exception fallbackError) {
                System.err.println("Fallback também falhou: " + fallbackError.getMessage());
            }
        }
    }

    // Modificar switchAudioStream
    public void switchAudioStream(int streamIndex) {
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
                                //   grabber.setOption("threads", "auto");
                                grabber.setOption("fflags", "nobuffer");
                                //   grabber.setOption("flags", "low_delay");
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
                           subtitleManager.setSubtitleStreamNames(new HashMap<>(savedSubtitleStreamNames));
                            System.out.println("Nomes das streams de legendas restaurados: " + subtitleManager.getSubtitleStreamNames().size());
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
                   subtitleManager.setSubtitles(new ArrayList<>(savedSubtitles));
                    subtitleManager.setCurrentSubtitleStream(savedSubtitleStream);
                    System.out.println("Legendas restauradas: " + subtitleManager.getSubtitles().size() + " entradas");
                }

                // Garantir que totalSubtitleStreams está correto
                if (subtitleManager.getTotalSubtitleStreams() == 0 && !subtitleManager.getSubtitleStreamNames().isEmpty()) {
                    subtitleManager.setTotalSubtitleStreams(subtitleManager.getSubtitleStreamNames().size());
                    System.out.println("Total de legendas embutidas corrigido: " + subtitleManager.getTotalSubtitleStreams());
                }

                // Buscar posição salva
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (savedFramePosition > 0 && totalFrames > 0) {
                            // Ir para o frame salvo
                            grabber.setFrameNumber((int) savedFramePosition);
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
                            grabber.setFrameNumber((int) savedFramePosition);
                            currentFrame = savedFramePosition;

                            // Atualizar UI
                            int progress = (int) ((savedFramePosition * 100) / totalFrames);
                            progressSlider.setValue(progress);
                            updateTimeLabel();

                            // Atualizar legenda
                            long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                            subtitleManager.updateSubtitle(currentTimeMs, videoPanel);

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


//    private void switchSubtitleStream(int streamIndex) {
//        subtitleManager.setCurrentSubtitleStream(streamIndex);
//        System.out.println("Trocando para legenda embutida stream: " + streamIndex);
//
//        if (videoFilePath == null) {
//            System.err.println("Caminho do vídeo não disponível");
//            return;
//        }
//
//        // Extrair legenda embutida usando FFmpeg em thread separada
//        new Thread(() -> {
//            try {
//                togglePlayPause();
//                // Sempre extrair como SRT para normalizar formato
//                File tempSubtitle = File.createTempFile("subtitle_", ".srt");
//                tempSubtitle.deleteOnExit();
//
//                System.out.println("Extraindo legenda para: " + tempSubtitle.getAbsolutePath());
//                System.out.println("Comando: ffmpeg -i \"" + videoFilePath + "\" -map 0:s:" + streamIndex + " \"" + tempSubtitle.getAbsolutePath() + "\"");
//
//                ProcessBuilder pb = new ProcessBuilder(
//                        ffmpegPath,
//                        "-i", videoFilePath,
//                        "-map", "0:s:" + streamIndex,
//                        "-c:s", "srt", // Converter para SRT
//                        "-y",
//                        tempSubtitle.getAbsolutePath()
//                );
//
//                pb.redirectErrorStream(true);
//                Process process = pb.start();
//
//                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//                String line;
//                boolean hasError = false;
//
//                while ((line = reader.readLine()) != null) {
//                    System.out.println("FFmpeg: " + line);
//
//                    if (line.toLowerCase().contains("error") || line.toLowerCase().contains("invalid")) {
//                        hasError = true;
//                        System.err.println("ERRO: " + line);
//                    }
//                }
//
//                int exitCode = process.waitFor();
//
//                System.out.println("FFmpeg terminou com código: " + exitCode);
//                System.out.println("Arquivo existe: " + tempSubtitle.exists());
//                System.out.println("Tamanho do arquivo: " + tempSubtitle.length() + " bytes");
//
//                if (exitCode == 0 && tempSubtitle.exists() && tempSubtitle.length() > 0) {
//                    System.out.println("Legenda extraída com sucesso!");
//
//                    try (BufferedReader br = new BufferedReader(new FileReader(tempSubtitle))) {
//                        System.out.println("=== Primeiras linhas da legenda ===");
//                        for (int i = 0; i < 5 && br.ready(); i++) {
//                            System.out.println(br.readLine());
//                        }
//                        System.out.println("=================================");
//                    }
//
//                    SwingUtilities.invokeLater(() -> {
//                        subtitleManager.loadSubtitleFile(tempSubtitle, VideoPlayer.this);
//                        if (!subtitleManager.getSubtitles().isEmpty()) {
//                            JOptionPane.showMessageDialog(this,
//                                    "Legenda carregada com sucesso!",
//                                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
//                        }
//
//                    });
//
//                } else {
//                    throw new Exception("Falha ao extrair legenda (código: " + exitCode + ", tamanho: " + tempSubtitle.length() + ")");
//                }
//                togglePlayPause();
//            } catch (Exception e) {
//                e.printStackTrace();
//                System.err.println("Erro detalhado: " + e.getMessage());
//
//                SwingUtilities.invokeLater(() -> {
//                    JOptionPane.showMessageDialog(this,
//                            "Não foi possível carregar a legenda embutida.\n" +
//                                    "Possíveis causas:\n" +
//                                    "- FFmpeg não está na pasta lib app\n" +
//                                    "- Stream de legenda incompatível\n" +
//                                    "- Formato de legenda não suportado\n\n" +
//                                    "Erro: " + e.getMessage(),
//                            "Erro", JOptionPane.ERROR_MESSAGE);
//                });
//            }
//        }).start();
//
//    }

    private void tryEnableHardwareAcceleration(FFmpegFrameGrabber grabber) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            // Primeiro, tentar hardware acceleration (mas sua GPU não suporta AV1)
            if (os.contains("win")) {
                try {
                    grabber.setVideoOption("hwaccel", "none");
                    System.out.println("Tentando aceleração GPU (auto)");
                } catch (Exception e) {
                    System.out.println("Hardware acceleration não disponível: " + e.getMessage());
                }
            }

            // CRÍTICO: Forçar uso do decoder dav1d para AV1 (muito mais rápido)
            // dav1d é 2-3x mais rápido que libaom-av1 decoder
            try {
                grabber.setVideoCodecName("libdav1d");
                System.out.println("Decoder dav1d configurado para AV1");
            } catch (Exception e) {
                System.out.println("Decoder dav1d não disponível, usando padrão");
            }

            // Threads: usar todos os cores mas limitar para não sobrecarregar
//        int threads = Math.min(Runtime.getRuntime().availableProcessors(), 8);
//        grabber.setVideoOption("threads", String.valueOf(threads));
//        System.out.println("Threads configuradas: " + threads);

        } catch (Exception e) {
            System.out.println("Erro ao configurar aceleração: " + e.getMessage());
        }
    }


    public void togglePlayPause() {
        if (isPlaying) {
            pauseVideo();
        } else {
            playVideo();
        }
    }

    // Método auxiliar para processar samples de áudio (para áudio apenas)
    private byte[] processAudioSamples(ShortBuffer channelSamples) {
        try {
            // Copiar samples para array
            short[] audioSamplesArray = new short[channelSamples.remaining()];
            channelSamples.mark();
            channelSamples.get(audioSamplesArray);
            channelSamples.reset();

            // ===== ANÁLISE E NORMALIZAÇÃO =====
            if (audioNormalizationEnabled && isAudioOnly) {
                // Fase 1: Análise inicial (primeiros N samples)
                if (!normalizationCalculated && normalizationSampleCount < NORMALIZATION_ANALYSIS_SAMPLES) {
                    loudnessAnalyzer.analyzeSamples(audioSamplesArray);
                    normalizationSampleCount++;

                    // IMPORTANTE: APLICAR globalAudioGain MESMO DURANTE ANÁLISE
                    audioSamplesArray = applySimpleGain(audioSamplesArray, globalAudioGain);

                    // Após análise inicial, calcular ganho
                    if (normalizationSampleCount >= NORMALIZATION_ANALYSIS_SAMPLES) {
                        float currentDbFS = loudnessAnalyzer.getDbFS();
                        maxPeakLevel = loudnessAnalyzer.getPeakLevel();

                        System.out.println("=== Análise de Áudio Concluída ===");
                        System.out.println("RMS médio: " + currentDbFS + " dBFS");
                        System.out.println("Pico máximo: " + (20 * Math.log10(maxPeakLevel)) + " dBFS");

                        // Calcular ganho de normalização
                        normalizationGain = calculateNormalizationGain(currentDbFS, targetLoudness);

                        System.out.println("Ganho de normalização: " + (20 * Math.log10(normalizationGain)) + " dB");
                        System.out.println("Fator multiplicador: " + normalizationGain);
                        System.out.println("==================================");

                        normalizationCalculated = true;
                        loudnessAnalyzer.reset();
                    }
                }

                // Fase 2: Aplicar normalização (com ajuste dinâmico contínuo)
                else if (normalizationCalculated) {
                    // Análise contínua para ajuste dinâmico
                    loudnessAnalyzer.analyzeSamples(audioSamplesArray);

                    // Atualizar histórico RMS
                    rmsHistory[rmsHistoryIndex] = loudnessAnalyzer.getRMS();
                    rmsHistoryIndex = (rmsHistoryIndex + 1) % rmsHistory.length;

                    // Calcular RMS médio do histórico
                    float sumRMS = 0.0f;
                    for (float rms : rmsHistory) {
                        sumRMS += rms;
                    }
                    averageRMS = sumRMS / rmsHistory.length;

                    // Ajuste dinâmico suave do ganho (evita mudanças bruscas)
                    float currentDbFS = loudnessAnalyzer.getDbFS();
                    float targetGain = calculateNormalizationGain(currentDbFS, targetLoudness);

                    // Interpolação suave (70% antigo, 30% novo)
                    normalizationGain = normalizationGain * 0.7f + targetGain * 0.3f;

                    loudnessAnalyzer.reset();

                    // Aplicar normalização aos samples
                    audioSamplesArray = applyNormalization(audioSamplesArray, normalizationGain);
                }
            } else {
                // SE NORMALIZAÇÃO DESATIVADA, APLICAR globalAudioGain DIRETAMENTE
                audioSamplesArray = applySimpleGain(audioSamplesArray, globalAudioGain);
            }

            // ===== CALCULAR SPECTRUM (usando samples normalizados) =====
            if (isAudioOnly) {
                float[] spectrum = calculateFFT(audioSamplesArray, audioSamplesArray.length);
                synchronized (spectrumLock) {
                    spectrumData = spectrum;
                }

                SwingUtilities.invokeLater(() -> {
                    videoPanel.updateSpectrum(spectrumData);
                });
            }

            // ===== PROCESSAR PARA REPRODUÇÃO =====
            if (audioChannels > 2) {
                // Downmix multicanal
                channelSamples.rewind();
                int totalSamples = channelSamples.remaining();
                int framesCount = totalSamples / audioChannels;
                byte[] audioBytes = new byte[framesCount * 2 * 2];

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

                    // APENAS volume do slider (globalAudioGain já aplicado acima)
                    left *= volume;
                    right *= volume;
                    left = Math.max(-32768, Math.min(32767, left));
                    right = Math.max(-32768, Math.min(32767, right));

                    short leftShort = (short) left;
                    short rightShort = (short) right;

                    audioBytes[i * 4] = (byte) ((leftShort >> 8) & 0xFF);
                    audioBytes[i * 4 + 1] = (byte) (leftShort & 0xFF);
                    audioBytes[i * 4 + 2] = (byte) ((rightShort >> 8) & 0xFF);
                    audioBytes[i * 4 + 3] = (byte) (rightShort & 0xFF);
                }

                return audioBytes;

            } else {
                // Mono/Stereo (usar samples já normalizados com globalAudioGain)
                byte[] audioBytes = new byte[audioSamplesArray.length * 2];
                for (int i = 0; i < audioSamplesArray.length; i++) {
                    // APENAS volume do slider (globalAudioGain já aplicado acima)
                    short s = (short) (audioSamplesArray[i] * volume);
                    audioBytes[i * 2] = (byte) ((s >> 8) & 0xFF);
                    audioBytes[i * 2 + 1] = (byte) (s & 0xFF);
                }
                return audioBytes;
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar samples: " + e.getMessage());
            return null;
        }
    }

    // NOVO MÉTODO: Aplicar ganho simples sem normalização
    private short[] applySimpleGain(short[] samples, float gain) {
        short[] adjusted = new short[samples.length];

        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i] * gain;

            // Soft clipping para evitar distorção
            if (Math.abs(sample) > 32767) {
                sample = softClip(sample);
            }

            adjusted[i] = (short) Math.max(-32768, Math.min(32767, sample));
        }

        return adjusted;
    }

// ==================== MÉTODO: Calcular FFT ====================

    private float[] calculateFFT(short[] audioSamples, int sampleCount) {
        // Usar apenas potência de 2 para FFT
        int fftSize = 512;
        if (sampleCount < fftSize) {
            fftSize = 256;
        }

        // Preparar dados para FFT
        float[] real = new float[fftSize];
        float[] imag = new float[fftSize];

        // Copiar samples e aplicar janela de Hamming
        for (int i = 0; i < Math.min(sampleCount, fftSize); i++) {
            float hamming = (float) (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (fftSize - 1)));
            real[i] = audioSamples[i] * hamming;
            imag[i] = 0;
        }

        // FFT simples (Cooley-Tukey)
        fft(real, imag);

        // Calcular magnitudes e agrupar em 64 barras
        float[] spectrum = new float[64];
        int samplesPerBar = (fftSize / 2) / 64;

        for (int i = 0; i < 64; i++) {
            float sum = 0;
            for (int j = 0; j < samplesPerBar; j++) {
                int index = i * samplesPerBar + j;
                if (index < fftSize / 2) {
                    float magnitude = (float) Math.sqrt(real[index] * real[index] + imag[index] * imag[index]);
                    sum += magnitude;
                }
            }

            // Normalizar e aplicar escala logarítmica
            float avg = sum / samplesPerBar;
            spectrum[i] = (float) (Math.log10(1 + avg) / 5.0); // Escala log
            spectrum[i] = Math.min(1.0f, spectrum[i]); // Limitar a 1.0
        }

        return spectrum;
    }

    // FFT Cooley-Tukey (in-place)
    private void fft(float[] real, float[] imag) {
        int n = real.length;

        // Bit reversal
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                float tempR = real[i];
                float tempI = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tempR;
                imag[j] = tempI;
            }
            int k = n / 2;
            while (k <= j) {
                j -= k;
                k /= 2;
            }
            j += k;
        }

        // Butterfly computations
        for (int len = 2; len <= n; len *= 2) {
            float angle = (float) (-2 * Math.PI / len);
            float wlenR = (float) Math.cos(angle);
            float wlenI = (float) Math.sin(angle);

            for (int i = 0; i < n; i += len) {
                float wR = 1.0f;
                float wI = 0.0f;

                for (int h = 0; h < len / 2; h++) {
                    float uR = real[i + h];
                    float uI = imag[i + h];
                    float tR = wR * real[i + h + len / 2] - wI * imag[i + h + len / 2];
                    float tI = wR * imag[i + h + len / 2] + wI * real[i + h + len / 2];

                    real[i + h] = uR + tR;
                    imag[i + h] = uI + tI;
                    real[i + h + len / 2] = uR - tR;
                    imag[i + h + len / 2] = uI - tI;

                    float tempR = wR;
                    wR = wR * wlenR - wI * wlenI;
                    wI = tempR * wlenI + wI * wlenR;
                }
            }
        }
    }

    // Método auxiliar para processar áudio de vídeo (mantém lógica original)
    private void processVideoAudioFrame(Frame frame) {
        if (frame.samples == null || audioLine == null) {
            return;
        }

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

                        outBuffer.putShort((short) left);
                        outBuffer.putShort((short) right);
                    }
                } else {
                    outBuffer = ByteBuffer.allocate(channelSamples.remaining() * 2);

                    while (channelSamples.hasRemaining()) {
                        short val = channelSamples.get();
                        val = (short) (val * volume);
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


    public void pauseVideo() {
        isPlaying = false;
        isStopped = false; // NÃO marcar como stopped, apenas pausado
        playPauseButton.setText("▶");

        if (audioLine != null && audioLine.isRunning()) {
            audioLine.stop();
            audioLine.flush();
            videoPanel.spectrumPanel.setPaused(true);
        }

        // Aguardar thread terminar para garantir que currentFrame está correto
        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.join(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Atualizar UI com a posição atual
        SwingUtilities.invokeLater(() -> {
            updateTimeLabel();
            if (totalFrames > 0) {
                int progress = (int) ((currentFrame * 100) / totalFrames);
                progressSlider.setValue(progress);
            }
        });

        System.out.println("Áudio pausado no frame: " + currentFrame);
    }

    private void stopVideo() {
        isPlaying = false;
        isStopped = true;
        playPauseButton.setText("▶");

        if (audioLine != null && audioLine.isRunning()) {
            audioLine.stop();
            audioLine.flush();
            videoPanel.spectrumPanel.setPaused(true);
        }

        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // SEMPRE resetar para o início
        currentFrame = 0;
        progressSlider.setValue(0);
        subtitleManager.setCurrentSubtitleText("");
        updateTimeLabel();

        if (grabber != null) {
            try {
                if (isAudioOnly) {
                    // Para áudio, resetar timestamp
                    System.out.println("Resetando posição do áudio para o início");
                    grabber.setTimestamp(0);
                } else {
                    // Para vídeo, resetar frame e mostrar primeiro frame
                    grabber.setFrameNumber(0);
                    Frame firstFrame = grabber.grabImage();
                    if (firstFrame != null && firstFrame.image != null) {
                        BufferedImage img = converter.convert(firstFrame);
                        if (img != null) {
                            videoPanel.updateImage(img);
                        }
                    }
                    grabber.setFrameNumber(0);
                }
            } catch (Exception e) {
                System.err.println("Erro ao resetar posição: " + e.getMessage());
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
            currentFrame = targetFrame;

            if (isAudioOnly) {
                // Para áudio, usar timestamp em microsegundos
                long targetTimestamp = (long) ((currentFrame / frameRate) * 1000000);
                System.out.println("Seek áudio para frame: " + targetFrame + " (timestamp: " + targetTimestamp + "µs)");
                grabber.setTimestamp(targetTimestamp);

                if (audioLine != null) {
                    audioLine.flush();
                }
            } else {
                // Para vídeo, usar frame number
                grabber.setFrameNumber((int) targetFrame);

                Frame frame = grabber.grabImage();
                if (frame != null && frame.image != null) {
                    BufferedImage img = converter.convert(frame);
                    if (img != null) {
                        videoPanel.updateImage(img);
                    }
                }

                // Atualizar legenda para nova posição
                long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                subtitleManager.updateSubtitle(currentTimeMs, videoPanel);

                if (audioLine != null) {
                    audioLine.flush();
                }
            }

            // Atualizar UI
            updateTimeLabel();
            progressSlider.setValue(percentage);

            if (wasPlaying) {
                Thread.sleep(100);
                playVideo();
            }

        } catch (Exception e) {
            System.err.println("Erro ao fazer seek: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateTimeLabel() {
        if (grabber == null || frameRate == 0) return;

        long currentSeconds = (long) (currentFrame / frameRate);
        long totalSeconds = (long) (totalFrames / frameRate);

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

    private void extractCoverArt(String audioFilePath) {
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


    // ==================== CLASSE INTERNA: AudioSpectrumPanel ====================

//    class AudioSpectrumPanel extends JPanel {
//        private float[] spectrum;
//        private float[] smoothedSpectrum;
//        private float[] peakLevels;
//        // NOVO: Cover art
//        private BufferedImage coverArt;
//        private float coverOpacity = 0.3f; // Opacidade da capa (0.0 a 1.0)
//
//        // Enum para modos de cor
//        public enum ColorMode {
//            DEFAULT,        // Verde -> Amarelo -> Vermelho
//            CUSTOM,         // Cores personalizadas
//            COVER_PALETTE   // Baseado na paleta da capa
//        }
//
//        // Variáveis de cor
//        private ColorMode colorMode = ColorMode.DEFAULT;
//        private Color customBottomColor = new Color(0, 255, 0);    // Verde
//        private Color customMiddleColor = new Color(255, 255, 0);  // Amarelo
//        private Color customTopColor = new Color(255, 0, 0);       // Vermelho
//
//        // Cores extraídas da capa
//        private Color coverBottomColor = new Color(0, 255, 0);
//        private Color coverMiddleColor = new Color(255, 255, 0);
//        private Color coverTopColor = new Color(255, 0, 0);
//
//
//        private static final float SMOOTHING_FACTOR = 0.25f;
//        private static final float GRAVITY = 0.92f;
//        private static final float PEAK_DECAY = 0.02f;
//
//        private Timer animationTimer;
//
//        // Controle de pausa e picos
//        private boolean paused = false;
//        private boolean lockPeaksOnPause = true;
//
//        // Parâmetros visuais ajustáveis
//        private int squareSize = 6;              // altura base dos quadrados
//        private int spacingY = 2;                // espaçamento vertical entre quadrados
//        private int glowRadius = 3;             // raio do brilho difuso
//        private float squareHeightMultiplier = 1.0f;
//        private float barWidthFactor = 1.3f;
//        private int barCount = 50;               // número de colunas
//        private int columnSpacing = 4;           // espaçamento entre colunas
//
//        // Dimensões ajustáveis do painel
//        private int panelWidth = 600;
//        private int panelHeight = 300;
//
//        // Controle de reflexo
//        private boolean showReflection = true;
//        private float reflectionHeight = 0.5f; // Altura do reflexo (0.0 a 1.0)
//        private int reflectionAlpha = 100; // Transparência do reflexo (0 a 255)
//
//        // Variáveis para controle de visibilidade
//        private boolean spectrumVisible = true;
//        private boolean coverArtVisible = true;
//
//// Adicionar na classe AudioSpectrumPanel:
//
//        public void setSpectrumVisible(boolean visible) {
//            this.spectrumVisible = visible;
//            repaint();
//        }
//
//        public void setCoverArtVisible(boolean visible) {
//            this.coverArtVisible = visible;
//            repaint();
//        }
//
//        public boolean isSpectrumVisible() {
//            return spectrumVisible;
//        }
//
//        public boolean isCoverArtVisible() {
//            return coverArtVisible;
//        }
//
//        public void setCoverArt(BufferedImage cover) {
//            this.coverArt = cover;
//
//            // Se modo é COVER_PALETTE, extrair cores da imagem
//            if (cover != null && colorMode == ColorMode.COVER_PALETTE) {
//                extractColorsFromCover(cover);
//            }
//
//            repaint();
//        }
//
//        // Extrai paleta de cores dominantes da imagem
//        private void extractColorsFromCover(BufferedImage image) {
//            if (image == null) return;
//
//            System.out.println("=== Extraindo paleta de cores da capa ===");
//
//            // Redimensionar imagem para análise mais rápida
//            int sampleWidth = Math.min(100, image.getWidth());
//            int sampleHeight = Math.min(100, image.getHeight());
//
//            // Coletar amostras de cores
//            Map<Integer, Integer> colorFrequency = new HashMap<>();
//
//            for (int y = 0; y < sampleHeight; y++) {
//                for (int x = 0; x < sampleWidth; x++) {
//                    int rgb = image.getRGB(
//                            x * image.getWidth() / sampleWidth,
//                            y * image.getHeight() / sampleHeight
//                    );
//
//                    // Quantizar cor (reduzir precisão para agrupar cores similares)
//                    int quantized = quantizeColor(rgb);
//
//                    colorFrequency.put(quantized, colorFrequency.getOrDefault(quantized, 0) + 1);
//                }
//            }
//
//            // Ordenar cores por frequência
//            List<Map.Entry<Integer, Integer>> sortedColors = new ArrayList<>(colorFrequency.entrySet());
//            sortedColors.sort((a, b) -> b.getValue().compareTo(a.getValue()));
//
//            // Extrair cores dominantes (ignorar cores muito escuras ou muito claras)
//            List<Color> dominantColors = new ArrayList<>();
//            for (Map.Entry<Integer, Integer> entry : sortedColors) {
//                Color c = new Color(entry.getKey());
//
//                // Filtrar cores muito escuras (preto) ou muito claras (branco)
//                int brightness = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
//                if (brightness > 30 && brightness < 225) {
//                    dominantColors.add(c);
//                    if (dominantColors.size() >= 5) break;
//                }
//            }
//
//            if (dominantColors.size() >= 3) {
//                // Escolher cores baseadas em saturação e brilho
//                Color[] paletteColors = selectPaletteColors(dominantColors);
//
//                coverBottomColor = paletteColors[0];
//                coverMiddleColor = paletteColors[1];
//                coverTopColor = paletteColors[2];
//
//                System.out.println("Paleta extraída:");
//                System.out.println("  Bottom: " + colorToString(coverBottomColor));
//                System.out.println("  Middle: " + colorToString(coverMiddleColor));
//                System.out.println("  Top: " + colorToString(coverTopColor));
//                System.out.println("======================================");
//
//            } else {
//                // Fallback para cores padrão
//                System.out.println("Paleta insuficiente, usando cores padrão");
//                coverBottomColor = new Color(0, 255, 0);
//                coverMiddleColor = new Color(255, 255, 0);
//                coverTopColor = new Color(255, 0, 0);
//            }
//        }
//
//        // Quantizar cor (agrupar cores similares)
//        private int quantizeColor(int rgb) {
//            int r = (rgb >> 16) & 0xFF;
//            int g = (rgb >> 8) & 0xFF;
//            int b = rgb & 0xFF;
//
//            // Reduzir precisão (ex: 256 níveis -> 32 níveis)
//            r = (r / 32) * 32;
//            g = (g / 32) * 32;
//            b = (b / 32) * 32;
//
//            return (r << 16) | (g << 8) | b;
//        }
//
//        // Selecionar 3 cores da paleta baseado em critérios
//        private Color[] selectPaletteColors(List<Color> dominantColors) {
//            Color[] result = new Color[3];
//
//            // Ordenar por saturação (cores mais vibrantes primeiro)
//            dominantColors.sort((a, b) -> {
//                float satA = getSaturation(a);
//                float satB = getSaturation(b);
//                return Float.compare(satB, satA);
//            });
//
//            // Pegar cores com saturações diferentes
//            if (dominantColors.size() >= 3) {
//                result[0] = dominantColors.get(0); // Mais saturada (bottom)
//                result[1] = adjustBrightness(dominantColors.get(1), 1.2f); // Média (middle - mais clara)
//                result[2] = adjustBrightness(dominantColors.get(2), 1.4f); // Alta (top - muito clara)
//            } else {
//                result[0] = dominantColors.get(0);
//                result[1] = adjustBrightness(dominantColors.get(0), 1.2f);
//                result[2] = adjustBrightness(dominantColors.get(0), 1.5f);
//            }
//
//            return result;
//        }
//
//        // Calcular saturação de uma cor
//        private float getSaturation(Color c) {
//            float r = c.getRed() / 255.0f;
//            float g = c.getGreen() / 255.0f;
//            float b = c.getBlue() / 255.0f;
//
//            float max = Math.max(r, Math.max(g, b));
//            float min = Math.min(r, Math.min(g, b));
//
//            if (max == 0) return 0;
//            return (max - min) / max;
//        }
//
//        // Ajustar brilho de uma cor
//        private Color adjustBrightness(Color c, float factor) {
//            int r = Math.min(255, (int) (c.getRed() * factor));
//            int g = Math.min(255, (int) (c.getGreen() * factor));
//            int b = Math.min(255, (int) (c.getBlue() * factor));
//            return new Color(r, g, b);
//        }
//
//        // Converter cor para string
//        private String colorToString(Color c) {
//            return String.format("RGB(%d, %d, %d)", c.getRed(), c.getGreen(), c.getBlue());
//        }
//
//        // NOVO: Método para ajustar opacidade da capa
//        public void setCoverOpacity(float opacity) {
//            this.coverOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
//            repaint();
//        }
//
//        // Métodos para controlar o reflexo
//        public void setShowReflection(boolean show) {
//            this.showReflection = show;
//        }
//
//        public void setReflectionHeight(float height) {
//            this.reflectionHeight = Math.max(0.0f, Math.min(1.0f, height));
//        }
//
//        public void setReflectionAlpha(int alpha) {
//            this.reflectionAlpha = Math.max(0, Math.min(255, alpha));
//        }
//
//        public AudioSpectrumPanel() {
//            initArrays(barCount);
//
//            setBackground(Color.BLACK);
//            setOpaque(true);
//            setPreferredSize(new Dimension(panelWidth, panelHeight));
//
//            // Atualização ~60 FPS
//            animationTimer = new Timer(16, e -> {
//                for (int i = 0; i < spectrum.length; i++) {
//                    // suavização + gravidade
//                    smoothedSpectrum[i] = smoothedSpectrum[i] * (1 - SMOOTHING_FACTOR)
//                            + spectrum[i] * SMOOTHING_FACTOR;
//                    smoothedSpectrum[i] *= GRAVITY;
//
//                    // pico (peak hold)
//                    if (smoothedSpectrum[i] > peakLevels[i]) {
//                        peakLevels[i] = smoothedSpectrum[i];
//                    } else if (!(paused && lockPeaksOnPause)) {
//                        peakLevels[i] = Math.max(0f, peakLevels[i] - PEAK_DECAY);
//                    }
//                }
//                repaint();
//            });
//            animationTimer.start();
//        }
//
//        // ==================== MÉTODOS DE CONFIGURAÇÃO ====================
//
//        private void initArrays(int count) {
//            spectrum = new float[count];
//            smoothedSpectrum = new float[count];
//            peakLevels = new float[count];
//        }
//
//        public void setBarCount(int count) {
//            this.barCount = Math.max(8, count);
//            initArrays(this.barCount);
//        }
//
//        public void setPaused(boolean paused) {
//            this.paused = paused;
//        }
//
//        public void setLockPeaksOnPause(boolean lock) {
//            this.lockPeaksOnPause = lock;
//        }
//
//        public void setSquareSize(int size) {
//            this.squareSize = Math.max(2, size);
//        }
//
//        public void setSquareHeightMultiplier(float multiplier) {
//            this.squareHeightMultiplier = Math.max(0.5f, multiplier);
//        }
//
//        public void setBarWidthFactor(float factor) {
//            this.barWidthFactor = Math.max(0.5f, factor);
//        }
//
//        public void setGlowRadius(int radius) {
//            this.glowRadius = Math.max(0, radius);
//        }
//
//        public void setColumnSpacing(int spacing) {
//            this.columnSpacing = Math.max(0, spacing);
//        }
//
//        /**
//         * Define largura e altura do painel (método dinâmico)
//         */
//        public void setPanelSize(int width, int height) {
//            this.panelWidth = Math.max(100, width);
//            this.panelHeight = Math.max(100, height);
//
//            // Definir bounds diretamente (funciona com null layout)
//            setBounds(getX(), getY(), panelWidth, panelHeight);
//
//            System.out.println("Spectrum size alterado para: " + panelWidth + "x" + panelHeight);
//            repaint();
//        }
//
//        /**
//         * Retorna a largura atual configurada
//         */
//        public int getPanelWidth() {
//            return panelWidth;
//        }
//
//        /**
//         * Retorna a altura atual configurada
//         */
//        public int getPanelHeight() {
//            return panelHeight;
//        }
//
//        public void updateSpectrum(float[] newSpectrum) {
//            if (newSpectrum != null) {
//                int len = Math.min(newSpectrum.length, spectrum.length);
//                System.arraycopy(newSpectrum, 0, spectrum, 0, len);
//            }
//        }
//
//        @Override
//        protected void paintComponent(Graphics g) {
//            super.paintComponent(g);
//            Graphics2D g2d = (Graphics2D) g.create();
//            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//
//            int width = getWidth();
//            int height = getHeight();
//
//            // ===== DESENHAR COVER ART NO FUNDO (SE EXISTIR) =====
//            if (coverArt != null && coverArtVisible) {
//                drawCoverArt(g2d, width, height);
//            }
//            // ===== DESENHAR SPECTRUM (SE VISÍVEL) =====
//            if (spectrumVisible) {
//                int barWidth = Math.max(1, (int) (((width - (barCount + 1) * columnSpacing) / (float) barCount) * barWidthFactor));
//
//                // Dividir altura: metade para spectrum original, metade para reflexo
//                int titleHeight = 80;
//                int baseLineY = showReflection ? (height - titleHeight) / 2 + titleHeight : height - 20;
//                int maxBarHeight = baseLineY - titleHeight - 20;
//
//
//                // ===== Desenhar Barras ORIGINAIS =====
//                drawBars(g2d, width, barWidth, maxBarHeight, baseLineY, false);
//
//                // ===== Linha de base (linha d'água) =====
//                if (showReflection) {
//                    // Linha mais destacada quando há reflexo
//                    g2d.setColor(new Color(100, 150, 255, 120));
//                    g2d.fillRect(0, baseLineY, width, 3);
//
//                    // Efeito de brilho na linha d'água
//                    GradientPaint waterGlow = new GradientPaint(
//                            0, baseLineY - 10, new Color(100, 150, 255, 0),
//                            0, baseLineY, new Color(100, 150, 255, 80)
//                    );
//                    g2d.setPaint(waterGlow);
//                    g2d.fillRect(0, baseLineY - 10, width, 10);
//                } else {
//                    g2d.setColor(new Color(255, 255, 255, 50));
//                    g2d.fillRect(0, baseLineY, width, 2);
//                }
//
//                // ===== Desenhar REFLEXO =====
//                if (showReflection) {
//                    drawReflection(g2d, width, barWidth, maxBarHeight, baseLineY);
//                } else if (!coverArtVisible) {
//                    // ===== Título =====
//                    g2d.setColor(new Color(255, 255, 255, 100));
//                    g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
//                    String title = "Audio Spectrum Analyzer";
//                    FontMetrics fm = g2d.getFontMetrics();
//                    int titleWidth = fm.stringWidth(title);
//                    g2d.drawString(title, (width - titleWidth) / 2, 50);
//                }
//            }
//            g2d.dispose();
//        }
//
//        // ===== NOVO MÉTODO: Desenhar as barras =====
//        private void drawBars(Graphics2D g2d, int width, int barWidth, int maxBarHeight, int baseY, boolean isReflection) {
//            for (int i = 0; i < barCount; i++) {
//                int x = columnSpacing + i * (barWidth + columnSpacing);
//                int barHeight = (int) (smoothedSpectrum[i] * maxBarHeight);
//                barHeight = Math.max(squareSize, barHeight);
//
//                int adjustedSquareHeight = (int) (squareSize * squareHeightMultiplier);
//                int numSquares = barHeight / (adjustedSquareHeight + spacingY);
//                if (numSquares < 1) numSquares = 1;
//
//                int startY = isReflection ?
//                        baseY + 3 : // Reflexo começa após a linha d'água
//                        baseY - (numSquares * (adjustedSquareHeight + spacingY)); // Original
//
//                float intensity = Math.min(1.0f, smoothedSpectrum[i] * 1.5f);
//
//                // === Cor adaptada à altura ===
//                float avgPos = (float) barHeight / maxBarHeight;
//                Color glowColor = getColorForPosition(avgPos);
//
//                // Transparência para reflexo
//                float alphaMultiplier = isReflection ? (reflectionAlpha / 255.0f) : 1.0f;
//
//                // === Brilho difuso ===
//                for (int glow = glowRadius; glow > 0; glow--) {
//                    float alpha = (float) glow / glowRadius;
//                    int glowAlpha = (int) (40 * alpha * intensity * alphaMultiplier);
//                    g2d.setColor(new Color(
//                            glowColor.getRed(),
//                            glowColor.getGreen(),
//                            glowColor.getBlue(),
//                            Math.max(0, glowAlpha)
//                    ));
//
//                    int yPos = isReflection ? startY - glow / 2 : baseY - barHeight - glow / 2;
//                    g2d.fillRoundRect(x - glow / 2, yPos, barWidth + glow, barHeight + glow, 6, 6);
//                }
//
//                // === Quadradinhos ===
//                int reflectionSquares = isReflection ? (int) (numSquares * reflectionHeight) : numSquares;
//
//                for (int j = 0; j < reflectionSquares; j++) {
//                    int y = startY + j * (adjustedSquareHeight + spacingY);
//
//                    // Para reflexo: inverter gradiente e adicionar fade gradual
//                    float posRatio;
//                    if (isReflection) {
//                        posRatio = (float) j / (float) (reflectionSquares - 1);
//                        // Fade adicional: mais transparente quanto mais longe da linha d'água
//                        alphaMultiplier *= (1.0f - (j / (float) reflectionSquares) * 0.6f);
//                    } else {
//                        posRatio = (float) (numSquares - 1 - j) / (float) (numSquares - 1);
//                    }
//
//                    Color baseColor = getColorForPosition(posRatio);
//
//                    int r = Math.min(255, (int) (baseColor.getRed() * (0.6f + 0.4f * intensity)));
//                    int gC = Math.min(255, (int) (baseColor.getGreen() * (0.6f + 0.4f * intensity)));
//                    int b = Math.min(255, (int) (baseColor.getBlue() * (0.6f + 0.4f * intensity)));
//                    int alpha = Math.min(255, (int) ((200 + 55 * intensity) * alphaMultiplier));
//
//                    g2d.setColor(new Color(r, gC, b, alpha));
//                    g2d.fillRect(x, y, barWidth, adjustedSquareHeight);
//
//                    // borda sutil
//                    g2d.setColor(new Color(0, 0, 0, (int) (60 * alphaMultiplier)));
//                    g2d.drawRect(x, y, barWidth, adjustedSquareHeight);
//                }
//
//                // === Marcador de pico (apenas no original, não no reflexo) ===
//                if (!isReflection) {
//                    int adjustedSquareHeightForPeak = (int) (squareSize * squareHeightMultiplier);
//                    int peakY = baseY - (int) (peakLevels[i] * maxBarHeight) - adjustedSquareHeightForPeak - spacingY;
//                    peakY = Math.max(10, Math.min(baseY - adjustedSquareHeightForPeak, peakY));
//
//                    g2d.setColor(new Color(255, 80, 80, (int) (180 + 60 * intensity)));
//                    g2d.fillRect(x, peakY, barWidth, adjustedSquareHeightForPeak);
//                    g2d.setColor(new Color(0, 0, 0, 80));
//                    g2d.drawRect(x, peakY, barWidth, adjustedSquareHeightForPeak);
//                }
//            }
//        }
//
//        // ===== NOVO MÉTODO: Desenhar reflexo =====
//        private void drawReflection(Graphics2D g2d, int width, int barWidth, int maxBarHeight, int baseY) {
//            // Criar composição para efeito de água
//            Composite originalComposite = g2d.getComposite();
//
//            // Efeito de ondulação sutil (opcional)
//            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
//
//            // Desenhar barras refletidas
//            drawBars(g2d, width, barWidth, (int) (maxBarHeight * reflectionHeight), baseY, true);
//
//            // Gradiente de fade de cima para baixo no reflexo
//            int reflectionAreaHeight = (int) (maxBarHeight * reflectionHeight) + 50;
//            GradientPaint fadeGradient = new GradientPaint(
//                    0, baseY + 3, new Color(0, 0, 0, 0),
//                    0, baseY + reflectionAreaHeight, new Color(0, 0, 0, 150)
//            );
//            g2d.setPaint(fadeGradient);
//            g2d.fillRect(0, baseY + 3, width, reflectionAreaHeight);
//
//            g2d.setComposite(originalComposite);
//        }
//
//        //** Gradiente baseado no modo de cor selecionado (0 = base, 1 = topo) */
//        private Color getColorForPosition(float posRatio) {
//            posRatio = Math.max(0, Math.min(1, posRatio));
//
//            Color bottomColor, middleColor, topColor;
//
//            // Selecionar paleta baseada no modo
//            switch (colorMode) {
//                case CUSTOM:
//                    bottomColor = customBottomColor;
//                    middleColor = customMiddleColor;
//                    topColor = customTopColor;
//                    break;
//
//                case COVER_PALETTE:
//                    bottomColor = coverBottomColor;
//                    middleColor = coverMiddleColor;
//                    topColor = coverTopColor;
//                    break;
//
//                case DEFAULT:
//                default:
//                    // Verde -> Amarelo -> Vermelho (padrão)
//                    if (posRatio < 0.5f) {
//                        float localRatio = posRatio * 2.0f;
//                        return new Color((int) (255 * localRatio), 255, 0);
//                    } else {
//                        float localRatio = (posRatio - 0.5f) * 2.0f;
//                        return new Color(255, (int) (255 * (1 - localRatio)), 0);
//                    }
//            }
//
//            // Interpolar entre as 3 cores personalizadas
//            if (posRatio < 0.5f) {
//                // Bottom -> Middle
//                float localRatio = posRatio * 2.0f;
//                return interpolateColor(bottomColor, middleColor, localRatio);
//            } else {
//                // Middle -> Top
//                float localRatio = (posRatio - 0.5f) * 2.0f;
//                return interpolateColor(middleColor, topColor, localRatio);
//            }
//        }
//
//        private Color interpolateColor(Color c1, Color c2, float ratio) {
//            int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * ratio);
//            int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio);
//            int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio);
//            return new Color(
//                    Math.max(0, Math.min(255, r)),
//                    Math.max(0, Math.min(255, g)),
//                    Math.max(0, Math.min(255, b))
//            );
//        }
//        // ==================== MÉTODOS PÚBLICOS PARA CONTROLE ====================
//
//        public void setColorMode(ColorMode mode) {
//            this.colorMode = mode;
//            System.out.println("Modo de cor alterado para: " + mode);
//
//            // Se mudar para COVER_PALETTE, extrair cores da capa atual
//            if (mode == ColorMode.COVER_PALETTE && coverArt != null) {
//                extractColorsFromCover(coverArt);
//            }
//
//            repaint();
//        }
//
//        public ColorMode getColorMode() {
//            return colorMode;
//        }
//
//        public void setCustomColors(Color bottom, Color middle, Color top) {
//            this.customBottomColor = bottom;
//            this.customMiddleColor = middle;
//            this.customTopColor = top;
//            System.out.println("Cores personalizadas definidas:");
//            System.out.println("  Bottom: " + colorToString(bottom));
//            System.out.println("  Middle: " + colorToString(middle));
//            System.out.println("  Top: " + colorToString(top));
//
//            // Se já está em modo CUSTOM, repintar
//            if (colorMode == ColorMode.CUSTOM) {
//                repaint();
//            }
//        }
//
//        public Color getCustomBottomColor() {
//            return customBottomColor;
//        }
//
//        public Color getCustomMiddleColor() {
//            return customMiddleColor;
//        }
//
//        public Color getCustomTopColor() {
//            return customTopColor;
//        }
//
//        // NOVO: Método para desenhar cover art no fundo
//        private void drawCoverArt(Graphics2D g2d, int panelWidth, int panelHeight) {
//            if (coverArt == null) return;
//
//            // Salvar composição original
//            Composite originalComposite = g2d.getComposite();
//
//            // Calcular dimensões mantendo aspect ratio e centralizando
//            int imgWidth = coverArt.getWidth();
//            int imgHeight = coverArt.getHeight();
//
//            double scaleX = (double) panelWidth / imgWidth;
//            double scaleY = (double) panelHeight / imgHeight;
//            double scale = Math.min(scaleX, scaleY);
//
//            int scaledWidth = (int) (imgWidth * scale);
//            int scaledHeight = (int) (imgHeight * scale);
//            int x = (panelWidth - scaledWidth) / 2;
//            int y = (panelHeight - scaledHeight) / 2;
//
//            // Aplicar opacidade
//            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, coverOpacity));
//
//            // Desenhar cover art
//            g2d.drawImage(coverArt, x, y, scaledWidth, scaledHeight, null);
//
//            // Opcional: Adicionar blur/desfoque nas bordas para efeito mais suave
//            // Desenhar gradiente escuro nas bordas para destacar o spectrum
//            GradientPaint topGradient = new GradientPaint(
//                    0, 0, new Color(0, 0, 0, 180),
//                    0, panelHeight / 4, new Color(0, 0, 0, 0)
//            );
//            g2d.setPaint(topGradient);
//            g2d.fillRect(0, 0, panelWidth, panelHeight / 4);
//
//            GradientPaint bottomGradient = new GradientPaint(
//                    0, panelHeight * 3 / 4, new Color(0, 0, 0, 0),
//                    0, panelHeight, new Color(0, 0, 0, 180)
//            );
//            g2d.setPaint(bottomGradient);
//            g2d.fillRect(0, panelHeight * 3 / 4, panelWidth, panelHeight / 4);
//
//            // Restaurar composição
//            g2d.setComposite(originalComposite);
//        }
//    }
// ==================== CLASSE PARA ANÁLISE DE LOUDNESS ====================

    class AudioLoudnessAnalyzer {
        private float runningSum = 0.0f;
        private int sampleCount = 0;
        private float peakLevel = 0.0f;

        public void analyzeSamples(short[] samples) {
            float sum = 0.0f;
            float peak = 0.0f;

            for (short sample : samples) {
                float normalized = sample / 32768.0f; // Normalizar para -1.0 a 1.0
                sum += normalized * normalized; // RMS
                peak = Math.max(peak, Math.abs(normalized));
            }

            runningSum += sum;
            sampleCount += samples.length;
            peakLevel = Math.max(peakLevel, peak);
        }

        public float getRMS() {
            if (sampleCount == 0) return 0.0f;
            return (float) Math.sqrt(runningSum / sampleCount);
        }

        public float getPeakLevel() {
            return peakLevel;
        }

        public float getDbFS() {
            float rms = getRMS();
            if (rms <= 0.0f) return -100.0f;
            return 20.0f * (float) Math.log10(rms);
        }

        public void reset() {
            runningSum = 0.0f;
            sampleCount = 0;
            peakLevel = 0.0f;
        }
    }
// ==================== MÉTODO PARA CALCULAR GANHO DE NORMALIZAÇÃO ====================

    private float calculateNormalizationGain(float currentDbFS, float targetDbFS) {
        // Calcular diferença em dB
        float dbDifference = targetDbFS - currentDbFS;

        // Converter dB para ganho linear
        float gain = (float) Math.pow(10.0, dbDifference / 20.0);

        // LIMITES MAIS CONSERVADORES para evitar volume alto
        // Máximo +6dB de ganho, mínimo -30dB de atenuação
        gain = Math.max(0.03f, Math.min(2.0f, gain));

        // APLICAR GANHO GLOBAL ADICIONAL
        gain *= globalAudioGain;

        return gain;
    }
    // ==================== MÉTODO PARA APLICAR NORMALIZAÇÃO ====================

    private short[] applyNormalization(short[] samples, float gain) {
        short[] normalized = new short[samples.length];

        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i] * gain;

            // Soft clipping para evitar distorção severa
            if (Math.abs(sample) > 32767) {
                sample = softClip(sample);
            }

            normalized[i] = (short) Math.max(-32768, Math.min(32767, sample));
        }

        return normalized;
    }

    // Soft clipping suave para evitar distorção
    private float softClip(float sample) {
        float threshold = 32767 * 0.9f; // 90% do máximo

        if (Math.abs(sample) <= threshold) {
            return sample;
        }

        // Aplicar função de clipping suave (tanh)
        float sign = Math.signum(sample);
        float abs = Math.abs(sample);
        float normalized = abs / 32768.0f;
        float clipped = (float) Math.tanh(normalized * 1.5) * 32767;

        return sign * clipped;
    }

// ==================== MÉTODOS PÚBLICOS PARA CONTROLE ====================

    public void setAudioNormalizationEnabled(boolean enabled) {
        this.audioNormalizationEnabled = enabled;
        System.out.println("Normalização de áudio: " + (enabled ? "ATIVADA" : "DESATIVADA"));

        if (!enabled) {
            normalizationGain = 1.0f;
        }
    }

    public void setTargetLoudness(float targetDbFS) {
        this.targetLoudness = Math.max(-30.0f, Math.min(0.0f, targetDbFS));
        System.out.println("Target loudness definido: " + this.targetLoudness + " dBFS");

        // Recalcular ganho se já analisado
        if (normalizationCalculated) {
            float currentDbFS = loudnessAnalyzer.getDbFS();
            normalizationGain = calculateNormalizationGain(currentDbFS, this.targetLoudness);
        }
    }

    public boolean isAudioNormalizationEnabled() {
        return audioNormalizationEnabled;
    }

    public float getCurrentNormalizationGain() {
        return normalizationGain;
    }

    public String getNormalizationInfo() {
        if (!audioNormalizationEnabled) {
            return "Normalização: Desativada | Volume: " + (int) (globalAudioGain * 100) + "%";
        }

        if (!normalizationCalculated) {
            return "Normalização: Analisando... (" + normalizationSampleCount + "/" + NORMALIZATION_ANALYSIS_SAMPLES + ")";
        }

        float gainDb = 20.0f * (float) Math.log10(normalizationGain);
        return String.format("Normalização: %.1f dB | Volume: %d%% | Pico: %.1f dBFS",
                gainDb,
                (int) (globalAudioGain * 100),
                20 * Math.log10(maxPeakLevel));
    }
    // ==================== PRESETS DE LOUDNESS ====================

    public enum LoudnessPreset {
        STREAMING(-14.0f, "Streaming (Spotify/YouTube)"),
        BROADCAST(-23.0f, "Broadcast TV/Radio"),
        CINEMA(-11.0f, "Cinema"),
        LOUD(-11.0f, "Loud (Club/Party)"),
        QUIET(-18.0f, "Quiet (Background)");

        private final float dbFS;
        private final String description;

        LoudnessPreset(float dbFS, String description) {
            this.dbFS = dbFS;
            this.description = description;
        }

        public float getDbFS() {
            return dbFS;
        }

        public String getDescription() {
            return description;
        }
    }

    // Aplicar preset
    public void applyLoudnessPreset(LoudnessPreset preset) {
        setTargetLoudness(preset.getDbFS());
        System.out.println("Preset aplicado: " + preset.getDescription());
    }

    // NOVO: Controle de ganho global (volume master do áudio)
    public void setGlobalAudioGain(float gain) {
        this.globalAudioGain = Math.max(0.01f, Math.min(1.0f, gain));
        System.out.println("Ganho global de áudio: " + (int) (this.globalAudioGain * 100) + "%");
    }

    public float getGlobalAudioGain() {
        return globalAudioGain;
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
            FlatDarkFlatIJTheme.setup();
            UIManager.put("Button.arc", 999);
            VideoPlayer player = new VideoPlayer();
            player.setVisible(true);
        });
    }
}