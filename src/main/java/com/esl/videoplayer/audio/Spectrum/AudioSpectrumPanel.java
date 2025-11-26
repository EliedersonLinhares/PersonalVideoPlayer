package com.esl.videoplayer.audio.Spectrum;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class AudioSpectrumPanel extends JPanel {
    private float[] spectrum;
    private float[] smoothedSpectrum;
    private float[] peakLevels;
    private BufferedImage coverArt;
    private float coverOpacity = 0.3f;

    // Enum para layout do spectrum
    public enum LayoutMode {
        LINEAR,     // Barras em linha horizontal
        CIRCULAR,    // Barras em círculo
        WAVEFORM      // Forma de onda contínua
    }

    // Enum para modos de cor
    public enum ColorMode {
        DEFAULT,        // Verde -> Amarelo -> Vermelho
        CUSTOM,         // Cores personalizadas
        COVER_PALETTE   // Baseado na paleta da capa
    }

    // Variáveis de layout
    private LayoutMode layoutMode = LayoutMode.LINEAR;

    // Variáveis de cor
    private ColorMode colorMode = ColorMode.DEFAULT;
    private Color customBottomColor = new Color(0, 255, 0);
    private Color customMiddleColor = new Color(255, 255, 0);
    private Color customTopColor = new Color(255, 0, 0);

    private Color coverBottomColor = new Color(0, 255, 0);
    private Color coverMiddleColor = new Color(255, 255, 0);
    private Color coverTopColor = new Color(255, 0, 0);

    private static final float SMOOTHING_FACTOR = 0.25f;
    private static final float GRAVITY = 0.92f;
    private static final float PEAK_DECAY = 0.02f;

    private Timer animationTimer;

    // Controle de pausa e picos
    private boolean paused = false;
    private boolean lockPeaksOnPause = true;

    // Parâmetros visuais ajustáveis
    private int squareSize = 6;
    private int spacingY = 2;        // espaçamento vertical (LINEAR)
    private int spacingRadial = 2;   // espaçamento radial (CIRCULAR)
    private int glowRadius = 3;
    private float squareHeightMultiplier = 1.0f;
    private float barWidthFactor = 1.3f;
    private int barCount = 50;
    private int columnSpacing = 4;   // espaçamento entre colunas (LINEAR)

    // Dimensões ajustáveis do painel
    private int panelWidth = 600;
    private int panelHeight = 300;

    // Parâmetros específicos do modo CIRCULAR
    private int innerRadius = 80;
    private int maxBarLength = 150;
    private float rotationOffset = -90;

    // Parâmetros para WAVEFORM
    private int waveformAmplitude = 150;
    private boolean waveformFilled = true;
    private int waveformLayers = 5; // NOVO: número de camadas
    private float waveformLayerSpacing = 0.2f; // NOVO: espaçamento entre camadas (0.0 a 1.0)

    // Controle de reflexo
    private boolean showReflection = true;
    private float reflectionHeight = 0.5f;  // Para LINEAR
    private int reflectionAlpha = 100;      // Para LINEAR
    private float reflectionIntensity = 0.3f; // Para CIRCULAR

    // Variáveis para controle de visibilidade
    private boolean spectrumVisible = true;
    private boolean coverArtVisible = true;
    private boolean showCenterCircle = false; // Apenas para CIRCULAR

    // Parâmetros para extração de cores
    private float coverColorDifferenceMultiplier = 1.5f; // Multiplicador para aumentar diferença entre cores

    public AudioSpectrumPanel() {
        initArrays(barCount);

        setBackground(Color.BLACK);
        setOpaque(true);
        setPreferredSize(new Dimension(panelWidth, panelHeight));

        // Atualização ~60 FPS
        animationTimer = new Timer(16, e -> {
            for (int i = 0; i < spectrum.length; i++) {
                smoothedSpectrum[i] = smoothedSpectrum[i] * (1 - SMOOTHING_FACTOR)
                        + spectrum[i] * SMOOTHING_FACTOR;
                smoothedSpectrum[i] *= GRAVITY;

                if (smoothedSpectrum[i] > peakLevels[i]) {
                    peakLevels[i] = smoothedSpectrum[i];
                } else if (!(paused && lockPeaksOnPause)) {
                    peakLevels[i] = Math.max(0f, peakLevels[i] - PEAK_DECAY);
                }
            }
            repaint();
        });
        animationTimer.start();
    }

    // ==================== MÉTODOS DE CONFIGURAÇÃO ====================

    private void initArrays(int count) {
        spectrum = new float[count];
        smoothedSpectrum = new float[count];
        peakLevels = new float[count];
    }

    public void setLayoutMode(LayoutMode mode) {
        this.layoutMode = mode;
        System.out.println("Layout alterado para: " + mode);

        // Ajustar dimensões padrão baseado no layout
        if (mode == LayoutMode.CIRCULAR && panelHeight < panelWidth) {
            setPanelSize(600, 400); // Formato quadrado para circular
        } else if (mode == LayoutMode.LINEAR && panelHeight == panelWidth) {
            setPanelSize(600, 300); // Formato retangular para linear
        }

        repaint();
    }

    public LayoutMode getLayoutMode() {
        return layoutMode;
    }

    // Métodos específicos para modo CIRCULAR
    public void setInnerRadius(int radius) {
        this.innerRadius = Math.max(20, radius);
        repaint();
    }

    public void setMaxBarLength(int length) {
        this.maxBarLength = Math.max(20, length);
        repaint();
    }

    public void setRotationOffset(float degrees) {
        this.rotationOffset = degrees;
        repaint();
    }

    public void setShowCenterCircle(boolean show) {
        this.showCenterCircle = show;
        repaint();
    }

    public void setReflectionIntensity(float intensity) {
        this.reflectionIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        repaint();
    }

    // Métodos para WAVEFORM
    public void setWaveformAmplitude(int amplitude) {
        this.waveformAmplitude = Math.max(20, amplitude);
        repaint();
    }

    public void setWaveformFilled(boolean filled) {
        this.waveformFilled = filled;
        repaint();
    }
    // NOVO
    public void setWaveformLayers(int layers) {
        this.waveformLayers = Math.max(1, Math.min(10, layers));
        repaint();
    }

    // ATUALIZADO: Agora controla o offset de frequência entre camadas
    public void setWaveformLayerSpacing(float spacing) {
        this.waveformLayerSpacing = Math.max(0.0f, Math.min(2.0f, spacing));
        repaint();
    }
    // NOVO: Obtém valor do spectrum com offset de frequência
    private float getSpectrumWithOffset(int index, int offset) {
        int newIndex = index + offset;
        if (newIndex < 0) newIndex = 0;
        if (newIndex >= smoothedSpectrum.length) newIndex = smoothedSpectrum.length - 1;
        return smoothedSpectrum[newIndex];
    }

    // Métodos comuns
    public void setBarCount(int count) {
        this.barCount = Math.max(8, count);
        initArrays(this.barCount);
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void setLockPeaksOnPause(boolean lock) {
        this.lockPeaksOnPause = lock;
    }

    public void setSquareSize(int size) {
        this.squareSize = Math.max(2, size);
    }

    public void setSquareHeightMultiplier(float multiplier) {
        this.squareHeightMultiplier = Math.max(0.5f, multiplier);
    }

    public void setBarWidthFactor(float factor) {
        this.barWidthFactor = Math.max(0.5f, factor);
    }

    public void setGlowRadius(int radius) {
        this.glowRadius = Math.max(0, radius);
    }

    public void setColumnSpacing(int spacing) {
        this.columnSpacing = Math.max(0, spacing);
    }

    public void setSpacingRadial(int spacing) {
        this.spacingRadial = Math.max(0, spacing);
    }

    public void setPanelSize(int width, int height) {
        this.panelWidth = Math.max(100, width);
        this.panelHeight = Math.max(100, height);
        setBounds(getX(), getY(), panelWidth, panelHeight);
        System.out.println("Spectrum size alterado para: " + panelWidth + "x" + panelHeight);
        repaint();
    }

    public int getPanelWidth() {
        return panelWidth;
    }

    public int getPanelHeight() {
        return panelHeight;
    }

    public void setSpectrumVisible(boolean visible) {
        this.spectrumVisible = visible;
        repaint();
    }

    public void setCoverArtVisible(boolean visible) {
        this.coverArtVisible = visible;
        repaint();
    }

    public boolean isSpectrumVisible() {
        return spectrumVisible;
    }

    public boolean isCoverArtVisible() {
        return coverArtVisible;
    }

    public void setShowReflection(boolean show) {
        this.showReflection = show;
        repaint();
    }

    public void setReflectionHeight(float height) {
        this.reflectionHeight = Math.max(0.0f, Math.min(1.0f, height));
    }

    public void setReflectionAlpha(int alpha) {
        this.reflectionAlpha = Math.max(0, Math.min(255, alpha));
    }

    public void updateSpectrum(float[] newSpectrum) {
        if (newSpectrum != null) {
            int len = Math.min(newSpectrum.length, spectrum.length);
            System.arraycopy(newSpectrum, 0, spectrum, 0, len);
        }
    }

    // ==================== MÉTODOS DE COR ====================

    public void setCoverArt(BufferedImage cover) {
        this.coverArt = cover;
        if (cover != null && colorMode == ColorMode.COVER_PALETTE) {
            extractColorsFromCover(cover);
        }
        repaint();
    }

    public void setCoverOpacity(float opacity) {
        this.coverOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        repaint();
    }

    public void setCoverColorDifferenceMultiplier(float multiplier) {
        this.coverColorDifferenceMultiplier = Math.max(1.0f, Math.min(3.0f, multiplier));
        if (coverArt != null && colorMode == ColorMode.COVER_PALETTE) {
            extractColorsFromCover(coverArt);
            repaint();
        }
    }

//    private void extractColorsFromCover(BufferedImage image) {
//        if (image == null) return;
//
//        System.out.println("=== Extraindo paleta de cores da capa ===");
//
//        int sampleWidth = Math.min(100, image.getWidth());
//        int sampleHeight = Math.min(100, image.getHeight());
//
//        Map<Integer, Integer> colorFrequency = new HashMap<>();
//
//        for (int y = 0; y < sampleHeight; y++) {
//            for (int x = 0; x < sampleWidth; x++) {
//                int rgb = image.getRGB(
//                        x * image.getWidth() / sampleWidth,
//                        y * image.getHeight() / sampleHeight
//                );
//                int quantized = quantizeColor(rgb);
//                colorFrequency.put(quantized, colorFrequency.getOrDefault(quantized, 0) + 1);
//            }
//        }
//
//        List<Map.Entry<Integer, Integer>> sortedColors = new ArrayList<>(colorFrequency.entrySet());
//        sortedColors.sort((a, b) -> b.getValue().compareTo(a.getValue()));
//
//        List<Color> dominantColors = new ArrayList<>();
//        for (Map.Entry<Integer, Integer> entry : sortedColors) {
//            Color c = new Color(entry.getKey());
//            int brightness = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
//            if (brightness > 30 && brightness < 225) {
//                dominantColors.add(c);
//                if (dominantColors.size() >= 5) break;
//            }
//        }
//
//        if (dominantColors.size() >= 3) {
//            Color[] paletteColors = selectPaletteColors(dominantColors);
//            coverBottomColor = paletteColors[0];
//            coverMiddleColor = paletteColors[1];
//            coverTopColor = paletteColors[2];
//
//            System.out.println("Paleta extraída:");
//            System.out.println("  Bottom: " + colorToString(coverBottomColor));
//            System.out.println("  Middle: " + colorToString(coverMiddleColor));
//            System.out.println("  Top: " + colorToString(coverTopColor));
//            System.out.println("======================================");
//        } else {
//            System.out.println("Paleta insuficiente, usando cores padrão");
//            coverBottomColor = new Color(0, 255, 0);
//            coverMiddleColor = new Color(255, 255, 0);
//            coverTopColor = new Color(255, 0, 0);
//        }
//    }
private void extractColorsFromCover(BufferedImage image) {
    if (image == null) return;

    System.out.println("=== Extraindo paleta de cores da capa ===");

    int sampleWidth = Math.min(100, image.getWidth());
    int sampleHeight = Math.min(100, image.getHeight());

    Map<Integer, Integer> colorFrequency = new HashMap<>();

    for (int y = 0; y < sampleHeight; y++) {
        for (int x = 0; x < sampleWidth; x++) {
            int rgb = image.getRGB(
                    x * image.getWidth() / sampleWidth,
                    y * image.getHeight() / sampleHeight
            );
            int quantized = quantizeColor(rgb);
            colorFrequency.put(quantized, colorFrequency.getOrDefault(quantized, 0) + 1);
        }
    }

    List<Map.Entry<Integer, Integer>> sortedColors = new ArrayList<>(colorFrequency.entrySet());
    sortedColors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

    // Extrair cores dominantes com MAIOR diversidade
    List<Color> dominantColors = new ArrayList<>();
    for (Map.Entry<Integer, Integer> entry : sortedColors) {
        Color c = new Color(entry.getKey());

        // Filtrar cores muito escuras ou muito claras
        int brightness = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
        if (brightness > 30 && brightness < 225) {
            // NOVO: Verificar se a cor é suficientemente diferente das já selecionadas
            boolean isDifferentEnough = true;
            for (Color existing : dominantColors) {
                if (getColorDistance(c, existing) < 80) { // Threshold de diferença
                    isDifferentEnough = false;
                    break;
                }
            }

            if (isDifferentEnough) {
                dominantColors.add(c);
                if (dominantColors.size() >= 5) break;
            }
        }
    }

    if (dominantColors.size() >= 2) {
        Color[] paletteColors = selectPaletteColors(dominantColors);

        coverBottomColor = paletteColors[0];
        coverMiddleColor = paletteColors[1];
        coverTopColor = paletteColors[2];

        System.out.println("Paleta extraída:");
        System.out.println("  Bottom: " + colorToString(coverBottomColor));
        System.out.println("  Middle: " + colorToString(coverMiddleColor));
        System.out.println("  Top: " + colorToString(coverTopColor));
        System.out.println("======================================");

    } else {
        System.out.println("Paleta insuficiente, usando cores padrão");
        coverBottomColor = new Color(0, 255, 0);
        coverMiddleColor = new Color(255, 255, 0);
        coverTopColor = new Color(255, 0, 0);
    }
}
    // Calcular distância euclidiana entre duas cores no espaço RGB
    private float getColorDistance(Color c1, Color c2) {
        int rDiff = c1.getRed() - c2.getRed();
        int gDiff = c1.getGreen() - c2.getGreen();
        int bDiff = c1.getBlue() - c2.getBlue();

        return (float) Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff);
    }

    private int quantizeColor(int rgb) {
        int r = ((rgb >> 16) & 0xFF) / 32 * 32;
        int g = ((rgb >> 8) & 0xFF) / 32 * 32;
        int b = (rgb & 0xFF) / 32 * 32;
        return (r << 16) | (g << 8) | b;
    }

// Selecionar 3 cores da paleta baseado em critérios
private Color[] selectPaletteColors(List<Color> dominantColors) {
    Color[] result = new Color[3];

    // Ordenar por saturação (cores mais vibrantes primeiro)
    dominantColors.sort((a, b) -> {
        float satA = getSaturation(a);
        float satB = getSaturation(b);
        return Float.compare(satB, satA);
    });

    // Pegar cores com saturações diferentes e aumentar contraste
    if (dominantColors.size() >= 3) {
        // Cor base (mais saturada)
        result[0] = dominantColors.get(0);

        // Cor média - mais clara E mais saturada
        Color middle = dominantColors.get(1);
        middle = adjustBrightness(middle, 1.2f * coverColorDifferenceMultiplier);
        middle = adjustSaturation(middle, 1.3f);
        result[1] = middle;

        // Cor alta - muito mais clara E mais saturada
        Color top = dominantColors.get(2);
        top = adjustBrightness(top, 1.4f * coverColorDifferenceMultiplier);
        top = adjustSaturation(top, 1.5f);
        result[2] = top;
    } else {
        result[0] = dominantColors.get(0);
        result[1] = adjustBrightness(adjustSaturation(dominantColors.get(0), 1.3f), 1.2f * coverColorDifferenceMultiplier);
        result[2] = adjustBrightness(adjustSaturation(dominantColors.get(0), 1.5f), 1.5f * coverColorDifferenceMultiplier);
    }

    return result;
}

    // Ajustar saturação de uma cor
    private Color adjustSaturation(Color c, float factor) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);

        // hsb[0] = Hue (matiz)
        // hsb[1] = Saturation (saturação)
        // hsb[2] = Brightness (brilho)

        // Aumentar saturação
        hsb[1] = Math.min(1.0f, hsb[1] * factor);

        return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }

    private float getSaturation(Color c) {
        float r = c.getRed() / 255.0f;
        float g = c.getGreen() / 255.0f;
        float b = c.getBlue() / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / max;
    }

    private Color adjustBrightness(Color c, float factor) {
        int r = Math.min(255, (int) (c.getRed() * factor));
        int g = Math.min(255, (int) (c.getGreen() * factor));
        int b = Math.min(255, (int) (c.getBlue() * factor));
        return new Color(r, g, b);
    }

    private String colorToString(Color c) {
        return String.format("RGB(%d, %d, %d)", c.getRed(), c.getGreen(), c.getBlue());
    }

    public void setColorMode(ColorMode mode) {
        this.colorMode = mode;
        System.out.println("Modo de cor alterado para: " + mode);
        if (mode == ColorMode.COVER_PALETTE && coverArt != null) {
            extractColorsFromCover(coverArt);
        }
        repaint();
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public void setCustomColors(Color bottom, Color middle, Color top) {
        this.customBottomColor = bottom;
        this.customMiddleColor = middle;
        this.customTopColor = top;
        System.out.println("Cores personalizadas definidas:");
        System.out.println("  Bottom: " + colorToString(bottom));
        System.out.println("  Middle: " + colorToString(middle));
        System.out.println("  Top: " + colorToString(top));
        if (colorMode == ColorMode.CUSTOM) {
            repaint();
        }
    }

    public Color getCustomBottomColor() {
        return customBottomColor;
    }

    public Color getCustomMiddleColor() {
        return customMiddleColor;
    }

    public Color getCustomTopColor() {
        return customTopColor;
    }

    // ==================== RENDERIZAÇÃO ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Desenhar cover art no fundo
        if (coverArt != null && coverArtVisible) {
            drawCoverArt(g2d, width, height);
        }

        // Desenhar spectrum baseado no modo de layout
        if (spectrumVisible) {
            if (layoutMode == LayoutMode.CIRCULAR) {
                drawCircularSpectrum(g2d, width / 2, height / 2);
            } else if (layoutMode == LayoutMode.WAVEFORM) {
                drawWaveformSpectrum(g2d, width, height);
            } else {
                drawLinearSpectrum(g2d, width, height);
            }
        }

        g2d.dispose();
    }

    // ==================== MODO WAVEFORM ====================

    private void drawWaveformSpectrum(Graphics2D g2d, int width, int height) {
        int centerY = height / 2;
        float spacing = (float) width / (barCount - 1);

        // Desenhar múltiplas camadas com offsets de frequência
        for (int layer = 0; layer < waveformLayers; layer++) {
            // Offset de frequência para cada camada (em vez de escala)
            // Camadas internas mostram frequências mais altas, externas mais baixas
            int frequencyOffset = (int)((waveformLayers / 2 - layer) * waveformLayerSpacing * barCount * 0.3f);

            // Cor baseada na posição da camada
            float colorPosition = (float) layer / Math.max(1, waveformLayers - 1);
            Color layerColor = getColorForPosition(colorPosition);

            // Criar caminhos da forma de onda para esta camada
            Path2D.Float topPath = new Path2D.Float();
            Path2D.Float bottomPath = new Path2D.Float();

            // Obter primeiro valor com offset
            float firstAmplitude = getSpectrumWithOffset(0, frequencyOffset) * waveformAmplitude;
            topPath.moveTo(0, centerY - firstAmplitude);
            bottomPath.moveTo(0, centerY + firstAmplitude);

            for (int i = 1; i < barCount; i++) {
                float x = i * spacing;
                // Cada camada lê de uma faixa diferente do spectrum
                float amplitude = getSpectrumWithOffset(i, frequencyOffset) * waveformAmplitude;

                topPath.lineTo(x, centerY - amplitude);
                bottomPath.lineTo(x, centerY + amplitude);
            }

            if (waveformFilled && layer == waveformLayers - 1) {
                // Preencher apenas a camada mais externa
                Path2D.Float filledPath = new Path2D.Float();
                filledPath.moveTo(0, centerY - firstAmplitude);

                // Parte superior
                for (int i = 1; i < barCount; i++) {
                    float x = i * spacing;
                    float amplitude = getSpectrumWithOffset(i, frequencyOffset) * waveformAmplitude;
                    filledPath.lineTo(x, centerY - amplitude);
                }

                // Conectar pela parte inferior
                for (int i = barCount - 1; i >= 0; i--) {
                    float x = i * spacing;
                    float amplitude = getSpectrumWithOffset(i, frequencyOffset) * waveformAmplitude;
                    filledPath.lineTo(x, centerY + amplitude);
                }
                filledPath.closePath();

                // Gradiente vertical com transparência
                GradientPaint gradient = new GradientPaint(
                        0, centerY - waveformAmplitude,
                        new Color(layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), 80),
                        0, centerY + waveformAmplitude,
                        new Color(layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), 40)
                );
                g2d.setPaint(gradient);
                g2d.fill(filledPath);
            }

            // Desenhar linhas da camada
            float lineThickness = 2.8f - (layer * 0.2f); // Linhas mais grossas
            g2d.setStroke(new BasicStroke(Math.max(1.5f, lineThickness),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Transparência progressiva para profundidade
            int alpha = 220 - (layer * 25);
            alpha = Math.max(120, alpha);

            Color lineColor = new Color(layerColor.getRed(), layerColor.getGreen(),
                    layerColor.getBlue(), alpha);

            // Linha superior
            g2d.setColor(lineColor);
            g2d.draw(topPath);

            // Linha inferior
            g2d.draw(bottomPath);
        }

        // Linha central
        g2d.setColor(new Color(255, 255, 255, 80));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawLine(0, centerY, width, centerY);
    }

    // ==================== MODO LINEAR ====================

    private void drawLinearSpectrum(Graphics2D g2d, int width, int height) {
        int barWidth = Math.max(1, (int) (((width - (barCount + 1) * columnSpacing) / (float) barCount) * barWidthFactor));

        int titleHeight = 80;
        int baseLineY = showReflection ? (height - titleHeight) / 2 + titleHeight : height - 20;
        int maxBarHeight = baseLineY - titleHeight - 20;

        // Desenhar Barras ORIGINAIS
        drawLinearBars(g2d, width, barWidth, maxBarHeight, baseLineY, false);

        // Linha de base
        if (showReflection) {
            g2d.setColor(new Color(100, 150, 255, 120));
            g2d.fillRect(0, baseLineY, width, 3);

            GradientPaint waterGlow = new GradientPaint(
                    0, baseLineY - 10, new Color(100, 150, 255, 0),
                    0, baseLineY, new Color(100, 150, 255, 80)
            );
            g2d.setPaint(waterGlow);
            g2d.fillRect(0, baseLineY - 10, width, 10);
        } else {
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.fillRect(0, baseLineY, width, 2);
        }

        // Desenhar REFLEXO
        if (showReflection) {
            drawLinearReflection(g2d, width, barWidth, maxBarHeight, baseLineY);
        } else if (!coverArtVisible) {
            // Título
            g2d.setColor(new Color(255, 255, 255, 100));
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
            String title = "Audio Spectrum Analyzer";
            FontMetrics fm = g2d.getFontMetrics();
            int titleWidth = fm.stringWidth(title);
            g2d.drawString(title, (width - titleWidth) / 2, 50);
        }
    }

    private void drawLinearBars(Graphics2D g2d, int width, int barWidth, int maxBarHeight, int baseY, boolean isReflection) {
        for (int i = 0; i < barCount; i++) {
            int x = columnSpacing + i * (barWidth + columnSpacing);
            int barHeight = (int) (smoothedSpectrum[i] * maxBarHeight);
            barHeight = Math.max(squareSize, barHeight);

            int adjustedSquareHeight = (int) (squareSize * squareHeightMultiplier);
            int numSquares = barHeight / (adjustedSquareHeight + spacingY);
            if (numSquares < 1) numSquares = 1;

            int startY = isReflection ?
                    baseY + 3 :
                    baseY - (numSquares * (adjustedSquareHeight + spacingY));

            float intensity = Math.min(1.0f, smoothedSpectrum[i] * 1.5f);
            float avgPos = (float) barHeight / maxBarHeight;
            Color glowColor = getColorForPosition(avgPos);

            float alphaMultiplier = isReflection ? (reflectionAlpha / 255.0f) : 1.0f;

            // Brilho difuso
            for (int glow = glowRadius; glow > 0; glow--) {
                float alpha = (float) glow / glowRadius;
                int glowAlpha = (int) (40 * alpha * intensity * alphaMultiplier);
                g2d.setColor(new Color(
                        glowColor.getRed(),
                        glowColor.getGreen(),
                        glowColor.getBlue(),
                        Math.max(0, glowAlpha)
                ));

                int yPos = isReflection ? startY - glow / 2 : baseY - barHeight - glow / 2;
                g2d.fillRoundRect(x - glow / 2, yPos, barWidth + glow, barHeight + glow, 6, 6);
            }

            // Quadradinhos
            int reflectionSquares = isReflection ? (int) (numSquares * reflectionHeight) : numSquares;

            for (int j = 0; j < reflectionSquares; j++) {
                int y = startY + j * (adjustedSquareHeight + spacingY);

                float posRatio;
                if (isReflection) {
                    posRatio = (float) j / (float) (reflectionSquares - 1);
                    alphaMultiplier *= (1.0f - (j / (float) reflectionSquares) * 0.6f);
                } else {
                    posRatio = (float) (numSquares - 1 - j) / (float) (numSquares - 1);
                }

                Color baseColor = getColorForPosition(posRatio);

                int r = Math.min(255, (int) (baseColor.getRed() * (0.6f + 0.4f * intensity)));
                int gC = Math.min(255, (int) (baseColor.getGreen() * (0.6f + 0.4f * intensity)));
                int b = Math.min(255, (int) (baseColor.getBlue() * (0.6f + 0.4f * intensity)));
                int alpha = Math.min(255, (int) ((200 + 55 * intensity) * alphaMultiplier));

                g2d.setColor(new Color(r, gC, b, alpha));
                g2d.fillRect(x, y, barWidth, adjustedSquareHeight);

                g2d.setColor(new Color(0, 0, 0, (int) (60 * alphaMultiplier)));
                g2d.drawRect(x, y, barWidth, adjustedSquareHeight);
            }

            // Marcador de pico
            if (!isReflection) {
                int adjustedSquareHeightForPeak = (int) (squareSize * squareHeightMultiplier);
                int peakY = baseY - (int) (peakLevels[i] * maxBarHeight) - adjustedSquareHeightForPeak - spacingY;
                peakY = Math.max(10, Math.min(baseY - adjustedSquareHeightForPeak, peakY));

                g2d.setColor(new Color(255, 80, 80, (int) (180 + 60 * intensity)));
                g2d.fillRect(x, peakY, barWidth, adjustedSquareHeightForPeak);
                g2d.setColor(new Color(0, 0, 0, 80));
                g2d.drawRect(x, peakY, barWidth, adjustedSquareHeightForPeak);
            }
        }
    }

    private void drawLinearReflection(Graphics2D g2d, int width, int barWidth, int maxBarHeight, int baseY) {
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

        drawLinearBars(g2d, width, barWidth, (int) (maxBarHeight * reflectionHeight), baseY, true);

        int reflectionAreaHeight = (int) (maxBarHeight * reflectionHeight) + 50;
        GradientPaint fadeGradient = new GradientPaint(
                0, baseY + 3, new Color(0, 0, 0, 0),
                0, baseY + reflectionAreaHeight, new Color(0, 0, 0, 150)
        );
        g2d.setPaint(fadeGradient);
        g2d.fillRect(0, baseY + 3, width, reflectionAreaHeight);

        g2d.setComposite(originalComposite);
    }

    // ==================== MODO CIRCULAR ====================

    private void drawCircularSpectrum(Graphics2D g2d, int centerX, int centerY) {
        float angleStep = 360.0f / barCount;

        for (int i = 0; i < barCount; i++) {
            float angle = rotationOffset + i * angleStep;
            float angleRad = (float) Math.toRadians(angle);

            int barLength = (int) (smoothedSpectrum[i] * maxBarLength);
            barLength = Math.max(squareSize, barLength);

            int adjustedSquareHeight = (int) (squareSize * squareHeightMultiplier);
            int numSquares = barLength / (adjustedSquareHeight + spacingRadial);
            if (numSquares < 1) numSquares = 1;

            float intensity = Math.min(1.0f, smoothedSpectrum[i] * 1.5f);
            float avgPos = (float) barLength / maxBarLength;
            Color glowColor = getColorForPosition(avgPos);

            // Brilho difuso
            if (glowRadius > 0) {
                for (int glow = glowRadius; glow > 0; glow--) {
                    float alpha = (float) glow / glowRadius;
                    int glowAlpha = (int) (40 * alpha * intensity);
                    g2d.setColor(new Color(
                            glowColor.getRed(),
                            glowColor.getGreen(),
                            glowColor.getBlue(),
                            Math.max(0, glowAlpha)
                    ));

                    float glowStartX = centerX + (float) Math.cos(angleRad) * (innerRadius - glow);
                    float glowStartY = centerY + (float) Math.sin(angleRad) * (innerRadius - glow);

                    drawRadialBar(g2d, glowStartX, glowStartY, angleRad,
                            barLength + glow * 2, adjustedSquareHeight + glow);
                }
            }

            // Desenhar quadrados da barra
            for (int j = 0; j < numSquares; j++) {
                float distance = innerRadius + j * (adjustedSquareHeight + spacingRadial);
                float posRatio = (float) (numSquares - 1 - j) / (float) (numSquares - 1);
                Color baseColor = getColorForPosition(posRatio);

                int r = Math.min(255, (int) (baseColor.getRed() * (0.6f + 0.4f * intensity)));
                int gC = Math.min(255, (int) (baseColor.getGreen() * (0.6f + 0.4f * intensity)));
                int b = Math.min(255, (int) (baseColor.getBlue() * (0.6f + 0.4f * intensity)));
                int alpha = Math.min(255, (int) (200 + 55 * intensity));

                g2d.setColor(new Color(r, gC, b, alpha));

                float squareX = centerX + (float) Math.cos(angleRad) * distance;
                float squareY = centerY + (float) Math.sin(angleRad) * distance;

                drawRadialSquare(g2d, squareX, squareY, angleRad, adjustedSquareHeight);

                g2d.setColor(new Color(0, 0, 0, 60));
                drawRadialSquare(g2d, squareX, squareY, angleRad, adjustedSquareHeight);
            }

            // Marcador de pico
            float peakDistance = innerRadius + (int) (peakLevels[i] * maxBarLength);
            float peakX = centerX + (float) Math.cos(angleRad) * peakDistance;
            float peakY = centerY + (float) Math.sin(angleRad) * peakDistance;

            g2d.setColor(new Color(255, 80, 80, (int) (180 + 60 * intensity)));
            drawRadialSquare(g2d, peakX, peakY, angleRad, adjustedSquareHeight);
        }

        // Desenhar círculo central
        if (showCenterCircle) {
            drawCenterCircle(g2d, centerX, centerY);
        }

        // Efeito de reflexo
        if (showReflection) {
            drawCircularReflectionEffect(g2d, centerX, centerY);
        }
    }

    private void drawRadialSquare(Graphics2D g2d, float x, float y, float angleRad, int size) {
        int barWidth = (int) (size * barWidthFactor);

        Rectangle2D rect = new Rectangle2D.Float(-barWidth / 2f, -size / 2f, barWidth, size);
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.rotate(angleRad + Math.PI / 2);

        Shape rotatedRect = transform.createTransformedShape(rect);
        g2d.fill(rotatedRect);
    }

    private void drawRadialBar(Graphics2D g2d, float x, float y, float angleRad, int length, int width) {
        int barWidth = (int) (width * barWidthFactor);

        Rectangle2D rect = new Rectangle2D.Float(0, -barWidth / 2f, length, barWidth);
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.rotate(angleRad);

        Shape rotatedRect = transform.createTransformedShape(rect);
        g2d.fill(rotatedRect);
    }

    private void drawCenterCircle(Graphics2D g2d, int centerX, int centerY) {
        RadialGradientPaint gradient = new RadialGradientPaint(
                centerX, centerY, innerRadius,
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{
                        new Color(30, 30, 30, 200),
                        new Color(15, 15, 15, 220),
                        new Color(0, 0, 0, 255)
                }
        );
        g2d.setPaint(gradient);
        g2d.fillOval(centerX - innerRadius, centerY - innerRadius,
                innerRadius * 2, innerRadius * 2);

        g2d.setColor(new Color(100, 100, 100, 150));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(centerX - innerRadius, centerY - innerRadius,
                innerRadius * 2, innerRadius * 2);
    }

    private void drawCircularReflectionEffect(Graphics2D g2d, int centerX, int centerY) {
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, reflectionIntensity));

        RadialGradientPaint reflection = new RadialGradientPaint(
                centerX, centerY, innerRadius + maxBarLength,
                new float[]{0.6f, 1.0f},
                new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(255, 255, 255, 50)
                }
        );
        g2d.setPaint(reflection);
        int diameter = 2 * (innerRadius + maxBarLength);
        g2d.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);

        g2d.setComposite(originalComposite);
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private Color getColorForPosition(float posRatio) {
        posRatio = Math.max(0, Math.min(1, posRatio));

        Color bottomColor, middleColor, topColor;

        switch (colorMode) {
            case CUSTOM:
                bottomColor = customBottomColor;
                middleColor = customMiddleColor;
                topColor = customTopColor;
                break;

            case COVER_PALETTE:
                bottomColor = coverBottomColor;
                middleColor = coverMiddleColor;
                topColor = coverTopColor;
                break;

            case DEFAULT:
            default:
                if (posRatio < 0.5f) {
                    float localRatio = posRatio * 2.0f;
                    return new Color((int) (255 * localRatio), 255, 0);
                } else {
                    float localRatio = (posRatio - 0.5f) * 2.0f;
                    return new Color(255, (int) (255 * (1 - localRatio)), 0);
                }
        }

        if (posRatio < 0.5f) {
            float localRatio = posRatio * 2.0f;
            return interpolateColor(bottomColor, middleColor, localRatio);
        } else {
            float localRatio = (posRatio - 0.5f) * 2.0f;
            return interpolateColor(middleColor, topColor, localRatio);
        }
    }

    private Color interpolateColor(Color c1, Color c2, float ratio) {
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * ratio);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio);
        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b))
        );
    }

    private void drawCoverArt(Graphics2D g2d, int panelWidth, int panelHeight) {
        if (coverArt == null) return;

        Composite originalComposite = g2d.getComposite();

        int imgWidth = coverArt.getWidth();
        int imgHeight = coverArt.getHeight();

        double scaleX = (double) panelWidth / imgWidth;
        double scaleY = (double) panelHeight / imgHeight;
        double scale = Math.min(scaleX, scaleY);

        int scaledWidth = (int) (imgWidth * scale);
        int scaledHeight = (int) (imgHeight * scale);
        int x = (panelWidth - scaledWidth) / 2;
        int y = (panelHeight - scaledHeight) / 2;

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, coverOpacity));
        g2d.drawImage(coverArt, x, y, scaledWidth, scaledHeight, null);

        // Gradiente de vinheta (mais sutil no modo circular)
        if (layoutMode == LayoutMode.CIRCULAR) {
            RadialGradientPaint vignette = new RadialGradientPaint(
                    panelWidth / 2f, panelHeight / 2f,
                    Math.min(panelWidth, panelHeight) / 2f,
                    new float[]{0.0f, 0.7f, 1.0f},
                    new Color[]{
                            new Color(0, 0, 0, 0),
                            new Color(0, 0, 0, 100),
                            new Color(0, 0, 0, 180)
                    }
            );
            g2d.setPaint(vignette);
            g2d.fillRect(0, 0, panelWidth, panelHeight);
        } else {
            // Gradiente linear no modo LINEAR
            GradientPaint topGradient = new GradientPaint(
                    0, 0, new Color(0, 0, 0, 180),
                    0, panelHeight / 4, new Color(0, 0, 0, 0)
            );
            g2d.setPaint(topGradient);
            g2d.fillRect(0, 0, panelWidth, panelHeight / 4);

            GradientPaint bottomGradient = new GradientPaint(
                    0, panelHeight * 3 / 4, new Color(0, 0, 0, 0),
                    0, panelHeight, new Color(0, 0, 0, 180)
            );
            g2d.setPaint(bottomGradient);
            g2d.fillRect(0, panelHeight * 3 / 4, panelWidth, panelHeight / 4);
        }

        g2d.setComposite(originalComposite);
    }
}