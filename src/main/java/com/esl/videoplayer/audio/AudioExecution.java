package com.esl.videoplayer.audio;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.io.File;
import java.nio.ShortBuffer;
import java.util.Arrays;

public class AudioExecution {
    private VideoPlayer videoPlayer;

    public AudioExecution(VideoPlayer videoPlayer){
        this.videoPlayer = videoPlayer;
    }

    public void loadAudio(String filepath) {
        videoPlayer.getMainPanel().clearFilteredImage();
        // Salvar caminho do áudio
        videoPlayer.setCurrentVideoPath(filepath);
        videoPlayer.setVideoFilePath(filepath);

        System.out.println("=== INÍCIO loadAudio ===");

        // NOVO: Limpar playlist e fechar dialog
        videoPlayer.playListExecution.clearPlaylistAndCloseDialog(videoPlayer.getMainPanel());

        // ADICIONAR: Registrar arquivo como recente
        videoPlayer.getRecentFilesManager().addRecentFile(filepath, false);

        loadAudioBase(filepath);
    }

    public void loadAudioFromPlaylist(String filepath) {
        // Salvar caminho do áudio
        videoPlayer.setCurrentVideoPath(filepath);
        videoPlayer.setVideoFilePath(filepath);

        System.out.println("=== INÍCIO loadAudioFromPlaylist  ===");

        loadAudioBase(filepath);
    }

    private void loadAudioBase(String filepath) {

        //Limpa os items setados anteriormente
        videoPlayer.cleanUpItems();

        System.out.println("Iniciando thread de carregamento de áudio...");

        // Resetar normalização
        videoPlayer.getAudioLoudnessManager().setNormalizationCalculated(false);
        videoPlayer.getAudioLoudnessManager().setNormalizationSampleCount(0);
        videoPlayer.getAudioLoudnessManager().setNormalizationGain(1.0f);
        videoPlayer. getAudioLoudnessManager().setMaxPeakLevel(0.0f);
        videoPlayer.getAudioLoudnessManager().setAverageRMS(0.0f);
        videoPlayer.getAudioLoudnessManager().setRmsHistoryIndex(0);
        Arrays.fill(videoPlayer.getAudioLoudnessManager().getRmsHistory(), 0.0f);
        videoPlayer.getLoudnessAnalyzer().reset();


        // Carregar áudio em thread separada
        Thread loaderThread = new Thread(() -> {
            System.out.println("Thread de carregamento de áudio INICIADA");
            try {
                System.out.println("1. Criando FFmpegFrameGrabber para áudio...");
                videoPlayer.setGrabber(new FFmpegFrameGrabber(filepath));
                System.out.println("2. FFmpegFrameGrabber criado");

                // Opções para melhorar performance de áudio
                System.out.println("3. Aplicando opções de áudio...");
                try {
                    videoPlayer.getGrabber().setOption("analyzeduration", "2000000");
                    videoPlayer.getGrabber().setOption("probesize", "2000000");
                    System.out.println("3. Opções aplicadas");
                } catch (Exception e) {
                    System.out.println("Erro nas opções: " + e.getMessage());
                }

                System.out.println("4. Chamando grabber.start()...");
                videoPlayer.getGrabber().start();
                System.out.println("5. grabber.start() CONCLUÍDO!");

                // Marcar como áudio apenas
                videoPlayer.setAudioOnly(true);

                System.out.println("11. Tentando extrair cover art...");
                videoPlayer.getCoverArt().extractCoverArt(filepath, videoPlayer.getFfmpegPath(), videoPlayer.getMainPanel());

                System.out.println("6. Obtendo informações do áudio...");
                videoPlayer.setAudioChannels(videoPlayer.getGrabber().getAudioChannels());
                videoPlayer.setSampleRate(videoPlayer.getGrabber().getSampleRate());
                System.out.println("7. Canais: " + videoPlayer.getAudioChannels() + ", SampleRate: " + videoPlayer.getSampleRate());

                // Calcular duração em "frames" baseado na taxa de amostragem
                // Para áudio, vamos simular frames a 30 FPS para controle de progresso
                videoPlayer.setFrameRate(30.0);
                double durationSeconds = videoPlayer.getGrabber().getLengthInTime() / 1000000.0; // microsegundos para segundos
                videoPlayer.setTotalFrames((long) (durationSeconds * videoPlayer.getFrameRate()));
                videoPlayer.setCurrentFrame(0);
                System.out.println("8. Duração: " + durationSeconds + "s, Frames simulados: " + videoPlayer.getTotalFrames());

                // Configurar linha de áudio
                if (videoPlayer.getAudioChannels() > 0 && videoPlayer.getSampleRate() > 0) {
                    System.out.println("9. Criando audioLine...");
                    try {
                        int outputChannels = videoPlayer.getAudioChannels() > 2 ? 2 : videoPlayer.getAudioChannels();

                        if (videoPlayer.getAudioChannels() > 2) {
                            System.out.println("Áudio " + videoPlayer.getAudioChannels() + " canais detectado, fazendo downmix para estéreo");
                        }

                        AudioFormat audioFormat = new AudioFormat(
                                videoPlayer.getSampleRate(),
                                16,                    // 16-bit samples
                                outputChannels,        // stereo ou mono
                                true,                  // signed
                                true                   // big-endian
                        );

                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        videoPlayer.setAudioLine((SourceDataLine) AudioSystem.getLine(info));

                        int bufferSize = videoPlayer.getSampleRate() * outputChannels * 2;
                        videoPlayer.getAudioLine().open(audioFormat, bufferSize);
                        System.out.println("10. AudioLine configurado com sucesso");


                        // Pré-análise de loudness (amostragem dos primeiros 10 segundos)
                        float peakLevel = 0f;
                        long analyzeUntil = Math.min(videoPlayer.getGrabber().getLengthInTime(), 10_000_000L); // 10s em micros

                        try (FFmpegFrameGrabber analyzer = new FFmpegFrameGrabber(filepath)) {
                            analyzer.start();
                            Frame f;
                            while ((f = analyzer.grabSamples()) != null && analyzer.getTimestamp() < analyzeUntil) {
                                if (f.samples != null && f.samples[0] instanceof ShortBuffer sb) {
                                    while (sb.hasRemaining()) {
                                        float sample = Math.abs(sb.get() / 32768f);
                                        if (sample > peakLevel) peakLevel = sample;
                                    }
                                    sb.rewind();
                                }
                            }
                        }
                        // Calcula gain para normalizar ao redor de 0.85 de pico
                        float targetPeak = 0.85f;
                        float detectedGain = (peakLevel > 0.01f) ? (targetPeak / peakLevel) : 1.0f;
                        float finalGain = Math.min(detectedGain, 4.0f); // limita para não distorcer demais

                        videoPlayer.getAudioLoudnessManager().setGlobalAudioGain(0.20f);
                        videoPlayer.getAudioLoudnessManager().setAudioNormalizationEnabled(false);
                        videoPlayer.getAudioLoudnessManager().setTargetLoudness(-18.0f);

                        System.out.println("Loudness detectado — peak: " + peakLevel + ", gain aplicado: " + finalGain);


//                        // Combinação default - deve refletir o que está no menu
//                      audioLoudnessManager.setGlobalAudioGain(0.20f);          // 20% do volume
//                      audioLoudnessManager.setTargetLoudness(-18.0f);         // Target mais baixo
//                      audioLoudnessManager.setAudioNormalizationEnabled(false); // normalização desativada

                    } catch (Exception audioEx) {
                        System.err.println("10. Erro ao configurar áudio: " + audioEx.getMessage());
                        videoPlayer.setAudioLine(null);
                    }
                }

                System.out.println("11. Áudio carregado! Habilitando UI...");

                SwingUtilities.invokeLater(() -> {

                    // Redimensionar e centralizar a janela
                    videoPlayer.setSize(700, 500);
                    videoPlayer.setLocationRelativeTo(null); // IMPORTANTE: Centralizar após redimensionar
                    videoPlayer. setResizable(false);// Desabilta maximizar a janela
                    System.out.println("12. SwingUtilities.invokeLater EXECUTANDO");
                    videoPlayer.getPlayPauseButton().setEnabled(true);
                    videoPlayer.getStopButton().setEnabled(true);
                    videoPlayer.getProgressSlider().setEnabled(true);
                    videoPlayer.getProgressSlider().setValue(0);
                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getRewindButton().setEnabled(true);
                    videoPlayer.getForwardButton().setEnabled(true);
                    videoPlayer.getVolumeButton().setEnabled(true);
                    videoPlayer.getVolumeSlider().setEnabled(true);

                    // Desabilitar controles de vídeo
                    videoPlayer.getNextFrameButton().setEnabled(false);
                    videoPlayer.getCaptureFrameButton().setEnabled(false);
                    videoPlayer.getCaptureAllFrameButton().setEnabled(false);

                    videoPlayer.updateTimeLabel();

                    // Limpar painel de vídeo e mostrar mensagem
//                    mainPanel.updateImage(null);
//                    mainPanel.repaint();
                    // Mostrar spectrum analyzer

                    // USAR O MÉTODO PÚBLICO EM VEZ DE ACESSAR spectrumPanel DIRETAMENTE
                    videoPlayer.getMainPanel().setSpectrumSize(600, 400);
                    // Ativar reflexo
                    videoPlayer.getMainPanel().setSpectrumReflection(true);

                    // Ajustar altura do reflexo (0.0 a 1.0)
                    videoPlayer.getMainPanel().setSpectrumReflectionHeight(1f); // 50% da altura original

                    // Ajustar transparência do reflexo (0 a 255)
                    videoPlayer.getMainPanel().setSpectrumReflectionAlpha(180); // Mais transparente

                    videoPlayer.getMainPanel().showSpectrum();

                    // Ajustar opacidade da capa (0.0 = invisível, 1.0 = opaco)
                    videoPlayer.getMainPanel().setCoverOpacity(0.5f); // 30% de opacidade (padrão)

                    // Para capa mais visível
                    // mainPanel.setCoverOpacity(0.5f);

                    // Para capa mais discreta
                    // mainPanel.setCoverOpacity(0.2f);


                    // ATIVAR MODO COVER_PALETTE se houver capa
                    videoPlayer.getMainPanel().setSpectrumColorMode(AudioSpectrumPanel.ColorMode.COVER_PALETTE);
                    videoPlayer.setTitle("Media Player - " + new File(filepath).getName());

                    videoPlayer.getMainPanel().setupAudioContextMenu(videoPlayer, videoPlayer.getGrabber(), videoPlayer.getPlaylistManager());

                    videoPlayer.playVideoOrAudio();
                    System.out.println("13. UI HABILITADA - Pronto para reproduzir áudio!");
                });

                System.out.println("14. Thread de carregamento de áudio CONCLUÍDA");

            } catch (Exception e) {
                System.err.println("ERRO CRÍTICO na thread de carregamento de áudio:");
                e.printStackTrace();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("videoPlayer.AudioFileThread.error") + "\n" + e.getMessage(),
                            I18N.get("videoPlayer.BatchCapture.showMessageDialog.Error.title"), JOptionPane.ERROR_MESSAGE);

                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getPlayPauseButton().setEnabled(false);
                    videoPlayer.getStopButton().setEnabled(false);
                    videoPlayer.getVolumeButton().setEnabled(false);
                    videoPlayer.getVolumeSlider().setEnabled(true);
                    videoPlayer.setTitle("Media Player");
                });
            }
        }, "AudioLoader");

        loaderThread.start();
        System.out.println("=== FIM loadAudio (método principal) ===");
    }
}
