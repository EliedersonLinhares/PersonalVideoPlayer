package com.esl.videoplayer.File;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import jnafilechooser.api.JnaFileChooser;
import mslinks.ShellLink;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static com.twelvemonkeys.io.FileUtil.getExtension;

public class FileManager {
    private static final Set<String> AUDIO_EXTS = Set.of("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma", "ac3", "aiff");
    private static final Set<String> IMAGE_EXTS = Set.of("jpeg", "jpg", "png", "webp", "psd", "bmp", "tiff");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "m4s", "avi", "mkv", "mov", "flv", "webm", "gif", "wmv");


    private VideoPlayer videoPlayer;
    public FileManager(VideoPlayer videoPlayer){
        this.videoPlayer = videoPlayer;
    }

    private void handleFile(File file, JComponent parent) {
        if (file.getName().endsWith(".lnk")) {
            try {
                handleFile(new File(new ShellLink(file).resolveTarget()), parent);
            } catch (Exception e) {
                showError(parent, I18N.get("videoPlayer.OpenFile.LinkError.text"));
            }
            return;
        }

        String ext = getExtension(file);
        if (AUDIO_EXTS.contains(ext)) videoPlayer.getAudioExecution().loadAudio(file.getAbsolutePath());
        else if (IMAGE_EXTS.contains(ext)) videoPlayer.getImageExecution().loadImage(file.getAbsolutePath());
        else if (VIDEO_EXTS.contains(ext)) videoPlayer.getVideoExecution().loadVideo(file.getAbsolutePath());
        else showError(parent, I18N.get("videoPlayer.OpenFile.NoFileSupport.text"));
    }
    public void fileTypesAction(File f) {

        if (f.getName().endsWith("mp3")
                || f.getName().endsWith("flac")
                || f.getName().endsWith("wav")
                || f.getName().endsWith("ogg")
                || f.getName().endsWith("m4a")
                || f.getName().endsWith("aac")
                || f.getName().endsWith("opus")
                || f.getName().endsWith("wma")
                || f.getName().endsWith("ac3")
                || f.getName().endsWith("aiff")
        ) {
            videoPlayer.getAudioExecution().loadAudio(f.getAbsolutePath());
        } else if (f.getName().endsWith("jpeg")
                || f.getName().endsWith("jpg")
                || f.getName().endsWith("png")
                || f.getName().endsWith("webp")
                || f.getName().endsWith("psd")
                || f.getName().endsWith("bmp")
                || f.getName().endsWith("tiff")
        ) {
            videoPlayer.getImageExecution().loadImage(f.getAbsolutePath());
        } else {
            videoPlayer.getVideoExecution().loadVideo(f.getAbsolutePath());
        }
    }
    /**
     * Método para abrir um arquivo de vídeo/áudio programaticamente.
     */
    public void openFile(String filePath) throws Exception {

        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new Exception(I18N.get("videoPlayer.ContextOpen.openFile.Exception.fileNotFound") + " " + filePath);
        }

        if (!isMediaFile(path.toFile())) {
            throw new Exception(I18N.get("videoPlayer.ContextOpen.openFile.Exception.UnsupportedFileFormat") + " " + filePath);
        }

        if (filePath.endsWith("mp3")
                || filePath.endsWith("flac")
                || filePath.endsWith("wav")
                || filePath.endsWith("ogg")
                || filePath.endsWith("m4a")
                || filePath.endsWith("aac")
                || filePath.endsWith("opus")
                || filePath.endsWith("wma")
                || filePath.endsWith("ac3")
                || filePath.endsWith("aiff")
        ) {
            videoPlayer.getAudioExecution().loadAudio(filePath);
        } else if (filePath.endsWith("jpeg")
                || filePath.endsWith("jpg")
                || filePath.endsWith("png")
                || filePath.endsWith("webp")
                || filePath.endsWith("psd")
                || filePath.endsWith("bmp")
                || filePath.endsWith("tiff")
        ) {
            videoPlayer.getImageExecution().loadImage(filePath);
        } else {
            videoPlayer.getVideoExecution().loadVideo(filePath);
        }


        System.out.println("Abrindo arquivo: " + filePath);

    }
    public void openVideoOrAudio() {
        if (videoPlayer.isPlaying()) {
            videoPlayer.pauseVideo();
        }
        JnaFileChooser fc = new JnaFileChooser();
        fc.addFilter("Arquivos de Vídeo (*.mp4,*.m4s, *.avi, *.mkv, *.mov, *.flv, *.webm, *.gif, *.wmv, *.mov, *.3gp)", "mp4", "m4s", "avi", "mkv", "mov", "flv", "webm", "gif", "wmv", "mov", "3gp");
        fc.addFilter("Arquivos de Audio (*.mp3,*.flac, *.wav, *.ogg, *.m4a, *.aac, *.opus, *.wma, *.ac3, *.aiff)", "mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma", "ac3", "aiff");
        fc.addFilter("Arquivos de Imagem(*.jpeg,*.jpg,*.png,*.webp)", "jpeg", "jpg", "png", "webp");
        if (fc.showOpenDialog(videoPlayer)) {
            File f = fc.getSelectedFile();
            fileTypesAction(f);
        }
    }
    private static String getExtension(File file) {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }
    private static void showError(JComponent parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, I18N.get("videoPlayer.BatchCapture.showMessageDialog.Error.title"), JOptionPane.ERROR_MESSAGE);
    }
    private static boolean isMediaFile(File file) {
        if (!file.isFile()) {
            return false;
        }

        String name = file.getName().toLowerCase();
        String[] supportedFormats = {
                // Vídeo
                ".mp4", ".avi", ".mkv", ".mov", ".flv", ".webm",
                ".gif", ".wmv", ".3gp",
                // Áudio
                ".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac"
                , ".wma", ".ac3", ".aiff",
                // Image
                ".jpeg", ".jpg", ".png", ".webp", ".psd", ".bmp", ".tiff"
        };

        for (String format : supportedFormats) {
            if (name.endsWith(format)) {
                return true;
            }
        }

        return false;
    }
    public void setupDropTarget(JComponent component) {
        component.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof File file) {
                                handleFile(file, component);
                            }
                        }
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }
}
