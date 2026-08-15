package src.bulletTypes;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

import src.Main;

/**
 * A bala que os cachorros da Adriana cospem: um pedaco de EQUACAO.
 *
 * Mecanicamente e uma bala em linha reta como qualquer outra — a colisao
 * continua sendo circulo contra circulo, com o raio informado. O que muda
 * e o desenho: em vez de bolinha, sai "dy/dx", "∫", "lim", "Δx". A ideia
 * e a mesma do XadrezBullet do Clayton, e a piada do roteiro e a mesma:
 * "DOS CACHORROS QUE SABEM CÁLCULO! DERIVEM ELE ATÉ O 0!" (linha 31).
 *
 * PORQUE O TEXTO NAO GIRA: um simbolo matematico de cabeca pra baixo
 * deixa de ser um simbolo matematico e vira rabisco. Aqui a legibilidade
 * do glifo vale mais que o realismo do giro — e o jogador precisa
 * distinguir a bala do fundo em um quadro de 1/60 de segundo.
 *
 * O RAIO E MENOR QUE O DESENHO de proposito (ver o fatorHitbox no
 * construtor de quem cria): equacao e um retangulo de texto, e cobrar
 * colisao pela caixa inteira puniria o jogador por encostar no espaco
 * vazio entre a barra da fracao e o "x".
 */
public class EquacaoBullet extends Bullet {

    /** Os pedacos de equacao que podem sair. Curtos de proposito. */
    public static final String[] TERMOS = {
        "dy/dx", "∫", "lim", "Δx", "f'(x)", "∂", "Σ", "dx", "≠0", "∞"
    };

    private final double dx, dy;

    private final String texto;
    private final Color cor;

    /** Corpo da fonte, em pixels. */
    private final int tamanhoDaFonte;

    public EquacaoBullet(double x, double y, double dx, double dy,
                         double raio, String texto, int tamanhoDaFonte, Color cor) {

        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.radius = raio;
        this.texto = texto;
        this.tamanhoDaFonte = tamanhoDaFonte;
        this.cor = cor;

        this.hitPlayer = true;
    }

    @Override
    public void tick() {

        x += dx;
        y += dy;

        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
            isAlive = false;
            return;
        }

        if (Main.player == null) {
            return;
        }

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        if (dist <= radius + Main.player.getRadius() && Main.player.levarDano()) {
            isAlive = false;
        }
    }

    @Override
    public void render(Graphics2D g) {

        g.setFont(new Font("Serif", Font.BOLD, tamanhoDaFonte));

        FontMetrics fm = g.getFontMetrics();

        int larg = fm.stringWidth(texto);
        int baseX = (int) (x - larg / 2.0);
        int baseY = (int) (y + fm.getAscent() / 2.5);

        // Contorno preto em volta (quatro deslocamentos): sem ele, uma
        // equacao clara em cima da foto clara do cenario some. Barato o
        // suficiente pra rodar em dezenas de balas por frame.
        g.setColor(new Color(0, 0, 0, 190));

        for (int dxo = -1; dxo <= 1; dxo += 2) {
            for (int dyo = -1; dyo <= 1; dyo += 2) {
                g.drawString(texto, baseX + dxo, baseY + dyo);
            }
        }

        g.setColor(cor);
        g.drawString(texto, baseX, baseY);

        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius),
                       (int) (radius * 2), (int) (radius * 2));
        }
    }

    public String getTexto() {
        return texto;
    }
}
