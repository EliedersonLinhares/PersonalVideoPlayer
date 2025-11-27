package com.esl.videoplayer.Video;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * Carrega e gerencia a imagem de fundo do VideoPanel
 */
public class BackgroundImageLoader {

    private BufferedImage backgroundImage;
    private boolean imageLoaded = false;

    /**
     * Construtor - tenta carregar a imagem automaticamente
     */
    public BackgroundImageLoader() {
        loadBackgroundImage();
    }

    /**
     * Tenta carregar a imagem de fundo de múltiplas localizações
     */
    private void loadBackgroundImage() {
        // Lista de caminhos possíveis
        String[] possiblePaths = {
                "img/background.png",
                "img/background.jpg",
                "img/logo.png",
                "img/logo.jpg",
                "resources/img/background.png",
                "resources/img/background.jpg"
        };

        // Tentar carregar de arquivo externo primeiro
        for (String path : possiblePaths) {
            try {
                File imageFile = new File(path);
                if (imageFile.exists()) {
                    backgroundImage = ImageIO.read(imageFile);
                    imageLoaded = true;
                    System.out.println("✓ Imagem de fundo carregada: " + path);
                    return;
                }
            } catch (Exception e) {
                // Continuar tentando outros caminhos
            }
        }

        // Tentar carregar do classpath (recursos internos do JAR)
        for (String path : possiblePaths) {
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream(path);
                if (is != null) {
                    backgroundImage = ImageIO.read(is);
                    imageLoaded = true;
                    System.out.println("✓ Imagem de fundo carregada do classpath: " + path);
                    is.close();
                    return;
                }
            } catch (Exception e) {
                // Continuar tentando
            }
        }

        System.out.println("⚠ Nenhuma imagem de fundo encontrada. Usando fallback.");
        imageLoaded = false;
    }

    /**
     * Desenha a imagem de fundo ou fallback
     */
    public void drawBackground(Graphics2D g2d, int panelWidth, int panelHeight) {
        if (imageLoaded && backgroundImage != null) {
            drawImageCentered(g2d, panelWidth, panelHeight);
        } else {
            drawFallbackMessage(g2d, panelWidth, panelHeight);
        }
    }

    /**
     * Desenha a imagem centralizada e redimensionada
     */
    private void drawImageCentered(Graphics2D g2d, int panelWidth, int panelHeight) {
        int imgWidth = backgroundImage.getWidth();
        int imgHeight = backgroundImage.getHeight();

        // Calcular dimensões mantendo aspect ratio
        double panelRatio = (double) panelWidth / panelHeight;
        double imgRatio = (double) imgWidth / imgHeight;

        int drawWidth, drawHeight, x, y;

        // Ajustar para caber no painel mantendo proporção
        if (panelRatio > imgRatio) {
            // Painel mais largo - ajustar pela altura
            drawHeight = (int) (panelHeight * 1.0); // 60% da altura do painel
            drawWidth = (int) (imgWidth * ((double) drawHeight / imgHeight));
        } else {
            // Painel mais alto - ajustar pela largura
            drawWidth = (int) (panelWidth * 1.0); // 60% da largura do painel
            drawHeight = (int) (imgHeight * ((double) drawWidth / imgWidth));
        }

        // Centralizar
        x = (panelWidth - drawWidth) / 2;
        y = (panelHeight - drawHeight) / 2;

        // Aplicar transparência suave
        AlphaComposite alphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f);
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(alphaComposite);

        // Desenhar com suavização
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(backgroundImage, x, y, drawWidth, drawHeight, null);

        // Restaurar composite original
        g2d.setComposite(originalComposite);

        // Desenhar texto por cima da imagem
       // drawOverlayText(g2d, panelWidth, panelHeight);
    }

    /**
     * Desenha texto sobre a imagem
     */
    private void drawOverlayText(Graphics2D g2d, int panelWidth, int panelHeight) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Sombra do texto
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 18));
        String msg = "Clique com botão direito para abrir arquivo";
        FontMetrics fm = g2d.getFontMetrics();
        int x = (panelWidth - fm.stringWidth(msg)) / 2;
        int y = panelHeight - 60;

        // Desenhar sombra
        g2d.drawString(msg, x + 2, y + 2);

        // Desenhar texto principal
        g2d.setColor(Color.WHITE);
        g2d.drawString(msg, x, y);
    }

    /**
     * Desenha mensagem fallback quando não há imagem
     */
    private void drawFallbackMessage(Graphics2D g2d, int panelWidth, int panelHeight) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Título principal
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 24));
        String title = "Video Player";
        FontMetrics fmTitle = g2d.getFontMetrics();
        int xTitle = (panelWidth - fmTitle.stringWidth(title)) / 2;
        int yTitle = panelHeight / 2 - 20;
        g2d.drawString(title, xTitle, yTitle);

        // Subtítulo
        g2d.setColor(new Color(200, 200, 200));
        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        String subtitle = "Clique com botão direito para começar";
        FontMetrics fmSub = g2d.getFontMetrics();
        int xSub = (panelWidth - fmSub.stringWidth(subtitle)) / 2;
        int ySub = panelHeight / 2 + 20;
        g2d.drawString(subtitle, xSub, ySub);
    }

    /**
     * Verifica se a imagem foi carregada com sucesso
     */
    public boolean isImageLoaded() {
        return imageLoaded;
    }

    /**
     * Retorna a imagem carregada
     */
    public BufferedImage getBackgroundImage() {
        return backgroundImage;
    }

    /**
     * Define uma nova imagem de fundo
     */
    public void setBackgroundImage(BufferedImage image) {
        this.backgroundImage = image;
        this.imageLoaded = (image != null);
    }

    /**
     * Recarrega a imagem de fundo
     */
    public void reload() {
        loadBackgroundImage();
    }
}