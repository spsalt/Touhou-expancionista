package src.bulletTypes;

import java.awt.*;

import src.Main;

/**
 * Bala com movimento por integracao: guarda velocidade (dx, dy) e
 * aceleracao (d2x, d2y) e integra a cada tick.
 *
 *   dx += d2x;   x += dx;
 *
 * Com isso da pra fazer quase todo padrao do jogo so escolhendo numeros:
 *   - aceleracao 0            -> linha reta
 *   - d2y positivo pequeno    -> bala que "cai" (gravidade)
 *   - d2 apontando pro centro -> curva
 * Nada de if especial por padrao: o padrao mora nos parametros.
 */
public class IntegralBullet extends Bullet {

    private double dx, dy;    // velocidade
    private double d2x, d2y;  // aceleracao

    private Color cor;

    /**
     * @param x,y       posicao inicial
     * @param dx,dy     velocidade inicial (pixels por tick)
     * @param d2x,d2y   aceleracao (pixels por tick ao quadrado)
     * @param radius    raio de colisao e de desenho
     * @param hitPlayer true se e bala inimiga (machuca o jogador)
     */
    public IntegralBullet(double x, double y, double dx, double dy,
                          double d2x, double d2y, double radius, boolean hitPlayer) {

        this(x, y, dx, dy, d2x, d2y, radius, hitPlayer,
             hitPlayer ? Color.RED : Color.CYAN);
    }

    /** Mesmo construtor, deixando escolher a cor da bala. */
    public IntegralBullet(double x, double y, double dx, double dy,
                          double d2x, double d2y, double radius, boolean hitPlayer,
                          Color cor) {

        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.d2x = d2x;
        this.d2y = d2y;
        this.radius = radius;
        this.hitPlayer = hitPlayer;
        this.cor = cor;
    }

    @Override
    public void tick() {

        // 1) integra a posicao e a velocidade
        x += dx;
        y += dy;

        dx += d2x;
        dy += d2y;

        // 2) morre ao sair do campo de jogo (com uma margem, senao balas que
        //    nascem na borda sumiriam no mesmo frame em que nascem)
        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
            this.isAlive = false;
            return;
        }

        // 3) colisao com o jogador. Quem decide o que fazer com o dano e o
        //    Player, nao a bala: aqui so avisamos.
        if (isAlive && hitPlayer && Main.player != null) {

            double dist = Main.getDist(this.x, this.y, Main.player.getX(), Main.player.getY());

            if (dist <= this.radius + Main.player.getRadius()) {

                // So consome a bala se o dano foi aplicado de fato
                // (se o jogador estiver invulneravel, a bala passa reto).
                if (Main.player.levarDano()) {
                    this.isAlive = false;
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {

        int d = (int) (radius * 2);

        // Miolo claro + borda colorida: fica legivel mesmo com a tela cheia de bala.
        g.setColor(cor);
        g.fillOval((int) (x - radius), (int) (y - radius), d, d);

        g.setColor(Color.WHITE);
        g.fillOval((int) (x - radius * 0.45), (int) (y - radius * 0.45),
                   (int) (radius * 0.9), (int) (radius * 0.9));
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getDx() {
        return dx;
    }

    public void setDx(double dx) {
        this.dx = dx;
    }

    public double getDy() {
        return dy;
    }

    public void setDy(double dy) {
        this.dy = dy;
    }

    public double getD2x() {
        return d2x;
    }

    public void setD2x(double d2x) {
        this.d2x = d2x;
    }

    public double getD2y() {
        return d2y;
    }

    public void setD2y(double d2y) {
        this.d2y = d2y;
    }

    public Color getCor() {
        return cor;
    }

    public void setCor(Color cor) {
        this.cor = cor;
    }
}
