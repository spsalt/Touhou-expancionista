package src;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * As tralhas que voam do estudante quando ele morre.
 *
 * Em Touhou, morrer nao e so o contador de vidas baixar: o personagem
 * explode numa chuva de particulas e os itens que ele carregava se
 * espalham pela tela. Isso resolve um problema concreto de leitura — no
 * meio de uma parede de bala, uma morte silenciosa passa despercebida, e
 * o jogador fica alguns segundos sem entender por que perdeu.
 *
 * Aqui as particulas sao as coisas do estudante: a mochila, o lapis, o
 * caderno, a calculadora, o cafe e um DISQUETE virgem.
 *
 * O disquete e proposital, e a lista de exercicios NAO esta aqui de
 * proposito: pelo Roteiro.txt (linha 7) ela ficou salva num computador
 * do LEPEC — e justamente por isso que ele atravessa o campus. Ele nunca
 * carrega a lista durante o jogo; carrega o disquete vazio em que
 * pretende salvar ela. Poe a lista voando do corpo dele no estagio 1 e a
 * premissa inteira da historia deixa de fazer sentido.
 *
 * FISICA: velocidade inicial sorteada num leque pra cima, gravidade
 * constante e giro proprio. E parabola pura, o mesmo que o
 * IntegralBullet faz, so que aqui nada colide com nada — destroco e
 * puramente decorativo, e por isso nem entra na lista de balas.
 */
public class Destroco {

    /** Os PNGs sorteaveis. Faltando algum, cai no desenho geometrico. */
    private static final String[] SPRITES = {
        "sprites/GFX/destrocos/mochila.png",
        "sprites/GFX/destrocos/lapis.png",
        "sprites/GFX/destrocos/caderno.png",
        "sprites/GFX/destrocos/calculadora.png",
        "sprites/GFX/destrocos/cafe.png",
        "sprites/GFX/destrocos/disquete.png",
    };

    private static final Random RNG = new Random();

    private double x, y;
    private double dx, dy;

    private final double gravidade;

    /** Rotacao atual e quanto ela cresce por tick. */
    private double angulo;
    private final double giro;

    /** Ticks de vida restantes e o total, pro fade. */
    private int vida;
    private final int vidaMaxima;

    private final String sprite;
    private final double escala;

    private boolean isAlive = true;

    private Destroco(double x, double y, double dx, double dy, String sprite, double escala) {

        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.sprite = sprite;
        this.escala = escala;

        this.gravidade = Config.getDouble("morte.gravidade", 0.22);

        this.angulo = RNG.nextDouble() * Math.PI * 2;
        this.giro   = (RNG.nextDouble() - 0.5) * Config.getDouble("morte.giroMaximo", 0.26);

        this.vidaMaxima = Math.max(1, Config.getInt("morte.ticksDeVida", 110));
        this.vida = this.vidaMaxima;
    }

    /**
     * Explode as tralhas do jogador a partir de (x, y).
     *
     * As direcoes sao sorteadas num leque PRA CIMA (de -PI ate 0, que na
     * tela aponta pro topo) e a gravidade traz tudo de volta. Sortear em
     * 360 graus mandaria metade das coisas direto pro chao no primeiro
     * frame, e o efeito perderia o arco que faz ele ser legivel.
     */
    public static void explodir(double x, double y) {

        int quantidade = Math.max(1, Config.getInt("morte.quantidadeDeDestrocos", 10));

        double forcaMin = Config.getDouble("morte.forcaMinima", 2.2);
        double forcaMax = Config.getDouble("morte.forcaMaxima", 5.4);
        double escala   = Config.getDouble("morte.escalaDosDestrocos", 1.0);

        // Comeca num ponto sorteado do vetor e PERCORRE ele em ordem, em
        // vez de sortear cada tralha. Sorteio independente repetiria a
        // mochila quatro vezes em metade das mortes; assim os seis
        // objetos sempre aparecem antes de qualquer um repetir.
        int primeiro = RNG.nextInt(SPRITES.length);

        for (int i = 0; i < quantidade; i++) {

            // Distribui o leque por igual e sorteia so um empurraozinho:
            // puro sorteio agruparia tudo de um lado em metade das mortes.
            double base = -Math.PI + (i + 0.5) * Math.PI / quantidade;
            double ang = base + (RNG.nextDouble() - 0.5) * 0.4;

            double forca = forcaMin + RNG.nextDouble() * (forcaMax - forcaMin);

            Main.destrocos.add(new Destroco(
                x, y,
                Math.cos(ang) * forca,
                Math.sin(ang) * forca,
                SPRITES[(primeiro + i) % SPRITES.length],
                escala
            ));
        }
    }

    public void tick() {

        x += dx;
        y += dy;

        dy += gravidade;

        angulo += giro;

        vida--;

        // Morre pelo tempo ou ao sair por baixo do campo. Nao ha limite
        // lateral de proposito: uma tralha saindo pela borda continua
        // fazendo sentido, e prender ela na parede pareceria bug.
        if (vida <= 0 || y > Main.CAMPO_Y + Main.CAMPO_H + 80) {
            isAlive = false;
        }
    }

    public void render(Graphics2D g) {

        // Some no ultimo terco da vida, nao a vida toda: assim ele fica
        // solido enquanto o olho ainda esta procurando o que aconteceu.
        double fracao = vida / (double) vidaMaxima;
        float alpha = (float) Math.max(0, Math.min(1, fracao * 3));

        java.awt.Composite composto = g.getComposite();

        if (alpha < 1f) {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, alpha));
        }

        BufferedImage img = Assets.get(sprite);

        if (img == null) {
            g.setColor(new Color(230, 220, 200));
            g.fillRect((int) x - 4, (int) y - 4, 8, 8);
        } else {

            int larg = (int) (img.getWidth() * escala);
            int alt  = (int) (img.getHeight() * escala);

            AffineTransform anterior = g.getTransform();

            g.rotate(angulo, x, y);
            g.drawImage(img, (int) (x - larg / 2.0), (int) (y - alt / 2.0), larg, alt, null);

            g.setTransform(anterior);
        }

        g.setComposite(composto);
    }

    public boolean isAlive() {
        return isAlive;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
