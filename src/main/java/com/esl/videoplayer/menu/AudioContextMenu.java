package com.esl.videoplayer.menu;

import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.audio.AudioLoudnessManager;
import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.audio.cover.CoverArt;
import com.esl.videoplayer.configuration.AboutDialog;
import com.esl.videoplayer.configuration.AppInfo;
import com.esl.videoplayer.configuration.MediaInfoDialog;
import com.esl.videoplayer.localization.I18N;
import com.esl.videoplayer.playlist.PlayListExecution;
import com.esl.videoplayer.playlist.PlaylistManager;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.function.Consumer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;


public class AudioContextMenu extends AbstractContextMenu {

    // ── Dependências específicas ──────────────────────────────────────────────
    private final AudioLoudnessManager audioLoudnessManager;
    private final FFmpegFrameGrabber grabber;

    // ── Espectro ──────────────────────────────────────────────────────────────
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

    // ── Áudio ─────────────────────────────────────────────────────────────────
    private JMenu audioMenu;
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

    // ── Playlist ──────────────────────────────────────────────────────────────
    private JMenu playlistMenu;
    private JMenuItem openPlaylistItem;
    private JCheckBoxMenuItem autoPlayItem;
    private PlayListExecution playListExecution;
    private JCheckBoxMenuItem playFirtsItemRandom;

    private JMenuItem fileInfoItem;


    private JMenu waveformSettingsMenu;
    private JCheckBoxMenuItem filledItem;
    private JMenuItem layersItem;
    private JMenuItem amplitudeItem;
    private JMenuItem spacingItem;
    private final CoverArt coverArt;          // receba via construtor
    private final String   audioFilePath;     // receba via construtor




    // ── Construtor ────────────────────────────────────────────────────────────
    public AudioContextMenu(MainPanel mainPanel, VideoPlayer videoPlayer,
                            AudioLoudnessManager audioLoudnessManager,
                            FFmpegFrameGrabber grabber, PlaylistManager playlistManager, CoverArt coverArt, String audioFilePath) {
        super(mainPanel, videoPlayer);
        this.audioLoudnessManager = audioLoudnessManager;
        this.grabber = grabber;
        this.coverArt = coverArt;
        this.audioFilePath = audioFilePath;

        playListExecution = new PlayListExecution(mainPanel,videoPlayer,playlistManager);


        AudioSpectrumPanel.ColorMode mode = configManager.getSavedSpectrumColor();
        AudioSpectrumPanel.CustomPalette palette = configManager.getSavedSpectrumCustomPalette();

        if (mode == AudioSpectrumPanel.ColorMode.CUSTOM) {
            // Aplica as cores baseadas na paleta salva se não for 'NONE'
            switch (palette) {
                case SYNTHWAVE -> mainPanel.setSpectrumCustomColors(new Color(100, 0, 200), new Color(200, 0, 255), new Color(255, 100, 255));
                case MATRIX    -> mainPanel.setSpectrumCustomColors(new Color(0, 255, 0), new Color(0, 255, 128), new Color(128, 255, 255));
                case FIRE      -> mainPanel.setSpectrumCustomColors(new Color(255, 100, 0), new Color(255, 200, 0), new Color(255, 255, 100));
                case ICE       -> mainPanel.setSpectrumCustomColors(new Color(0, 100, 200), new Color(0, 200, 255), new Color(200, 255, 255));
                case NONE      -> { /* Aqui você pode carregar cores RGB salvas manualmente se desejar */ }
            }
        }
        mainPanel.setSpectrumColorMode(mode);

    }

    // ── buildMenu ─────────────────────────────────────────────────────────────
    @Override
    protected JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();

        menu.add(buildSpectrumSubmenu());
        menu.add(buildAudioSubmenu());
        menu.add(buildFileInfoItem());
        menu.add(buildAboutItem());
        menu.add(buildLanguageMenu());
        menu.add(buildPlaylistSubmenu());

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                if (grabber == null) {
                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("audio.noaudio.message"),
                            I18N.get("dialog.warning"),
                            JOptionPane.INFORMATION_MESSAGE);
                    SwingUtilities.invokeLater(() -> menu.setVisible(false));
                    return;
                }
                spectrumMenu.setEnabled(true);
                audioMenu.setEnabled(true);
                mainPanel.updateLayoutMenuSelection();
                addRecentFiles(menu);
            }

            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                removeRecentFiles(menu, I18N.get("menu.playlist"));
            }

            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                removeRecentFiles(menu, I18N.get("menu.playlist"));
            }
        });


        return menu;
    }




    // --- novo método ---
    private JMenuItem buildFileInfoItem() {
        fileInfoItem = new JMenuItem();
        fileInfoItem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fileInfoItem.addActionListener(e ->
                MediaInfoDialog.showForAudio(
                        mainPanel,
                        grabber,
                        audioFilePath,
                        coverArt != null ? coverArt.getAudioCoverArt() : null));
        return fileInfoItem;
    }


    private JMenu buildSpectrumSubmenu() {
        spectrumMenu = new JMenu();
        spectrumMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // IMPORTANTE: Constrói o submenu de configurações primeiro (ele inicia ativo ou inativo ocultamente)
        JMenu wfSettings = buildWaveformSettingsSubmenu();

        spectrumMenu.add(buildLayoutSubmenu());
        spectrumMenu.add(wfSettings);
        spectrumMenu.add(buildColorModeSubmenu());
        spectrumMenu.addSeparator();

        showSpectrumItem = new JCheckBoxMenuItem();
        showSpectrumItem.setSelected(configManager.getSavedShowAudioSpectrum());
        showSpectrumItem.addActionListener(e ->{
                    mainPanel.setSpectrumVisible(showSpectrumItem.isSelected());
                    configManager.saveShowAudioSpectrum(showSpectrumItem.isSelected());
                });


        showCoverItem = new JCheckBoxMenuItem();
        showCoverItem.setSelected(configManager.getSavedShowAudioImageCover());
        showCoverItem.addActionListener(e ->{
                mainPanel.setCoverArtVisible(showCoverItem.isSelected());
                configManager.saveShowAudioImageCover(showCoverItem.isSelected());
    });

        spectrumMenu.add(showSpectrumItem);
        spectrumMenu.add(showCoverItem);
        spectrumMenu.addSeparator();
        spectrumMenu.add(buildReflectionSubmenu());
        return spectrumMenu;
    }

    private JMenu buildLayoutSubmenu() {
        spectrumLayoutMenu = new JMenu();
        ButtonGroup grp = new ButtonGroup();

        // Carrega o layout que foi salvo anteriormente
        AudioSpectrumPanel.LayoutMode savedLayout = configManager.getSavedSpectrumLayout();

        linearLayoutMenuItem = new JRadioButtonMenuItem();
        linearLayoutMenuItem.setSelected(savedLayout == AudioSpectrumPanel.LayoutMode.LINEAR);
        linearLayoutMenuItem.addActionListener(e ->{
                mainPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.LINEAR);
                configManager.saveSpectrumLayout(AudioSpectrumPanel.LayoutMode.LINEAR);
                waveformSettingsMenu.setEnabled(false);
        });

        circularLayoutMenuItem = new JRadioButtonMenuItem();
        circularLayoutMenuItem.setSelected(savedLayout == AudioSpectrumPanel.LayoutMode.CIRCULAR);
        circularLayoutMenuItem.addActionListener(e ->{
                mainPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.CIRCULAR);
                configManager.saveSpectrumLayout(AudioSpectrumPanel.LayoutMode.CIRCULAR);
               waveformSettingsMenu.setEnabled(false);
        });

        waveLayoutMenuItem = new JRadioButtonMenuItem();
        waveLayoutMenuItem.setSelected(savedLayout == AudioSpectrumPanel.LayoutMode.WAVEFORM);
        waveLayoutMenuItem.addActionListener(e -> {
            mainPanel.setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode.WAVEFORM);
            configManager.saveSpectrumLayout(AudioSpectrumPanel.LayoutMode.WAVEFORM);

            // Aplica os valores customizados que estão salvos no ConfigManager
            mainPanel.spectrumPanel.setWaveformLayers(configManager.getSavedWaveformLayers());
            mainPanel.spectrumPanel.setWaveformAmplitude(configManager.getSavedWaveformAmplitude());
            mainPanel.spectrumPanel.setWaveformLayerSpacing(configManager.getSavedWaveformSpacing());
            mainPanel.spectrumPanel.setWaveformFilled(configManager.getSavedWaveformFilled());
            waveformSettingsMenu.setEnabled(true);
        });

        grp.add(linearLayoutMenuItem);
        grp.add(circularLayoutMenuItem);
        grp.add(waveLayoutMenuItem);
        spectrumLayoutMenu.add(linearLayoutMenuItem);
        spectrumLayoutMenu.add(circularLayoutMenuItem);
        spectrumLayoutMenu.add(waveLayoutMenuItem);
        return spectrumLayoutMenu;
    }

    private JMenu buildWaveformSettingsSubmenu() {
        waveformSettingsMenu = new JMenu();
        waveformSettingsMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Define o estado inicial se está ativo ou não baseado na configuração salva
        AudioSpectrumPanel.LayoutMode savedLayout = configManager.getSavedSpectrumLayout();
        waveformSettingsMenu.setEnabled(savedLayout == AudioSpectrumPanel.LayoutMode.WAVEFORM);

        // ---- 1. Opção FILLED (Preenchido) ----
        filledItem = new JCheckBoxMenuItem();
        filledItem.setSelected(configManager.getSavedWaveformFilled());
        filledItem.addActionListener(e -> {
            boolean selected = filledItem.isSelected();
            mainPanel.spectrumPanel.setWaveformFilled(selected);
            configManager.saveWaveformFilled(selected);
        });
        waveformSettingsMenu.add(filledItem);
        waveformSettingsMenu.addSeparator();

        // ---- 2. Opção LAYERS (Camadas: 1 a 10) ----
        layersItem = new JMenuItem();
        layersItem.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(null,
                    I18N.get("audio.spectrum.wave.waveLayer.question"),
                    configManager.getSavedWaveformLayers());
            if (input != null) {
                try {
                    int val = Integer.parseInt(input);
                    // Respeita as validações do seu setter
                    val = Math.max(1, Math.min(10, val));
                    mainPanel.spectrumPanel.setWaveformLayers(val);
                    configManager.saveWaveformLayers(val);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(null,  I18N.get("audio.spectrum.wave.waveLayer.MessageDialog.text"),
                            I18N.get("audio.spectrum.waveMessageDialog.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        waveformSettingsMenu.add(layersItem);

        // ---- 3. Opção AMPLITUDE (Mínimo 20) ----
        amplitudeItem = new JMenuItem();
        amplitudeItem.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(null,
                    I18N.get("audio.spectrum.wave.waveAmplitude.question"),
                    configManager.getSavedWaveformAmplitude());
            if (input != null) {
                try {
                    int val = Integer.parseInt(input);
                    val = Math.max(20, val);
                    mainPanel.spectrumPanel.setWaveformAmplitude(val);
                    configManager.saveWaveformAmplitude(val);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(null, I18N.get("audio.spectrum.wave.waveLayer.MessageDialog.text"),
                            I18N.get("audio.spectrum.waveMessageDialog.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        waveformSettingsMenu.add(amplitudeItem);

        // ---- 4. Opção SPACING (Espaçamento: 0.0 a 2.0) ----
        spacingItem = new JMenuItem();
        spacingItem.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(null,
                    I18N.get("audio.spectrum.wave.waveSpacing.question"),
                    configManager.getSavedWaveformSpacing());
            if (input != null) {
                try {
                    float val = Float.parseFloat(input);
                    val = Math.max(0.0f, Math.min(2.0f, val));
                    mainPanel.spectrumPanel.setWaveformLayerSpacing(val);
                    configManager.saveWaveformSpacing(val);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(null, I18N.get("audio.spectrum.wave.waveAmplitude.MessageDialog.text"),
                            I18N.get("audio.spectrum.waveMessageDialog.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        waveformSettingsMenu.add(spacingItem);

        return waveformSettingsMenu;
    }

    private JMenu buildColorModeSubmenu() {
        colorModeMenu = new JMenu();
        ButtonGroup grp = new ButtonGroup();

        AudioSpectrumPanel.ColorMode savedColor = configManager.getSavedSpectrumColor();

        defaultColorItem = new JRadioButtonMenuItem("");
        defaultColorItem.setSelected(savedColor == AudioSpectrumPanel.ColorMode.DEFAULT);
        defaultColorItem.addActionListener(e ->{
                mainPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.DEFAULT);
                configManager.saveSpectrumColor(AudioSpectrumPanel.ColorMode.DEFAULT);
            configManager.saveSpectrumCustomPalette(AudioSpectrumPanel.CustomPalette.NONE);
        });

        coverColorItem = new JRadioButtonMenuItem();
        coverColorItem.setSelected(savedColor == AudioSpectrumPanel.ColorMode.COVER_PALETTE);
        coverColorItem.addActionListener(e -> {
            mainPanel.spectrumPanel.setCoverColorDifferenceMultiplier(2.0f);
            mainPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.COVER_PALETTE);
            configManager.saveSpectrumColor(AudioSpectrumPanel.ColorMode.COVER_PALETTE);
            configManager.saveSpectrumCustomPalette(AudioSpectrumPanel.CustomPalette.NONE);
        });

        customColorItem = new JRadioButtonMenuItem("");
        customColorItem.setSelected(savedColor == AudioSpectrumPanel.ColorMode.CUSTOM);
        customColorItem.addActionListener(e ->{
            showCustomColorDialog();
            configManager.saveSpectrumColor(AudioSpectrumPanel.ColorMode.CUSTOM);
            configManager.saveSpectrumCustomPalette(AudioSpectrumPanel.CustomPalette.NONE);
        });

        grp.add(defaultColorItem);
        grp.add(coverColorItem);
        grp.add(customColorItem);
        colorModeMenu.add(defaultColorItem);
        colorModeMenu.add(coverColorItem);
        colorModeMenu.add(customColorItem);
        colorModeMenu.addSeparator();


        synthwaveItem = createPaletteItem(
                AudioSpectrumPanel.CustomPalette.SYNTHWAVE,
                new Color(100, 0, 200), new Color(200, 0, 255), new Color(255, 100, 255));
        matrixItem = createPaletteItem(
                AudioSpectrumPanel.CustomPalette.MATRIX,
                new Color(0, 255, 0), new Color(0, 255, 128), new Color(128, 255, 255));
        fireItem = createPaletteItem(
                AudioSpectrumPanel.CustomPalette.FIRE,
                new Color(255, 100, 0), new Color(255, 200, 0), new Color(255, 255, 100));
        iceItem = createPaletteItem(
                AudioSpectrumPanel.CustomPalette.ICE,
                new Color(0, 100, 200), new Color(0, 200, 255), new Color(200, 255, 255));



        colorModeMenu.add(synthwaveItem);
        colorModeMenu.add(matrixItem);
        colorModeMenu.add(fireItem);
        colorModeMenu.add(iceItem);
        return colorModeMenu;
    }

    private JMenuItem createPaletteItem(AudioSpectrumPanel.CustomPalette palette, Color bottom, Color middle, Color top) {
        JMenuItem item = new JMenuItem();
        item.addActionListener(e -> {
            mainPanel.setSpectrumCustomColors(bottom, middle, top);
            mainPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
            customColorItem.setSelected(true);

            // SALVA A SUBDIVISÃO ESCOLHIDA:
            configManager.saveSpectrumColor(AudioSpectrumPanel.ColorMode.CUSTOM);
            configManager.saveSpectrumCustomPalette(palette);
        });
        return item;
    }


//    private JMenu buildReflectionSubmenu() {
//        reflectionMenu = new JMenu();
//
//        enableReflectionItem = new JCheckBoxMenuItem("", true);
//        enableReflectionItem.addActionListener(e ->
//                mainPanel.setSpectrumReflection(enableReflectionItem.isSelected()));
//
//        reflectionHeightMenu = new JMenu();
//        String[] hLabels = {"25%", "50%", "75%", "100%"};
//        float[] hValues = {0.25f, 0.5f, 0.75f, 1.0f};
//        ButtonGroup hGrp = new ButtonGroup();
//        for (int i = 0; i < hLabels.length; i++) {
//            final float v = hValues[i];
//            JRadioButtonMenuItem it = new JRadioButtonMenuItem(hLabels[i], i == 1);
//            it.addActionListener(e -> mainPanel.setSpectrumReflectionHeight(v));
//            hGrp.add(it);
//            reflectionHeightMenu.add(it);
//        }
//
//        reflectionOpacityMenu = new JMenu();
//        String[] oLabels = {"25%", "50%", "75%", "100%"};
//        int[] oValues = {64, 128, 192, 255};
//        ButtonGroup oGrp = new ButtonGroup();
//        for (int i = 0; i < oLabels.length; i++) {
//            final int v = oValues[i];
//            JRadioButtonMenuItem it = new JRadioButtonMenuItem(oLabels[i], i == 1);
//            it.addActionListener(e -> mainPanel.setSpectrumReflectionAlpha(v));
//            oGrp.add(it);
//            reflectionOpacityMenu.add(it);
//        }
//
//        reflectionMenu.add(enableReflectionItem);
//        reflectionMenu.add(reflectionHeightMenu);
//        reflectionMenu.add(reflectionOpacityMenu);
//        return reflectionMenu;
//    }
//
//    private JMenu buildAudioSubmenu() {
//        audioMenu = new JMenu();
//        audioMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
//
//        enableNormalizationItem = new JCheckBoxMenuItem("", false);
//        enableNormalizationItem.addActionListener(e ->
//                audioLoudnessManager.setAudioNormalizationEnabled(
//                        enableNormalizationItem.isSelected()));
//        audioMenu.add(enableNormalizationItem);
//        audioMenu.addSeparator();
//        audioMenu.add(buildLoudnessSubmenu());
//        audioMenu.addSeparator();
//        audioMenu.add(buildVolumeGainSubmenu());
//        audioMenu.addSeparator();
//
//        audioInfoItem = new JMenuItem();
//        audioInfoItem.addActionListener(e -> {
//            String info = audioLoudnessManager.getNormalizationInfo();
//            JOptionPane.showMessageDialog(mainPanel,
//                    info + "\n\n" +
//                            I18N.get("audio.normalization") + ": " +
//                            (audioLoudnessManager.isAudioNormalizationEnabled()
//                                    ? I18N.get("audio.enabled") : I18N.get("audio.disabled")) + "\n" +
//                            I18N.get("audio.globalvolume") + ": " +
//                            (int) (audioLoudnessManager.getGlobalAudioGain() * 100) + "%",
//                    I18N.get("audio.info.title"),
//                    JOptionPane.INFORMATION_MESSAGE);
//        });
//        audioMenu.add(audioInfoItem);
//        return audioMenu;
//    }
//
//    private JMenu buildLoudnessSubmenu() {
//        loudnessMenu = new JMenu();
//        ButtonGroup grp = new ButtonGroup();
//
//        streamingItem = loudnessItem(grp, () -> audioLoudnessManager.setTargetLoudness(-14.0f));
//        quietItem = loudnessItem(grp, () -> audioLoudnessManager.setTargetLoudness(-18.0f));
//        broadcastItem = loudnessItem(grp, () -> audioLoudnessManager.setTargetLoudness(-23.0f));
//        cinemaItem = loudnessItem(grp, () -> audioLoudnessManager.setTargetLoudness(-24.0f));
//        loudItem = loudnessItem(grp, () -> audioLoudnessManager.setTargetLoudness(-11.0f));
//        customLoudnessItem = new JRadioButtonMenuItem();
//        customLoudnessItem.addActionListener(e -> showCustomLoudnessDialog());
//        grp.add(customLoudnessItem);
//
//        quietItem.setSelected(true);
//        loudnessMenu.add(streamingItem);
//        loudnessMenu.add(quietItem);
//        loudnessMenu.add(broadcastItem);
//        loudnessMenu.add(cinemaItem);
//        loudnessMenu.add(loudItem);
//        loudnessMenu.addSeparator();
//        loudnessMenu.add(customLoudnessItem);
//        return loudnessMenu;
//    }
//
//    private JRadioButtonMenuItem loudnessItem(ButtonGroup grp, Runnable action) {
//        JRadioButtonMenuItem item = new JRadioButtonMenuItem();
//        item.addActionListener(e -> action.run());
//        grp.add(item);
//        return item;
//    }
//
//    private JMenu buildVolumeGainSubmenu() {
//        volumeGainMenu = new JMenu();
//        ButtonGroup grp = new ButtonGroup();
//        String[] labels = {"20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"};
//        float[] values = {0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};
//
//        for (int i = 0; i < labels.length; i++) {
//            final float v = values[i];
//            JRadioButtonMenuItem it = new JRadioButtonMenuItem(labels[i], i == 0);
//            it.addActionListener(e -> audioLoudnessManager.setGlobalAudioGain(v));
//            grp.add(it);
//            volumeGainMenu.add(it);
//        }
//        return volumeGainMenu;
//    }

    private JMenu buildReflectionSubmenu() {
        reflectionMenu = new JMenu();

        // Carrega o estado salvo da reflexão
        boolean savedReflection = configManager.getSavedSpectrumReflection();
        enableReflectionItem = new JCheckBoxMenuItem("", savedReflection);
        enableReflectionItem.addActionListener(e -> {
            boolean isSelected = enableReflectionItem.isSelected();
            mainPanel.setSpectrumReflection(isSelected);
            configManager.saveSpectrumReflection(isSelected); // Salva
        });

        // Configuração de Altura da Reflexão
        reflectionHeightMenu = new JMenu();
        String[] hLabels = {"25%", "50%", "75%", "100%"};
        float[] hValues = {0.25f, 0.5f, 0.75f, 1.0f};
        float savedHeight = configManager.getSavedSpectrumReflectionHeight(); // Carrega

        ButtonGroup hGrp = new ButtonGroup();
        for (int i = 0; i < hLabels.length; i++) {
            final float v = hValues[i];
            // Verifica se este item coincide com o valor salvo
            boolean isSelected = Math.abs(savedHeight - v) < 0.01f;
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(hLabels[i], isSelected);
            it.addActionListener(e -> {
                mainPanel.setSpectrumReflectionHeight(v);
                configManager.saveSpectrumReflectionHeight(v); // Salva
            });
            hGrp.add(it);
            reflectionHeightMenu.add(it);
        }

        // Configuração de Opacidade da Reflexão
        reflectionOpacityMenu = new JMenu();
        String[] oLabels = {"25%", "50%", "75%", "100%"};
        int[] oValues = {64, 128, 192, 255};
        int savedAlpha = configManager.getSavedSpectrumReflectionAlpha(); // Carrega

        ButtonGroup oGrp = new ButtonGroup();
        for (int i = 0; i < oLabels.length; i++) {
            final int v = oValues[i];
            // Verifica se este item coincide com o valor salvo
            boolean isSelected = (savedAlpha == v);
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(oLabels[i], isSelected);
            it.addActionListener(e -> {
                mainPanel.setSpectrumReflectionAlpha(v);
                configManager.saveSpectrumReflectionAlpha(v); // Salva
            });
            oGrp.add(it);
            reflectionOpacityMenu.add(it);
        }

        reflectionMenu.add(enableReflectionItem);
        reflectionMenu.add(reflectionHeightMenu);
        reflectionMenu.add(reflectionOpacityMenu);
        return reflectionMenu;
    }

    private JMenu buildAudioSubmenu() {
        audioMenu = new JMenu();
        audioMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Carrega o estado salvo da normalização
        boolean savedNormalization = configManager.getSavedAudioNormalization();
        enableNormalizationItem = new JCheckBoxMenuItem("", savedNormalization);
        enableNormalizationItem.addActionListener(e -> {
            boolean isSelected = enableNormalizationItem.isSelected();
            audioLoudnessManager.setAudioNormalizationEnabled(isSelected);
            configManager.saveAudioNormalization(isSelected); // Salva
        });

        audioMenu.add(enableNormalizationItem);
        audioMenu.addSeparator();
        audioMenu.add(buildLoudnessSubmenu());
        audioMenu.addSeparator();
        audioMenu.add(buildVolumeGainSubmenu());
        audioMenu.addSeparator();

        audioInfoItem = new JMenuItem();
        audioInfoItem.addActionListener(e -> {
            String info = audioLoudnessManager.getNormalizationInfo();
            JOptionPane.showMessageDialog(mainPanel,
                    info + "\n\n" +
                            I18N.get("audio.normalization") + ": " +
                            (audioLoudnessManager.isAudioNormalizationEnabled()
                                    ? I18N.get("audio.enabled") : I18N.get("audio.disabled")) + "\n" +
                            I18N.get("audio.globalvolume") + ": " +
                            (int) (audioLoudnessManager.getGlobalAudioGain() * 100) + "%",
                    I18N.get("audio.info.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        audioMenu.add(audioInfoItem);
        return audioMenu;
    }

    private JMenu buildLoudnessSubmenu() {
        loudnessMenu = new JMenu();
        ButtonGroup grp = new ButtonGroup();

        float savedLoudness = configManager.getSavedAudioTargetLoudness(); // Carrega

        // Altera a passagem de ações para também salvar o novo valor de loudness
        streamingItem = loudnessItem(grp, -14.0f, savedLoudness, () -> audioLoudnessManager.setTargetLoudness(-14.0f));
        quietItem = loudnessItem(grp, -18.0f, savedLoudness, () -> audioLoudnessManager.setTargetLoudness(-18.0f));
        broadcastItem = loudnessItem(grp, -23.0f, savedLoudness, () -> audioLoudnessManager.setTargetLoudness(-23.0f));
        cinemaItem = loudnessItem(grp, -24.0f, savedLoudness, () -> audioLoudnessManager.setTargetLoudness(-24.0f));
        loudItem = loudnessItem(grp, -11.0f, savedLoudness, () -> audioLoudnessManager.setTargetLoudness(-11.0f));

        customLoudnessItem = new JRadioButtonMenuItem();
        customLoudnessItem.addActionListener(e -> showCustomLoudnessDialog());
        grp.add(customLoudnessItem);

        // Se o valor salvo não coincidir com nenhum pré-definido, marca o customizado como selecionado
        boolean matchedAny = Math.abs(savedLoudness - (-14.0f)) < 0.01f ||
                Math.abs(savedLoudness - (-18.0f)) < 0.01f ||
                Math.abs(savedLoudness - (-23.0f)) < 0.01f ||
                Math.abs(savedLoudness - (-24.0f)) < 0.01f ||
                Math.abs(savedLoudness - (-11.0f)) < 0.01f;
        if (!matchedAny) {
            customLoudnessItem.setSelected(true);
        }

        loudnessMenu.add(streamingItem);
        loudnessMenu.add(quietItem);
        loudnessMenu.add(broadcastItem);
        loudnessMenu.add(cinemaItem);
        loudnessMenu.add(loudItem);
        loudnessMenu.addSeparator();
        loudnessMenu.add(customLoudnessItem);
        return loudnessMenu;
    }

    // Método auxiliar modificado para marcar a seleção inicial e salvar ao clicar
    private JRadioButtonMenuItem loudnessItem(ButtonGroup grp, float itemValue, float savedValue, Runnable action) {
        boolean isSelected = Math.abs(savedValue - itemValue) < 0.01f;
        JRadioButtonMenuItem item = new JRadioButtonMenuItem();
        item.setSelected(isSelected);
        item.addActionListener(e -> {
            action.run();
            configManager.saveAudioTargetLoudness(itemValue); // Salva
        });
        grp.add(item);
        return item;
    }

    private JMenu buildVolumeGainSubmenu() {
        volumeGainMenu = new JMenu();
        ButtonGroup grp = new ButtonGroup();
        String[] labels = {"20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"};
        float[] values = {0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f};

        float savedGain = configManager.getSavedAudioGlobalGain(); // Carrega

        for (int i = 0; i < labels.length; i++) {
            final float v = values[i];
            boolean isSelected = Math.abs(savedGain - v) < 0.01f;
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(labels[i], isSelected);
            it.addActionListener(e -> {
                audioLoudnessManager.setGlobalAudioGain(v);
                configManager.saveAudioGlobalGain(v); // Salva
            });
            grp.add(it);
            volumeGainMenu.add(it);
        }
        return volumeGainMenu;
    }

    private JMenu buildPlaylistSubmenu() {
        playlistMenu = new JMenu();
        playlistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        openPlaylistItem = new JMenuItem();
        openPlaylistItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        openPlaylistItem.addActionListener(e -> playListExecution.showPlaylistDialog());

        autoPlayItem = new JCheckBoxMenuItem();
        autoPlayItem.setSelected(configManager.isSavedPlaylistAutoPlay());
        autoPlayItem.addActionListener(e ->{
                mainPanel.setAutoPlayNext(autoPlayItem.isSelected());
               configManager.savedPlaylistAutoPlay(autoPlayItem.isSelected());
        });

        playFirtsItemRandom = new JCheckBoxMenuItem();
        playFirtsItemRandom.setSelected(configManager.isSavedPlaylistRandom());
        playFirtsItemRandom.addActionListener(e ->{
            mainPanel.setPlayListFirstItemRandom(playFirtsItemRandom.isSelected());
            configManager.savedPlaylistRandom(playFirtsItemRandom.isSelected());
        });

        playlistMenu.add(openPlaylistItem);
        playlistMenu.addSeparator();
        playlistMenu.add(autoPlayItem);
        playlistMenu.add(playFirtsItemRandom);
        return playlistMenu;
    }

    // ── Diálogos ──────────────────────────────────────────────────────────────

    private void showCustomColorDialog() {
        Color bottom = JColorChooser.showDialog(mainPanel,
                I18N.get("audio.CustomColorDialog.Bottom"), new Color(0, 255, 0));
        if (bottom == null) return;

        Color middle = JColorChooser.showDialog(mainPanel,
                I18N.get("audio.CustomColorDialog.Middle"), new Color(255, 255, 0));
        if (middle == null) return;

        Color top = JColorChooser.showDialog(mainPanel,
                I18N.get("audio.CustomColorDialog.Top"), new Color(255, 0, 0));
        if (top == null) return;

        mainPanel.setSpectrumCustomColors(bottom, middle, top);
        mainPanel.setSpectrumColorMode(AudioSpectrumPanel.ColorMode.CUSTOM);
        JOptionPane.showMessageDialog(mainPanel,
                I18N.get("audio.CustomColorDialog.ConfirmationDialog.text"),
                I18N.get("audio.CustomColorDialog.ConfirmationDialog.title"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showCustomLoudnessDialog() {
        String input = JOptionPane.showInputDialog(mainPanel,
                I18N.get("audio.CustomLoudnessDialog.Text1") + "\n" +
                        I18N.get("audio.CustomLoudnessDialog.Text2") + "\n" +
                        I18N.get("audio.CustomLoudnessDialog.Text3") + "\n" +
                        I18N.get("audio.CustomLoudnessDialog.Text4"),
                "-18.0");
        if (input == null) return;

        try {
            float v = Float.parseFloat(input);
            if (v >= -30.0f && v <= 0.0f) {
                audioLoudnessManager.setTargetLoudness(v);
                JOptionPane.showMessageDialog(mainPanel,
                        I18N.get("audio.CustomLoudnessDialog.ConfirmationDialog.Text") + v + " dBFS",
                        I18N.get("audio.CustomLoudnessDialog.ConfirmationDialog.Title"),
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogError.Text"),
                        I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogError.Title"),
                        JOptionPane.WARNING_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(mainPanel,
                    I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogNumberFormatError.Text"),
                    I18N.get("audio.CustomLoudnessDialog.ConfirmationDialogNumberFormatError.Title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Sincronização de layout com MainPanel ────────────────────────────────

    /**
     * Chamado por MainPanel.updateLayoutMenuSelection().
     */
    public void syncLayoutSelection(AudioSpectrumPanel.LayoutMode mode) {
        SwingUtilities.invokeLater(() -> {
            if (linearLayoutMenuItem == null) return;
            switch (mode) {
                case LINEAR -> linearLayoutMenuItem.setSelected(true);
                case CIRCULAR -> circularLayoutMenuItem.setSelected(true);
                case WAVEFORM -> waveLayoutMenuItem.setSelected(true);
            }
        });
    }

    // ── updateTexts ───────────────────────────────────────────────────────────
    @Override
    protected void updateTexts() {
        if (spectrumMenu != null) spectrumMenu.setText(I18N.get("audio.spectrum"));
        if (spectrumLayoutMenu != null) spectrumLayoutMenu.setText(I18N.get("audio.spectrum.layout"));
        if (linearLayoutMenuItem != null) linearLayoutMenuItem.setText(I18N.get("audio.spectrum.linear"));
        if (circularLayoutMenuItem != null) circularLayoutMenuItem.setText(I18N.get("audio.spectrum.circular"));
        if (waveLayoutMenuItem != null) waveLayoutMenuItem.setText(I18N.get("audio.spectrum.wave"));
        if (waveformSettingsMenu != null) waveformSettingsMenu.setText(I18N.get("audio.spectrum.wave.menuName"));
        if (filledItem != null) filledItem.setText(I18N.get("audio.spectrum.wave.waveForm"));
        if (layersItem != null) layersItem.setText(I18N.get("audio.spectrum.wave.waveLayer"));
        if (amplitudeItem != null) amplitudeItem.setText(I18N.get("audio.spectrum.wave.waveAmplitude"));
        if (spacingItem != null) spacingItem.setText(I18N.get("audio.spectrum.wave.waveSpacing"));

        if (colorModeMenu != null) colorModeMenu.setText(I18N.get("audio.spectrum.colormode"));
        if (defaultColorItem != null) defaultColorItem.setText(I18N.get("audio.spectrum.color.default"));
        if (coverColorItem != null) coverColorItem.setText(I18N.get("audio.spectrum.color.cover"));
        if (customColorItem != null) customColorItem.setText(I18N.get("audio.spectrum.color.custom"));
        if (synthwaveItem != null) synthwaveItem.setText(I18N.get("audio.spectrum.palette.synthwave"));
        if (matrixItem != null) matrixItem.setText(I18N.get("audio.spectrum.palette.matrix"));
        if (fireItem != null) fireItem.setText(I18N.get("audio.spectrum.palette.fire"));
        if (iceItem != null) iceItem.setText(I18N.get("audio.spectrum.palette.ice"));
        if (showSpectrumItem != null) showSpectrumItem.setText(I18N.get("audio.spectrum.show"));
        if (showCoverItem != null) showCoverItem.setText(I18N.get("audio.spectrum.showcover"));
        if (reflectionMenu != null) reflectionMenu.setText(I18N.get("audio.spectrum.reflection"));
        if (enableReflectionItem != null) enableReflectionItem.setText(I18N.get("audio.spectrum.reflection.enable"));
        if (reflectionHeightMenu != null) reflectionHeightMenu.setText(I18N.get("audio.spectrum.reflection.height"));
        if (reflectionOpacityMenu != null) reflectionOpacityMenu.setText(I18N.get("audio.spectrum.reflection.opacity"));
        if (audioMenu != null) audioMenu.setText(I18N.get("audio.menu"));
        if (enableNormalizationItem != null) enableNormalizationItem.setText(I18N.get("audio.normalization.enable"));
        if (loudnessMenu != null) loudnessMenu.setText(I18N.get("audio.loudness"));
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
        if (customLoudnessItem != null) customLoudnessItem.setText(I18N.get("audio.loudness.custom"));
        if (volumeGainMenu != null) volumeGainMenu.setText(I18N.get("audio.globalvolume"));
        if (audioInfoItem != null) audioInfoItem.setText(I18N.get("audio.info"));
        if (playlistMenu != null) playlistMenu.setText(I18N.get("menu.playlist"));
        if (openPlaylistItem != null) openPlaylistItem.setText(I18N.get("menu.playlist.manage"));
        if (autoPlayItem != null) autoPlayItem.setText(I18N.get("menu.playlist.autoplay"));
        if (playFirtsItemRandom != null) playFirtsItemRandom.setText(I18N.get("menu.playlist.playListFirstItemRandom"));
        if (fileInfoItem != null) fileInfoItem.setText(I18N.get("mediainfo.menuitem"));
        if (aboutItem != null) aboutItem.setText(I18N.get("about.menuitem"));

        updateLanguageMenuTexts();
    }
}


