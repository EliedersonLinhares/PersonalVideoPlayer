package com.esl.videoplayer.audio;

import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ExtractAudio implements I18N.LanguageChangeListener {
    public void confirmExtraction(MainPanel mainPanel, VideoPlayer videoPlayer, FFmpegFrameGrabber grabber) {

        String[][] formatos = {
                {"MP3",  "mp3"},
                {"AAC",  "aac"},
                {"FLAC", "flac"},
                {"OGG",  "ogg"},
                {"WAV",  "wav"},
                {"OPUS", "opus"},
                {"M4A",  "m4a"},
                {"WMA",  "wma"},
                {"AC3",  "ac3"},
                {"AIFF", "aiff"}
        };

        // Bitrates — FLAC/WAV/AIFF ignoram, mas não causam erro
        String[] bitrates = {"96k", "128k", "192k", "256k", "320k"};

        String[] nomes = Arrays.stream(formatos)
                .map(f -> f[0])
                .toArray(String[]::new);

        JComboBox<String> comboFormato  = new JComboBox<>(nomes);
        JComboBox<String> comboBitrate  = new JComboBox<>(bitrates);
        comboFormato.setSelectedIndex(0);  // MP3
        comboBitrate.setSelectedIndex(1);  // 128k como padrão

        // Desabilita bitrate para formatos sem perdas
        comboFormato.addActionListener(e -> {
            String sel = formatos[comboFormato.getSelectedIndex()][1];
            boolean semPerdas = sel.equals("flac") || sel.equals("wav") || sel.equals("aiff");
            comboBitrate.setEnabled(!semPerdas);
        });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 4, 4, 4);
        gbc.anchor  = GridBagConstraints.WEST;

        // Linha 0 — pergunta
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel(I18N.get("extractAudio.JLabel1")), gbc);

        // Linha 1 — formato
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel(I18N.get("extractAudio.JLabel2")), gbc);
        gbc.gridx = 1;
        panel.add(comboFormato, gbc);

        // Linha 2 — bitrate
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel(I18N.get("extractAudio.JLabel3")), gbc);
        gbc.gridx = 1;
        panel.add(comboBitrate, gbc);

        int resposta = JOptionPane.showConfirmDialog(
                mainPanel,
                panel,
                I18N.get("extractAudio.ConfirmDialog.title"),
                JOptionPane.YES_NO_OPTION
        );

        if (resposta == JOptionPane.YES_OPTION) {
            String formatoEscolhido = formatos[comboFormato.getSelectedIndex()][1];
            String bitrateEscolhido = comboBitrate.isEnabled()
                    ? Objects.requireNonNull(comboBitrate.getSelectedItem()).toString()
                    : null; // null = deixa o FFmpeg decidir para sem perdas
            iniciarProcessamento(mainPanel,videoPlayer, grabber, formatoEscolhido, bitrateEscolhido);
        }
    }
    private void iniciarProcessamento(MainPanel mainPanel, VideoPlayer videoPlayer, FFmpegFrameGrabber grabberOriginal,
                                      String formato, String bitrate) {
        try { grabberOriginal.stop(); } catch (Exception e) { e.printStackTrace(); }

        String inputPath  = videoPlayer.getVideoFilePath();
        String outputPath = inputPath.replaceAll("\\.[^.]+$", "") + "_audio." + formato;

        JDialog janelaProgresso = new JDialog(videoPlayer, I18N.get("extractAudio.JDialog.title"), true);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        janelaProgresso.add(new JLabel(I18N.get("extractAudio.JLabel4")), BorderLayout.NORTH);
        janelaProgresso.add(progressBar, BorderLayout.CENTER);
        janelaProgresso.setSize(300, 100);
        janelaProgresso.setLocationRelativeTo(mainPanel);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
                    grabber.start();
                    long totalMicros = grabber.getLengthInTime();

                    try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 0)) {
                        recorder.setAudioChannels(grabber.getAudioChannels());
                        recorder.setSampleRate(grabber.getSampleRate());
                        recorder.setAudioCodec(resolverCodec(formato));
                        recorder.setFormat(formato);

                        // Aplica bitrate apenas se foi fornecido (com perdas)
                        if (bitrate != null) {
                            int bps = Integer.parseInt(bitrate.replace("k", "")) * 1000;
                            recorder.setAudioBitrate(bps);
                        }

                        // OPUS exige 48000 Hz obrigatoriamente
                        if (formato.equals("opus")) {
                            recorder.setSampleRate(48000);
                        }

                        recorder.start();

                        Frame frame;
                        while ((frame = grabber.grabSamples()) != null) {
                            recorder.recordSamples(frame.sampleRate, frame.audioChannels, frame.samples);
                            if (totalMicros > 0) {
                                publish((int) ((double) grabber.getTimestamp() / totalMicros * 100));
                            }
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                progressBar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                janelaProgresso.dispose();
                try {
                    get();
                    JOptionPane.showMessageDialog(videoPlayer,  I18N.get("extractAudio.showMessageDialog.success.text") + "\n" + outputPath);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(videoPlayer,   I18N.get("extractAudio.showMessageDialog.success.error") + " " + e.getMessage());
                } finally {
                    // Sempre reinicializa o grabber, independente de sucesso ou erro
                    SwingUtilities.invokeLater(videoPlayer::reinicializarGrabber);
                }
            }
        };

        worker.execute();
        janelaProgresso.setVisible(true);
    }
    private int resolverCodec(String formato) {
        return switch (formato) {
            case "aac", "m4a" -> avcodec.AV_CODEC_ID_AAC;
            case "flac" -> avcodec.AV_CODEC_ID_FLAC;
            case "ogg"  -> avcodec.AV_CODEC_ID_VORBIS;
            case "wav"  -> avcodec.AV_CODEC_ID_PCM_S16LE;
            case "opus" -> avcodec.AV_CODEC_ID_OPUS;
            case "wma"  -> avcodec.AV_CODEC_ID_WMAV2;
            case "ac3"  -> avcodec.AV_CODEC_ID_AC3;
            case "aiff" -> avcodec.AV_CODEC_ID_PCM_S16BE;
            default     -> avcodec.AV_CODEC_ID_MP3;
        };
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
     //Action when language are changed
    }
}
