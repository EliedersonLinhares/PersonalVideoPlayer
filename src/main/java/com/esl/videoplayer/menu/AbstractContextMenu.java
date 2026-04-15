package com.esl.videoplayer.menu;

import com.esl.videoplayer.configuration.RecentFilesManager;
import com.esl.videoplayer.Video.MainPanel;
import com.esl.videoplayer.VideoPlayer;
import com.esl.videoplayer.configuration.ConfigManager;
import com.esl.videoplayer.localization.I18N;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Locale;
import java.util.List;

public abstract class AbstractContextMenu implements I18N.LanguageChangeListener {

    protected final VideoPlayer videoPlayer;
    protected final MainPanel mainPanel;
    protected final ConfigManager configManager;
    protected RecentFilesManager recentFilesManager;

    protected AbstractContextMenu(MainPanel mainPanel, VideoPlayer videoPlayer) {
        this.mainPanel = mainPanel;
        this.videoPlayer   = videoPlayer;
        this.configManager = new ConfigManager();
    }

    public void setRecentFilesManager(RecentFilesManager manager) {
        this.recentFilesManager = manager;
    }

    // ── Ponto de entrada ──────────────────────────────────────────────────────

    /**
     * Constrói o menu, instala o MouseListener no MainPanel,
     * aplica os textos iniciais e registra o listener de idioma.
     */
    public final void install() {
        JPopupMenu menu = buildMenu();
        attachMouseListener(menu);
        updateTexts();
        I18N.addLanguageChangeListener(this);
    }

    public void cleanup() {
        I18N.removeLanguageChangeListener(this);
    }

    // ── Contrato para subclasses ──────────────────────────────────────────────

    /** Monta e devolve o JPopupMenu completo. */
    protected abstract JPopupMenu buildMenu();

    /** Atualiza todos os textos do menu com os valores de I18N atuais. */
    protected abstract void updateTexts();

    // ── Comportamentos comuns ─────────────────────────────────────────────────

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
    }

    /**
     * Instala um MouseListener que exibe o popup no botão direito,
     * tratando tanto mousePressed quanto mouseReleased (compatibilidade
     * com diferentes PLAFs e sistemas operacionais).
     */
    protected final void attachMouseListener(JPopupMenu menu) {
        mainPanel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { tryShow(e); }
            @Override public void mouseReleased(MouseEvent e) { tryShow(e); }

            private void tryShow(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e))
                    menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    /**
     * Cria um submenu de idioma com RadioButtons para EN e PT-BR,
     * já com o idioma atual selecionado.
     * Cada subclasse adiciona este menu onde quiser via {@code menu.add(buildLanguageMenu())}.
     */
    protected JMenu                langMenu;
    protected JRadioButtonMenuItem langEnItem;
    protected JRadioButtonMenuItem langPtItem;

    /**
     * Cria o submenu de idioma e guarda referências para atualização posterior.
     * Cada subclasse adiciona este menu onde quiser via {@code menu.add(buildLanguageMenu())}.
     */
    protected final JMenu buildLanguageMenu() {
        langMenu = new JMenu();
        langMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        Locale cur = I18N.getCurrentLocale();
        ButtonGroup grp = new ButtonGroup();

        langEnItem = new JRadioButtonMenuItem();
        langEnItem.setSelected("en".equals(cur.getLanguage()));
        langEnItem.addActionListener(e -> changeLanguage(Locale.of("en", "US")));

        langPtItem = new JRadioButtonMenuItem();
        langPtItem.setSelected("pt".equals(cur.getLanguage()));
        langPtItem.addActionListener(e -> changeLanguage(Locale.of("pt", "BR")));

        grp.add(langEnItem);
        grp.add(langPtItem);
        langMenu.add(langEnItem);
        langMenu.add(langPtItem);
        return langMenu;
    }

    /** Atualiza os textos do submenu de idioma. Chamado por updateTexts() das subclasses. */
    protected final void updateLanguageMenuTexts() {
        if (langMenu   != null) langMenu.setText(I18N.get("language.text"));
        if (langEnItem != null) langEnItem.setText(I18N.get("language.item1"));
        if (langPtItem != null) langPtItem.setText(I18N.get("language.item2"));
    }

    /**
     * Adiciona dinamicamente os arquivos recentes ao menu.
     * Deve ser chamado em {@code popupMenuWillBecomeVisible}.
     */
    protected final void addRecentFiles(JPopupMenu menu) {
        if (recentFilesManager == null) return;

        List<RecentFilesManager.RecentFile> files = recentFilesManager.getRecentFiles();
        if (files.isEmpty()) return;

        menu.addSeparator();

        JMenuItem title = new JMenuItem(I18N.get("recent.title"));
        title.setEnabled(false);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        menu.add(title);

        int idx = 1;
        for (RecentFilesManager.RecentFile rf : files) {
            JMenuItem item = new JMenuItem(rf.getIcon() + " " + idx + ". " + rf.getDisplayName());
            item.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            item.setToolTipText(rf.getFilePath());

            if (idx <= 5)
                item.setAccelerator(KeyStroke.getKeyStroke(
                        KeyEvent.VK_0 + idx, InputEvent.CTRL_DOWN_MASK));

            item.addActionListener(e -> {
                File f = new File(rf.getFilePath());
                if (f.exists()) {
                    videoPlayer.loadFromRecentFile(f);
                } else {
                    JOptionPane.showMessageDialog(mainPanel,
                            I18N.get("recent.notfound") + "\n" + rf.getFilePath(),
                            I18N.get("dialog.error"),
                            JOptionPane.ERROR_MESSAGE);
                    recentFilesManager.removeRecentFile(rf.getFilePath());
                }
            });

            menu.add(item);
            idx++;
        }

        menu.addSeparator();

        JMenuItem clear = new JMenuItem("🗑️ " + I18N.get("recent.clear"));
        clear.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(mainPanel,
                    I18N.get("recent.clear.confirm"),
                    I18N.get("dialog.confirm"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.YES_OPTION) {
                recentFilesManager.clearRecentFiles();
                JOptionPane.showMessageDialog(mainPanel,
                        I18N.get("recent.clear.success"),
                        I18N.get("dialog.success"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        menu.add(clear);
    }

    /**
     * Remove os itens de arquivos recentes (adicionados dinamicamente)
     * do menu a partir do primeiro separador após o último item fixo.
     * Deve ser chamado em {@code popupMenuWillBecomeInvisible/Canceled}.
     *
     * @param menu         O JPopupMenu a limpar.
     * @param anchorText   Texto do último item fixo antes dos recentes
     *                     (ex.: I18N.get("menu.playlist") ou I18N.get("language.text")).
     */
    protected final void removeRecentFiles(JPopupMenu menu, String anchorText) {
        Component[] comps = menu.getComponents();
        int anchorIdx = -1;

        for (int i = 0; i < comps.length; i++) {
            if (comps[i] instanceof JMenu m && anchorText.equals(m.getText())) {
                anchorIdx = i;
                break;
            }
        }

        if (anchorIdx >= 0) {
            while (menu.getComponentCount() > anchorIdx + 1)
                menu.remove(anchorIdx + 1);
        }
    }

    /** Troca o idioma e persiste a preferência. */
    protected final void changeLanguage(Locale locale) {
        I18N.setLocale(locale);
        configManager.saveLocale(locale);
    }

}
