package src.enemyTypes.spellCards;

import java.awt.Color;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.IntegralBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD 3 - "∑ f(x)Δx  Somas de Riemann"
 *
 * O padrao classico de Touhou: a chefe cospe um monte de bolinhas em
 * TODAS as direcoes, mas nao de qualquer jeito — elas saem em BRACOS.
 * Cada braco e um fio continuo de balas, e entre um braco e outro sobra
 * um corredor curvo que o jogador percorre. Como a chefe gira devagar
 * enquanto atira, esses corredores varrem a tela e o jogador precisa ir
 * andando de lado pra se manter dentro de um.
 *
 * O TEMA: cada braco e um retangulo da soma de Riemann saindo da origem.
 * Quanto mais longe da chefe, mais "grossa" fica a particao — e por isso
 * o padrao e apertado perto dela e generoso perto do jogador.
 *
 * O CICLO (rajada -> alivio)
 * --------------------------
 * Diferente da parede que existia aqui antes, este ataque RESPIRA:
 *
 *   1. RAJADA  — durante 'ticksDeRajada' ela dispara sem parar
 *   2. ALIVIO  — durante 'ticksDeAlivio' ela nao dispara nada
 *
 * O alivio nao e enfeite: e onde o jogador troca de corredor. Sem essa
 * pausa ele ficaria preso no mesmo corredor a luta inteira, porque
 * atravessar um braco e impossivel (as balas se encostam de proposito).
 * A cada ciclo o giro INVERTE de sentido, entao o corredor onde ele
 * estava passa a andar pro outro lado e a pausa vira decisao, nao
 * descanso.
 *
 * POR QUE ISSO E JOGAVEL (a conta que define 'giro')
 * --------------------------------------------------
 * O corredor e angular, mas o jogador anda em pixels. Na distancia r da
 * chefe, um corredor que gira 'w' radianos por tick desliza
 *
 *     v_corredor = w * r   pixels por tick
 *
 * Se v_corredor passar da velocidade do jogador, nao existe desvio: o
 * corredor foge dele. Por isso o giro e propositalmente pequeno, e o
 * iniciar() imprime essa comparacao no modo debug — se voce aumentar
 * 'adriana.riemann.giro' no properties, olhe o console antes de achar
 * que ficou so "mais dificil".
 */
public class SomasDeRiemannSpell extends SpellCard {

    /** Quantos bracos saem por disparo. Mais bracos = corredores estreitos. */
    private final int bracos;

    /** Ticks entre um disparo e o proximo, dentro da rajada. */
    private final int cadencia;

    /** Quanto o padrao inteiro gira a cada disparo, em radianos. */
    private final double giro;

    private final double velocidade;
    private final double raioBala;

    /** Deslocamento vertical do ponto de saida, a partir da chefe. */
    private final double alturaSaida;

    private final int ticksDeRajada;
    private final int ticksDeAlivio;

    /* --- estado --- */

    /** Angulo do primeiro braco. Cresce (ou diminui) a cada disparo. */
    private double anguloBase = 0;

    /** +1 ou -1: pra que lado o padrao esta girando neste ciclo. */
    private int sentido = 1;

    /** Ciclo anterior, so pra detectar a virada e inverter o sentido. */
    private int cicloAnterior = 0;

    public SomasDeRiemannSpell() {

        super("∑ f(x)Δx  Somas de Riemann",
              Config.getDouble("adriana.riemann.hp", 340),
              Config.getInt("adriana.riemann.duracao", 1800));

        this.bracos     = Math.max(3, Config.getInt("adriana.riemann.bracos", 9));
        this.cadencia   = Math.max(1, Config.getInt("adriana.riemann.cadencia", 6));
        this.giro       = Config.getDouble("adriana.riemann.giro", 0.035);
        this.velocidade = Config.getDouble("adriana.riemann.velocidadeBala", 1.9);
        this.raioBala   = Config.getDouble("adriana.riemann.raioBala", 6.0);
        this.alturaSaida = Config.getDouble("adriana.riemann.alturaSaida", 30);

        this.ticksDeRajada = Math.max(1, Config.getInt("adriana.riemann.ticksDeRajada", 270));
        this.ticksDeAlivio = Math.max(0, Config.getInt("adriana.riemann.ticksDeAlivio", 110));
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        anguloBase = 0;
        sentido = 1;
        cicloAnterior = 0;

        if (Main.debugMode) {

            double raioAteOJogador = Main.CAMPO_H * 0.62;
            double wPorTick = (giro / cadencia);

            System.out.printf("[Riemann] corredor desliza %.2f px/tick na altura do jogador; "
                            + "ele anda %.2f (normal) / %.2f (foco)%n",
                              wPorTick * raioAteOJogador,
                              Config.getDouble("jogador.velocidade", 4.0),
                              Config.getDouble("jogador.velocidadeFoco", 1.75));

            System.out.printf("[Riemann] corredor livre a %.0f px da chefe: %.0f px%n",
                              raioAteOJogador,
                              2 * Math.PI * raioAteOJogador / bracos - 2 * raioBala);
        }
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        int periodo = ticksDeRajada + ticksDeAlivio;
        int ciclo = t / periodo;
        int fase = t % periodo;

        // Virou o ciclo: inverte o giro. O corredor onde o jogador estava
        // passa a andar pro lado contrario, entao a pausa que acabou de
        // acontecer era a hora de escolher outro.
        if (ciclo != cicloAnterior) {
            cicloAnterior = ciclo;
            sentido = -sentido;
        }

        // ALIVIO: nao atira nada. As balas ja no ar continuam andando.
        if (fase >= ticksDeRajada) {
            return;
        }

        if (fase % cadencia != 0) {
            return;
        }

        // O som so a cada 3 aneis: com cadencia 6 seriam 10 disparos por
        // segundo, e o efeito viraria um chiado por cima da musica.
        dispararUmAnel(chefe, (fase / cadencia) % 3 == 0);

        anguloBase += sentido * giro;
    }

    /**
     * Solta uma bala por braco, todas saindo do mesmo ponto e distribuidas
     * igualmente em 360 graus.
     *
     * Os disparos seguidos ficam colados de proposito (velocidade*cadencia
     * fica pouco abaixo do diametro da bala): assim cada braco e uma
     * PAREDE continua, e nao uma fileira de bolinhas com buraco no meio.
     * Se as balas se separarem, o ataque inteiro perde o sentido — o
     * jogador atravessa o braco em vez de procurar o corredor.
     */
    private void dispararUmAnel(BossEnemy chefe, boolean comSom) {

        if (comSom) {
            Som.tocar(Som.TIRO_INIMIGO);
        }

        double origemX = chefe.getX();
        double origemY = chefe.getY() + alturaSaida;

        for (int i = 0; i < bracos; i++) {

            double angulo = anguloBase + i * 2 * Math.PI / bracos;

            Main.bullets.add(new IntegralBullet(
                origemX,
                origemY,
                Math.cos(angulo) * velocidade,
                Math.sin(angulo) * velocidade,
                0, 0,
                raioBala,
                true,
                corDoBraco(i)
            ));
        }
    }

    /**
     * Cor de cada braco.
     *
     * Cada braco tem um matiz diferente (HSB, matiz = i/bracos). Nao e
     * enfeite: com a tela cheia de bala igual o olho perde a conta de onde
     * um braco termina e o outro comeca. Com cores distintas o corredor
     * fica obvio mesmo no meio da bagunca.
     */
    private Color corDoBraco(int i) {

        float matiz = (float) i / bracos;

        return Color.getHSBColor(matiz, 0.62f, 1.0f);
    }
}
