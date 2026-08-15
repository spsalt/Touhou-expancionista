package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * A tela de CONTINUE, que aparece quando as vidas acabam.
 *
 * E a mesma pergunta que a serie faz, e o texto e deliberadamente
 * desconfortavel: continuar recomeça de onde voce parou, mas o jogo
 * passa a saber que voce nao venceu por conta propria. Nao ha punicao
 * mecanica nenhuma — a punicao e o registro. Quem usa continue termina
 * numa tela de vitoria diferente.
 *
 * Por que a pergunta importa em vez de so continuar: sem ela, morrer nao
 * significa nada e todo o sistema de vidas, bomba e deathbomb perde o
 * peso. Com ela, o jogador escolhe conscientemente entre ver o final
 * "sujo" agora ou tentar o limpo do comeco.
 *
 * A tela nao roda logica de jogo: o mundo fica congelado atras dela, e
 * as balas ficam exatamente onde estavam. Quem retoma a partida e o
 * Main.continuarPartida().
 */
public class MenuDeContinue {

    private final String[] opcoes = { "Sim, quero continuar", "Não, voltar ao menu" };

    /** Comeca no "Nao" de proposito: continuar tem que ser uma escolha ativa. */
    private int selecionada = 1;

    /** Cronometro proprio, so pro brilho pulsar. */
    private int t = 0;

    /** Zera a selecao. Chamado toda vez que a tela aparece. */
    public void reiniciar() {
        selecionada = 1;
        t = 0;
    }

    public void tick() {

        t++;

        // As flags sao zeradas na hora de usar, senao um toque anda
        // varias casas (o tick roda 60x por segundo). Mesma solucao do Menu.
        if (Main.up) {

            Main.up = false;
            Som.tocar(Som.MENU_MOVER);
            selecionada = (selecionada + opcoes.length - 1) % opcoes.length;

        } else if (Main.down) {

            Main.down = false;
            Som.tocar(Som.MENU_MOVER);
            selecionada = (selecionada + 1) % opcoes.length;

        } else if (Main.enter || Main.z) {

            Main.enter = false;
            Main.z = false;

            Som.tocar(Som.MENU_OK);
            confirmar();
        }
    }

    private void confirmar() {

        if (selecionada == 0) {
            Main.continuarPartida();
        } else {
            Main.gameState = "GameOver";
        }
    }

    /**
     * Desenhada POR CIMA da cena congelada, com um veu escuro.
     *
     * Manter o campo visivel atras (em vez de cortar pra uma tela preta)
     * e proposital: o jogador ve exatamente a parede de bala que o matou
     * enquanto decide se quer voltar pra ela.
     */
    public void render(Graphics2D g) {

        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);

        int cx = Main.CAMPO_X + Main.CAMPO_W / 2;
        int cy = Main.CAMPO_Y + Main.CAMPO_H / 2;

        // --- titulo ---
        g.setFont(new Font("Monospaced", Font.BOLD, 34));
        centralizado(g, "CONTINUE?", cx, cy - 130, new Color(255, 210, 120));

        // --- a pergunta, em duas linhas pra caber no campo ---
        g.setFont(new Font("Monospaced", Font.PLAIN, 15));

        centralizado(g, "Você deseja continuar jogando, sabendo",
                     cx, cy - 78, new Color(220, 215, 235));
        centralizado(g, "que não conseguiu derrotar o jogo",
                     cx, cy - 56, new Color(220, 215, 235));
        centralizado(g, "normalmente?",
                     cx, cy - 34, new Color(220, 215, 235));

        // --- opcoes ---
        g.setFont(new Font("Monospaced", Font.BOLD, 20));

        for (int i = 0; i < opcoes.length; i++) {

            boolean ativa = (i == selecionada);

            // A ativa pulsa: num veu escuro, cor sozinha nao chama tanto.
            int brilho = ativa ? 200 + (int) (55 * Math.sin(t * 0.12)) : 150;

            Color cor = ativa
                      ? new Color(255, Math.min(255, brilho), 120)
                      : new Color(140, 140, 155);

            centralizado(g, (ativa ? "> " : "  ") + opcoes[i], cx, cy + 24 + i * 34, cor);
        }

        // --- consequencia, escrita sem rodeio ---
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));

        centralizado(g, "Continuar devolve " + Config.getInt("continue.vidas", 4)
                      + " vidas e libera os poderes no máximo,",
                     cx, cy + 112, new Color(160, 160, 180));
        centralizado(g, "mas o final não contará como uma vitória limpa.",
                     cx, cy + 132, new Color(160, 160, 180));

        if (Main.continuesUsados > 0) {
            centralizado(g, "continues usados nesta partida: " + Main.continuesUsados,
                         cx, cy + 164, new Color(200, 120, 120));
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        centralizado(g, "Setas para escolher · ENTER ou Z para confirmar",
                     cx, Main.CAMPO_Y + Main.CAMPO_H - 30, new Color(120, 120, 130));
    }

    /** Desenha o texto centrado em cx, com sombra pra ler sobre o campo. */
    private void centralizado(Graphics2D g, String texto, int cx, int cy, Color cor) {

        FontMetrics fm = g.getFontMetrics();
        int x = cx - fm.stringWidth(texto) / 2;

        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(texto, x + 2, cy + 2);

        g.setColor(cor);
        g.drawString(texto, x, cy);
    }

    /* =========================
            GETTERS
       ========================= */

    public int getSelecionada() {
        return selecionada;
    }
}
