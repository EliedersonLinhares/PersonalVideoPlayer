package com.esl.videoplayer.Video;

import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public class ScreenMode implements I18N.LanguageChangeListener {

    private JWindow fullscreenWindow;  // janela dedicada, criada uma vez

    private void ensureFullscreenWindow(VideoPlayer videoPlayer) {
        if (fullscreenWindow == null) {
            fullscreenWindow = new JWindow(videoPlayer);
            fullscreenWindow.setBackground(Color.BLACK);
        }
    }

    public void enterFullScreen(VideoPlayer videoPlayer,JPanel controlPanel, String currentVideoPath) {

        GraphicsDevice gd = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (!gd.isFullScreenSupported()) {
            JOptionPane.showMessageDialog(videoPlayer,
                    I18N.get("ScreenMode.enterFullScreen.showMessageDialog.text"),
                    I18N.get("ScreenMode.enterFullScreen.showMessageDialog.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        ensureFullscreenWindow(videoPlayer);

        fullscreenWindow.getContentPane().removeAll();
        fullscreenWindow.getContentPane().add(videoPlayer.getMainPanel());
        fullscreenWindow.getContentPane().revalidate();

        videoPlayer.saveVideoState();
        controlPanel.setVisible(false);

        // Ativar fullscreen na janela dedicada, não na principal
        gd.setFullScreenWindow(fullscreenWindow);
        fullscreenWindow.setVisible(true);

        if (currentVideoPath != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    Thread.sleep(150);
                    videoPlayer.restoreVideoState();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }


    public void exitFullScreen(VideoPlayer videoPlayer,  JPanel controlPanel, Rectangle normalBounds,
                               String currentVideoPath) {

        GraphicsDevice gd = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice();

        videoPlayer.saveVideoState();

        // Sair do fullscreen
        gd.setFullScreenWindow(null);

        if (fullscreenWindow != null) {
            fullscreenWindow.getContentPane().removeAll();
            fullscreenWindow.setVisible(false);
        }

        // Devolver o MainPanel ao VideoPlayer (o JFrame principal)
        videoPlayer.getContentPane().add(videoPlayer.getMainPanel());
        videoPlayer.getContentPane().revalidate();
        videoPlayer.getContentPane().repaint();

        controlPanel.setVisible(true);

        if (normalBounds != null) {
            videoPlayer.setBounds(normalBounds);
        }

        if (currentVideoPath != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    Thread.sleep(150);
                    videoPlayer.restoreVideoState();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // Chamar isso apenas quando o aplicativo for realmente encerrado
    public void cleanup() {
        if (fullscreenWindow != null) {
            fullscreenWindow.dispose();
            fullscreenWindow = null;
        }
    }


    @Override
    public void onLanguageChanged(Locale newLocale) {

    }
}
