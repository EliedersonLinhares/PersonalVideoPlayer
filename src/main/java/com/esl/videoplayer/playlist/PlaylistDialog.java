package com.esl.videoplayer.playlist;


import com.esl.videoplayer.localization.I18N;
import jnafilechooser.api.JnaFileChooser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class PlaylistDialog extends JDialog implements I18N.LanguageChangeListener {
    private PlaylistManager playlistManager;
    private DefaultListModel<PlaylistItem> listModel;
    private JList<PlaylistItem> playlistList;

    private JButton addButton;
    private JButton removeButton;
    private JButton clearButton;
    private JButton moveUpButton;
    private JButton moveDownButton;
    private JButton saveButton;

    private JToggleButton shuffleButton;
    private JToggleButton repeatButton;
    private JToggleButton repeatOneButton;

    private JButton playButton;
    private JButton nextButton;
    private JButton previousButton;

    private JLabel statusLabel;

    // Interface para comunicação com VideoPlayer
    private PlaylistCallback callback;

    public PlaylistDialog(JFrame parent, PlaylistManager manager, PlaylistCallback callback) {
        super(parent, "Playlist Manager", false);
        this.playlistManager = manager;
        this.callback = callback;

        setSize(400, 500);
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        int newX = parent.getX() + (parent.getWidth()  - 10);
        int newY = parent.getY() - (parent.getHeight() / 2 - 250);
        setLocation(newX, newY);

        initComponents();
        refreshPlaylist();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // ===== Painel Central: Lista =====
        listModel = new DefaultListModel<>();
        playlistList = new JList<>(listModel);
        playlistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistList.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        playlistList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                PlaylistItem item = (PlaylistItem) value;
                String displayText = item.toString();

                super.getListCellRendererComponent(list, displayText, index, isSelected, cellHasFocus);

                if (index == playlistManager.getCurrentIndex()) {
                    setForeground(new Color(0, 150, 255));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (item.isPlayed() && playlistManager.isShuffle()) {
                    if (!isSelected) {
                        setForeground(new Color(120, 120, 120));
                    }
                    setFont(getFont().deriveFont(Font.ITALIC));
                }

                return this;
            }
        });

        playlistList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = playlistList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        playSelectedTrack(index);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(playlistList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Tracks"));
        add(scrollPane, BorderLayout.CENTER);

        // ===== Painel Esquerdo: Gerenciamento =====
        JPanel leftPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));

        addButton = new JButton("➕");
        addButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.addButton"));
        addButton.setPreferredSize(new Dimension(35, 35));
        addButton.addActionListener(e -> addTracks());

        removeButton = new JButton("➖");
        removeButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.removeButton"));
        removeButton.setPreferredSize(new Dimension(35, 35));
        removeButton.addActionListener(e -> removeSelectedTrack());

        clearButton = new JButton("🗑");
        clearButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.clearButton"));
        clearButton.setPreferredSize(new Dimension(35, 35));
        clearButton.addActionListener(e -> clearPlaylist(true));

        moveUpButton = new JButton("⬆");
        moveUpButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.moveUpButton"));
        moveUpButton.setPreferredSize(new Dimension(35, 35));
        moveUpButton.addActionListener(e -> moveUp());

        moveDownButton = new JButton("⬇");
        moveDownButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.moveDownButton"));
        moveDownButton.setPreferredSize(new Dimension(35, 35));
        moveDownButton.addActionListener(e -> moveDown());

        JPanel spacer = new JPanel();

        leftPanel.add(addButton);
        leftPanel.add(removeButton);
        leftPanel.add(clearButton);
        leftPanel.add(spacer);
        leftPanel.add(moveUpButton);
        leftPanel.add(moveDownButton);

        add(leftPanel, BorderLayout.WEST);

        // ===== Painel Inferior: Controles =====
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JPanel playbackPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        previousButton = new JButton("⮜");
        previousButton.setPreferredSize(new Dimension(35, 35));
        previousButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.previousButton"));
        previousButton.addActionListener(e -> playPrevious());

        playButton = new JButton("▶");
        playButton.setPreferredSize(new Dimension(35, 35));
        playButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.playButton"));
        playButton.addActionListener(e -> playSelected());

        nextButton = new JButton("⮞");
        nextButton.setPreferredSize(new Dimension(35, 35));
        nextButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.nextButton"));
        nextButton.addActionListener(e -> playNext());

        shuffleButton = new JToggleButton("🔀");
        shuffleButton.setPreferredSize(new Dimension(35, 35));
        shuffleButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.shuffleButton"));
        shuffleButton.addActionListener(e -> toggleShuffle());

        repeatButton = new JToggleButton("🔁");
        repeatButton.setPreferredSize(new Dimension(35, 35));
        repeatButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.repeatButton"));
        repeatButton.addActionListener(e -> toggleRepeat());

        repeatOneButton = new JToggleButton("🔂");
        repeatOneButton.setPreferredSize(new Dimension(35, 35));
        repeatOneButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.repeatOneButton"));
        repeatOneButton.addActionListener(e -> toggleRepeatOne());

        playbackPanel.add(previousButton);
        playbackPanel.add(playButton);
        playbackPanel.add(nextButton);
        playbackPanel.add(shuffleButton);
        playbackPanel.add(repeatButton);
        playbackPanel.add(repeatOneButton);

        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        saveButton = new JButton("💾");
        saveButton.setToolTipText(I18N.get("PlaylistDialog.initComponents.saveButton"));
        saveButton.setPreferredSize(new Dimension(35, 35));
        saveButton.addActionListener(e -> savePlaylist());

        filePanel.add(saveButton);

        statusLabel = new JLabel("0 " + I18N.get("PlaylistDialog.initComponents.statusLabel"));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        bottomPanel.add(filePanel, BorderLayout.WEST);
        bottomPanel.add(playbackPanel, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ===== Métodos de Ação =====

    private void notifyPlaylistChanged() {
        refreshPlaylist();
    }

    private void addTracks() {
        JnaFileChooser fc = new JnaFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.addFilter(I18N.get("PlaylistDialog.addTracks.addFilters1"), "mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "ac3", "aiff");
        fc.addFilter(I18N.get("PlaylistDialog.addTracks.addFilters2"), "mp4", "avi", "mkv", "mov", "flv", "webm");

        if (fc.showOpenDialog(this)) {
            File[] files = fc.getSelectedFiles();
            for (File file : files) {
                PlaylistItem item = new PlaylistItem(file.getAbsolutePath());
                playlistManager.addItem(item);
            }
            notifyPlaylistChanged();
        }
    }
    private void removeSelectedTrack() {
        int selected = playlistList.getSelectedIndex();
        if (selected >= 0) {
            playlistManager.removeItem(selected);
            notifyPlaylistChanged();
        }
    }

    private void clearPlaylist(boolean showDialog) {
        if (showDialog) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    I18N.get("PlaylistDialog.clearPlaylist.showConfirmDialog.text"),
                    I18N.get("PlaylistDialog.clearPlaylist.showConfirmDialog.title"), JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                playlistManager.clear();
                notifyPlaylistChanged();
            }
        } else {
            playlistManager.clear();
            notifyPlaylistChanged();
        }
    }

    private void moveUp() {
        int selected = playlistList.getSelectedIndex();
        if (selected > 0) {
            playlistManager.moveItem(selected, selected - 1);
            notifyPlaylistChanged();
            playlistList.setSelectedIndex(selected - 1);
        }
    }

    private void moveDown() {
        int selected = playlistList.getSelectedIndex();
        if (selected >= 0 && selected < playlistManager.size() - 1) {
            playlistManager.moveItem(selected, selected + 1);
            notifyPlaylistChanged();
            playlistList.setSelectedIndex(selected + 1);
        }
    }

    private void savePlaylist() {
        JnaFileChooser fc = new JnaFileChooser();
        fc.setDefaultFileName("playlist.m3u");
        fc.addFilter("Playlist (*.m3u)", "m3u");

        if (fc.showSaveDialog(this)) {
            File file = fc.getSelectedFile();
            String path = file.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".m3u")) {
                path += ".m3u";
            }

            try {
                playlistManager.saveM3U(path);
                JOptionPane.showMessageDialog(this,
                        I18N.get("PlaylistDialog.savePlaylist.showMessageDialog.text1"),
                        I18N.get("PlaylistDialog.savePlaylist.showMessageDialog.title1"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        I18N.get("PlaylistDialog.savePlaylist.showMessageDialog.text2")+ "\n" + e.getMessage(),
                        I18N.get("PlaylistDialog.savePlaylist.showMessageDialog.title2"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void toggleShuffle() {
        playlistManager.setShuffle(shuffleButton.isSelected());
        updateStatusLabel();
    }

    private void toggleRepeat() {
        playlistManager.setRepeat(repeatButton.isSelected());
        if (repeatButton.isSelected()) {
            repeatOneButton.setSelected(false);
        }
        updateStatusLabel();
    }

    private void toggleRepeatOne() {
        playlistManager.setRepeatOne(repeatOneButton.isSelected());
        if (repeatOneButton.isSelected()) {
            repeatButton.setSelected(false);
        }
        updateStatusLabel();
    }

    private void playSelected() {
        if (callback != null) {
            callback.onAutoPlayRequested();
        }

        int selected = playlistList.getSelectedIndex();
        if (selected >= 0) {
            playSelectedTrack(selected);
        } else if (playlistManager.size() > 0) {
            playSelectedTrack(0);
        }
    }

    private void playSelectedTrack(int index) {
        playlistManager.setCurrentIndex(index);
        PlaylistItem item = playlistManager.getCurrentItem();
        if (item != null) {
            playTrack(item);
            refreshPlaylist();
        }
    }

    private void playNext() {
        if (callback != null) {
            callback.onAutoPlayRequested();
        }

        PlaylistItem next = playlistManager.next();
        if (next != null) {
            playTrack(next);
            refreshPlaylist();
        } else {
            JOptionPane.showMessageDialog(this,
                    I18N.get("PlaylistDialog.playNext.showMessageDialog.text"),
                    I18N.get("PlaylistDialog.playNext.showMessageDialog.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void playPrevious() {
        if (callback != null) {
            callback.onAutoPlayRequested();
        }

        PlaylistItem prev = playlistManager.previous();
        if (prev != null) {
            playTrack(prev);
            refreshPlaylist();
        }
    }

    private void playTrack(PlaylistItem item) {
        if (callback != null) {
            callback.onPlayTrack(item.getFilePath());
        }
    }

    public void refreshPlaylist() {
        listModel.clear();
        for (PlaylistItem item : playlistManager.getPlaylist()) {
            listModel.addElement(item);
        }

        if (playlistManager.getCurrentIndex() >= 0) {
            playlistList.setSelectedIndex(playlistManager.getCurrentIndex());
        }
        playlistList.repaint();
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(playlistManager.size()).append(" " + I18N.get("PlaylistDialog.updateStatusLabel1"));

        if (playlistManager.isShuffle()) sb.append(" | " + I18N.get("PlaylistDialog.updateStatusLabel2"));
        if (playlistManager.isRepeat()) sb.append(" | " + I18N.get("PlaylistDialog.updateStatusLabel3"));
        if (playlistManager.isRepeatOne()) sb.append(" | " + I18N.get("PlaylistDialog.updateStatusLabel4"));

        statusLabel.setText(sb.toString());
    }

    @Override
    public void onLanguageChanged(Locale newLocale) {

    }

    // Interface para callback
    public interface PlaylistCallback {
        void onPlayTrack(String filePath);
        void onAutoPlayRequested();
    }
}