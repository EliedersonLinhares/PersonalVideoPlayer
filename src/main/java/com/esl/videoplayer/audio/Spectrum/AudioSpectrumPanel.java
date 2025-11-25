package com.esl.videoplayer.audio.Spectrum;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AudioSpectrumPanel extends JPanel {
    private float[] spectrum;
    private float[] smoothedSpectrum;
    private float[] peakLevels;
    // NOVO: Cover art
    private BufferedImage coverArt;
    private float coverOpacity = 0.3f; // Opacidade da capa (0.0 a 1.0)

    // Enum para modos de cor
    public enum ColorMode {
        DEFAULT,        // Verde -> Amarelo -> Vermelho
        CUSTOM,         // Cores personalizadas
        COVER_PALETTE   // Baseado na paleta da capa
    }

    // Variáveis de cor
    private AudioSpectrumPanel.ColorMode colorMode = AudioSpectrumPanel.ColorMode.DEFAULT;
    private Color customBottomColor = new Color(0, 255, 0);    // Verde
    private Color customMiddleColor = new Color(255, 255, 0);  // Amarelo
    private Color customTopColor = new Color(255, 0, 0);       // Vermelho

    // Cores extraídas da capa
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
    private int squareSize = 6;              // altura base dos quadrados
    private int spacingY = 2;                // espaçamento vertical entre quadrados
    private int glowRadius = 3;             // raio do brilho difuso
    private float squareHeightMultiplier = 1.0f;
    private float barWidthFactor = 1.3f;
    private int barCount = 50;               // número de colunas
    private int columnSpacing = 4;           // espaçamento entre colunas

    // Dimensões ajustáveis do painel
    private int panelWidth = 600;
    private int panelHeight = 300;

    // Controle de reflexo
    private boolean showReflection = true;
    private float reflectionHeight = 0.5f; // Altura do reflexo (0.0 a 1.0)
    private int reflectionAlpha = 100; // Transparência do reflexo (0 a 255)

    // Variáveis para controle de visibilidade
    private boolean spectrumVisible = true;
    private boolean coverArtVisible = true;

// Adicionar na classe AudioSpectrumPanel:

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

    public void setCoverArt(BufferedImage cover) {
        this.coverArt = cover;

        // Se modo é COVER_PALETTE, extrair cores da imagem
        if (cover != null && colorMode == AudioSpectrumPanel.ColorMode.COVER_PALETTE) {
            extractColorsFromCover(cover);
        }

        repaint();
    }

    // Extrai paleta de cores dominantes da imagem
    private void extractColorsFromCover(BufferedImage image) {
        if (image == null) return;

        System.out.println("=== Extraindo paleta de cores da capa ===");

        // Redimensionar imagem para análise mais rápida
        int sampleWidth = Math.min(100, image.getWidth());
        int sampleHeight = Math.min(100, image.getHeight());

        // Coletar amostras de cores
        Map<Integer, Integer> colorFrequency = new HashMap<>();

        for (int y = 0; y < sampleHeight; y++) {
            for (int x = 0; x < sampleWidth; x++) {
                int rgb = image.getRGB(
                        x * image.getWidth() / sampleWidth,
                        y * image.getHeight() / sampleHeight
                );

                // Quantizar cor (reduzir precisão para agrupar cores similares)
                int quantized = quantizeColor(rgb);

                colorFrequency.put(quantized, colorFrequency.getOrDefault(quantized, 0) + 1);
            }
        }

        // Ordenar cores por frequência
        java.util.List<Map.Entry<Integer, Integer>> sortedColors = new ArrayList<>(colorFrequency.entrySet());
        sortedColors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Extrair cores dominantes (ignorar cores muito escuras ou muito claras)
        java.util.List<Color> dominantColors = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : sortedColors) {
            Color c = new Color(entry.getKey());

            // Filtrar cores muito escuras (preto) ou muito claras (branco)
            int brightness = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
            if (brightness > 30 && brightness < 225) {
                dominantColors.add(c);
                if (dominantColors.size() >= 5) break;
            }
        }

        if (dominantColors.size() >= 3) {
            // Escolher cores baseadas em saturação e brilho
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
            // Fallback para cores padrão
            System.out.println("Paleta insuficiente, usando cores padrão");
            coverBottomColor = new Color(0, 255, 0);
            coverMiddleColor = new Color(255, 255, 0);
            coverTopColor = new Color(255, 0, 0);
        }
    }

    // Quantizar cor (agrupar cores similares)
    private int quantizeColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Reduzir precisão (ex: 256 níveis -> 32 níveis)
        r = (r / 32) * 32;
        g = (g / 32) * 32;
        b = (b / 32) * 32;

        return (r << 16) | (g << 8) | b;
    }

    // Selecionar 3 cores da paleta baseado em critérios
    private Color[] selectPaletteColors(java.util.List<Color> dominantColors) {
        Color[] result = new Color[3];

        // Ordenar por saturação (cores mais vibrantes primeiro)
        dominantColors.sort((a, b) -> {
            float satA = getSaturation(a);
            float satB = getSaturation(b);
            return Float.compare(satB, satA);
        });

        // Pegar cores com saturações diferentes
        if (dominantColors.size() >= 3) {
            result[0] = dominantColors.get(0); // Mais saturada (bottom)
            result[1] = adjustBrightness(dominantColors.get(1), 1.2f); // Média (middle - mais clara)
            result[2] = adjustBrightness(dominantColors.get(2), 1.4f); // Alta (top - muito clara)
        } else {
            result[0] = dominantColors.get(0);
            result[1] = adjustBrightness(dominantColors.get(0), 1.2f);
            result[2] = adjustBrightness(dominantColors.get(0), 1.5f);
        }

        return result;
    }

    // Calcular saturação de uma cor
    private float getSaturation(Color c) {
        float r = c.getRed() / 255.0f;
        float g = c.getGreen() / 255.0f;
        float b = c.getBlue() / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));

        if (max == 0) return 0;
        return (max - min) / max;
    }

    // Ajustar brilho de uma cor
    private Color adjustBrightness(Color c, float factor) {
        int r = Math.min(255, (int) (c.getRed() * factor));
        int g = Math.min(255, (int) (c.getGreen() * factor));
        int b = Math.min(255, (int) (c.getBlue() * factor));
        return new Color(r, g, b);
    }

    // Converter cor para string
    private String colorToString(Color c) {
        return String.format("RGB(%d, %d, %d)", c.getRed(), c.getGreen(), c.getBlue());
    }

    // NOVO: Método para ajustar opacidade da capa
    public void setCoverOpacity(float opacity) {
        this.coverOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        repaint();
    }

    // Métodos para controlar o reflexo
    public void setShowReflection(boolean show) {
        this.showReflection = show;
    }

    public void setReflectionHeight(float height) {
        this.reflectionHeight = Math.max(0.0f, Math.min(1.0f, height));
    }

    public void setReflectionAlpha(int alpha) {
        this.reflectionAlpha = Math.max(0, Math.min(255, alpha));
    }

    public AudioSpectrumPanel() {
        initArrays(barCount);

        setBackground(Color.BLACK);
        setOpaque(true);
        setPreferredSize(new Dimension(panelWidth, panelHeight));

        // Atualização ~60 FPS
        animationTimer = new Timer(16, e -> {
            for (int i = 0; i < spectrum.length; i++) {
                // suavização + gravidade
                smoothedSpectrum[i] = smoothedSpectrum[i] * (1 - SMOOTHING_FACTOR)
                        + spectrum[i] * SMOOTHING_FACTOR;
                smoothedSpectrum[i] *= GRAVITY;

                // pico (peak hold)
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

    /**
     * Define largura e altura do painel (método dinâmico)
     */
    public void setPanelSize(int width, int height) {
        this.panelWidth = Math.max(100, width);
        this.panelHeight = Math.max(100, height);

        // Definir bounds diretamente (funciona com null layout)
        setBounds(getX(), getY(), panelWidth, panelHeight);

        System.out.println("Spectrum size alterado para: " + panelWidth + "x" + panelHeight);
        repaint();
    }

    /**
     * Retorna a largura atual configurada
     */
    public int getPanelWidth() {
        return panelWidth;
    }

    /**
     * Retorna a altura atual configurada
     */
    public int getPanelHeight() {
        return panelHeight;
    }

    public void updateSpectrum(float[] newSpectrum) {
        if (newSpectrum != null) {
            int len = Math.min(newSpectrum.length, spectrum.length);
            System.arraycopy(newSpectrum, 0, spectrum, 0, len);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // ===== DESENHAR COVER ART NO FUNDO (SE EXISTIR) =====
        if (coverArt != null && coverArtVisible) {
            drawCoverArt(g2d, width, height);
        }
        // ===== DESENHAR SPECTRUM (SE VISÍVEL) =====
        if (spectrumVisible) {
            int barWidth = Math.max(1, (int) (((width - (barCount + 1) * columnSpacing) / (float) barCount) * barWidthFactor));

            // Dividir altura: metade para spectrum original, metade para reflexo
            int titleHeight = 80;
            int baseLineY = showReflection ? (height - titleHeight) / 2 + titleHeight : height - 20;
            int maxBarHeight = baseLineY - titleHeight - 20;


            // ===== Desenhar Barras ORIGINAIS =====
            drawBars(g2d, width, barWidth, maxBarHeight, baseLineY, false);

            // ===== Linha de base (linha d'água) =====
            if (showReflection) {
                // Linha mais destacada quando há reflexo
                g2d.setColor(new Color(100, 150, 255, 120));
                g2d.fillRect(0, baseLineY, width, 3);

                // Efeito de brilho na linha d'água
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

            // ===== Desenhar REFLEXO =====
            if (showReflection) {
                drawReflection(g2d, width, barWidth, maxBarHeight, baseLineY);
            } else if (!coverArtVisible) {
                // ===== Título =====
                g2d.setColor(new Color(255, 255, 255, 100));
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String title = "Audio Spectrum Analyzer";
                FontMetrics fm = g2d.getFontMetrics();
                int titleWidth = fm.stringWidth(title);
                g2d.drawString(title, (width - titleWidth) / 2, 50);
            }
        }
        g2d.dispose();
    }

    // ===== NOVO MÉTODO: Desenhar as barras =====
    private void drawBars(Graphics2D g2d, int width, int barWidth, int maxBarHeight, int baseY, boolean isReflection) {
        for (int i = 0; i < barCount; i++) {
            int x = columnSpacing + i * (barWidth + columnSpacing);
            int barHeight = (int) (smoothedSpectrum[i] * maxBarHeight);
            barHeight = Math.max(squareSize, barHeight);

            int adjustedSquareHeight = (int) (squareSize * squareHeightMultiplier);
            int numSquares = barHeight / (adjustedSquareHeight + spacingY);
            if (numSquares < 1) numSquares = 1;

            int startY = isReflection ?
                    baseY + 3 : // Reflexo começa após a linha d'água
                    baseY - (numSquares * (adjustedSquareHeight + spacingY)); // Original

            float intensity = Math.min(1.0f, smoothedSpectrum[i] * 1.5f);

            // === Cor adaptada à altura ===
            float avgPos = (float) barHeight / maxBarHeight;
            Color glowColor = getColorForPosition(avgPos);

            // Transparência para reflexo
            float alphaMultiplier = isReflection ? (reflectionAlpha / 255.0f) : 1.0f;

            // === Brilho difuso ===
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

            // === Quadradinhos ===
            int reflectionSquares = isReflection ? (int) (numSquares * reflectionHeight) : numSquares;

            for (int j = 0; j < reflectionSquares; j++) {
                int y = startY + j * (adjustedSquareHeight + spacingY);

                // Para reflexo: inverter gradiente e adicionar fade gradual
                float posRatio;
                if (isReflection) {
                    posRatio = (float) j / (float) (reflectionSquares - 1);
                    // Fade adicional: mais transparente quanto mais longe da linha d'água
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

                // borda sutil
                g2d.setColor(new Color(0, 0, 0, (int) (60 * alphaMultiplier)));
                g2d.drawRect(x, y, barWidth, adjustedSquareHeight);
            }

            // === Marcador de pico (apenas no original, não no reflexo) ===
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

    // ===== NOVO MÉTODO: Desenhar reflexo =====
    private void drawReflection(Graphics2D g2d, int width, int barWidth, int maxBarHeight, int baseY) {
        // Criar composição para efeito de água
        Composite originalComposite = g2d.getComposite();

        // Efeito de ondulação sutil (opcional)
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

        // Desenhar barras refletidas
        drawBars(g2d, width, barWidth, (int) (maxBarHeight * reflectionHeight), baseY, true);

        // Gradiente de fade de cima para baixo no reflexo
        int reflectionAreaHeight = (int) (maxBarHeight * reflectionHeight) + 50;
        GradientPaint fadeGradient = new GradientPaint(
                0, baseY + 3, new Color(0, 0, 0, 0),
                0, baseY + reflectionAreaHeight, new Color(0, 0, 0, 150)
        );
        g2d.setPaint(fadeGradient);
        g2d.fillRect(0, baseY + 3, width, reflectionAreaHeight);

        g2d.setComposite(originalComposite);
    }

    //** Gradiente baseado no modo de cor selecionado (0 = base, 1 = topo) */
    private Color getColorForPosition(float posRatio) {
        posRatio = Math.max(0, Math.min(1, posRatio));

        Color bottomColor, middleColor, topColor;

        // Selecionar paleta baseada no modo
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
                // Verde -> Amarelo -> Vermelho (padrão)
                if (posRatio < 0.5f) {
                    float localRatio = posRatio * 2.0f;
                    return new Color((int) (255 * localRatio), 255, 0);
                } else {
                    float localRatio = (posRatio - 0.5f) * 2.0f;
                    return new Color(255, (int) (255 * (1 - localRatio)), 0);
                }
        }

        // Interpolar entre as 3 cores personalizadas
        if (posRatio < 0.5f) {
            // Bottom -> Middle
            float localRatio = posRatio * 2.0f;
            return interpolateColor(bottomColor, middleColor, localRatio);
        } else {
            // Middle -> Top
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
    // ==================== MÉTODOS PÚBLICOS PARA CONTROLE ====================

    public void setColorMode(AudioSpectrumPanel.ColorMode mode) {
        this.colorMode = mode;
        System.out.println("Modo de cor alterado para: " + mode);

        // Se mudar para COVER_PALETTE, extrair cores da capa atual
        if (mode == AudioSpectrumPanel.ColorMode.COVER_PALETTE && coverArt != null) {
            extractColorsFromCover(coverArt);
        }

        repaint();
    }

    public AudioSpectrumPanel.ColorMode getColorMode() {
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

        // Se já está em modo CUSTOM, repintar
        if (colorMode == AudioSpectrumPanel.ColorMode.CUSTOM) {
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

    // NOVO: Método para desenhar cover art no fundo
    private void drawCoverArt(Graphics2D g2d, int panelWidth, int panelHeight) {
        if (coverArt == null) return;

        // Salvar composição original
        Composite originalComposite = g2d.getComposite();

        // Calcular dimensões mantendo aspect ratio e centralizando
        int imgWidth = coverArt.getWidth();
        int imgHeight = coverArt.getHeight();

        double scaleX = (double) panelWidth / imgWidth;
        double scaleY = (double) panelHeight / imgHeight;
        double scale = Math.min(scaleX, scaleY);

        int scaledWidth = (int) (imgWidth * scale);
        int scaledHeight = (int) (imgHeight * scale);
        int x = (panelWidth - scaledWidth) / 2;
        int y = (panelHeight - scaledHeight) / 2;

        // Aplicar opacidade
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, coverOpacity));

        // Desenhar cover art
        g2d.drawImage(coverArt, x, y, scaledWidth, scaledHeight, null);

        // Opcional: Adicionar blur/desfoque nas bordas para efeito mais suave
        // Desenhar gradiente escuro nas bordas para destacar o spectrum
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

        // Restaurar composição
        g2d.setComposite(originalComposite);
    }
}