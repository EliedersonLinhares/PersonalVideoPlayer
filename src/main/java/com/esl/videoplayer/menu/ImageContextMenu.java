package com.esl.videoplayer.menu;

import com.esl.videoplayer.Image.ImageFilterManager;
import com.esl.videoplayer.Image.ImageSaveManager;
import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageContextMenu extends AbstractContextMenu {

    // ── Dependências específicas ──────────────────────────────────────────────
    private final ImageFilterManager filterManager;
    private final ImageSaveManager saveManager;
    private final FFmpegFrameGrabber grabber;

    // ── Itens do menu ─────────────────────────────────────────────────────────
    private JMenu filterMenu;
    private JMenuItem brightnessItem;
    private JMenuItem contrastItem;
    private JMenuItem resetFilterItem;
    private JMenuItem saveAsItem;

    // ── Construtor ────────────────────────────────────────────────────────────
    public ImageContextMenu(MainPanel mainPanel, VideoPlayer videoPlayer,
                            ImageFilterManager filterManager,
                            ImageSaveManager saveManager,
                            FFmpegFrameGrabber grabber) {
        super(mainPanel, videoPlayer);
        this.filterManager = filterManager;
        this.saveManager = saveManager;
        this.grabber = grabber;
    }

    // ── buildMenu ─────────────────────────────────────────────────────────────
    @Override
    protected JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();

        menu.add(buildFilterSubmenu());
        menu.addSeparator();
        menu.add(buildSaveAsItem());
        menu.addSeparator();
        menu.add(buildLanguageMenu());

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                addRecentFiles(menu);
                mainPanel.updateLayoutMenuSelection();
            }

            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                removeRecentFiles(menu, I18N.get("language.text"));
            }

            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                removeRecentFiles(menu, I18N.get("language.text"));
            }
        });

        return menu;
    }

    // ── Submenus ──────────────────────────────────────────────────────────────

    private JMenu buildFilterSubmenu() {
        filterMenu = new JMenu();

        brightnessItem = new JMenuItem();
        brightnessItem.addActionListener(e ->
                showFilterDialog(
                        I18N.get("mainPanel.brightnessDialog.title"),
                        filterManager.getBrightness(),
                        newVal -> {
                            BufferedImage result = filterManager.setBrightness(newVal);
                            if (result != null) mainPanel.repaintImagePanel(result);
                        }));

        contrastItem = new JMenuItem();
        contrastItem.addActionListener(e ->
                showFilterDialog(
                        I18N.get("mainPanel.contrastDialog.title"),
                        filterManager.getContrast(),
                        newVal -> {
                            BufferedImage result = filterManager.setContrast(newVal);
                            if (result != null) mainPanel.repaintImagePanel(result);
                        }));

        resetFilterItem = new JMenuItem();
        resetFilterItem.addActionListener(e -> {
            BufferedImage result = filterManager.reset();
            if (result != null) mainPanel.repaintImagePanel(result);
        });

        filterMenu.add(brightnessItem);
        filterMenu.add(contrastItem);
        filterMenu.addSeparator();
        filterMenu.add(resetFilterItem);
        return filterMenu;
    }

    private JMenuItem buildSaveAsItem() {
        saveAsItem = new JMenuItem();
        saveAsItem.addActionListener(e ->
                saveManager.saveImage(mainPanel.getCurrentFilteredImage(), videoPlayer));
        return saveAsItem;
    }

    // ── Diálogo de filtro com preview ─────────────────────────────────────────

    private void showFilterDialog(String filterName, float currentVal,
                                  java.util.function.Consumer<Float> onApply) {

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(mainPanel),
                filterName,
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(false);

        int pw = Math.min(grabber.getImageWidth(), 600);
        int ph = Math.min(grabber.getImageHeight(), 400);

        BufferedImage current = mainPanel.getCurrentFilteredImage();
        JLabel previewLabel = new JLabel();
        previewLabel.setPreferredSize(new Dimension(pw, ph));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setIcon(new ImageIcon(scaleImage(current, pw, ph)));
        dialog.add(previewLabel, BorderLayout.CENTER);

        JSlider slider = new JSlider(0, 200, (int) (currentVal * 100));
        slider.setMajorTickSpacing(50);
        slider.setMinorTickSpacing(10);
        slider.setPaintTicks(true);

        JLabel valueLabel = new JLabel(String.format("%.2f", currentVal), SwingConstants.CENTER);
        valueLabel.setPreferredSize(new Dimension(48, 20));

        JPanel sliderRow = new JPanel(new BorderLayout(6, 0));
        sliderRow.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 12));
        sliderRow.add(new JLabel(filterName + ":"), BorderLayout.WEST);
        sliderRow.add(slider, BorderLayout.CENTER);
        sliderRow.add(valueLabel, BorderLayout.EAST);

        JButton okBtn = new JButton(I18N.get("mainPanel.imageMenu.brightnessDialog.confirm"));
        JButton cancelBtn = new JButton(I18N.get("mainPanel.imageMenu.brightnessDialog.cancel"));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.add(cancelBtn);
        btnPanel.add(okBtn);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        bottom.add(sliderRow, BorderLayout.CENTER);
        bottom.add(btnPanel, BorderLayout.SOUTH);
        dialog.add(bottom, BorderLayout.SOUTH);

        final float[] lastApplied = {currentVal};

        slider.addChangeListener(e -> {
            float v = slider.getValue() / 100.0f;
            valueLabel.setText(String.format("%.2f", v));
            if (Math.abs(v - lastApplied[0]) < 0.01f) return;
            lastApplied[0] = v;

            // Detecta brilho vs contraste pelo nome (mantém o comportamento original)
            BufferedImage preview = filterName.contains("b")
                    ? filterManager.previewBrightness(v, pw, ph)
                    : filterManager.previewContrast(v, pw, ph);

            if (preview != null) previewLabel.setIcon(new ImageIcon(preview));
        });

        okBtn.addActionListener(e -> {
            onApply.accept(slider.getValue() / 100.0f);
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(mainPanel);
        dialog.setVisible(true);
    }

    private BufferedImage scaleImage(BufferedImage src, int maxW, int maxH) {
        if (src == null) return null;
        double scale = Math.min((double) maxW / src.getWidth(),
                (double) maxH / src.getHeight());
        int w = (int) (src.getWidth() * scale);
        int h = (int) (src.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = out.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, w, h, null);
        g2d.dispose();
        return out;
    }

    // ── updateTexts ───────────────────────────────────────────────────────────
    @Override
    protected void updateTexts() {
        if (filterMenu != null) filterMenu.setText(I18N.get("mainPanel.imageFilterMenu.text"));
        if (brightnessItem != null) brightnessItem.setText(I18N.get("mainPanel.imageBrightnessItem.text"));
        if (contrastItem != null) contrastItem.setText(I18N.get("mainPanel.imageContrastItem.text"));
        if (resetFilterItem != null) resetFilterItem.setText(I18N.get("mainPanel.imageFilterResetItem.text"));
        if (saveAsItem != null) saveAsItem.setText(I18N.get("mainPanel.imageSaveAsItem.text"));

        updateLanguageMenuTexts();
    }
}