package com.esl.videoplayer;

import com.esl.videoplayer.File.FileManager;
import com.esl.videoplayer.localization.I18N;
import com.esl.videoplayer.theme.ThemeManager;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;

import javax.swing.*;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class PlayerExecutor {

    public static void main(String[] args) {

        // Substitui os args corrompidos pelos args nativos do Windows (UTF-16)
        String[] safeArgs = getWindowsArgs();
        if (safeArgs != null && safeArgs.length > 0) {
            args = safeArgs;
        }

        final String[] finalArgs = args;
        SwingUtilities.invokeLater(() -> {
            ThemeManager themeManager2 = new ThemeManager();

            //Para iniciar com o tema dependendo do que foi escolhido previamente
            if (themeManager2.getCurrentTheme().contentEquals("FlatArcOrangeIJTheme")) {
                FlatArcOrangeIJTheme.setup();
            } else if (themeManager2.getCurrentTheme().contentEquals("FlatArcDarkOrangeIJTheme")) {
                FlatArcDarkOrangeIJTheme.setup();
            } else if (themeManager2.getCurrentTheme().contentEquals("FlatDraculaIJTheme")) {
                FlatDraculaIJTheme.setup();
            } else {
                FlatArcDarkOrangeIJTheme.setup();
            }
            //Botoes redondos
            UIManager.put("Button.arc", 999);

            VideoPlayer player = new VideoPlayer();
            player.setVisible(true);
            if (finalArgs.length > 0) {
                try {
                    String filePath = sanitizeFilePath(finalArgs[0]);
                    player.getFileManager().openFile(filePath);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(player,
                            I18N.get("videoPlayer.IllegalCharacter.showMessageDialog.text1") + ":\n" +
                                    I18N.get("videoPlayer.IllegalCharacter.showMessageDialog.text2"),
                            I18N.get("videoPlayer.IllegalCharacter.showMessageDialog.title"), JOptionPane.ERROR_MESSAGE);

                }
            }
        });
    }

    /**
     * Divide a linha de comando respeitando aspas e espaços.
     */
    private static List<String> splitCommandLine(String cmdLine) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : cmdLine.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args;
    }

    private static String[] getWindowsArgs() {
        try {
            // Lê a linha de comando do processo atual via ProcessHandle
            String commandLine = ProcessHandle.current()
                    .info()
                    .commandLine()
                    .orElse(null);

            if (commandLine == null) return null;

            List<String> argList = splitCommandLine(commandLine);

            // Remove o executável (primeiro elemento)
            if (argList.size() > 1) {
                return argList.subList(1, argList.size()).toArray(new String[0]);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Sanitiza o caminho do arquivo para lidar com caracteres especiais,
     * emojis, caracteres Unicode e outros casos problemáticos.
     */
    private static String sanitizeFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }

        try {
            // 1. Normaliza o Unicode (NFC = forma mais compatível)
            // Resolve casos como letras acentuadas em formas decompostas
            String normalized = Normalizer.normalize(filePath, Normalizer.Form.NFC);

            // 2. Converte para Path nativo do sistema operacional
            // O java.nio lida com Unicode melhor que java.io.File
            Path path = Paths.get(normalized);

            // 3. Converte para caminho absoluto e resolve ".." e "."
            Path absolutePath = path.toAbsolutePath().normalize();

            // 4. Retorna como string usando o separador nativo do SO
            return absolutePath.toString();

        } catch (InvalidPathException e) {
            // Se ainda falhar, tenta uma limpeza mais agressiva
            return fallbackSanitize(filePath);
        }
    }

    /**
     * Limpeza mais agressiva como último recurso.
     * Cria um arquivo temporário com nome seguro apontando para o original.
     */
    private static String fallbackSanitize(String filePath) {
        try {
            // Extrai só o diretório pai e tenta acessar via listagem
            // em vez de usar o nome diretamente
            File originalFile = new File(filePath);
            File parentDir = originalFile.getParentFile();

            if (parentDir != null && parentDir.exists()) {
                String originalName = originalFile.getName();

                // Percorre os arquivos do diretório comparando por nome
                File[] files = parentDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().equals(originalName)) {
                            return f.getAbsolutePath();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return filePath; // retorna original se tudo falhar
    }

}
