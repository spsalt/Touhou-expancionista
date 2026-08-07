package src.enemyTypes;

import src.Config;
import src.Main;

/**
 * PADRAO HORIZONTAL (25% das ondas).
 *
 * Atravessa a tela de um lado ao outro, na mesma altura, atirando no
 * jogador enquanto passa. E o padrao mais simples dos tres: serve pra
 * forcar o jogador a sair da coluna em que estava parado.
 *
 * Tem um balanco senoidal opcional no Y ("inimigo.horizontal.amplitudeOnda").
 * Com amplitude 0 (padrao) a travessia e uma linha reta; subindo o valor,
 * ele passa serpenteando.
 */
public class HorizontalEnemy extends WaveEnemy {

    private double velocidade;

    /** +1 = anda pra direita, -1 = anda pra esquerda. */
    private int sentido;

    /** Altura de referencia. O balanco oscila em volta dela. */
    private double yBase;

    private double amplitudeOnda;
    private double periodoOnda;

    /**
     * @param peloLadoEsquerdo true = entra pela esquerda e sai pela direita
     * @param relY altura da travessia, em fracao da altura do campo (0 a 1)
     */
    public HorizontalEnemy(boolean peloLadoEsquerdo, double relY) {

        super(peloLadoEsquerdo ? Main.CAMPO_X - 40 : Main.CAMPO_X + Main.CAMPO_W + 40,
              Main.CAMPO_Y + Main.CAMPO_H * relY);

        this.sentido = peloLadoEsquerdo ? +1 : -1;
        this.yBase = this.y;

        this.velocidade    = Config.getDouble("inimigo.horizontal.velocidade", 3.4);
        this.amplitudeOnda = Config.getDouble("inimigo.horizontal.amplitudeOnda", 0);
        this.periodoOnda   = Config.getDouble("inimigo.horizontal.periodoOnda", 90);

        // Protecao contra periodo 0 no .properties (divisao por zero).
        if (this.periodoOnda < 1) {
            this.periodoOnda = 1;
        }
    }

    @Override
    protected void mover() {

        x += velocidade * sentido;

        // Y e recalculado do zero a cada tick (nao acumulado), entao a
        // altura media nunca "escorrega" ao longo da travessia.
        y = yBase + amplitudeOnda * Math.sin(2 * Math.PI * t / periodoOnda);
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getVelocidade() {
        return velocidade;
    }

    public void setVelocidade(double velocidade) {
        this.velocidade = velocidade;
    }

    public int getSentido() {
        return sentido;
    }

    public void setSentido(int sentido) {
        this.sentido = sentido;
    }

    public double getYBase() {
        return yBase;
    }

    public void setYBase(double yBase) {
        this.yBase = yBase;
    }

    public double getAmplitudeOnda() {
        return amplitudeOnda;
    }

    public void setAmplitudeOnda(double amplitudeOnda) {
        this.amplitudeOnda = amplitudeOnda;
    }

    public double getPeriodoOnda() {
        return periodoOnda;
    }

    public void setPeriodoOnda(double periodoOnda) {
        this.periodoOnda = periodoOnda;
    }
}
