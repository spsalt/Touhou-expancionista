package src.enemyTypes;

import java.awt.Color;

import src.Config;
import src.Main;
import src.Som;

/**
 * DISQUETE INFECTADO — o vetor do virus, solto pelo campus.
 *
 * "um virus antigo foi contraido por LEITURA DISQUETONICA nos
 *  laboratorios do DCO" (Roteiro.txt linha 5)
 *
 * Sao os disquetes que espalharam a coisa toda, agora boiando pelo
 * caminho. Descem devagar QUICANDO nas laterais do campo, e cada vez que
 * batem numa parede cospem um leque de balas.
 *
 * DE ONDE VEM A IDEIA
 * -------------------
 * E o kedama da serie: aquele bichinho redondo que desce flutuando em
 * ziguezague em vez de vir em linha reta. Ele existe pra quebrar o ritmo
 * de uma fase que so tem inimigo descendo — o jogador nao consegue tratar
 * a tela como colunas, porque tem uma coisa atravessando na diagonal.
 *
 * O TIRO SAI NO QUIQUE, e nao num relogio proprio. Isso importa: o
 * jogador consegue PREVER o disparo olhando a trajetoria (dá pra ver a
 * batida chegando), o que transforma um inimigo erratico num inimigo
 * legivel. Cadencia fixa aqui daria disparo saindo de qualquer lugar da
 * diagonal, sem aviso nenhum.
 *
 * Herda de WaveEnemy pra pegar HP, drop e pontuacao do resto da fase,
 * mas sobrescreve mover() e atirar() — os dois sao a personalidade dele.
 */
public class DisqueteEnemy extends WaveEnemy {

    /** Velocidade horizontal. O sinal diz pra que lado ele esta indo. */
    private double dx;

    /** Velocidade de descida. Constante: so o horizontal quica. */
    private final double dy;

    /** Quantas balas saem por quique. */
    private final int balasPorQuique;

    /** Abertura do leque do quique, em radianos. */
    private final double abertura;

    /** Giro proprio do sprite, so estetico. */
    private double angulo = 0;
    private final double giro;

    public DisqueteEnemy(double x, boolean paraDireita) {

        super(x, Main.CAMPO_Y - 30);

        double velH = Config.getDouble("inimigo.disquete.velocidadeH", 2.4);

        this.dx = paraDireita ? velH : -velH;
        this.dy = Config.getDouble("inimigo.disquete.velocidadeV", 1.1);

        this.balasPorQuique = Math.max(1, Config.getInt("inimigo.disquete.balasPorQuique", 5));
        this.abertura       = Config.getDouble("inimigo.disquete.abertura", 1.5);

        this.giro = Config.getDouble("inimigo.disquete.giro", 0.035) * (paraDireita ? 1 : -1);

        this.hp = Config.getDouble("inimigo.disquete.hp", 9.0);
        this.hpMaximo = this.hp;
        this.radius = Config.getDouble("inimigo.disquete.raio", 13.0);

        this.sprite = Config.getString("inimigo.disquete.sprite",
                                       "sprites/enemies/disquete_inimigo.png");
        this.escalaSprite = Config.getDouble("inimigo.disquete.escalaSprite", 2.2);

        this.corBala = new Color(120, 200, 255);

        this.pontos = Config.getInt("inimigo.disquete.pontos", 200);
        this.itens  = Config.getInt("inimigo.disquete.itens", 4);

        // Desliga o tiro por relogio do WaveEnemy: aqui quem dispara e o
        // quique. Ver atirar().
        this.cadenciaTiro = 0;
    }

    @Override
    protected void mover() {

        x += dx;
        y += dy;

        angulo += giro;

        // Quicou: inverte o sentido, prende na parede e cospe.
        //
        // O clamp e obrigatorio: sem ele o disquete pode entrar um pouco
        // na parede num frame e, se o dx for grande, sair de novo pro
        // lado errado no seguinte, ficando preso vibrando na borda.
        if (x < Main.CAMPO_X + radius) {

            x = Main.CAMPO_X + radius;
            dx = Math.abs(dx);
            quicar();

        } else if (x > Main.CAMPO_X + Main.CAMPO_W - radius) {

            x = Main.CAMPO_X + Main.CAMPO_W - radius;
            dx = -Math.abs(dx);
            quicar();
        }

        if (y > Main.CAMPO_Y + Main.CAMPO_H + 40) {
            isAlive = false;
        }
    }

    /**
     * O tiro sai do quique, entao atirar() nao faz nada por conta propria.
     *
     * Sobrescrito (em vez de so zerar a cadencia) pra ficar explicito pra
     * quem ler a classe: o disparo mora em quicar().
     */
    @Override
    protected void atirar() {
    }

    /** Leque de balas pro lado de dentro do campo, no momento da batida. */
    private void quicar() {

        // Ainda entrando na tela: quicar de fora seria tiro invisivel.
        if (y < Main.CAMPO_Y) {
            return;
        }

        Som.tocar(Som.TIRO_INIMIGO);

        // O leque aponta pra ONDE ELE PASSOU A IR (dx ja foi invertido),
        // inclinado pra baixo. Assim o disparo acompanha a batida em vez
        // de sair pelas costas.
        double base = Math.atan2(dy, dx);

        for (int i = 0; i < balasPorQuique; i++) {

            double f = (balasPorQuique == 1) ? 0.5 : i / (double) (balasPorQuique - 1);
            double ang = base - abertura / 2 + abertura * f;

            Main.bullets.add(new src.bulletTypes.IntegralBullet(
                x, y,
                Math.cos(ang) * velocidadeBala,
                Math.sin(ang) * velocidadeBala,
                0, 0,
                raioBala,
                true,
                corBala
            ));
        }
    }

    /**
     * Desenha o disquete GIRANDO devagar.
     *
     * Sem o giro ele desliza pela diagonal parecendo um adesivo colado na
     * tela; girando, le como um objeto boiando — que e o que justifica
     * ele quicar em vez de descer reto.
     *
     * O giro e so desenho: a colisao continua sendo o circulo de raio
     * 'radius' no centro, como em todo inimigo do jogo.
     */
    @Override
    public void render(java.awt.Graphics2D g) {

        java.awt.image.BufferedImage img = src.Assets.get(sprite);

        if (img == null) {
            super.render(g);
            return;
        }

        int lado = (int) (radius * 2 * escalaSprite);

        java.awt.geom.AffineTransform anterior = g.getTransform();

        g.rotate(angulo, x, y);
        g.drawImage(img, (int) (x - lado / 2.0), (int) (y - lado / 2.0), lado, lado, null);

        g.setTransform(anterior);

        renderBarraDeVida(g);

        if (Main.debugMode) {
            g.setColor(java.awt.Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius),
                       (int) (radius * 2), (int) (radius * 2));
        }
    }

    public double getAngulo() {
        return angulo;
    }
}
