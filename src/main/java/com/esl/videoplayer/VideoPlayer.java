package com.esl.videoplayer;


import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.Video.VideoPanel;
import com.esl.videoplayer.audio.AudioLoudnessAnalyzer;
import com.esl.videoplayer.audio.AudioLoudnessManager;
import com.esl.videoplayer.audio.cover.CoverArt;
import com.esl.videoplayer.capture.CaptureFrameManager;
import com.esl.videoplayer.filters.FiltersManager;
import com.esl.videoplayer.playlist.PlaylistDialog;
import com.esl.videoplayer.playlist.PlaylistItem;
import com.esl.videoplayer.playlist.PlaylistManager;
import com.esl.videoplayer.subtitle.SubtitleEntry;
import com.esl.videoplayer.subtitle.SubtitleManager;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme;
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
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
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
    private AudioLoudnessManager audioLoudnessManager;
    private AudioLoudnessAnalyzer loudnessAnalyzer;
    private CaptureFrameManager captureFrameManager;
    private FiltersManager filtersManager;
    private CoverArt coverArt;

    public VideoPanel videoPanel;
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

    private Thread batchCaptureThread = null;
    private volatile boolean batchCapturePaused = false;
    private volatile boolean batchCaptureCancelled = false;

    String ffmpegPath = new File("lib/ffmpeg/bin/ffmpeg.exe").getAbsolutePath();
    String ffprobePath = new File("lib/ffmpeg/bin/ffprobe.exe").getAbsolutePath();

    // Adicionar variável de instância para controlar se é áudio ou vídeo
    private boolean isAudioOnly = false;

    //Variaveis para exiber a resoluçao atual da tela
    private int screenWidth;
    private int screenHeight;

    public VideoPlayer() {
        setTitle("Video Player - JavaCV");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(700, 500);
        setLocationRelativeTo(null);

        captureFrameManager = new CaptureFrameManager();
        filtersManager = new FiltersManager();
        subtitleManager = new SubtitleManager();
        loudnessAnalyzer = new AudioLoudnessAnalyzer();
        audioLoudnessManager = new AudioLoudnessManager();
        coverArt = new CoverArt();

        videoPanel = new VideoPanel(grabber, subtitleManager, this,audioLoudnessManager);
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
                if (videoPanel != null && videoPanel.getAutoPlayItem() != null) {
                    if (videoPanel.getAutoPlayItem().isSelected()) {
                        System.out.println("====Reativando o autoplay");
                        videoPanel.setAutoPlayNext(true);
                    }
                }
            }
        });

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
                               videoPanel.getCustomCapturePath(), videoFilePath,currentFrame, videoPanel.isSilentCapture());
                        return true;
                    }
                    break;
                case KeyEvent.VK_V:
                    if (grabber != null) {
                        captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this,batchCaptureThread,isPlaying,
                        totalFrames, videoPanel.getBatchCaptureInterval(),frameRate, videoPanel.getBatchCapturePath(), videoFilePath);
                        return true;
                    }
                    break;
            }
            return false; // Não consumir o evento se não for um dos nossos atalhos
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
                            videoPanel.getCustomCapturePath(), videoFilePath,currentFrame, videoPanel.isSilentCapture());
                } else if (e.getKeyCode() == KeyEvent.VK_V) {
                    captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this,batchCaptureThread,isPlaying,
                            totalFrames, videoPanel.getBatchCaptureInterval(),frameRate, videoPanel.getBatchCapturePath(), videoFilePath);
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
        if(videoPanel.getAutoPlayItem().isSelected()){
            System.out.println("====Reativando o autoplay");
            videoPanel.setAutoPlayNext(true);
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
       videoPanel.setAutoPlayNext(false);
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
            controlPanel.setVisible(false);
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
            controlPanel.setVisible(true);
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

    private void initComponents() {
        setLayout(new BorderLayout());

        add(videoPanel, BorderLayout.CENTER);

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
              videoPanel.getCustomCapturePath(), videoFilePath,currentFrame, videoPanel.isSilentCapture()));

        captureAllFrameButton = new JButton("\uD83D\uDCE6");
        captureAllFrameButton.setEnabled(false);
        captureAllFrameButton.setPreferredSize(new Dimension(35, 35));
        captureAllFrameButton.setToolTipText("Capturar todos os frames");
        captureAllFrameButton.addActionListener(e -> captureFrameManager.batchCaptureFrames(grabber, VideoPlayer.this, batchCaptureThread,isPlaying,
        totalFrames,videoPanel.getBatchCaptureInterval(),frameRate,videoPanel.getBatchCapturePath(),videoFilePath));

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
                    if (frameIndex % videoPanel.getBatchCaptureInterval()== 0) {
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
                            playVideoOrAudio();
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

    private void loadVideo(String filepath) {
        isAudioOnly = false;

        // Salvar caminho do vídeo
        currentVideoPath = filepath;
        videoFilePath = filepath;

        System.out.println("=== INÍCIO loadVideo ===");
        // Limpar playlist e fechar dialog
        clearPlaylistAndCloseDialog();

        loadVideoBase(filepath);
    }
    private void loadVideoFromPlaylist(String filepath) {
        isAudioOnly = false;

        // Salvar caminho do vídeo
        currentVideoPath = filepath;
        videoFilePath = filepath;

        System.out.println("=== INÍCIO loadVideo ===");

        loadVideoBase(filepath);
    }
    private void loadVideoBase(String filepath) {

        // **VERIFICAR SE É AV1 EM ALTA RESOLUÇÃO (BLOQUEAR SE SIM)**
        if (!checkAV1Resolution(filepath)) {
            System.out.println("Vídeo bloqueado - AV1 em alta resolução com decoder lento");
            return; // Sair sem carregar o vídeo
        }

        //Limpa os items setados anteriormente
        cleanUpItems();

        System.out.println("Atualizando título...");
        setTitle("Video Player - Carregando video...");

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

                    setTitle("Video Player - " + new File(filepath).getName());

                    videoPanel.setupVideoContextMenu(subtitleManager, captureFrameManager, this,
                            filtersManager,videoFilePath, grabber, nextFrameButton, totalAudioStreams,
                            currentAudioStream, audioStreamNames, ffmpegPath);

                    playVideoOrAudio();

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

    private void cleanUpItems() {
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
        if(videoPanel.getSpectrumLayoutMode() == AudioSpectrumPanel.LayoutMode.CIRCULAR){
            videoPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.CIRCULAR);
        }else if(videoPanel.getSpectrumLayoutMode() == AudioSpectrumPanel.LayoutMode.WAVEFORM) {
            videoPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.WAVEFORM);
        }else {
            videoPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.LINEAR);
        }
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
        filtersManager.setBrightness(0.0);
        filtersManager.setContrast(1.0);
        filtersManager.setGamma(1.0);
        filtersManager.setSaturation(1.0);
        filtersManager.setFiltersEnabled(false);
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

    public void playVideoOrAudio() {
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
                                    byte[] audioBytes =  audioLoudnessManager.processAudioSamples(channelSamples,loudnessAnalyzer, videoPanel,
                                    isAudioOnly, audioChannels, volume);

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
                                if (filtersManager.isFiltersEnabled() ) {
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

        if (videoPanel.isAutoPlayNext() && playlistManager.size() > 0) {
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

    private void loadAudio(String filepath) {
        // Salvar caminho do áudio
        currentVideoPath = filepath;
        videoFilePath = filepath;

        System.out.println("=== INÍCIO loadAudio ===");

        // NOVO: Limpar playlist e fechar dialog
        clearPlaylistAndCloseDialog();

        loadAudioBase(filepath);
    }

    private void loadAudioFromPlaylist(String filepath) {
        // Salvar caminho do áudio
        currentVideoPath = filepath;
        videoFilePath = filepath;

        System.out.println("=== INÍCIO loadAudioFromPlaylist  ===");

        loadAudioBase(filepath);
    }

    private void loadAudioBase(String filepath) {

        //Limpa os items setados anteriormente
        cleanUpItems();

        System.out.println("Atualizando título...");
        setTitle("Video Player - Carregando áudio...");

        System.out.println("Iniciando thread de carregamento de áudio...");

        // Resetar normalização
        audioLoudnessManager.setNormalizationCalculated(false);
        audioLoudnessManager.setNormalizationSampleCount(0);
        audioLoudnessManager.setNormalizationGain(1.0f);
        audioLoudnessManager.setMaxPeakLevel(0.0f);
        audioLoudnessManager.setAverageRMS(0.0f);
        audioLoudnessManager.setRmsHistoryIndex(0);
        Arrays.fill(audioLoudnessManager.getRmsHistory(), 0.0f);
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
                coverArt.extractCoverArt(filepath, ffmpegPath, videoPanel);

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
                                true                   // big-endian
                        );

                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        audioLine = (SourceDataLine) AudioSystem.getLine(info);

                        int bufferSize = sampleRate * outputChannels * 2;
                        audioLine.open(audioFormat, bufferSize);
                        System.out.println("10. AudioLine configurado com sucesso");

                        // Combinação default - deve refletir o que está no menu
                      audioLoudnessManager.setGlobalAudioGain(0.20f);          // 20% do volume
                      audioLoudnessManager.setTargetLoudness(-18.0f);         // Target mais baixo
                      audioLoudnessManager.setAudioNormalizationEnabled(false); // normalização desativada

                    } catch (Exception audioEx) {
                        System.err.println("10. Erro ao configurar áudio: " + audioEx.getMessage());
                        audioLine = null;
                    }
                }

                System.out.println("11. Áudio carregado! Habilitando UI...");

                SwingUtilities.invokeLater(() -> {

                    // Redimensionar e centralizar a janela
                    setSize(700, 500);
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
                    setTitle("Video Player - " + new File(filepath).getName());

                    videoPanel.setupAudioContextMenu(this, grabber);

                    playVideoOrAudio();
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
                playVideoOrAudio();
            }

        } catch (Exception e) {
            System.err.println("Erro ao avançar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void nextFrame() {
        if (grabber == null || totalFrames == 0) return;

        System.out.println("Avançando " + videoPanel.getFramesToSkip() + " frame(s)...");

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
            while (framesAdvanced < videoPanel.getFramesToSkip()) {
                Frame frame = grabber.grab();

                if (frame == null) {
                    System.out.println("Chegou ao fim do vídeo");
                    break;
                }

                // Se for frame de vídeo, contar
                if (frame.image != null) {
                    framesAdvanced++;

                    // Só exibir o último frame
                    if (framesAdvanced == videoPanel.getFramesToSkip()) {
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

                            System.out.println("Frame atual: " + currentFrame + " (avançou " + videoPanel.getFramesToSkip() + " frames)");
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
                long targetFrame = Math.min(totalFrames - 1, currentFrame + videoPanel.getFramesToSkip());
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
                                    playVideoOrAudio();
                                    System.out.println("Reprodução retomada com novo áudio");
                                });
                            }

                        } else if (savedPlayingState) {
                            playVideoOrAudio();
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
                playVideoOrAudio();
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
            FlatArcDarkOrangeIJTheme.setup();
            FlatArcOrangeIJTheme.setup();
            UIManager.put("Button.arc", 999);
            VideoPlayer player = new VideoPlayer();
            player.setVisible(true);
        });
    }
}