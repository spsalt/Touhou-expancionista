package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Painel lateral de status (direita da tela) e moldura do campo de jogo.
 *
 * So desenha: nao tem tick, nao muda estado nenhum. Le tudo do Main.player.
 *
 * As posicoes verticais de cada secao sao CONSTANTES (Y_*) em vez de uma
 * variavel "linha" acumulada dentro de render(). E o que deixa possivel
 * expor getBotaoGptExpansaoBounds() pro Main saber onde fica o botao pra
 * testar clique do mouse, sem duplicar a conta em dois lugares.
 */
public class Hud {

    private static final Font FONTE_TITULO = new Font("Monospaced", Font.BOLD, 16);
    private static final Font FONTE_TEXTO  = new Font("Monospaced", Font.PLAIN, 14);

    /* --- layout vertical, em pixels a partir de Main.CAMPO_Y --- */

    private static final int Y_TITULO        = 40;
    private static final int Y_SUBTITULO      = 60;
    private static final int Y_PONTOS_LABEL   = 104;
    private static final int Y_PONTOS_VALOR   = 122;
    private static final int Y_VIDAS_LABEL    = 158;
    private static final int Y_VIDAS_ICONES   = 176;
    private static final int Y_GPT_LABEL      = 206;
    private static final int Y_GPT_BOTAO      = 218;
    private static final int Y_NIVEL_LABEL    = 270;
    private static final int Y_BARRA_XP       = 280;
    private static final int Y_AUTOFIRE       = 314;

    private static final int TAMANHO_BOTAO_GPT = 34;

    /** Onde comeca o painel lateral (logo depois do campo, com um respiro). */
    private final int painelX;

    public Hud() {
        this.painelX = Main.CAMPO_X + Main.CAMPO_W + 24;
    }

    public void render(Graphics2D g) {

        desenharMolduraDoCampo(g);

        if (Main.player == null) {
            return;
        }

        g.setFont(FONTE_TITULO);
        g.setColor(new Color(255, 220, 120));
        g.drawString("TOUHOU 67", painelX, Main.CAMPO_Y + Y_TITULO);

        g.setFont(FONTE_TEXTO);
        g.setColor(new Color(180, 180, 180));
        g.drawString("Expancionista", painelX, Main.CAMPO_Y + Y_SUBTITULO);

        g.setColor(Color.WHITE);
        g.drawString("Pontos", painelX, Main.CAMPO_Y + Y_PONTOS_LABEL);

        g.setColor(new Color(255, 230, 150));
        g.drawString(String.format("%09d", Main.player.getPontuacao()),
                     painelX, Main.CAMPO_Y + Y_PONTOS_VALOR);

        g.setColor(Color.WHITE);
        g.drawString("Vidas", painelX, Main.CAMPO_Y + Y_VIDAS_LABEL);
        desenharIcones(g, painelX, Main.CAMPO_Y + Y_VIDAS_ICONES, Main.player.getVidas(),
                       new Color(255, 90, 90));

        desenharSecaoGptExpansao(g);

        g.setFont(FONTE_TEXTO);
        g.setColor(Color.WHITE);
        g.drawString("Nivel " + Main.player.getLevel()
                   + (Main.player.isNivelMaximo() ? " MAX" : ""),
                     painelX, Main.CAMPO_Y + Y_NIVEL_LABEL);

        desenharBarraDeXp(g, painelX, Main.CAMPO_Y + Y_BARRA_XP);

        // Estado do tiro automatico: verde ligado, cinza desligado.
        boolean auto = Main.player.isAutofire();

        g.setColor(auto ? new Color(140, 255, 140) : new Color(110, 110, 110));
        g.drawString("AUTO " + (auto ? "ON " : "OFF") + "  [C]", painelX, Main.CAMPO_Y + Y_AUTOFIRE);

        desenharControles(g);
    }

    /**
     * Rotulo "GPT Expansion" + botao com a logo, clicavel, mostrando
     * quantas cargas restam. Fica escuro/acinzentado quando nao ha carga.
     */
    private void desenharSecaoGptExpansao(Graphics2D g) {

        g.setFont(FONTE_TEXTO);
        g.setColor(Color.WHITE);
        g.drawString("GPT Expansion", painelX, Main.CAMPO_Y + Y_GPT_LABEL);

        Rectangle botao = getBotaoGptExpansaoBounds();
        boolean disponivel = Main.player.getBombas() > 0;

        Color corBorda = disponivel ? new Color(255, 220, 120) : new Color(80, 80, 90);
        Color corFundo = disponivel ? new Color(70, 55, 15) : new Color(30, 30, 36);

        g.setColor(corFundo);
        g.fillRoundRect(botao.x, botao.y, botao.width, botao.height, 10, 10);

        g.setColor(corBorda);
        g.drawRoundRect(botao.x, botao.y, botao.width, botao.height, 10, 10);

        BufferedImage logo = Assets.get("sprites/GFX/gpt_logo.png");
        int margem = 5;

        if (logo != null) {

            // Sem carga: desenha em cinza (mistura pro cinza) em vez de
            // sumir, pra continuar dando pra ver ONDE clicar quando recarregar.
            g.drawImage(logo, botao.x + margem, botao.y + margem,
                       botao.width - margem * 2, botao.height - margem * 2, null);

            if (!disponivel) {
                g.setColor(new Color(20, 20, 26, 170));
                g.fillRoundRect(botao.x, botao.y, botao.width, botao.height, 10, 10);
            }
        }

        // Contador de cargas, ao lado do botao.
        g.setColor(disponivel ? new Color(255, 230, 150) : new Color(120, 120, 120));
        g.drawString("x" + Main.player.getBombas(), botao.x + botao.width + 10, botao.y + botao.height - 10);
    }

    /**
     * Onde o botao da GPT Expansion fica na tela, em coordenadas do painel
     * (as mesmas usadas pelo mouse do Swing). Publico pra o Main testar
     * clique sem duplicar a conta de posicao.
     */
    public Rectangle getBotaoGptExpansaoBounds() {
        return new Rectangle(painelX, Main.CAMPO_Y + Y_GPT_BOTAO, TAMANHO_BOTAO_GPT, TAMANHO_BOTAO_GPT);
    }

    private void desenharControles(Graphics2D g) {

        int rodape = Main.CAMPO_Y + Main.CAMPO_H - 108;

        g.setColor(new Color(140, 140, 140));
        g.drawString("Z  atirar",       painelX, rodape);
        g.drawString("X  foco",         painelX, rodape + 18);
        g.drawString("C  autofire",     painelX, rodape + 36);
        g.drawString("V / clique GPT",  painelX, rodape + 54);
        g.drawString("ESC pausar",      painelX, rodape + 72);
        g.drawString("F5 recarregar",   painelX, rodape + 90);

        if (Config.getBool("debug.permitirPularEstagio", true)) {
            g.setColor(new Color(180, 160, 100));
            g.drawString("F2 pular fase", painelX, rodape + 108);
        }
    }

    /** Contorno da arena, pra deixar claro onde as balas valem. */
    private void desenharMolduraDoCampo(Graphics2D g) {

        g.setColor(new Color(70, 70, 110));
        g.drawRect(Main.CAMPO_X - 1, Main.CAMPO_Y - 1, Main.CAMPO_W + 1, Main.CAMPO_H + 1);

        g.setColor(new Color(12, 12, 26));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
    }

    /** Desenha N quadradinhos lado a lado (vidas). */
    private void desenharIcones(Graphics2D g, int x, int y, int quantidade, Color cor) {

        g.setColor(cor);

        for (int i = 0; i < quantidade; i++) {
            g.fillRect(x + i * 18, y - 10, 12, 12);
        }
    }

    private void desenharBarraDeXp(Graphics2D g, int x, int y) {

        int largura = 140;
        int altura = 8;

        // No teto a barra fica cheia e dourada, em vez de travada em zero.
        boolean max = Main.player.isNivelMaximo();

        double progresso = max
                         ? 1.0
                         : Main.player.getXp() / (double) Main.player.getXpParaProximoNivel();

        progresso = Math.max(0, Math.min(1, progresso));

        g.setColor(new Color(40, 40, 60));
        g.fillRect(x, y, largura, altura);

        g.setColor(max ? new Color(255, 220, 120) : new Color(150, 255, 150));
        g.fillRect(x, y, (int) (largura * progresso), altura);

        g.setColor(new Color(90, 90, 110));
        g.drawRect(x, y, largura, altura);
    }
}
