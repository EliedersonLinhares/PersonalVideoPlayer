package com.esl.videoplayer.configuration;

import com.esl.videoplayer.localization.I18N;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;

/**
 * JDialog "Sobre" do MediaPlayer.
 * Recebe um {@link BufferedImage} opcional como logo do aplicativo.
 *
 * Chamada: AboutDialog.show(parentComponent, logoImage);
 */
public class AboutDialog extends JDialog {

    // ── Configurações do aplicativo ───────────────────────────────────────────
    // Centralize aqui — ou mova para um AppInfo.java separado
    private static final String APP_NAME       = "MediaPlayer";
    private static final String APP_VERSION    = "0.8.0 beta";
    private static final String APP_URL        = "https://github.com/EliedersonLinhares/PersonalVideoPlayer";
    private static final String AUTHOR_NAME    = "Eliederson Linhares";
    private static final String AUTHOR_URL     = "https://github.com/EliedersonLinhares";
    private static final String LIB_FFMPEG     = "FFmpeg 7.1.1-1.5.12 (GPL v3)";
    private static final String LIB_JAVACV     = "JavaCV 1.5.x";
    private static final String LIB_JAVA       = "Java 25";

    // ── Paleta (mesma do MediaInfoDialog) ────────────────────────────────────
    private static final Color BG          = new Color(30, 30, 35);
    private static final Color HEADER_BG   = new Color(20, 20, 26);
    private static final Color SECTION_BG  = new Color(38, 38, 46);
    private static final Color ACCENT      = new Color(100, 180, 255);
    private static final Color ACCENT2     = new Color(180, 130, 255);
    private static final Color TEXT_COLOR  = new Color(220, 220, 230);
    private static final Color MUTED       = new Color(130, 130, 150);
    private static final Color SEPARATOR   = new Color(55, 55, 68);

    private static final int LOGO_SIZE = 80;

    // ── Construtor ────────────────────────────────────────────────────────────
    public AboutDialog(Window owner, BufferedImage logo) {
        super(owner, I18N.get("about.title"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 0));
      // root.setBackground(BG);

        root.add(buildHeader(logo), BorderLayout.NORTH);
        root.add(buildBody(),       BorderLayout.CENTER);
        root.add(buildFooter(),     BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(420, 320));
        setLocationRelativeTo(owner);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HEADER — logo + nome do app + versão
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildHeader(BufferedImage logo) {
        JPanel p = new JPanel(new BorderLayout(18, 0));
        //p.setBackground(HEADER_BG);
        p.setBorder(new EmptyBorder(20, 24, 20, 24));

        // ── Logo ──────────────────────────────────────────────────────────────
        if (logo != null) {
            p.add(buildLogoLabel(logo), BorderLayout.WEST);
        }

        // ── Nome + versão ─────────────────────────────────────────────────────
        JPanel namePanel = new JPanel();
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        namePanel.setOpaque(false);

        JLabel appName = new JLabel(APP_NAME);
        appName.setFont(new Font("Segoe UI", Font.BOLD, 30));
        //appName.setForeground(TEXT_COLOR);
        appName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel(I18N.get("about.tagline"));
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tagline.setForeground(MUTED);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel version = new JLabel(I18N.get("about.version") + "  " + APP_VERSION);
        version.setFont(new Font("Segoe UI", Font.BOLD, 11));
        version.setForeground(ACCENT);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setBorder(new EmptyBorder(6, 0, 0, 0));

        namePanel.add(appName);
        namePanel.add(Box.createVerticalStrut(2));
        namePanel.add(tagline);
        namePanel.add(version);

        p.add(namePanel, BorderLayout.CENTER);
        return p;
    }

    private JLabel buildLogoLabel(BufferedImage src) {
        double scale = Math.min((double) LOGO_SIZE / src.getWidth(),
                (double) LOGO_SIZE / src.getHeight());
        int w = (int) (src.getWidth()  * scale);
        int h = (int) (src.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();

        JLabel lbl = new JLabel(new ImageIcon(scaled));
        lbl.setPreferredSize(new Dimension(LOGO_SIZE, LOGO_SIZE));
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setVerticalAlignment(SwingConstants.CENTER);
        return lbl;
    }

    // ── Record para representar uma linha da seção ────────────────────────────
    private record InfoRow(String label, String plainValue, String linkUrl, Color linkColor) {
        /** Linha simples (texto). */
        static InfoRow text(String label, String value) {
            return new InfoRow(label, value, null, null);
        }
        /** Linha com hyperlink clicável. */
        static InfoRow link(String label, String url, Color color) {
            return new InfoRow(label, null, url, color);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BODY — informações em seções
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
       // body.setBackground(BG);
        body.setBorder(new EmptyBorder(4, 0, 4, 0));

        body.add(buildSection(I18N.get("about.section.app"),
                InfoRow.link(I18N.get("about.website"), APP_URL, ACCENT)
        ));

        body.add(buildSection(I18N.get("about.section.author"),
                InfoRow.text(I18N.get("about.author"),     AUTHOR_NAME),
                InfoRow.link(I18N.get("about.authorsite"), AUTHOR_URL, ACCENT2)
        ));

        body.add(buildSection(I18N.get("about.section.libs"),
                InfoRow.text("FFmpeg", LIB_FFMPEG),
                InfoRow.text("JavaCV", LIB_JAVACV),
                InfoRow.text("Java",   LIB_JAVA)
        ));

        return body;
    }

    /**
     * Monta uma seção com título e linhas tipadas.
     * Aceita varargs de {@link InfoRow} para evitar arrays Object[][].
     */
    private JPanel buildSection(String title, InfoRow... rows) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBackground(SECTION_BG);
        section.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, SEPARATOR),
                new EmptyBorder(10, 24, 10, 24)));

        GridBagConstraints gl = new GridBagConstraints();
        gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(1, 0, 1, 14);

        GridBagConstraints gv = new GridBagConstraints();
        gv.anchor  = GridBagConstraints.WEST;
        gv.fill    = GridBagConstraints.HORIZONTAL;
        gv.weightx = 1.0;
        gv.insets  = new Insets(1, 0, 1, 0);

        int row = 0;

        // Título da seção
        JLabel sectionTitle = new JLabel(title.toUpperCase());
        sectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        sectionTitle.setForeground(MUTED);
        sectionTitle.setBorder(new EmptyBorder(0, 0, 6, 0));
        gl.gridx = 0; gl.gridy = row;
        gl.gridwidth = 2;
        section.add(sectionTitle, gl);
        gl.gridwidth = 1;
        row++;

        // Linhas de dados
        for (InfoRow r : rows) {
            JLabel lbl = new JLabel(r.label() + ":");
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lbl.setForeground(MUTED);

            JComponent val;
            if (r.linkUrl() != null) {
                Color c = r.linkColor() != null ? r.linkColor() : ACCENT;
                val = buildLink(r.linkUrl(), c);
            } else {
                JLabel vl = new JLabel(r.plainValue() != null ? r.plainValue() : "—");
                vl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                vl.setForeground(TEXT_COLOR);
                val = vl;
            }

            gl.gridx = 0; gl.gridy = row;
            gv.gridx = 1; gv.gridy = row;
            section.add(lbl, gl);
            section.add(val, gv);
            row++;
        }

        // Embrulha para preencher largura total
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(SECTION_BG);
        wrapper.add(section, BorderLayout.NORTH);
        return wrapper;
    }

    /** Label estilizado como hyperlink clicável que abre o browser. */
    private JLabel buildLink(String url, Color color) {
        JLabel lbl = new JLabel("<html><u>" + url + "</u></html>");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(color);
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openUrl(url); }
            @Override public void mouseEntered(MouseEvent e) {
                lbl.setForeground(color.brighter());
            }
            @Override public void mouseExited(MouseEvent e) {
                lbl.setForeground(color);
            }
        });
        return lbl;
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    I18N.get("about.urlerror") + "\n" + url,
                    I18N.get("dialog.warning"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FOOTER
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        //p.setBackground(HEADER_BG);
        p.setBorder(new EmptyBorder(10, 24, 10, 16));

        JLabel copy = new JLabel("© " + java.time.Year.now() + " " + AUTHOR_NAME);
        copy.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        copy.setForeground(MUTED);
        p.add(copy, BorderLayout.WEST);

        JButton close = new JButton(I18N.get("mediainfo.close"));
        //close.setBackground(ACCENT);
        //close.setForeground(Color.BLACK);
        close.setFont(new Font("Segoe UI", Font.BOLD, 12));
       // close.setFocusPainted(false);
       // close.setBorderPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> dispose());
        p.add(close, BorderLayout.EAST);

        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FACTORY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Abre o dialog "Sobre".
     *
     * @param parent  Qualquer componente da janela pai.
     * @param logo    Logo do aplicativo como {@link BufferedImage}, ou {@code null}.
     */
    public static void show(Component parent, BufferedImage logo) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        new AboutDialog(owner, logo).setVisible(true);
    }
}