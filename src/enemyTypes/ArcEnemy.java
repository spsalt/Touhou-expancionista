package src.enemyTypes;

import src.Config;
import src.Main;

/**
 * PADRAO ARCO (25% das ondas).
 *
 * Entra por um dos lados na horizontal, faz uma curva de 90 graus e sai
 * pelo topo, atirando no jogador o caminho todo. A fase lanca varios em
 * fila (com atraso entre eles), entao eles desenham o mesmo arco um atras
 * do outro.
 *
 * O movimento e feito girando o ANGULO da velocidade, nao mexendo em x/y
 * direto:
 *
 *     x += cos(angulo) * velocidade
 *     y += sin(angulo) * velocidade
 *
 * Lembrando que na tela o Y cresce pra BAIXO, entao:
 *     angulo = 0      -> direita        angulo = PI     -> esquerda
 *     angulo = PI/2   -> baixo          angulo = -PI/2  -> cima
 *
 * Entrando pela esquerda o angulo vai de 0 ate -PI/2 (vira pra cima);
 * pela direita, vai de PI ate 3PI/2 (que tambem aponta pra cima).
 * Nos dois casos e um giro de 90 graus, so muda o sentido.
 *
 * O raio da curva sai de "velocidade / velocidade angular", entao a config
 * pede o RAIO (que da pra enxergar na tela) e a velocidade angular e
 * calculada a partir dele.
 */
public class ArcEnemy extends WaveEnemy {

    private double velocidade;

    /** Direcao atual do movimento, em radianos. */
    private double angulo;

    /** +1 ou -1: pra que lado o angulo gira. */
    private int sentidoRotacao;

    /** Radianos por tick. Derivado de velocidade / raio da curva. */
    private double velocidadeAngular;

    /** Quanto ja girou. Para de curvar ao chegar em PI/2. */
    private double rotacaoAcumulada = 0;

    /**
     * @param peloLadoEsquerdo true = entra pela esquerda indo pra direita
     * @param relY altura de entrada, em fracao da altura do campo (0 a 1)
     */
    public ArcEnemy(boolean peloLadoEsquerdo, double relY) {

        super(peloLadoEsquerdo ? Main.CAMPO_X - 40 : Main.CAMPO_X + Main.CAMPO_W + 40,
              Main.CAMPO_Y + Main.CAMPO_H * relY);

        this.velocidade = Config.getDouble("inimigo.arco.velocidade", 3.0);

        double raioDaCurva = Config.getDouble("inimigo.arco.raioDaCurva", 170);

        // Protecao contra raio 0 no .properties (daria divisao por zero).
        if (raioDaCurva < 1) {
            raioDaCurva = 1;
        }

        this.velocidadeAngular = velocidade / raioDaCurva;

        if (peloLadoEsquerdo) {
            this.angulo = 0;              // comeca indo pra direita
            this.sentidoRotacao = -1;     // 0 -> -PI/2 (cima)
        } else {
            this.angulo = Math.PI;        // comeca indo pra esquerda
            this.sentidoRotacao = +1;     // PI -> 3PI/2 (cima)
        }
    }

    @Override
    protected void mover() {

        // Curva ate completar 90 graus; depois segue reto pra cima.
        if (rotacaoAcumulada < Math.PI / 2) {

            // min(): o ultimo passo e recortado pra nao passar de 90 graus.
            double passo = Math.min(velocidadeAngular, Math.PI / 2 - rotacaoAcumulada);

            angulo += passo * sentidoRotacao;
            rotacaoAcumulada += passo;
        }

        x += Math.cos(angulo) * velocidade;
        y += Math.sin(angulo) * velocidade;
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

    public double getAngulo() {
        return angulo;
    }

    public void setAngulo(double angulo) {
        this.angulo = angulo;
    }

    public double getVelocidadeAngular() {
        return velocidadeAngular;
    }

    public void setVelocidadeAngular(double velocidadeAngular) {
        this.velocidadeAngular = velocidadeAngular;
    }

    public double getRotacaoAcumulada() {
        return rotacaoAcumulada;
    }
}
