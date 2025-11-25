package com.esl.videoplayer.filters;

import com.esl.videoplayer.VideoPlayer;
import org.bytedeco.javacv.Frame;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class FiltersManager {

    private boolean filtersEnabled = false;
    private double brightness = 0.0;  // -1.0 a 1.0
    private double contrast = 1.0;    // 0.0 a 2.0
    private double gamma = 1.0;       // 0.1 a 10.0
    private double saturation = 1.0;  // 0.0 a 3.0

    public FiltersManager(){
    }
    public String buildFilterString() {
        if (!filtersEnabled) {
            return null;
        }
        StringBuilder filterString = new StringBuilder();

        // Ajustar brilho: eq=brightness=valor (-1.0 a 1.0)
        if (brightness != 0.0) {
            if (!filterString.isEmpty()) filterString.append(",");
            filterString.append(String.format("eq=brightness=%.2f", brightness));
        }

        // Ajustar contraste: eq=contrast=valor (0.0 a 2.0, padrão 1.0)
        if (contrast != 1.0) {
            if (!filterString.isEmpty()) filterString.append(",");
            filterString.append(String.format("eq=contrast=%.2f", contrast));
        }

        // Ajustar gamma: eq=gamma=valor (0.1 a 10.0, padrão 1.0)
        if (gamma != 1.0) {
            if (!filterString.isEmpty()) filterString.append(",");
            filterString.append(String.format("eq=gamma=%.2f", gamma));
        }

        // Ajustar saturação: eq=saturation=valor (0.0 a 3.0, padrão 1.0)
        if (saturation != 1.0) {
            if (!filterString.isEmpty()) filterString.append(",");
            filterString.append(String.format("eq=saturation=%.2f", saturation));
        }

        return !filterString.isEmpty() ? filterString.toString() : null;
    }

    public void applyFilters(VideoPlayer videoPlayer) {
        if (videoPlayer.grabber == null || videoPlayer.videoFilePath == null) {
            JOptionPane.showMessageDialog(videoPlayer,
                    "Nenhum vídeo carregado.",
                    "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Apenas notificar que os filtros serão aplicados em tempo real
        String message;
        if (filtersEnabled && buildFilterString() != null) {
            message = "Filtros ativados!\n\nOs filtros serão aplicados em tempo real durante a reprodução.";
        } else {
            message = "Filtros desativados!\n\nO vídeo será exibido sem filtros.";
        }

        JOptionPane.showMessageDialog(videoPlayer,
                message,
                "Filtros",
                JOptionPane.INFORMATION_MESSAGE);

        // Forçar atualização do frame atual
        if (!videoPlayer.isPlaying) {
            try {
                Frame frame = videoPlayer.grabber.grab();
                if (frame != null && frame.image != null) {
                    BufferedImage img = videoPlayer.converter.convert(frame);
                    if (img != null) {
                        img = applyImageFilters(img);
                        videoPlayer.videoPanel.updateImage(img);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        videoPlayer.playVideoOrAudio();
    }

    public void resetFilters(VideoPlayer videoPlayer) {
        brightness = 0.0;
        contrast = 1.0;
        gamma = 1.0;
        saturation = 1.0;
        filtersEnabled = false;

        // Reaplicar (sem filtros)
        applyFilters(videoPlayer);
    }


    public BufferedImage applyImageFilters(BufferedImage original) {
        if (!filtersEnabled || original == null) {
            return original;
        }

        int width = original.getWidth();
        int height = original.getHeight();

        // Criar imagem de saída
        BufferedImage filtered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Obter arrays de pixels para processamento em lote
        int[] srcPixels = original.getRGB(0, 0, width, height, null, 0, width);
        int[] dstPixels = new int[srcPixels.length];

        // Pre-calcular tabelas de lookup para otimização
        int[] brightnessContrastLUT = null;
        int[] gammaLUT = null;

        // Criar LUT combinada para brilho + contraste + gamma
        if (brightness != 0.0 || contrast != 1.0 || gamma != 1.0) {
            brightnessContrastLUT = new int[256];

            for (int i = 0; i < 256; i++) {
                double value = i;

                // Aplicar contraste: g(x) = α * (f(x) - 128) + 128
                if (contrast != 1.0) {
                    value = (value - 128.0) * contrast + 128.0;
                }

                // Aplicar brilho: g(x) = f(x) + β
                if (brightness != 0.0) {
                    value += brightness * 255.0;
                }

                // Aplicar gamma
                if (gamma != 1.0) {
                    value = Math.max(0, Math.min(255, value));
                    value = 255.0 * Math.pow(value / 255.0, 1.0 / gamma);
                }

                brightnessContrastLUT[i] = (int) Math.max(0, Math.min(255, value));
            }
        }

        // Processar pixels
        if (saturation != 1.0) {
            // Processamento com saturação (mais complexo)
            for (int i = 0; i < srcPixels.length; i++) {
                int rgb = srcPixels[i];

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Aplicar brilho + contraste + gamma via LUT
                if (brightnessContrastLUT != null) {
                    r = brightnessContrastLUT[r];
                    g = brightnessContrastLUT[g];
                    b = brightnessContrastLUT[b];
                }

                // Aplicar saturação
                float gray = r * 0.299f + g * 0.587f + b * 0.114f;
                r = clamp((int) (gray + saturation * (r - gray)), 0, 255);
                g = clamp((int) (gray + saturation * (g - gray)), 0, 255);
                b = clamp((int) (gray + saturation * (b - gray)), 0, 255);

                dstPixels[i] = (r << 16) | (g << 8) | b;
            }
        } else {
            // Processamento sem saturação (muito mais rápido)
            if (brightnessContrastLUT != null) {
                for (int i = 0; i < srcPixels.length; i++) {
                    int rgb = srcPixels[i];

                    int r = brightnessContrastLUT[(rgb >> 16) & 0xFF];
                    int g = brightnessContrastLUT[(rgb >> 8) & 0xFF];
                    int b = brightnessContrastLUT[rgb & 0xFF];

                    dstPixels[i] = (r << 16) | (g << 8) | b;
                }
            } else {
                // Sem filtros ativos
                System.arraycopy(srcPixels, 0, dstPixels, 0, srcPixels.length);
            }
        }

        // Definir pixels processados
        filtered.setRGB(0, 0, width, height, dstPixels, 0, width);

        return filtered;
    }
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void showBrightnessDialog(VideoPlayer videoPlayer) {

        // Criar diálogo customizado
        JDialog dialog = new JDialog(videoPlayer, "Ajustar Brilho", true);
        dialog.setSize(450, 200);
        dialog.setLocationRelativeTo(videoPlayer);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Painel superior com informações
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Ajustar Brilho", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        topPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel infoLabel = new JLabel("<html><center>Arraste o controle para ajustar<br>(-1.0 = mais escuro, 0 = normal, +1.0 = mais claro)</center></html>", SwingConstants.CENTER);
        topPanel.add(infoLabel, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);

        // Painel central com slider
        JPanel centerPanel = new JPanel(new BorderLayout(10, 5));

        JLabel valueLabel = new JLabel(String.format("Valor: %.2f", brightness), SwingConstants.CENTER);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 14));

        JSlider slider = new JSlider(-100, 100, (int) (brightness * 100));
        slider.setPreferredSize(new Dimension(100, 20));
        slider.setMajorTickSpacing(50);
        slider.setMinorTickSpacing(10);
        // slider.setPaintTicks(true);
        //slider.setPaintLabels(true);


        // Criar labels personalizados
        java.util.Hashtable<Integer, JLabel> labelTable = new java.util.Hashtable<>();
        labelTable.put(-100, new JLabel("-1.0"));
        labelTable.put(-50, new JLabel("-0.5"));
        labelTable.put(0, new JLabel("0"));
        labelTable.put(50, new JLabel("0.5"));
        labelTable.put(100, new JLabel("1.0"));
        slider.setLabelTable(labelTable);

        slider.addChangeListener(e -> {
            double value = slider.getValue() / 100.0;
            valueLabel.setText(String.format("Valor: %.2f", value));
        });

        centerPanel.add(valueLabel, BorderLayout.NORTH);
        centerPanel.add(slider, BorderLayout.CENTER);
        panel.add(centerPanel, BorderLayout.CENTER);

        // Painel inferior com botões
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        JButton applyButton = new JButton("Aplicar");
        JButton resetButton = new JButton("Resetar");
        JButton cancelButton = new JButton("Cancelar");

        applyButton.addActionListener(e -> {
            brightness = slider.getValue() / 100.0;
            filtersEnabled = true;
            dialog.dispose();

            // Mostrar mensagem de processamento
            SwingUtilities.invokeLater(() -> {
                JDialog processingDialog = new JDialog(videoPlayer, "Processando...", true);
                processingDialog.setSize(300, 100);
                processingDialog.setLocationRelativeTo(videoPlayer);
                processingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

                JPanel procPanel = new JPanel(new BorderLayout(10, 10));
                procPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                JLabel procLabel = new JLabel("Aplicando filtros, aguarde...", SwingConstants.CENTER);
                procPanel.add(procLabel, BorderLayout.CENTER);
                processingDialog.add(procPanel);

                // Aplicar filtros em thread separada
                new Thread(() -> {
                    applyFilters(videoPlayer);
                    SwingUtilities.invokeLater(() -> processingDialog.dispose());
                }).start();

                processingDialog.setVisible(true);
            });
        });

        resetButton.addActionListener(e -> {
            slider.setValue(0);
            valueLabel.setText("Valor: 0.00");
        });

        cancelButton.addActionListener(e -> {
            dialog.dispose();
        });

        buttonPanel.add(resetButton);
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    public boolean isFiltersEnabled() {
        return filtersEnabled;
    }

    public void setFiltersEnabled(boolean filtersEnabled) {
        this.filtersEnabled = filtersEnabled;
    }

    public double getBrightness() {
        return brightness;
    }

    public void setBrightness(double brightness) {
        this.brightness = brightness;
    }

    public double getContrast() {
        return contrast;
    }

    public void setContrast(double contrast) {
        this.contrast = contrast;
    }

    public double getGamma() {
        return gamma;
    }

    public void setGamma(double gamma) {
        this.gamma = gamma;
    }

    public double getSaturation() {
        return saturation;
    }

    public void setSaturation(double saturation) {
        this.saturation = saturation;
    }
}
