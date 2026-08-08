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

    /** Ticks que a aresta candidata fica piscando antes de disparar. */
    private final int ticksDeAviso;

    /** Ticks de pausa entre uma conquista e o aviso da proxima. */
    private final int ticksEntreConquistas;

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

    private Random rng;

    /** O no do jogador e sempre o indice 0 — ver iniciarCiclo(). */
    private static final int NO_DO_JOGADOR = 0;

    public OptimumPathForestSpell() {

        super("⊛  Optimum Path Forest",
              Config.getDouble("papa.opf.hp", 520),
              Config.getInt("papa.opf.duracao", 2600));

        this.prototipos = Math.max(2, Config.getInt("papa.opf.prototipos", 3));
        this.amostras   = Math.max(4, Config.getInt("papa.opf.amostras", 9));

        this.ticksDeAviso         = Math.max(1, Config.getInt("papa.opf.ticksDeAviso", 30));
        this.ticksEntreConquistas = Math.max(0, Config.getInt("papa.opf.ticksEntreConquistas", 16));
        this.ticksDeAlivio        = Math.max(0, Config.getInt("papa.opf.ticksDeAlivio", 120));

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
                esperaAteProxima = ticksEntreConquistas;
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
     * Escolhe o proximo no da fila e comeca a piscar a aresta dele.
     *
     * Prototipo nao tem aresta de entrada — ele so acende, sem aviso e
     * sem tiro, porque nao ha "de onde" o tiro viria.
     */
    private void anunciarProximaConquista() {

        alvoAtual = ordem[animadas];

        if (nos[alvoAtual].pred < 0) {
            executarConquista();
            esperaAteProxima = ticksEntreConquistas;
            return;
        }

        avisando = ticksDeAviso;

        ultimaJogada = "raiz " + nos[alvoAtual].raiz
                     + " mira amostra " + alvoAtual
                     + "  ·  custo fmax " + (int) nos[alvoAtual].custo;
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
        double frac = avisando / (double) ticksDeAviso;
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

    private void desenharNos(Graphics2D g) {

        for (int i = 0; i < nos.length; i++) {

            No n = nos[i];

            int r = n.prototipo ? 9 : 5;

            if (!n.conquistado) {
                // Ainda nao classificada: cinza fraco.
                g.setColor(new Color(160, 160, 180, 90));
                g.drawOval((int) (n.x - r), (int) (n.y - r), r * 2, r * 2);
                continue;
            }

            Color c = corDaRaiz(n.raiz);

            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
            g.fillOval((int) (n.x - r), (int) (n.y - r), r * 2, r * 2);

            // O prototipo ganha um anel: e a semente, nao uma amostra
            // qualquer, e o jogador precisa distinguir os dois.
            if (n.prototipo) {
                g.setColor(Color.WHITE);
                g.drawOval((int) (n.x - r - 3), (int) (n.y - r - 3), (r + 3) * 2, (r + 3) * 2);
            }
        }

        // O no do jogador, marcado com um X ate ser classificado.
        No meu = nos[NO_DO_JOGADOR];

        g.setColor(meu.conquistado ? new Color(255, 120, 120) : new Color(255, 255, 255, 150));
        g.drawLine((int) meu.x - 7, (int) meu.y - 7, (int) meu.x + 7, (int) meu.y + 7);
        g.drawLine((int) meu.x - 7, (int) meu.y + 7, (int) meu.x + 7, (int) meu.y - 7);
    }

    /** Quantas amostras ja foram classificadas, no canto do campo. */
    private void desenharLegenda(Graphics2D g) {

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(210, 205, 235, 190));

        String texto = (alivio > 0)
                     ? "FLORESTA COMPLETA — reamostrando"
                     : "classificando  " + animadas + " / " + ordem.length;

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
