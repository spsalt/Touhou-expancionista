package src.enemyTypes.spellCards;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Random;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.IntegralBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD FINAL - "⊛ Optimum Path Forest"
 *
 * O ataque do PAPA IA. Uma abstracao, em bala, do classificador OPF
 * (Optimum Path Forest) do Papa e do Falcao — o algoritmo que o PAPA
 * "roda" pra decidir a que classe o jogador pertence.
 *
 * O ALGORITMO, EM UMA RESPIRADA
 * -----------------------------
 * O OPF ve o conjunto de dados como um grafo completo: cada amostra e um
 * no e o peso da aresta e a distancia entre as amostras. Alguns nos sao
 * eleitos PROTOTIPOS (as sementes, as raizes). Dai roda uma competicao:
 * cada prototipo tenta conquistar as outras amostras oferecendo o
 * caminho de menor CUSTO, e o custo de um caminho e
 *
 *     f(π) = max( peso de cada aresta do caminho )
 *
 * ou seja, um caminho vale pelo seu PIOR pulo (a funcao fmax). Quem
 * oferecer o menor custo leva a amostra, e ela passa a poder conquistar
 * outras a partir dali. No fim, cada prototipo e raiz de uma arvore de
 * caminhos otimos — juntas, uma FLORESTA — e cada amostra e classificada
 * pela raiz que a conquistou.
 *
 * COMO ISSO VIRA BULLET HELL
 * --------------------------
 * A competicao acontece na tela, uma conquista por vez, e cada uma tem
 * DOIS TEMPOS:
 *
 *   1. AVISO   — a aresta candidata acende pulsando, ligando o
 *                conquistador a amostra que ele esta prestes a tomar
 *   2. DISPARO — a aresta vira um feixe de balas, E a amostra recem
 *                conquistada cospe um leque MIRADO no jogador
 *
 * O aviso existe porque a primeira versao deste ataque era ilegivel:
 * feixes apareciam de pontos arbitrarios sem nada anunciando. Com a
 * aresta acendendo antes, da pra ver de onde vem o proximo tiro e sair
 * da frente. E o leque mirado existe porque, sem ele, dava pra passar o
 * ataque inteiro PARADO — os feixes seguem a geometria do grafo e nao a
 * do jogador, entao quase nunca acertavam quem nao se mexia. Agora toda
 * amostra classificada atira em quem esta olhando, e ficar parado e a
 * pior jogada possivel.
 *
 * O grafo e pequeno de proposito (3 raizes, 9 amostras): com vinte nos a
 * floresta virava sopa de linha. Poucos nos, bem espacados, deixam a
 * estrutura de arvore visivel — que e o ponto do ataque.
 *
 * A fila de conquista e por custo crescente, entao as arestas curtas
 * saem primeiro e as longas por ultimo: o jogador ve a floresta crescer
 * de dentro pra fora e consegue prever o que vem.
 *
 * O AQUECIMENTO — a curva que da forma a luta inteira
 * ---------------------------------------------------
 * O classificador nao comeca pronto: ele TREINA. No comeco do spell card
 * a floresta cresce devagar (aviso longo, pausa longa entre conquistas) e
 * quem realmente aperta o jogador sao os alunos corrompidos entrando em
 * ARCO pelas laterais, num fluxo constante e de baixa cadencia.
 *
 * Conforme o tempo passa, os dois lados trocam de lugar:
 *
 *     OPF        devagar  ------------------->  rapido
 *     laterais   constante ------------------>  raro
 *
 * A ideia e que a luta mude de assunto sem mudar de tela. No comeco o
 * problema e o CAMPO (gente entrando pelos lados o tempo todo, e uma
 * maquina lenta no fundo); no fim o problema e a MAQUINA (a floresta
 * fechando rapido, e o campo praticamente vazio em volta). Um jogador que
 * so aprendeu a lidar com um dos dois nao passa.
 *
 * O HP e o dobro do de um spell card normal justamente porque a primeira
 * metade e propositalmente mais lenta: sem isso, o ataque acabaria antes
 * de chegar na parte que ele quer mostrar.
 *
 * A conta e barata: umas duas dezenas de nos, Prim O(n²) rodado UMA vez
 * por ciclo em iniciarCiclo(). Nada de algoritmo por frame.
 */
public class OptimumPathForestSpell extends SpellCard {

    /** Um no do grafo: uma amostra a ser classificada. */
    private static class No {

        double x, y;

        /** Custo do caminho otimo ate aqui (a funcao fmax). */
        double custo = Double.MAX_VALUE;

        /** De quem esta amostra foi conquistada. -1 = ninguem/prototipo. */
        int pred = -1;

        /** Indice do prototipo que e raiz da arvore desta amostra. */
        int raiz = -1;

        boolean prototipo = false;
        boolean conquistado = false;

        No(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    /* --- ajustes --- */

    private final int prototipos;
    private final int amostras;

    /* --- o aquecimento: cada par vai do LENTO (inicio) ao RAPIDO (fim) --- */

    /** Ticks ate o classificador estar no ritmo maximo. */
    private final int ticksDeAquecimento;

    /** Ticks que a aresta candidata fica piscando antes de disparar. */
    private final int ticksDeAvisoInicial;
    private final int ticksDeAvisoFinal;

    /** Ticks de pausa entre uma conquista e o aviso da proxima. */
    private final int ticksEntreConquistasInicial;
    private final int ticksEntreConquistasFinal;

    /* --- os alunos entrando em arco pelas laterais --- */

    /** Ticks entre um aluno e o proximo, no COMECO (fluxo constante). */
    private final int intervaloDosArcosInicial;

    /** E no FIM, quando eles ja quase nao vem mais. */
    private final int intervaloDosArcosFinal;

    /** Ticks ate o proximo aluno entrar. */
    private int ateOProximoArco = 0;

    /** Alterna o lado de entrada, pra nao virem todos do mesmo. */
    private boolean arcoPelaEsquerda = true;

    /** Quantos alunos entram de uma vez. */
    private final int arcosPorLevada;

    /** Espalhamento vertical das entradas, em fracao do campo. */
    private final double dispersaoDosArcos;

    private final int ticksDeAlivio;

    private final int balasPorAresta;
    private final double velocidadeBala;
    private final double raioBala;
    private final double espacamentoNoFeixe;

    /** O leque que cada amostra conquistada cospe no jogador. */
    private final int balasDoNo;
    private final double aberturaDoNo;
    private final double velocidadeDoNo;

    private final int balasDoVeredito;
    private final double aberturaDoVeredito;

    /** Margem pra os nos nao nascerem colados na borda do campo. */
    private final double margem;

    /* --- estado do ciclo --- */

    private No[] nos = new No[0];

    /** Ordem em que os nos foram conquistados (indices), do Prim. */
    private int[] ordem = new int[0];

    /** Quantas conquistas ja foram animadas neste ciclo. */
    private int animadas = 0;

    /** > 0 = a aresta candidata esta piscando; ao chegar em 0, dispara. */
    private int avisando = 0;

    /** Ticks de pausa depois de uma conquista, antes do proximo aviso. */
    private int esperaAteProxima = 0;

    /** No que esta sendo anunciado/conquistado agora. -1 = nenhum. */
    private int alvoAtual = -1;

    /** Texto do que acabou de acontecer, mostrado no rodape. */
    private String ultimaJogada = "";

    /** Ticks de pausa restantes entre um ciclo e o proximo. */
    private int alivio = 0;

    /** Cronometro proprio, so pra animar a varredura do desenho. */
    private int tDesenho = 0;

    /**
     * 0 = classificador frio (lento, com o campo cheio de alunos).
     * 1 = classificador treinado (rapido, com o campo vazio).
     *
     * Guardado num campo porque tanto a logica quanto o desenho leem ele.
     */
    private double aquecimento = 0;

    private Random rng;

    /** O no do jogador e sempre o indice 0 — ver iniciarCiclo(). */
    private static final int NO_DO_JOGADOR = 0;

    public OptimumPathForestSpell() {

        super("⊛  Optimum Path Forest",
              Config.getDouble("papa.opf.hp", 520),
              Config.getInt("papa.opf.duracao", 2600));

        this.prototipos = Math.max(2, Config.getInt("papa.opf.prototipos", 3));
        this.amostras   = Math.max(4, Config.getInt("papa.opf.amostras", 9));

        this.ticksDeAquecimento = Math.max(1, Config.getInt("papa.opf.ticksDeAquecimento", 1500));

        this.ticksDeAvisoInicial = Math.max(1, Config.getInt("papa.opf.ticksDeAvisoInicial", 62));
        this.ticksDeAvisoFinal   = Math.max(1, Config.getInt("papa.opf.ticksDeAvisoFinal", 22));

        this.ticksEntreConquistasInicial = Math.max(0, Config.getInt("papa.opf.ticksEntreConquistasInicial", 40));
        this.ticksEntreConquistasFinal   = Math.max(0, Config.getInt("papa.opf.ticksEntreConquistasFinal", 10));

        this.arcosPorLevada    = Math.max(1, Config.getInt("papa.opf.arcosPorLevada", 2));
        this.dispersaoDosArcos = Config.getDouble("papa.opf.dispersaoDosArcos", 0.16);

        this.intervaloDosArcosInicial = Math.max(10, Config.getInt("papa.opf.intervaloDosArcosInicial", 46));
        this.intervaloDosArcosFinal   = Math.max(10, Config.getInt("papa.opf.intervaloDosArcosFinal", 520));

        this.ticksDeAlivio = Math.max(0, Config.getInt("papa.opf.ticksDeAlivio", 120));

        this.balasPorAresta     = Math.max(1, Config.getInt("papa.opf.balasPorAresta", 7));
        this.velocidadeBala     = Config.getDouble("papa.opf.velocidadeBala", 2.6);
        this.raioBala           = Config.getDouble("papa.opf.raioBala", 6.0);
        this.espacamentoNoFeixe = Config.getDouble("papa.opf.espacamentoNoFeixe", 17);

        this.balasDoNo      = Math.max(1, Config.getInt("papa.opf.balasDoNo", 5));
        this.aberturaDoNo   = Config.getDouble("papa.opf.aberturaDoNo", 0.55);
        this.velocidadeDoNo = Config.getDouble("papa.opf.velocidadeDoNo", 3.4);

        this.balasDoVeredito    = Math.max(1, Config.getInt("papa.opf.balasDoVeredito", 15));
        this.aberturaDoVeredito = Config.getDouble("papa.opf.aberturaDoVeredito", 1.3);

        this.margem = Config.getDouble("papa.opf.margem", 60);
    }

    /* =========================
            CICLO
       ========================= */

    @Override
    public void iniciar(BossEnemy chefe) {

        long seed = Config.getInt("papa.opf.seed", -1);
        rng = (seed < 0) ? new Random() : new Random(seed);

        iniciarCiclo(chefe);
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        tDesenho = t;
        aquecimento = Math.min(1.0, t / (double) ticksDeAquecimento);

        soltarArcos(t);

        if (alivio > 0) {
            alivio--;

            if (alivio == 0) {
                iniciarCiclo(chefe);
            }

            return;
        }

        // 1) piscando a aresta candidata
        if (avisando > 0) {

            avisando--;

            if (avisando == 0) {
                executarConquista();
                esperaAteProxima = rampa(ticksEntreConquistasInicial, ticksEntreConquistasFinal);
            }

            return;
        }

        // 2) respirando depois de disparar
        if (esperaAteProxima > 0) {
            esperaAteProxima--;
            return;
        }

        // 3) acabou a floresta?
        if (animadas >= ordem.length) {
            alivio = ticksDeAlivio;
            alvoAtual = -1;
            return;
        }

        // 4) anuncia a proxima conquista
        anunciarProximaConquista();
    }

    /**
     * Interpola entre o valor do comeco e o do fim conforme o aquecimento.
     *
     * Uma reta e suficiente: a curva do ataque ja e perceptivel porque os
     * DOIS lados se movem em sentidos opostos ao mesmo tempo. Suavizacao
     * extra aqui so tornaria o meio da luta indistinguivel das pontas.
     */
    private int rampa(int inicio, int fim) {
        return (int) Math.round(inicio + (fim - inicio) * aquecimento);
    }

    /**
     * Os alunos corrompidos entrando em ARCO pelas laterais.
     *
     * Fluxo constante e de baixa cadencia no comeco, rareando ate quase
     * nada no fim. Sao os mesmos ArcEnemy do estagio 1 — reaproveitar o
     * inimigo comum aqui e proposital: a ultima luta do jogo fecha o
     * circulo mostrando de novo o que o jogador enfrentou no primeiro
     * estagio, agora como pano de fundo de outra coisa.
     */
    private void soltarArcos(int t) {

        if (ateOProximoArco > 0) {
            ateOProximoArco--;
            return;
        }

        ateOProximoArco = rampa(intervaloDosArcosInicial, intervaloDosArcosFinal);

        // Perto do fim eles simplesmente param: um aluno solitario a cada
        // nove segundos nao acrescenta nada e ainda rouba atencao da
        // floresta, que a essa altura ja e o assunto da luta.
        if (aquecimento >= Config.getDouble("papa.opf.aquecimentoQueCortaArcos", 0.92)) {
            return;
        }

        double base = Config.getDouble("papa.opf.alturaDosArcos", 0.55);

        for (int i = 0; i < arcosPorLevada; i++) {

            // Alturas ESPALHADAS dentro da levada, e nao todas iguais.
            //
            // Com a mesma altura, dois alunos entrando pelos dois lados
            // desenhavam o mesmo arco espelhado e o jogador tratava a
            // levada como um objeto so. Espalhando, cada um cobre uma
            // faixa diferente e a levada vira duas ameacas.
            double f = (arcosPorLevada == 1) ? 0.5 : i / (double) (arcosPorLevada - 1);
            double relY = base + (f - 0.5) * dispersaoDosArcos;

            relY = Math.max(0.08, Math.min(0.85, relY));

            Main.enemies.add(new src.enemyTypes.ArcEnemy(arcoPelaEsquerda, relY));

            arcoPelaEsquerda = !arcoPelaEsquerda;
        }
    }

    /**
     * Escolhe o proximo no da fila e comeca a piscar a aresta dele.
     *
     * Prototipo nao tem aresta de entrada — ele so acende, sem aviso e
     * sem tiro, porque nao ha "de onde" o tiro viria.
     */
    private void anunciarProximaConquista() {

        alvoAtual = ordem[animadas];

        if (nos[alvoAtual].pred < 0) {
            executarConquista();
            esperaAteProxima = rampa(ticksEntreConquistasInicial, ticksEntreConquistasFinal);
            return;
        }

        avisando = rampa(ticksDeAvisoInicial, ticksDeAvisoFinal);

        Som.tocar(Som.OPF_SCAN);

        ultimaJogada = "raiz " + nos[alvoAtual].raiz
                     + " mira amostra " + alvoAtual
                     + "  ·  custo fmax " + (int) nos[alvoAtual].custo;
    }

    /**
     * Fim do ataque: leva os alunos junto.
     *
     * Sem isto, quem tivesse entrado em arco nos ultimos segundos
     * continuaria voando (e atirando) durante a cutscene de derrota do
     * PAPA — dano vindo de um ataque que ja acabou.
     */
    @Override
    public void encerrar(BossEnemy chefe) {

        for (int i = 0; i < Main.enemies.size(); i++) {

            if (Main.enemies.get(i) instanceof src.enemyTypes.ArcEnemy) {
                Main.enemies.get(i).setAlive(false);
            }
        }
    }

    /**
     * Sorteia um conjunto novo de amostras, elege os prototipos e roda o
     * OPF inteiro de uma vez. A partir dai o ciclo so ANIMA o resultado.
     *
     * Rodar tudo aqui (e nao um passo por frame) e de proposito: o
     * resultado do OPF depende de todas as amostras existirem desde o
     * comeco, entao calcular por partes daria uma floresta diferente da
     * que o algoritmo de verdade daria.
     */
    private void iniciarCiclo(BossEnemy chefe) {

        int total = 1 + prototipos + amostras;

        nos = new No[total];

        // O indice 0 e SEMPRE o jogador. E o que torna o ataque pessoal:
        // ele nao esta desviando da classificacao, ele esta DENTRO dela.
        nos[NO_DO_JOGADOR] = new No(
            Main.player != null ? Main.player.getX() : Main.CAMPO_X + Main.CAMPO_W / 2.0,
            Main.player != null ? Main.player.getY() : Main.CAMPO_Y + Main.CAMPO_H * 0.8);

        for (int i = 1; i < total; i++) {
            nos[i] = new No(
                Main.CAMPO_X + margem + rng.nextDouble() * (Main.CAMPO_W - 2 * margem),
                Main.CAMPO_Y + margem + rng.nextDouble() * (Main.CAMPO_H - 2 * margem));
        }

        // Os prototipos sao os indices 1..prototipos. Sortear a posicao ja
        // basta pra variar; nao precisa sortear tambem QUEM e prototipo.
        for (int i = 1; i <= prototipos; i++) {
            nos[i].prototipo = true;
            nos[i].custo = 0;
            nos[i].raiz = i;
        }

        rodarOPF();

        animadas = 0;
        esperaAteProxima = 0;
        avisando = 0;
        alivio = 0;
        alvoAtual = -1;
        ultimaJogada = "";
    }

    /**
     * O algoritmo em si: Prim adaptado com a funcao de custo fmax.
     *
     * A diferenca pro Prim classico esta numa linha — em vez de comparar
     * com o peso da aresta, compara-se com
     *
     *     max(custo[s], peso(s,t))
     *
     * ou seja, o caminho so piora quando encontra um pulo maior do que
     * qualquer um que ele ja tinha dado. E isso que faz um prototipo
     * conquistar toda uma regiao densa mesmo estando longe dela: basta
     * que exista uma cadeia de pulos curtos ate la.
     *
     * O(n²) com busca linear do minimo. Com ~20 nos isso e uma piscada, e
     * uma fila de prioridade so deixaria o codigo mais dificil de ler.
     */
    private void rodarOPF() {

        boolean[] fechado = new boolean[nos.length];
        ordem = new int[nos.length];

        int quantos = 0;

        for (int passo = 0; passo < nos.length; passo++) {

            // 1) o no aberto de menor custo vence a rodada
            int melhor = -1;

            for (int i = 0; i < nos.length; i++) {
                if (!fechado[i] && (melhor == -1 || nos[i].custo < nos[melhor].custo)) {
                    melhor = i;
                }
            }

            // Custo infinito = ninguem alcanca (nao acontece em grafo
            // completo, mas a guarda evita lixo se alguem mexer nisso).
            if (melhor == -1 || nos[melhor].custo == Double.MAX_VALUE) {
                break;
            }

            fechado[melhor] = true;
            ordem[quantos++] = melhor;

            // 2) o vencedor tenta conquistar todo mundo que sobrou
            for (int t = 0; t < nos.length; t++) {

                if (fechado[t]) {
                    continue;
                }

                double peso = Main.getDist(nos[melhor].x, nos[melhor].y, nos[t].x, nos[t].y);

                // AQUI mora o fmax.
                double proposta = Math.max(nos[melhor].custo, peso);

                if (proposta < nos[t].custo) {
                    nos[t].custo = proposta;
                    nos[t].pred = melhor;
                    nos[t].raiz = nos[melhor].raiz;
                }
            }
        }

        // Encolhe pro tamanho realmente preenchido.
        int[] certo = new int[quantos];
        System.arraycopy(ordem, 0, certo, 0, quantos);
        ordem = certo;
    }

    /**
     * Fecha a conquista anunciada: marca o no, solta o feixe pela aresta e
     * o leque mirado a partir da amostra recem classificada.
     */
    private void executarConquista() {

        int alvo = alvoAtual;

        animadas++;
        nos[alvo].conquistado = true;

        Som.tocar(Som.OPF_CONQUISTA);

        if (nos[alvo].pred < 0) {
            return;
        }

        Color cor = corDaRaiz(nos[alvo].raiz);

        dispararFeixe(nos[nos[alvo].pred], nos[alvo], cor);

        // O leque MIRADO: e o que impede o ataque de ser passado parado.
        // Sai do no que acabou de ser classificado, e nao da chefe, pra
        // ficar claro que quem atira e a arvore crescendo.
        dispararLequeMirado(nos[alvo], cor, balasDoNo, aberturaDoNo, velocidadeDoNo);

        // O veredito sobre o jogador: mais largo e mais rapido, saindo de
        // quem o conquistou. E o clima do ataque, e acontece uma vez so
        // por ciclo.
        if (alvo == NO_DO_JOGADOR) {

            Som.tocar(Som.OPF_VEREDITO);

            dispararLequeMirado(nos[nos[alvo].pred], cor.brighter(),
                                balasDoVeredito, aberturaDoVeredito, velocidadeDoNo * 1.15);

            ultimaJogada = "VEREDITO: você pertence à raiz " + nos[alvo].raiz;
        }
    }

    /**
     * O feixe: uma fileira de balas saindo de 'de' na direcao de 'para'.
     *
     * Elas nao param no no de destino — seguem em frente ate sair do
     * campo. Parar exatamente no no seria mais "certinho" em relacao ao
     * grafo, mas deixaria bala parada acumulando na tela, e o jogador
     * perderia a leitura de qual feixe ainda esta vindo.
     */
    private void dispararFeixe(No de, No para, Color cor) {

        double angulo = Math.atan2(para.y - de.y, para.x - de.x);

        double dx = Math.cos(angulo) * velocidadeBala;
        double dy = Math.sin(angulo) * velocidadeBala;

        for (int i = 0; i < balasPorAresta; i++) {

            // Nascem escalonadas PRA TRAS da origem: assim o feixe entra
            // no campo como uma linha que se estende, e nao como um bloco
            // que aparece inteiro de uma vez.
            double recuo = i * espacamentoNoFeixe;

            Main.bullets.add(new IntegralBullet(
                de.x - Math.cos(angulo) * recuo,
                de.y - Math.sin(angulo) * recuo,
                dx, dy,
                0, 0,
                raioBala,
                true,
                cor
            ));
        }
    }

    /**
     * Leque de balas saindo de um no, MIRADO na posicao do jogador no
     * instante do disparo.
     *
     * Mirar (e nao seguir) e o essencial: a bala nao corrige a rota, ela
     * so nasce apontada. Entao quem esta parado toma, quem se mexe
     * depois do disparo escapa — que e exatamente a leitura que a gente
     * quer cobrar neste ataque.
     */
    private void dispararLequeMirado(No de, Color cor, int quantas,
                                     double abertura, double velocidade) {

        if (Main.player == null) {
            return;
        }

        double base = Math.atan2(Main.player.getY() - de.y, Main.player.getX() - de.x);

        for (int i = 0; i < quantas; i++) {

            double f = (quantas == 1) ? 0.5 : i / (double) (quantas - 1);
            double ang = base - abertura / 2 + abertura * f;

            Main.bullets.add(new IntegralBullet(
                de.x, de.y,
                Math.cos(ang) * velocidade,
                Math.sin(ang) * velocidade,
                0, 0,
                raioBala * 0.8,
                true,
                cor
            ));
        }
    }

    /**
     * Uma cor por arvore. Matizes bem separados (o circulo dividido pelo
     * numero de prototipos) pra dar pra ver, de relance, qual regiao do
     * campo pertence a qual raiz — que e a saida do OPF.
     */
    private Color corDaRaiz(int raiz) {

        if (raiz < 1) {
            return new Color(190, 190, 200);
        }

        float matiz = (raiz - 1) / (float) prototipos;

        return Color.getHSBColor(matiz, 0.70f, 1.0f);
    }

    /* =========================
            RENDER
       ========================= */

    /**
     * Desenha o grafo por baixo das balas: amostras, prototipos e as
     * arestas ja conquistadas.
     *
     * Isso nao e enfeite — e a unica forma de o ataque ser justo. As
     * amostras ainda nao conquistadas aparecem apagadas, e sao elas que
     * dizem de onde os proximos feixes vao sair.
     */
    @Override
    public void render(Graphics2D g) {

        if (nos.length == 0) {
            return;
        }

        // O codigo vai por BAIXO de tudo: e fundo, nao informacao.
        desenharCodigoDeFundo(g, tDesenho);

        desenharVarredura(g, tDesenho);

        desenharArestas(g);
        desenharArestaAnunciada(g);
        desenharNos(g);
        desenharLegenda(g);
    }

    /**
     * A aresta que esta prestes a disparar, piscando.
     *
     * Desenhada grossa, na cor da raiz que vai conquistar, com um circulo
     * crescendo em cima da amostra alvo. Os dois juntos respondem as duas
     * perguntas que o jogador precisa fazer: DE ONDE vem e PRA ONDE vai.
     */
    private void desenharArestaAnunciada(Graphics2D g) {

        if (avisando <= 0 || alvoAtual < 0 || nos[alvoAtual].pred < 0) {
            return;
        }

        No alvo = nos[alvoAtual];
        No de = nos[alvo.pred];

        Color c = corDaRaiz(alvo.raiz);

        // Pisca mais rapido conforme o disparo se aproxima: o ritmo do
        // pisca-pisca ja conta quanto tempo falta, sem precisar de barra.
        int periodo = Math.max(2, avisando / 4);
        boolean aceso = (avisando / periodo) % 2 == 0;

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(aceso ? 3.2f : 1.4f));

        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), aceso ? 230 : 110));
        g.drawLine((int) de.x, (int) de.y, (int) alvo.x, (int) alvo.y);

        // Mira fechando em cima do alvo.
        // Usa o valor VIGENTE do aviso (ele encolhe conforme o
        // aquecimento), senao a mira fecharia rapido demais no fim e
        // devagar demais no comeco.
        double frac = avisando / (double) Math.max(1, rampa(ticksDeAvisoInicial, ticksDeAvisoFinal));
        int r = (int) (10 + 26 * frac);

        g.drawOval((int) (alvo.x - r), (int) (alvo.y - r), r * 2, r * 2);

        g.setStroke(anterior);
    }

    private void desenharArestas(Graphics2D g) {

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(1.4f));

        for (No n : nos) {

            if (!n.conquistado || n.pred < 0) {
                continue;
            }

            Color c = corDaRaiz(n.raiz);

            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 110));
            g.drawLine((int) nos[n.pred].x, (int) nos[n.pred].y, (int) n.x, (int) n.y);
        }

        g.setStroke(anterior);
    }

    /**
     * Os nos, desenhados como TERMINAIS e nao como bolinhas.
     *
     * Quadrado com cantos marcados, mais o indice em hexadecimal do lado.
     * A escolha e proposital: o resto do jogo fala em circulos (bala,
     * hitbox, item), entao dar forma retangular e tipografia de monitor
     * ao grafo separa "coisa que a IA esta calculando" de "coisa que
     * machuca". E o unico ataque do jogo rodado por uma maquina — tem que
     * parecer uma.
     */
    private void desenharNos(Graphics2D g) {

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));

        for (int i = 0; i < nos.length; i++) {

            No n = nos[i];

            int r = n.prototipo ? 9 : 6;

            if (!n.conquistado) {

                // Ainda nao classificada: so os CANTOS do quadrado, como
                // uma mira de camera esperando foco.
                g.setColor(new Color(150, 160, 180, 110));
                desenharCantos(g, n.x, n.y, r + 2);
                continue;
            }

            Color c = corDaRaiz(n.raiz);

            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 210));
            g.fillRect((int) (n.x - r), (int) (n.y - r), r * 2, r * 2);

            g.setColor(new Color(20, 22, 30, 190));
            g.drawRect((int) (n.x - r), (int) (n.y - r), r * 2, r * 2);

            // O prototipo ganha os colchetes de "raiz".
            if (n.prototipo) {
                g.setColor(Color.WHITE);
                desenharCantos(g, n.x, n.y, r + 5);
            }

            // Endereco do no, como saida de terminal.
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
            g.drawString(String.format("%02X", i), (int) (n.x + r + 3), (int) (n.y - r));
        }

        // O no do jogador, marcado com um X ate ser classificado.
        No meu = nos[NO_DO_JOGADOR];

        g.setColor(meu.conquistado ? new Color(255, 120, 120) : new Color(255, 255, 255, 150));
        g.drawLine((int) meu.x - 7, (int) meu.y - 7, (int) meu.x + 7, (int) meu.y + 7);
        g.drawLine((int) meu.x - 7, (int) meu.y + 7, (int) meu.x + 7, (int) meu.y - 7);
    }


    /**
     * O CODIGO DO PROPRIO OPF, rolando no fundo.
     *
     * Nao e texto decorativo nem lorem ipsum: e o algoritmo de treino do
     * classificador, no estilo da LibOPF (Papa & Falcao) — selecao de
     * prototipos pela MST, a IFT com custo fmax e a classificacao de uma
     * amostra nova. E literalmente o que este spell card executa em
     * rodarOPF(); quem parar pra ler o fundo esta lendo a implementacao
     * do ataque que esta desviando.
     *
     * Fica bem transparente de proposito. Bullet hell nao perdoa fundo
     * que compete com bala — a regra aqui e a mesma do Background: se o
     * jogador precisar de meio segundo a mais pra achar um projetil, o
     * enfeite passou do ponto.
     */
    private static final String[] CODIGO = {
        "/* ---------------------------------------------------- */",
        "/*  Optimum-Path Forest  -  treinamento supervisionado  */",
        "/*  Papa & Falcao.  Custo de caminho: f_max             */",
        "/* ---------------------------------------------------- */",
        "",
        "/* o caminho vale pelo seu PIOR pulo, e nao pela soma */",
        "float fmax(Graph *g, int s, int t) {",
        "    return MAX(g->pathval[s], dist(g->node[s], g->node[t]));",
        "}",
        "",
        "/* 1) prototipos = nos vizinhos na MST que caem   */",
        "/*    em classes diferentes: a fronteira real     */",
        "void selecionarPrototipos(Graph *g) {",
        "    mst = primMinimumSpanningTree(g);",
        "    for (int s = 0; s < g->nnodes; s++) {",
        "        int t = mst->pred[s];",
        "        if (t != NIL && g->node[s].label != g->node[t].label) {",
        "            g->node[s].status = PROTOTYPE;",
        "            g->node[t].status = PROTOTYPE;",
        "        }",
        "    }",
        "}",
        "",
        "/* 2) IFT: os prototipos competem pelas amostras */",
        "void treinar(Graph *g) {",
        "",
        "    RealHeap *Q = createRealHeap(g->nnodes, g->pathval);",
        "",
        "    for (int s = 0; s < g->nnodes; s++) {",
        "        g->pathval[s] = INFINITY;      /* ninguem alcanca */",
        "        if (g->node[s].status == PROTOTYPE) {",
        "            g->pathval[s]   = 0.0;     /* semente: custo 0 */",
        "            g->node[s].pred = NIL;",
        "            g->node[s].root = s;",
        "            insertRealHeap(Q, s);",
        "        }",
        "    }",
        "",
        "    while (!isEmptyRealHeap(Q)) {",
        "",
        "        removeRealHeap(Q, &s);         /* menor custo vence */",
        "        g->ordered[i++] = s;           /* ordem da conquista */",
        "",
        "        for (int t = 0; t < g->nnodes; t++) {",
        "            if (g->pathval[t] > g->pathval[s]) {",
        "",
        "                tmp = fmax(g, s, t);",
        "",
        "                if (tmp < g->pathval[t]) {",
        "                    g->node[t].pred  = s;",
        "                    g->node[t].root  = g->node[s].root;",
        "                    g->node[t].label = g->node[s].label;",
        "                    updateRealHeap(Q, t, tmp);",
        "                }",
        "            }",
        "        }",
        "    }",
        "}",
        "",
        "/* 3) classificar: a amostra entra na arvore que  */",
        "/*    oferecer o caminho mais barato ate ela      */",
        "int classificar(Graph *g, Sample x) {",
        "",
        "    int   melhor = NIL;",
        "    float minimo = INFINITY;",
        "",
        "    for (int i = 0; i < g->nnodes; i++) {",
        "",
        "        int   s = g->ordered[i];       /* na ordem do IFT */",
        "        float custo = MAX(g->pathval[s], dist(g->node[s], x));",
        "",
        "        if (custo < minimo) { minimo = custo; melhor = s; }",
        "",
        "        /* dai pra frente ninguem melhora: pode parar */",
        "        if (g->pathval[s] > minimo) break;",
        "    }",
        "",
        "    return g->node[melhor].label;      /* o veredito */",
        "}",
        "",
    };

    /**
     * Desenha o codigo rolando de baixo pra cima, em loop.
     *
     * A rolagem e continua (em pixels, nao em linhas) pra nao dar o efeito
     * de salto que uma lista rolando de linha em linha teria. Desenha so a
     * faixa visivel, entao o custo nao depende do tamanho do texto.
     */
    private void desenharCodigoDeFundo(Graphics2D g, int t) {

        int alturaLinha = Math.max(8, Config.getInt("papa.opf.alturaDaLinhaDoCodigo", 14));
        double velocidade = Config.getDouble("papa.opf.velocidadeDoCodigo", 0.35);

        int alturaTotal = CODIGO.length * alturaLinha;

        // Rolagem em PIXELS e nao em linhas: rolando de linha em linha o
        // texto daria saltos, e o fundo chamaria atencao justamente por
        // causa do movimento brusco.
        double rolagem = (t * velocidade) % alturaTotal;

        g.setFont(new Font("Monospaced", Font.PLAIN, 11));

        int alphaCodigo    = Config.getInt("papa.opf.alphaDoCodigo", 48);
        int alphaComentario = Config.getInt("papa.opf.alphaDoComentario", 36);

        Color corCodigo     = new Color(150, 235, 195, alphaCodigo);
        Color corComentario = new Color(120, 170, 235, alphaComentario);

        int x0 = Main.CAMPO_X + 12;

        // Duas passadas do texto (a que sobe e a que entra por baixo)
        // cobrem a tela inteira sem precisar de caso especial na virada.
        for (int passada = 0; passada < 2; passada++) {

            int base = Main.CAMPO_Y + Main.CAMPO_H - (int) rolagem + passada * alturaTotal;

            for (int i = 0; i < CODIGO.length; i++) {

                int y = base + i * alturaLinha - alturaTotal;

                // So o que esta na tela. Sem isto, seriam ~90 drawString
                // por passada, quase todos fora do campo.
                if (y < Main.CAMPO_Y - alturaLinha || y > Main.CAMPO_Y + Main.CAMPO_H) {
                    continue;
                }

                String linha = CODIGO[i];

                if (linha.isEmpty()) {
                    continue;
                }

                boolean comentario = linha.trim().startsWith("/*")
                                  || linha.trim().startsWith("*")
                                  || linha.contains("/*");

                g.setColor(comentario ? corComentario : corCodigo);
                g.drawString(linha, x0, y);
            }
        }
    }

    /**
     * Quatro cantinhos em L formando um quadrado aberto.
     *
     * E o vocabulario visual de "alvo sob analise" que todo HUD de
     * maquina usa, e custa oito drawLine.
     */
    private void desenharCantos(Graphics2D g, double cx, double cy, int r) {

        int p = Math.max(2, r / 2);

        int x0 = (int) (cx - r);
        int x1 = (int) (cx + r);
        int y0 = (int) (cy - r);
        int y1 = (int) (cy + r);

        g.drawLine(x0, y0, x0 + p, y0);   g.drawLine(x0, y0, x0, y0 + p);
        g.drawLine(x1, y0, x1 - p, y0);   g.drawLine(x1, y0, x1, y0 + p);
        g.drawLine(x0, y1, x0 + p, y1);   g.drawLine(x0, y1, x0, y1 - p);
        g.drawLine(x1, y1, x1 - p, y1);   g.drawLine(x1, y1, x1, y1 - p);
    }

    /**
     * Linha de varredura descendo o campo, e um leve grid por baixo.
     *
     * Puro clima: nao afeta jogabilidade nenhuma. Mas e o que faz o
     * ataque LER como uma maquina processando, e nao como luzinhas
     * bonitas — que era a reclamacao original sobre este spell card.
     */
    private void desenharVarredura(Graphics2D g, int t) {

        // Grid fraco.
        g.setColor(new Color(120, 200, 255, 16));

        for (int gx = Main.CAMPO_X; gx < Main.CAMPO_X + Main.CAMPO_W; gx += 40) {
            g.drawLine(gx, Main.CAMPO_Y, gx, Main.CAMPO_Y + Main.CAMPO_H);
        }

        for (int gy = Main.CAMPO_Y; gy < Main.CAMPO_Y + Main.CAMPO_H; gy += 40) {
            g.drawLine(Main.CAMPO_X, gy, Main.CAMPO_X + Main.CAMPO_W, gy);
        }

        // A linha que varre, com rastro.
        int periodo = Math.max(1, Config.getInt("papa.opf.periodoDaVarredura", 210));
        int y = Main.CAMPO_Y + (t % periodo) * Main.CAMPO_H / periodo;

        for (int i = 0; i < 10; i++) {
            g.setColor(new Color(140, 220, 255, 40 - i * 4));
            g.drawLine(Main.CAMPO_X, y - i * 3, Main.CAMPO_X + Main.CAMPO_W, y - i * 3);
        }

        g.setColor(new Color(190, 240, 255, 90));
        g.drawLine(Main.CAMPO_X, y, Main.CAMPO_X + Main.CAMPO_W, y);
    }

    /** Quantas amostras ja foram classificadas, no canto do campo. */
    private void desenharLegenda(Graphics2D g) {

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(150, 230, 190, 200));

        // Prompt de terminal: e a IA falando, e nao uma legenda do jogo.
        String texto = (alivio > 0)
                     ? "opf> forest complete — resampling"
                     : "opf> ift running  " + animadas + "/" + ordem.length
                       + ((tDesenho / 20) % 2 == 0 ? " _" : "");

        // O aquecimento vira barra: o jogador ve a maquina ficando rapida
        // e entende por que o campo esta esvaziando ao mesmo tempo.
        int larguraBarra = 120;
        int bx = Main.CAMPO_X + Main.CAMPO_W - larguraBarra - 10;
        int by = Main.CAMPO_Y + Main.CAMPO_H - 30;

        g.setColor(new Color(90, 150, 130, 150));
        g.drawRect(bx, by, larguraBarra, 6);

        g.setColor(new Color(150, 230, 190, 200));
        g.fillRect(bx, by, (int) (larguraBarra * aquecimento), 6);

        g.drawString("training", bx, by - 4);

        g.drawString(texto, Main.CAMPO_X + 8, Main.CAMPO_Y + Main.CAMPO_H - 26);

        // Linha de baixo: o que o algoritmo esta fazendo AGORA, em
        // palavras. E o que transforma o padrao de "luzes aleatorias" em
        // "alguem esta rodando um algoritmo em cima de mim".
        if (!ultimaJogada.isEmpty()) {
            g.setColor(new Color(255, 220, 150, 190));
            g.drawString(ultimaJogada, Main.CAMPO_X + 8, Main.CAMPO_Y + Main.CAMPO_H - 10);
        }
    }
}
