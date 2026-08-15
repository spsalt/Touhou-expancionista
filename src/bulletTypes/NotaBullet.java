package src.bulletTypes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

import src.Main;

/**
 * A BALA BRANCA DE CONTORNO PRETO — a "nota" do RED RECOGNA.
 *
 * Ela existe pra ser DIFERENTE de tudo que ja voa no jogo. Todas as outras
 * balas sao um miolo claro dentro de uma borda colorida (vermelha, azul,
 * verde); esta e o contrario: corpo branco chapado com contorno PRETO.
 *
 * A inversao nao e capricho. No RED RECOGNA a tela inteira fica vermelha —
 * o sol, a coroa, os aneis, o fundo. Uma bala vermelha a mais ali dentro
 * nao seria uma bala a mais, seria textura. O branco com contorno preto e
 * a unica combinacao que sobrevive naquele fundo, porque nao compete por
 * matiz: ela compete por LUMINANCIA, que o olho separa primeiro.
 *
 * E como ela se parece com nada mais no jogo, o jogador aprende em um
 * segundo que ela obedece a outra regra — que e exatamente o ponto do
 * padrao que ela desenha (ver RedRecognaSpell.soltarNotas).
 */
public class NotaBullet extends Bullet {

    private double dx, dy;

    /** Aceleracao. Serve pras notas curvarem em vez de irem retas. */
    private final double d2x, d2y;

    /** Giro do losango interno. So estetico, mas e o que da "vida" a ela. */
    private double angulo;
    private final double giro;

    public NotaBullet(double x, double y, double dx, double dy,
                      double d2x, double d2y, double raio) {

        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.d2x = d2x;
        this.d2y = d2y;
        this.radius = raio;
        this.hitPlayer = true;

        this.angulo = Math.atan2(dy, dx);
        this.giro = 0.06;
    }

    @Override
    public void tick() {

        x += dx;
        y += dy;

        dx += d2x;
        dy += d2y;

        angulo += giro;

        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
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

    @Override
    public void render(Graphics2D g) {

        int d = (int) (radius * 2);

        // Sombra preta por baixo, deslocada. Num fundo vermelho claro o
        // contorno sozinho ainda somia; a sombra garante a separacao.
        g.setColor(new Color(0, 0, 0, 120));
        g.fillOval((int) (x - radius) + 2, (int) (y - radius) + 2, d, d);

        // Corpo branco chapado.
        g.setColor(Color.WHITE);
        g.fillOval((int) (x - radius), (int) (y - radius), d, d);

        // Contorno preto grosso: e ele que faz a bala existir por cima do
        // vermelho, e nao a cor de dentro.
        Stroke anterior = g.getStroke();

        g.setStroke(new BasicStroke(2.4f));
        g.setColor(new Color(15, 12, 18));
        g.drawOval((int) (x - radius), (int) (y - radius), d, d);

        // Losango girando no miolo, tambem preto. Custa quatro pontos e
        // resolve um problema real: bola branca lisa nao mostra que esta
        // girando, e sem giro ela parece parada mesmo se movendo rapido.
        int[] px = new int[4];
        int[] py = new int[4];

        for (int i = 0; i < 4; i++) {

            double a = angulo + i * Math.PI / 2;
            double r = radius * (i % 2 == 0 ? 0.55 : 0.34);

            px[i] = (int) (x + Math.cos(a) * r);
            py[i] = (int) (y + Math.sin(a) * r);
        }

        g.setColor(new Color(20, 16, 24));
        g.fillPolygon(px, py, 4);

        g.setStroke(anterior);
    }

    public double getDx() {
        return dx;
    }

    public double getDy() {
        return dy;
    }
}
