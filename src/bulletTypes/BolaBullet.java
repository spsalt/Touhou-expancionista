package src.bulletTypes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

import src.Main;

/**
 * BOLA DE FUTEBOL — a bala das criaturas do anticodigo.
 *
 * Ela nao vai reta: sobe, perde velocidade e CAI em cima do jogador. E uma
 * parabola comum (velocidade inicial + gravidade constante), a mesma
 * matematica do IntegralBullet — a diferenca e que aqui a curva e o ataque
 * inteiro, e nao um detalhe.
 *
 * POR QUE UMA BALA QUE CAI MUDA O JOGO
 * ------------------------------------
 * Todo o resto do jogo dispara em linha reta ou em espiral: da pra ler a
 * direcao no instante em que a bala nasce e decidir na hora. Uma parabola
 * so revela onde vai cair DEPOIS de subir — o jogador tem que olhar pro
 * alto, calcular a queda e sair de lugar antes dela chegar, enquanto
 * desvia de tudo o mais que esta na tela. E o unico projetil do jogo que
 * cobra previsao em vez de reacao.
 *
 * A bola gira no ar, e o giro e proporcional a velocidade horizontal — uma
 * bola chutada com forca roda mais. Sem isso ela parece um adesivo
 * deslizando pela tela.
 *
 * Desenhada na mao (circulo branco + gomos pretos) porque nesse tamanho um
 * PNG de bola vira uma bolinha cinza sem leitura, e porque assim a bola
 * herda o giro de graca.
 */
public class BolaBullet extends Bullet {

    private double dx, dy;

    /** Aceleracao vertical. E a unica forca: nao ha atrito horizontal. */
    private final double gravidade;

    /** Rotacao atual e quanto ela cresce por tick. */
    private double angulo;
    private final double giro;

    /**
     * BOLA QUE FURA: atravessa a tela inteira sem ser parada por nada.
     *
     * E a bola gigante que o Clayton chuta pra fechar o ataque. Ela nao
     * quica, nao curva e nao morre no alto: vai em linha reta ate sair do
     * outro lado. O nome vem do que ela faz com o padrao — ela FURA o
     * ataque, abre um rasgo no meio de tudo e passa.
     */
    private boolean gigante = false;

    public void marcarComoGigante() {
        this.gigante = true;
    }

    public boolean isGigante() {
        return gigante;
    }

    public BolaBullet(double x, double y, double dx, double dy,
                      double gravidade, double radius) {

        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.gravidade = gravidade;
        this.radius = radius;
        this.hitPlayer = true;

        this.angulo = Math.random() * Math.PI * 2;

        // Rola pro lado pra onde foi chutada, e mais rapido quanto mais
        // forte o chute.
        this.giro = dx * 0.035;
    }

    @Override
    public void tick() {

        x += dx;
        y += dy;

        dy += gravidade;

        angulo += giro;

        // A GIGANTE VARRE AS BALAS QUE ENCOSTAM NELA.
        //
        // E o que "furar o ataque" quer dizer: ela nao desvia do padrao,
        // ela abre um rasgo nele. Sem isso a bola atravessaria a parede de
        // bala sem deixar marca e o momento nao significaria nada — e o
        // corredor que ela abre e justamente onde o jogador deve entrar.
        if (gigante) {
            varrerBalasNoCaminho();
        }

        // A MARGEM DE CIMA E MAIOR QUE A DOS LADOS.
        //
        // A bola PRECISA poder subir pra fora do campo e voltar — e a
        // graca dela. Com a margem padrao ela morria no alto do arco e o
        // ataque virava "bala que some no meio do caminho".
        if (y < Main.CAMPO_Y - 400) {
            isAlive = false;
            return;
        }

        if (x < Main.CAMPO_X - Main.MARGEM_SAIDA_BALA
         || x > Main.CAMPO_X + Main.CAMPO_W + Main.MARGEM_SAIDA_BALA
         || y > Main.CAMPO_Y + Main.CAMPO_H + Main.MARGEM_SAIDA_BALA) {
            isAlive = false;
            return;
        }

        if (Main.player != null) {

            double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

            if (dist <= radius + Main.player.getRadius()) {
                if (Main.player.levarDano()) {
                    isAlive = false;
                }
            }
        }
    }

    /**
     * Apaga as balas inimigas que a gigante encosta.
     *
     * Nao toca nas balas do jogador nem em outras bolas gigantes. E o
     * mesmo teste circulo-contra-circulo do resto do jogo.
     */
    private void varrerBalasNoCaminho() {

        for (int i = 0; i < Main.bullets.size(); i++) {

            Bullet b = Main.bullets.get(i);

            if (b == this || !b.isAlive() || !b.isHitPlayer()) {
                continue;
            }

            if (b instanceof BolaBullet && ((BolaBullet) b).isGigante()) {
                continue;
            }

            if (Main.getDist(x, y, b.getX(), b.getY()) <= radius + b.getRadius()) {
                b.setAlive(false);
            }
        }
    }

    @Override
    public void render(Graphics2D g) {

        AffineTransform anterior = g.getTransform();
        g.rotate(angulo, x, y);

        int d = (int) (radius * 2);

        // Corpo branco com uma sombra embaixo, pra ler como esfera e nao
        // como circulo chapado.
        g.setColor(new Color(225, 228, 235));
        g.fillOval((int) (x - radius), (int) (y - radius), d, d);

        g.setColor(new Color(170, 175, 190));
        g.fillOval((int) (x - radius * 0.75), (int) (y - radius * 0.1),
                   (int) (radius * 1.5), (int) (radius * 1.1));

        g.setColor(new Color(235, 238, 245));
        g.fillOval((int) (x - radius * 0.9), (int) (y - radius * 0.9),
                   (int) (radius * 1.4), (int) (radius * 1.4));

        // Os gomos: um pentagono no centro e outros em volta. Nao precisa
        // ser fiel — precisa dizer "bola de futebol" em 20 px.
        g.setColor(new Color(25, 25, 32));

        desenharGomo(g, x, y, radius * 0.42);

        for (int i = 0; i < 5; i++) {

            double a = angulo * 0 + i * 2 * Math.PI / 5 - Math.PI / 2;

            desenharGomo(g,
                         x + Math.cos(a) * radius * 0.72,
                         y + Math.sin(a) * radius * 0.72,
                         radius * 0.24);
        }

        Stroke sAnterior = g.getStroke();

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(40, 40, 50));
        g.drawOval((int) (x - radius), (int) (y - radius), d, d);

        g.setStroke(sAnterior);
        g.setTransform(anterior);
    }

    /** Um pentagono cheio, usado como gomo da bola. */
    private void desenharGomo(Graphics2D g, double cx, double cy, double r) {

        int[] px = new int[5];
        int[] py = new int[5];

        for (int i = 0; i < 5; i++) {

            double a = i * 2 * Math.PI / 5 - Math.PI / 2;

            px[i] = (int) (cx + Math.cos(a) * r);
            py[i] = (int) (cy + Math.sin(a) * r);
        }

        g.fillPolygon(px, py, 5);
    }

    /* =========================
            GETTERS
       ========================= */

    public double getDx() {
        return dx;
    }

    public double getDy() {
        return dy;
    }

    public double getAngulo() {
        return angulo;
    }
}
