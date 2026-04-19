package com.esl.videoplayer.configuration;

import com.esl.videoplayer.localization.I18N;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * JDialog que exibe informações detalhadas do arquivo de mídia carregado.
 * Para áudio: inclui seção de metadata (tags ID3/Vorbis) e miniatura da capa.
 */
public class MediaInfoDialog extends JDialog {

    public enum MediaType { VIDEO, AUDIO, IMAGE }

    // ── Paleta ────────────────────────────────────────────────────────────────
    private static final Color ROW_A        = new Color(40, 40, 48);
    private static final Color ROW_B        = new Color(48, 48, 58);
    private static final Color ACCENT       = new Color(100, 180, 255);
    private static final Color ACCENT2      = new Color(180, 130, 255);   // seção metadata
    private static final Color SECTION_BG   = new Color(25, 25, 32);
    private static final Color TEXT_COLOR   = new Color(220, 220, 230);
    private static final Color LABEL_COLOR  = new Color(140, 180, 220);
    private static final Color META_LABEL   = new Color(180, 150, 255);

    private static final int THUMB_SIZE     = 110;   // px da miniatura

    // ── Construtor principal ──────────────────────────────────────────────────
    public MediaInfoDialog(Window owner,
                           FFmpegFrameGrabber grabber,
                           String filePath,
                           MediaType mediaType,
                           BufferedImage coverArt) {
        super(owner, I18N.get("mediainfo.title"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 0));
       // root.setBackground(BG);

        root.add(buildHeader(filePath, mediaType), BorderLayout.NORTH);

        if (mediaType == MediaType.AUDIO) {
            root.add(buildAudioCenter(grabber, filePath, coverArt), BorderLayout.CENTER);
        } else {
            root.add(buildTable(grabber, filePath, mediaType, null), BorderLayout.CENTER);
        }

        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(500, 340));
        setLocationRelativeTo(owner);
    }

    // ── Construtor sem capa (compat. Video/Image) ─────────────────────────────
    public MediaInfoDialog(Window owner,
                           FFmpegFrameGrabber grabber,
                           String filePath,
                           MediaType mediaType) {
        this(owner, grabber, filePath, mediaType, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HEADER
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildHeader(String filePath, MediaType mediaType) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
       // p.setBackground(HEADER_BG);
        p.setBorder(new EmptyBorder(14, 18, 14, 18));

        String icon = switch (mediaType) {
            case VIDEO -> "🎬";
            case AUDIO -> "🎵";
            case IMAGE -> "🖼";
        };
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        p.add(iconLabel, BorderLayout.WEST);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 2));
        info.setOpaque(false);

        String fileName = (filePath != null) ? new File(filePath).getName()
                : I18N.get("mediainfo.nofile");
        JLabel nameLabel = new JLabel(fileName);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
      //  nameLabel.setForeground(TEXT_COLOR);

        String typeStr = mediaType.name().charAt(0)
                + mediaType.name().substring(1).toLowerCase()
                + " — " + I18N.get("mediainfo.fileinfo");
        JLabel typeLabel = new JLabel(typeStr);
        typeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        typeLabel.setForeground(LABEL_COLOR);

        info.add(nameLabel);
        info.add(typeLabel);
        p.add(info, BorderLayout.CENTER);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CENTRO DE ÁUDIO  (tabela técnica + painel de metadata + miniatura)
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildAudioCenter(FFmpegFrameGrabber grabber,
                                    String filePath,
                                    BufferedImage coverArt) {

        JPanel center = new JPanel(new BorderLayout(0, 0));
        //center.setBackground(BG);

        // Obtém metadata do grabber
        Map<String, String> meta = grabber.getMetadata();   // Map<String,String> no JavaCV

        // ── Miniatura + metadata lado a lado (topo) ───────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setBackground(SECTION_BG);
        topPanel.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Miniatura
        if (coverArt != null) {
            JLabel thumb = buildThumb(coverArt);
            topPanel.add(thumb, BorderLayout.WEST);
        }

        // Grid de metadata
        topPanel.add(buildMetadataPanel(meta), BorderLayout.CENTER);

        center.add(topPanel, BorderLayout.NORTH);

        // ── Tabela técnica (abaixo) ────────────────────────────────────────────
        center.add(buildTable(grabber, filePath, MediaType.AUDIO, meta), BorderLayout.CENTER);

        return center;
    }

    // ── Miniatura ─────────────────────────────────────────────────────────────
    private JLabel buildThumb(BufferedImage cover) {
        // Escala proporcional
        double scale = Math.min((double) THUMB_SIZE / cover.getWidth(),
                (double) THUMB_SIZE / cover.getHeight());
        int w = (int) (cover.getWidth()  * scale);
        int h = (int) (cover.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(cover, 0, 0, w, h, null);
        g2.dispose();

        JLabel lbl = new JLabel(new ImageIcon(scaled));
        lbl.setBorder(new LineBorder(ACCENT2, 1));
        lbl.setPreferredSize(new Dimension(THUMB_SIZE + 2, THUMB_SIZE + 2));
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setVerticalAlignment(SwingConstants.CENTER);
        return lbl;
    }

    // ── Painel de metadata (grid 2 colunas) ───────────────────────────────────
    private JPanel buildMetadataPanel(Map<String, String> meta) {
        // Campos de interesse na ordem de exibição
        String[][] fields = {
                { I18N.get("mediainfo.meta.title"),    metaGet(meta, "TITLE",    "title") },
                { I18N.get("mediainfo.meta.artist"),   metaGet(meta, "ARTIST",   "artist") },
                { I18N.get("mediainfo.meta.album"),    metaGet(meta, "ALBUM",    "album") },
                { I18N.get("mediainfo.meta.genre"),    metaGet(meta, "GENRE",    "genre") },
                { I18N.get("mediainfo.meta.date"),     metaGet(meta, "DATE",     "date") },
                { I18N.get("mediainfo.meta.composer"), metaGet(meta, "COMPOSER", "composer") },
                { I18N.get("mediainfo.meta.track"),    metaGet(meta, "track",    "TRACKNUMBER") },
                { I18N.get("mediainfo.meta.label"),    metaGet(meta, "LABEL",    "label") },
        };

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints cl = new GridBagConstraints();
        cl.anchor = GridBagConstraints.WEST;
        cl.insets = new Insets(2, 4, 2, 8);
        GridBagConstraints cv = new GridBagConstraints();
        cv.anchor = GridBagConstraints.WEST;
        cv.insets = new Insets(2, 0, 2, 4);
        cv.fill   = GridBagConstraints.HORIZONTAL;
        cv.weightx = 1.0;

        int row = 0;
        for (String[] f : fields) {
            if (f[1].equals("—")) continue;   // omite campos vazios

            JLabel lbl = new JLabel(f[0] + ":");
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lbl.setForeground(META_LABEL);

            JLabel val = new JLabel(f[1]);
            val.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            val.setForeground(TEXT_COLOR);

            cl.gridx = 0; cl.gridy = row;
            cv.gridx = 1; cv.gridy = row;
            grid.add(lbl, cl);
            grid.add(val, cv);
            row++;
        }
        return grid;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TABELA TÉCNICA
    // ══════════════════════════════════════════════════════════════════════════
    private JScrollPane buildTable(FFmpegFrameGrabber g,
                                   String filePath,
                                   MediaType mediaType,
                                   Map<String, String> meta) {
        String[][] rows = switch (mediaType) {
            case VIDEO -> buildVideoRows(g, filePath);
            case AUDIO -> buildAudioRows(g, filePath);
            case IMAGE -> buildImageRows(g, filePath);
        };

        String[] cols = { I18N.get("mediainfo.col.property"),
                I18N.get("mediainfo.col.value") };

        DefaultTableModel model = new DefaultTableModel(rows, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(
                    javax.swing.table.TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? ROW_A : ROW_B);
                c.setForeground(col == 0 ? LABEL_COLOR : TEXT_COLOR);
                ((JLabel) c).setBorder(new EmptyBorder(5, 12, 5, 12));
                return c;
            }
        };

        table.setBackground(ROW_A);
        table.setForeground(TEXT_COLOR);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
       // table.getTableHeader().setBackground(HEADER_BG);
        table.getTableHeader().setForeground(ACCENT);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBorder(new EmptyBorder(4, 12, 4, 12));
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(290);

        JScrollPane sp = new JScrollPane(table);
        //sp.setBackground(BG);
        sp.setBorder(new EmptyBorder(0, 0, 0, 0));
       // sp.getViewport().setBackground(BG);
        return sp;
    }

    // ── Rodapé ────────────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
       // p.setBackground(HEADER_BG);
        JButton close = new JButton(I18N.get("mediainfo.close"));
      //  close.setBackground(ACCENT);
        close.setForeground(Color.BLACK);
        close.setFont(new Font("Segoe UI", Font.BOLD, 12));
     //   close.setFocusPainted(false);
      //  close.setBorderPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> dispose());
        p.add(close);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LINHAS POR TIPO
    // ══════════════════════════════════════════════════════════════════════════

    private String[][] buildVideoRows(FFmpegFrameGrabber g, String filePath) {
        return new String[][] {
                { I18N.get("mediainfo.filename"),     fileName(filePath) },
                { I18N.get("mediainfo.filepath"),     filePath != null ? filePath : "—" },
                { I18N.get("mediainfo.filesize"),     fileSize(filePath) },
                { I18N.get("mediainfo.format"),       safe(g.getFormat()) },
                { I18N.get("mediainfo.videocodec"),   safe(g.getVideoCodecName()) },
                { I18N.get("mediainfo.audiocodec"),   safe(g.getAudioCodecName()) },
                { I18N.get("mediainfo.resolution"),   g.getImageWidth() + " × " + g.getImageHeight() },
                { I18N.get("mediainfo.framerate"),    fmtDouble(g.getFrameRate()) + " fps" },
                { I18N.get("mediainfo.duration"),     fmtDuration(g.getLengthInTime()) },
                { I18N.get("mediainfo.videobitrate"), fmtBitrate(g.getVideoBitrate()) },
                { I18N.get("mediainfo.audiobitrate"), fmtBitrate(g.getAudioBitrate()) },
                { I18N.get("mediainfo.samplerate"),   g.getSampleRate() > 0 ? g.getSampleRate() + " Hz" : "—" },
                { I18N.get("mediainfo.channels"),     fmtChannels(g.getAudioChannels()) },
                { I18N.get("mediainfo.pixelformat"),  safe(g.getPixelFormat() >= 0 ? "YUV/" + g.getPixelFormat() : null) },
                { I18N.get("mediainfo.totalframes"),  g.getLengthInFrames() > 0 ? String.valueOf(g.getLengthInFrames()) : "—" },
        };
    }

    private String[][] buildAudioRows(FFmpegFrameGrabber g, String filePath) {
        return new String[][] {
                { I18N.get("mediainfo.filename"),     fileName(filePath) },
                { I18N.get("mediainfo.filepath"),     filePath != null ? filePath : "—" },
                { I18N.get("mediainfo.filesize"),     fileSize(filePath) },
                { I18N.get("mediainfo.format"),       safe(g.getFormat()) },
                { I18N.get("mediainfo.audiocodec"),   safe(g.getAudioCodecName()) },
                { I18N.get("mediainfo.duration"),     fmtDuration(g.getLengthInTime()) },
                { I18N.get("mediainfo.audiobitrate"), fmtBitrate(g.getAudioBitrate()) },
                { I18N.get("mediainfo.samplerate"),   g.getSampleRate() > 0 ? g.getSampleRate() + " Hz" : "—" },
                { I18N.get("mediainfo.channels"),     fmtChannels(g.getAudioChannels()) },
                { I18N.get("mediainfo.bitdepth"),     fmtBitDepth(g) },
        };
    }

    private String[][] buildImageRows(FFmpegFrameGrabber g, String filePath) {
        return new String[][] {
                { I18N.get("mediainfo.filename"),    fileName(filePath) },
                { I18N.get("mediainfo.filepath"),    filePath != null ? filePath : "—" },
                { I18N.get("mediainfo.filesize"),    fileSize(filePath) },
                { I18N.get("mediainfo.format"),      safe(g.getFormat()) },
                { I18N.get("mediainfo.videocodec"),  safe(g.getVideoCodecName()) },
                { I18N.get("mediainfo.resolution"),  g.getImageWidth() + " × " + g.getImageHeight() },
                { I18N.get("mediainfo.pixelformat"), safe(g.getPixelFormat() >= 0 ? "YUV/" + g.getPixelFormat() : null) },
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UTILITÁRIOS
    // ══════════════════════════════════════════════════════════════════════════

    /** Tenta várias chaves de metadata (case-insensitive fallback). */
    private String metaGet(Map<String, String> meta, String... keys) {
        if (meta == null) return "—";
        for (String k : keys) {
            String v = meta.get(k);
            if (v == null) v = meta.get(k.toLowerCase());
            if (v == null) v = meta.get(k.toUpperCase());
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "—";
    }

    private String safe(String v) {
        return (v != null && !v.isBlank()) ? v : "—";
    }

    private String fileName(String path) {
        return path != null ? new File(path).getName() : "—";
    }

    private String fileSize(String path) {
        if (path == null) return "—";
        File f = new File(path);
        if (!f.exists()) return "—";
        long bytes = f.length();
        if (bytes < 1024)             return bytes + " B";
        if (bytes < 1024 * 1024)      return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String fmtBitrate(int bps) {
        if (bps <= 0) return "—";
        if (bps < 1_000_000) return (bps / 1000) + " kbps";
        return String.format("%.1f Mbps", bps / 1_000_000.0);
    }

    private String fmtDuration(long micros) {
        if (micros <= 0) return "—";
        long s  = micros / 1_000_000;
        long ms = (micros / 1000) % 1000;
        long h  = s / 3600; s %= 3600;
        long m  = s / 60;   s %= 60;
        return h > 0
                ? String.format("%d:%02d:%02d.%03d", h, m, s, ms)
                : String.format("%d:%02d.%03d", m, s, ms);
    }

    private String fmtDouble(double v) {
        return v > 0 ? String.format("%.3f", v) : "—";
    }

    private String fmtChannels(int ch) {
        return switch (ch) {
            case 1  -> "1 (Mono)";
            case 2  -> "2 (Stereo)";
            case 6  -> "6 (5.1)";
            case 8  -> "8 (7.1)";
            default -> ch > 0 ? String.valueOf(ch) : "—";
        };
    }

    /**
     * Tenta inferir bit depth a partir do SampleFormat do grabber.
     * JavaCV expõe getSampleFormat() como int (AVSampleFormat).
     * Valores comuns: AV_SAMPLE_FMT_S16=1 (16-bit), AV_SAMPLE_FMT_S32=3 (32-bit),
     * AV_SAMPLE_FMT_FLT=4 (32-bit float), AV_SAMPLE_FMT_DBL=5 (64-bit).
     */
    private String fmtBitDepth(FFmpegFrameGrabber g) {
        int fmt = g.getSampleFormat();   // AVSampleFormat ordinal
        return switch (fmt) {
            case 0  -> "8-bit (unsigned)";
            case 1  -> "16-bit";
            case 2  -> "32-bit (int)";
            case 3  -> "32-bit (int)";
            case 4  -> "32-bit (float)";
            case 5  -> "64-bit (double)";
            case 6  -> "8-bit planar";
            case 7  -> "16-bit planar";
            case 8  -> "32-bit planar (int)";
            case 9  -> "32-bit planar (float)";
            case 10 -> "64-bit planar";
            default -> "—";
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FACTORY METHODS — chamados pelos menus
    // ══════════════════════════════════════════════════════════════════════════

    public static void showForVideo(Component parent,
                                    FFmpegFrameGrabber grabber,
                                    String filePath) {
        Window w = SwingUtilities.getWindowAncestor(parent);
        new MediaInfoDialog(w, grabber, filePath, MediaType.VIDEO).setVisible(true);
    }


    public static void showForAudio(Component parent,
                                    FFmpegFrameGrabber grabber,
                                    String filePath,
                                    BufferedImage coverArt) {
        Window w = SwingUtilities.getWindowAncestor(parent);
        new MediaInfoDialog(w, grabber, filePath, MediaType.AUDIO, coverArt).setVisible(true);
    }

    public static void showForImage(Component parent,
                                    FFmpegFrameGrabber grabber,
                                    String filePath) {
        Window w = SwingUtilities.getWindowAncestor(parent);
        new MediaInfoDialog(w, grabber, filePath, MediaType.IMAGE).setVisible(true);
    }
}