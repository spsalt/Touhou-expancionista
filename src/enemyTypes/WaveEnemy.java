package src.enemyTypes;

import java.awt.Color;

import src.Config;
import src.Som;

/**
 * Base dos inimigos que aparecem em onda.
 *
 * Junta tudo que os tres padroes tem em comum (HP, tamanho, ritmo e
 * velocidade do tiro, sprite, pontos, drop) num lugar so. As subclasses
 * ficam com uma responsabilidade unica: sobrescrever mover() e dizer
 * por onde o inimigo passa.
 *
 * Hierarquia:  Enemy  ->  WaveEnemy  ->  {Pendulum, Arc, Horizontal}Enemy
 *
 * Os valores compartilhados vem da secao "inimigo." do game.properties;
 * os especificos de cada padrao vem de "inimigo.pendulo.", "inimigo.arco."
 * e "inimigo.horizontal.".
 */
public abstract class WaveEnemy extends Enemy {

    /** Ticks entre um tiro e outro. */
    protected int cadenciaTiro;

    protected double velocidadeBala;
    protected double raioBala;

    /** Cor das balas deste inimigo. */
    protected Color corBala = new Color(255, 80, 80);

    protected WaveEnemy(double x, double y) {

        super(x, y,
              Config.getDouble("inimigo.hp", 6.0),
              Config.getDouble("inimigo.raio", 10.0));

        this.cadenciaTiro   = Config.getInt("inimigo.cadenciaTiro", 50);
        this.velocidadeBala = Config.getDouble("inimigo.velocidadeBala", 3.2);
        this.raioBala       = Config.getDouble("inimigo.raioBala", 5.0);

        this.pontos = Config.getInt("inimigo.pontosAoMorrer", 100);
        this.itens  = Config.getInt("inimigo.itensAoMorrer", 3);

        this.sprite       = Config.getString("inimigo.sprite", "sprites/enemies/inimigo.png");
        this.escalaSprite = Config.getDouble("inimigo.escalaSprite", 2.2);
    }

    /**
     * Comportamento padrao de tiro: uma bala mirada no jogador a cada
     * 'cadenciaTiro' ticks. Serve pros tres padroes; quem quiser algo
     * diferente e so sobrescrever.
     */
    @Override
    protected void atirar() {

        if (cadenciaTiro <= 0) {
            return;
        }

        // Comeca a atirar so depois de meia cadencia, pra o inimigo nao
        // disparar no mesmo frame em que nasce (ainda fora da tela).
        if (t < cadenciaTiro / 2) {
            return;
        }

        if (t % cadenciaTiro != 0) {
            return;
        }

        Som.tocar(Som.TIRO_INIMIGO);
        atirarMirado(velocidadeBala, raioBala, corBala);
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public int getCadenciaTiro() {
        return cadenciaTiro;
    }

    public void setCadenciaTiro(int cadenciaTiro) {
        this.cadenciaTiro = cadenciaTiro;
    }

    public double getVelocidadeBala() {
        return velocidadeBala;
    }

    public void setVelocidadeBala(double velocidadeBala) {
        this.velocidadeBala = velocidadeBala;
    }

    public double getRaioBala() {
        return raioBala;
    }

    public void setRaioBala(double raioBala) {
        this.raioBala = raioBala;
    }

    public Color getCorBala() {
        return corBala;
    }

    public void setCorBala(Color corBala) {
        this.corBala = corBala;
    }
}
