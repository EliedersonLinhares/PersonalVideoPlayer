package com.esl.videoplayer;


import com.esl.videoplayer.File.FileManager;
import com.esl.videoplayer.Image.ImageExecution;
import com.esl.videoplayer.Video.AudioStreams;
import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.Video.VideoExecution;
import com.esl.videoplayer.audio.AudioExecution;
import com.esl.videoplayer.configuration.RecentFilesManager;
import com.esl.videoplayer.Video.ScreenMode;
import com.esl.videoplayer.configuration.ConfigManager;
import com.esl.videoplayer.configuration.ConfigurationFrame;
import com.esl.videoplayer.configuration.VideoProgressManager;
import com.esl.videoplayer.localization.I18N;
import com.esl.videoplayer.playlist.PlayListExecution;
import com.esl.videoplayer.theme.ThemeManager;
import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.audio.AudioLoudnessAnalyzer;
import com.esl.videoplayer.audio.AudioLoudnessManager;
import com.esl.videoplayer.audio.cover.CoverArt;
import com.esl.videoplayer.capture.CaptureFrameManager;
import com.esl.videoplayer.filters.FiltersManager;
import com.esl.videoplayer.playlist.PlaylistItem;
import com.esl.videoplayer.playlist.PlaylistManager;
import com.esl.videoplayer.subtitle.SubtitleEntry;
import com.esl.videoplayer.subtitle.SubtitleManager;

import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.List;

public class VideoPlayer extends JFrame implements I18N.LanguageChangeListener {

    private final MainPanel mainPanel;
    private static FFmpegFrameGrabber grabber;
    private final Java2DFrameConverter converter;
    private volatile boolean isPlaying = false;
    private String videoFilePath;
    // Adicionar no início da classe, junto com outras variáveis de instância
    private final String ffmpegPath = new File("lib/ffmpeg/bin/ffmpeg.exe").getAbsolutePath();
    private final String ffprobePath = new File("lib/ffmpeg/bin/ffprobe.exe").getAbsolutePath();
    private final PlaylistManager playlistManager;
    private SubtitleManager subtitleManager;
    private AudioLoudnessManager audioLoudnessManager;
    private AudioLoudnessAnalyzer loudnessAnalyzer;
    private CaptureFrameManager captureFrameManager;
    private FiltersManager filtersManager;
    private CoverArt coverArt;
    private ScreenMode screenMode;
    private RecentFilesManager recentFilesManager;
    private ThemeManager themeManager;
    private ConfigManager configManager;
    private VideoProgressManager videoProgressManager;
    private JPanel controlPanel;
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
    private JButton configButton;
    private Thread playbackThread;
    private volatile boolean isStopped = true;
    private volatile boolean isSeeking = false;
    private SourceDataLine audioLine;
    private int audioChannels;
    private int sampleRate;
    private float volume = 1.0f;
    private float previousVolume = 1.0f; // Guardar volume anterior ao mutar
    private boolean isMuted = false;
    private long totalFrames;
    private double frameRate;
    private long currentFrame = 0;

    // Variáveis de instância para salvar estado completo
    private Rectangle normalBounds = null;
    private String currentVideoPath = null;
    private long savedFramePosition = 0;
    private boolean savedPlayingState = false;
    private List<SubtitleEntry> savedSubtitles = null;
    private int savedSubtitleStream = -1;
    private int savedAudioStream = 0; // NOVO: Salvar stream de áudio
    // Adicionar variável de instância
    private Map<Integer, String> savedAudioStreamNames = new HashMap<>();
    private Map<Integer, String> savedSubtitleStreamNames = new HashMap<>();

    // No início da classe, junto com outras variáveis:
    private JButton rewindButton;
    private JButton forwardButton;
    private JButton nextFrameButton;
    private JButton captureFrameButton;
    private JButton captureAllFrameButton;

    // Adicionar variável de instância para controlar se é áudio ou vídeo
    private boolean isAudioOnly = false;
    //Variaveis para exiber a resoluçao atual da tela
    private int screenWidth;
    private int screenHeight;

    public PlayListExecution playListExecution;
    private ImageExecution imageExecution;
    private AudioExecution audioExecution;
    private AudioStreams audioStreams;
    private VideoExecution videoExecution;
    private FileManager fileManager;

    public FFmpegFrameGrabber getGrabber() {return grabber;}
    public void setGrabber(FFmpegFrameGrabber grabber) {this.grabber = grabber;}
    public Java2DFrameConverter getConverter() {return converter;}
    public AudioStreams getAudioStreams() {
        return audioStreams;
    }
    public MainPanel getMainPanel() {return mainPanel;}
    public boolean isPlaying() {return isPlaying;}
    public void setPlaying(boolean playing) {isPlaying = playing;}
    public String getVideoFilePath() {return videoFilePath;}
    public void setVideoFilePath(String videoFilePath) {this.videoFilePath = videoFilePath;}
    public String getFfmpegPath() {return ffmpegPath;}
    public String getFfprobePath() {return ffprobePath;}
    public PlaylistManager getPlaylistManager() {return playlistManager;}

    public SubtitleManager getSubtitleManager() {return subtitleManager;}
    public AudioLoudnessManager getAudioLoudnessManager() {return audioLoudnessManager;}
    public AudioLoudnessAnalyzer getLoudnessAnalyzer() {return loudnessAnalyzer;}
    public FiltersManager getFiltersManager() {return filtersManager;}
    public CoverArt getCoverArt() {return coverArt;}
    public RecentFilesManager getRecentFilesManager() {return recentFilesManager;}

    public JButton getPlayPauseButton() {return playPauseButton;}
    public JSlider getProgressSlider() {return progressSlider;}
    public JSlider getVolumeSlider() {return volumeSlider;}
    public JButton getOpenButton() {return openButton;}
    public JButton getVolumeButton() {return volumeButton;}
    public JButton getRewindButton() {return rewindButton;}
    public JButton getForwardButton() {return forwardButton;}
    public JButton getNextFrameButton() {return nextFrameButton;}
    public JButton getCaptureFrameButton() {return captureFrameButton;}
    public JButton getCaptureAllFrameButton() {return captureAllFrameButton;}
    public JButton getStopButton() {return stopButton;}

    public Thread getPlaybackThread() {return playbackThread;}
    public void setPlaybackThread(Thread playbackThread) {this.playbackThread = playbackThread;}
    public void setStopped(boolean stopped) {isStopped = stopped;}
    public boolean isSeeking() {return isSeeking;}
    public SourceDataLine getAudioLine() {return audioLine;}
    public void setAudioLine(SourceDataLine audioLine) {this.audioLine = audioLine;}
    public int getAudioChannels() {return audioChannels;}
    public void setAudioChannels(int audioChannels) {this.audioChannels = audioChannels;}
    public int getSampleRate() {return sampleRate;}
    public void setSampleRate(int sampleRate) {this.sampleRate = sampleRate;}
    public long getTotalFrames() {return totalFrames;}
    public void setTotalFrames(long totalFrames) {this.totalFrames = totalFrames;}
    public double getFrameRate() {return frameRate;}

    public void setFrameRate(double frameRate) {this.frameRate = frameRate;}
    public long getCurrentFrame() {return currentFrame;}
    public void setCurrentFrame(long currentFrame) {this.currentFrame = currentFrame;}
    public void setCurrentVideoPath(String currentVideoPath) {this.currentVideoPath = currentVideoPath;}
    public Map<Integer, String> getSavedAudioStreamNames() {return savedAudioStreamNames;}
    public Map<Integer, String> getSavedSubtitleStreamNames() {return savedSubtitleStreamNames;}

    public void setAudioOnly(boolean audioOnly) {isAudioOnly = audioOnly;}
    public int getScreenWidth() {return screenWidth;}
    public int getScreenHeight() {return screenHeight;}

    public CaptureFrameManager getCaptureFrameManager() {return captureFrameManager;}
    public VideoProgressManager getVideoProgressManager() {return videoProgressManager;}
    public VideoExecution getVideoExecution() {return videoExecution;}
    public AudioExecution getAudioExecution() {return audioExecution;}
    public ImageExecution getImageExecution() {return imageExecution;}
    public FileManager getFileManager() {return fileManager;}

    public VideoPlayer() {
        setTitle("Media Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(700, 500);
        setLocationRelativeTo(null);

        try {
            List<Image> icons = new ArrayList<>();
            icons.add(new ImageIcon(Objects.requireNonNull(getClass().getResource("/img/icone16.png"))).getImage());
            icons.add(new ImageIcon(Objects.requireNonNull(getClass().getResource("/img/icone32.png"))).getImage());
            icons.add(new ImageIcon(Objects.requireNonNull(getClass().getResource("/img/icone64.png"))).getImage());
            icons.add(new ImageIcon(Objects.requireNonNull(getClass().getResource("/img/icone128.png"))).getImage());
            setIconImages(icons); // Use setIconImages (plural)
        } catch (NullPointerException e) {
            JOptionPane.showMessageDialog(this, I18N.get("videoPlayer.icons.showMessageDialog.text") + " " + e.getMessage());
        }
        captureFrameManager = new CaptureFrameManager(this);
        filtersManager = new FiltersManager();
        subtitleManager = new SubtitleManager();
        loudnessAnalyzer = new AudioLoudnessAnalyzer();
        audioLoudnessManager = new AudioLoudnessManager();
        coverArt = new CoverArt();
        screenMode = new ScreenMode();

        playlistManager = new PlaylistManager();
        mainPanel = new MainPanel(grabber, subtitleManager, this, audioLoudnessManager, playlistManager);
        playListExecution = new PlayListExecution(mainPanel,this, playlistManager);
        recentFilesManager = new RecentFilesManager();
        themeManager = new ThemeManager();
        configManager = new ConfigManager();
        videoProgressManager = new VideoProgressManager();
        imageExecution = new ImageExecution(this);
        audioExecution = new AudioExecution(this);
        audioStreams = new AudioStreams(this);
        videoExecution = new VideoExecution(this);
        fileManager = new FileManager(this);

        // DEBUG: Verificar estado inicial
        // recentFilesManager.printDebugInfo();
        mainPanel.setRecentFilesManager(recentFilesManager);
        mainPanel.setThemeManager(themeManager);

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

        // IMPORTANTE: Atualizar textos pela primeira vez
        updateTexts();

        // IMPORTANTE: Registrar listener APÓS criar todos os componentes
        I18N.addLanguageChangeListener(this);

        // NOVO: Adicionar KeyEventDispatcher global para capturar teclas em qualquer lugar
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
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
                        screenMode.exitFullScreen(VideoPlayer.this, audioLine, controlPanel, normalBounds,
                                grabber, isPlaying, playbackThread, currentVideoPath);
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
                        captureFrameManager.captureFrame(VideoPlayer.this, mainPanel,
                                mainPanel.getCustomCapturePath(), videoFilePath, currentFrame, mainPanel.isSilentCapture());
                        return true;
                    }
                    break;
                case KeyEvent.VK_V:
                    if (grabber != null) {
                        captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this, isPlaying,
                                totalFrames, mainPanel.getBatchCaptureInterval(), frameRate, mainPanel.getBatchCapturePath(), videoFilePath);
                        return true;
                    }
                    break;
            }
            return false; // Não consumir o evento se não for um dos nossos atalhos
        });

        // Adicionar listener de teclado no painel de vídeo (mantido como backup)
        mainPanel.setFocusable(true);
        mainPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                    if (gd.getFullScreenWindow() == VideoPlayer.this) {
                        screenMode.exitFullScreen(VideoPlayer.this, audioLine, controlPanel, normalBounds,
                                grabber, isPlaying, playbackThread, currentVideoPath);
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
                    captureFrameManager.captureFrame(VideoPlayer.this, mainPanel,
                            mainPanel.getCustomCapturePath(), videoFilePath, currentFrame, mainPanel.isSilentCapture());
                } else if (e.getKeyCode() == KeyEvent.VK_V) {
                    captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this, isPlaying,
                            totalFrames, mainPanel.getBatchCaptureInterval(), frameRate, mainPanel.getBatchCapturePath(), videoFilePath);
                }
            }
        });

        // Click esquerdo para pausar/continuar
        mainPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (grabber != null && (isPlaying || !isStopped)) {
                        togglePlayPause();
                    }
                }
                mainPanel.requestFocusInWindow();
            }
        });


        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Em windowClosing / dispose
                if (videoFilePath != null && grabber != null) {
                    videoProgressManager.saveProgress(videoFilePath, currentFrame, totalFrames);
                }
                dispose(); // chama o dispose() sobrescrito acima
                System.exit(0);
            }
        });

        fileManager.setupDropTarget((JComponent) this.getComponent(0));
    }

    public void toggleFullScreen() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (gd.getFullScreenWindow() == this) {
            screenMode.exitFullScreen(this, audioLine, controlPanel, normalBounds,
                    grabber, isPlaying, playbackThread, currentVideoPath);
        } else {
            screenMode.enterFullScreen(this, audioLine, controlPanel, normalBounds,
                    grabber, isPlaying, playbackThread, currentVideoPath);
        }
    }

    public void saveVideoState() {
        if (videoFilePath != null) {
            currentVideoPath = videoFilePath;
            savedFramePosition = currentFrame;
            savedPlayingState = isPlaying;

            // Salvar legendas
            savedSubtitles = new ArrayList<>(subtitleManager.getSubtitles());
            savedSubtitleStream = subtitleManager.getCurrentSubtitleStream();

            // Salvar stream de áudio e SEMPRE salvar nomes e total
            savedAudioStream = audioStreams.getCurrentAudioStream();
            savedAudioStreamNames = new HashMap<>(audioStreams.getAudioStreamNames());
            savedSubtitleStreamNames = new HashMap<>(subtitleManager.getSubtitleStreamNames());

            // IMPORTANTE: Salvar totalAudioStreams também
            int savedTotal = audioStreams.getTotalAudioStreams();

            System.out.println("Estado salvo - Vídeo: " + currentVideoPath);
            System.out.println("Frame: " + savedFramePosition + ", Tocando: " + savedPlayingState);
            System.out.println("Legendas: " + savedSubtitles.size() + " entradas, Stream: " + savedSubtitleStream);
            System.out.println("Áudio Stream: " + savedAudioStream);
            System.out.println("Total Audio Streams: " + savedTotal);
            System.out.println("Streams de áudio salvas: " + savedAudioStreamNames.size());

            // Se não tem nomes salvos, criar nomes padrão baseado no total
            if (savedAudioStreamNames.isEmpty() && savedTotal > 0) {
                for (int i = 0; i < savedTotal; i++) {
                    savedAudioStreamNames.put(i, "Audio Track " + (i + 1));
                }
                System.out.println("Nomes de áudio criados automaticamente: " + savedAudioStreamNames.size());
            }
        }
    }

    public void restoreVideoState() {

        System.out.println("Restaurando vídeo: " + currentVideoPath);
        System.out.println("Posição: frame " + savedFramePosition);
        System.out.println("Stream de áudio: " + savedAudioStream);

        // Recarregar vídeo com stream de áudio salva
        audioStreams.loadVideoWithAudioStream(currentVideoPath, savedAudioStream);

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
                    audioStreams.setAudioStreamNames(new HashMap<>(savedAudioStreamNames));
                    System.out.println("Nomes de streams de áudio restaurados: " + audioStreams.getAudioStreamNames().size());
                }

                if (!savedSubtitleStreamNames.isEmpty()) {
                    subtitleManager.setSubtitleStreamNames(new HashMap<>(savedSubtitleStreamNames));
                    subtitleManager.setTotalSubtitleStreams(subtitleManager.getSubtitleStreamNames().size());
                    System.out.println("Nomes de streams de legendas restaurados: " + subtitleManager.getSubtitleStreamNames().size());
                    System.out.println("totalSubtitleStreams definido: " + subtitleManager.getTotalSubtitleStreams());
                }

                // Restaurar stream de áudio
                audioStreams.setCurrentAudioStream(savedAudioStream);
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
                                    mainPanel.updateImage(img);
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
                            subtitleManager.updateSubtitle(currentTimeMs, mainPanel);

                            System.out.println("Posição restaurada: frame " + savedFramePosition);
                            System.out.println("Slider atualizado para: " + progress + "%");
                            System.out.println("Estado completamente restaurado:");
                            System.out.println("  - totalAudioStreams: " + audioStreams.getTotalAudioStreams());
                            System.out.println("  - currentAudioStream: " + audioStreams.getCurrentAudioStream());
                            System.out.println("  - audioStreamNames size: " + audioStreams.getAudioStreamNames().size());
                            System.out.println("  - totalSubtitleStreams: " + subtitleManager.getTotalSubtitleStreams());
                            System.out.println("  - subtitleStreamNames size: " + subtitleManager.getSubtitleStreamNames().size());

                            // Retomar reprodução se estava tocando
                            if (savedPlayingState) {
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(300); // Delay para estabilizar
                                        SwingUtilities.invokeLater(() -> {

                                            playVideoOrAudio();
                                            System.out.println("Reprodução retomada");
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }).start();
                            }

                        } else if (savedPlayingState) {
                            // Se estava tocando do início
                            playVideoOrAudio();
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

    private void initComponents() {
        setLayout(new BorderLayout());

        add(mainPanel, BorderLayout.CENTER);

        // Painel de controles
        controlPanel = new JPanel(new BorderLayout());
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

        openButton = new JButton("📁");
        openButton.setPreferredSize(new Dimension(35, 35));
        openButton.addActionListener(e -> {
            try {
                fileManager.openVideoOrAudio();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        loadPlaylistButton = new JButton("📂");
        loadPlaylistButton.setEnabled(true);
        loadPlaylistButton.setPreferredSize(new Dimension(35, 35));
        loadPlaylistButton.addActionListener(e -> playListExecution.loadAndPlayPlaylist(mainPanel,this));

        rewindButton = new JButton("⏪");
        rewindButton.setEnabled(false);
        rewindButton.setPreferredSize(new Dimension(35, 35));
        rewindButton.addActionListener(e -> rewind10Seconds());

        playPauseButton = new JButton("▶");
        playPauseButton.setEnabled(false);
        playPauseButton.setPreferredSize(new Dimension(50, 50));
        playPauseButton.addActionListener(e -> togglePlayPause());

        forwardButton = new JButton("⏩");
        forwardButton.setEnabled(false);
        forwardButton.setPreferredSize(new Dimension(35, 35));
        forwardButton.addActionListener(e -> forward10Seconds());

        stopButton = new JButton("■");
        stopButton.setEnabled(false);
        stopButton.setPreferredSize(new Dimension(35, 35));
        stopButton.addActionListener(e -> stopVideo());

        nextFrameButton = new JButton("⏭");
        nextFrameButton.setEnabled(false);
        nextFrameButton.setPreferredSize(new Dimension(35, 35));
        nextFrameButton.addActionListener(e -> nextFrame());

        captureFrameButton = new JButton("📷");
        captureFrameButton.setEnabled(false);
        captureFrameButton.setPreferredSize(new Dimension(35, 35));
        captureFrameButton.addActionListener(e -> captureFrameManager.captureFrame(this, mainPanel,
                mainPanel.getCustomCapturePath(), videoFilePath, currentFrame, mainPanel.isSilentCapture()));

        captureAllFrameButton = new JButton("📦");
        captureAllFrameButton.setEnabled(false);
        captureAllFrameButton.setPreferredSize(new Dimension(35, 35));
        captureAllFrameButton.addActionListener(e -> captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this, isPlaying,
                totalFrames, mainPanel.getBatchCaptureInterval(), frameRate, mainPanel.getBatchCapturePath(), videoFilePath));

        configButton = new JButton("⚙");
        configButton.setPreferredSize(new Dimension(35, 35));
        configButton.addActionListener(e -> {
            ConfigurationFrame configFrame = new ConfigurationFrame();
            configFrame.setVisible(true);
        });


        volumeButton = new JButton("🔊");
        volumeButton.setEnabled(false);
        volumeButton.setPreferredSize(new Dimension(35, 35));
        volumeButton.addActionListener(e -> toggleMute());

        volumeLabel = new JLabel("100%");
        volumeSlider = new JSlider(0, 100, 100);
        volumeSlider.setPreferredSize(new Dimension(100, 20));
        volumeSlider.addChangeListener(e -> {
            if (volumeSlider.getValueIsAdjusting() || !isMuted) {
                int vol = volumeSlider.getValue();
                volume = vol / 100.0f;
                volumeLabel.setText(vol + "%");

                if (isMuted && volumeSlider.getValueIsAdjusting() && vol > 0) {
                    isMuted = false;
                    updateVolumeButton();
                }

                if (vol == 0 && !isMuted) {
                    isMuted = true;
                    previousVolume = 0.5f;
                    updateVolumeButton();
                }
                configManager.saveVolume(vol);
            }

        });
        int savedVolume = configManager.getSavedVolume();
        volumeSlider.setValue(savedVolume);
        volume = savedVolume / 100.0f;
        volumeLabel.setText(savedVolume + "%");

        boolean savedMuted = configManager.isSavedMuted();
        if (savedMuted) toggleMute();
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
            configManager.saveMuted(isMuted);
        } else {
            // Mutar - salvar volume atual e zerar
            isMuted = true;
            previousVolume = volume;
            volume = 0.0f;
            volumeSlider.setValue(0);
            volumeLabel.setText("0%");
            System.out.println("Som desativado - Volume anterior salvo: " + (int) (previousVolume * 100) + "%");
            configManager.saveMuted(isMuted);
        }

        updateVolumeButton();
    }

    // NOVO: Método para atualizar o ícone do botão de volume
    private void updateVolumeButton() {
        if (isMuted || volume == 0.0f) {
            volumeButton.setText("🔇"); // Mudo
        } else {
            volumeButton.setText("🔊"); // Volume alto
        }
    }

    public void loadFromRecentFile(File file) {
        if (isPlaying) {
            pauseVideo();
        }
        fileManager.fileTypesAction(file);
    }

    public void cleanUpItems() {
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
        coverArt.setAudioCoverArt(null);

        //Manter o layout do Spectrum panel
        if (mainPanel.getSpectrumLayoutMode() == AudioSpectrumPanel.LayoutMode.CIRCULAR) {
            mainPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.CIRCULAR);
        } else if (mainPanel.getSpectrumLayoutMode() == AudioSpectrumPanel.LayoutMode.WAVEFORM) {
            mainPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.WAVEFORM);
        } else {
            mainPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.LINEAR);
        }
        SwingUtilities.invokeLater(() -> {
            mainPanel.setCoverArt(null);
            mainPanel.repaint();
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
        filtersManager.setBrightness(0.0);
        filtersManager.setContrast(1.0);
        filtersManager.setGamma(1.0);
        filtersManager.setSaturation(1.0);
        filtersManager.setFiltersEnabled(false);
    }

    public void playVideoOrAudio() {
        if (grabber == null || isPlaying) return;

        isPlaying = true;
        isStopped = false;
        playPauseButton.setText("⏸");

        if (audioLine != null && !audioLine.isRunning()) {
            audioLine.start();
            mainPanel.spectrumPanel.setPaused(false);
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
                                    byte[] audioBytes = audioLoudnessManager.processAudioSamples(channelSamples, loudnessAnalyzer, mainPanel,
                                            isAudioOnly, audioChannels, volume);

                                    if (audioBytes != null) {
                                        audioLine.write(audioBytes, 0, audioBytes.length);
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
                                if (filtersManager.isFiltersEnabled()) {
                                    // Para 4K, aplicar filtros apenas a cada 2 frames
                                    if (!isHeavyVideo || currentFrame % 2 == 0) {
                                        img = filtersManager.applyImageFilters(img);
                                    }
                                }
                                mainPanel.updateImage(img);
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

                            // Dentro do loop de reprodução, a cada N frames
                            if (currentFrame % (int) (frameRate * 30) == 0) {
                                videoProgressManager.saveProgress(videoFilePath, currentFrame, totalFrames);
                            }


                            currentFrame++;
                            frameCount++;

                            // Atualizar legenda
                            long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                            subtitleManager.updateSubtitle(currentTimeMs, mainPanel);

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
        if ( playListExecution.getPlaylistManager().size() > 0 && playListExecution.getPlaylistManager().isShuffle()) {
            playlistManager.markCurrentAsPlayed();
        }

        if (mainPanel.isAutoPlayNext() && playListExecution.getPlaylistManager().size() > 0) {
            SwingUtilities.invokeLater(() -> {
                PlaylistItem next = playListExecution.getPlaylistManager().next();
                playListExecution.getPlaylistDialog().refreshPlaylist();
                if (next != null) {
                    System.out.println("Auto-play: " + next.getDisplayName());
                    playListExecution.playFromPlaylist(next.getFilePath(),this);
                } else {
                    stopVideo();
                }
            });
        } else {
            SwingUtilities.invokeLater(this::stopVideo);
        }
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
                        mainPanel.updateImage(img);
                    }
                }

                // Resetar posição
                grabber.setFrameNumber((int) targetFrame);

                // Atualizar legenda
                long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                subtitleManager.updateSubtitle(currentTimeMs, mainPanel);
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
                playVideoOrAudio();
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
                        mainPanel.updateImage(img);
                    }
                }

                // Resetar posição
                grabber.setFrameNumber((int) targetFrame);

                // Atualizar legenda
                long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                subtitleManager.updateSubtitle(currentTimeMs, mainPanel);
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
                playVideoOrAudio();
            }

        } catch (Exception e) {
            System.err.println("Erro ao avançar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void nextFrame() {
        if (grabber == null || totalFrames == 0) return;

        System.out.println("Avançando " + mainPanel.getFramesToSkip() + " frame(s)...");

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
            while (framesAdvanced < mainPanel.getFramesToSkip()) {
                Frame frame = grabber.grab();

                if (frame == null) {
                    System.out.println("Chegou ao fim do vídeo");
                    break;
                }

                // Se for frame de vídeo, contar
                if (frame.image != null) {
                    framesAdvanced++;

                    // Só exibir o último frame
                    if (framesAdvanced == mainPanel.getFramesToSkip()) {
                        BufferedImage img = converter.convert(frame);
                        if (img != null) {
                            mainPanel.updateImage(img);
                            currentFrame++;

                            // Atualizar UI
                            updateTimeLabel();
                            int progress = (int) ((currentFrame * 100) / totalFrames);
                            progressSlider.setValue(progress);

                            // Atualizar legenda
                            long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                            subtitleManager.updateSubtitle(currentTimeMs, mainPanel);

                            System.out.println("Frame atual: " + currentFrame + " (avançou " + mainPanel.getFramesToSkip() + " frames)");
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
                long targetFrame = Math.min(totalFrames - 1, currentFrame + mainPanel.getFramesToSkip());
                grabber.setFrameNumber((int) targetFrame);
                currentFrame = targetFrame;

                Frame frame = grabber.grabImage();
                if (frame != null && frame.image != null) {
                    BufferedImage img = converter.convert(frame);
                    if (img != null) {
                        mainPanel.updateImage(img);
                        updateTimeLabel();
                        int progress = (int) ((targetFrame * 100) / totalFrames);
                        progressSlider.setValue(progress);

                        long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                        subtitleManager.updateSubtitle(currentTimeMs, mainPanel);
                    }
                }
                grabber.setFrameNumber((int) targetFrame);
            } catch (Exception fallbackError) {
                System.err.println("Fallback também falhou: " + fallbackError.getMessage());
            }
        }
    }

    public void restoreVideoStateAfterAudioSwitch() {
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
                                    mainPanel.updateImage(img);
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
                            subtitleManager.updateSubtitle(currentTimeMs, mainPanel);

                            System.out.println("Posição restaurada após troca de áudio: frame " + savedFramePosition);

                            // Retomar reprodução se estava tocando
                            if (savedPlayingState) {
                                Thread.sleep(300);
                                SwingUtilities.invokeLater(() -> {
                                    playVideoOrAudio();
                                    System.out.println("Reprodução retomada com novo áudio");
                                });
                            }

                        } else if (savedPlayingState) {
                            playVideoOrAudio();
                        }

                        // Mostrar mensagem de sucesso
                        String streamName = audioStreams.getAudioStreamNames()
                                .getOrDefault(audioStreams.getCurrentAudioStream(), "channel " + (audioStreams.getCurrentAudioStream() + 1));
                        JOptionPane.showMessageDialog(this,
                                I18N.get("audioChannelChange.dialog.message") + "\n" + streamName,
                                I18N.get("audioChannelChange.dialog.title"), JOptionPane.INFORMATION_MESSAGE);

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

    public void togglePlayPause() {
        if (isPlaying) {
            pauseVideo();
        } else {
            playVideoOrAudio();
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

        // Em pauseVideo() e stopVideo()
        videoProgressManager.saveProgress(videoFilePath, currentFrame, totalFrames);

        if (audioLine != null && audioLine.isRunning()) {
            audioLine.stop();
            audioLine.flush();
            mainPanel.spectrumPanel.setPaused(true);
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

    public void reinicializarGrabber() {
        try {
            if (grabber != null) {
                try {
                    grabber.stop();
                } catch (Exception _) {
                }
                try {
                    grabber.release();
                } catch (Exception _) {
                }
            }
            grabber = new FFmpegFrameGrabber(videoFilePath);
            grabber.start();

            // Restaura o estado anterior se necessário
            // grabber.setTimestamp(ultimaPosicao);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, I18N.get("videoPlayer.RestartGrabber.error") +
                    "\n" + e.getMessage());
        }
    }

    private void stopVideo() {
        isPlaying = false;
        isStopped = true;
        playPauseButton.setText("▶");
        // Em pauseVideo() e stopVideo()
        videoProgressManager.saveProgress(videoFilePath, currentFrame, totalFrames);

        if (audioLine != null && audioLine.isRunning()) {
            audioLine.stop();
            audioLine.flush();
            mainPanel.spectrumPanel.setPaused(true);
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
                    System.out.println("Formato: " + grabber.getFormat());
                    if (!Objects.equals(grabber.getFormat(), "image2")) {
                        grabber.setFrameNumber(0);
                    }
                    Frame firstFrame = grabber.grabImage();
                    if (firstFrame != null && firstFrame.image != null) {
                        BufferedImage img = converter.convert(firstFrame);
                        if (img != null) {
                            mainPanel.updateImage(img);
                        }
                    }
                    if (!Objects.equals(grabber.getFormat(), "image2")) {
                        grabber.setFrameNumber(0);
                    }
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
                        mainPanel.updateImage(img);
                    }
                }

                // Atualizar legenda para nova posição
                long currentTimeMs = (long) ((currentFrame / frameRate) * 1000);
                subtitleManager.updateSubtitle(currentTimeMs, mainPanel);

                if (audioLine != null) {
                    audioLine.flush();
                }
            }

            // Atualizar UI
            updateTimeLabel();
            progressSlider.setValue(percentage);

            if (wasPlaying) {
                Thread.sleep(100);
                playVideoOrAudio();
            }

        } catch (Exception e) {
            System.err.println("Erro ao fazer seek: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateTimeLabel() {
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
                grabber = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        super.dispose();
    }

    // ADICIONAR método público para obter o JFrame (necessário para atualizar UI ao trocar tema)
    public JFrame getFrame() {
        // OPÇÃO 1: Se VideoPlayer extends JFrame
        if (this instanceof JFrame) {
            return (JFrame) this;
        }
        System.err.println("⚠️ AVISO: Não foi possível encontrar JFrame!");
        return null;
    }

    // Método para atualizar todos os textos da interface
    private void updateTexts() {

        // Atualizar tooltips dos botões
        openButton.setToolTipText(I18N.get("button.open.tooltip"));
        loadPlaylistButton.setToolTipText(I18N.get("button.playlist.tooltip"));
        rewindButton.setToolTipText(I18N.get("button.rewind.tooltip"));
        playPauseButton.setToolTipText(I18N.get("button.playpause.tooltip"));
        forwardButton.setToolTipText(I18N.get("button.forward.tooltip"));
        stopButton.setToolTipText(I18N.get("button.stop.tooltip"));
        // Considerar o número de frames configurado
        int frames = mainPanel != null ? mainPanel.getFramesToSkip() : 1;
        nextFrameButton.setToolTipText(I18N.get("button.nextframe.tooltip") + " " + frames + " frame" + (frames > 1 ? "s" : ""));
        captureFrameButton.setToolTipText(I18N.get("button.capture.tooltip"));
        captureAllFrameButton.setToolTipText(I18N.get("button.captureall.tooltip"));
        configButton.setToolTipText(I18N.get("button.config.tooltip"));
        volumeButton.setToolTipText(I18N.get("button.volume.tooltip"));

    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        System.out.println("VideoPlayer: Idioma mudou para: " + newLocale);
        updateTexts();
        revalidate();
        repaint();
    }

}