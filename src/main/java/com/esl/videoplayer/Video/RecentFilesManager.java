package com.esl.videoplayer.Video;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Gerencia os arquivos recentemente abertos no player
 * Salva até 5 arquivos mais recentes com suas informações em arquivo .txt
 */
public class RecentFilesManager {

    private static final int MAX_RECENT_FILES = 5;
    private static final String RECENT_FILES_TXT = "recent_files.txt";
    private static final String SEPARATOR = "|";

    private final File recentFilesFile;
    private final LinkedList<RecentFile> recentFiles;

    public RecentFilesManager() {
        // Criar arquivo no diretório do usuário
        String userHome = System.getProperty("user.home");
        String appDir = userHome + File.separator + ".videoplayer";

        // Criar diretório se não existir
        File appDirectory = new File(appDir);
        if (!appDirectory.exists()) {
            boolean created = appDirectory.mkdirs();
            System.out.println("Diretório criado: " + appDir + " - Sucesso: " + created);
        }

        this.recentFilesFile = new File(appDir, RECENT_FILES_TXT);
        this.recentFiles = new LinkedList<>();

        System.out.println("Arquivo de recentes: " + recentFilesFile.getAbsolutePath());

        // Criar arquivo vazio se não existir
        if (!recentFilesFile.exists()) {
            try {
                boolean created = recentFilesFile.createNewFile();
                System.out.println("Arquivo de recentes criado: " + created);
            } catch (IOException e) {
                System.err.println("Erro ao criar arquivo de recentes: " + e.getMessage());
                e.printStackTrace();
            }
        }

        loadRecentFiles();
    }

    /**
     * Classe interna para representar um arquivo recente
     */
    public static class RecentFile {
        private final String filePath;
        private final String fileName;
        private final boolean isAudio;
        private final long lastAccessed;

        public RecentFile(String filePath, boolean isAudio) {
            this.filePath = filePath;
            this.fileName = new File(filePath).getName();
            this.isAudio = isAudio;
            this.lastAccessed = System.currentTimeMillis();
        }

        public RecentFile(String filePath, String fileName, boolean isAudio, long lastAccessed) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.isAudio = isAudio;
            this.lastAccessed = lastAccessed;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFileName() {
            return fileName;
        }

        public boolean isAudio() {
            return isAudio;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public boolean fileExists() {
            return Files.exists(Paths.get(filePath));
        }

        public String getDisplayName() {
            // Truncar nome se muito longo
            if (fileName.length() > 40) {
                return fileName.substring(0, 37) + "...";
            }
            return fileName;
        }

        public String getIcon() {
            return isAudio ? ">" : ">";
        }

        /**
         * Serializa o arquivo para string
         */
        public String serialize() {
            return filePath + SEPARATOR +
                    fileName + SEPARATOR +
                    isAudio + SEPARATOR +
                    lastAccessed;
        }

        /**
         * Deserializa string para RecentFile
         */
        public static RecentFile deserialize(String data) {
            try {
                String[] parts = data.split("\\" + SEPARATOR);
                if (parts.length == 4) {
                    return new RecentFile(
                            parts[0],
                            parts[1],
                            Boolean.parseBoolean(parts[2]),
                            Long.parseLong(parts[3])
                    );
                }
            } catch (Exception e) {
                System.err.println("Erro ao deserializar arquivo recente: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Adiciona um arquivo à lista de recentes
     */
    public void addRecentFile(String filePath, boolean isAudio) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }

        System.out.println("Adicionando arquivo recente: " + filePath);

        // Remover se já existe (para reordenar)
        recentFiles.removeIf(rf -> rf.getFilePath().equals(filePath));

        // Adicionar no início
        recentFiles.addFirst(new RecentFile(filePath, isAudio));

        // Limitar a MAX_RECENT_FILES
        while (recentFiles.size() > MAX_RECENT_FILES) {
            RecentFile removed = recentFiles.removeLast();
            System.out.println("Removido (limite excedido): " + removed.getFileName());
        }

        // Salvar imediatamente
        saveRecentFiles();
    }

    /**
     * Obtém a lista de arquivos recentes
     */
    public List<RecentFile> getRecentFiles() {
        // Retornar apenas arquivos que ainda existem
        return recentFiles.stream()
                .filter(RecentFile::fileExists)
                .toList();
    }

    /**
     * Limpa todos os arquivos recentes
     */
    public void clearRecentFiles() {
        System.out.println("Limpando todos os arquivos recentes...");
        recentFiles.clear();
        saveRecentFiles();
        System.out.println("Lista de recentes limpa!");
    }

    /**
     * Remove um arquivo específico da lista
     */
    public void removeRecentFile(String filePath) {
        System.out.println("Removendo arquivo recente: " + filePath);
        boolean removed = recentFiles.removeIf(rf -> rf.getFilePath().equals(filePath));
        if (removed) {
            saveRecentFiles();
            System.out.println("Arquivo removido da lista de recentes!");
        }
    }

    /**
     * Retorna o caminho do arquivo de recentes (para debug)
     */
    public String getRecentFilesPath() {
        return recentFilesFile.getAbsolutePath();
    }

    /**
     * Carrega os arquivos recentes do arquivo .txt
     */
    private void loadRecentFiles() {
        if (!recentFilesFile.exists()) {
            System.out.println("Arquivo de recentes não existe. Nenhum arquivo para carregar.");
            return;
        }

        // Verificar se o arquivo está vazio
        if (recentFilesFile.length() == 0) {
            System.out.println("Arquivo de recentes está vazio. Nenhum arquivo recente disponível.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(recentFilesFile))) {
            String line;
            int count = 0;

            System.out.println("=== Carregando arquivos recentes ===");

            while ((line = reader.readLine()) != null && count < MAX_RECENT_FILES) {
                line = line.trim();
                if (!line.isEmpty()) {
                    RecentFile rf = RecentFile.deserialize(line);
                    if (rf != null) {
                        // Adicionar apenas se o arquivo ainda existir
                        if (rf.fileExists()) {
                            recentFiles.add(rf);
                            System.out.println("✓ Carregado: " + rf.getFileName());
                            count++;
                        } else {
                            System.out.println("✗ Ignorado (não existe): " + rf.getFilePath());
                        }
                    } else {
                        System.out.println("✗ Erro ao deserializar linha: " + line);
                    }
                }
            }

            System.out.println("Total de arquivos recentes carregados: " + recentFiles.size());
            System.out.println("====================================");

        } catch (IOException e) {
            System.err.println("Erro ao carregar arquivos recentes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Salva os arquivos recentes no arquivo .txt
     */
    private void saveRecentFiles() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(recentFilesFile))) {
            System.out.println("=== Salvando arquivos recentes ===");

            for (RecentFile rf : recentFiles) {
                writer.write(rf.serialize());
                writer.newLine();
                System.out.println("✓ Salvo: " + rf.getFileName());
            }

            writer.flush();
            System.out.println("Arquivo salvo em: " + recentFilesFile.getAbsolutePath());
            System.out.println("===================================");

        } catch (IOException e) {
            System.err.println("Erro ao salvar arquivos recentes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Verifica se há arquivos recentes disponíveis
     */
    public boolean hasRecentFiles() {
        return !getRecentFiles().isEmpty();
    }
}