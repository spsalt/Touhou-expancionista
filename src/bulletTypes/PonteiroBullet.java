package src.bulletTypes;

import java.awt.Color;
import java.awt.Graphics2D;

import src.Main;
import src.enemyTypes.Enemy;

/**
 * PONTEIRO — a bala teleguiada do jogador.
 *
 * Fraca de proposito (dano bem menor que o tiro normal), mas PERSEGUE o
 * inimigo mais proximo. O papel dela no jogo nao e dano bruto: e limpar
 * inimigo que fugiu pro canto enquanto voce se concentra em desviar.
 *
 * O nome vem do tema do jogo — um ponteiro segue o endereco pra onde
 * aponta.
 *
 * COMO A PERSEGUICAO FUNCIONA
 * ---------------------------
 * Ela NAO teleporta a direcao pro alvo: gira o angulo da velocidade um
 * pouco por tick (taxaDeGiro). Isso importa por dois motivos:
 *
 *   - visualmente vira uma curva, nao uma quebra em L
 *   - da limite ao teleguiamento: alvo que se move rapido de lado consegue
 *     escapar, entao a bala nao e um "acerto garantido"
 *
 * O alvo e reescolhido a cada tick (o mais proximo naquele instante), o
 * que faz ela mudar de presa quando a atual morre.
 */
public class PonteiroBullet extends Bullet {

    /** Velocidade escalar (o modulo — a direcao vive no angulo). */
    private final double velocidade;

    /** Direcao atual, em radianos. */
    private double angulo;

    /** Quanto o angulo pode girar por tick, em radianos. */
    private final double taxaDeGiro;

    private final Color cor;

    /** Ticks de vida, pra deixar um rastro que encolhe. */
    private int t = 0;

    public PonteiroBullet(double x, double y, double angulo, double velocidade,
                          double taxaDeGiro, double raio, double dano, Color cor) {

        this.x = x;
        this.y = y;
        this.angulo = angulo;
        this.velocidade = velocidade;
        this.taxaDeGiro = taxaDeGiro;
        this.radius = raio;
        this.dano = dano;
        this.cor = cor;
        this.hitPlayer = false;   // bala do jogador

        this.sprite = src.Config.getString("tiro.ponteiro.sprite",
                                           "sprites/GFX/bala_ponteiro.png");
        this.opacidadeSprite = (float) src.Config.getDouble("tiro.ponteiro.opacidade", 0.62);
    }

    @Override
    public void tick() {

        Enemy alvo = Main.inimigoMaisProximo(x, y);

        if (alvo != null) {
            girarPara(alvo.getX(), alvo.getY());
        }

        x += Math.cos(angulo) * velocidade;
        y += Math.sin(angulo) * velocidade;

        t++;

        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
            isAlive = false;
        }
    }

    /**
     * Vira o angulo NA DIRECAO do alvo, no maximo taxaDeGiro por tick.
     *
     * A diferenca de angulo e normalizada pra faixa -PI..PI antes de
     * limitar. Sem isso, um alvo logo "atras" da bala daria uma diferenca
     * de quase 2*PI e ela giraria pelo caminho mais longo — dando uma
     * volta completa em vez de simplesmente virar pro outro lado.
     */
    private void girarPara(double alvoX, double alvoY) {

        double desejado = Math.atan2(alvoY - y, alvoX - x);
        double diferenca = desejado - angulo;

        while (diferenca > Math.PI)  diferenca -= 2 * Math.PI;
        while (diferenca < -Math.PI) diferenca += 2 * Math.PI;

        angulo += Math.max(-taxaDeGiro, Math.min(taxaDeGiro, diferenca));
    }

    @Override
    public void render(Graphics2D g) {

        // Rastro curto atras da bala: com ela curvando, o rastro e o que
        // deixa a trajetoria legivel no meio da tela cheia.
        for (int i = 3; i >= 1; i--) {

            double rx = x - Math.cos(angulo) * i * radius * 1.2;
            double ry = y - Math.sin(angulo) * i * radius * 1.2;
            double r = radius * (1 - i * 0.22);

            g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 46 - i * 12));
            g.fillOval((int) (rx - r), (int) (ry - r), (int) (r * 2), (int) (r * 2));
        }

        if (desenharSprite(g, angulo, 3.0)) {
            return;
        }

        // Sem o PNG: bolinha simples.
        g.setColor(cor);
        g.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));

        g.setColor(Color.WHITE);
        g.fillOval((int) (x - radius * 0.4), (int) (y - radius * 0.4),
                   (int) (radius * 0.8), (int) (radius * 0.8));
    }

    /* =========================
            GETTERS
       ========================= */

    public double getAngulo() {
        return angulo;
    }

    public int getT() {
        return t;
    }
}
