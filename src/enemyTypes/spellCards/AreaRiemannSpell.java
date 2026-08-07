package src.enemyTypes.spellCards;

import java.awt.Color;

import src.Config;
import src.Main;
import src.bulletTypes.IntegralBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD 3 - "Área Sob a Curva"
 *
 * Preenche a tela com os RETANGULOS DE RIEMANN de uma funcao — a soma que
 * aproxima a area sob uma curva, que e literalmente a definicao de integral
 * que a Adriana cobra em prova.
 *
 * COMO FUNCIONA
 * -------------
 * O campo e dividido em N colunas. Para cada coluna calcula-se f(x), e a
 * coluna e preenchida com balas do topo da curva ate embaixo — igual aos
 * retanguloszinhos que se desenha embaixo do grafico:
 *
 *      |    ___
 *      |  _|###|_                    cada # e uma bala
 *      | |#######|
 *      |_|#######|_
 *      +-------------
 *
 * A funcao f muda de disparo pra disparo (senoide, parabola, senoide
 * dupla), entao o formato do "buraco" seguro muda a cada onda.
 *
 * O QUE FAZ ISSO SER JOGAVEL: uma coluna em cada K e PULADA (a "falha na
 * integração"). Sem isso viraria uma parede solida sem saida. O jogador
 * precisa ler a curva e correr pra falha antes dela passar por ele.
 */
public class AreaRiemannSpell extends SpellCard {

    private final int cadencia;
    private final int colunas;
    private final int balasPorColuna;
    private final double velocidade;
    private final double raioBala;
    private final int colunaFalhaACada;

    private int disparos = 0;

    public AreaRiemannSpell() {

        super("∑f(x)Δx  Área Sob a Curva",
              Config.getDouble("adriana.area.hp", 340),
              Config.getInt("adriana.area.duracao", 1800));

        this.cadencia         = Math.max(1, Config.getInt("adriana.area.cadencia", 130));
        this.colunas          = Math.max(3, Config.getInt("adriana.area.colunas", 16));
        this.balasPorColuna   = Math.max(1, Config.getInt("adriana.area.balasPorColuna", 7));
        this.velocidade       = Config.getDouble("adriana.area.velocidadeBala", 1.7);
        this.raioBala         = Config.getDouble("adriana.area.raioBala", 6.5);
        this.colunaFalhaACada = Math.max(2, Config.getInt("adriana.area.colunaFalhaACada", 5));
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

        // Desloca qual coluna e a falha a cada disparo, senao o jogador
        // ficaria parado no mesmo corredor a onda inteira.
        int deslocamentoDaFalha = disparos % colunaFalhaACada;

        double larguraColuna = Main.CAMPO_W / (double) colunas;
        double alturaMaxima = Config.getDouble("adriana.area.alturaMaxima", 150);

        for (int c = 0; c < colunas; c++) {

            // A falha na integração: coluna vazia por onde da pra escapar.
            if ((c + deslocamentoDaFalha) % colunaFalhaACada == 0) {
                continue;
            }

            double xColuna = Main.CAMPO_X + larguraColuna * (c + 0.5);

            // f(x) normalizado em 0..1 — a altura desta barra
            double fx = avaliarFuncao(c / (double) (colunas - 1), disparos);
            int quantasBalas = 1 + (int) (fx * (balasPorColuna - 1));

            for (int b = 0; b < quantasBalas; b++) {

                // Empilha de baixo (a "base" da barra) pra cima.
                double yBala = chefe.getY() - b * (alturaMaxima / balasPorColuna);

                // Barras mais altas ficam mais claras: da pra "ler" o
                // grafico de relance e achar a falha mais rapido.
                int brilho = 120 + (int) (100 * fx);

                Main.bullets.add(new IntegralBullet(
                    xColuna,
                    yBala,
                    0,
                    velocidade,
                    0, 0,
                    raioBala,
                    true,
                    new Color(brilho, 80, brilho)
                ));
            }
        }

        disparos++;
    }

    /**
     * A funcao cuja area esta sendo somada. Troca a cada disparo pra o
     * contorno das barras nunca repetir duas ondas seguidas.
     *
     * @param s        posicao horizontal normalizada (0 a 1)
     * @param variante qual funcao usar
     * @return altura normalizada (0 a 1)
     */
    private double avaliarFuncao(double s, int variante) {

        switch (variante % 3) {

            case 0:
                // meia senoide: morro no meio
                return Math.sin(Math.PI * s);

            case 1:
                // parabola invertida: vale no meio, alto nas pontas
                return 4 * (s - 0.5) * (s - 0.5);

            default:
                // senoide dupla: dois morros
                return 0.5 + 0.5 * Math.sin(2 * Math.PI * s - Math.PI / 2);
        }
    }
}
