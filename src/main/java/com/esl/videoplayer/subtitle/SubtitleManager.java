package com.esl.videoplayer.subtitle;

import com.esl.videoplayer.VideoPlayer;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubtitleManager {
    private int currentSubtitleStream = -1; // -1 = desabilitado
    private int totalSubtitleStreams = 0;
    private Map<Integer, String> subtitleStreamNames = new HashMap<>();
    private List<SubtitleEntry> subtitles = new ArrayList<>();
    private String currentSubtitleText = "";
    private int baseSubtitleFontSize = 24; // Tamanho base configurado pelo usuário
    private Color subtitleColor = Color.WHITE;

    private ParseSubtitles parseSubtitles;

   public  SubtitleManager(){
       parseSubtitles = new ParseSubtitles();
   }

    public void searchExternalSubtitles(String videoPath) {
        System.out.println("21. Iniciando busca de legendas externas...");
        File videoFile = new File(videoPath);
        String baseName = videoFile.getName().replaceFirst("[.][^.]+$", "");
        File parentDir = videoFile.getParentFile();

        String[] subtitleExtensions = {".srt", ".sub", ".ass", ".ssa", ".vtt"};

        for (String ext : subtitleExtensions) {
            File subFile = new File(parentDir, baseName + ext);
            if (subFile.exists()) {
                System.out.println("Legenda externa encontrada: " + subFile.getName());
                System.out.println("NÃO carregando automaticamente - usuário pode carregar pelo menu");
                // NÃO CARREGAR AUTOMATICAMENTE - só informar
                // loadSubtitleFile(subFile); // <-- REMOVIDO
                return;
            }
        }

        System.out.println("Nenhuma legenda externa encontrada");
    }

    public void loadSubtitleFile(File file, VideoPlayer videoPlayer) {
        System.out.println("Carregando legenda: " + file.getName());

        new Thread(() -> {
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                System.out.println("Arquivo lido: " + content.length() + " caracteres");

                // Detectar formato
                String filename = file.getName().toLowerCase();
                if (filename.endsWith(".srt")) {
                    System.out.println("Parseando SRT...");
                    parseSubtitles.parseSRT(content, this);
                } else if (filename.endsWith(".vtt")) {
                    System.out.println("Parseando VTT...");
                    parseSubtitles.parseVTT(content, this);
                } else if (filename.endsWith(".ass") || filename.endsWith(".ssa")) {
                    System.out.println("Parseando ASS/SSA...");
                  parseSubtitles.parseASS(content, this);
                } else {
                    System.out.println("Formato de legenda não suportado: " + file.getName());
                    return;
                }

                currentSubtitleStream = -2;
                System.out.println("Legenda parseada: " + subtitles.size() + " entradas");

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(videoPlayer,
                            "Legenda carregada com sucesso!",
                            "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception e) {
                System.err.println("Erro ao carregar legenda: " + e.getMessage());
                e.printStackTrace();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(videoPlayer,
                            "Erro ao carregar legenda: " + e.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "SubtitleLoader").start();
    }


    public int getCurrentSubtitleStream() {
        return currentSubtitleStream;
    }

    public void setCurrentSubtitleStream(int currentSubtitleStream) {
        this.currentSubtitleStream = currentSubtitleStream;
    }

    public void detectEmbeddedSubtitles(String videoPath, String ffprobePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath,
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_streams",
                    videoPath
            );

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();

            // Parse JSON simples (sem biblioteca externa)
            String json = output.toString();
            parseSubtitleStreamsFromJson(json);

            System.out.println("Total de faixas de legendas embutidas: " + totalSubtitleStreams);

        } catch (Exception e) {
            System.out.println("ffprobe não disponível, tentando método alternativo...");
            // Fallback: analisar output do console do FFmpeg
            detectSubtitlesFromConsoleOutput();
        }
    }
    private void detectSubtitlesFromConsoleOutput() {
        // Método alternativo: capturar output do FFmpeg que já está no console
        // Nota: Isso já está sendo impresso pelo JavaCV, vamos apenas contar
        totalSubtitleStreams = 0;

        // Como último recurso, assumir que não há legendas
        System.out.println("Não foi possível detectar legendas automaticamente");
    }
    private void parseSubtitleStreamsFromJson(String json) {
        // Parse simples sem biblioteca JSON
        int streamIndex = 0;
        int subtitleIndex = 0;

        // Procurar por streams de legendas
        Pattern streamPattern = Pattern.compile("\"codec_type\"\\s*:\\s*\"subtitle\"");

        Matcher streamMatcher = streamPattern.matcher(json);

        while (streamMatcher.find()) {
            int matchStart = streamMatcher.start();

            // Procurar para trás até achar o início deste stream (último '{' antes do match)
            int streamStart = json.lastIndexOf("{", matchStart);

            // Procurar o fim do objeto (considerar objetos aninhados)
            int streamEnd = findMatchingBrace(json, streamStart);

            if (streamStart != -1 && streamEnd > streamStart) {
                String streamData = json.substring(streamStart, streamEnd);

                // Extrair índice do stream
                Pattern indexPattern = Pattern.compile("\"index\"\\s*:\\s*(\\d+)");
                Matcher indexMatcher = indexPattern.matcher(streamData);
                if (indexMatcher.find()) {
                    streamIndex = Integer.parseInt(indexMatcher.group(1));
                }

                // Extrair idioma (dentro de tags)
                String lang = null;
                Pattern langPattern = Pattern.compile("\"(?:language|TAG:language)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher langMatcher = langPattern.matcher(streamData);
                if (langMatcher.find()) {
                    lang = langMatcher.group(1);
                }

                // Extrair título (dentro de tags)
                String title = null;
                Pattern titlePattern = Pattern.compile("\"(?:TAG:)?title\"\\s*:\\s*\"([^\"]+)\"");
                Matcher titleMatcher = titlePattern.matcher(streamData);
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1);
                }

                // Criar nome do stream
                String streamName;
                if (title != null && !title.isEmpty()) {
                    streamName = title;
                    if (lang != null && !lang.isEmpty()) {
                        streamName += " (" + lang + ")";
                    }
                } else if (lang != null && !lang.isEmpty()) {
                    streamName = "Subtitle " + (subtitleIndex + 1) + " (" + lang + ")";
                } else {
                    streamName = "Subtitle " + (subtitleIndex + 1);
                }

                subtitleStreamNames.put(subtitleIndex, streamName);

                System.out.println("Legenda embutida encontrada: Stream " + streamIndex + " - " + streamName);

                subtitleIndex++;
            }
        }

        totalSubtitleStreams = subtitleIndex;
    }
    public int findMatchingBrace(String json, int openBraceIndex) {
        int count = 1;
        for (int i = openBraceIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                count++;
            } else if (c == '}') {
                count--;
                if (count == 0) {
                    return i + 1;
                }
            }
        }
        return json.length();
    }
    public void loadExternalSubtitle(String videoFilePath, VideoPlayer videoPlayer) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Selecionar Arquivo de Legenda");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(java.io.File f) {
                String name = f.getName().toLowerCase();
                return f.isDirectory() || name.endsWith(".srt") || name.endsWith(".sub") ||
                        name.endsWith(".ass") || name.endsWith(".ssa") || name.endsWith(".vtt");
            }

            public String getDescription() {
                return "Arquivos de Legenda (*.srt, *.sub, *.ass, *.vtt)";
            }
        });

        if (videoFilePath != null) {
            fileChooser.setCurrentDirectory(new File(videoFilePath).getParentFile());
        }

        if (fileChooser.showOpenDialog(videoPlayer) == JFileChooser.APPROVE_OPTION) {
            loadSubtitleFile(fileChooser.getSelectedFile(), videoPlayer);
        }
    }

    public void updateSubtitle(long currentTimeMs, VideoPlayer.VideoPanel videoPanel) {
        if (getCurrentSubtitleStream() == -1 || subtitles.isEmpty()) {
            currentSubtitleText = "";
            return;
        }

        // Buscar legenda correspondente ao tempo atual
        for (SubtitleEntry entry : subtitles) {
            if (currentTimeMs >= entry.getStartTime() && currentTimeMs <= entry.getEndTime()) {
                if (!currentSubtitleText.equals(entry.getText())) {
                    currentSubtitleText = entry.getText();
                    videoPanel.repaint();
                }
                return;
            }
        }

        // Nenhuma legenda no tempo atual
        if (!currentSubtitleText.isEmpty()) {
            currentSubtitleText = "";
            videoPanel.repaint();
        }
    }

    // Modificar o método drawSubtitles()
    public void drawSubtitles(Graphics2D g2d, int panelWidth, int panelHeight, int getJPanelHeight) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Usar tamanho adaptativo baseado no tamanho da janela
        int adaptiveFontSize = getAdaptiveSubtitleSize(getJPanelHeight);
        Font subtitleFont = new Font("Arial", Font.BOLD, adaptiveFontSize);
        g2d.setFont(subtitleFont);

        // Dividir texto em linhas
        String[] lines = getCurrentSubtitleText().split("\n");
        FontMetrics fm = g2d.getFontMetrics();

        int lineHeight = fm.getHeight();
        int totalHeight = lineHeight * lines.length;

        // Posição vertical: 10% da altura da tela acima da parte inferior
        int marginBottom = (int) (panelHeight * 0.10);
        int startY = panelHeight - totalHeight - marginBottom;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int textWidth = fm.stringWidth(line);
            int textX = (panelWidth - textWidth) / 2;
            int textY = startY + (i * lineHeight) + fm.getAscent();

            // Sombra preta (contorno proporcional ao tamanho)
            g2d.setColor(Color.BLACK);
            int shadowSize = Math.max(2, adaptiveFontSize / 12);

            for (int dx = -shadowSize; dx <= shadowSize; dx++) {
                for (int dy = -shadowSize; dy <= shadowSize; dy++) {
                    if (dx != 0 || dy != 0) {
                        g2d.drawString(line, textX + dx, textY + dy);
                    }
                }
            }

            // Texto com cor configurável
            g2d.setColor(getSubtitleColor());
            g2d.drawString(line, textX, textY);
        }
    }

    // Novo método para calcular tamanho adaptativo
    private int getAdaptiveSubtitleSize(int getJPanelHeight) {
        int panelHeight = getJPanelHeight;

        // Tamanho de referência: 600px de altura = tamanho base
        // Escala proporcionalmente baseado na altura do painel
        double heightRatio = panelHeight / 600.0;

        // Aplicar escala ao tamanho base, com limites
        int adaptiveSize = (int) (getBaseSubtitleFontSize() * heightRatio);

        // Limitar entre 12px e 120px para evitar extremos
        adaptiveSize = Math.max(12, Math.min(120, adaptiveSize));

        return adaptiveSize;
    }

    public void switchSubtitleStream(int streamIndex, String videoFilePath, VideoPlayer videoPlayer, String ffmpegPath) {
        setCurrentSubtitleStream(streamIndex);
        System.out.println("Trocando para legenda embutida stream: " + streamIndex);

        if (videoFilePath == null) {
            System.err.println("Caminho do vídeo não disponível");
            return;
        }

        // Extrair legenda embutida usando FFmpeg em thread separada
        new Thread(() -> {
            try {
                videoPlayer.togglePlayPause();
                // Sempre extrair como SRT para normalizar formato
                File tempSubtitle = File.createTempFile("subtitle_", ".srt");
                tempSubtitle.deleteOnExit();

                System.out.println("Extraindo legenda para: " + tempSubtitle.getAbsolutePath());
                System.out.println("Comando: ffmpeg -i \"" + videoFilePath + "\" -map 0:s:" + streamIndex + " \"" + tempSubtitle.getAbsolutePath() + "\"");

                ProcessBuilder pb = new ProcessBuilder(
                        ffmpegPath,
                        "-i", videoFilePath,
                        "-map", "0:s:" + streamIndex,
                        "-c:s", "srt", // Converter para SRT
                        "-y",
                        tempSubtitle.getAbsolutePath()
                );

                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean hasError = false;

                while ((line = reader.readLine()) != null) {
                    System.out.println("FFmpeg: " + line);

                    if (line.toLowerCase().contains("error") || line.toLowerCase().contains("invalid")) {
                        hasError = true;
                        System.err.println("ERRO: " + line);
                    }
                }

                int exitCode = process.waitFor();

                System.out.println("FFmpeg terminou com código: " + exitCode);
                System.out.println("Arquivo existe: " + tempSubtitle.exists());
                System.out.println("Tamanho do arquivo: " + tempSubtitle.length() + " bytes");

                if (exitCode == 0 && tempSubtitle.exists() && tempSubtitle.length() > 0) {
                    System.out.println("Legenda extraída com sucesso!");

                    try (BufferedReader br = new BufferedReader(new FileReader(tempSubtitle))) {
                        System.out.println("=== Primeiras linhas da legenda ===");
                        for (int i = 0; i < 5 && br.ready(); i++) {
                            System.out.println(br.readLine());
                        }
                        System.out.println("=================================");
                    }

                    SwingUtilities.invokeLater(() -> {
                        loadSubtitleFile(tempSubtitle, videoPlayer);
                        if (!getSubtitles().isEmpty()) {
                            JOptionPane.showMessageDialog(videoPlayer,
                                    "Legenda carregada com sucesso!",
                                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                        }

                    });

                } else {
                    throw new Exception("Falha ao extrair legenda (código: " + exitCode + ", tamanho: " + tempSubtitle.length() + ")");
                }
                videoPlayer.togglePlayPause();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Erro detalhado: " + e.getMessage());

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(videoPlayer,
                            "Não foi possível carregar a legenda embutida.\n" +
                                    "Possíveis causas:\n" +
                                    "- FFmpeg não está na pasta lib app\n" +
                                    "- Stream de legenda incompatível\n" +
                                    "- Formato de legenda não suportado\n\n" +
                                    "Erro: " + e.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();

    }


    public int getTotalSubtitleStreams() {
        return totalSubtitleStreams;
    }

    public void setTotalSubtitleStreams(int totalSubtitleStreams) {
        this.totalSubtitleStreams = totalSubtitleStreams;
    }

    public Map<Integer, String> getSubtitleStreamNames() {
        return subtitleStreamNames;
    }

    public void setSubtitleStreamNames(Map<Integer, String> subtitleStreamNames) {
        this.subtitleStreamNames = subtitleStreamNames;
    }

    public List<SubtitleEntry> getSubtitles() {
        return subtitles;
    }

    public void setSubtitles(List<SubtitleEntry> subtitles) {
        this.subtitles = subtitles;
    }

    public String getCurrentSubtitleText() {
        return currentSubtitleText;
    }

    public void setCurrentSubtitleText(String currentSubtitleText) {
        this.currentSubtitleText = currentSubtitleText;
    }

    public int getBaseSubtitleFontSize() {
        return baseSubtitleFontSize;
    }

    public void setBaseSubtitleFontSize(int baseSubtitleFontSize) {
        this.baseSubtitleFontSize = baseSubtitleFontSize;
    }

    public Color getSubtitleColor() {
        return subtitleColor;
    }

    public void setSubtitleColor(Color subtitleColor) {
        this.subtitleColor = subtitleColor;
    }
}
