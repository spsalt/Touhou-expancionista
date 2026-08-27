package src;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Random;

/**
 * O ESTOURO DE COR das transformacoes.
 *
 * VERMELHO quando um chefe vira a forma maligna; ROXO quando o estudante
 * grita "ESPANDAAAAA" e ganha a armadura. Sao os dois momentos em que o
 * jogo muda de patamar, e ate agora os dois aconteciam em silencio visual
 * — o sprite simplesmente trocava.
 *
 * A cor nao e enfeite: ela e a mesma gramatica que o resto do jogo ja usa.
 * Roxo e a cor da alma e do Santo Java (o poder que vem do jogador);
 * vermelho e a cor da corrupcao, dos cachorros e das balas inimigas. Quem
 * jogou meia hora ja sabe o que cada uma significa antes de ler qualquer
 * texto.
 *
 * TRES CAMADAS, todas em cima do mesmo cronometro:
 *
 *   1. FLASH   — o campo inteiro tingido, sumindo rapido
 *   2. ONDA    — um anel que abre e afina
 *   3. FAISCAS — riscos radiais que desaceleram e apagam
 *
 * Nao colide com nada e nao interfere em jogabilidade: e uma lista
 * separada, como os Destroco. Passar isso pelos loops de colisao seria
 * trabalho jogado fora sessenta vezes por segundo.
 */
public class Explosao {

    /** Uma faisca: risco radial que sai rapido e vai freando. */
    private static class Faisca {

        double x, y;
        double dx, dy;
        Color cor;

        Faisca(double x, double y, double dx, double dy, Color cor) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.cor = cor;
        }
    }

    /**
     * As paletas.
     *
     * Tres tons cada, do mais claro (miolo) ao mais escuro (borda). Usar
     * uma paleta em vez de uma cor so e o que faz o estouro parecer fogo
     * e nao um circulo pintado.
     */
    public static final Color[] VERMELHA = {
        new Color(255, 235, 190),
        new Color(255, 140, 90),
        new Color(230, 40, 55),
        new Color(150, 15, 35),
    };

    public static final Color[] ROXA = {
        new Color(240, 225, 255),
        new Color(200, 140, 255),
        new Color(150, 60, 235),
        new Color(85, 20, 160),
    };

    private static final Random RNG = new Random();

    private final double x, y;
    private final Color[] paleta;

    private final Faisca[] faiscas;

    /** Ticks restantes e total, pra tudo poder ler o progresso. */
    private int vida;
    private final int vidaMaxima;

    /** Raio final da onda. */
    private final double raioDaOnda;

    /** Intensidade do flash de tela (0 a 1). */
    private final double forcaDoFlash;

    private boolean isAlive = true;

    private Explosao(double x, double y, Color[] paleta, double escala) {

        this.x = x;
        this.y = y;
        this.paleta = paleta;

        this.vidaMaxima = Math.max(1, Config.getInt("explosao.ticksDeVida", 46));
        this.vida = this.vidaMaxima;

        this.raioDaOnda = Config.getDouble("explosao.raioDaOnda", 300) * escala;
        this.forcaDoFlash = Config.getDouble("explosao.forcaDoFlash", 0.55) * escala;

        int quantas = Math.max(1, (int) (Config.getInt("explosao.faiscas", 46) * escala));

        this.faiscas = new Faisca[quantas];

        double velMin = Config.getDouble("explosao.velocidadeMinima", 4.0) * escala;
        double velMax = Config.getDouble("explosao.velocidadeMaxima", 13.0) * escala;

        for (int i = 0; i < quantas; i++) {

            // Angulos distribuidos por IGUAL, com um empurraozinho
            // sorteado. Sorteio puro deixaria buracos visiveis no circulo
            // — o olho enxerga falha de simetria muito mais rapido do que
            // enxerga regularidade.
            double ang = 2 * Math.PI * i / quantas + (RNG.nextDouble() - 0.5) * 0.35;
            double vel = velMin + RNG.nextDouble() * (velMax - velMin);

            faiscas[i] = new Faisca(x, y,
                                    Math.cos(ang) * vel,
                                    Math.sin(ang) * vel,
                                    paleta[RNG.nextInt(paleta.length)]);
        }
    }

    /* =========================
            FABRICAS
       ========================= */

    /** Transformacao de chefe: vermelho, e grande. */
    public static void vermelha(double x, double y) {
        Main.explosoes.add(new Explosao(x, y, VERMELHA,
                Config.getDouble("explosao.escalaDoChefe", 1.0)));
    }

    /**
     * O "ESPANDAAAAA" do jogador: na COR DA SKIN, um pouco menor.
     *
     * Antes era roxo fixo. O roxo era a cor do estudante, nao a cor da
     * mecanica — com um personagem laranja em cena, o momento mais
     * importante do roteiro continuava estourando roxo e passava a falar
     * de outra pessoa.
     */
    public static void daAura(double x, double y) {

        Main.explosoes.add(new Explosao(x, y, paletaDaAura(Skin.atual().getCorDaAura()),
                Config.getDouble("explosao.escalaDoJogador", 0.8)));
    }

    /**
     * Os quatro tons de fogo a partir de UMA cor.
     *
     * Do miolo pra borda: quase branco, claro, a cor cheia, e uma versao
     * escura pro contorno. E a mesma progressao das paletas escritas a mao
     * ali em cima — o que muda e que agora ela e derivada, entao um
     * personagem novo ganha um estouro coerente sem ninguem escolher
     * quatro cores na mao (e sem ninguem escolher quatro cores que nao
     * combinam).
     */
    private static Color[] paletaDaAura(Color base) {

        return new Color[] {
            Skin.variar(base, 0.10, 1.00, 255),
            Skin.variar(base, 0.45, 1.00, 255),
            Skin.variar(base, 1.00, 0.95, 255),
            Skin.variar(base, 1.00, 0.55, 255),
        };
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        double atrito = Config.getDouble("explosao.atrito", 0.90);

        for (Faisca f : faiscas) {

            f.x += f.dx;
            f.y += f.dy;

            // Freio exponencial: a faisca dispara e morre parando, que e o
            // que o olho espera de um estouro. Velocidade constante daria
            // "chuva de bala", nao explosao.
            f.dx *= atrito;
            f.dy *= atrito;
        }

        vida--;

        if (vida <= 0) {
            isAlive = false;
        }
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        double frac = vida / (double) vidaMaxima;

        desenharFlash(g, frac);
        desenharOnda(g, frac);
        desenharFaiscas(g, frac);
    }

    /**
     * O campo inteiro tingido por um instante.
     *
     * Ao QUADRADO do progresso: some muito mais rapido do que uma queda
     * linear, entao da o estalo sem deixar a tela colorida tempo demais —
     * que atrapalharia a leitura das balas.
     */
    private void desenharFlash(Graphics2D g, double frac) {

        int alpha = (int) (255 * forcaDoFlash * frac * frac);

        if (alpha <= 0) {
            return;
        }

        Color c = paleta[2];

        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, alpha)));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
    }

    /** Anel que abre e vai afinando. */
    private void desenharOnda(Graphics2D g, double frac) {

        double avanco = 1 - frac;
        double r = raioDaOnda * Math.sqrt(avanco);

        if (r < 1) {
            return;
        }

        Stroke anterior = g.getStroke();

        for (int i = 0; i < 3; i++) {

            double rr = r * (1 - i * 0.13);
            int alpha = (int) (200 * frac * (1 - i * 0.28));

            if (alpha <= 0) {
                continue;
            }

            Color c = paleta[i];

            g.setStroke(new BasicStroke((float) (2 + 6 * frac)));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            g.drawOval((int) (x - rr), (int) (y - rr), (int) (rr * 2), (int) (rr * 2));
        }

        g.setStroke(anterior);
    }

    /**
     * As faiscas, desenhadas como RISCOS e nao como pontos.
     *
     * O risco vai da posicao atual ate onde ela estava alguns frames
     * atras (reconstruido pela velocidade). E o mesmo truque de motion
     * blur do rastro do ponteiro: custa um drawLine e resolve o
     * serrilhado de uma particula rapida saltando pela tela.
     */
    private void desenharFaiscas(Graphics2D g, double frac) {

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int alpha = (int) (255 * Math.min(1, frac * 1.6));

        for (Faisca f : faiscas) {

            g.setColor(new Color(f.cor.getRed(), f.cor.getGreen(), f.cor.getBlue(), alpha));

            g.drawLine((int) f.x, (int) f.y,
                       (int) (f.x - f.dx * 2.2), (int) (f.y - f.dy * 2.2));
        }

        g.setStroke(anterior);
    }

    public boolean isAlive() {
        return isAlive;
    }
}
