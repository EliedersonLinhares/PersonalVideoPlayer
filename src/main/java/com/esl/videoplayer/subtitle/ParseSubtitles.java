package com.esl.videoplayer.subtitle;

public class ParseSubtitles {

    private String cleanSubtitleText(String text) {
        if (text == null || text.isEmpty()) return text;

        // Remover tags HTML/XML (incluindo ASS tags)
        // Exemplos: <font face="sans-serif" size="71">texto</font>, <i>texto</i>, <b>texto</b>
        text = text.replaceAll("<[^>]+>", "");

        // Remover códigos de formatação ASS (entre chaves)
        // Exemplos: {\an8}, {\pos(960,30)}, {\fad(300,300)}
        text = text.replaceAll("\\{[^}]+\\}", "");

        // Remover comandos de override ASS
        // Exemplos: {\i1}, {\b1}, {}
        text = text.replaceAll("\\\\[a-zA-Z]+\\d*", "");

        // Remover espaços múltiplos
        text = text.replaceAll("\\s+", " ");

        // Remover espaços no início e fim
        text = text.trim();

        return text;
    }

    public void parseASS(String content, SubtitleManager subtitleManager) {
        System.out.println("Iniciando parse ASS/SSA...");
        subtitleManager.getSubtitles().clear();

        try {
            content = content.replace("\r\n", "\n").replace("\r", "\n");
            String[] lines = content.split("\n");

            boolean inEventsSection = false;
            int textColumnIndex = -1;
            int startColumnIndex = -1;
            int endColumnIndex = -1;

            for (String line : lines) {
                line = line.trim();

                // Detectar início da seção [Events]
                if (line.equalsIgnoreCase("[Events]")) {
                    inEventsSection = true;
                    continue;
                }

                // Detectar outra seção (sair de Events)
                if (line.startsWith("[") && !line.equalsIgnoreCase("[Events]")) {
                    inEventsSection = false;
                    continue;
                }

                if (!inEventsSection) continue;

                // Parsear formato das colunas
                if (line.startsWith("Format:")) {
                    String formatLine = line.substring(7).trim();
                    String[] columns = formatLine.split(",");

                    for (int i = 0; i < columns.length; i++) {
                        String col = columns[i].trim().toLowerCase();
                        if (col.equals("text")) textColumnIndex = i;
                        else if (col.equals("start")) startColumnIndex = i;
                        else if (col.equals("end")) endColumnIndex = i;
                    }
                    continue;
                }

                // Parsear diálogos
                if (line.startsWith("Dialogue:") && textColumnIndex != -1) {
                    String dialogueLine = line.substring(9).trim();
                    String[] parts = dialogueLine.split(",", textColumnIndex + 1);

                    if (parts.length > textColumnIndex) {
                        try {
                            String startTime = parts[startColumnIndex].trim();
                            String endTime = parts[endColumnIndex].trim();
                            String text = parts[textColumnIndex].trim();

                            // Limpar formatação
                            text = cleanSubtitleText(text);

                            if (!text.isEmpty()) {
                                long start = parseASSTimestamp(startTime);
                                long end = parseASSTimestamp(endTime);

                                subtitleManager.getSubtitles().add(new SubtitleEntry(start, end, text));
                            }
                        } catch (Exception e) {
                            System.err.println("Erro ao parsear linha ASS: " + e.getMessage());
                        }
                    }
                }
            }

            System.out.println("ASS parseado com sucesso: " + subtitleManager.getSubtitles().size() + " entradas");

        } catch (Exception e) {
            System.err.println("Erro crítico no parse ASS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private long parseASSTimestamp(String timestamp) {
        try {
            // Formato ASS: H:MM:SS.cc (onde cc são centésimos de segundo)
            timestamp = timestamp.trim();

            String[] parts = timestamp.split("[:.]");

            if (parts.length >= 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                int centiseconds = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;

                // Converter centésimos para milissegundos
                int millis = centiseconds * 10;

                return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis;
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Erro ao parsear timestamp ASS '" + timestamp + "': " + e.getMessage());
            return 0;
        }
    }

    public void parseSRT(String content, SubtitleManager subtitleManager) {
        System.out.println("Iniciando parse SRT...");
        subtitleManager.getSubtitles().clear();

        try {
            // Normalizar quebras de linha
            content = content.replace("\r\n", "\n").replace("\r", "\n");

            // Split por blocos (separados por linha vazia dupla)
            String[] blocks = content.split("\n\n+");

            System.out.println("Total de blocos encontrados: " + blocks.length);

            for (String block : blocks) {
                block = block.trim();
                if (block.isEmpty()) continue;

                String[] lines = block.split("\n");
                if (lines.length < 3) continue; // Precisa ter: número, timestamp, texto

                try {
                    // Linha 0: número (ignorar)
                    // Linha 1: timestamp
                    String timeLine = lines[1];

                    if (!timeLine.contains("-->")) continue;

                    String[] times = timeLine.split("-->");
                    if (times.length != 2) continue;

                    long startTime = parseTimestamp(times[0].trim());
                    long endTime = parseTimestamp(times[1].trim());

                    // Linhas 2+: texto da legenda
                    StringBuilder text = new StringBuilder();
                    for (int i = 2; i < lines.length; i++) {
                        if (text.length() > 0) text.append("\n");
                        text.append(lines[i]);
                    }

                    // IMPORTANTE: Limpar tags HTML/ASS do texto
                    String cleanedText = cleanSubtitleText(text.toString());

                    if (!cleanedText.isEmpty()) {
                        subtitleManager.getSubtitles().add(new SubtitleEntry(startTime, endTime, cleanedText));
                    }

                } catch (Exception e) {
                    // Pular entrada inválida
                    System.err.println("Erro ao parsear bloco: " + e.getMessage());
                }
            }

            System.out.println("SRT parseado com sucesso: " + subtitleManager.getSubtitles().size() + " entradas");

        } catch (Exception e) {
            System.err.println("Erro crítico no parse SRT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void parseVTT(String content, SubtitleManager subtitleManager) {
        System.out.println("Iniciando parse VTT...");
        subtitleManager.getSubtitles().clear();

        try {
            // Remover header WEBVTT
            content = content.replaceFirst("WEBVTT[^\n]*\n+", "");

            // Normalizar quebras de linha
            content = content.replace("\r\n", "\n").replace("\r", "\n");

            // Split por blocos
            String[] blocks = content.split("\n\n+");

            System.out.println("Total de blocos VTT encontrados: " + blocks.length);

            for (String block : blocks) {
                block = block.trim();
                if (block.isEmpty()) continue;

                String[] lines = block.split("\n");
                if (lines.length < 2) continue;

                try {
                    // Encontrar linha de timestamp (contém -->)
                    String timeLine = null;
                    int textStartIndex = 0;

                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].contains("-->")) {
                            timeLine = lines[i];
                            textStartIndex = i + 1;
                            break;
                        }
                    }

                    if (timeLine == null) continue;

                    String[] times = timeLine.split("-->");
                    if (times.length != 2) continue;

                    // VTT usa ponto ao invés de vírgula
                    String startStr = times[0].trim().replace('.', ',');
                    String endStr = times[1].trim().split(" ")[0].replace('.', ','); // Remover opções após o tempo

                    long startTime = parseTimestamp(startStr);
                    long endTime = parseTimestamp(endStr);

                    // Texto da legenda
                    StringBuilder text = new StringBuilder();
                    for (int i = textStartIndex; i < lines.length; i++) {
                        if (text.length() > 0) text.append("\n");
                        text.append(lines[i]);
                    }

                    // Limpar tags HTML/VTT
                    String cleanedText = cleanSubtitleText(text.toString());

                    if (!cleanedText.isEmpty()) {
                        subtitleManager.getSubtitles().add(new SubtitleEntry(startTime, endTime, cleanedText));
                    }

                } catch (Exception e) {
                    System.err.println("Erro ao parsear bloco VTT: " + e.getMessage());
                }
            }

            System.out.println("VTT parseado com sucesso: " + subtitleManager.getSubtitles().size() + " entradas");

        } catch (Exception e) {
            System.err.println("Erro crítico no parse VTT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private long parseTimestamp(String timestamp) {
        try {
            // Formato: HH:MM:SS,mmm ou MM:SS,mmm
            timestamp = timestamp.trim();

            // Remover qualquer coisa após espaço (alguns arquivos têm posição, etc)
            if (timestamp.contains(" ")) {
                timestamp = timestamp.split(" ")[0];
            }

            String[] parts = timestamp.split("[:,]");

            if (parts.length == 4) {
                // HH:MM:SS,mmm
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                int millis = Integer.parseInt(parts[3]);

                return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis;
            } else if (parts.length == 3) {
                // MM:SS,mmm
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                int millis = Integer.parseInt(parts[2]);

                return (minutes * 60000L) + (seconds * 1000L) + millis;
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Erro ao parsear timestamp '" + timestamp + "': " + e.getMessage());
            return 0;
        }
    }
}
