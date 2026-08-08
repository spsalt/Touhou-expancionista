package src.bulletTypes;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Random;

import src.Main;

/**
 * Peca de xadrez que anda pelo tabuleiro invisivel do campo de jogo.
 *
 * O QUE FAZ ELA SER "XADREZ" E O MOVIMENTO, NAO O DESENHO: em vez de
 * viajar em linha reta como toda bala do jogo, ela anda em LANCES —
 * desliza ate a casa de destino, PARA, pensa um instante e escolhe o
 * proximo lance seguindo as regras da peca dela:
 *
 *   TORRE   -> so na horizontal ou vertical
 *   BISPO   -> so na diagonal
 *   CAVALO  -> em L (2 casas num eixo + 1 no outro), o unico que "pula"
 *   RAINHA  -> reta ou diagonal, qualquer direcao
 *
 * A pausa entre lances e o que torna o padrao jogavel: o jogador tem uma
 * janela pra ler pra onde cada peca vai antes dela se mexer. Bala continua
 * viajando o tempo todo nao daria pra ler nada com 20 delas na tela.
 */
public class XadrezBullet extends Bullet {

    /** As pecas disponiveis, com o glifo Unicode de cada uma. */
    public enum Peca {

        TORRE('♜'),
        BISPO('♝'),
        CAVALO('♞'),
        RAINHA('♛');

        final char glifo;

        Peca(char glifo) {
            this.glifo = glifo;
        }
    }

    /** Tamanho da "casa" do tabuleiro, em pixels. */
    private final double casa;

    private final Peca peca;
    private final Color cor;

    /** Para onde esta deslizando agora. */
    private double alvoX, alvoY;

    /** Pixels por tick durante o deslize. */
    private final double velocidade;

    /** Ticks parada entre um lance e o proximo. */
    private final int pausaEntreLances;

    /** Contador da pausa. > 0 = parada, pensando no lance. */
    private int pausa;

    /** Quantos lances ainda pode dar antes de sair do tabuleiro. */
    private int lancesRestantes;

    /**
     * Chance de um lance vertical ir pra BAIXO (0 a 1).
     *
     * Sem esse vies as pecas sorteavam cima e baixo por igual, faziam um
     * passeio aleatorio e ficavam vagando no topo — coladas no chefe, sem
     * nunca ameacar o jogador. Com 0.78 elas ainda ziguezagueiam, mas o
     * saldo do passeio e sempre pra frente.
     */
    private final double viesParaBaixo;

    private final Random rng;

    public XadrezBullet(double x, double y, Peca peca, double casa,
                        double velocidade, int pausaEntreLances, int lances,
                        double raio, Color cor, long semente) {

        this.x = x;
        this.y = y;
        this.peca = peca;
        this.casa = casa;
        this.velocidade = velocidade;
        this.pausaEntreLances = Math.max(1, pausaEntreLances);
        this.lancesRestantes = Math.max(1, lances);
        this.radius = raio;
        this.cor = cor;
        this.hitPlayer = true;

        this.viesParaBaixo = Math.max(0, Math.min(1,
                src.Config.getDouble("clayton.xadrez.viesParaBaixo", 0.78)));

        this.rng = new Random(semente);

        this.alvoX = x;
        this.alvoY = y;
        this.pausa = this.pausaEntreLances;
    }

    @Override
    public void tick() {

        // Parada entre lances: e a janela de leitura do jogador.
        if (pausa > 0) {
            pausa--;

            if (pausa == 0) {
                escolherLance();
            }

            return;
        }

        deslizarAteOAlvo();

        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
            isAlive = false;
            return;
        }

        colidirComJogador();
    }

    /** Anda em direcao ao alvo; chegando, entra em pausa de novo. */
    private void deslizarAteOAlvo() {

        double dx = alvoX - x;
        double dy = alvoY - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Chegou (ou ja estava): fecha o lance.
        if (dist <= velocidade) {

            x = alvoX;
            y = alvoY;

            lancesRestantes--;

            if (lancesRestantes <= 0) {
                // Acabaram os lances: sai reto pra fora do tabuleiro.
                alvoY = Main.CAMPO_Y + Main.CAMPO_H + 200;
                lancesRestantes = 1;
                return;
            }

            pausa = pausaEntreLances;
            return;
        }

        x += dx / dist * velocidade;
        y += dy / dist * velocidade;
    }

    /**
     * Sorteia o proximo destino conforme as regras da peca.
     *
     * O alvo e sempre recortado pra dentro do campo: peca que mirasse pra
     * fora sairia do tabuleiro no primeiro lance e o padrao se esvaziaria.
     */
    private void escolherLance() {

        int[] passo = sortearPasso();

        alvoX = prender(x + passo[0] * casa, Main.CAMPO_X, Main.CAMPO_X + Main.CAMPO_W);
        alvoY = prender(y + passo[1] * casa, Main.CAMPO_Y, Main.CAMPO_Y + Main.CAMPO_H);
    }

    /** @return {colunas, linhas} do lance, em casas. */
    private int[] sortearPasso() {

        switch (peca) {

            case TORRE:
                // Uma direcao ortogonal, de 1 a 3 casas.
                return rng.nextBoolean()
                     ? new int[] { sinal() * (1 + rng.nextInt(3)), 0 }
                     : new int[] { 0, sinalV() * (1 + rng.nextInt(3)) };

            case BISPO: {
                // Diagonal: mesmo numero de casas nos dois eixos.
                int n = 1 + rng.nextInt(3);
                return new int[] { sinal() * n, sinalV() * n };
            }

            case CAVALO:
                // O L classico: 2 num eixo, 1 no outro.
                return rng.nextBoolean()
                     ? new int[] { sinal() * 2, sinalV() * 1 }
                     : new int[] { sinal() * 1, sinalV() * 2 };

            case RAINHA:
            default: {
                // Reta ou diagonal, qualquer direcao.
                int n = 1 + rng.nextInt(3);
                int dx = sinal() * n;
                int dy = sinalV() * n;

                int modo = rng.nextInt(3);

                if (modo == 0) return new int[] { dx, 0 };
                if (modo == 1) return new int[] { 0, dy };
                return new int[] { dx, dy };
            }
        }
    }

    /** Sinal horizontal: esquerda e direita tem a mesma chance. */
    private int sinal() {
        return rng.nextBoolean() ? 1 : -1;
    }

    /** Sinal vertical: puxado pra baixo, pra peca sempre avancar no campo. */
    private int sinalV() {
        return (rng.nextDouble() < viesParaBaixo) ? 1 : -1;
    }

    private static double prender(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void colidirComJogador() {

        if (!isAlive || Main.player == null) {
            return;
        }

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        if (dist <= radius + Main.player.getRadius() && Main.player.levarDano()) {
            isAlive = false;
        }
    }

    @Override
    public void render(Graphics2D g) {

        // Piscada durante a pausa: avisa que ela esta prestes a se mexer.
        boolean prestesAMover = pausa > 0 && pausa < 20 && (pausa / 4) % 2 == 0;

        g.setColor(prestesAMover ? Color.WHITE : cor);

        // O glifo e desenhado como TEXTO, entao o tamanho da fonte manda no
        // desenho — mas a colisao continua sendo o 'radius', menor que ele.
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (radius * 3.2)));

        String s = String.valueOf(peca.glifo);
        int larg = g.getFontMetrics().stringWidth(s);
        int alt = g.getFontMetrics().getAscent();

        g.drawString(s, (int) (x - larg / 2.0), (int) (y + alt / 2.5));
    }

    /* =========================
            GETTERS
       ========================= */

    public Peca getPeca() {
        return peca;
    }

    public boolean estaParada() {
        return pausa > 0;
    }
}
