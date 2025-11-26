package com.esl.videoplayer.Video;

import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.audio.AudioLoudnessManager;
import com.esl.videoplayer.capture.CaptureFrameManager;
import com.esl.videoplayer.filters.FiltersManager;
import com.esl.videoplayer.subtitle.SubtitleManager;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

public class VideoPanel extends JPanel {
    private BufferedImage currentImage;

    private BufferedImage coverArt; // NOVO: Cover art do áudio
    JCheckBoxMenuItem autoPlayItem;

    private boolean autoPlayNext;
    private int framesToSkip = 1; // Quantidade de frames para avançar
    private boolean silentCapture = false;
    private String customCapturePath = null;
    private int batchCaptureInterval = 2; // Capturar a cada N frames
    private String batchCapturePath = null;

    // NOVO: Referências aos itens do menu de layout
    private JRadioButtonMenuItem linearLayoutMenuItem;
    private JRadioButtonMenuItem circularLayoutMenuItem;
    private JRadioButtonMenuItem waveLayoutMenuItem;

    public AudioSpectrumPanel spectrumPanel;
    private  SubtitleManager subtitleManager;
    private AudioLoudnessManager audioLoudnessManager;

    public BufferedImage getCurrentImage() {
        return currentImage;
    }
    public VideoPanel(FFmpegFrameGrabber grabber, SubtitleManager subtitleManager, VideoPlayer videoPlayer, AudioLoudnessManager audioLoudnessManager) {

        this.subtitleManager = subtitleManager;
        this.audioLoudnessManager = audioLoudnessManager;

        setBackground(Color.BLACK);
        setDoubleBuffered(true);

      //setLayout(new BorderLayout());

        // NÃO usar BorderLayout aqui, usar null layout para controle manual
        setLayout(null);

        // Criar spectrum panel (inicialmente invisível)
        spectrumPanel = new AudioSpectrumPanel();
        spectrumPanel.setVisible(false);

        add(spectrumPanel, BorderLayout.CENTER);

        if (grabber == null) {
            setupContextMenu(videoPlayer,grabber);
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

    public void setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode mode){
        if(spectrumPanel != null){
            spectrumPanel.setLayoutMode(mode);
        }
    }
    public AudioSpectrumPanel.LayoutMode getSpectrumLayoutMode(){
        if(spectrumPanel != null){
         return spectrumPanel.getLayoutMode();
        }
        return null;
    }

    // NOVO: Método para atualizar menu de acordo com o layout atual
    public void updateLayoutMenuSelection() {
        if (spectrumPanel == null) return;

        AudioSpectrumPanel.LayoutMode currentMode = spectrumPanel.getLayoutMode();

        SwingUtilities.invokeLater(() -> {
            if (linearLayoutMenuItem != null && circularLayoutMenuItem != null && waveLayoutMenuItem != null) {
                switch (currentMode) {
                    case LINEAR:
                        linearLayoutMenuItem.setSelected(true);
                        break;
                    case CIRCULAR:
                        circularLayoutMenuItem.setSelected(true);
                        break;
                    case WAVEFORM:
                        waveLayoutMenuItem.setSelected(true);
                        break;
                }
            }
        });
    }

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

    private void setupContextMenu(VideoPlayer videoPlayer, FFmpegFrameGrabber grabber) {
        JPopupMenu contextMenu = new JPopupMenu();
        // === Submenu: Playlist ===
        JMenu playlistMenu = new JMenu("Playlist");
        playlistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JMenuItem openPlaylistItem = new JMenuItem("📋 Nova Playlist...");
        openPlaylistItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        openPlaylistItem.addActionListener(e -> videoPlayer.showPlaylistDialog());

        autoPlayItem = new JCheckBoxMenuItem("Auto-play Próxima", true);
        autoPlayItem.addActionListener(e -> autoPlayNext = autoPlayItem.isSelected());

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

    public void setupAudioContextMenu(VideoPlayer videoPlayer, FFmpegFrameGrabber grabber) {
        JPopupMenu contextMenu = new JPopupMenu();

        JMenu spectrumMenu = new JMenu("Equalizador de áudio Animado");
        spectrumMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // === Submenu: Layout do Spectrum ===
        JMenu spectrumLayoutMenu = new JMenu("Formato do equalizador");

        ButtonGroup layoutGroup = new ButtonGroup();

        linearLayoutMenuItem = new JRadioButtonMenuItem("Linear", true);
        linearLayoutMenuItem.addActionListener(e ->  setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.LINEAR));

        circularLayoutMenuItem = new JRadioButtonMenuItem("Circular");
        circularLayoutMenuItem.addActionListener(e -> setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.CIRCULAR));

        waveLayoutMenuItem = new JRadioButtonMenuItem("Wave");
        waveLayoutMenuItem.addActionListener(e -> {
            setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.WAVEFORM);
            spectrumPanel.setWaveformLayers(5);        // 5 linhas
            spectrumPanel.setWaveformAmplitude(180);
            spectrumPanel.setWaveformLayerSpacing(0.8f); // Espaçamento entre camadas
            spectrumPanel.setWaveformFilled(false);     // Preencher a camada externa
         });

        layoutGroup.add(linearLayoutMenuItem);
        layoutGroup.add(circularLayoutMenuItem);
        layoutGroup.add(waveLayoutMenuItem);

        spectrumLayoutMenu.add(linearLayoutMenuItem);
        spectrumLayoutMenu.add(circularLayoutMenuItem);
        spectrumLayoutMenu.add(waveLayoutMenuItem);

        // === Submenu: Modo de Cor ===
        JMenu colorModeMenu = new JMenu("Modo de Cor");

        ButtonGroup colorGroup1 = new ButtonGroup();

        JRadioButtonMenuItem defaultColorItem = new JRadioButtonMenuItem("Padrão (Verde-Amarelo-Vermelho)");
        defaultColorItem.addActionListener(e -> setSpectrumColorMode(AudioSpectrumPanel.ColorMode.DEFAULT));

        JRadioButtonMenuItem coverColorItem = new JRadioButtonMenuItem("Baseado na Capa", true);
        coverColorItem.addActionListener(e -> {
            spectrumPanel.setCoverColorDifferenceMultiplier(2.0f);
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.COVER_PALETTE);

        });

        JRadioButtonMenuItem customColorItem = new JRadioButtonMenuItem("Personalizado...");
        customColorItem.addActionListener(e -> showCustomColorDialog());

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
            setSpectrumCustomColors(
                    new Color(100, 0, 200),
                    new Color(200, 0, 255),
                    new Color(255, 100, 255)
            );
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);
        });

        JMenuItem matrixItem = new JMenuItem("Paleta: Matrix");
        matrixItem.addActionListener(e -> {
            setSpectrumCustomColors(
                    new Color(0, 255, 0),
                    new Color(0, 255, 128),
                    new Color(128, 255, 255)
            );
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);
        });

        JMenuItem fireItem = new JMenuItem("Paleta: Fogo");
        fireItem.addActionListener(e -> {
            setSpectrumCustomColors(
                    new Color(255, 100, 0),
                    new Color(255, 200, 0),
                    new Color(255, 255, 100)
            );
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);
        });

        JMenuItem iceItem = new JMenuItem("Paleta: Gelo");
        iceItem.addActionListener(e -> {
            setSpectrumCustomColors(
                    new Color(0, 100, 200),
                    new Color(0, 200, 255),
                    new Color(200, 255, 255)
            );
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);
        });

        colorModeMenu.add(synthwaveItem);
        colorModeMenu.add(matrixItem);
        colorModeMenu.add(fireItem);
        colorModeMenu.add(iceItem);

        // === Checkboxes de Visibilidade ===
        spectrumMenu.add(spectrumLayoutMenu);
        spectrumMenu.add(colorModeMenu);
        spectrumMenu.addSeparator();

        JCheckBoxMenuItem showSpectrumItem = new JCheckBoxMenuItem("Mostrar Equalizador", true);
        showSpectrumItem.addActionListener(e -> setSpectrumVisible(showSpectrumItem.isSelected()));

        JCheckBoxMenuItem showCoverItem = new JCheckBoxMenuItem("Mostrar Capa", true);
        showCoverItem.addActionListener(e -> setCoverArtVisible(showCoverItem.isSelected()));

        spectrumMenu.add(showSpectrumItem);
        spectrumMenu.add(showCoverItem);

        spectrumMenu.addSeparator();

        // === Submenu: Reflexo ===
        JMenu reflectionMenu = new JMenu("Reflexo");

        JCheckBoxMenuItem enableReflectionItem = new JCheckBoxMenuItem("Ativar Reflexo", true);
        enableReflectionItem.addActionListener(e -> {
            setSpectrumReflection(enableReflectionItem.isSelected());
        });

        JMenu reflectionHeightMenu = new JMenu("Altura do Reflexo");
        String[] heightLabels = {"25%", "50%", "75%", "100%"};
        float[] heightValues = {0.25f, 0.5f, 0.75f, 1.0f};

        ButtonGroup heightGroup = new ButtonGroup();
        for (int i = 0; i < heightLabels.length; i++) {
            final float height = heightValues[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(heightLabels[i], i == 1); // 50% selecionado
            item.addActionListener(e -> {
                setSpectrumReflectionHeight(height);
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
                setSpectrumReflectionAlpha(opacity);
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
            audioLoudnessManager.setAudioNormalizationEnabled(enableNormalizationItem.isSelected());
        });

        audioMenu2.add(enableNormalizationItem);
        audioMenu2.addSeparator();

        // === Submenu: Presets de Loudness ===
        JMenu loudnessMenu = new JMenu("Intensidade Sonora");

        ButtonGroup loudnessGroup = new ButtonGroup();

        JRadioButtonMenuItem streamingItem = new JRadioButtonMenuItem("Streaming (-14 LUFS)");
        streamingItem.setToolTipText("Spotify, YouTube, Apple Music");
        streamingItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-14.0f));

        JRadioButtonMenuItem quietItem = new JRadioButtonMenuItem("Quiet (-18 LUFS)", true);
        quietItem.setToolTipText("Música ambiente, uso casual");
        quietItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-18.0f));

        JRadioButtonMenuItem broadcastItem = new JRadioButtonMenuItem("Broadcast (-23 LUFS)");
        broadcastItem.setToolTipText("TV, Rádio");
        broadcastItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-23.0f));

        JRadioButtonMenuItem cinemaItem = new JRadioButtonMenuItem("Cinema (-24 LUFS)");
        cinemaItem.setToolTipText("Padrão de cinema");
        cinemaItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-24.0f));

        JRadioButtonMenuItem loudItem = new JRadioButtonMenuItem("Loud (-11 LUFS)");
        loudItem.setToolTipText("Festas, clubs");
        loudItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-11.0f));

        JRadioButtonMenuItem customLoudnessItem = new JRadioButtonMenuItem("Personalizado...");
        customLoudnessItem.addActionListener(e -> showCustomLoudnessDialog());

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
                audioLoudnessManager.setGlobalAudioGain(vol);
            });
            volumeGroup.add(item);
            volumeGainMenu.add(item);
        }

        audioMenu2.add(volumeGainMenu);
        audioMenu2.addSeparator();

        // === Informações ===
        JMenuItem infoItem = new JMenuItem("Informações de Áudio");
        infoItem.addActionListener(e -> {
            String info = audioLoudnessManager.getNormalizationInfo();
            JOptionPane.showMessageDialog(this,
                    info + "\n\n" +
                            "Normalização: " + (audioLoudnessManager.isAudioNormalizationEnabled() ? "Ativada" : "Desativada") + "\n" +
                            "Volume Global: " + (int) (audioLoudnessManager.getGlobalAudioGain() * 100) + "%",
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
        openPlaylistItem.addActionListener(e -> videoPlayer.showPlaylistDialog());

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
                    JOptionPane.showMessageDialog(videoPlayer,
                            "Nenhum audio carregado.\nAbra um audio primeiro.",
                            "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    SwingUtilities.invokeLater(() -> contextMenu.setVisible(false));
                    return;
                }

                spectrumMenu.setEnabled(true);
                audioMenu2.setEnabled(true);

                // NOVO: Atualizar seleção do menu baseado no layout atual
                updateLayoutMenuSelection();

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

    public void setupVideoContextMenu(SubtitleManager subtitleManager, CaptureFrameManager captureFrameManager, VideoPlayer videoPlayer,
                                      FiltersManager filtersManager, String videoFilePath, FFmpegFrameGrabber grabber, JButton nextFrameButton,
                                      int totalAudioStreams, int currentAudioStream, Map<Integer, String> audioStreamNames, String ffmpegPath) {
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
        loadExternal.addActionListener(e -> subtitleManager.loadExternalSubtitle(videoFilePath, videoPlayer));
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
        hwAccelItem.setSelected(videoPlayer.hardwareAccelerationEnabled);
        hwAccelItem.addActionListener(e -> {
            videoPlayer.hardwareAccelerationEnabled = hwAccelItem.isSelected();
            System.out.println("Aceleração GPU: " + (videoPlayer.hardwareAccelerationEnabled ? "Habilitada" : "Desabilitada"));

            if (grabber != null) {
                JOptionPane.showMessageDialog(videoPlayer,
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
        selectFolderItem.addActionListener(e -> captureFrameManager.selectCaptureFolder(customCapturePath,videoFilePath, videoPlayer));
        captureMenu.add(selectFolderItem);

        // Opção de resetar para pasta padrão
        JMenuItem resetFolderItem = new JMenuItem("Usar Pasta do Vídeo");
        resetFolderItem.addActionListener(e -> captureFrameManager.resetCaptureFolder(customCapturePath, videoPlayer));
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

            JOptionPane.showMessageDialog(videoPlayer,
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
        selectBatchFolderItem.addActionListener(e -> captureFrameManager.selectBatchCaptureFolder(batchCapturePath, videoFilePath, videoPlayer));
        batchCaptureMenu.add(selectBatchFolderItem);

        // Resetar para pasta padrão
        JMenuItem resetBatchFolderItem = new JMenuItem("Usar Pasta do Vídeo");
        resetBatchFolderItem.addActionListener(e -> captureFrameManager.resetBatchCaptureFolder(batchCapturePath, videoPlayer));
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

            JOptionPane.showMessageDialog(videoPlayer,
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
        brightnessItem.addActionListener(e -> filtersManager.showBrightnessDialog(videoPlayer));
        filtersMenu.add(brightnessItem);

        // TODO: Adicionar outros filtros depois
        // JMenuItem contrastItem = new JMenuItem("Contraste...");
        // JMenuItem gammaItem = new JMenuItem("Gamma...");
        // JMenuItem saturationItem = new JMenuItem("Saturação...");

        filtersMenu.addSeparator();

        // Resetar todos os filtros
        JMenuItem resetFiltersItem = new JMenuItem("Resetar Filtros");
        resetFiltersItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(videoPlayer,
                    "Deseja resetar todos os filtros aplicados?",
                    "Confirmar Reset",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                filtersManager.resetFilters(videoPlayer);
            }
        });
        filtersMenu.add(resetFiltersItem);

        filtersMenu.addSeparator();

        // Mostrar filtros ativos
        JMenuItem showFiltersItem = new JMenuItem("Filtros Ativos");
        showFiltersItem.addActionListener(e -> {
            StringBuilder info = new StringBuilder();
            info.append("Filtros atualmente aplicados:\n\n");

            if (!filtersManager.isFiltersEnabled() || filtersManager.buildFilterString() == null) {
                info.append("Nenhum filtro ativo");
            } else {
                if (filtersManager.getBrightness() != 0.0) {
                    info.append(String.format("• Brilho: %.2f\n", filtersManager.getBrightness()));
                }
                if (filtersManager.getContrast() != 1.0) {
                    info.append(String.format("• Contraste: %.2f\n", filtersManager.getContrast()));
                }
                if (filtersManager.getGamma() != 1.0) {
                    info.append(String.format("• Gamma: %.2f\n",filtersManager.getGamma()));
                }
                if (filtersManager.getSaturation() != 1.0) {
                    info.append(String.format("• Saturação: %.2f\n", filtersManager.getSaturation()));
                }

                info.append("\nString FFmpeg:\n").append(filtersManager.buildFilterString());
            }

            JOptionPane.showMessageDialog(videoPlayer,
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
        openPlaylistItem.addActionListener(e -> videoPlayer.showPlaylistDialog());

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
            videoPlayer.toggleFullScreen();
        });
        contextMenu.add(fullscreenItem);

        // Atualizar estado ao abrir menu
        contextMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                if (grabber == null) {
                    JOptionPane.showMessageDialog(videoPlayer,
                            "Nenhum vídeo carregado.\nAbra um vídeo primeiro.",
                            "Aviso", JOptionPane.INFORMATION_MESSAGE);
                    SwingUtilities.invokeLater(() -> contextMenu.setVisible(false));
                    return;
                }

                updateContextMenus(audioMenu, subtitleMenu, videoPlayer, subtitleManager, totalAudioStreams, currentAudioStream, audioStreamNames, videoFilePath,ffmpegPath);
                GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                fullscreenItem.setSelected(gd.getFullScreenWindow() == videoPlayer);

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
                    setSpectrumCustomColors(bottom, middle, top);
                    setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
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
                    audioLoudnessManager.setTargetLoudness(loudness);
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
    private void updateContextMenus(JMenu audioMenu, JMenu subtitleMenu, VideoPlayer videoPlayer, SubtitleManager subtitleManager,
                                    int totalAudioStreams, int currentAudioStream, Map<Integer, String> audioStreamNames, String videoFilePath,
                                    String ffmpegPath) {
        // Atualizar menu de áudio
        audioMenu.removeAll();
        if (totalAudioStreams > 1) {
            for (int i = 0; i < totalAudioStreams; i++) {
                final int streamIndex = i;

                // Tentar obter nome da stream
                String streamName = audioStreamNames.getOrDefault(i, "Faixa de Áudio " + (i + 1));

                JCheckBoxMenuItem item = new JCheckBoxMenuItem(streamName);
                item.setSelected(i == currentAudioStream);
                item.addActionListener(e -> videoPlayer.switchAudioStream(streamIndex));
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
                item.addActionListener(e -> subtitleManager.switchSubtitleStream(streamIndex, videoFilePath, videoPlayer,ffmpegPath));
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

    public String getBatchCapturePath() {
        return batchCapturePath;
    }

    public void setBatchCapturePath(String batchCapturePath) {
        this.batchCapturePath = batchCapturePath;
    }

    public int getBatchCaptureInterval() {
        return batchCaptureInterval;
    }

    public void setBatchCaptureInterval(int batchCaptureInterval) {
        this.batchCaptureInterval = batchCaptureInterval;
    }

    public String getCustomCapturePath() {
        return customCapturePath;
    }

    public void setCustomCapturePath(String customCapturePath) {
        this.customCapturePath = customCapturePath;
    }

    public boolean isSilentCapture() {
        return silentCapture;
    }

    public void setSilentCapture(boolean silentCapture) {
        this.silentCapture = silentCapture;
    }

    public int getFramesToSkip() {
        return framesToSkip;
    }

    public void setFramesToSkip(int framesToSkip) {
        this.framesToSkip = framesToSkip;
    }

    public boolean isAutoPlayNext() {
        return autoPlayNext;
    }

    public void setAutoPlayNext(boolean autoPlayNext) {
        this.autoPlayNext = autoPlayNext;
    }
    public JCheckBoxMenuItem getAutoPlayItem() {
        return autoPlayItem;
    }

    public void setAutoPlayItem(JCheckBoxMenuItem autoPlayItem) {
        this.autoPlayItem = autoPlayItem;
    }


}
