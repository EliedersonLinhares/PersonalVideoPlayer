package com.esl.videoplayer.configuration;

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

    // Valores padrão para cada chave
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(KEY_VOLUME, "100");
        DEFAULTS.put(KEY_MUTED,  "false");
        DEFAULTS.put(KEY_LANGUAGE, "en_US"); // padrão inglês
        DEFAULTS.put(KEY_SILENTCAPTURE, "false");
        DEFAULTS.put(KEY_SUBTITLE_SIZE,  "24");
        DEFAULTS.put(KEY_SUBTITLE_COLOR, String.valueOf(Color.WHITE.getRGB())); // -1 = branco opaco

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
    // Atalhos específicos para idioma (usados pelo I18N / VideoPanel)
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
    // Atalhos específicos para legenda (usados pelo SubtitleManager / VideoPanel)
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