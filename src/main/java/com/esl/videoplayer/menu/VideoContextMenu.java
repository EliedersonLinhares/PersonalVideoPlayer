package com.esl.videoplayer.menu;

import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.audio.ExtractAudio;
import com.esl.videoplayer.capture.CaptureFrameManager;
import com.esl.videoplayer.filters.FiltersManager;
import com.esl.videoplayer.localization.I18N;
import com.esl.videoplayer.playlist.PlayListExecution;
import com.esl.videoplayer.playlist.PlaylistDialog;
import com.esl.videoplayer.playlist.PlaylistManager;
import com.esl.videoplayer.subtitle.SubtitleManager;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Map;

public class VideoContextMenu extends AbstractContextMenu {

    // ── Dependências específicas ──────────────────────────────────────────────
    private final SubtitleManager subtitleManager;
    private final CaptureFrameManager captureFrameManager;
    private final FiltersManager filtersManager;
    private final FFmpegFrameGrabber grabber;
    private final JButton nextFrameButton;
    private final int totalAudioStreams;
    private final int currentAudioStream;
    private final Map<Integer, String> audioStreamNames;
    private final String videoFilePath;
    private final String ffmpegPath;
    private final ExtractAudio extractAudio = new ExtractAudio();
    // ── Itens do menu ─────────────────────────────────────────────────────────
    private JMenu audioMenu;
    private JMenu subtitleMenu;
    private JMenuItem noSubtitle;
    private JMenuItem loadExternal;
    private JMenu subtitleSettingsMenu;
    private JMenu sizeMenu;
    private JMenu colorMenu;
    private JRadioButtonMenuItem whiteColor;
    private JRadioButtonMenuItem yellowColor;
    private JMenu performanceMenu;
    private JCheckBoxMenuItem hwAccelItem;
    private JMenu frameSkipMenu;
    private JMenu captureMenu;
    private JCheckBoxMenuItem silentCaptureItem;
    private JMenuItem selectFolderItem;
    private JMenuItem resetFolderItem;
    private JMenuItem showCurrentFolder;
    private JMenu batchCaptureMenu;
    private JMenu intervalMenu;
    private JMenuItem selectBatchFolderItem;
    private JMenuItem resetBatchFolderItem;
    private JMenuItem showBatchFolder;
    private JMenu extractAudioMenu;
    private JMenuItem confirmExtractAudioItem;
    private JMenu filtersMenu;
    private JMenuItem brightnessItem;
    private JMenuItem resetFiltersItem;
    private JMenuItem showFiltersItem;
    private JCheckBoxMenuItem fullscreenItem;
    private JMenu playlistMenu;
    private JMenuItem openPlaylistItem;
    private JCheckBoxMenuItem autoPlayItem;
    private JMenuItem noAudio;
    private PlayListExecution playListExecution;

    // ── Construtor ────────────────────────────────────────────────────────────
    public VideoContextMenu(MainPanel mainPanel, VideoPlayer videoPlayer,
                            SubtitleManager subtitleManager,
                            CaptureFrameManager captureFrameManager,
                            FiltersManager filtersManager,
                            FFmpegFrameGrabber grabber,
                            JButton nextFrameButton,
                            int totalAudioStreams, int currentAudioStream,
                            Map<Integer, String> audioStreamNames,
                            String videoFilePath, String ffmpegPath, PlaylistManager playlistManager) {
        super(mainPanel, videoPlayer);
        this.subtitleManager = subtitleManager;
        this.captureFrameManager = captureFrameManager;
        this.filtersManager = filtersManager;
        this.grabber = grabber;
        this.nextFrameButton = nextFrameButton;
        this.totalAudioStreams = totalAudioStreams;
        this.currentAudioStream = currentAudioStream;
        this.audioStreamNames = audioStreamNames;
        this.videoFilePath = videoFilePath;
        this.ffmpegPath = ffmpegPath;
        playListExecution = new PlayListExecution(mainPanel,videoPlayer,playlistManager);
    }

    // ── buildMenu ─────────────────────────────────────────────────────────────
    @Override
    protected JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();

        menu.add(buildAudioSubmenu());
        menu.add(buildSubtitleSubmenu());
        menu.add(buildSubtitleSettingsSubmenu());
        menu.add(buildPerformanceSubmenu());
        menu.add(buildCaptureSubmenu());
        menu.add(buildBatchCaptureSubmenu());
        menu.add(buildExtractAudioSubmenu());
        menu.add(buildFiltersSubmenu());
        menu.add(buildFullscreenItem());
        menu.add(buildLanguageMenu());
        menu.add(buildPlaylistSubmenu());

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                refreshAudioAndSubtitleMenus();
                GraphicsDevice gd = GraphicsEnvironment
                        .getLocalGraphicsEnvironment().getDefaultScreenDevice();
                fullscreenItem.setSelected(gd.getFullScreenWindow() == videoPlayer);
                subtitleMenu.setEnabled(true);
                audioMenu.setEnabled(true);
                captureMenu.setEnabled(true);
                batchCaptureMenu.setEnabled(true);
                performanceMenu.setEnabled(true);
                intervalMenu.setEnabled(true);
                fullscreenItem.setEnabled(true);
                subtitleSettingsMenu.setEnabled(true);
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

    // ── Submenus ──────────────────────────────────────────────────────────────

    private JMenu buildAudioSubmenu() {
        audioMenu = new JMenu();
        return audioMenu;
    }

    private JMenu buildSubtitleSubmenu() {
        subtitleMenu = new JMenu();

        noSubtitle = new JMenuItem();
        noSubtitle.addActionListener(e -> {
            subtitleManager.setCurrentSubtitleStream(-1);
            subtitleManager.setCurrentSubtitleText("");
            mainPanel.repaint();
        });
        subtitleMenu.add(noSubtitle);

        loadExternal = new JMenuItem();
        loadExternal.addActionListener(e ->
                subtitleManager.loadExternalSubtitle(videoFilePath, videoPlayer));
        subtitleMenu.add(loadExternal);

        return subtitleMenu;
    }

    private JMenu buildSubtitleSettingsSubmenu() {
        subtitleSettingsMenu = new JMenu();

        // Tamanho
        sizeMenu = new JMenu();
        int[] sizes = {16, 20, 24, 28, 32, 36, 40, 48, 56, 64};
        ButtonGroup sizeGroup = new ButtonGroup();
        int savedSize = configManager.getSavedSubtitleSize();

        for (int size : sizes) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(size + "px");
            item.setSelected(size == savedSize);
            item.addActionListener(e -> {
                subtitleManager.setBaseSubtitleFontSize(size);
                configManager.saveSubtitleSize(size);
                mainPanel.repaint();
            });
            sizeGroup.add(item);
            sizeMenu.add(item);
        }
        subtitleSettingsMenu.add(sizeMenu);

        // Cor
        colorMenu = new JMenu();
        ButtonGroup colorGroup = new ButtonGroup();
        Color savedColor = configManager.getSavedSubtitleColor();

        whiteColor = new JRadioButtonMenuItem();
        whiteColor.setSelected(savedColor.equals(Color.WHITE));
        whiteColor.addActionListener(e -> {
            subtitleManager.setSubtitleColor(Color.WHITE);
            configManager.saveSubtitleColor(Color.WHITE);
            mainPanel.repaint();
        });
        colorGroup.add(whiteColor);
        colorMenu.add(whiteColor);

        yellowColor = new JRadioButtonMenuItem();
        yellowColor.setSelected(savedColor.equals(Color.YELLOW));
        yellowColor.addActionListener(e -> {
            subtitleManager.setSubtitleColor(Color.YELLOW);
            configManager.saveSubtitleColor(Color.YELLOW);
            mainPanel.repaint();
        });
        colorGroup.add(yellowColor);
        colorMenu.add(yellowColor);

        subtitleSettingsMenu.add(colorMenu);
        return subtitleSettingsMenu;
    }

    private JMenu buildPerformanceSubmenu() {
        performanceMenu = new JMenu();

        hwAccelItem = new JCheckBoxMenuItem();
        hwAccelItem.setSelected(videoPlayer.getVideoExecution().hardwareAccelerationEnabled);
        hwAccelItem.addActionListener(e -> {
            videoPlayer.getVideoExecution().hardwareAccelerationEnabled = hwAccelItem.isSelected();
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
        ButtonGroup skipGroup = new ButtonGroup();
        int savedSkip = configManager.getSavedFrameSkipValue();

        for (int v : new int[]{1, 2, 3, 5, 10, 15, 30}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    v + " frame" + (v > 1 ? "s" : ""));
            item.setSelected(v == savedSkip);
            item.addActionListener(e -> {
                mainPanel.setFramesToSkip(v);
                if (nextFrameButton != null)
                    nextFrameButton.setToolTipText(
                            I18N.get("button.nextframe.tooltip") + " " + v +
                                    " frame" + (v > 1 ? "s" : ""));
                configManager.saveFrameSkipValue(v);
            });
            skipGroup.add(item);
            frameSkipMenu.add(item);
        }
        performanceMenu.add(frameSkipMenu);
        return performanceMenu;
    }

    private JMenu buildCaptureSubmenu() {
        captureMenu = new JMenu();

        silentCaptureItem = new JCheckBoxMenuItem();
        silentCaptureItem.setSelected(configManager.isSilentCapture());
        silentCaptureItem.addActionListener(e -> {
            mainPanel.setSilentCapture(silentCaptureItem.isSelected());
            configManager.saveSilentCapture(silentCaptureItem.isSelected());
        });
        captureMenu.add(silentCaptureItem);
        captureMenu.addSeparator();

        selectFolderItem = new JMenuItem();
        selectFolderItem.addActionListener(e ->
                captureFrameManager.selectCaptureFolder(
                        mainPanel.getCustomCapturePath(), videoFilePath, videoPlayer));
        captureMenu.add(selectFolderItem);

        resetFolderItem = new JMenuItem();
        resetFolderItem.addActionListener(e ->
                captureFrameManager.resetCaptureFolder(videoPlayer));
        captureMenu.add(resetFolderItem);
        captureMenu.addSeparator();

        showCurrentFolder = new JMenuItem();
        showCurrentFolder.addActionListener(e -> {
            String folder = resolveFolder(mainPanel.getCustomCapturePath(),
                    I18N.get("menu.capture.nofolder"));
            JOptionPane.showMessageDialog(videoPlayer,
                    I18N.get("menu.capture.currentfolder") + "\n" + folder,
                    I18N.get("menu.capture.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        captureMenu.add(showCurrentFolder);
        return captureMenu;
    }

    private JMenu buildBatchCaptureSubmenu() {
        batchCaptureMenu = new JMenu();

        intervalMenu = new JMenu();
        ButtonGroup intervalGroup = new ButtonGroup();
        int savedInterval = configManager.getSavedCaptureFrameInterval();

        for (int v : new int[]{2, 3, 5, 10, 15, 30, 60, 120}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                    I18N.get("menu.batch.every") + " " + v + " frame" + (v > 2 ? "s" : ""));
            item.setSelected(v == savedInterval);
            item.addActionListener(e -> {
                mainPanel.setBatchCaptureInterval(v);
                configManager.saveCaptureFrameInterval(v);
            });
            intervalGroup.add(item);
            intervalMenu.add(item);
        }
        batchCaptureMenu.add(intervalMenu);
        batchCaptureMenu.addSeparator();

        selectBatchFolderItem = new JMenuItem();
        selectBatchFolderItem.addActionListener(e ->
                captureFrameManager.selectBatchCaptureFolder(
                        mainPanel.getBatchCapturePath(), videoFilePath, videoPlayer));
        batchCaptureMenu.add(selectBatchFolderItem);

        resetBatchFolderItem = new JMenuItem();
        resetBatchFolderItem.addActionListener(e ->
                captureFrameManager.resetCaptureFolder(videoPlayer));
        batchCaptureMenu.add(resetBatchFolderItem);
        batchCaptureMenu.addSeparator();

        showBatchFolder = new JMenuItem();
        showBatchFolder.addActionListener(e -> {
            String folder = resolveFolder(mainPanel.getBatchCapturePath(),
                    I18N.get("menu.batch.nofolder"));
            JOptionPane.showMessageDialog(videoPlayer,
                    I18N.get("menu.batch.currentfolder") + "\n" + folder,
                    I18N.get("menu.batch.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        batchCaptureMenu.add(showBatchFolder);
        return batchCaptureMenu;
    }

    private JMenu buildExtractAudioSubmenu() {
        extractAudioMenu = new JMenu();
        confirmExtractAudioItem = new JMenuItem();
        confirmExtractAudioItem.addActionListener(e ->
                extractAudio.confirmExtraction(mainPanel, videoPlayer, grabber));
        extractAudioMenu.add(confirmExtractAudioItem);
        return extractAudioMenu;
    }

    private JMenu buildFiltersSubmenu() {
        filtersMenu = new JMenu();
        applyResolutionRestriction();

        brightnessItem = new JMenuItem();
        brightnessItem.addActionListener(e ->
                filtersManager.showBrightnessDialog(videoPlayer));
        filtersMenu.add(brightnessItem);
        filtersMenu.addSeparator();

        resetFiltersItem = new JMenuItem();
        resetFiltersItem.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(videoPlayer,
                    I18N.get("menu.filters.reset.confirm"),
                    I18N.get("dialog.confirm"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.YES_OPTION)
                filtersManager.resetFilters(videoPlayer);
        });
        filtersMenu.add(resetFiltersItem);
        filtersMenu.addSeparator();

        showFiltersItem = new JMenuItem();
        showFiltersItem.addActionListener(e -> showActiveFiltersInfo());
        filtersMenu.add(showFiltersItem);
        return filtersMenu;
    }

    private JCheckBoxMenuItem buildFullscreenItem() {
        fullscreenItem = new JCheckBoxMenuItem();
        fullscreenItem.addActionListener(e -> videoPlayer.toggleFullScreen());
        return fullscreenItem;
    }

    private JMenu buildPlaylistSubmenu() {
        playlistMenu = new JMenu();
        playlistMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        openPlaylistItem = new JMenuItem();
        openPlaylistItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        openPlaylistItem.addActionListener(e -> playListExecution.showPlaylistDialog());

        autoPlayItem = new JCheckBoxMenuItem();
        autoPlayItem.setSelected(true);
        autoPlayItem.addActionListener(e ->
                mainPanel.setAutoPlayNext(autoPlayItem.isSelected()));

        playlistMenu.add(openPlaylistItem);
        playlistMenu.addSeparator();
        playlistMenu.add(autoPlayItem);
        return playlistMenu;
    }

    // ── Lógica auxiliar ───────────────────────────────────────────────────────

    private void applyResolutionRestriction() {
        int w = grabber.getImageWidth();
        int h = grabber.getImageHeight();
        mainPanel.setVideoSize(w, h);
        filtersMenu.setEnabled(w <= 1280 && h <= 720);
    }

    private String resolveFolder(String customPath, String fallback) {
        if (customPath != null && !customPath.isEmpty()) return customPath;
        if (videoFilePath != null) return new File(videoFilePath).getParent();
        return fallback;
    }

    private void showActiveFiltersInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(I18N.get("menu.filters.active.title")).append("\n\n");

        if (!filtersManager.isFiltersEnabled() || filtersManager.buildFilterString() == null) {
            sb.append(I18N.get("menu.filters.active.none"));
        } else {
            if (filtersManager.getBrightness() != 0.0)
                sb.append(String.format("• %s: %.2f\n",
                        I18N.get("filter.brightness"), filtersManager.getBrightness()));
            if (filtersManager.getContrast() != 1.0)
                sb.append(String.format("• %s: %.2f\n",
                        I18N.get("filter.contrast"), filtersManager.getContrast()));
            if (filtersManager.getGamma() != 1.0)
                sb.append(String.format("• %s: %.2f\n",
                        I18N.get("filter.gamma"), filtersManager.getGamma()));
            if (filtersManager.getSaturation() != 1.0)
                sb.append(String.format("• %s: %.2f\n",
                        I18N.get("filter.saturation"), filtersManager.getSaturation()));
            sb.append("\n").append(I18N.get("menu.filters.active.ffmpeg"))
                    .append(":\n").append(filtersManager.buildFilterString());
        }

        JOptionPane.showMessageDialog(videoPlayer, sb.toString(),
                I18N.get("menu.filters.active"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Atualiza áudio e legendas dinamicamente cada vez que o menu é aberto.
     */
    private void refreshAudioAndSubtitleMenus() {
        // Áudio
        audioMenu.removeAll();
        if (totalAudioStreams > 1) {
            for (int i = 0; i < totalAudioStreams; i++) {
                final int idx = i;
                String name = audioStreamNames.getOrDefault(i, "Faixa de Áudio " + (i + 1));
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
                item.setSelected(i == videoPlayer.getAudioStreams().getCurrentAudioStream());
                item.addActionListener(e -> videoPlayer.getAudioStreams().switchAudioStream(idx));
                audioMenu.add(item);
            }
        } else {
            noAudio = new JMenuItem(I18N.get("audioChannelChange.OneOptionOnly"));
            noAudio.setEnabled(false);
            audioMenu.add(noAudio);
        }

        // Legendas embutidas
        int fixed = 2;
        while (subtitleMenu.getMenuComponentCount() > fixed)
            subtitleMenu.remove(fixed);

        if (subtitleManager.getTotalSubtitleStreams() > 0) {
            subtitleMenu.insertSeparator(fixed);
            for (int i = 0; i < subtitleManager.getTotalSubtitleStreams(); i++) {
                final int idx = i;
                String name = subtitleManager.getSubtitleStreamNames()
                        .getOrDefault(i, "Legenda " + (i + 1));
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
                item.setSelected(i == subtitleManager.getCurrentSubtitleStream());
                item.addActionListener(e -> subtitleManager.switchSubtitleStream(
                        idx, videoFilePath, videoPlayer, ffmpegPath));
                subtitleMenu.add(item);
            }
        }
    }

    // ── updateTexts ───────────────────────────────────────────────────────────
    @Override
    protected void updateTexts() {
        if (audioMenu != null) audioMenu.setText(I18N.get("menu.audio"));
        if (subtitleMenu != null) subtitleMenu.setText(I18N.get("menu.subtitle"));
        if (noSubtitle != null) noSubtitle.setText(I18N.get("menu.subtitle.disabled"));
        if (loadExternal != null) loadExternal.setText(I18N.get("menu.subtitle.loadexternal"));
        if (subtitleSettingsMenu != null) subtitleSettingsMenu.setText(I18N.get("menu.subtitle.settings"));
        if (sizeMenu != null) sizeMenu.setText(I18N.get("menu.subtitle.fontsize"));
        if (colorMenu != null) colorMenu.setText(I18N.get("menu.subtitle.color"));
        if (whiteColor != null) whiteColor.setText(I18N.get("color.white"));
        if (yellowColor != null) yellowColor.setText(I18N.get("color.yellow"));
        if (performanceMenu != null) performanceMenu.setText(I18N.get("menu.performance"));
        if (hwAccelItem != null) hwAccelItem.setText(I18N.get("menu.performance.hwaccel"));
        if (frameSkipMenu != null) frameSkipMenu.setText(I18N.get("menu.performance.frameskip"));
        if (captureMenu != null) captureMenu.setText(I18N.get("menu.capture"));
        if (silentCaptureItem != null) silentCaptureItem.setText(I18N.get("menu.capture.silent"));
        if (selectFolderItem != null) selectFolderItem.setText(I18N.get("menu.capture.selectfolder"));
        if (resetFolderItem != null) resetFolderItem.setText(I18N.get("menu.capture.resetfolder"));
        if (showCurrentFolder != null) showCurrentFolder.setText(I18N.get("menu.capture.showcurrent"));
        if (batchCaptureMenu != null) batchCaptureMenu.setText(I18N.get("menu.batch"));
        if (intervalMenu != null) intervalMenu.setText(I18N.get("menu.batch.interval"));
        if (selectBatchFolderItem != null) selectBatchFolderItem.setText(I18N.get("menu.batch.selectfolder"));
        if (resetBatchFolderItem != null) resetBatchFolderItem.setText(I18N.get("menu.batch.resetfolder"));
        if (showBatchFolder != null) showBatchFolder.setText(I18N.get("menu.batch.showcurrent"));
        if (extractAudioMenu != null) extractAudioMenu.setText(I18N.get("mainPanel.extractAudioMenu"));
        if (confirmExtractAudioItem != null)
            confirmExtractAudioItem.setText(I18N.get("mainPanel.confirmExtractAudioItem"));
        if (filtersMenu != null) {
            filtersMenu.setText(I18N.get("menu.filters"));
            filtersMenu.setToolTipText(String.format(
                    I18N.get("video.highResolutionFilterBlock.toolTipText") + " %dx%d)",
                    grabber.getImageWidth(), grabber.getImageHeight()));
        }
        if (brightnessItem != null) brightnessItem.setText(I18N.get("menu.filters.brightness"));
        if (resetFiltersItem != null) resetFiltersItem.setText(I18N.get("menu.filters.reset"));
        if (showFiltersItem != null) showFiltersItem.setText(I18N.get("menu.filters.active"));
        if (fullscreenItem != null) fullscreenItem.setText(I18N.get("menu.fullscreen"));
        if (playlistMenu != null) playlistMenu.setText(I18N.get("menu.playlist"));
        if (openPlaylistItem != null) openPlaylistItem.setText(I18N.get("menu.playlist.manage"));
        if (autoPlayItem != null) autoPlayItem.setText(I18N.get("menu.playlist.autoplay"));

        updateLanguageMenuTexts();
    }
}