package com.esl.videoplayer.Image;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

/**
 * Gerencia a aplicação de filtros de imagem usando Java2D (RescaleOp + Graphics2D).
 *
 * Interface 100% compatível com a versão anterior baseada em FFmpegFrameFilter —
 * basta trocar a instância no VideoPanel, sem alterar nenhuma outra chamada.
 *
 * Implementação:
 *   - Brilho  → RescaleOp com scaleFactor (multiplicador por canal RGB)
 *   - Contraste → ajuste de níveis via LookupTable pixel a pixel
 *   - Scale do preview → Graphics2D com INTERPOLATION_BILINEAR
 *
 * Escala dos valores (mesma do contrato anterior):
 *   brightness: 0.0 (escuro total) → 1.0 (neutro) → 2.0 (muito claro)
 *   contrast:   0.0 (cinza plano)  → 1.0 (neutro) → 2.0 (alto contraste)
 */
public class ImageFilterManager {

    // ── Estado interno ─────────────────────────────────────────────────────────

    /** Grabber mantido apenas para compatibilidade de interface (getImageWidth/Height). */
    private FFmpegFrameGrabber grabber;

    /** Frame original — convertido uma vez e guardado como BufferedImage base. */
    private Frame originalFrame;

    /**
     * Cópia da imagem original convertida para TYPE_INT_RGB.
     * É a base sobre a qual os filtros são reaplicados sempre do zero.
     */
    private BufferedImage originalImage;

    private final Java2DFrameConverter converter = new Java2DFrameConverter();

    private float brightness = 1.0f;
    private float contrast   = 1.0f;

    // ── Construtor ─────────────────────────────────────────────────────────────

    /** Construtor sem argumentos — grabber injetado depois via setGrabber(). */
    public ImageFilterManager() {}

    // ── Injeção do grabber e do frame ──────────────────────────────────────────

    /**
     * Define o grabber após ele ter sido criado e iniciado no VideoPlayer.
     * Necessário apenas para compatibilidade de interface (dimensões).
     */
    public void setGrabber(FFmpegFrameGrabber grabber) {
        this.grabber = grabber;
    }

    /**
     * Define o frame original a partir do frame capturado no playImage().
     * Converte e guarda como BufferedImage RGB para uso nos filtros Java2D.
     * Reseta automaticamente os filtros para o estado neutro.
     */
    public void setOriginalFrame(Frame frame) {
        this.originalFrame = frame;
        this.brightness    = 1.0f;
        this.contrast      = 1.0f;

        // Converte o frame para BufferedImage TYPE_INT_RGB uma única vez.
        // Todos os filtros partem desta cópia, nunca da imagem já filtrada.
        BufferedImage converted = converter.convert(frame);
        if (converted != null) {
            this.originalImage = toRGB(converted);
        }
    }

    public Frame getOriginalFrame() { return originalFrame; }
    public float getBrightness()    { return brightness;    }
    public float getContrast()      { return contrast;      }

    // ── Setters com retorno da imagem filtrada ─────────────────────────────────

    /** Altera o brilho e retorna a imagem filtrada em resolução original. */
    public BufferedImage setBrightness(float brightness) {
        this.brightness = clamp(brightness, 0.0f, 2.0f);
        return applyFilters(imageWidth(), imageHeight());
    }

    /** Altera o contraste e retorna a imagem filtrada em resolução original. */
    public BufferedImage setContrast(float contrast) {
        this.contrast = clamp(contrast, 0.0f, 2.0f);
        return applyFilters(imageWidth(), imageHeight());
    }

    /** Reseta brilho e contraste para 1.0 e retorna a imagem original sem filtros. */
    public BufferedImage reset() {
        brightness = 1.0f;
        contrast   = 1.0f;
        if (originalImage == null) return null;
        return copyImage(originalImage);
    }

    /** Aplica os filtros atuais na resolução original. */
    public BufferedImage applyFilters() {
        return applyFilters(imageWidth(), imageHeight());
    }

    /**
     * Aplica brilho + contraste na resolução solicitada usando Java2D.
     *
     * Pipeline:
     *   1. Parte sempre do originalImage (sem degradação progressiva)
     *   2. Aplica brilho via RescaleOp (multiplicação por canal)
     *   3. Aplica contraste via LookupTable (curva S por pixel)
     *   4. Redimensiona com Graphics2D bilinear se necessário
     *
     * @param outWidth  Largura da imagem de saída
     * @param outHeight Altura da imagem de saída
     * @return BufferedImage com filtros aplicados, ou null se sem imagem carregada
     */
    public BufferedImage applyFilters(int outWidth, int outHeight) {
        if (originalImage == null) return null;

        // Passo 1: cópia limpa do original (sempre parte do zero)
        BufferedImage result = copyImage(originalImage);

        // Passo 2: brilho via RescaleOp
        // scaleFactor=1.0 → sem alteração | >1.0 → mais claro | <1.0 → mais escuro
        // offset=0 → sem deslocamento de nível
        if (Float.compare(brightness, 1.0f) != 0) {
            RescaleOp rescale = new RescaleOp(brightness, 0, null);
            result = rescale.filter(result, null);
        }

        // Passo 3: contraste via LookupTable
        // Mapeia cada valor 0-255 para um novo valor que aumenta ou reduz o contraste.
        // contrast=1.0 → tabela identidade | >1.0 → curva S ampliada | <1.0 → achatada
        if (Float.compare(contrast, 1.0f) != 0) {
            result = applyContrast(result, contrast);
        }

        // Passo 4: redimensiona para o preview mantendo proporção, se necessário
        boolean needsScale = (outWidth != originalImage.getWidth() ||
                outHeight != originalImage.getHeight());
        if (needsScale) {
            result = scaleImage(result, outWidth, outHeight);
        }

        return result;
    }

    /** Preview temporário de brilho sem alterar o estado atual. */
    public BufferedImage previewBrightness(float previewBrightness, int outWidth, int outHeight) {
        float saved = this.brightness;
        this.brightness = clamp(previewBrightness, 0.0f, 2.0f);
        BufferedImage result = applyFilters(outWidth, outHeight);
        this.brightness = saved;
        return result;
    }

    /** Preview temporário de contraste sem alterar o estado atual. */
    public BufferedImage previewContrast(float previewContrast, int outWidth, int outHeight) {
        float saved = this.contrast;
        this.contrast = clamp(previewContrast, 0.0f, 2.0f);
        BufferedImage result = applyFilters(outWidth, outHeight);
        this.contrast = saved;
        return result;
    }

    /** Verifica se há imagem carregada e pronta para filtrar. */
    public boolean hasImage() {
        return originalImage != null;
    }

    // ── Implementação dos filtros ──────────────────────────────────────────────

    /**
     * Aplica contraste via LookupTable.
     *
     * Fórmula: output = clamp((input - 128) * contrast + 128, 0, 255)
     *   - contrast=1.0 → identidade (sem alteração)
     *   - contrast>1.0 → amplia a distância do ponto médio (mais contraste)
     *   - contrast<1.0 → comprime em direção ao cinza médio (menos contraste)
     */
    private BufferedImage applyContrast(BufferedImage src, float contrastVal) {
        // Monta a tabela de lookup uma vez (256 entradas)
        byte[] lut = new byte[256];
        for (int i = 0; i < 256; i++) {
            int val = (int)((i - 128) * contrastVal + 128);
            lut[i] = (byte) Math.max(0, Math.min(255, val));
        }

        java.awt.image.ByteLookupTable lookupTable =
                new java.awt.image.ByteLookupTable(0, lut);
        java.awt.image.LookupOp op = new java.awt.image.LookupOp(lookupTable, null);

        // LookupOp não aceita TYPE_INT_RGB diretamente em alguns JDKs;
        // opera em cima de uma cópia com componentes separados.
        BufferedImage dest = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        return op.filter(src, dest);
    }

    // ── Utilitários ───────────────────────────────────────────────────────────

    /**
     * Garante que a imagem seja TYPE_INT_RGB.
     * RescaleOp e LookupOp operam corretamente nesse tipo.
     */
    private BufferedImage toRGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE); // fundo branco para imagens com alpha
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /**
     * Copia a imagem em TYPE_INT_RGB.
     * Garante que cada chamada a applyFilters() parta de um estado limpo.
     */
    private BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    /**
     * Redimensiona mantendo proporção dentro de maxW x maxH,
     * usando interpolação bilinear para qualidade adequada em preview.
     */
    private BufferedImage scaleImage(BufferedImage src, int maxW, int maxH) {
        double scale = Math.min((double) maxW / src.getWidth(),
                (double) maxH / src.getHeight());
        int w = (int)(src.getWidth()  * scale);
        int h = (int)(src.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    /** Largura da imagem original (ou 0 se não carregada). */
    private int imageWidth() {
        return originalImage != null ? originalImage.getWidth() : 0;
    }

    /** Altura da imagem original (ou 0 se não carregada). */
    private int imageHeight() {
        return originalImage != null ? originalImage.getHeight() : 0;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
