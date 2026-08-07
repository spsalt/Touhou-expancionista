package src.enemyTypes;

import src.Config;
import src.Main;

/**
 * PADRAO PENDULO (50% das ondas).
 *
 * Entra pelo topo, DIMINUI a velocidade no meio da tela e segue em frente
 * ate sair por baixo. Nunca para de verdade: so fica lento na zona do meio,
 * que e onde ele atira.
 *
 * A desaceleracao e uma curva de sino (gaussiana) em cima da distancia ate
 * a "zona lenta", em vez de if/else por trecho:
 *
 *     fator = 1 - lentidaoMaxima * e^( -d^2 / (2*largura^2) )
 *
 *   - longe da zona (d grande)  -> e^(...) ~ 0  -> fator ~ 1     (velocidade cheia)
 *   - no centro da zona (d = 0) -> e^(...) = 1  -> fator ~ 0.15  (bem devagar)
 *
 * Fica suave nas duas pontas sem nenhum tratamento especial, e da pra
 * regular o "quanto" e o "onde" so por dois numeros no game.properties.
 */
public class PendulumEnemy extends WaveEnemy {

    /** Velocidade de queda quando esta longe da zona lenta. */
    private double velocidade;

    /** 0 = nao desacelera; 0.85 = cai pra 15% da velocidade no centro. */
    private double lentidaoMaxima;

    /** Altura (em pixels absolutos) onde ele fica mais lento. */
    private double yZonaLenta;

    /** Largura da zona lenta. Maior = desacelera mais cedo e por mais tempo. */
    private double larguraZonaLenta;

    /**
     * @param x coluna em que ele desce (fixa: o pendulo nao anda de lado)
     */
    public PendulumEnemy(double x) {

        // Nasce acima do topo do campo, pra entrar deslizando.
        super(x, Main.CAMPO_Y - 40);

        this.velocidade       = Config.getDouble("inimigo.pendulo.velocidade", 3.2);
        this.lentidaoMaxima   = Config.getDouble("inimigo.pendulo.lentidaoMaxima", 0.85);
        this.larguraZonaLenta = Config.getDouble("inimigo.pendulo.larguraZonaLenta", 110);

        double relY = Config.getDouble("inimigo.pendulo.zonaLentaRelY", 0.45);
        this.yZonaLenta = Main.CAMPO_Y + Main.CAMPO_H * relY;
    }

    @Override
    protected void mover() {

        double d = y - yZonaLenta;

        // Curva de sino: vale 1 no centro da zona e cai rapido pros lados.
        double sino = Math.exp(-(d * d) / (2 * larguraZonaLenta * larguraZonaLenta));

        double fator = 1.0 - lentidaoMaxima * sino;

        y += velocidade * fator;
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

    public double getLentidaoMaxima() {
        return lentidaoMaxima;
    }

    public void setLentidaoMaxima(double lentidaoMaxima) {
        this.lentidaoMaxima = lentidaoMaxima;
    }

    public double getYZonaLenta() {
        return yZonaLenta;
    }

    public void setYZonaLenta(double yZonaLenta) {
        this.yZonaLenta = yZonaLenta;
    }

    public double getLarguraZonaLenta() {
        return larguraZonaLenta;
    }

    public void setLarguraZonaLenta(double larguraZonaLenta) {
        this.larguraZonaLenta = larguraZonaLenta;
    }
}
