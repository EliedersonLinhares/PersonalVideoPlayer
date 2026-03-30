package com.esl.videoplayer.Image;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import jnafilechooser.api.JnaFileChooser;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Gerencia o salvamento da imagem editada em disco usando JnaFileChooser.
 *
 * Suporta PNG e JPG. Cuida de: adicionar extensão ausente,
 * confirmar sobrescrita e converter ARGB para RGB antes de salvar em JPG.
 */
public class ImageSaveManager  implements I18N.LanguageChangeListener {

    /** Componente pai usado para posicionar os diálogos do Swing. */
    private final Component parent;

    /** Último diretório acessado; reutilizado nas próximas aberturas do chooser. */
    private File lastDirectory = null;

    // ── Construtor ─────────────────────────────────────────────────────────────

    /**
     * @param parent Componente Swing pai (ex.: o painel do player).
     */
    public ImageSaveManager(Component parent) {
        this.parent = parent;
    }

    // ── API pública ────────────────────────────────────────────────────────────

    /**
     * Exibe o diálogo de "Salvar como" usando JnaFileChooser e grava a imagem.
     *
     * Oferece dois filtros: JPG e PNG. O último diretório visitado é reutilizado
     * na próxima chamada. Confirma sobrescrita se o arquivo já existir.
     *
     * @param image Imagem a ser salva (com os filtros já aplicados).
     * @return true se a imagem foi salva com sucesso, false caso contrário.
     */
    public boolean saveImage(BufferedImage image, VideoPlayer videoPlayer) {

        // ── Configura o JnaFileChooser ────────────────────────────────────────
        JnaFileChooser fc = new JnaFileChooser(lastDirectory);
        fc.setDefaultFileName(videoPlayer.videoFilePath);
        fc.setMultiSelectionEnabled(false);

        // Filtros de formato — a ordem define o padrão (primeiro = padrão)
        fc.addFilter("JPEG (*.jpg)", "jpg", "jpeg");
        fc.addFilter(" PNG (*.png)", "png");


        if (!fc.showSaveDialog((Window) parent)) {
            return false; // usuário cancelou
        }

        File file = fc.getSelectedFile();
        if (file == null) return false;

        // ── Detecta o formato pelo nome escolhido pelo usuário ─────────────────
        String format = resolveFormatFromFile(file);

        // ── Garante extensão correta no nome do arquivo ────────────────────────
        file = ensureExtension(file, format);

        // ── Memoriza o diretório para a próxima abertura ───────────────────────
        lastDirectory = file.getParentFile();

        // ── Confirmação de sobrescrita ─────────────────────────────────────────
        if (file.exists() && !confirmOverwrite(file)) {
            return false;
        }

        // ── Salva ──────────────────────────────────────────────────────────────
        return writeImage(image, format, file);
    }

    /**
     * Salva diretamente em um arquivo sem abrir o chooser.
     * Útil para "Salvar" (sem "como"), quando o caminho já é conhecido.
     *
     * @param image  Imagem a ser salva.
     * @param file   Arquivo de destino.
     * @param format "png" ou "jpg".
     * @return true se salvou com sucesso.
     */
    public boolean saveImageTo(BufferedImage image, File file, String format) {
        if (image == null || file == null) return false;
        return writeImage(image, format, file);
    }

    // ── Lógica interna ─────────────────────────────────────────────────────────

    /**
     * Escreve a imagem no disco no formato solicitado.
     * Converte para RGB antes de salvar em JPG (não suporta canal alpha).
     */
    private boolean writeImage(BufferedImage image, String format, File file) {
        try {
            BufferedImage toSave = prepareForFormat(image, format);
            boolean ok = ImageIO.write(toSave, format, file);

            if (ok) {
                showInfo( I18N.get("ImageSaveManager.WriteImage.text1")  + "\n" + file.getAbsolutePath());
            } else {
                showError( I18N.get("ImageSaveManager.WriteImage.text2") + " " + format);
            }
            return ok;

        } catch (Exception ex) {
            ex.printStackTrace();
            showError( I18N.get("ImageSaveManager.WriteImage.text3") + "\n" + ex.getMessage());
            return false;
        }
    }

    /**
     * Garante que a imagem esteja no tipo correto para o formato.
     * JPG não suporta alpha — converte para TYPE_INT_RGB com fundo branco.
     */
    private BufferedImage prepareForFormat(BufferedImage image, String format) {
        if (!format.equals("jpg") && !format.equals("jpeg")) {
            return image; // PNG suporta alpha, sem conversão necessária
        }
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image; // já é RGB
        }

        // Converte ARGB → RGB com fundo branco
        BufferedImage rgb = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgb.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return rgb;
    }

    /**
     * Detecta o formato pelo nome do arquivo escolhido.
     * Retorna "png" como padrão se a extensão não for reconhecida.
     */
    private String resolveFormatFromFile(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "jpg";
        if (name.endsWith(".png"))                           return "png";
        // Sem extensão: padrão PNG (será adicionada por ensureExtension)
        return "png";
    }

    /**
     * Adiciona a extensão ao arquivo caso o usuário não tenha digitado.
     */
    private File ensureExtension(File file, String format) {
        String name = file.getName().toLowerCase();
        boolean hasExt = name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg");
        if (hasExt) return file;
        return new File(file.getParentFile(), file.getName() + "." + format);
    }

    /**
     * Pergunta ao usuário se deseja substituir um arquivo existente.
     */
    private boolean confirmOverwrite(File file) {
        int choice = JOptionPane.showConfirmDialog(
                parent,
                "O arquivo já existe:\n" + file.getName() + "\n\nDeseja substituir?",
                "Confirmar substituição",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
    }

    // ── Helpers de diálogo ─────────────────────────────────────────────────────
    private void showInfo(String message) {
        JOptionPane.showMessageDialog(parent, message, I18N.get("ImageSaveManager.WriteImage.title1"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(parent, message, I18N.get("ImageSaveManager.WriteImage.title2"),
                JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {

    }
}
