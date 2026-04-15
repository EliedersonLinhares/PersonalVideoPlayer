package com.esl.videoplayer.Video;

import com.esl.videoplayer.Image.ImageFilterManager;
import com.esl.videoplayer.Image.ImageSaveManager;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.audio.AudioLoudnessManager;
import com.esl.videoplayer.audio.ExtractAudio;
import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.capture.CaptureFrameManager;
import com.esl.videoplayer.configuration.BackgroundImageLoader;
import com.esl.videoplayer.configuration.ConfigManager;
import com.esl.videoplayer.configuration.RecentFilesManager;
import com.esl.videoplayer.filters.FiltersManager;
import com.esl.videoplayer.localization.I18N;
import com.esl.videoplayer.menu.AudioContextMenu;
import com.esl.videoplayer.menu.ImageContextMenu;
import com.esl.videoplayer.menu.VideoContextMenu;
import com.esl.videoplayer.playlist.PlayListExecution;
import com.esl.videoplayer.playlist.PlaylistManager;
import com.esl.videoplayer.subtitle.SubtitleManager;
import com.esl.videoplayer.theme.ThemeManager;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.Frame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MainPanel extends JPanel implements I18N.LanguageChangeListener {

    private final ImageFilterManager filterManager;
    private final ImageSaveManager saveManager;
    public AudioSpectrumPanel spectrumPanel;
    private BufferedImage currentImage;
    private BufferedImage currentFilteredImage;
    private BufferedImage coverArt;
    private RecentFilesManager recentFilesManager;
    private ThemeManager themeManager;
    private SubtitleManager subtitleManager;
    private AudioLoudnessManager audioLoudnessManager;
    private BackgroundImageLoader backgroundImageLoader;
    private ConfigManager configManager;
    private ExtractAudio extractAudio;

    // Referências às classes de menu (necessárias para cleanup e syncLayoutSelection)
    private AudioContextMenu audioContextMenu;

    private boolean autoPlayNext;
    private int framesToSkip = 1;
    private boolean silentCapture = false;
    private String customCapturePath = null;
    private int batchCaptureInterval = 2;
    private String batchCapturePath = null;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private PlayListExecution playListExecution;

    // autoPlayItem mantido aqui pois é lido externamente via getAutoPlayItem()
    private JCheckBoxMenuItem autoPlayItem;

    // ── Construtor ────────────────────────────────────────────────────────────

    public MainPanel(FFmpegFrameGrabber grabber, SubtitleManager subtitleManager,
                     VideoPlayer videoPlayer, AudioLoudnessManager audioLoudnessManager, PlaylistManager playlistManager) {

        this.subtitleManager = subtitleManager;
        this.audioLoudnessManager = audioLoudnessManager;
        backgroundImageLoader = new BackgroundImageLoader();
        configManager = new ConfigManager();
        extractAudio = new ExtractAudio();
        filterManager = new ImageFilterManager();
        saveManager = new ImageSaveManager(videoPlayer);
        playListExecution = new PlayListExecution(this,videoPlayer,playlistManager);

        subtitleManager.setBaseSubtitleFontSize(configManager.getSavedSubtitleSize());
        subtitleManager.setSubtitleColor(configManager.getSavedSubtitleColor());

        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setLayout(null);

        spectrumPanel = new AudioSpectrumPanel();
        spectrumPanel.setVisible(false);
        add(spectrumPanel, BorderLayout.CENTER);

        setAutoPlayNext(true);
        autoPlayItem = new JCheckBoxMenuItem(I18N.get("mainPanel.autoPlayItem.text"), autoPlayNext);
        autoPlayItem.addActionListener(e -> autoPlayNext = autoPlayItem.isSelected());

        if (grabber == null) {
            setupDefaultContextMenu(videoPlayer);
        }

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (spectrumPanel.isVisible()) centerSpectrumPanel();
            }
        });

        I18N.setLocale(configManager.getSavedLocale());
    }

    // ── Instalação dos menus de contexto ──────────────────────────────────────

    /**
     * Menu padrão exibido antes de qualquer mídia ser carregada
     * (playlist, temas, idioma, arquivos recentes).
     */
    private void setupDefaultContextMenu(VideoPlayer videoPlayer) {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                tryShowDefault(e, videoPlayer);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                tryShowDefault(e, videoPlayer);
            }
        });
    }

    private void tryShowDefault(MouseEvent mouseEvent, VideoPlayer videoPlayer) {
        if (mouseEvent.isPopupTrigger() || SwingUtilities.isRightMouseButton(mouseEvent))
            buildAndShowDefaultPopup(mouseEvent, videoPlayer);
    }

    private void buildAndShowDefaultPopup(MouseEvent e, VideoPlayer videoPlayer) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(buildDefaultPlaylistMenu(videoPlayer));
        menu.add(buildDefaultThemeMenu(videoPlayer));
        addDefaultLanguageMenu(menu);
        addDefaultRecentFiles(menu, videoPlayer);
        updateDefaultPopupTexts(menu);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Chamado pelo VideoPlayer após abrir um arquivo de vídeo.
     */
    public void setupVideoContextMenu(SubtitleManager subtitleManager,
                                      CaptureFrameManager captureFrameManager,
                                      VideoPlayer videoPlayer,
                                      FiltersManager filtersManager,
                                      String videoFilePath,
                                      FFmpegFrameGrabber grabber,
                                      JButton nextFrameButton,
                                      int totalAudioStreams, int currentAudioStream,
                                      Map<Integer, String> audioStreamNames,
                                      String ffmpegPath , PlaylistManager playlistManager) {

        VideoContextMenu menu = new VideoContextMenu(
                this, videoPlayer, subtitleManager, captureFrameManager,
                filtersManager, grabber, nextFrameButton,
                totalAudioStreams, currentAudioStream, audioStreamNames,
                videoFilePath, ffmpegPath,playlistManager);
        menu.setRecentFilesManager(recentFilesManager);
        menu.install();
    }

    /**
     * Chamado pelo VideoPlayer após abrir um arquivo de áudio.
     */
    public void setupAudioContextMenu(VideoPlayer videoPlayer,
                                      FFmpegFrameGrabber grabber, PlaylistManager playlistManager) {
        audioContextMenu = new AudioContextMenu(
                this, videoPlayer, audioLoudnessManager, grabber, playlistManager);
        audioContextMenu.setRecentFilesManager(recentFilesManager);
        audioContextMenu.install();
    }

    /**
     * Chamado pelo VideoPlayer após abrir uma imagem.
     */
    public void setupImageContextMenu(VideoPlayer videoPlayer,
                                      FFmpegFrameGrabber grabber) {
        filterManager.setGrabber(grabber);

        ImageContextMenu menu = new ImageContextMenu(
                this, videoPlayer, filterManager, saveManager, grabber);
        menu.setRecentFilesManager(recentFilesManager);
        menu.install();
    }

    // ── Menu padrão (sem mídia carregada) — helpers internos ─────────────────

    private JMenu buildDefaultPlaylistMenu(VideoPlayer videoPlayer) {
        JMenu menu = new JMenu("\uD83D\uDCCB " + I18N.get("menu.playlist"));

        JMenuItem open = new JMenuItem(I18N.get("main.playlist.new"));
        open.addActionListener(e -> playListExecution.showPlaylistDialog());

        JCheckBoxMenuItem jCheckBoxMenuItem = new JCheckBoxMenuItem(
                I18N.get("menu.playlist.autoplay"), true);
        jCheckBoxMenuItem.addActionListener(e -> autoPlayNext = jCheckBoxMenuItem.isSelected());

        menu.add(open);
        menu.addSeparator();
        menu.add(jCheckBoxMenuItem);
        return menu;
    }

    private JMenu buildDefaultThemeMenu(VideoPlayer videoPlayer) {
        JMenu menu = new JMenu("\uD83C\uDFA8 " + I18N.get("theme.menu"));

        if (themeManager == null) {
            JMenuItem menuItemNoTheme = new JMenuItem(I18N.get("theme.unavailable"));
            menuItemNoTheme.setEnabled(false);
            menu.add(menuItemNoTheme);
            return menu;
        }

        String current = themeManager.getCurrentTheme();
        ButtonGroup buttonGroup = new ButtonGroup();

        for (Map.Entry<String, ThemeManager.ThemeInfo> entry :
                themeManager.getAvailableThemes().entrySet()) {
            JRadioButtonMenuItem item = getJRadioButtonMenuItem(videoPlayer, entry, current);
            buttonGroup.add(item);
            menu.add(item);
        }

        menu.addSeparator();
        JMenuItem menuItemInfo = new JMenuItem(I18N.get("theme.current") + ": " +
                themeManager.getCurrentThemeInfo().getDisplayName());
        menuItemInfo.setEnabled(false);
        menu.add(menuItemInfo);
        return menu;
    }

    private JRadioButtonMenuItem getJRadioButtonMenuItem(VideoPlayer videoPlayer, Map.Entry<String, ThemeManager.ThemeInfo> entry, String current) {
        String key = entry.getKey();
        ThemeManager.ThemeInfo info = entry.getValue();

        JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                info.getDisplayName(), key.equals(current));
        item.addActionListener(ev -> {
            if (!key.equals(themeManager.getCurrentTheme())) {
                themeManager.changeThemeSilent(key, videoPlayer.getFrame());
                revalidate();
                repaint();
            }
        });
        return item;
    }

    private void addDefaultLanguageMenu(JPopupMenu menu) {
        JMenu langMenu = new JMenu(I18N.get("language.text"));
        langMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        Locale locale = I18N.getCurrentLocale();
        ButtonGroup buttonGroup = new ButtonGroup();

        JRadioButtonMenuItem radioMenuButtonEn = new JRadioButtonMenuItem(
                I18N.get("language.item1"), "en".equals(locale.getLanguage()));
        radioMenuButtonEn.addActionListener(e -> changeLanguage(Locale.of("en", "US")));

        JRadioButtonMenuItem radioMenuButtonPt = new JRadioButtonMenuItem(
                I18N.get("language.item2"), "pt".equals(locale.getLanguage()));
        radioMenuButtonPt.addActionListener(e -> changeLanguage(Locale.of("pt", "BR")));

        buttonGroup.add(radioMenuButtonEn);
        buttonGroup.add(radioMenuButtonPt);
        langMenu.add(radioMenuButtonEn);
        langMenu.add(radioMenuButtonPt);
        menu.add(langMenu);
    }

    private void addDefaultRecentFiles(JPopupMenu menu, VideoPlayer videoPlayer) {
        if (recentFilesManager == null) return;
        List<RecentFilesManager.RecentFile> files = recentFilesManager.getRecentFiles();
        if (files.isEmpty()) return;

        menu.addSeparator();
        JMenuItem title = new JMenuItem(I18N.get("recent.title"));
        title.setEnabled(false);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        menu.add(title);

        int idx = 1;
        for (RecentFilesManager.RecentFile rf : files) {
            JMenuItem item = new JMenuItem(rf.getIcon() + " " + idx + ". " + rf.getDisplayName());
            item.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            item.setToolTipText(rf.getFilePath());
            if (idx <= 5)
                item.setAccelerator(KeyStroke.getKeyStroke(
                        KeyEvent.VK_0 + idx, InputEvent.CTRL_DOWN_MASK));
            item.addActionListener(e -> {
                File f = new File(rf.getFilePath());
                if (f.exists()) {
                    videoPlayer.loadFromRecentFile(f);
                } else {
                    JOptionPane.showMessageDialog(this,
                            I18N.get("recent.notfound") + "\n" + rf.getFilePath(),
                            I18N.get("dialog.error"), JOptionPane.ERROR_MESSAGE);
                    recentFilesManager.removeRecentFile(rf.getFilePath());
                }
            });
            menu.add(item);
            idx++;
        }

        menu.addSeparator();
        JMenuItem clear = new JMenuItem("🗑️ " + I18N.get("recent.clear"));
        clear.addActionListener(e -> {
            int confirmDialogOk = JOptionPane.showConfirmDialog(this,
                    I18N.get("recent.clear.confirm"), I18N.get("dialog.confirm"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirmDialogOk == JOptionPane.YES_OPTION) {
                recentFilesManager.clearRecentFiles();
                JOptionPane.showMessageDialog(this,
                        I18N.get("recent.clear.success"),
                        I18N.get("dialog.success"), JOptionPane.INFORMATION_MESSAGE);
            }
        });
        menu.add(clear);
    }

    private void updateDefaultPopupTexts(JPopupMenu menu) {
        // Os textos já são resolvidos via I18N.get() na criação — nada a fazer aqui.
        // Mantido como hook caso seja necessário no futuro.
    }

    private void changeLanguage(Locale locale) {
        I18N.setLocale(locale);
        configManager.saveLocale(locale);
    }

    // ── I18N.LanguageChangeListener ───────────────────────────────────────────

    @Override
    public void onLanguageChanged(Locale newLocale) {
        // As classes de menu cuidam dos próprios textos via onLanguageChanged delas.
        // MainPanel só precisa redesenhar.
        revalidate();
        repaint();
    }

    public void cleanup() {
        I18N.removeLanguageChangeListener(this);
        if (audioContextMenu != null) audioContextMenu.cleanup();
    }

    // ── Spectrum ──────────────────────────────────────────────────────────────

    public AudioSpectrumPanel.LayoutMode getSpectrumLayoutMode() {
        return spectrumPanel != null ? spectrumPanel.getLayoutMode() : null;
    }

    public void setSpectrumLayoutMode(AudioSpectrumPanel.LayoutMode mode) {
        if (spectrumPanel != null) spectrumPanel.setLayoutMode(mode);
    }

    public void updateLayoutMenuSelection() {
        if (spectrumPanel == null || audioContextMenu == null) return;
        audioContextMenu.syncLayoutSelection(spectrumPanel.getLayoutMode());
    }

    public void setSpectrumColorMode(AudioSpectrumPanel.ColorMode mode) {
        if (spectrumPanel != null) spectrumPanel.setColorMode(mode);
    }

    public void setSpectrumCustomColors(Color bottom, Color middle, Color top) {
        if (spectrumPanel != null) spectrumPanel.setCustomColors(bottom, middle, top);
    }

    public void setSpectrumVisible(boolean visible) {
        if (spectrumPanel != null) spectrumPanel.setSpectrumVisible(visible);
    }

    public void setSpectrumReflection(boolean show) {
        if (spectrumPanel != null) spectrumPanel.setShowReflection(show);
    }

    public void setSpectrumReflectionHeight(float height) {
        if (spectrumPanel != null) spectrumPanel.setReflectionHeight(height);
    }

    public void setSpectrumReflectionAlpha(int alpha) {
        if (spectrumPanel != null) spectrumPanel.setReflectionAlpha(alpha);
    }

    public void setSpectrumSize(int width, int height) {
        if (spectrumPanel != null) {
            spectrumPanel.setPanelSize(width, height);
            if (spectrumPanel.isVisible()) centerSpectrumPanel();
        }
    }

    private void centerSpectrumPanel() {
        int x = (getWidth() - spectrumPanel.getPanelWidth()) / 2;
        int y = (getHeight() - spectrumPanel.getPanelHeight()) / 2;
        spectrumPanel.setBounds(x, y, spectrumPanel.getPanelWidth(),
                spectrumPanel.getPanelHeight());
    }

    public void showSpectrum() {
        this.currentImage = null;
        if (spectrumPanel != null) {
            spectrumPanel.setVisible(true);
            centerSpectrumPanel();
        }
        repaint();
    }

    public void updateSpectrum(float[] spectrum) {
        if (spectrumPanel != null) spectrumPanel.updateSpectrum(spectrum);
    }

    // ── Imagem / Vídeo ────────────────────────────────────────────────────────

    public void setGrabber(FFmpegFrameGrabber grabber) {
        filterManager.setGrabber(grabber);
    }

    public void setOriginalImageFrame(Frame frame) {
        filterManager.setOriginalFrame(frame);
        Java2DFrameConverter conv = new Java2DFrameConverter();
        BufferedImage img = conv.convert(frame);
        if (img != null) repaintImagePanel(img);
    }

    public void repaintImagePanel(BufferedImage image) {
        this.currentFilteredImage = image;
        repaint();
    }

    public void clearFilteredImage() {
        this.currentFilteredImage = null;
    }

    public BufferedImage getCurrentImage() {
        return currentImage;
    }

    public BufferedImage getCurrentFilteredImage() {
        return currentFilteredImage;
    }

    public void updateImage(BufferedImage img) {
        this.currentImage = img;
        this.coverArt = null;
        if (spectrumPanel != null) spectrumPanel.setVisible(false);
        repaint();
    }

    public void setCoverArt(BufferedImage cover) {
        this.coverArt = cover;
        if (spectrumPanel != null) spectrumPanel.setCoverArt(cover);
    }

    public void setCoverArtVisible(boolean visible) {
        if (spectrumPanel != null) spectrumPanel.setCoverArtVisible(visible);
    }

    public void setCoverOpacity(float opacity) {
        if (spectrumPanel != null) spectrumPanel.setCoverOpacity(opacity);
    }

    // ── Resolução ─────────────────────────────────────────────────────────────

    public void setVideoSize(int w, int h) {
        this.videoWidth = w;
        this.videoHeight = h;
    }

    // ── paintComponent ────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (spectrumPanel != null && spectrumPanel.isVisible()) return;

        BufferedImage toDraw = currentFilteredImage != null
                ? currentFilteredImage : currentImage;

        if (toDraw == null) {
            backgroundImageLoader.drawBackground((Graphics2D) g, getWidth(), getHeight());
            return;
        }

        int pw = getWidth(), ph = getHeight();
        int iw = toDraw.getWidth(), ih = toDraw.getHeight();

        int dw, dh, x, y;
        if ((double) pw / ph > (double) iw / ih) {
            dh = ph;
            dw = (int) (iw * ((double) ph / ih));
            x = (pw - dw) / 2;
            y = 0;
        } else {
            dw = pw;
            dh = (int) (ih * ((double) pw / iw));
            x = 0;
            y = (ph - dh) / 2;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_SPEED);
        g2d.drawImage(toDraw, x, y, dw, dh, null);

        if (!subtitleManager.getCurrentSubtitleText().isEmpty())
            subtitleManager.drawSubtitles(g2d, pw, ph, getHeight());
    }

    // ── Managers externos ─────────────────────────────────────────────────────

    public void setThemeManager(ThemeManager manager) {
        this.themeManager = manager;
        themeManager.printDebugInfo();
    }

    public void setRecentFilesManager(RecentFilesManager manager) {
        this.recentFilesManager = manager;
    }

    // ── Getters / Setters de estado ───────────────────────────────────────────

    public boolean isAutoPlayNext() {
        return autoPlayNext;
    }

    public void setAutoPlayNext(boolean v) {
        this.autoPlayNext = v;
    }

    public JCheckBoxMenuItem getAutoPlayItem() {
        return autoPlayItem;
    }

    public void setAutoPlayItem(JCheckBoxMenuItem v) {
        this.autoPlayItem = v;
    }

    public int getFramesToSkip() {
        return framesToSkip;
    }

    public void setFramesToSkip(int v) {
        this.framesToSkip = v;
    }

    public boolean isSilentCapture() {
        return configManager.isSilentCapture();
    }

    public void setSilentCapture(boolean v) {
        this.silentCapture = v;
    }

    public String getCustomCapturePath() {
        return customCapturePath;
    }

    public void setCustomCapturePath(String v) {
        this.customCapturePath = v;
    }

    public String getBatchCapturePath() {
        return batchCapturePath;
    }

    public void setBatchCapturePath(String v) {
        this.batchCapturePath = v;
    }

    public int getBatchCaptureInterval() {
        return batchCaptureInterval;
    }

    public void setBatchCaptureInterval(int v) {
        this.batchCaptureInterval = v;
    }
}
