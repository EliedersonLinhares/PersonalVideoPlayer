package com.esl.videoplayer.configuration;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class AppInfo {

    private static BufferedImage logo;

    /** Carrega o logo uma única vez do classpath (ex: /resources/logo.png). */
    public static BufferedImage getLogo() {
        if (logo == null) {
            try {
                logo = ImageIO.read(
                        Objects.requireNonNull(AppInfo.class.getResource("/img/icone128.png")));
            } catch (Exception e) {
                logo = null;   // sem logo: AboutDialog exibe só o texto
            }
        }
        return logo;
    }

    /** Permite injetar um logo já carregado (ex: o mesmo usado no JFrame). */
    public static void setLogo(BufferedImage img) {
        logo = img;
    }
}