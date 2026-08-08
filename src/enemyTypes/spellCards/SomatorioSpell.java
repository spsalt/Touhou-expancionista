package src.enemyTypes.spellCards;

import java.awt.Color;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.IntegralBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD 2 - "Somatório de Faltas"
 *
 * Desenha o sigma maiusculo (Σ) com balas e solta ele pra baixo.
 *
 * O GLIFO: diferente da integral, o Σ nao e uma curva suave — e uma
 * POLILINHA de 4 segmentos. Em coordenadas normalizadas (-1 a 1, com Y
 * crescendo pra baixo):
 *
 *        (-1,-1) ────────── (1,-1)      barra de cima
 *           \
 *            \                          diagonal descendo
 *          (0.1, 0)                     vertice do meio
 *            /
 *           /                           diagonal subindo
 *        (-1, 1) ────────── (1, 1)      barra de baixo
 *
 * As balas sao distribuidas por COMPRIMENTO ao longo dessa polilinha (e
 * nao por segmento), senao os trechos curtos ficariam com balas coladas e
 * os longos com buracos grandes o bastante pra passar sem desviar.
 *
 * O ATAQUE: dois sigmas por vez, um de cada lado, descendo em velocidade
 * constante e com uma leve aceleracao lateral pro centro — entao o corredor
 * seguro entre eles vai fechando enquanto eles caem.
 */
public class SomatorioSpell extends SpellCard {

    /** Vertices do Σ em coordenadas normalizadas (x, y), Y pra baixo. */
    private static final double[][] VERTICES = {
        { 1.0, -1.0 },
        { -1.0, -1.0 },
        { 0.1,  0.0 },
        { -1.0,  1.0 },
        { 1.0,  1.0 }
    };

    private final int cadencia;
    private final int balasPorSimbolo;
    private final double escalaX;
    private final double escalaY;
    private final double velocidade;
    private final double aceleracaoLateral;
    private final double raioBala;

    private int disparos = 0;

    /** Padroes de espalhamento, ciclados a cada disparo. */
    private static final IntegralBullet.PadraoEspalhamento[] PADROES = {
        IntegralBullet.PadraoEspalhamento.DIVERGENTE,
        IntegralBullet.PadraoEspalhamento.LEQUE,
        IntegralBullet.PadraoEspalhamento.ESPIRAL
    };

    public SomatorioSpell() {

        super("Σ  Somatório de Faltas",
              Config.getDouble("adriana.somatorio.hp", 300),
              Config.getInt("adriana.somatorio.duracao", 1800));

        this.cadencia          = Math.max(1, Config.getInt("adriana.somatorio.cadencia", 110));
        this.balasPorSimbolo   = Math.max(5, Config.getInt("adriana.somatorio.balasPorSimbolo", 34));
        this.escalaX           = Config.getDouble("adriana.somatorio.escalaX", 60);
        this.escalaY           = Config.getDouble("adriana.somatorio.escalaY", 70);
        this.velocidade        = Config.getDouble("adriana.somatorio.velocidadeBala", 1.9);
        this.aceleracaoLateral = Config.getDouble("adriana.somatorio.aceleracaoLateral", 0.012);
        this.raioBala          = Config.getDouble("adriana.somatorio.raioBala", 6.0);
    }

    @Override
    public void iniciar(BossEnemy chefe) {
        disparos = 0;
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (t % cadencia != 0) {
            return;
        }

        Som.tocar(Som.TIRO_INIMIGO);

        // Um sigma de cada lado do campo. Alternam de posicao a cada
        // disparo, pra o corredor seguro nunca ficar no mesmo lugar.
        double desvio = (disparos % 2 == 0) ? 0.22 : 0.30;

        desenharSigma(Main.CAMPO_X + Main.CAMPO_W * desvio,           chefe.getY(), +1);
        desenharSigma(Main.CAMPO_X + Main.CAMPO_W * (1 - desvio),     chefe.getY(), -1);

        disparos++;
    }

    /**
     * Monta um Σ de balas centrado em (cx, cy).
     *
     * @param sentidoLateral +1 empurra o simbolo pra direita conforme cai,
     *                       -1 pra esquerda (e o que fecha o corredor)
     */
    private void desenharSigma(double cx, double cy, int sentidoLateral) {

        double comprimentoTotal = comprimentoDaPolilinha();

        for (int i = 0; i < balasPorSimbolo; i++) {

            // Distancia percorrida ao longo do contorno, distribuida por igual.
            double alvo = comprimentoTotal * i / (double) (balasPorSimbolo - 1);

            double[] ponto = pontoAoLongoDoContorno(alvo);

            IntegralBullet bala = new IntegralBullet(
                cx + ponto[0] * escalaX,
                cy + ponto[1] * escalaY,
                0,
                velocidade,
                sentidoLateral * aceleracaoLateral,   // acelera de lado
                0,
                raioBala,
                true,
                new Color(120, 170, 255)
            );

            // O sigma desce inteiro e so depois se desfaz, no padrao do
            // disparo atual (ciclado, pra nao repetir onda apos onda).
            bala.configurarEspalhamento(
                Config.getInt("adriana.somatorio.ticksAteEspalhar", 85),
                PADROES[disparos % PADROES.length],
                i, balasPorSimbolo,
                Config.getDouble("adriana.somatorio.aberturaEspalhamento", 0.8),
                Config.getDouble("adriana.somatorio.ganhoVelocidade", 1.15)
            );

            Main.bullets.add(bala);
        }
    }

    /** Soma o comprimento dos 4 segmentos do Σ (em coordenadas normalizadas). */
    private double comprimentoDaPolilinha() {

        double total = 0;

        for (int i = 0; i < VERTICES.length - 1; i++) {
            total += distanciaEntreVertices(i, i + 1);
        }

        return total;
    }

    /**
     * Anda 'alvo' unidades ao longo do contorno e devolve onde parou.
     * E o que garante espacamento uniforme: sem isso, distribuir por
     * segmento deixaria a barra de cima (curta) apinhada e a diagonal
     * (longa) cheia de buracos.
     *
     * @return {x, y} normalizados
     */
    private double[] pontoAoLongoDoContorno(double alvo) {

        double percorrido = 0;

        for (int i = 0; i < VERTICES.length - 1; i++) {

            double comprimento = distanciaEntreVertices(i, i + 1);

            if (percorrido + comprimento >= alvo || i == VERTICES.length - 2) {

                // Fracao dentro DESTE segmento
                double f = (comprimento == 0) ? 0 : (alvo - percorrido) / comprimento;
                f = Math.max(0, Math.min(1, f));

                return new double[] {
                    VERTICES[i][0] + (VERTICES[i + 1][0] - VERTICES[i][0]) * f,
                    VERTICES[i][1] + (VERTICES[i + 1][1] - VERTICES[i][1]) * f
                };
            }

            percorrido += comprimento;
        }

        return new double[] { VERTICES[0][0], VERTICES[0][1] };
    }

    private double distanciaEntreVertices(int a, int b) {

        double dx = VERTICES[b][0] - VERTICES[a][0];
        double dy = VERTICES[b][1] - VERTICES[a][1];

        return Math.sqrt(dx * dx + dy * dy);
    }
}
