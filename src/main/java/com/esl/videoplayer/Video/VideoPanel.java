package com.esl.videoplayer.Video;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.audio.AudioLoudnessManager;
import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.capture.CaptureFrameManager;
import com.esl.videoplayer.configuration.ConfigManager;
import com.esl.videoplayer.filters.FiltersManager;
import com.esl.videoplayer.localization.I18N;
import com.esl.videoplayer.subtitle.SubtitleManager;
import com.esl.videoplayer.theme.ThemeManager;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VideoPanel extends JPanel implements I18N.LanguageChangeListener {
    public AudioSpectrumPanel spectrumPanel;
    // JCheckBoxMenuItem autoPlayItem;
    private BufferedImage currentImage;
    private RecentFilesManager recentFilesManager;
    private ThemeManager themeManager;
    private BufferedImage coverArt; // NOVO: Cover art do áudio
    private boolean autoPlayNext;
    private int framesToSkip = 1; // Quantidade de frames para avançar
    private boolean silentCapture = false;
    private String customCapturePath = null;
    private int batchCaptureInterval = 2; // Capturar a cada N frames
    private String batchCapturePath = null;
    // NOVO: Referências aos itens do menu de layout
    private SubtitleManager subtitleManager;
    private AudioLoudnessManager audioLoudnessManager;
    private BackgroundImageLoader backgroundImageLoader;
    private ConfigManager configManager;

    // Referências aos menus e itens que precisam ser traduzidos
    private JMenu audioMenu;
    private JMenu subtitleMenu;
    private JMenu subtitleSettingsMenu;
    private JMenu performanceMenu;
    private JMenu captureMenu;
    private JMenu batchCaptureMenu;
    private JMenu filtersMenu;
    private JMenu playlistMenu;


    // Menu de idioma para vídeo
    private JMenu videoLanguageMenu;
    private JRadioButtonMenuItem videoUsItem;
    private JRadioButtonMenuItem videoPtItem;
    private ButtonGroup videoLanguageGroup;

    // Menu de idioma para áudio
    private JMenu audioLanguageMenu;
    private JRadioButtonMenuItem audioUsItem;
    private JRadioButtonMenuItem audioPtItem;
    private ButtonGroup audioLanguageGroup;


    // Itens de menu
    private JMenuItem noSubtitle;
    private JMenuItem loadExternal;
    private JMenu sizeMenu;
    private JMenu colorMenu;
    private JRadioButtonMenuItem whiteColor;
    private JRadioButtonMenuItem yellowColor;
    private JCheckBoxMenuItem hwAccelItem;
    private JMenu frameSkipMenu;
    private JCheckBoxMenuItem silentCaptureItem;
    private JMenuItem selectFolderItem;
    private JMenuItem resetFolderItem;
    private JMenuItem showCurrentFolder;
    private JMenu intervalMenu;
    private JMenuItem selectBatchFolderItem;
    private JMenuItem resetBatchFolderItem;
    private JMenuItem showBatchFolder;
    private JMenuItem brightnessItem;
    private JMenuItem resetFiltersItem;
    private JMenuItem showFiltersItem;
    private JCheckBoxMenuItem fullscreenItem;
    private JMenuItem openPlaylistItem;
    private JCheckBoxMenuItem autoPlayItem;
    private JMenuItem titleItem;
    private JMenuItem clearItem;
    private JMenuItem noAudio;


    // NOVOS: Atributos para setupAudioContextMenu
    private JMenu spectrumMenu;
    private JMenu spectrumLayoutMenu;
    private JRadioButtonMenuItem linearLayoutMenuItem;
    private JRadioButtonMenuItem circularLayoutMenuItem;
    private JRadioButtonMenuItem waveLayoutMenuItem;
    private JMenu colorModeMenu;
    private JRadioButtonMenuItem defaultColorItem;
    private JRadioButtonMenuItem coverColorItem;
    private JRadioButtonMenuItem customColorItem;
    private JMenuItem synthwaveItem;
    private JMenuItem matrixItem;
    private JMenuItem fireItem;
    private JMenuItem iceItem;
    private JCheckBoxMenuItem showSpectrumItem;
    private JCheckBoxMenuItem showCoverItem;
    private JMenu reflectionMenu;
    private JCheckBoxMenuItem enableReflectionItem;
    private JMenu reflectionHeightMenu;
    private JMenu reflectionOpacityMenu;
    private JMenu audioMenu2;
    private JCheckBoxMenuItem enableNormalizationItem;
    private JMenu loudnessMenu;
    private JRadioButtonMenuItem streamingItem;
    private JRadioButtonMenuItem quietItem;
    private JRadioButtonMenuItem broadcastItem;
    private JRadioButtonMenuItem cinemaItem;
    private JRadioButtonMenuItem loudItem;
    private JRadioButtonMenuItem customLoudnessItem;
    private JMenu volumeGainMenu;
    private JMenuItem audioInfoItem;
    private JMenu audioPlaylistMenu;
    private JMenuItem audioOpenPlaylistItem;
    private JCheckBoxMenuItem audioAutoPlayItem;

    // NOVOS: Atributos para buildAndShowPopup
    private JMenu mainPlaylistMenu;
    private JMenuItem mainOpenPlaylistItem;
    private JMenu themeMenu;
    private JMenuItem recentFilesTitle;
    private JMenuItem clearRecentItem;

    private int videoWidth = 0;
    private int videoHeight = 0;

    public VideoPanel(FFmpegFrameGrabber grabber, SubtitleManager subtitleManager, VideoPlayer videoPlayer, AudioLoudnessManager audioLoudnessManager) {

        this.subtitleManager = subtitleManager;

        this.audioLoudnessManager = audioLoudnessManager;
        backgroundImageLoader = new BackgroundImageLoader();
        configManager = new ConfigManager();
        subtitleManager.setBaseSubtitleFontSize(configManager.getSavedSubtitleSize());
        subtitleManager.setSubtitleColor(configManager.getSavedSubtitleColor());
        setBackground(Color.BLACK);
        setDoubleBuffered(true);

        //setLayout(new BorderLayout());

        // NÃO usar BorderLayout aqui, usar null layout para controle manual
        setLayout(null);

        // Criar spectrum panel (inicialmente invisível)
        spectrumPanel = new AudioSpectrumPanel();
        spectrumPanel.setVisible(false);

        //Parte do menu colocada no construtor para setar o auto-play
        setAutoPlayNext(true);
        autoPlayItem = new JCheckBoxMenuItem("Auto-play Próxima", autoPlayNext);
        autoPlayItem.addActionListener(e -> autoPlayNext = autoPlayItem.isSelected());

        add(spectrumPanel, BorderLayout.CENTER);

        if (grabber == null) {
            setupContextMenu(videoPlayer);
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

        Locale savedLocale = configManager.getSavedLocale(); // "en_US" por padrão
        I18N.setLocale(savedLocale);

    }

    public BufferedImage getCurrentImage() {
        return currentImage;
    }

    public void setThemeManager(ThemeManager manager) {
        this.themeManager = manager;
        themeManager.printDebugInfo();
    }

    // ==================== MÉTODOS PÚBLICOS NA VideoPanel ====================

    public AudioSpectrumPanel.LayoutMode getSpectrumLayoutMode() {
        if (spectrumPanel != null) {
            return spectrumPanel.getLayoutMode();
        }
        return null;
    }

    public void setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode mode) {
        if (spectrumPanel != null) {
            spectrumPanel.setLayoutMode(mode);
        }
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

    public void setRecentFilesManager(RecentFilesManager manager) {
        this.recentFilesManager = manager;
    }

    private void setupContextMenu(VideoPlayer videoPlayer) {
        // Listener global – quando clicar com o botão direito, criamos o menu NA HORA
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    buildAndShowPopup(e, videoPlayer);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    buildAndShowPopup(e, videoPlayer);
                }
            }
        });
    }

    private void buildAndShowPopup(MouseEvent e, VideoPlayer videoPlayer) {
        // Criar JPopupMenu do zero
        JPopupMenu menu = new JPopupMenu();

        // === PLAYLIST ===
        menu.add(buildPlaylistMenu(videoPlayer));

        // === TEMAS ===
        menu.add(buildThemeMenu(videoPlayer));

        //getJLanguageMenu(menu);
        // Menu de idioma dinâmico (usa I18N.get diretamente)
        getMainLanguageMenu(menu);

        // === ARQUIVOS RECENTES ===
        addRecentFilesToMenu(menu, videoPlayer);

        // Atualizar textos do menu de playlist e temas
        updateMainPopupTexts();

        // Mostrar menu
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenu buildPlaylistMenu(VideoPlayer videoPlayer) {
        mainPlaylistMenu = new JMenu();

        mainOpenPlaylistItem = new JMenuItem();
        mainOpenPlaylistItem.addActionListener(e -> videoPlayer.showPlaylistDialog());

        autoPlayItem = new JCheckBoxMenuItem("", true);
        autoPlayItem.addActionListener(e -> autoPlayNext = autoPlayItem.isSelected());

        mainPlaylistMenu.add(mainOpenPlaylistItem);
        mainPlaylistMenu.addSeparator();
        mainPlaylistMenu.add(autoPlayItem);

        return mainPlaylistMenu;
    }

    private JMenu buildThemeMenu(VideoPlayer videoPlayer) {
        themeMenu = new JMenu();

        if (themeManager == null) {
            JMenuItem noTheme = new JMenuItem();
            noTheme.setEnabled(false);
            noTheme.setText(I18N.get("theme.unavailable"));
            themeMenu.add(noTheme);
            return themeMenu;
        }

        String currentTheme = themeManager.getCurrentTheme();
        ButtonGroup group = new ButtonGroup();

        for (Map.Entry<String, ThemeManager.ThemeInfo> entry : themeManager.getAvailableThemes().entrySet()) {
            String themeName = entry.getKey();
            ThemeManager.ThemeInfo info = entry.getValue();

            JRadioButtonMenuItem item = new JRadioButtonMenuItem(info.getDisplayName());
            item.setSelected(themeName.equals(currentTheme));

            item.addActionListener(ev -> {
                if (!themeName.equals(themeManager.getCurrentTheme())) {
                    JFrame frame = videoPlayer.getFrame();
                    themeManager.changeThemeSilent(themeName, frame);
                    VideoPanel.this.revalidate();
                    VideoPanel.this.repaint();
                }
            });

            group.add(item);
            themeMenu.add(item);
        }

        themeMenu.addSeparator();

        JMenuItem info = new JMenuItem(I18N.get("theme.current") + ": " +
                themeManager.getCurrentThemeInfo().getDisplayName());
        info.setEnabled(false);
        themeMenu.add(info);

        return themeMenu;
    }

    private void addRecentFilesToMenu(JPopupMenu contextMenu, VideoPlayer videoPlayer) {
        if (recentFilesManager == null) {
            return;
        }

        List<RecentFilesManager.RecentFile> recentFiles = recentFilesManager.getRecentFiles();

        if (!recentFiles.isEmpty()) {
            contextMenu.addSeparator();

            recentFilesTitle = new JMenuItem();
            recentFilesTitle.setEnabled(false);
            recentFilesTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
            recentFilesTitle.setText(I18N.get("recent.title"));
            contextMenu.add(recentFilesTitle);

            int index = 1;
            for (RecentFilesManager.RecentFile recentFile : recentFiles) {
                String menuText = recentFile.getIcon() + " " + index + ". " + recentFile.getDisplayName();

                JMenuItem fileItem = new JMenuItem(menuText);
                fileItem.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                fileItem.setToolTipText(recentFile.getFilePath());

                if (index <= 5) {
                    fileItem.setAccelerator(KeyStroke.getKeyStroke(
                            KeyEvent.VK_0 + index,
                            InputEvent.CTRL_DOWN_MASK
                    ));
                }

                fileItem.addActionListener(ev -> {
                    File file = new File(recentFile.getFilePath());
                    if (file.exists()) {
                        videoPlayer.loadFromRecentFile(file);
                    } else {
                        JOptionPane.showMessageDialog(
                                this,
                                I18N.get("recent.notfound") + "\n" + recentFile.getFilePath(),
                                I18N.get("dialog.error"),
                                JOptionPane.ERROR_MESSAGE
                        );
                        recentFilesManager.removeRecentFile(recentFile.getFilePath());
                    }
                });

                contextMenu.add(fileItem);
                index++;
            }

            contextMenu.addSeparator();

            clearRecentItem = new JMenuItem();
            //clearRecentItem.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            clearRecentItem.setText("🗑️ " + I18N.get("recent.clear"));
            clearRecentItem.addActionListener(ev -> {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        I18N.get("recent.clear.confirm"),
                        I18N.get("dialog.confirm"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    recentFilesManager.clearRecentFiles();
                    JOptionPane.showMessageDialog(
                            this,
                            I18N.get("recent.clear.success"),
                            I18N.get("dialog.success"),
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            });

            contextMenu.add(clearRecentItem);
        }
    }

    private void removeRecentFilesFromMenu(JPopupMenu contextMenu) {
        Component[] components = contextMenu.getComponents();
        int playlistMenuIndex = -1;

        // Encontrar o índice do menu Playlist
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof JMenu) {
                JMenu menu = (JMenu) components[i];
                String menuText = menu.getText();
                // Verificar em ambos os idiomas
                if (menuText.equals("Playlist") ||
                        menuText.equals(I18N.get("menu.playlist"))) {
                    playlistMenuIndex = i;
                    break;
                }
            }
        }

        // Remover tudo após o menu Playlist
        if (playlistMenuIndex >= 0) {
            while (contextMenu.getComponentCount() > playlistMenuIndex + 1) {
                contextMenu.remove(playlistMenuIndex + 1);
            }
        }
    }

    public void setupAudioContextMenu(VideoPlayer videoPlayer, FFmpegFrameGrabber grabber) {
        JPopupMenu contextMenu = new JPopupMenu();

        // === Menu de Espectro ===
        spectrumMenu = new JMenu();
        spectrumMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Submenu: Layout do Spectrum
        spectrumLayoutMenu = new JMenu();
        ButtonGroup layoutGroup = new ButtonGroup();

        linearLayoutMenuItem = new JRadioButtonMenuItem("", true);
        linearLayoutMenuItem.addActionListener(e -> setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.LINEAR));

        circularLayoutMenuItem = new JRadioButtonMenuItem("");
        circularLayoutMenuItem.addActionListener(e -> setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.CIRCULAR));

        waveLayoutMenuItem = new JRadioButtonMenuItem("");
        waveLayoutMenuItem.addActionListener(e -> {
            setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.WAVEFORM);
            spectrumPanel.setWaveformLayers(5);
            spectrumPanel.setWaveformAmplitude(180);
            spectrumPanel.setWaveformLayerSpacing(0.8f);
            spectrumPanel.setWaveformFilled(false);
        });

        layoutGroup.add(linearLayoutMenuItem);
        layoutGroup.add(circularLayoutMenuItem);
        layoutGroup.add(waveLayoutMenuItem);

        spectrumLayoutMenu.add(linearLayoutMenuItem);
        spectrumLayoutMenu.add(circularLayoutMenuItem);
        spectrumLayoutMenu.add(waveLayoutMenuItem);

        // Submenu: Modo de Cor
        colorModeMenu = new JMenu();
        ButtonGroup colorGroup1 = new ButtonGroup();

        defaultColorItem = new JRadioButtonMenuItem("");
        defaultColorItem.addActionListener(e -> setSpectrumColorMode(AudioSpectrumPanel.ColorMode.DEFAULT));

        coverColorItem = new JRadioButtonMenuItem("", true);
        coverColorItem.addActionListener(e -> {
            spectrumPanel.setCoverColorDifferenceMultiplier(2.0f);
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.COVER_PALETTE);
        });

        customColorItem = new JRadioButtonMenuItem("");
        customColorItem.addActionListener(e -> showCustomColorDialog());

        colorGroup1.add(defaultColorItem);
        colorGroup1.add(coverColorItem);
        colorGroup1.add(customColorItem);

        colorModeMenu.add(defaultColorItem);
        colorModeMenu.add(coverColorItem);
        colorModeMenu.add(customColorItem);
        colorModeMenu.addSeparator();

        // Paletas pré-definidas
        synthwaveItem = new JMenuItem("");
        synthwaveItem.addActionListener(e -> {
            setSpectrumCustomColors(
                    new Color(100, 0, 200),
                    new Color(200, 0, 255),
                    new Color(255, 100, 255)
            );
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);
        });

        matrixItem = new JMenuItem("");
        matrixItem.addActionListener(e -> {
            setSpectrumCustomColors(
                    new Color(0, 255, 0),
                    new Color(0, 255, 128),
                    new Color(128, 255, 255)
            );
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);
        });

        fireItem = new JMenuItem("");
        fireItem.addActionListener(e -> {
            setSpectrumCustomColors(
                    new Color(255, 100, 0),
                    new Color(255, 200, 0),
                    new Color(255, 255, 100)
            );
            setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);
        });

        iceItem = new JMenuItem("");
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

        spectrumMenu.add(spectrumLayoutMenu);
        spectrumMenu.add(colorModeMenu);
        spectrumMenu.addSeparator();

        // Checkboxes de Visibilidade
        showSpectrumItem = new JCheckBoxMenuItem("", true);
        showSpectrumItem.addActionListener(e -> setSpectrumVisible(showSpectrumItem.isSelected()));

        showCoverItem = new JCheckBoxMenuItem("", true);
        showCoverItem.addActionListener(e -> setCoverArtVisible(showCoverItem.isSelected()));

        spectrumMenu.add(showSpectrumItem);
        spectrumMenu.add(showCoverItem);
        spectrumMenu.addSeparator();

        // Submenu: Reflexo
        reflectionMenu = new JMenu();

        enableReflectionItem = new JCheckBoxMenuItem("", true);
        enableReflectionItem.addActionListener(e -> setSpectrumReflection(enableReflectionItem.isSelected()));

        reflectionHeightMenu = new JMenu();
        String[] heightLabels = {"25%", "50%", "75%", "100%"};
        float[] heightValues = {0.25f, 0.5f, 0.75f, 1.0f};

        ButtonGroup heightGroup = new ButtonGroup();
        for (int i = 0; i < heightLabels.length; i++) {
            final float height = heightValues[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(heightLabels[i], i == 1);
            item.addActionListener(e -> setSpectrumReflectionHeight(height));
            heightGroup.add(item);
            reflectionHeightMenu.add(item);
        }

        reflectionOpacityMenu = new JMenu();
        String[] opacityLabels = {"25%", "50%", "75%", "100%"};
        int[] opacityValues = {64, 128, 192, 255};

        ButtonGroup opacityGroup = new ButtonGroup();
        for (int i = 0; i < opacityLabels.length; i++) {
            final int opacity = opacityValues[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(opacityLabels[i], i == 1);
            item.addActionListener(e -> setSpectrumReflectionAlpha(opacity));
            opacityGroup.add(item);
            reflectionOpacityMenu.add(item);
        }

        reflectionMenu.add(enableReflectionItem);
        reflectionMenu.add(reflectionHeightMenu);
        reflectionMenu.add(reflectionOpacityMenu);

        spectrumMenu.add(reflectionMenu);
        contextMenu.add(spectrumMenu);

        // === Menu de Áudio ===
        audioMenu2 = new JMenu();
        audioMenu2.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Normalização
        enableNormalizationItem = new JCheckBoxMenuItem("", false);
        enableNormalizationItem.addActionListener(e ->
                audioLoudnessManager.setAudioNormalizationEnabled(enableNormalizationItem.isSelected()));

        audioMenu2.add(enableNormalizationItem);
        audioMenu2.addSeparator();

        // Submenu: Presets de Loudness
        loudnessMenu = new JMenu();
        ButtonGroup loudnessGroup = new ButtonGroup();

        streamingItem = new JRadioButtonMenuItem("");
        streamingItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-14.0f));

        quietItem = new JRadioButtonMenuItem("", true);
        quietItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-18.0f));

        broadcastItem = new JRadioButtonMenuItem("");
        broadcastItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-23.0f));

        cinemaItem = new JRadioButtonMenuItem("");
        cinemaItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-24.0f));

        loudItem = new JRadioButtonMenuItem("");
        loudItem.addActionListener(e -> audioLoudnessManager.setTargetLoudness(-11.0f));

        customLoudnessItem = new JRadioButtonMenuItem("");
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

        // Submenu: Volume Global
        volumeGainMenu = new JMenu();
        ButtonGroup volumeGroup = new ButtonGroup();

        String[] volumeLabels = {"20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"};
        float[] volumeValues = {0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};

        for (int i = 0; i < volumeLabels.length; i++) {
            final float vol = volumeValues[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(volumeLabels[i], i == 0);
            item.addActionListener(e -> audioLoudnessManager.setGlobalAudioGain(vol));
            volumeGroup.add(item);
            volumeGainMenu.add(item);
        }

        audioMenu2.add(volumeGainMenu);
        audioMenu2.addSeparator();

        // Informações
        audioInfoItem = new JMenuItem("");
        audioInfoItem.addActionListener(e -> {
            String info = audioLoudnessManager.getNormalizationInfo();
            JOptionPane.showMessageDialog(this,
                    info + "\n\n" +
                            I18N.get("audio.normalization") + ": " +
                            (audioLoudnessManager.isAudioNormalizationEnabled() ?
                                    I18N.get("audio.enabled") : I18N.get("audio.disabled")) + "\n" +
                            I18N.get("audio.globalvolume") + ": " +
                            (int) (audioLoudnessManager.getGlobalAudioGain() * 100) + "%",
                    I18N.get("audio.info.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        });

        audioMenu2.add(audioInfoItem);
        contextMenu.add(audioMenu2);

        // === Menu de Playlist ===
        audioPlaylistMenu = new JMenu();
        audioPlaylistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        audioOpenPlaylistItem = new JMenuItem();
        audioOpenPlaylistItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        audioOpenPlaylistItem.addActionListener(e -> videoPlayer.showPlaylistDialog());

        audioAutoPlayItem = new JCheckBoxMenuItem("", true);
        audioAutoPlayItem.addActionListener(e -> autoPlayNext = audioAutoPlayItem.isSelected());

        audioPlaylistMenu.add(audioOpenPlaylistItem);
        audioPlaylistMenu.addSeparator();
        audioPlaylistMenu.add(audioAutoPlayItem);

        //Menu de mudança de idioma
        getAudioLanguageMenu(contextMenu);

        contextMenu.add(audioPlaylistMenu);

        // Listener do popup
        contextMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                if (grabber == null) {
                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("audio.noaudio.message"),
                            I18N.get("dialog.warning"),
                            JOptionPane.INFORMATION_MESSAGE);
                    SwingUtilities.invokeLater(() -> contextMenu.setVisible(false));
                    return;
                }
                addRecentFilesToMenu(contextMenu, videoPlayer);
                spectrumMenu.setEnabled(true);
                audioMenu2.setEnabled(true);
                updateLayoutMenuSelection();
            }

            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                removeRecentFilesFromMenu(contextMenu);
            }

            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                removeRecentFilesFromMenu(contextMenu);
            }
        });

        // Adicionar listener de mouse
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

        // IMPORTANTE: Atualizar textos pela primeira vez
        updateAudioMenuTexts();

        // IMPORTANTE: Registrar listener já foi feito no setupVideoContextMenu
        // Não precisa registrar novamente

        // IMPORTANTE: Registrar listener APÓS criar tudo
        I18N.addLanguageChangeListener(this);
    }

    public void setupVideoContextMenu(SubtitleManager subtitleManager, CaptureFrameManager captureFrameManager,
                                      VideoPlayer videoPlayer, FiltersManager filtersManager, String videoFilePath,
                                      FFmpegFrameGrabber grabber, JButton nextFrameButton, int totalAudioStreams,
                                      int currentAudioStream, Map<Integer, String> audioStreamNames, String ffmpegPath) {

        JPopupMenu contextMenu = new JPopupMenu();

        // Menu de áudio
        audioMenu = new JMenu();
        contextMenu.add(audioMenu);

        // Menu de legendas
        subtitleMenu = new JMenu();
        noSubtitle = new JMenuItem();
        noSubtitle.addActionListener(e -> {
            subtitleManager.setCurrentSubtitleStream(-1);
            subtitleManager.setCurrentSubtitleText("");
            repaint();
        });
        subtitleMenu.add(noSubtitle);

        loadExternal = new JMenuItem();
        loadExternal.addActionListener(e -> subtitleManager.loadExternalSubtitle(videoFilePath, videoPlayer));
        subtitleMenu.add(loadExternal);

        contextMenu.add(subtitleMenu);

        // Menu de configurações de legenda
        subtitleSettingsMenu = new JMenu();

        // Submenu de tamanho
        sizeMenu = new JMenu();
        int[] sizes = {16, 20, 24, 28, 32, 36, 40, 48, 56, 64};
        ButtonGroup sizeGroup = new ButtonGroup();

        for (int size : sizes) {
            int savedSize = configManager.getSavedSubtitleSize();

            JRadioButtonMenuItem sizeItem = new JRadioButtonMenuItem(size + "px");
            sizeItem.setSelected(size == savedSize);
            sizeItem.addActionListener(e -> {
                subtitleManager.setBaseSubtitleFontSize(size);
                System.out.println("Tamanho base da legenda alterado para: " + size);
                configManager.saveSubtitleSize(size);
                repaint();
            });
            sizeGroup.add(sizeItem);
            sizeMenu.add(sizeItem);
        }

        subtitleSettingsMenu.add(sizeMenu);
        Color savedColor = configManager.getSavedSubtitleColor();
        // Submenu de cor
        colorMenu = new JMenu();
        ButtonGroup colorGroup = new ButtonGroup();

        whiteColor = new JRadioButtonMenuItem();
        whiteColor.setSelected(savedColor.equals(Color.WHITE));
        whiteColor.addActionListener(e -> {
            subtitleManager.setSubtitleColor(Color.WHITE);
            configManager.saveSubtitleColor(Color.WHITE);
            repaint();
        });
        colorGroup.add(whiteColor);
        colorMenu.add(whiteColor);

        yellowColor = new JRadioButtonMenuItem();
        yellowColor.setSelected(savedColor.equals(Color.YELLOW));
        yellowColor.addActionListener(e -> {
            subtitleManager.setSubtitleColor(Color.YELLOW);
            configManager.saveSubtitleColor(Color.YELLOW);
            repaint();
        });
        colorGroup.add(yellowColor);
        colorMenu.add(yellowColor);

        subtitleSettingsMenu.add(colorMenu);
        contextMenu.add(subtitleSettingsMenu);

        // Menu de Performance
        performanceMenu = new JMenu();

        hwAccelItem = new JCheckBoxMenuItem();
        hwAccelItem.setSelected(videoPlayer.hardwareAccelerationEnabled);
        hwAccelItem.addActionListener(e -> {
            videoPlayer.hardwareAccelerationEnabled = hwAccelItem.isSelected();
            if (grabber != null) {
                JOptionPane.showMessageDialog(videoPlayer,
                        I18N.get("menu.performance.hwaccel.restart"),
                        I18N.get("dialog.warning"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        performanceMenu.add(hwAccelItem);

        performanceMenu.addSeparator();
        frameSkipMenu = new JMenu();

        ButtonGroup frameSkipGroup = new ButtonGroup();
        int[] skipValues = {1, 2, 3, 5, 10, 15, 30};

        for (int skipValue : skipValues) {
            JRadioButtonMenuItem skipItem = new JRadioButtonMenuItem(skipValue + " frame" + (skipValue > 1 ? "s" : ""));
            skipItem.setSelected(skipValue == framesToSkip);

            final int value = skipValue;
            skipItem.addActionListener(e -> {
                framesToSkip = value;
                if (nextFrameButton != null) {
                    nextFrameButton.setToolTipText(I18N.get("button.nextframe.tooltip") + " " + framesToSkip + " frame" + (framesToSkip > 1 ? "s" : ""));
                }
            });

            frameSkipGroup.add(skipItem);
            frameSkipMenu.add(skipItem);
        }

        performanceMenu.add(frameSkipMenu);
        contextMenu.add(performanceMenu);

        // Menu de Captura
        captureMenu = new JMenu();

        silentCaptureItem = new JCheckBoxMenuItem();
        silentCaptureItem.setSelected(configManager.isSilentCapture());
        silentCaptureItem.addActionListener(e -> {
            silentCapture = silentCaptureItem.isSelected();
            configManager.saveSilentCapture(silentCapture);
        });
        captureMenu.add(silentCaptureItem);

        captureMenu.addSeparator();

        selectFolderItem = new JMenuItem();
        selectFolderItem.addActionListener(e -> captureFrameManager.selectCaptureFolder(customCapturePath, videoFilePath, videoPlayer));
        captureMenu.add(selectFolderItem);

        resetFolderItem = new JMenuItem();
        resetFolderItem.addActionListener(e -> captureFrameManager.resetCaptureFolder( videoPlayer));
        captureMenu.add(resetFolderItem);

        captureMenu.addSeparator();

        showCurrentFolder = new JMenuItem();
        showCurrentFolder.addActionListener(e -> {
            String currentFolder;
            if (customCapturePath != null && !customCapturePath.isEmpty()) {
                currentFolder = customCapturePath;
            } else if (videoFilePath != null) {
                File videoFile = new File(videoFilePath);
                currentFolder = videoFile.getParent();
            } else {
                currentFolder = I18N.get("menu.capture.nofolder");
            }

            JOptionPane.showMessageDialog(videoPlayer,
                    I18N.get("menu.capture.currentfolder") + "\n" + currentFolder,
                    I18N.get("menu.capture.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        captureMenu.add(showCurrentFolder);

        contextMenu.add(captureMenu);

        // Menu de Captura em Lote
        batchCaptureMenu = new JMenu();

        intervalMenu = new JMenu();
        ButtonGroup intervalGroup = new ButtonGroup();
        int[] intervals = {2, 3, 5, 10, 15, 30, 60, 120};

        for (int interval : intervals) {
            JRadioButtonMenuItem intervalItem = new JRadioButtonMenuItem(I18N.get("menu.batch.every") + " " + interval + " frame" + (interval > 2 ? "s" : ""));
            intervalItem.setSelected(interval == batchCaptureInterval);

            final int value = interval;
            intervalItem.addActionListener(e -> {
                batchCaptureInterval = value;
            });

            intervalGroup.add(intervalItem);
            intervalMenu.add(intervalItem);
        }

        batchCaptureMenu.add(intervalMenu);
        batchCaptureMenu.addSeparator();

        selectBatchFolderItem = new JMenuItem();
        selectBatchFolderItem.addActionListener(e -> captureFrameManager.selectBatchCaptureFolder(batchCapturePath, videoFilePath, videoPlayer));
        batchCaptureMenu.add(selectBatchFolderItem);

        resetBatchFolderItem = new JMenuItem();
        resetBatchFolderItem.addActionListener(e -> captureFrameManager.resetCaptureFolder(videoPlayer));
        batchCaptureMenu.add(resetBatchFolderItem);

        batchCaptureMenu.addSeparator();

        showBatchFolder = new JMenuItem();
        showBatchFolder.addActionListener(e -> {
            String currentFolder;
            if (batchCapturePath != null && !batchCapturePath.isEmpty()) {
                currentFolder = batchCapturePath;
            } else if (videoFilePath != null) {
                File videoFile = new File(videoFilePath);
                currentFolder = videoFile.getParent();
            } else {
                currentFolder = I18N.get("menu.batch.nofolder");
            }

            JOptionPane.showMessageDialog(videoPlayer,
                    I18N.get("menu.batch.currentfolder") + "\n" + currentFolder,
                    I18N.get("menu.batch.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        batchCaptureMenu.add(showBatchFolder);

        contextMenu.add(batchCaptureMenu);

        // Menu de Filtros
        filtersMenu = new JMenu();
        videoResolutionCheck( grabber);
        brightnessItem = new JMenuItem();
        brightnessItem.addActionListener(e -> filtersManager.showBrightnessDialog(videoPlayer));
        filtersMenu.add(brightnessItem);

        filtersMenu.addSeparator();

        resetFiltersItem = new JMenuItem();
        resetFiltersItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(videoPlayer,
                    I18N.get("menu.filters.reset.confirm"),
                    I18N.get("dialog.confirm"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                filtersManager.resetFilters(videoPlayer);
            }
        });
        filtersMenu.add(resetFiltersItem);

        filtersMenu.addSeparator();

        showFiltersItem = new JMenuItem();



        showFiltersItem.addActionListener(e -> {
            StringBuilder info = new StringBuilder();
            info.append(I18N.get("menu.filters.active.title")).append("\n\n");

            if (!filtersManager.isFiltersEnabled() || filtersManager.buildFilterString() == null) {
                info.append(I18N.get("menu.filters.active.none"));
            } else {
                if (filtersManager.getBrightness() != 0.0) {
                    info.append(String.format("• %s: %.2f\n", I18N.get("filter.brightness"), filtersManager.getBrightness()));
                }
                if (filtersManager.getContrast() != 1.0) {
                    info.append(String.format("• %s: %.2f\n", I18N.get("filter.contrast"), filtersManager.getContrast()));
                }
                if (filtersManager.getGamma() != 1.0) {
                    info.append(String.format("• %s: %.2f\n", I18N.get("filter.gamma"), filtersManager.getGamma()));
                }
                if (filtersManager.getSaturation() != 1.0) {
                    info.append(String.format("• %s: %.2f\n", I18N.get("filter.saturation"), filtersManager.getSaturation()));
                }

                info.append("\n").append(I18N.get("menu.filters.active.ffmpeg")).append(":\n").append(filtersManager.buildFilterString());
            }

            JOptionPane.showMessageDialog(videoPlayer,
                    info.toString(),
                    I18N.get("menu.filters.active"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        filtersMenu.add(showFiltersItem);

        contextMenu.add(filtersMenu);

        // Tela cheia
        fullscreenItem = new JCheckBoxMenuItem();
        fullscreenItem.addActionListener(e -> {
            videoPlayer.toggleFullScreen();
        });
        contextMenu.add(fullscreenItem);

        //Menu de mudança de idioma
        getVideoLanguageMenu(contextMenu);

        // Menu de Playlist
        playlistMenu = new JMenu();
        playlistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        openPlaylistItem = new JMenuItem();
        openPlaylistItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        openPlaylistItem.addActionListener(e -> videoPlayer.showPlaylistDialog());

        autoPlayItem = new JCheckBoxMenuItem();
        autoPlayItem.setSelected(true);
        autoPlayItem.addActionListener(e -> {
            autoPlayNext = autoPlayItem.isSelected();
        });

        playlistMenu.add(openPlaylistItem);
        playlistMenu.addSeparator();
        playlistMenu.add(autoPlayItem);

        contextMenu.add(playlistMenu);

        //Atualizar estado ao abrir menu
        contextMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                updateContextMenus(audioMenu, subtitleMenu, videoPlayer, subtitleManager, totalAudioStreams, currentAudioStream, audioStreamNames, videoFilePath, ffmpegPath);
                GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                fullscreenItem.setSelected(gd.getFullScreenWindow() == videoPlayer);


                subtitleMenu.setEnabled(true);
                audioMenu.setEnabled(true);
                captureMenu.setEnabled(true);
                batchCaptureMenu.setEnabled(true);
                performanceMenu.setEnabled(true);
                intervalMenu.setEnabled(true);
                fullscreenItem.setEnabled(true);
                subtitleSettingsMenu.setEnabled(true);

                // ADICIONAR arquivos recentes dinamicamente ao menu principal
                addRecentFilesToMenu(contextMenu, videoPlayer);
            }

            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                // LIMPAR itens de arquivos recentes ao fechar
                removeRecentFilesFromMenu(contextMenu);
            }

            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                // LIMPAR itens de arquivos recentes ao cancelar
                removeRecentFilesFromMenu(contextMenu);
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

        // IMPORTANTE: Atualizar textos pela primeira vez
        updateVideoMenuTexts();

        // IMPORTANTE: Registrar listener APÓS criar tudo
        I18N.addLanguageChangeListener(this);
    }

    private void videoResolutionCheck(FFmpegFrameGrabber grabber) {


        // Verificar resolução do vídeo e habilitar/desabilitar menu de filtros
        videoWidth = grabber.getImageWidth();
        videoHeight = grabber.getImageHeight();
        boolean isHighResolution = (videoWidth > 1280 || videoHeight > 720);

        if (isHighResolution) {
            System.out.println("-----------------------HIGHRESOLUTION");
            filtersMenu.setEnabled(false);

        } else {
            filtersMenu.setEnabled(true);
            filtersMenu.setToolTipText(null);
        }
    }

    // Método para criar menu de idioma para VÍDEO
    private JMenu getVideoLanguageMenu(JPopupMenu contextMenu) {
        videoLanguageMenu = new JMenu();
        videoLanguageMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        videoLanguageGroup = new ButtonGroup();

        videoUsItem = new JRadioButtonMenuItem();
        videoUsItem.addActionListener(e -> changeLanguage(Locale.of("en", "US")));

        videoPtItem = new JRadioButtonMenuItem();
        videoPtItem.addActionListener(e -> changeLanguage(Locale.of("pt", "BR")));

        videoLanguageGroup.add(videoUsItem);
        videoLanguageGroup.add(videoPtItem);

        videoLanguageMenu.add(videoUsItem);
        videoLanguageMenu.add(videoPtItem);
        contextMenu.add(videoLanguageMenu);

        // Atualizar estados iniciais
        updateVideoLanguageMenuState();

        return videoLanguageMenu;
    }

    // Método para criar menu de idioma para ÁUDIO
    private JMenu getAudioLanguageMenu(JPopupMenu contextMenu) {
        audioLanguageMenu = new JMenu();
        audioLanguageMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        audioLanguageGroup = new ButtonGroup();

        audioUsItem = new JRadioButtonMenuItem();
        audioUsItem.addActionListener(e -> changeLanguage(Locale.of("en", "US")));

        audioPtItem = new JRadioButtonMenuItem();
        audioPtItem.addActionListener(e -> changeLanguage(Locale.of("pt", "BR")));

        audioLanguageGroup.add(audioUsItem);
        audioLanguageGroup.add(audioPtItem);

        audioLanguageMenu.add(audioUsItem);
        audioLanguageMenu.add(audioPtItem);
        contextMenu.add(audioLanguageMenu);

        // Atualizar estados iniciais
        updateAudioLanguageMenuState();

        return audioLanguageMenu;
    }

    // Atualizar estado do menu de idioma de VÍDEO (com JRadioButtonMenuItem)
    private void updateVideoLanguageMenuState() {
        if (videoUsItem == null || videoPtItem == null) {
            return;
        }

        Locale currentLocale = I18N.getCurrentLocale();
        String currentLang = currentLocale.getLanguage();

        videoUsItem.setText(I18N.get("language.item1"));
        videoPtItem.setText(I18N.get("language.item2"));

        // Selecionar o idioma atual
        if ("en".equals(currentLang)) {
            videoUsItem.setSelected(true);
        } else if ("pt".equals(currentLang)) {
            videoPtItem.setSelected(true);
        }
    }

    // Atualizar estado do menu de idioma de ÁUDIO (com JRadioButtonMenuItem)
    private void updateAudioLanguageMenuState() {
        if (audioUsItem == null || audioPtItem == null) {
            return;
        }

        Locale currentLocale = I18N.getCurrentLocale();
        String currentLang = currentLocale.getLanguage();

        audioUsItem.setText(I18N.get("language.item1"));
        audioPtItem.setText(I18N.get("language.item2"));

        // Selecionar o idioma atual
        if ("en".equals(currentLang)) {
            audioUsItem.setSelected(true);
        } else if ("pt".equals(currentLang)) {
            audioPtItem.setSelected(true);
        }
    }

    // Menu dinâmico (popup principal) com JRadioButtonMenuItem
    private JMenu getMainLanguageMenu(JPopupMenu contextMenu) {

        JMenu languageMenu = new JMenu(I18N.get("language.text"));
        languageMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        Locale currentLocale = I18N.getCurrentLocale();
        String currentLang = currentLocale.getLanguage();

        ButtonGroup languageGroup = new ButtonGroup();

        JRadioButtonMenuItem usItem = new JRadioButtonMenuItem(I18N.get("language.item1"));
        usItem.setSelected("en".equals(currentLang));
        usItem.addActionListener(e -> changeLanguage(Locale.of("en", "US")));

        JRadioButtonMenuItem ptItem = new JRadioButtonMenuItem(I18N.get("language.item2"));
        ptItem.setSelected("pt".equals(currentLang));
        ptItem.addActionListener(e -> changeLanguage(Locale.of("pt", "BR")));

        languageGroup.add(usItem);
        languageGroup.add(ptItem);

        languageMenu.add(usItem);
        languageMenu.add(ptItem);
        contextMenu.add(languageMenu);
        return languageMenu;
    }

    private void updateVideoMenuTexts() {
        // Verificar se os componentes foram inicializados
        if (audioMenu != null) {
            audioMenu.setText(I18N.get("menu.audio"));
        }

        if (subtitleMenu != null) {
            subtitleMenu.setText(I18N.get("menu.subtitle"));
        }

        if (noSubtitle != null) {
            noSubtitle.setText(I18N.get("menu.subtitle.disabled"));
        }

        if (loadExternal != null) {
            loadExternal.setText(I18N.get("menu.subtitle.loadexternal"));
        }

        if (subtitleSettingsMenu != null) {
            subtitleSettingsMenu.setText(I18N.get("menu.subtitle.settings"));
        }

        if (sizeMenu != null) {
            sizeMenu.setText(I18N.get("menu.subtitle.fontsize"));
        }

        if (colorMenu != null) {
            colorMenu.setText(I18N.get("menu.subtitle.color"));
        }

        if (whiteColor != null) {
            whiteColor.setText(I18N.get("color.white"));
        }

        if (yellowColor != null) {
            yellowColor.setText(I18N.get("color.yellow"));
        }

        if (performanceMenu != null) {
            performanceMenu.setText(I18N.get("menu.performance"));
        }

        if (hwAccelItem != null) {
            hwAccelItem.setText(I18N.get("menu.performance.hwaccel"));
        }

        if (frameSkipMenu != null) {
            frameSkipMenu.setText(I18N.get("menu.performance.frameskip"));
        }

        if (captureMenu != null) {
            captureMenu.setText(I18N.get("menu.capture"));
        }

        if (silentCaptureItem != null) {
            silentCaptureItem.setText(I18N.get("menu.capture.silent"));
        }

        if (selectFolderItem != null) {
            selectFolderItem.setText(I18N.get("menu.capture.selectfolder"));
        }

        if (resetFolderItem != null) {
            resetFolderItem.setText(I18N.get("menu.capture.resetfolder"));
        }

        if (showCurrentFolder != null) {
            showCurrentFolder.setText(I18N.get("menu.capture.showcurrent"));
        }

        if (batchCaptureMenu != null) {
            batchCaptureMenu.setText(I18N.get("menu.batch"));
        }

        if (intervalMenu != null) {
            intervalMenu.setText(I18N.get("menu.batch.interval"));
        }

        if (selectBatchFolderItem != null) {
            selectBatchFolderItem.setText(I18N.get("menu.batch.selectfolder"));
        }

        if (resetBatchFolderItem != null) {
            resetBatchFolderItem.setText(I18N.get("menu.batch.resetfolder"));
        }

        if (showBatchFolder != null) {
            showBatchFolder.setText(I18N.get("menu.batch.showcurrent"));
        }

        if (filtersMenu != null) {
            filtersMenu.setText(I18N.get("menu.filters"));
        }

        if (brightnessItem != null) {
            brightnessItem.setText(I18N.get("menu.filters.brightness"));
        }

        if (resetFiltersItem != null) {
            resetFiltersItem.setText(I18N.get("menu.filters.reset"));
        }

        if (showFiltersItem != null) {
            showFiltersItem.setText(I18N.get("menu.filters.active"));
        }

        if (fullscreenItem != null) {
            fullscreenItem.setText(I18N.get("menu.fullscreen"));
        }

        if (playlistMenu != null) {
            playlistMenu.setText(I18N.get("menu.playlist"));
        }

        if (openPlaylistItem != null) {
            openPlaylistItem.setText(I18N.get("menu.playlist.manage"));
        }

        if (autoPlayItem != null) {
            autoPlayItem.setText(I18N.get("menu.playlist.autoplay"));
        }

        // Menu de idioma do vídeo
        if (videoLanguageMenu != null) {
            videoLanguageMenu.setText(I18N.get("language.text"));
        }
        if (videoUsItem != null) {
            videoUsItem.setText(I18N.get("language.item1"));
        }
        if (videoPtItem != null) {
            videoPtItem.setText(I18N.get("language.item2"));
        }

        // Textos do menu de vídeo (já existente)
        if (audioMenu != null) {
            audioMenu.setText(I18N.get("menu.audio"));
        }

        if(filtersMenu != null) {
            filtersMenu.setToolTipText(String.format(
                    I18N.get("video.highResolutionFilterBlock.toolTipText") + " %dx%d)",
                    videoWidth, videoHeight
            ));
        }


        updateAudioMenuTexts();
        updateMainPopupTexts();

    }

    // Método específico para atualizar textos do menu de áudio
    private void updateAudioMenuTexts() {
        if (spectrumMenu != null) {
            spectrumMenu.setText(I18N.get("audio.spectrum"));
        }

        if (spectrumLayoutMenu != null) {
            spectrumLayoutMenu.setText(I18N.get("audio.spectrum.layout"));
        }

        if (linearLayoutMenuItem != null) {
            linearLayoutMenuItem.setText(I18N.get("audio.spectrum.linear"));
        }

        if (circularLayoutMenuItem != null) {
            circularLayoutMenuItem.setText(I18N.get("audio.spectrum.circular"));
        }

        if (waveLayoutMenuItem != null) {
            waveLayoutMenuItem.setText(I18N.get("audio.spectrum.wave"));
        }

        if (colorModeMenu != null) {
            colorModeMenu.setText(I18N.get("audio.spectrum.colormode"));
        }

        if (defaultColorItem != null) {
            defaultColorItem.setText(I18N.get("audio.spectrum.color.default"));
        }

        if (coverColorItem != null) {
            coverColorItem.setText(I18N.get("audio.spectrum.color.cover"));
        }

        if (customColorItem != null) {
            customColorItem.setText(I18N.get("audio.spectrum.color.custom"));
        }

        if (synthwaveItem != null) {
            synthwaveItem.setText(I18N.get("audio.spectrum.palette.synthwave"));
        }

        if (matrixItem != null) {
            matrixItem.setText(I18N.get("audio.spectrum.palette.matrix"));
        }

        if (fireItem != null) {
            fireItem.setText(I18N.get("audio.spectrum.palette.fire"));
        }

        if (iceItem != null) {
            iceItem.setText(I18N.get("audio.spectrum.palette.ice"));
        }

        if (showSpectrumItem != null) {
            showSpectrumItem.setText(I18N.get("audio.spectrum.show"));
        }

        if (showCoverItem != null) {
            showCoverItem.setText(I18N.get("audio.spectrum.showcover"));
        }

        if (reflectionMenu != null) {
            reflectionMenu.setText(I18N.get("audio.spectrum.reflection"));
        }

        if (enableReflectionItem != null) {
            enableReflectionItem.setText(I18N.get("audio.spectrum.reflection.enable"));
        }

        if (reflectionHeightMenu != null) {
            reflectionHeightMenu.setText(I18N.get("audio.spectrum.reflection.height"));
        }

        if (reflectionOpacityMenu != null) {
            reflectionOpacityMenu.setText(I18N.get("audio.spectrum.reflection.opacity"));
        }

        if (audioMenu2 != null) {
            audioMenu2.setText(I18N.get("audio.menu"));
        }

        if (enableNormalizationItem != null) {
            enableNormalizationItem.setText(I18N.get("audio.normalization.enable"));
        }

        if (loudnessMenu != null) {
            loudnessMenu.setText(I18N.get("audio.loudness"));
        }

        if (streamingItem != null) {
            streamingItem.setText(I18N.get("audio.loudness.streaming"));
            streamingItem.setToolTipText(I18N.get("audio.loudness.streaming.tooltip"));
        }

        if (quietItem != null) {
            quietItem.setText(I18N.get("audio.loudness.quiet"));
            quietItem.setToolTipText(I18N.get("audio.loudness.quiet.tooltip"));
        }

        if (broadcastItem != null) {
            broadcastItem.setText(I18N.get("audio.loudness.broadcast"));
            broadcastItem.setToolTipText(I18N.get("audio.loudness.broadcast.tooltip"));
        }

        if (cinemaItem != null) {
            cinemaItem.setText(I18N.get("audio.loudness.cinema"));
            cinemaItem.setToolTipText(I18N.get("audio.loudness.cinema.tooltip"));
        }

        if (loudItem != null) {
            loudItem.setText(I18N.get("audio.loudness.loud"));
            loudItem.setToolTipText(I18N.get("audio.loudness.loud.tooltip"));
        }

        if (customLoudnessItem != null) {
            customLoudnessItem.setText(I18N.get("audio.loudness.custom"));
        }

        if (volumeGainMenu != null) {
            volumeGainMenu.setText(I18N.get("audio.globalvolume"));
        }

        if (audioInfoItem != null) {
            audioInfoItem.setText(I18N.get("audio.info"));
        }

        if (audioPlaylistMenu != null) {
            audioPlaylistMenu.setText(I18N.get("menu.playlist"));
        }

        if (audioOpenPlaylistItem != null) {
            audioOpenPlaylistItem.setText(I18N.get("menu.playlist.manage"));
        }

        if (audioAutoPlayItem != null) {
            audioAutoPlayItem.setText(I18N.get("menu.playlist.autoplay"));
        }
        // Menu de idioma do áudio
        if (audioLanguageMenu != null) {
            audioLanguageMenu.setText(I18N.get("language.text"));
        }
        if (audioUsItem != null) {
            audioUsItem.setText(I18N.get("language.item1"));
        }
        if (audioPtItem != null) {
            audioPtItem.setText(I18N.get("language.item2"));
        }

    }

    // Método específico para atualizar textos do popup principal
    private void updateMainPopupTexts() {
        if (mainPlaylistMenu != null) {
            mainPlaylistMenu.setText("\uD83D\uDCCB " + I18N.get("menu.playlist"));
        }

        if (mainOpenPlaylistItem != null) {
            mainOpenPlaylistItem.setText(I18N.get("main.playlist.new"));
        }

        if (autoPlayItem != null) {
            autoPlayItem.setText(I18N.get("menu.playlist.autoplay"));
        }

        if (themeMenu != null) {
            themeMenu.setText("\uD83C\uDFA8 " + I18N.get("theme.menu"));
        }
    }

    private void changeLanguage(Locale locale) {
        I18N.setLocale(locale);
        configManager.saveLocale(locale);

    }
    @Override
    public void onLanguageChanged(Locale newLocale) {
        System.out.println("VideoPanel: Idioma mudou para: " + newLocale);
        updateVideoMenuTexts();
        revalidate();
        repaint();
    }

    // Método de limpeza (chamar quando o painel for removido)
    public void cleanup() {
        I18N.removeLanguageChangeListener(this);
    }


    // Diálogo para escolher cores personalizadas
    private void showCustomColorDialog() {
        Color bottom = JColorChooser.showDialog(this,
                I18N.get("audio.CustomColorDialog.Bottom"),
                new Color(0, 255, 0));

        if (bottom != null) {
            Color middle = JColorChooser.showDialog(this,
                    I18N.get("audio.CustomColorDialog.Middle"),
                    new Color(255, 255, 0));

            if (middle != null) {
                Color top = JColorChooser.showDialog(this,
                        I18N.get("audio.CustomColorDialog.Top"),
                        new Color(255, 0, 0));

                if (top != null) {
                    setSpectrumCustomColors(bottom, middle, top);
                    setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
                    JOptionPane.showMessageDialog(this,
                            I18N.get("audio.CustomColorDialog.ConfirmationDialog.text"),
                            I18N.get("audio.CustomColorDialog.ConfirmationDialog.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }

    // Diálogo para loudness personalizado
    private void showCustomLoudnessDialog() {
        String input = JOptionPane.showInputDialog(this,
                   I18N.get("audio.CustomLoudnessDialog.Text1") + "\n" +
                           I18N.get("audio.CustomLoudnessDialog.Text2") +"\n" +
                           I18N.get("audio.CustomLoudnessDialog.Text3") + "\n" +
                           I18N.get("audio.CustomLoudnessDialog.Text4") ,
                "-18.0");

        if (input != null) {
            try {
                float loudness = Float.parseFloat(input);
                if (loudness >= -30.0f && loudness <= 0.0f) {
                    audioLoudnessManager.setTargetLoudness(loudness);
                    JOptionPane.showMessageDialog(this,
                            I18N.get("audio.CustomLoudnessDialog.ConfirmationDialog.Text") + loudness + " dBFS",
                            I18N.get("audio.CustomLoudnessDialog.ConfirmationDialog.Title"),
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogError.Text"),
                            I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogError.Title"),
                            JOptionPane.WARNING_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogNumberFormatError.Text"),
                        I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogNumberFormatError.Title"),
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
               // item.setSelected(i == currentAudioStream);
                item.setSelected(i == videoPlayer.getCurrentAudioStream());
                item.addActionListener(e -> videoPlayer.switchAudioStream(streamIndex));
                audioMenu.add(item);
            }
        } else {
            noAudio = new JMenuItem();
            noAudio.setText(I18N.get("audioChannelChange.OneOptionOnly"));
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
                item.addActionListener(e -> subtitleManager.switchSubtitleStream(streamIndex, videoFilePath, videoPlayer, ffmpegPath));
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
            // SUBSTITUIR: Desenhar imagem de fundo em vez de texto simples
            Graphics2D g2d = (Graphics2D) g;
            backgroundImageLoader.drawBackground(g2d, getWidth(), getHeight());
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
        return configManager.isSilentCapture();
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
