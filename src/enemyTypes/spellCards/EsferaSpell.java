package src.enemyTypes.spellCards;

import java.awt.Color;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.Bullet3D;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD 4 - "Sólido de Revolução" (o ataque 3D)
 *
 * Solta uma ESFERA de balas em espaco tridimensional de verdade. A esfera
 * gira e se expande; o que aparece na tela e a projecao em perspectiva
 * dela (a matematica da projecao esta em Bullet3D).
 *
 * Tematicamente e o solido de revolucao — o volume que se obtem girando
 * uma curva em torno de um eixo, o capitulo seguinte ao de area sob a
 * curva. Combina com a fala dela no roteiro: "voce vai se tornar mais um
 * SOLIDO da nossa revolucao".
 *
 * DISTRIBUICAO DOS PONTOS NA ESFERA
 * ---------------------------------
 * O jeito ingenuo (sortear latitude e longitude uniformemente) AMONTOA os
 * pontos nos polos, porque as faixas de latitude perto do polo tem area
 * bem menor que as do equador. O resultado fica com dois tufos e um vazio
 * no meio — pessimo pra um bullet hell, onde a densidade tem que ser
 * previsivel.
 *
 * A correcao e a espiral de Fibonacci: distribui N pontos quase
 * perfeitamente uniformes na superficie usando o angulo aureo.
 *
 *     z      = 1 - 2*(i + 0.5)/N          <- altura, uniforme entre -1 e 1
 *     raio   = sqrt(1 - z*z)              <- raio do circulo naquela altura
 *     theta  = i * anguloAureo            <- gira o angulo aureo a cada ponto
 *
 * Como o angulo aureo e irracional, nenhuma volta cai em cima da anterior,
 * e os pontos se espalham por igual.
 */
public class EsferaSpell extends SpellCard {

    /** Angulo aureo em radianos: PI * (3 - sqrt(5)). */
    private static final double ANGULO_AUREO = Math.PI * (3 - Math.sqrt(5));

    private final int cadencia;
    private final int balasPorEsfera;
    private final double raioInicial;
    private final double velocidadeExpansao;
    private final double raioBala;
    private final double distanciaCamera;
    private final double velocidadeGiro;

    /** Quantas esferas saem por ciclo e de quantos em quantos ticks. */
    private final int esferasPorCiclo;
    private final int intervaloEntreEsferas;

    /** Distancia percorrida em que a bala termina de virar violeta. */
    private final double distanciaAteVioleta;

    /** Limites de vida das balas: alcance 3D, tempo e duracao do fade. */
    private final double alcanceMaximo;
    private final int vidaMaxima;
    private final int ticksDeFade;

    private int disparos = 0;

    public EsferaSpell() {

        super("∮  Sólido de Revolução",
              Config.getDouble("adriana.esfera.hp", 380),
              Config.getInt("adriana.esfera.duracao", 2000));

        this.cadencia           = Math.max(1, Config.getInt("adriana.esfera.cadencia", 150));
        this.balasPorEsfera     = Math.max(8, Config.getInt("adriana.esfera.balas", 55));
        this.raioInicial        = Config.getDouble("adriana.esfera.raioInicial", 30);
        this.velocidadeExpansao = Config.getDouble("adriana.esfera.velocidadeExpansao", 0.95);
        this.raioBala           = Config.getDouble("adriana.esfera.raioBala", 6.0);
        this.distanciaCamera    = Config.getDouble("adriana.esfera.distanciaCamera", 240);
        this.velocidadeGiro     = Config.getDouble("adriana.esfera.velocidadeGiro", 0.018);

        this.esferasPorCiclo       = Math.max(1, Config.getInt("adriana.esfera.esferasPorCiclo", 1));
        this.intervaloEntreEsferas = Math.max(1, Config.getInt("adriana.esfera.intervaloEntreEsferas", 22));
        this.distanciaAteVioleta   = Config.getDouble("adriana.esfera.distanciaAteVioleta", 260);

        this.alcanceMaximo = Config.getDouble("adriana.esfera.alcanceMaximo", 420);
        this.vidaMaxima    = Config.getInt("adriana.esfera.vidaMaximaTicks", 220);
        this.ticksDeFade   = Config.getInt("adriana.esfera.ticksDeFade", 45);
    }

    @Override
    public void iniciar(BossEnemy chefe) {
        disparos = 0;
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        // Quantas esferas saem por ciclo vem da config. Com 1 (padrao) sai
        // uma casca por vez; subindo o valor elas saem espacadas no tempo e,
        // como todas expandem na mesma velocidade, viram cascas concentricas
        // crescendo juntas com corredores entre elas.
        int noCiclo = t % cadencia;

        if (noCiclo % intervaloEntreEsferas != 0
                || noCiclo >= intervaloEntreEsferas * esferasPorCiclo) {
            return;
        }

        Som.tocar(Som.ADRIANA_REVOLUCAO);

        // Alterna o sentido do giro a cada esfera, pra o jogador nao
        // decorar um unico caminho de fuga.
        double giro = (disparos % 2 == 0) ? velocidadeGiro : -velocidadeGiro;

        double centroX = chefe.getX();
        double centroY = chefe.getY() + Config.getDouble("adriana.esfera.deslocamentoY", 60);

        for (int i = 0; i < balasPorEsfera; i++) {

            // --- espiral de Fibonacci na superficie da esfera ---
            double z = 1 - 2 * (i + 0.5) / balasPorEsfera;
            double raioNaAltura = Math.sqrt(Math.max(0, 1 - z * z));
            double theta = i * ANGULO_AUREO;

            // Vetor unitario apontando pra este ponto da superficie
            double ux = Math.cos(theta) * raioNaAltura;
            double uy = z;
            double uz = Math.sin(theta) * raioNaAltura;

            // A bala nasce na superficie e se afasta RADIALMENTE — por isso
            // a velocidade e o mesmo vetor unitario, so que escalado.
            Main.bullets.add(new Bullet3D(
                centroX, centroY,
                ux * raioInicial, uy * raioInicial, uz * raioInicial,
                ux * velocidadeExpansao, uy * velocidadeExpansao, uz * velocidadeExpansao,
                raioBala,
                distanciaCamera,
                giro,
                new Color(255, 50, 60),      // nasce vermelha...
                distanciaAteVioleta,         // ...e vai pro violeta ao se afastar
                alcanceMaximo, vidaMaxima, ticksDeFade
            ));
        }

        disparos++;
    }
}
