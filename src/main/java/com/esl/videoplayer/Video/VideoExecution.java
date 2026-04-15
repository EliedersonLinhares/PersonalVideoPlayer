package com.esl.videoplayer.Video;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.io.File;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1;

public class VideoExecution {
    public boolean hardwareAccelerationEnabled = false;
    private VideoPlayer videoPlayer;

    public VideoExecution(VideoPlayer videoPlayer){
        this.videoPlayer = videoPlayer;
    }

    public void loadVideo(String filepath) {
        videoPlayer.getMainPanel().clearFilteredImage();
        videoPlayer.setAudioOnly(false);

        // Salvar caminho do vídeo
        videoPlayer.setCurrentVideoPath(filepath);
        videoPlayer.setVideoFilePath(filepath);

        System.out.println("=== INÍCIO loadVideo ===");
        // Limpar playlist e fechar dialog
        videoPlayer.playListExecution.clearPlaylistAndCloseDialog(videoPlayer.getMainPanel());

        // ADICIONAR: Registrar arquivo como recente
        videoPlayer.getRecentFilesManager().addRecentFile(filepath, false);

        loadVideoBase(filepath);
    }

    public void loadVideoFromPlaylist(String filepath) {
        videoPlayer.setAudioOnly(false);

        // Salvar caminho do vídeo
        videoPlayer.setCurrentVideoPath(filepath);
        videoPlayer.setVideoFilePath(filepath);

        System.out.println("=== INÍCIO loadVideo ===");

        loadVideoBase(filepath);
    }

    private void loadVideoBase(String filepath) {

        // **VERIFICAR SE É AV1 EM ALTA RESOLUÇÃO (BLOQUEAR SE SIM)**
        if (!checkAV1Resolution(filepath)) {
            System.out.println("Vídeo bloqueado - AV1 em alta resolução com decoder lento");
            return; // Sair sem carregar o vídeo
        }

        //Limpa os items setados anteriormente
        videoPlayer.cleanUpItems();

        System.out.println("Atualizando título...");
        videoPlayer.setTitle("Media Player - Carregando video...");

        System.out.println("Iniciando thread de carregamento...");

        // Carregar vídeo em thread separada
        Thread loaderThread = new Thread(() -> {
            System.out.println("Thread de carregamento INICIADA");
            try {
                System.out.println("1. videoFilePath definido: " + videoPlayer.getVideoFilePath());

                System.out.println("2. Criando FFmpegFrameGrabber...");
                videoPlayer.setGrabber(new FFmpegFrameGrabber(filepath));
                System.out.println("3. FFmpegFrameGrabber criado");

                String extension = filepath.substring(filepath.lastIndexOf('.') + 1).toLowerCase();
                System.out.println("4. Extensão detectada: " + extension);

                // **NOVO: Otimizar threads para todos os vídeos**
                System.out.println("5. Configurando threads...");
                try {
                    int numThreads = Runtime.getRuntime().availableProcessors();
                    System.out.println("Threads configuradas: " + numThreads);
                } catch (Exception e) {
                    System.out.println("Erro ao configurar threads: " + e.getMessage());
                }

                // Aplicar aceleração GPU se habilitada
                if (hardwareAccelerationEnabled) {
                    System.out.println("6. Tentando habilitar aceleração GPU...");
                    tryEnableHardwareAcceleration(videoPlayer.getGrabber());
                } else {
                    System.out.println("6. Aceleração GPU desabilitada");
                }

                // Opções para melhorar performance
                if (extension.equals("wmv")) {
                    System.out.println("7. Aplicando opções WMV...");
                    try {
                        //  grabber.setOption("threads", "auto");
                        videoPlayer.getGrabber().setOption("fflags", "nobuffer");
                        //  grabber.setOption("flags", "low_delay");
                    } catch (Exception e) {
                        System.out.println("Erro nas opções WMV: " + e.getMessage());
                    }
                } else {
                    System.out.println("7. Não é WMV, pulando opções específicas");
                }

                // Opções gerais
                System.out.println("8. Aplicando opções gerais...");
                try {
                    videoPlayer.getGrabber().setOption("analyzeduration", "2000000");
                    videoPlayer.getGrabber().setOption("probesize", "2000000");
                    videoPlayer.getGrabber().setOption("fflags", "+genpts");
                    System.out.println("8. Opções aplicadas");
                } catch (Exception e) {
                    System.out.println("Erro nas opções gerais: " + e.getMessage());
                }

                System.out.println("Iniciando pré-detecção de streams de áudio...");

                // Detectar mapeamento ANTES de start() usando um grabber temporário
                try {
                    FFmpegFrameGrabber tempGrabber = new FFmpegFrameGrabber(filepath);

                    tempGrabber.start();
                    tempGrabber.setOption("c:v", "libdav1d");
                    videoPlayer.getAudioStreams().setTotalAudioStreams(tempGrabber.getAudioStream());
                    tempGrabber.stop();
                    tempGrabber.release();

                    System.out.println("Total de faixas de áudio pré-detectadas: " + videoPlayer.getAudioStreams().getTotalAudioStreams());

                    // Detectar nomes e mapeamento
                    if (videoPlayer.getAudioStreams().getTotalAudioStreams() > 0) {
                        videoPlayer.getAudioStreams().detectAudioStreamNames(filepath);
                        System.out.println("Mapeamento de áudio detectado: " + videoPlayer.getAudioStreams().logicalToPhysicalAudioStream);
                    }
//                    // DEPOIS (correto):
//                    tempGrabber.stop();
//                    tempGrabber.release();
//
//                    detectAudioStreamNames(filepath); // detecta SEMPRE
                    videoPlayer.getAudioStreams().setTotalAudioStreams(videoPlayer.getAudioStreams().logicalToPhysicalAudioStream.size()); // usa o que o ffprobe encontrou

                    System.out.println("Total de faixas de áudio pré-detectadas: " + videoPlayer.getAudioStreams().getTotalAudioStreams());

                    // Agora usar o mapeamento para stream lógica 0
                    if (!videoPlayer.getAudioStreams().logicalToPhysicalAudioStream.isEmpty()) {

                        int physicalStream0 = videoPlayer.getAudioStreams().logicalToPhysicalAudioStream.get(0);
                        System.out.println("Definindo stream lógica 0 (física " + physicalStream0 + ")");
                        videoPlayer.getGrabber().setAudioStream(physicalStream0);

//                        // DEPOIS (correto):
//                        grabber.setAudioStream(0); // sempre começa no primeiro áudio lógico

                    } else {
                        System.out.println("Mapeamento vazio, não definindo stream de áudio");
                    }

                } catch (Exception e) {
                    System.out.println("Erro na pré-detecção de áudio: " + e.getMessage());
                    e.printStackTrace();
                    // Continuar sem setAudioStream - usará stream padrão
                }

                System.out.println("9. Chamando grabber.start()...");

                videoPlayer.getGrabber().start();
                System.out.println("10. grabber.start() CONCLUÍDO!");
                System.out.println(videoPlayer.getGrabber().getVideoMetadata());
                // **DETECTAR E OTIMIZAR AV1**
                System.out.println("11. Detectando codec...");

                detectAndOptimizeAV1(videoPlayer.getGrabber());
                // Definir stream atual como 0 (primeira lógica)
                videoPlayer.getAudioStreams().setCurrentAudioStream(0);
                System.out.println("12. Stream de áudio atual definida: " + videoPlayer.getAudioStreams().getCurrentAudioStream());

                // Detectar streams de legendas em thread separada
                System.out.println("13. Iniciando detecção de legendas em thread separada...");
                new Thread(() -> {
                    try {
                        System.out.println("Thread de legendas INICIADA");
                        videoPlayer.getSubtitleManager().detectEmbeddedSubtitles(filepath, videoPlayer.getFfprobePath());
                        System.out.println("Thread de legendas CONCLUÍDA");
                    } catch (Exception e) {
                        System.out.println("Não foi possível detectar legendas embutidas: " + e.getMessage());
                    }
                }, "SubtitleDetector").start();

                System.out.println("14. Obtendo totalFrames e frameRate...");
                videoPlayer.setTotalFrames(videoPlayer.getGrabber().getLengthInVideoFrames());
                videoPlayer.setFrameRate(videoPlayer.getGrabber().getVideoFrameRate());
                videoPlayer.setCurrentFrame(0);
                System.out.println("15. Total frames: " + videoPlayer.getTotalFrames() + ", FPS: " + videoPlayer.getFrameRate());

                // Correções de FPS
                System.out.println("16. Verificando correções de FPS...");

                if (extension.equals("flv") && videoPlayer.getFrameRate() > 100) {
                    double tbr = videoPlayer.getGrabber().getFrameRate();
                    if (tbr > 0 && tbr < 100) {
                        videoPlayer.setFrameRate(tbr);
                        System.out.println("FLV detectado - usando FPS corrigido: " + videoPlayer.getFrameRate());
                    } else {
                        videoPlayer.setFrameRate(29.97);
                        System.out.println("FLV detectado - usando FPS padrão: 29.97");
                    }
                }

                if (extension.equals("wmv") && videoPlayer.getFrameRate() > 100) {
                    double tbr = videoPlayer.getGrabber().getFrameRate();
                    if (tbr > 0 && tbr < 100) {
                        videoPlayer.setFrameRate(tbr);
                        System.out.println("WMV detectado - usando FPS corrigido: " + videoPlayer.getFrameRate());
                    } else {
                        videoPlayer.setFrameRate(30.0);
                        System.out.println("WMV detectado - usando FPS padrão: 30");
                    }
                }

                if (extension.equals("gif")) {
                    double tbr = videoPlayer.getGrabber().getFrameRate();
                    if (tbr > 0 && Math.abs(tbr - videoPlayer.getFrameRate()) > 1) {
                        videoPlayer.setFrameRate(tbr);
                        System.out.println("GIF detectado - usando FPS (tbr): " + videoPlayer.getFrameRate());
                    }
                    videoPlayer.setAudioLine(null);
                }

                if (videoPlayer.getFrameRate() > 120 || videoPlayer.getFrameRate() < 1) {
                    System.out.println("FPS inválido, corrigindo para 30");
                    videoPlayer.setFrameRate(30.0);
                }

                System.out.println("17. Configurando áudio...");
                videoPlayer.setAudioChannels(videoPlayer.getGrabber().getAudioChannels());
                videoPlayer.setSampleRate(videoPlayer.getGrabber().getSampleRate());
                System.out.println("18. Canais: " + videoPlayer.getAudioChannels() + ", SampleRate: " + videoPlayer.getSampleRate());

                if (videoPlayer.getAudioChannels() > 0 && videoPlayer.getSampleRate() > 0 && !extension.equals("gif")) {
                    System.out.println("19. Criando audioLine...");
                    try {
                        int outputChannels = videoPlayer.getAudioChannels() > 2 ? 2 : videoPlayer.getAudioChannels();

                        if (videoPlayer.getAudioChannels() > 2) {
                            System.out.println("Áudio " + videoPlayer.getAudioChannels() + " canais detectado, fazendo downmix para estéreo");
                        }

                        AudioFormat audioFormat = new AudioFormat(videoPlayer.getSampleRate(), 16, outputChannels, true, true);
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                        videoPlayer.setAudioLine((SourceDataLine) AudioSystem.getLine(info));

                        // **BUFFER MAIOR para evitar travamentos**
                        int bufferSize = videoPlayer.getSampleRate() * outputChannels * 4; // 4 bytes, 1 segundo de buffer

                        if (extension.equals("wmv")) {
                            bufferSize *= 2;
                        }

                        // **Para vídeos 4K (AV1), aumentar ainda mais o buffer**
                        int videoWidth = videoPlayer.getGrabber().getImageWidth();
                        if (videoWidth >= 3840) {
                            bufferSize *= 3; // 3 segundos de buffer para 4K
                            System.out.println("Vídeo 4K detectado - usando buffer de áudio expandido");
                        }

                        videoPlayer.getAudioLine().open(audioFormat, bufferSize);
                        System.out.println("20. AudioLine configurado com buffer de " + bufferSize + " bytes");
                    } catch (Exception audioEx) {
                        System.err.println("20. Erro ao configurar áudio: " + audioEx.getMessage());
                        videoPlayer.setAudioLine(null);
                    }
                } else {
                    System.out.println("19-20. Sem áudio");
                }

                System.out.println("21. Procurando legendas externas...");
                videoPlayer.getSubtitleManager().searchExternalSubtitles(filepath);
                System.out.println("22. Busca de legendas concluída");

                System.out.println("23. Vídeo carregado! Habilitando UI...");

                int videoWidth = videoPlayer.getGrabber().getImageWidth();
                int videoHeight = videoPlayer.getGrabber().getImageHeight();
                int tempVideoWidth = videoWidth;
                int tempVideoHeight = videoHeight;
                if (videoWidth <= 500) {
                    tempVideoWidth = 1080;
                }
                if (videoHeight <= 500) {
                    tempVideoHeight = 720;
                }

                // Guardar dimensões para usar no SwingUtilities.invokeLater
                final int finalWidth = tempVideoWidth;
                final int finalHeight = tempVideoHeight;
                System.out.println("Resoluçao do video: " + finalHeight + " : " + finalWidth);

                SwingUtilities.invokeLater(() -> {
                    // Redimensionar e centralizar a janela

                    videoPlayer.setSize(finalWidth, finalHeight);

                    // Se a resolução do video for igual ou maior que a resolução da tela maximizar
                    if (finalWidth >= videoPlayer.getScreenWidth() || finalHeight >= videoPlayer.getScreenHeight()) {
                        System.out.println("Excedeu ou é igual, maximizando ");
                        videoPlayer.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    }

                    videoPlayer.setLocationRelativeTo(null); // Centralizar após redimensionar
                    videoPlayer.setResizable(true); // Pode maximizar a janela

                    System.out.println("24. SwingUtilities.invokeLater EXECUTANDO");
                    videoPlayer.getPlayPauseButton().setEnabled(true);
                    videoPlayer.getStopButton().setEnabled(true);
                    videoPlayer.getProgressSlider().setEnabled(true);
                    videoPlayer.getProgressSlider().setValue(0);
                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getRewindButton().setEnabled(true);
                    videoPlayer.getForwardButton().setEnabled(true);
                    videoPlayer.getNextFrameButton().setEnabled(true);
                    videoPlayer.getCaptureFrameButton().setEnabled(true);
                    videoPlayer.getCaptureAllFrameButton().setEnabled(true);
                    videoPlayer.getVolumeButton().setEnabled(true);
                    videoPlayer.getVolumeSlider().setEnabled(true);
                    videoPlayer.updateTimeLabel();

                    videoPlayer.setTitle("Media Player - " + new File(filepath).getName());

                    //Menu de contexto especifico para quando estiver usando video
                    videoPlayer.getMainPanel().setupVideoContextMenu(videoPlayer.getSubtitleManager(), videoPlayer.getCaptureFrameManager(), videoPlayer,
                            videoPlayer.getFiltersManager(), videoPlayer.getVideoFilePath(), videoPlayer.getGrabber(), videoPlayer.getNextFrameButton(), videoPlayer.getAudioStreams().getTotalAudioStreams(),
                            videoPlayer.getAudioStreams().getCurrentAudioStream(), videoPlayer.getAudioStreams().getAudioStreamNames(), videoPlayer.getFfmpegPath(), videoPlayer.getPlaylistManager());

                    // Só para vídeo (não áudio)
                    long resumeFrame = videoPlayer.getVideoProgressManager().checkAndPromptResume(filepath, videoPlayer.getTotalFrames());

                    if (resumeFrame > 0) {
                        try {
                            videoPlayer.getGrabber().setFrameNumber((int) resumeFrame);
                        } catch (FFmpegFrameGrabber.Exception e) {
                            throw new RuntimeException(e);
                        }
                        videoPlayer.setCurrentFrame(resumeFrame);
                        int progress = (int) ((resumeFrame * 100) / videoPlayer.getTotalFrames());
                        videoPlayer.getProgressSlider().setValue(progress);
                        videoPlayer.updateTimeLabel();
                    }

                    videoPlayer.playVideoOrAudio();

                    System.out.println("25. UI HABILITADA - Pronto para reproduzir!");
                });

                System.out.println("26. Thread de carregamento CONCLUÍDA");

            } catch (Exception e) {
                System.err.println("ERRO CRÍTICO na thread de carregamento:");
                e.printStackTrace();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(videoPlayer,
                            I18N.get("videoPlayer.VideoFileThread.error") + "\n" + e.getMessage(),
                            I18N.get("videoPlayer.BatchCapture.showMessageDialog.Error.title"), JOptionPane.ERROR_MESSAGE);

                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getPlayPauseButton().setEnabled(false);
                    videoPlayer.getStopButton().setEnabled(false);
                    videoPlayer.getVolumeButton().setEnabled(false);
                    videoPlayer.getVolumeSlider().setEnabled(true);
                    videoPlayer.setTitle("Media Player");
                });
            }
        }, "VideoLoader");

        System.out.println("Thread criada, iniciando...");
        loaderThread.start();
        System.out.println("Thread iniciada! Aguardando conclusão...");
        System.out.println("=== FIM loadVideo (método principal) ===");
    }

    private void detectAndOptimizeAV1(FFmpegFrameGrabber grabber) {
        try {
            String videoCodec = grabber.getVideoCodecName();
            System.out.println("Codec detectado: " + videoCodec);

            // Se for AV1 e estiver usando libaom-av1, avisar
            if (videoCodec != null && videoCodec.toLowerCase().contains("av1")) {
                System.out.println("=== VÍDEO AV1 DETECTADO ===");

                if (videoCodec.contains("libaom")) {
                    System.out.println("AVISO: Usando libaom-av1 (encoder como decoder)");
                    System.out.println("Isso é mais lento. Idealmente deveria usar libdav1d.");
                    System.out.println("Solução: Recompilar JavaCV com libdav1d ou usar build diferente.");

                    // Configurações específicas do libaom-av1
                    grabber.setOption("cpu-used", "8"); // Velocidade de codificação média (0=lento/qualidade, 8=rápido/baixa qualidade)
                    grabber.setOption("crf", "28");    // Qualidade (valor padrão é geralmente bom, menor é melhor qualidade)
                    grabber.setVideoBitrate(2000000); // Exemplo de definição de bitrate (2 Mbps)
                    grabber.setOption("g", "150");    // GOP size para melhor capacidade de busca
                    grabber.setOption("fps", "20");
                    grabber.setOption("c:v", "libdav1d");
                    grabber.setVideoCodec(AV_CODEC_ID_AV1);
                    // grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P10LE); // 10-bit color (opcional)
                } else if (videoCodec.contains("libdav1d") || videoCodec.contains("dav1d")) {
                    System.out.println("✓ Usando libdav1d - decoder otimizado!");
                }

                // Informações sobre a configuração
                int threads = Runtime.getRuntime().availableProcessors();
                System.out.println("Threads disponíveis: " + threads);
                System.out.println("Resolução: " + grabber.getImageWidth() + "x" + grabber.getImageHeight());
                System.out.println("FPS: " + grabber.getVideoFrameRate());

                // Para vídeos 4K AV1, sugerir desabilitar filtros
                if (grabber.getImageWidth() >= 3840) {
                    System.out.println("DICA: Para vídeo 4K, desabilite filtros para melhor performance");
                }
            }

        } catch (Exception e) {
            System.out.println("Erro ao detectar codec: " + e.getMessage());
        }
    }

    /**
     * Verifica se o vídeo é AV1 com resolução acima de 1080p e bloqueia reprodução
     * Retorna true se o vídeo pode ser reproduzido, false se deve ser bloqueado
     */
    private boolean checkAV1Resolution(String filepath) {
        FFmpegFrameGrabber tempGrabber = null;

        try {
            String extension = filepath.substring(filepath.lastIndexOf('.') + 1).toLowerCase();

            // Verificar apenas arquivos MP4
            if (!extension.equals("mp5")) {
                return true; // Permitir outros formatos
            }

            System.out.println("Verificando codec e resolução do MP4...");

            // Criar grabber temporário para verificação
            tempGrabber = new FFmpegFrameGrabber(filepath);
            tempGrabber.start();

            // Obter informações do vídeo
            String videoCodec = tempGrabber.getVideoCodecName();
            int width = tempGrabber.getImageWidth();
            int height = tempGrabber.getImageHeight();

            System.out.println("Codec: " + videoCodec);
            System.out.println("Resolução: " + width + "x" + height);

            // Verificar se é AV1
            boolean isAV1 = false;
            if (videoCodec != null) {
                String codecLower = videoCodec.toLowerCase();
                isAV1 = codecLower.contains("av1") || codecLower.contains("libaom");
            }

            // Verificar se está usando libaom-av1 (decoder lento)
            boolean isSlowDecoder = false;
            if (isAV1 && videoCodec != null && videoCodec.contains("libaom")) {
                isSlowDecoder = true;
                System.out.println("AVISO: Detectado libaom-av1 (decoder lento)");
            }

            // Verificar resolução
            boolean isHighRes = (width > 1920 || height > 1080);

            // Fechar grabber temporário
            tempGrabber.stop();
            tempGrabber.release();

            // Bloquear se for AV1 com libaom E alta resolução
            if (isAV1 && isSlowDecoder && isHighRes) {
                System.out.println("BLOQUEADO: AV1 com libaom-av1 em resolução " + width + "x" + height);

                // Mostrar modal de aviso
                SwingUtilities.invokeLater(() -> {
                    String message = String.format(
                            I18N.get("videoPlayer.AV1CodecError.showMessageDialog.text1") + "\n\n" +
                                    I18N.get("videoPlayer.AV1CodecError.showMessageDialog.text2") + "\n" +
                                    I18N.get("videoPlayer.AV1CodecError.showMessageDialog.text3") + " " + width + "x" + height + "\n\n" +
                                    I18N.get("videoPlayer.AV1CodecError.showMessageDialog.text4") + "\n",
                            width, height
                    );

                    JOptionPane.showMessageDialog(
                            videoPlayer,
                            message,
                            I18N.get("videoPlayer.AV1CodecError.showMessageDialog.title"),
                            JOptionPane.WARNING_MESSAGE
                    );

                    // Reabilitar botão para carregar outro vídeo
                    videoPlayer.getOpenButton().setEnabled(true);
                    videoPlayer.getPlayPauseButton().setEnabled(false);
                    videoPlayer.getStopButton().setEnabled(false);
                    videoPlayer.getVolumeButton().setEnabled(false);
                    videoPlayer.setTitle("MediaPlayer");
                });

                return false; // Bloquear reprodução
            }

            // Se for AV1 mas com libdav1d, ou resolução baixa, permitir
            if (isAV1 && !isSlowDecoder) {
                System.out.println("✓ AV1 com decoder otimizado detectado - permitindo reprodução");
            } else if (isAV1 && !isHighRes) {
                System.out.println("✓ AV1 em resolução baixa (" + width + "x" + height + ") - permitindo reprodução");
            }

            return true; // Permitir reprodução

        } catch (Exception e) {
            System.err.println("Erro ao verificar codec/resolução: " + e.getMessage());
            e.printStackTrace();

            // Em caso de erro na verificação, permitir tentar reproduzir
            if (tempGrabber != null) {
                try {
                    tempGrabber.stop();
                    tempGrabber.release();
                } catch (Exception ex) {
                    // Ignorar erro ao fechar
                }
            }

            return true; // Permitir reprodução em caso de erro na verificação
        }
    }


    public void tryEnableHardwareAcceleration(FFmpegFrameGrabber grabber) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            // Primeiro, tentar hardware acceleration (mas sua GPU não suporta AV1)
            if (os.contains("win")) {
                try {
                    grabber.setVideoOption("hwaccel", "none");
                    System.out.println("Tentando aceleração GPU (auto)");
                } catch (Exception e) {
                    System.out.println("Hardware acceleration não disponível: " + e.getMessage());
                }
            }

            // CRÍTICO: Forçar uso do decoder dav1d para AV1 (muito mais rápido)
            // dav1d é 2-3x mais rápido que libaom-av1 decoder
            try {
                grabber.setVideoCodecName("libdav1d");
                System.out.println("Decoder dav1d configurado para AV1");
            } catch (Exception e) {
                System.out.println("Decoder dav1d não disponível, usando padrão");
            }

            // Threads: usar todos os cores mas limitar para não sobrecarregar
//        int threads = Math.min(Runtime.getRuntime().availableProcessors(), 8);
//        grabber.setVideoOption("threads", String.valueOf(threads));
//        System.out.println("Threads configuradas: " + threads);

        } catch (Exception e) {
            System.out.println("Erro ao configurar aceleração: " + e.getMessage());
        }
    }
}
