package com.esl.videoplayer.configuration;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class ConfigurationFrame extends JDialog {
    private final String[] AUDIO_FORMATS = {".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac"};
    private final String[] VIDEO_FORMATS = {".mp4", ".avi", ".mkv", ".mov", ".flv", ".webm", ".gif", ".wmv", ".3gp"};

    private Map<String, JCheckBox> audioCheckboxes;
    private Map<String, JCheckBox> videoCheckboxes;
    private JCheckBox selectAllAudioCheckbox;
    private JCheckBox selectAllVideoCheckbox;

    public ConfigurationFrame() {
        setTitle("Configurações");
        setSize(600, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initComponents();
    }

    private void initComponents() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // Aba de Associação de Arquivos
        JPanel fileAssociationPanel = createFileAssociationPanel();
        tabbedPane.addTab("Associação de Arquivos", fileAssociationPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createFileAssociationPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Painel de conteúdo com scroll
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Título
        JLabel titleLabel = new JLabel("Associar arquivos de mídia ao programa");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(15));

        // Painel de Áudio
        JPanel audioPanel = createFormatPanel("Formatos de Áudio", AUDIO_FORMATS, true);
        audioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(audioPanel);
        contentPanel.add(Box.createVerticalStrut(15));

        // Painel de Vídeo
        JPanel videoPanel = createFormatPanel("Formatos de Vídeo", VIDEO_FORMATS, false);
        videoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(videoPanel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Painel de botões
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));

        JButton applyButton = new JButton("Aplicar");
        applyButton.setPreferredSize(new Dimension(100, 30));
        applyButton.addActionListener(e -> applyFileAssociations());

        JButton cancelButton = new JButton("Cancelar");
        cancelButton.setPreferredSize(new Dimension(100, 30));
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(cancelButton);
        buttonPanel.add(applyButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    private JPanel createFormatPanel(String title, String[] formats, boolean isAudio) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title));

        // Checkbox "Selecionar Todos"
        JCheckBox selectAllCheckbox = new JCheckBox("Selecionar Todos");
        selectAllCheckbox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        selectAllCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (isAudio) {
            selectAllAudioCheckbox = selectAllCheckbox;
            audioCheckboxes = new LinkedHashMap<>();
        } else {
            selectAllVideoCheckbox = selectAllCheckbox;
            videoCheckboxes = new LinkedHashMap<>();
        }

        panel.add(selectAllCheckbox);
        panel.add(Box.createVerticalStrut(8));

        // Painel com grid para os formatos
        JPanel formatsPanel = new JPanel(new GridLayout(0, 3, 10, 5));
        formatsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (String format : formats) {
            JCheckBox checkbox = new JCheckBox(format);
            checkbox.addActionListener(e -> updateSelectAllCheckbox(isAudio));
            formatsPanel.add(checkbox);

            if (isAudio) {
                audioCheckboxes.put(format, checkbox);
            } else {
                videoCheckboxes.put(format, checkbox);
            }
        }

        panel.add(formatsPanel);

        // Configurar ação do "Selecionar Todos"
        selectAllCheckbox.addActionListener(e -> {
            boolean selected = selectAllCheckbox.isSelected();
            Map<String, JCheckBox> checkboxMap = isAudio ? audioCheckboxes : videoCheckboxes;
            checkboxMap.values().forEach(cb -> cb.setSelected(selected));
        });

        return panel;
    }

    private void updateSelectAllCheckbox(boolean isAudio) {
        Map<String, JCheckBox> checkboxMap = isAudio ? audioCheckboxes : videoCheckboxes;
        JCheckBox selectAllCheckbox = isAudio ? selectAllAudioCheckbox : selectAllVideoCheckbox;

        boolean allSelected = checkboxMap.values().stream().allMatch(JCheckBox::isSelected);
        selectAllCheckbox.setSelected(allSelected);
    }

    private void applyFileAssociations() {
        // Verificar se está no Windows
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            JOptionPane.showMessageDialog(this,
                    "Esta funcionalidade está disponível apenas para Windows.",
                    "Sistema não suportado",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> selectedFormats = new ArrayList<>();

        // Coletar formatos selecionados
        audioCheckboxes.forEach((format, checkbox) -> {
            if (checkbox.isSelected()) {
                selectedFormats.add(format);
            }
        });

        videoCheckboxes.forEach((format, checkbox) -> {
            if (checkbox.isSelected()) {
                selectedFormats.add(format);
            }
        });

        if (selectedFormats.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Selecione pelo menos um formato para associar.",
                    "Nenhum formato selecionado",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Confirmar ação
        int confirm = JOptionPane.showConfirmDialog(this,
                "Associar " + selectedFormats.size() + " formato(s) ao programa?\n" +
                        "Esta ação requer privilégios de administrador.",
                "Confirmar associação",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            associateFiles(selectedFormats);
        }
    }

    private void associateFiles(List<String> formats) {
        try {
            // Obter caminho do executável atual
            String appPath = getApplicationPath();

            if (appPath == null) {
                JOptionPane.showMessageDialog(this,
                        "Não foi possível determinar o caminho do aplicativo.",
                        "Erro",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Criar arquivo .reg temporário
            File regFile = createRegistryFile(appPath, formats);

            // Executar o arquivo .reg
            ProcessBuilder pb = new ProcessBuilder("reg", "import", regFile.getAbsolutePath());
            Process process = pb.start();
            int exitCode = process.waitFor();

            // Deletar arquivo temporário
            regFile.delete();

            if (exitCode == 0) {
                JOptionPane.showMessageDialog(this,
                        "Associações de arquivo aplicadas com sucesso!",
                        "Sucesso",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Erro ao aplicar associações. Execute como administrador.",
                        "Erro",
                        JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao aplicar associações: " + ex.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

//    private String getApplicationPath() {
//        try {
//            // Obter o caminho do JAR ou classe em execução
//            String path = new File(ConfigurationFrame.class.getProtectionDomain()
//                    .getCodeSource().getLocation().toURI()).getPath();
//
//            // Se for um JAR, usar javaw.exe -jar
//            if (path.endsWith(".jar")) {
//                String javaHome = System.getProperty("java.home");
//                String javaw = javaHome + "\\bin\\javaw.exe";
//                return "\"" + javaw + "\" -jar \"" + path + "\" \"%1\"";
//            }
//
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
private String getApplicationPath() {
    try {
        // Primeiro, tentar detectar se está rodando como EXE
        String exePath = detectExePath();
        if (exePath != null) {
            return "\"" + exePath + "\" \"%1\"";
        }

        // Se não for EXE, obter o caminho do JAR ou classe em execução
        String path = new File(ConfigurationFrame.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getPath();

        // Se for um JAR, usar javaw.exe -jar
        if (path.endsWith(".jar")) {
            String javaHome = System.getProperty("java.home");
            String javaw = javaHome + "\\bin\\javaw.exe";
            return "\"" + javaw + "\" -jar \"" + path + "\" \"%1\"";
        }

        return null;
    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}
private String detectExePath() {
    try {
        // Método 1: Verificar se existe um EXE com o mesmo nome no diretório atual
        String userDir = System.getProperty("user.dir");
        File currentDir = new File(userDir);

        // Procurar por arquivos .exe no diretório
        File[] exeFiles = currentDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".exe"));

        if (exeFiles != null && exeFiles.length > 0) {
            // Se encontrar apenas um EXE, assumir que é o aplicativo
            if (exeFiles.length == 1) {
                return exeFiles[0].getAbsolutePath();
            }

            // Se houver múltiplos, procurar por nomes comuns
            for (File exe : exeFiles) {
                String name = exe.getName().toLowerCase();
                if (name.contains("videoplayer") ||
                        name.contains("mediaplayer") ||
                        name.contains("player")) {
                    return exe.getAbsolutePath();
                }
            }

            // Retornar o primeiro encontrado
            return exeFiles[0].getAbsolutePath();
        }

        // Método 2: Verificar pela propriedade do sistema (funciona com Launch4j)
        String exeProperty = System.getProperty("exe4j.moduleName");
        if (exeProperty != null && new File(exeProperty).exists()) {
            return exeProperty;
        }

        // Método 3: Através do ProcessHandle (Java 9+)
        try {
            ProcessHandle currentProcess = ProcessHandle.current();
            String command = currentProcess.info().command().orElse(null);
            if (command != null && command.toLowerCase().endsWith(".exe")) {
                return command;
            }
        } catch (Exception e) {
            // ProcessHandle pode não estar disponível
        }

        return null;
    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}

    private File createRegistryFile(String appPath, List<String> formats) throws IOException {
        File regFile = File.createTempFile("media_player_assoc", ".reg");
        StringBuilder content = new StringBuilder();

        content.append("Windows Registry Editor Version 5.00\n\n");

        for (String format : formats) {
            String ext = format.replace(".", "");
            String progId = "MediaPlayer." + ext;

            // Associar extensão
            content.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(format).append("]\n");
            content.append("@=\"").append(progId).append("\"\n\n");

            // Criar ProgID
            content.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(progId).append("]\n");
            content.append("@=\"Media Player File\"\n\n");

            // Comando de abertura
            content.append("[HKEY_CURRENT_USER\\Software\\Classes\\").append(progId).append("\\shell\\open\\command]\n");
            content.append("@=\"").append(appPath.replace("\\", "\\\\")).append("\"\n\n");
        }

        Files.write(regFile.toPath(), content.toString().getBytes());
        return regFile;
    }
}
