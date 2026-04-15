package com.esl.videoplayer.Video;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioStreams {
    private VideoPlayer videoPlayer;
    private int currentAudioStream = 0;
    private int totalAudioStreams = 0;
    private Map<Integer, String> audioStreamNames = new HashMap<>();
    public Map<Integer, Integer> logicalToPhysicalAudioStream = new HashMap<>();

    public AudioStreams(VideoPlayer videoPlayer){
        this.videoPlayer = videoPlayer;
    }

    public int getCurrentAudioStream() {
        return currentAudioStream;
    }

    public Map<Integer, String> getAudioStreamNames() {
        return audioStreamNames;
    }

    public int getTotalAudioStreams() {
        return totalAudioStreams;
    }

    public Map<Integer, Integer> getLogicalToPhysicalAudioStream() {
        return logicalToPhysicalAudioStream;
    }

    public void setAudioStreamNames(Map<Integer, String> audioStreamNames) {
        this.audioStreamNames = audioStreamNames;
    }

    public void setCurrentAudioStream(int currentAudioStream) {
        this.currentAudioStream = currentAudioStream;
    }

    public void setTotalAudioStreams(int totalAudioStreams) {
        this.totalAudioStreams = totalAudioStreams;
    }

    public void loadVideoWithAudioStream(String filepath, int audioStream) {
        // Salvar informações das streams ANTES de fechar
        int savedTotalAudioStreams = totalAudioStreams;
        Map<Integer, String> savedAudioNames = new HashMap<>(audioStreamNames);
        Map<Integer, String> savedSubtitleNames = new HashMap<>(videoPlayer.getSubtitleManager().getSubtitleStreamNames());
        int savedTotalSubtitleStreams = videoPlayer.getSubtitleManager().getTotalSubtitleStreams();
        Map<Integer, Integer> savedAudioMapping = new HashMap<>(logicalToPhysicalAudioStream); // NOVO

        // Fechar recursos atuais
        if (videoPlayer.getGrabber() != null) {
            try {
                videoPlayer.getGrabber().stop();
                videoPlayer.getGrabber().release();
            } catch (Exception e) {
                System.out.println("Erro ao fechar grabber anterior: " + e.getMessage());
            }
            videoPlayer.setGrabber(null);
        }

        if (videoPlayer.getAudioLine() != null && videoPlayer.getAudioLine().isOpen()) {
            videoPlayer.getAudioLine().close();
            videoPlayer.setAudioLine(null);
        }

        // Limpar estado (mas preservar informações de streams)
        videoPlayer.getSubtitleManager().getSubtitleStreamNames().clear();
        videoPlayer.getSubtitleManager().setCurrentSubtitleText("");
        videoPlayer.getSubtitleManager().setCurrentSubtitleStream(-1);
        videoPlayer.setCurrentFrame(0);

        // Desabilitar controles durante carregamento
        videoPlayer.getPlayPauseButton().setEnabled(false);
        videoPlayer.getStopButton().setEnabled(false);
        videoPlayer.getProgressSlider().setEnabled(false);
        videoPlayer.getOpenButton().setEnabled(false);
        videoPlayer.getVolumeButton().setEnabled(false);

        videoPlayer.setTitle("Media Player - Carregando...");

        // Carregar vídeo em thread separada
        new Thread(() -> {
            try {
                videoPlayer.setVideoFilePath(filepath);
                videoPlayer.setCurrentVideoPath(filepath);

                System.out.println("Abrindo arquivo com stream de áudio lógico " + audioStream + ": " + filepath);

                videoPlayer.setGrabber(new FFmpegFrameGrabber(filepath));

                // Converter índice lógico para físico
                int physicalStreamIndex = savedAudioMapping.getOrDefault(audioStream, audioStream);

                // IMPORTANTE: SEMPRE definir stream de áudio usando índice físico
                videoPlayer.getGrabber().setAudioStream(physicalStreamIndex);
                System.out.println("Stream de áudio selecionada: lógico=" + audioStream + ", físico=" + physicalStreamIndex);

                String extension = filepath.substring(filepath.lastIndexOf('.') + 1).toLowerCase();

                if (videoPlayer.getVideoExecution().hardwareAccelerationEnabled) {
                    videoPlayer.getVideoExecution().tryEnableHardwareAcceleration(videoPlayer.getGrabber());
                }

                if (extension.equals("wmv")) {
                    try {
                        // grabber.setOption("threads", "auto");
                        videoPlayer.getGrabber().setOption("fflags", "nobuffer");
                        // grabber.setOption("flags", "low_delay");
                    } catch (Exception e) {
                        System.out.println("Não foi possível aplicar opções WMV: " + e.getMessage());
                    }
                }

                try {
                    videoPlayer.getGrabber().setOption("analyzeduration", "2000000");
                    videoPlayer.getGrabber().setOption("probesize", "2000000");
                    videoPlayer.getGrabber().setOption("fflags", "+genpts");
                } catch (Exception e) {
                    System.out.println("Não foi possível aplicar opções de análise rápida");
                }

                System.out.println("Iniciando grabber...");
                videoPlayer.getGrabber().start();
                System.out.println("Grabber iniciado com sucesso!");

                // IMPORTANTE: Usar total de streams salvo (não re-detectar)
                totalAudioStreams = savedTotalAudioStreams;
                currentAudioStream = audioStream;

                // Restaurar mapeamento
                logicalToPhysicalAudioStream = new HashMap<>(savedAudioMapping);
                System.out.println("Mapeamento de áudio restaurado: " + logicalToPhysicalAudioStream);

                // Restaurar nomes das streams
                audioStreamNames = new HashMap<>(savedAudioNames);
                videoPlayer.getSubtitleManager().setSubtitleStreamNames(new HashMap<>(savedSubtitleNames));
                videoPlayer.getSubtitleManager().setTotalSubtitleStreams(savedTotalSubtitleStreams);

                System.out.println("Total de faixas de áudio (restaurado): " + totalAudioStreams);
                System.out.println("Stream de áudio atual: " + currentAudioStream);
                System.out.println("Nomes de áudio restaurados: " + audioStreamNames.size());
                System.out.println("Nomes de legendas restaurados: " + videoPlayer.getSubtitleManager().getSubtitleStreamNames().size());
                System.out.println("Total de legendas: " + videoPlayer.getSubtitleManager().getTotalSubtitleStreams());

                // Só detectar legendas se ainda não temos informação
                if (videoPlayer.getSubtitleManager().getSubtitleStreamNames().isEmpty()) {
                    new Thread(() -> {
                        try {
                            videoPlayer.getSubtitleManager().detectEmbeddedSubtitles(filepath, videoPlayer.getFfprobePath());
                        } catch (Exception e) {
                            System.out.println("Não foi possível detectar legendas embutidas: " + e.getMessage());
                        }
                    }, "SubtitleDetector").start();
                }

                videoPlayer.setTotalFrames(videoPlayer.getGrabber().getLengthInVideoFrames());
                videoPlayer.setFrameRate(videoPlayer.getGrabber().getVideoFrameRate());
                videoPlayer.setCurrentFrame(0);

                System.out.println("Total frames bruto: " + videoPlayer.getTotalFrames() + ", FPS bruto: " + videoPlayer.getFrameRate());

                if (videoPlayer.getFrameRate() > 120 || videoPlayer.getFrameRate() < 1) {
                    System.out.println("FPS inválido, corrigindo para 30");
                    videoPlayer.setFrameRate(30.0);
                }

                System.out.println("Configurando áudio...");
                videoPlayer.setAudioChannels(videoPlayer.getGrabber().getAudioChannels());
                videoPlayer.setSampleRate(videoPlayer.getGrabber().getSampleRate());
                System.out.println("Canais: " + videoPlayer.getAudioChannels() + ", SampleRate: " + videoPlayer.getSampleRate());

                if (videoPlayer.getAudioChannels() > 0 && videoPlayer.getSampleRate() > 0 && !extension.equals("gif")) {
                    System.out.println("Criando audioLine...");
                    try {
                        int outputChannels = videoPlayer.getAudioChannels() > 2 ? 2 : videoPlayer.getAudioChannels();

                        AudioFormat audioFormat = new AudioFormat(videoPlayer.getSampleRate(), 16, outputChannels, true, true);
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        videoPlayer.setAudioLine((SourceDataLine) AudioSystem.getLine(info));

                        int bufferSize = videoPlayer.getSampleRate() * outputChannels * 2;
                        if (extension.equals("wmv")) bufferSize *= 4;

                        videoPlayer.getAudioLine().open(audioFormat, bufferSize);
                        System.out.println("AudioLine configurado com sucesso");
                    } catch (Exception audioEx) {
                        System.err.println("Erro ao configurar áudio: " + audioEx.getMessage());
                        videoPlayer.setAudioLine(null);
                    }
                } else {
                    System.out.println("Sem áudio");
                }

                System.out.println("Procurando legendas externas...");
                videoPlayer.getSubtitleManager().searchExternalSubtitles(filepath);
                System.out.println("Busca de legendas concluída");

                System.out.println("Vídeo carregado! Habilitando UI...");

                SwingUtilities.invokeLater(() -> {
                    videoPlayer.getPlayPauseButton().setEnabled(true);
                    videoPlayer.getStopButton().setEnabled(true);
                    videoPlayer.getProgressSlider().setEnabled(true);
                    videoPlayer.getProgressSlider().setValue(0);
                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getVolumeButton().setEnabled(true);

                    videoPlayer.updateTimeLabel();

                    videoPlayer.setTitle("Media Player - " + new File(filepath).getName());

                    System.out.println("UI HABILITADA - Pronto para reproduzir!");
                    System.out.println("Estado final:");
                    System.out.println("  totalAudioStreams: " + totalAudioStreams);
                    System.out.println("  currentAudioStream: " + currentAudioStream);
                    System.out.println("  audioStreamNames: " + audioStreamNames);
                    System.out.println("  logicalToPhysicalAudioStream: " + logicalToPhysicalAudioStream);
                    System.out.println("  totalSubtitleStreams: " + videoPlayer.getSubtitleManager().getTotalSubtitleStreams());
                    System.out.println("  subtitleStreamNames: " + videoPlayer.getSubtitleManager().getSubtitleStreamNames());
                });

            } catch (Exception e) {


                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("videoPlayer.ErrorLoadVideoWithAudioStream.text") + "\n" + e.getMessage(),
                            I18N.get("videoPlayer.ErrorLoadVideoWithAudioStream.title"), JOptionPane.ERROR_MESSAGE);

                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getPlayPauseButton().setEnabled(false);
                    videoPlayer.getStopButton().setEnabled(false);
                    videoPlayer.getVolumeButton().setEnabled(false);
                    videoPlayer.setTitle("Media Player");
                });
            }
        }, "VideoLoader").start();
    }

    // Modificar o método detectAudioStreamNames
    public void detectAudioStreamNames(String filepath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    videoPlayer.getFfprobePath(),
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_streams",
                    "-select_streams", "a",
                    filepath
            );

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();

            String json = output.toString();
            parseAudioStreamsFromJson(json);

        } catch (Exception e) {
            System.out.println("Não foi possível detectar nomes de streams de áudio: " + e.getMessage());
            // Usar nomes padrão e mapeamento 1:1
            for (int i = 0; i < totalAudioStreams; i++) {
                if (!audioStreamNames.containsKey(i)) {
                    audioStreamNames.put(i, "Audio Track " + (i + 1));
                }
                logicalToPhysicalAudioStream.put(i, i);
            }
        }
    }

    private void parseAudioStreamsFromJson(String json) {
        int audioLogicalIndex = 0;

        Pattern indexPattern = Pattern.compile("\"index\"\\s*:\\s*(\\d+)");
        Pattern langPattern = Pattern.compile("\"(?:language|TAG:language)\"\\s*:\\s*\"([^\"]+)\"");
        Pattern titlePattern = Pattern.compile("\"(?:TAG:)?title\"\\s*:\\s*\"([^\"]+)\"");
        Pattern codecPattern = Pattern.compile("\"codec_type\"\\s*:\\s*\"audio\"");

        Matcher codecMatcher = codecPattern.matcher(json);

        while (codecMatcher.find()) {
            int matchStart = codecMatcher.start();
            int streamStart = json.lastIndexOf("{", matchStart);
            int streamEnd = videoPlayer.getSubtitleManager().findMatchingBrace(json, streamStart);

            if (streamStart != -1 && streamEnd > streamStart) {
                String streamData = json.substring(streamStart, streamEnd);

                // Extrair índice físico da stream no container
                int physicalIndex = audioLogicalIndex; // Default
                Matcher indexMatcher = indexPattern.matcher(streamData);
                if (indexMatcher.find()) {
                    physicalIndex = Integer.parseInt(indexMatcher.group(1));
                }

                // Mapear índice lógico (0, 1, 2...) para físico (pode ser 1, 2, 3... no MKV)
                logicalToPhysicalAudioStream.put(audioLogicalIndex, physicalIndex);

                // Extrair idioma
                String lang = null;
                Matcher langMatcher = langPattern.matcher(streamData);
                if (langMatcher.find()) {
                    lang = langMatcher.group(1);
                }

                // Extrair título
                String title = null;
                Matcher titleMatcher = titlePattern.matcher(streamData);
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1);
                }

                // Criar nome do stream
                String streamName;
                if (title != null && !title.isEmpty()) {
                    streamName = title;
                } else if (lang != null && !lang.isEmpty()) {
                    streamName = "Áudio " + lang.toUpperCase();
                } else {
                    streamName = "Audio Track " + (audioLogicalIndex + 1);
                }

                audioStreamNames.put(audioLogicalIndex, streamName);
                System.out.println("Stream lógico " + audioLogicalIndex + " → físico " + physicalIndex + ": " + streamName);

                audioLogicalIndex++;
            }
        }
    }
    public void switchAudioStream(int streamIndex) {
        if (streamIndex == currentAudioStream) {
            System.out.println("Já está na stream de áudio " + streamIndex);
            return;
        }

        if (videoPlayer.getVideoFilePath() == null) {
            System.out.println("Nenhum vídeo carregado");
            return;
        }

        System.out.println("Trocando para stream de áudio lógico: " + streamIndex);

        // Salvar TOTAL de streams ANTES de fechar
        int totalStreamsBeforeClose = totalAudioStreams;
        Map<Integer, String> audioNamesBeforeClose = new HashMap<>(audioStreamNames);
        Map<Integer, Integer> audioMappingBeforeClose = new HashMap<>(logicalToPhysicalAudioStream);

        // Salvar estado atual
        videoPlayer.saveVideoState();

        // Garantir que o total de streams está salvo corretamente
        if (videoPlayer.getSavedAudioStreamNames().isEmpty() && totalStreamsBeforeClose > 0) {
            for (int i = 0; i < totalStreamsBeforeClose; i++) {
                String name = audioNamesBeforeClose.getOrDefault(i, "Audio Track " + (i + 1));
                videoPlayer.getSavedAudioStreamNames().put(i, name);
            }
            System.out.println("Forçado salvamento de " + totalStreamsBeforeClose + " streams de áudio");
        }

        // Fechar recursos atuais
        try {
            if (videoPlayer.isPlaying()) {
                videoPlayer.setPlaying(false);
                if (videoPlayer.getPlaybackThread() != null) {
                    videoPlayer.getPlaybackThread().interrupt();
                    videoPlayer.getPlaybackThread().join(500);
                }
            }

            if (videoPlayer.getAudioLine() != null && videoPlayer.getAudioLine().isOpen()) {
                videoPlayer.getAudioLine().close();
                videoPlayer.setAudioLine(null);
            }

            if (videoPlayer.getGrabber() != null) {
                videoPlayer.getGrabber().stop();
                videoPlayer.getGrabber().release();
                videoPlayer.setGrabber(null);
            }

        } catch (Exception e) {
            System.err.println("Erro ao fechar recursos: " + e.getMessage());
        }

        // Definir nova stream de áudio
        currentAudioStream = streamIndex;

        // Recarregar vídeo com nova stream de áudio
        new Thread(() -> {
            try {
                Thread.sleep(100);

                SwingUtilities.invokeLater(() -> {
                    try {
                        videoPlayer.setGrabber(new FFmpegFrameGrabber(videoPlayer.getVideoFilePath()));

                        // Converter índice lógico para físico
                        int physicalStreamIndex = audioMappingBeforeClose.getOrDefault(streamIndex, streamIndex);

                        // SEMPRE definir stream de áudio explicitamente usando índice físico
                        videoPlayer.getGrabber().setAudioStream(physicalStreamIndex);
                        System.out.println("Stream de áudio definida: lógico=" + streamIndex + ", físico=" + physicalStreamIndex);

                        String extension = videoPlayer.getVideoFilePath().substring(videoPlayer.getVideoFilePath().lastIndexOf('.') + 1).toLowerCase();

                        // Por:
                        if (videoPlayer.getVideoExecution().hardwareAccelerationEnabled) {
                            videoPlayer.getVideoExecution().tryEnableHardwareAcceleration(videoPlayer.getGrabber());
                        }

                        if (extension.equals("wmv")) {
                            try {
                                //   grabber.setOption("threads", "auto");
                                videoPlayer.getGrabber().setOption("fflags", "nobuffer");
                                //   grabber.setOption("flags", "low_delay");
                            } catch (Exception e) {
                                System.out.println("Erro nas opções WMV: " + e.getMessage());
                            }
                        }

                        try {
                            videoPlayer.getGrabber().setOption("analyzeduration", "2000000");
                            videoPlayer.getGrabber().setOption("probesize", "2000000");
                            videoPlayer.getGrabber().setOption("fflags", "+genpts");
                        } catch (Exception e) {
                            System.out.println("Erro nas opções gerais: " + e.getMessage());
                        }

                        videoPlayer.getGrabber().start();
                        System.out.println("Grabber reiniciado com sucesso");

                        // Restaurar informações salvas
                        totalAudioStreams = totalStreamsBeforeClose;
                        logicalToPhysicalAudioStream = new HashMap<>(audioMappingBeforeClose);
                        System.out.println("Total de faixas de áudio (do backup): " + totalAudioStreams);
                        System.out.println("Mapeamento de áudio restaurado: " + logicalToPhysicalAudioStream);

                        if (!videoPlayer.getSavedAudioStreamNames().isEmpty()) {
                            audioStreamNames = new HashMap<>(videoPlayer.getSavedAudioStreamNames());
                            System.out.println("Nomes das streams de áudio restaurados: " + audioStreamNames.size());
                        }

                        if (!videoPlayer.getSavedSubtitleStreamNames().isEmpty()) {
                            videoPlayer.getSubtitleManager().setSubtitleStreamNames(new HashMap<>(videoPlayer.getSavedSubtitleStreamNames()));
                            System.out.println("Nomes das streams de legendas restaurados: " + videoPlayer.getSubtitleManager().getSubtitleStreamNames().size());
                        }

                        videoPlayer.setTotalFrames(videoPlayer.getGrabber().getLengthInVideoFrames());
                        videoPlayer.setFrameRate(videoPlayer.getGrabber().getVideoFrameRate());

                        videoPlayer.setAudioChannels(videoPlayer.getGrabber().getAudioChannels());
                        videoPlayer.setSampleRate(videoPlayer.getGrabber().getSampleRate());

                        System.out.println("Áudio detectado após start: Canais=" + videoPlayer.getAudioChannels() + ", SampleRate=" + videoPlayer.getSampleRate());

                        if (videoPlayer.getAudioChannels() > 0 && videoPlayer.getSampleRate() > 0) {
                            int outputChannels = videoPlayer.getAudioChannels() > 2 ? 2 : videoPlayer.getAudioChannels();

                            if (videoPlayer.getAudioChannels() > 2) {
                                System.out.println("Áudio " + videoPlayer.getAudioChannels() + " canais detectado, fazendo downmix para estéreo");
                            }

                            AudioFormat audioFormat = new AudioFormat(videoPlayer.getSampleRate(), 16, outputChannels, true, true);
                            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                            videoPlayer.setAudioLine((SourceDataLine) AudioSystem.getLine(info));

                            int bufferSize = videoPlayer.getSampleRate() * outputChannels * 2;
                            if (extension.equals("wmv")) bufferSize *= 4;

                            videoPlayer.getAudioLine().open(audioFormat, bufferSize);
                            System.out.println("Novo áudio configurado: " + videoPlayer.getSampleRate() + "Hz, " +videoPlayer.getAudioChannels() + " canais → " + outputChannels + " canais");
                            System.out.println("AudioLine criado e aberto com sucesso");
                        } else {
                            System.err.println("AVISO: Nenhum canal de áudio detectado! Canais=" + videoPlayer.getAudioChannels() + ", SampleRate=" + videoPlayer.getSampleRate());
                        }

                        videoPlayer.restoreVideoStateAfterAudioSwitch();

                    } catch (Exception e) {
                        System.err.println("Erro ao trocar stream de áudio: " + e.getMessage());
                        e.printStackTrace();

                        JOptionPane.showMessageDialog(videoPlayer,
                                I18N.get("videoPlayer.AudioStreamSwitch.error") + "\n" + e.getMessage(),
                                I18N.get("videoPlayer.BatchCapture.showMessageDialog.Error.title"), JOptionPane.ERROR_MESSAGE);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "AudioStreamSwitcher").start();
    }

}
