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
 * Este ataque RESPIRA:
 *
 *   1. RAJADA  — durante 'ticksDeRajada' ela dispara sem parar
 *   2. ALIVIO  — durante 'ticksDeAlivio' ela nao dispara nada
 *
 * A chefe fica PLANTADA no lugar o ataque inteiro (setParadoNoLugar), do
 * comeco ao fim — nao so durante a rajada. O motivo esta em (2) abaixo.
 *
 * O alivio nao e enfeite: e onde o jogador troca de corredor. Atravessar
 * um braco e impossivel de proposito (as balas se encostam), entao sem a
 * pausa ele ficaria preso no mesmo corredor a luta inteira.
 *
 * DOIS ERROS QUE ESTE ATAQUE JA TEVE, E POR QUE ELE E ASSIM AGORA
 * ---------------------------------------------------------------
 * A primeira versao gerava rotas impossiveis. Foram duas causas
 * somadas, e as duas correcoes estao aqui:
 *
 * (1) O GIRO ACUMULAVA. O angulo crescia 'giro' a cada disparo e so
 *     invertia no fim da rajada. Numa rajada de 45 disparos isso dava
 *     1,58 rad de varredura — na altura do jogador, 677 px de arco, mais
 *     que a largura do campo (600). Ou seja: o corredor em que ele
 *     estava SAIA DA TELA, encostando ele na parede lateral com um braco
 *     solido varrendo por cima. Nao havia jogada.
 *     Agora o angulo OSCILA, nao acumula:
 *
 *         anguloBase = amplitudeDoGiro * sin(2*PI * disparo / periodoDoGiro)
 *
 *     A varredura fica presa em 2*amplitude, e com 0,42 rad isso da uns
 *     344 px de arco — cabe folgado no campo, e todo corredor sempre
 *     volta. De quebra a inversao de sentido sai de graca: o seno ja
 *     vira sozinho.
 *
 * (2) A ORIGEM ANDAVA. As balas nasciam na chefe, e a chefe deriva 150 px
 *     pros lados. Isso importa mais do que parece, e o motivo e
 *     geometrico:
 *
 *       DUAS RETAS RADIAIS QUE SAEM DO MESMO PONTO NUNCA SE CRUZAM.
 *
 *     Enquanto todos os aneis sairem da MESMA origem, os bracos sao
 *     raios de um mesmo centro: eles podem ficar mais juntos ou mais
 *     separados, mas jamais formam um X — e todo corredor continua sendo
 *     um corredor ate a borda da tela. Bastou a origem andar 150 px pros
 *     bracos de rajadas diferentes se cruzarem e fecharem bolsoes sem
 *     saida. Por isso a chefe fica plantada o ataque INTEIRO, e nao so
 *     durante a rajada: soltar ela no alivio ja era suficiente pra
 *     reintroduzir o problema na rajada seguinte (a simulacao matava o
 *     jogador sempre no mesmo tick, na virada de uma rajada pra outra).
 *
 * A CONTA QUE MANTEM ISSO JOGAVEL
 * -------------------------------
 * O corredor e angular, mas o jogador anda em pixels. Na distancia r da
 * chefe, um corredor que gira 'w' radianos por tick desliza
 *
 *     v_corredor = w * r   pixels por tick
 *
 * e aqui a velocidade angular maxima e amplitudeDoGiro*2*PI/periodoDoGiro.
 * Com 0,42 e 520 da 0,005 rad/tick, ou 2,1 px/tick na altura do jogador,
 * contra os 4,0 que ele anda. O iniciar() imprime essa comparacao e a
 * varredura total no modo debug — se voce mexer nesses dois numeros,
 * olhe o console antes de concluir que ficou "so mais dificil".
 */
public class SomasDeRiemannSpell extends SpellCard {

    /** Quantos bracos saem por disparo. Mais bracos = corredores estreitos. */
    private final int bracos;

    /** Ticks entre um disparo e o proximo, dentro da rajada. */
    private final int cadencia;

    /** Ate onde o padrao gira pra cada lado, em radianos. */
    private final double amplitudeDoGiro;

    /** Ticks de uma vaivem completo do giro. */
    private final double periodoDoGiro;

    private final double velocidade;
    private final double raioBala;

    /** Deslocamento vertical do ponto de saida, a partir da chefe. */
    private final double alturaSaida;

    private final int ticksDeRajada;
    private final int ticksDeAlivio;

    /* --- estado --- */

    /** Angulo do primeiro braco. Vaivem, nao acumulo — ver o cabecalho. */
    private double anguloBase = 0;

    /** Quantos aneis ja sairam nesta rajada. E o 'x' do seno do giro. */
    private int disparosNaRajada = 0;

    public SomasDeRiemannSpell() {

        super("∑ f(x)Δx  Somas de Riemann",
              Config.getDouble("adriana.riemann.hp", 340),
              Config.getInt("adriana.riemann.duracao", 1800));

        this.bracos     = Math.max(3, Config.getInt("adriana.riemann.bracos", 12));
        this.cadencia   = Math.max(1, Config.getInt("adriana.riemann.cadencia", 6));

        this.amplitudeDoGiro = Config.getDouble("adriana.riemann.amplitudeDoGiro", 0.42);
        this.periodoDoGiro   = Math.max(1, Config.getDouble("adriana.riemann.periodoDoGiro", 520));
        this.velocidade = Config.getDouble("adriana.riemann.velocidadeBala", 1.9);
        this.raioBala   = Config.getDouble("adriana.riemann.raioBala", 6.0);
        this.alturaSaida = Config.getDouble("adriana.riemann.alturaSaida", 30);

        this.ticksDeRajada = Math.max(1, Config.getInt("adriana.riemann.ticksDeRajada", 270));
        this.ticksDeAlivio = Math.max(0, Config.getInt("adriana.riemann.ticksDeAlivio", 110));
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        anguloBase = 0;
        disparosNaRajada = 0;

        // Planta a chefe: a origem das balas tem que ser a mesma do
        // comeco ao fim, senao os bracos de rajadas diferentes se cruzam.
        chefe.setParadoNoLugar(true);

        if (Main.debugMode) {

            double raioAteOJogador = Main.CAMPO_H * 0.62;

            // Velocidade angular maxima do vaivem: derivada do seno no zero.
            double wMax = amplitudeDoGiro * 2 * Math.PI / periodoDoGiro;

            System.out.printf("[Riemann] corredor desliza no maximo %.2f px/tick na altura do "
                            + "jogador; ele anda %.2f (normal) / %.2f (foco)%n",
                              wMax * raioAteOJogador,
                              Config.getDouble("jogador.velocidade", 4.0),
                              Config.getDouble("jogador.velocidadeFoco", 1.75));

            // A conta que pegou o bug das rotas impossiveis: se a varredura
            // passar da largura do campo, o corredor sai da tela levando o
            // jogador junto ate a parede.
            double varredura = 2 * amplitudeDoGiro * raioAteOJogador;

            System.out.printf("[Riemann] varredura total do corredor: %.0f px "
                            + "(campo tem %d px) %s%n",
                              varredura, Main.CAMPO_W,
                              varredura > Main.CAMPO_W * 0.8 ? "<<< PERIGO: corredor sai da tela" : "ok");

            System.out.printf("[Riemann] corredor livre a %.0f px da chefe: %.0f px%n",
                              raioAteOJogador,
                              2 * Math.PI * raioAteOJogador / bracos - 2 * raioBala);
        }
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        int periodo = ticksDeRajada + ticksDeAlivio;
        int fase = t % periodo;

        boolean naRajada = fase < ticksDeRajada;

        if (fase == 0) {
            disparosNaRajada = 0;
        }

        // ALIVIO: nao atira nada. As balas ja no ar continuam andando.
        if (!naRajada || fase % cadencia != 0) {
            return;
        }

        // O som so a cada 3 aneis: com cadencia 6 seriam 10 disparos por
        // segundo, e o efeito viraria um chiado por cima da musica.
        dispararUmAnel(chefe, (fase / cadencia) % 3 == 0);

        disparosNaRajada++;

        // VAIVEM, nao acumulo. Ver o cabecalho da classe: e esta linha que
        // impede o corredor de sair da tela levando o jogador pro canto.
        anguloBase = amplitudeDoGiro
                   * Math.sin(2 * Math.PI * disparosNaRajada * cadencia / periodoDoGiro);
    }

    /** Solta a chefe ao sair do ataque, senao ela ficaria plantada pra sempre. */
    @Override
    public void encerrar(BossEnemy chefe) {
        chefe.setParadoNoLugar(false);
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
