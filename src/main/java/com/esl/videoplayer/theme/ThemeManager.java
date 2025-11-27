package com.esl.videoplayer.theme;


import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Gerencia os temas (skins) do aplicativo
 * Salva a escolha do usuário em arquivo .txt
 */
public class ThemeManager {

    private static final String THEME_FILE = "theme_config.txt";
    private static final String DEFAULT_THEME = "FlatArcOrangeIJTheme";

    private final File themeConfigFile;
    private String currentTheme;
    private final Map<String, ThemeInfo> availableThemes;

    /**
     * Classe interna para informações do tema
     */
    public static class ThemeInfo {
        private final String className;
        private final String displayName;
        private final Class<? extends FlatLaf> themeClass;

        public ThemeInfo(String className, String displayName, Class<? extends FlatLaf> themeClass) {
            this.className = className;
            this.displayName = displayName;
            this.themeClass = themeClass;
        }

        public String getClassName() {
            return className;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Class<? extends FlatLaf> getThemeClass() {
            return themeClass;
        }
    }

    public ThemeManager() {
        // Criar arquivo no diretório do usuário
        String userHome = System.getProperty("user.home");
        String appDir = userHome + File.separator + ".videoplayer";

        // Criar diretório se não existir
        File appDirectory = new File(appDir);
        if (!appDirectory.exists()) {
            appDirectory.mkdirs();
        }

        this.themeConfigFile = new File(appDir, THEME_FILE);
        this.availableThemes = new LinkedHashMap<>();

        // Registrar temas disponíveis
        registerThemes();

        System.out.println("Arquivo de tema: " + themeConfigFile.getAbsolutePath());

        // Criar arquivo vazio se não existir
        if (!themeConfigFile.exists()) {
            try {
                themeConfigFile.createNewFile();
                System.out.println("Arquivo de tema criado");
            } catch (IOException e) {
                System.err.println("Erro ao criar arquivo de tema: " + e.getMessage());
            }
        }

        // Carregar tema salvo ou usar default
        loadTheme();
    }

    /**
     * Registra os temas disponíveis
     */
    private void registerThemes() {
        // Adicionar temas existentes
        availableThemes.put("FlatArcOrangeIJTheme",
                new ThemeInfo("FlatArcOrangeIJTheme", "🔶 Arc Orange (Claro)", FlatArcOrangeIJTheme.class));

        availableThemes.put("FlatArcDarkOrangeIJTheme",
                new ThemeInfo("FlatArcDarkOrangeIJTheme", "🔸 Arc Dark Orange (Escuro)", FlatArcDarkOrangeIJTheme.class));

        availableThemes.put("FlatDraculaIJTheme",
                new ThemeInfo("FlatDraculaIJTheme", "🔸 Dracula (Escuro)", FlatDraculaIJTheme.class));

        // Adicionar mais temas aqui no futuro
        // availableThemes.put("NovoTema", new ThemeInfo("NovoTema", "📱 Novo Tema", NovoTema.class));

        System.out.println("Temas registrados: " + availableThemes.size());
    }

    /**
     * Carrega o tema salvo do arquivo
     */
    private void loadTheme() {
        if (!themeConfigFile.exists() || themeConfigFile.length() == 0) {
            System.out.println("Nenhum tema salvo. Usando tema padrão: " + DEFAULT_THEME);
            currentTheme = DEFAULT_THEME;
            saveTheme(); // Salvar o tema padrão
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(themeConfigFile))) {
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String loadedTheme = line.trim();

                // Verificar se o tema existe
                if (availableThemes.containsKey(loadedTheme)) {
                    currentTheme = loadedTheme;
                    System.out.println("✓ Tema carregado: " + currentTheme);
                } else {
                    System.out.println("⚠ Tema '" + loadedTheme + "' não encontrado. Usando padrão.");
                    currentTheme = DEFAULT_THEME;
                    saveTheme(); // Corrigir arquivo
                }
            } else {
                currentTheme = DEFAULT_THEME;
                saveTheme();
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar tema: " + e.getMessage());
            currentTheme = DEFAULT_THEME;
        }
    }

    /**
     * Salva o tema atual no arquivo
     */
    private void saveTheme() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(themeConfigFile))) {
            writer.write(currentTheme);
            writer.flush();
            System.out.println("✓ Tema salvo: " + currentTheme);
        } catch (IOException e) {
            System.err.println("Erro ao salvar tema: " + e.getMessage());
        }
    }

    /**
     * Obtém o tema atual
     */
    public String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Obtém informações do tema atual
     */
    public ThemeInfo getCurrentThemeInfo() {
        return availableThemes.get(currentTheme);
    }

    /**
     * Define um novo tema e salva
     */
    public void setTheme(String themeName) {
        if (!availableThemes.containsKey(themeName)) {
            System.err.println("Tema não existe: " + themeName);
            return;
        }

        this.currentTheme = themeName;
        saveTheme();
        System.out.println("Tema alterado para: " + themeName);
    }

    /**
     * Aplica o tema atual ao aplicativo
     */
    public boolean applyCurrentTheme() {
        System.out.println("Aplicando tema atual: " + currentTheme);
        return applyTheme(currentTheme);
    }

    /**
     * Aplica um tema específico
     */
    public boolean applyTheme(String themeName) {
        ThemeInfo themeInfo = availableThemes.get(themeName);
        if (themeInfo == null) {
            System.err.println("Tema não encontrado: " + themeName);
            return false;
        }

        try {
            System.out.println("Tentando aplicar tema: " + themeInfo.getDisplayName());
            System.out.println("Classe do tema: " + themeInfo.getThemeClass().getName());

            FlatLaf theme = themeInfo.getThemeClass().getDeclaredConstructor().newInstance();

            System.out.println("Instância do tema criada: " + theme);
            System.out.println("Aplicando com UIManager.setLookAndFeel...");

            UIManager.setLookAndFeel(theme);

            System.out.println("✓ Tema aplicado com sucesso!");
            System.out.println("Look and Feel atual: " + UIManager.getLookAndFeel().getName());

            return true;
        } catch (Exception e) {
            System.err.println("✗ Erro ao aplicar tema: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Altera o tema e atualiza a interface (versão silenciosa)
     */
    public void changeThemeSilent(String themeName, JFrame frame) {
        if (!availableThemes.containsKey(themeName)) {
            System.err.println("Tema não existe: " + themeName);
            return;
        }

        System.out.println("\n=== INICIANDO TROCA DE TEMA ===");
        System.out.println("Tema anterior: " + currentTheme);
        System.out.println("Novo tema: " + themeName);

        // Salvar novo tema
        setTheme(themeName);

        // Guardar estado atual da janela
        Dimension currentSize = frame.getSize();
        Point currentLocation = frame.getLocation();
        boolean wasMaximized = (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;

        System.out.println("Estado da janela salvo - Tamanho: " + currentSize + ", Posição: " + currentLocation);

        // Aplicar tema
        if (applyTheme(themeName)) {
            System.out.println("Tema aplicado com sucesso, atualizando UI...");



            // Atualizar TODAS as janelas e componentes
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }

            // Especificamente o frame principal
            SwingUtilities.updateComponentTreeUI(frame);

            System.out.println("UI atualizada com updateComponentTreeUI");

            // Restaurar estado
            if (!wasMaximized) {
                frame.setSize(currentSize);
                frame.setLocation(currentLocation);
            } else {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }

            System.out.println("Estado da janela restaurado");

            // Forçar repaint
            frame.invalidate();
            frame.revalidate();
            frame.repaint();

            System.out.println("✓ Tema alterado com sucesso!");
            System.out.println("=== TROCA DE TEMA CONCLUÍDA ===\n");
        } else {
            System.err.println("✗ Falha ao aplicar tema!");
            System.out.println("=== TROCA DE TEMA FALHOU ===\n");
        }
    }

    /**
     * Altera o tema e atualiza a interface
     */
    public void changeTheme(String themeName, JFrame frame) {
        if (!availableThemes.containsKey(themeName)) {
            System.err.println("Tema não existe: " + themeName);
            return;
        }

        // Salvar novo tema
        setTheme(themeName);

        // Guardar tamanho e posição atuais da janela
        Dimension currentSize = frame.getSize();
        Point currentLocation = frame.getLocation();
        boolean wasMaximized = (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;

        // Aplicar tema
        if (applyTheme(themeName)) {
            // Atualizar todas as janelas abertas
            SwingUtilities.updateComponentTreeUI(frame);

            // Restaurar tamanho e posição
            if (!wasMaximized) {
                frame.setSize(currentSize);
                frame.setLocation(currentLocation);
            } else {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }

            // Forçar repaint de todos os componentes
            frame.revalidate();
            frame.repaint();

            System.out.println("✓ Tema alterado com sucesso!");

            // Mostrar mensagem de sucesso
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame,
                        "Tema alterado para: " + availableThemes.get(themeName).getDisplayName(),
                        "Tema Aplicado",
                        JOptionPane.INFORMATION_MESSAGE);
            });
        } else {
            JOptionPane.showMessageDialog(frame,
                    "Erro ao aplicar o tema.",
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Obtém lista de todos os temas disponíveis
     */
    public Map<String, ThemeInfo> getAvailableThemes() {
        return new LinkedHashMap<>(availableThemes);
    }

    /**
     * Obtém lista de nomes dos temas
     */
    public List<String> getThemeNames() {
        return new ArrayList<>(availableThemes.keySet());
    }

    /**
     * Verifica se um tema existe
     */
    public boolean themeExists(String themeName) {
        return availableThemes.containsKey(themeName);
    }

    /**
     * Adiciona um novo tema dinamicamente
     */
    public void registerNewTheme(String className, String displayName, Class<? extends FlatLaf> themeClass) {
        availableThemes.put(className, new ThemeInfo(className, displayName, themeClass));
        System.out.println("Novo tema registrado: " + displayName);
    }

    /**
     * Método de debug
     */
    public void printDebugInfo() {
        System.out.println("========== DEBUG THEME MANAGER ==========");
        System.out.println("Arquivo: " + themeConfigFile.getAbsolutePath());
        System.out.println("Existe: " + themeConfigFile.exists());
        System.out.println("Tema atual: " + currentTheme);
        System.out.println("Temas disponíveis: " + availableThemes.size());

        System.out.println("\nLista de temas:");
        for (Map.Entry<String, ThemeInfo> entry : availableThemes.entrySet()) {
            String marker = entry.getKey().equals(currentTheme) ? "→ " : "  ";
            System.out.println(marker + entry.getValue().getDisplayName() + " (" + entry.getKey() + ")");
        }
        System.out.println("=========================================");
    }
}