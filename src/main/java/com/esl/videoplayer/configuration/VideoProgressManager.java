package com.esl.videoplayer.configuration;

import com.esl.videoplayer.localization.I18N;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Salva e recupera o progresso de reprodução de vídeos.
 *
 * Cada vídeo é identificado pelo seu caminho absoluto.
 * O progresso é armazenado como número do frame atual em um arquivo
 * dedicado (~/.videoplayer/video_progress.properties), separado das
 * preferências gerais do ConfigManager.
 *
 * Formato do arquivo:
 *   # Progresso de vídeos
 *   /home/user/filmes/filme.mp4=18450
 *   C:\Videos\serie.mkv=72300
 *
 * Uso típico no VideoPlayer:
 *   1. Ao abrir um vídeo  → videoProgressManager.checkAndPromptResume(...)
 *   2. Durante reprodução → videoProgressManager.saveProgress(path, currentFrame)
 *   3. Ao parar/fechar   → videoProgressManager.saveProgress(path, currentFrame)
 */
public class VideoProgressManager implements I18N.LanguageChangeListener {

    private static final String PROGRESS_FILE = "video_progress.properties";
    private static final String APP_DIR_NAME  = ".videoplayer";

    /**
     * Frame mínimo para considerar que vale a pena perguntar se quer continuar.
     * Evita o diálogo quando o usuário assistiu pouquíssimos segundos.
     * Ajuste conforme necessário (padrão: 150 frames ≈ 5 s a 30 fps).
     */
    private static final long MIN_FRAMES_TO_SAVE = 150;

    /**
     * Percentual do total de frames a partir do qual o vídeo é considerado
     * "concluído" e o progresso é removido automaticamente (padrão: 95 %).
     */
    private static final double COMPLETION_THRESHOLD = 0.95;

    private final File progressFile;

    // Chave: caminho absoluto do arquivo  |  Valor: frame salvo como String
    private final Map<String, String> progressMap;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    public VideoProgressManager() {
        String userHome = System.getProperty("user.home");
        String appDir   = userHome + File.separator + APP_DIR_NAME;

        File appDirectory = new File(appDir);
        if (!appDirectory.exists()) {
            appDirectory.mkdirs();
        }

        this.progressFile = new File(appDir, PROGRESS_FILE);
        this.progressMap  = new LinkedHashMap<>();

        if (!progressFile.exists()) {
            try {
                progressFile.createNewFile();
                System.out.println("Arquivo de progresso criado: " + progressFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Erro ao criar arquivo de progresso: " + e.getMessage());
            }
        }

        loadProgress();
    }

    // -------------------------------------------------------------------------
    // API pública principal
    // -------------------------------------------------------------------------

    /**
     * Verifica se há progresso salvo para o vídeo e, se houver, exibe um
     * diálogo perguntando ao usuário se deseja continuar de onde parou.
     *
     * Deve ser chamado logo após o vídeo terminar de carregar, antes de
     * iniciar a reprodução.
     *
     * @param videoPath  Caminho absoluto do vídeo.
     * @param totalFrames Total de frames do vídeo (necessário para seek).
     * @return  O frame a partir do qual reproduzir:
     *          - o frame salvo, se o usuário escolher continuar;
     *          - 0, se o usuário escolher recomeçar ou não houver progresso.
     */
    public long checkAndPromptResume(String videoPath, long totalFrames) {
        long savedFrame = getSavedFrame(videoPath);

        if (savedFrame <= MIN_FRAMES_TO_SAVE) {
            return 0; // Nada relevante salvo
        }

        // Calcular tempo formatado para exibir no diálogo
        String timeFormatted = formatFrameAsTime(savedFrame, 30.0); // usa 30 fps como estimativa inicial

        String message = String.format(
                "<html>"+ I18N.get("videoProgressManager.OptionDialog.text1") + " <b>%s</b> " + I18N.get("videoProgressManager.OptionDialog.text2") + "<br>" +
                        I18N.get("videoProgressManager.OptionDialog.text3") + "</html>",
                timeFormatted
        );

        int choice = javax.swing.JOptionPane.showOptionDialog(
                null,
                message,
                I18N.get("videoProgressManager.OptionDialog.title"),
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{I18N.get("videoProgressManager.OptionDialog.value1"), I18N.get("videoProgressManager.OptionDialog.value2")},
                I18N.get("videoProgressManager.OptionDialog.value1")
        );

        if (choice == javax.swing.JOptionPane.YES_OPTION) {
            System.out.println("✓ Usuário escolheu continuar no frame: " + savedFrame);
            return savedFrame;
        } else {
            // Usuário quer recomeçar: apagar o progresso salvo
            clearProgress(videoPath);
            System.out.println("Usuário escolheu recomeçar. Progresso apagado.");
            return 0;
        }
    }

    /**
     * Salva o frame atual como progresso do vídeo.
     *
     * Deve ser chamado:
     *  - Periodicamente durante a reprodução (ex.: a cada 30 s ou ao pausar).
     *  - Ao parar o vídeo (stopVideo).
     *  - Ao fechar a janela (dispose / windowClosing).
     *
     * Se o vídeo estiver concluído (≥ COMPLETION_THRESHOLD do total),
     * o progresso é removido automaticamente.
     *
     * @param videoPath    Caminho absoluto do vídeo.
     * @param currentFrame Frame atual da reprodução.
     * @param totalFrames  Total de frames do vídeo.
     */
    public void saveProgress(String videoPath, long currentFrame, long totalFrames) {
        if (videoPath == null || videoPath.isBlank()) return;
        if (currentFrame < MIN_FRAMES_TO_SAVE)        return;

        // Se o vídeo foi praticamente concluído, remover o progresso
        if (totalFrames > 0 && (double) currentFrame / totalFrames >= COMPLETION_THRESHOLD) {
            clearProgress(videoPath);
            System.out.println("Vídeo concluído. Progresso removido: " + videoPath);
            return;
        }

        progressMap.put(videoPath, String.valueOf(currentFrame));
        persistProgress();
        System.out.println("✓ Progresso salvo: frame " + currentFrame + " → " + new File(videoPath).getName());
    }

    /**
     * Remove o progresso salvo para o vídeo indicado.
     */
    public void clearProgress(String videoPath) {
        if (progressMap.remove(videoPath) != null) {
            persistProgress();
        }
    }

    /**
     * Retorna o frame salvo para o vídeo, ou 0 se não houver registro.
     */
    public long getSavedFrame(String videoPath) {
        if (videoPath == null) return 0;
        String raw = progressMap.get(videoPath);
        if (raw == null) return 0;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Verifica se há progresso salvo relevante para o vídeo.
     */
    public boolean hasProgress(String videoPath) {
        return getSavedFrame(videoPath) > MIN_FRAMES_TO_SAVE;
    }

    // -------------------------------------------------------------------------
    // Utilitário interno
    // -------------------------------------------------------------------------

    /**
     * Converte um número de frame em string de tempo "HH:mm:ss" ou "mm:ss".
     */
    private String formatFrameAsTime(long frame, double fps) {
        if (fps <= 0) fps = 30.0;
        long totalSeconds = (long) (frame / fps);
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    // -------------------------------------------------------------------------
    // Persistência interna
    // -------------------------------------------------------------------------

    private void loadProgress() {
        if (!progressFile.exists() || progressFile.length() == 0) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(progressFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Separador é o ÚLTIMO '=' para suportar caminhos Windows com '='
                int idx = line.lastIndexOf('=');
                if (idx < 1) continue;

                String path  = line.substring(0, idx).trim();
                String frame = line.substring(idx + 1).trim();
                progressMap.put(path, frame);
            }
            System.out.println("✓ Progresso carregado: " + progressMap.size() + " vídeo(s).");
        } catch (IOException e) {
            System.err.println("Erro ao carregar progresso: " + e.getMessage());
        }
    }

    private void persistProgress() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(progressFile))) {
            writer.write("# Progresso de reprodução de vídeos");
            writer.newLine();
            for (Map.Entry<String, String> entry : progressMap.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            System.err.println("Erro ao salvar progresso: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    public void printDebugInfo() {
        System.out.println("========== DEBUG VIDEO PROGRESS ==========");
        System.out.println("Arquivo : " + progressFile.getAbsolutePath());
        System.out.println("Vídeos  : " + progressMap.size());
        for (Map.Entry<String, String> entry : progressMap.entrySet()) {
            System.out.println("  frame " + entry.getValue() + " → " + entry.getKey());
        }
        System.out.println("==========================================");
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {

    }
}