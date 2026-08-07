package src.enemyTypes.spellCards;

import java.awt.Color;

import src.Config;
import src.Main;
import src.bulletTypes.IntegralBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD 1 - "Integral Indefinida"
 *
 * Desenha o simbolo da integral com balas e joga ele no jogador.
 *
 * O GLIFO: o simbolo ∫ e, geometricamente, UM PERIODO COMPLETO de uma
 * senoide desenhada na vertical. Parametrizando com s indo de 0 a 1:
 *
 *     x(s) = amplitude * sin(2*PI*s)      <- vai pra direita, volta, vai pra esquerda
 *     y(s) = (s - 0.5) * altura           <- desce em velocidade constante
 *
 * Em s=0 o seno vale 0, sobe ate 1 (gancho de cima), volta a 0 no meio
 * (a haste), desce ate -1 (gancho de baixo) e fecha em 0. Isso e o ∫.
 *
 * O ATAQUE: a cada disparo o simbolo inteiro nasce de uma vez, com as
 * balas paradas na forma dele, e todas partem juntas na direcao do
 * jogador. O jogador ve a integral se formando e tem tempo de escolher
 * por qual "curva" dela escapar.
 *
 * O simbolo tambem alterna de lado e gira um pouco a cada disparo, pra
 * duas ondas seguidas nao pedirem o mesmo desvio.
 */
public class IntegralSpell extends SpellCard {

    private final int cadencia;
    private final int balasPorSimbolo;
    private final double amplitude;
    private final double altura;
    private final double velocidade;
    private final double raioBala;

    /** Conta quantos simbolos ja sairam, pra variar o angulo entre eles. */
    private int disparos = 0;

    public IntegralSpell() {

        super("∫  Integral Indefinida",
              Config.getDouble("adriana.integral.hp", 260),
              Config.getInt("adriana.integral.duracao", 1800));

        this.cadencia        = Math.max(1, Config.getInt("adriana.integral.cadencia", 90));
        this.balasPorSimbolo = Math.max(2, Config.getInt("adriana.integral.balasPorSimbolo", 28));
        this.amplitude       = Config.getDouble("adriana.integral.amplitude", 34);
        this.altura          = Config.getDouble("adriana.integral.altura", 280);
        this.velocidade      = Config.getDouble("adriana.integral.velocidadeBala", 2.4);
        this.raioBala        = Config.getDouble("adriana.integral.raioBala", 6.0);
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

        // Gira o simbolo um pouco a cada disparo (angulo aureo-ish: nunca
        // repete a mesma inclinacao em disparos proximos).
        double inclinacao = disparos * 0.7;

        // A direcao base em que o simbolo inteiro viaja: no jogador.
        double alvoX = (Main.player != null) ? Main.player.getX() : chefe.getX();
        double alvoY = (Main.player != null) ? Main.player.getY() : chefe.getY() + 200;

        double dist = Main.getDist(alvoX, alvoY, chefe.getX(), chefe.getY());

        if (dist < 1) {
            dist = 1;
        }

        double dirX = (alvoX - chefe.getX()) / dist;
        double dirY = (alvoY - chefe.getY()) / dist;

        for (int i = 0; i < balasPorSimbolo; i++) {

            double s = i / (double) (balasPorSimbolo - 1);

            // Ponto do glifo ∫ em coordenadas locais
            double lx = Math.sin(2 * Math.PI * s) * amplitude;
            double ly = (s - 0.5) * altura;

            // Roda o glifo inteiro pela inclinacao deste disparo
            double cos = Math.cos(inclinacao);
            double sin = Math.sin(inclinacao);

            double rx = lx * cos - ly * sin;
            double ry = lx * sin + ly * cos;

            // Cor variando ao longo do tracado: ajuda a ler a curva como
            // um simbolo unico em vez de balas soltas.
            int verde = 90 + (int) (120 * s);

            Main.bullets.add(new IntegralBullet(
                chefe.getX() + rx,
                chefe.getY() + ry,
                dirX * velocidade,
                dirY * velocidade,
                0, 0,
                raioBala,
                true,
                new Color(230, verde, 60)
            ));
        }

        disparos++;
    }
}
