package com.esl.videoplayer.configuration;

import com.esl.videoplayer.audio.Spectrum.AudioSpectrumPanel;

import java.awt.*;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gerencia as configurações do aplicativo.
 * Salva e carrega preferências do usuário em arquivo .properties
 */
public class ConfigManager {

    private static final String CONFIG_FILE = "app_config.properties";
    private static final String APP_DIR_NAME = ".videoplayer";

    // Chaves de configuração disponíveis
    public static final String KEY_VOLUME        = "volume";
    public static final String KEY_MUTED         = "muted";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_SILENTCAPTURE = "silentcapture";
    public static final String KEY_SUBTITLE_SIZE     = "subtitle_size";     // tamanho em px
    public static final String KEY_SUBTITLE_COLOR    = "subtitle_color";    // RGB decimal (ex: "-1" = branco)
    public static final String KEY_FRAME_SKIP_VALUE    = "frame_skip_value";
    public static final String KEY_CAPTURE_FRAME_INTERVAL    = "capture_frame_interval";

    //Audio
    public static final String KEY_SHOW_AUDIO_SPECTRUM     = "show_spectrum";
    public static final String KEY_SHOW_AUDIO_IMAGE_COVER = "show_audio_image_cover";
    public static final String KEY_SPECTRUM_LAYOUT = "spectrum_layout";

    public static final String KEY_WAVEFORM_AMPLITUDE     = "waveform_amplitude";
    public static final String KEY_WAVEFORM_FILLED        = "waveform_filled";
    public static final String KEY_WAVEFORM_LAYERS        = "waveform_layers";
    public static final String KEY_WAVEFORM_SPACING       = "waveform_spacing";

    public static final String KEY_SPECTRUM_COLOR = "spectrum_color";
    public static final String KEY_SPECTRUM_CUSTOM_PALETTE = "spectrum_custom_palette";
    public static final String KEY_SPECTRUM_REFLECTION         = "spectrum_reflection";
    public static final String KEY_SPECTRUM_REFLECTION_HEIGHT  = "spectrum_reflection_height";
    public static final String KEY_SPECTRUM_REFLECTION_ALPHA   = "spectrum_reflection_alpha";

    public static final String KEY_AUDIO_NORMALIZATION         = "audio_normalization";
    public static final String KEY_AUDIO_TARGET_LOUDNESS       = "audio_target_loudness";
    public static final String KEY_AUDIO_GLOBAL_GAIN           = "audio_global_gain";

    public static final String KEY_PLAYLIST_FIRSTITEM_RANDOM           = "playlist_firstitem_random";
    public static final String KEY_PLAYLIST_AUTOPLAY           = "playlist_autoplay";
    public static final String KEY_PLAYLIST_SHUFFLE          = "playlist_shuffle";
    public static final String KEY_PLAYLIST_REPEAT           = "playlist_repeat";
    public static final String KEY_PLAYLIST_REPEAT_ONE       = "playlist_repeat_one";
    public static final String KEY_SAVE_RECENT_PLAYED_FILES  = "save_recent_played_files";


    // Valores padrão para cada chave
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(KEY_VOLUME, "100");
        DEFAULTS.put(KEY_MUTED,  "false");
        DEFAULTS.put(KEY_LANGUAGE, "en_US"); // padrão inglês
        DEFAULTS.put(KEY_SILENTCAPTURE, "false");
        DEFAULTS.put(KEY_SUBTITLE_SIZE,  "24");
        DEFAULTS.put(KEY_SUBTITLE_COLOR, String.valueOf(Color.WHITE.getRGB())); // -1 = branco opaco
        DEFAULTS.put(KEY_FRAME_SKIP_VALUE,  "1");
        DEFAULTS.put(KEY_CAPTURE_FRAME_INTERVAL,  "2");
        DEFAULTS.put(KEY_SAVE_RECENT_PLAYED_FILES,  "true");

        //Audio
        DEFAULTS.put(KEY_SHOW_AUDIO_SPECTRUM, "true");
        DEFAULTS.put(KEY_SHOW_AUDIO_IMAGE_COVER, "true");
        DEFAULTS.put(KEY_SPECTRUM_LAYOUT, "LINEAR");
        DEFAULTS.put(KEY_WAVEFORM_AMPLITUDE, "180");
        DEFAULTS.put(KEY_WAVEFORM_FILLED, "false");
        DEFAULTS.put(KEY_WAVEFORM_LAYERS, "5");
        DEFAULTS.put(KEY_WAVEFORM_SPACING, "0.8");
        DEFAULTS.put(KEY_SPECTRUM_COLOR, "DEFAULT");
        DEFAULTS.put(KEY_SPECTRUM_CUSTOM_PALETTE, "NONE");
        DEFAULTS.put(KEY_SPECTRUM_REFLECTION, "true");
        DEFAULTS.put(KEY_SPECTRUM_REFLECTION_HEIGHT, "0.5"); // Equivale a 50% (i == 1)
        DEFAULTS.put(KEY_SPECTRUM_REFLECTION_ALPHA, "128");  // Equivale a 50% / Opacidade 128 (i == 1)

        DEFAULTS.put(KEY_AUDIO_NORMALIZATION, "false");
        DEFAULTS.put(KEY_AUDIO_TARGET_LOUDNESS, "-18.0");    // Padrão do quietItem (-18.0f)
        DEFAULTS.put(KEY_AUDIO_GLOBAL_GAIN, "0.2");          // Padrão do primeiro item (20% -> 0.2f)

        //Playlist
        DEFAULTS.put(KEY_PLAYLIST_FIRSTITEM_RANDOM, "true");
        DEFAULTS.put(KEY_PLAYLIST_AUTOPLAY, "true");
        DEFAULTS.put(KEY_PLAYLIST_SHUFFLE, "false");
        DEFAULTS.put(KEY_PLAYLIST_REPEAT, "false");
        DEFAULTS.put(KEY_PLAYLIST_REPEAT_ONE, "false");

    }

    private final File configFile;
    private final Map<String, String> configs;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    public ConfigManager() {
        String userHome = System.getProperty("user.home");
        String appDir   = userHome + File.separator + APP_DIR_NAME;

        // Garante que o diretório da aplicação existe
        File appDirectory = new File(appDir);
        if (!appDirectory.exists()) {
            appDirectory.mkdirs();
        }

        this.configFile = new File(appDir, CONFIG_FILE);
        this.configs    = new LinkedHashMap<>(DEFAULTS); // começa com os defaults

        System.out.println("Arquivo de configuração: " + configFile.getAbsolutePath());

        // Cria o arquivo vazio se ainda não existir
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                System.out.println("Arquivo de configuração criado.");
            } catch (IOException e) {
                System.err.println("Erro ao criar arquivo de configuração: " + e.getMessage());
            }
        }

        loadConfigs();
    }

    // -------------------------------------------------------------------------
    // Leitura e escrita genéricas
    // -------------------------------------------------------------------------

    /**
     * Retorna o valor de uma configuração como String.
     * Se a chave não existir, retorna o valor padrão definido em DEFAULTS,
     * ou null caso não haja padrão.
     */
    public String get(String key) {
        return configs.getOrDefault(key, DEFAULTS.get(key));
    }

    /**
     * Define e persiste um valor de configuração.
     */
    public void set(String key, String value) {
        configs.put(key, value);
        saveConfigs();
        System.out.println("✓ Config salva: " + key + " = " + value);
    }

    // -------------------------------------------------------------------------
    // Helpers tipados — evitam conversões repetidas no código cliente
    // -------------------------------------------------------------------------

    /** Retorna configuração como int; usa defaultValue se conversão falhar. */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Define configuração a partir de um int. */
    public void setInt(String key, int value) {
        set(key, String.valueOf(value));
    }

    /** Retorna configuração como float; usa defaultValue se conversão falhar. */
    public float getFloat(String key, float defaultValue) {
        try {
            return Float.parseFloat(get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Define configuração a partir de um float. */
    public void setFloat(String key, float value) {
        set(key, String.valueOf(value));
    }

    /** Retorna configuração como boolean. */
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    /** Define configuração a partir de um boolean. */
    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para volume (usados diretamente pelo VideoPlayer)
    // -------------------------------------------------------------------------

    /**
     * Retorna o volume salvo (0–100). Padrão: 100.
     */
    public int getSavedVolume() {
        return getInt(KEY_VOLUME, 100);
    }

    /**
     * Salva o volume atual (0–100).
     */
    public void saveVolume(int volumePercent) {
        setInt(KEY_VOLUME, volumePercent);
    }

    /**
     * Retorna se o mute estava ativo. Padrão: false.
     */
    public boolean isSavedMuted() {
        return getBoolean(KEY_MUTED, false);
    }

    /**
     * Salva o estado de mute.
     */
    public void saveMuted(boolean muted) {
        setBoolean(KEY_MUTED, muted);
    }

    //Silent Capture frames
    /**
     * Retorna se a captura de frame sem mensagem está ativo
     */
    public boolean isSilentCapture() {
        return getBoolean(KEY_SILENTCAPTURE, false);
    }

    /**
     * Salva o estado de mute.
     */
    public void saveSilentCapture(boolean silentCapture) {
        setBoolean(KEY_SILENTCAPTURE, silentCapture);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para idioma (usados pelo I18N / MainPanel)
    // -------------------------------------------------------------------------

    /**
     * Retorna o Locale salvo. Padrão: en_US.
     * Suporta os formatos "en_US" e "en-US".
     */
    public Locale getSavedLocale() {
        String tag = get(KEY_LANGUAGE); // ex: "en_US"
        if (tag == null || tag.isBlank()) return Locale.of("en", "US");

        // Aceita tanto underscore ("en_US") quanto hífen ("en-US")
        String[] parts = tag.replace('-', '_').split("_", 2);
        if (parts.length == 2) {
            return Locale.of(parts[0], parts[1]);
        }
        return Locale.of(parts[0]);
    }

    /**
     * Salva o Locale atual no formato "language_COUNTRY" (ex: "pt_BR").
     */
    public void saveLocale(Locale locale) {
        String tag = locale.getLanguage();
        if (!locale.getCountry().isBlank()) {
            tag += "_" + locale.getCountry();
        }
        set(KEY_LANGUAGE, tag);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para legenda (usados pelo SubtitleManager / MainPanel)
    // -------------------------------------------------------------------------

    /**
     * Retorna o tamanho de fonte salvo para legendas. Padrão: 24px.
     */
    public int getSavedSubtitleSize() {
        return getInt(KEY_SUBTITLE_SIZE, 24);
    }

    /**
     * Salva o tamanho de fonte das legendas.
     */
    public void saveSubtitleSize(int size) {
        setInt(KEY_SUBTITLE_SIZE, size);
    }

    /**
     * Retorna a cor salva para legendas.
     * A cor é armazenada como int RGB (mesmo valor de Color.getRGB()).
     * Padrão: Color.WHITE.
     */
    public Color getSavedSubtitleColor() {
        // Usa getOrDefault direto para não confundir com null do helper get()
        String raw = get(KEY_SUBTITLE_COLOR);
        if (raw == null || raw.isBlank()) return Color.WHITE;
        try {
            return new Color(Integer.parseInt(raw), true); // true = inclui canal alpha
        } catch (NumberFormatException e) {
            System.err.println("Cor de legenda inválida no arquivo, usando padrão branco.");
            return Color.WHITE;
        }
    }

    /**
     * Salva a cor de legenda como int RGB.
     * Exemplo: Color.WHITE.getRGB() → "-1"
     */
    public void saveSubtitleColor(Color color) {
        set(KEY_SUBTITLE_COLOR, String.valueOf(color.getRGB()));
    }


// -------------------------------------------------------------------------
    // Atalhos específicos para configuração de frames por avanço
    // -------------------------------------------------------------------------

    /**
     * Retorna o valor padrão. Padrão: 1.
     */
    public int getSavedFrameSkipValue() {
        return getInt(KEY_FRAME_SKIP_VALUE, 1);
    }

    /**
     * Salva o volume atual (1, 2, 3, 5, 10, 15, 30).
     */
    public void saveFrameSkipValue(int skipValue) {
        setInt(KEY_FRAME_SKIP_VALUE, skipValue);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para configuração de intervalo de captura dos frames do video
    // -------------------------------------------------------------------------

    /**
     * Retorna o valor padrão. Padrão: 2.
     */
    public int getSavedCaptureFrameInterval() {
        return getInt(KEY_CAPTURE_FRAME_INTERVAL, 2);
    }

    /**
     * Salva o volume atual (1, 2, 3, 5, 10, 15, 30).
     */
    public void saveCaptureFrameInterval(int frameInterval) {
        setInt(KEY_CAPTURE_FRAME_INTERVAL, frameInterval);
    }

    public boolean isSavedRecentItemPlayed() {
        return getBoolean(KEY_SAVE_RECENT_PLAYED_FILES, true);
    }
    public void savedRecentItemPlayed(boolean savedRecentItemPlayed) {
        setBoolean(KEY_SAVE_RECENT_PLAYED_FILES, savedRecentItemPlayed);
    }
    // -------------------------------------------------------------------------
    // Opçóes de Áudio
    // -------------------------------------------------------------------------

    public boolean getSavedShowAudioSpectrum() {return getBoolean(KEY_SHOW_AUDIO_SPECTRUM, true);}
    public void saveShowAudioSpectrum(boolean spectrum) {setBoolean(KEY_SHOW_AUDIO_SPECTRUM, spectrum);}

    public boolean getSavedShowAudioImageCover() {return getBoolean(KEY_SHOW_AUDIO_IMAGE_COVER, true);}
    public void saveShowAudioImageCover(boolean cover) {setBoolean(KEY_SHOW_AUDIO_IMAGE_COVER, cover);}

    public AudioSpectrumPanel.LayoutMode getSavedSpectrumLayout() {
        String layoutStr = get(KEY_SPECTRUM_LAYOUT);
        try {
            return AudioSpectrumPanel.LayoutMode.valueOf(layoutStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Caso o arquivo tenha um valor inválido, retorna o padrão
            return AudioSpectrumPanel.LayoutMode.LINEAR;
        }
    }
    public void saveSpectrumLayout(AudioSpectrumPanel.LayoutMode layoutMode) {
        set(KEY_SPECTRUM_LAYOUT, layoutMode.name());
    }

    public int getSavedWaveformAmplitude() { return getInt(KEY_WAVEFORM_AMPLITUDE, 180); }
    public void saveWaveformAmplitude(int amplitude) { setInt(KEY_WAVEFORM_AMPLITUDE, amplitude); }

    public boolean getSavedWaveformFilled() { return getBoolean(KEY_WAVEFORM_FILLED, false); }
    public void saveWaveformFilled(boolean filled) { setBoolean(KEY_WAVEFORM_FILLED, filled); }

    public int getSavedWaveformLayers() { return getInt(KEY_WAVEFORM_LAYERS, 5); }
    public void saveWaveformLayers(int layers) { setInt(KEY_WAVEFORM_LAYERS, layers); }

    public float getSavedWaveformSpacing() { return getFloat(KEY_WAVEFORM_SPACING, 0.8f); }
    public void saveWaveformSpacing(float spacing) { setFloat(KEY_WAVEFORM_SPACING, spacing); }


    public AudioSpectrumPanel.ColorMode getSavedSpectrumColor() {
        String colorStr = get(KEY_SPECTRUM_COLOR);
        try {
            return AudioSpectrumPanel.ColorMode.valueOf(colorStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Caso o arquivo tenha um valor inválido, retorna o padrão
            return AudioSpectrumPanel.ColorMode.DEFAULT;
        }
    }
    public void saveSpectrumColor(AudioSpectrumPanel.ColorMode colorMode) {
        set(KEY_SPECTRUM_COLOR, colorMode.name());
    }

    public AudioSpectrumPanel.CustomPalette getSavedSpectrumCustomPalette() {
        String paletteStr = get(KEY_SPECTRUM_CUSTOM_PALETTE);
        try {
            return AudioSpectrumPanel.CustomPalette.valueOf(paletteStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            return AudioSpectrumPanel.CustomPalette.NONE;
        }
    }
    public void saveSpectrumCustomPalette(AudioSpectrumPanel.CustomPalette palette) {
        set(KEY_SPECTRUM_CUSTOM_PALETTE, palette.name());
    }

    public boolean getSavedSpectrumReflection() { return getBoolean(KEY_SPECTRUM_REFLECTION, true); }
    public void saveSpectrumReflection(boolean enabled) { setBoolean(KEY_SPECTRUM_REFLECTION, enabled); }

    public float getSavedSpectrumReflectionHeight() { return getFloat(KEY_SPECTRUM_REFLECTION_HEIGHT, 0.5f); }
    public void saveSpectrumReflectionHeight(float height) { setFloat(KEY_SPECTRUM_REFLECTION_HEIGHT, height); }

    public int getSavedSpectrumReflectionAlpha() { return getInt(KEY_SPECTRUM_REFLECTION_ALPHA, 128); }
    public void saveSpectrumReflectionAlpha(int alpha) { setInt(KEY_SPECTRUM_REFLECTION_ALPHA, alpha); }

    public boolean getSavedAudioNormalization() { return getBoolean(KEY_AUDIO_NORMALIZATION, false); }
    public void saveAudioNormalization(boolean enabled) { setBoolean(KEY_AUDIO_NORMALIZATION, enabled); }

    public float getSavedAudioTargetLoudness() { return getFloat(KEY_AUDIO_TARGET_LOUDNESS, -18.0f); }
    public void saveAudioTargetLoudness(float loudness) { setFloat(KEY_AUDIO_TARGET_LOUDNESS, loudness); }

    public float getSavedAudioGlobalGain() { return getFloat(KEY_AUDIO_GLOBAL_GAIN, 0.2f); }
    public void saveAudioGlobalGain(float gain) { setFloat(KEY_AUDIO_GLOBAL_GAIN, gain); }

    //Playlist
    public boolean isSavedPlaylistRandom() {
        return getBoolean(KEY_PLAYLIST_FIRSTITEM_RANDOM, true);
    }
    public void savedPlaylistRandom(boolean random) {
        setBoolean(KEY_PLAYLIST_FIRSTITEM_RANDOM, random);
    }
    public boolean isSavedPlaylistAutoPlay() {
        return getBoolean(KEY_PLAYLIST_AUTOPLAY, true);
    }
    public void savedPlaylistAutoPlay(boolean autoPlay) {
        setBoolean(KEY_PLAYLIST_AUTOPLAY, autoPlay);
    }

    public boolean isSavedPlaylistShuffle() {
        return getBoolean(KEY_PLAYLIST_SHUFFLE, false);
    }
    public void savedPlaylistShuffle(boolean shuffle) {
        setBoolean(KEY_PLAYLIST_SHUFFLE, shuffle);
    }

    public boolean isSavedPlaylistRepeat() {
        return getBoolean(KEY_PLAYLIST_REPEAT, false);
    }
    public void savedPlaylistRepeat(boolean repeat) {
        setBoolean(KEY_PLAYLIST_REPEAT, repeat);
    }

    public boolean isSavedPlaylistRepeatOne() {
        return getBoolean(KEY_PLAYLIST_REPEAT_ONE, false);
    }
    public void savedPlaylistRepeatOne(boolean repeatOne) {
        setBoolean(KEY_PLAYLIST_REPEAT_ONE, repeatOne);
    }

    // -------------------------------------------------------------------------
    // Persistência interna — formato "chave=valor", uma por linha
    // -------------------------------------------------------------------------

    /**
     * Carrega todas as configurações do arquivo.
     * Linhas em branco e comentários (iniciados com '#') são ignorados.
     * Chaves ausentes no arquivo mantêm o valor padrão de DEFAULTS.
     */
    private void loadConfigs() {
        if (!configFile.exists() || configFile.length() == 0) {
            System.out.println("Nenhuma configuração salva. Usando valores padrão.");
            saveConfigs(); // persiste os defaults
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int separatorIdx = line.indexOf('=');
                if (separatorIdx < 1) continue; // linha malformada

                String key   = line.substring(0, separatorIdx).trim();
                String value = line.substring(separatorIdx + 1).trim();
                configs.put(key, value);
            }
            System.out.println("✓ Configurações carregadas: " + configs.size() + " chave(s).");
        } catch (IOException e) {
            System.err.println("Erro ao carregar configurações: " + e.getMessage());
        }
    }

    /**
     * Salva todas as configurações no arquivo.
     */
    private void saveConfigs() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# Configurações do VideoPlayer");
            writer.newLine();
            for (Map.Entry<String, String> entry : configs.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            System.err.println("Erro ao salvar configurações: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /** Imprime todas as configurações atuais no console. */
    public void printDebugInfo() {
        System.out.println("========== DEBUG CONFIG MANAGER ==========");
        System.out.println("Arquivo : " + configFile.getAbsolutePath());
        System.out.println("Existe  : " + configFile.exists());
        System.out.println("Entradas: " + configs.size());
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
        System.out.println("==========================================");
    }
}