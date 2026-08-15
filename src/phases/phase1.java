package src.phases;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Random;

import src.Config;
import src.Cutscene;
import src.Main;
import src.Som;
import src.enemyTypes.Adriana;
import src.enemyTypes.ArcEnemy;
import src.enemyTypes.BossEnemy;
import src.enemyTypes.Clayton;
import src.enemyTypes.DisqueteEnemy;
import src.enemyTypes.Enemy;
import src.enemyTypes.HorizontalEnemy;
import src.enemyTypes.MonitorEnemy;
import src.enemyTypes.Papa;
import src.enemyTypes.PendulumEnemy;

/**
 * FASE 1 - "INDO PARA DCO" (ver Roteiro.txt).
 *
 * A fase e dividida em estagios. Ao trocar de estagio o cronometro 'time'
 * zera, e o que acontece e escrito em funcao dele: nada de maquina de
 * estado complicada, so "no tick X, spawna Y".
 *
 * Estagios previstos:
 *   1 - ondas de alunos corrompidos no caminho pro DCO   [FEITO]
 *   2 - Adriana, fase 1                                  [A FAZER]
 *   3 - Adriana, fase 2 (cachorros que sabem calculo)    [A FAZER]
 *   4 - Clayton, fase 1                                  [A FAZER]
 *   5 - Clayton, fase 2 (Tab maligno)                    [A FAZER]
 *
 * COMO AS ONDAS FUNCIONAM (estagio 1)
 * -----------------------------------
 * Cada onda e montada na hora a partir de um ou mais GRUPOS, e os grupos
 * rodam AO MESMO TEMPO. Cada grupo tem seu proprio padrao de movimento,
 * seu proprio lado de entrada e sua propria fila de nascimento, entao
 * duas ondas nunca ficam iguais: dois pendulos descendo enquanto um arco
 * cruza por baixo e um disquete quica atravessando os dois.
 *
 * ANTES ERA UM PADRAO POR ONDA. A ideia era legibilidade — o jogador lia a
 * onda inteira de uma vez. Na pratica isso deu uma fase que, depois de
 * cinco ondas, o jogador ja tinha visto todas as combinacoes possiveis:
 * eram cinco padroes puros em rodizio. Combinando, os mesmos cinco
 * padroes geram dezenas de leituras diferentes sem custar nenhum inimigo
 * novo.
 *
 * A DIFICULDADE NAO MUDOU. O total de inimigos por onda continua saindo da
 * mesma rampa de sempre (inimigosPorOndaInicio -> Fim): os grupos apenas
 * REPARTEM esse total entre si. O que cresce junto com a fase e quantos
 * grupos simultaneos podem existir — uma onda no comeco e quase sempre de
 * um padrao so, e no fim ela chega a tres.
 *
 * Os padroes de uma mesma onda sao sorteados SEM REPETICAO enquanto der.
 * Sorteio independente devolvia "pendulo + pendulo" com frequencia, que
 * visualmente e a mesma coisa que uma onda unica maior — ou seja, o custo
 * da mudanca sem nenhum do beneficio.
 */
public class phase1 {

    /** Os padroes de movimento que uma onda pode ter. */
    public enum Padrao {
        PENDULO,
        ARCO,
        HORIZONTAL,
        /** Disquetes infectados quicando nas laterais enquanto descem. */
        DISQUETE,
        /** Monitores de laboratorio que param no ar e cospem espiral. */
        MONITOR
    }

    /** Cronometro do estagio atual, em ticks. Zera a cada troca de estagio. */
    private int time = 0;

    private int stage = 1;

    /* --- controle das ondas --- */

    private int ondasLancadas = 0;

    /**
     * Um GRUPO: um padrao de movimento com sua propria fila de nascimento.
     *
     * Varios deles rodam ao mesmo tempo dentro da mesma onda, e e a
     * sobreposicao entre eles que gera as formacoes. Cada um guarda o
     * proprio indice porque as posicoes de nascimento (coluna, altura) sao
     * calculadas a partir dele — um contador compartilhado empilharia
     * inimigos de padroes diferentes na mesma coluna.
     */
    private static class Grupo {

        final Padrao padrao;
        final boolean pelaEsquerda;
        final int total;

        int restantes;
        int indice = 0;
        int proximoEm;

        Grupo(Padrao padrao, boolean pelaEsquerda, int total, int comecaEm) {
            this.padrao = padrao;
            this.pelaEsquerda = pelaEsquerda;
            this.total = total;
            this.restantes = total;
            this.proximoEm = comecaEm;
        }
    }

    /** Os grupos nascendo agora. Vazio = a onda terminou de nascer. */
    private final java.util.List<Grupo> gruposAtivos = new java.util.ArrayList<>();

    /** Momento (em tempo de estagio) da proxima onda. */
    private int proximaOndaEm = 0;

    private Random rng;

    /* --- ajustes lidos do game.properties --- */

    private int ticksAteComecar;
    private int intervaloEntreOndas;
    private int atrasoEntreInimigos;
    private int totalDeOndas;

    /** Tamanho da primeira e da ultima onda. As do meio interpolam entre os dois. */
    private int inimigosPorOndaInicio;
    private int inimigosPorOndaFim;

    private int pesoPendulo;
    private int pesoArco;
    private int pesoHorizontal;
    private int pesoDisquete;
    private int pesoMonitor;

    public phase1() {
        carregarConfig();
    }

    /** (Re)le os ajustes. Chamado no construtor e no hot-reload (F5). */
    public void carregarConfig() {

        this.ticksAteComecar      = Config.getInt("fase1.ticksAteComecar", 90);
        this.intervaloEntreOndas  = Config.getInt("fase1.intervaloEntreOndas", 300);
        this.atrasoEntreInimigos  = Config.getInt("fase1.atrasoEntreInimigos", 18);
        this.totalDeOndas         = Config.getInt("fase1.totalDeOndas", 30);

        this.inimigosPorOndaInicio = Math.max(1, Config.getInt("fase1.inimigosPorOndaInicio", 4));
        this.inimigosPorOndaFim    = Math.max(1, Config.getInt("fase1.inimigosPorOndaFim", 9));

        this.pesoPendulo    = Config.getInt("fase1.pesoPendulo", 50);
        this.pesoArco       = Config.getInt("fase1.pesoArco", 25);
        this.pesoHorizontal = Config.getInt("fase1.pesoHorizontal", 25);
        this.pesoDisquete   = Config.getInt("fase1.pesoDisquete", 20);
        this.pesoMonitor    = Config.getInt("fase1.pesoMonitor", 12);

        // Semente fixa (>= 0) faz as ondas sairem SEMPRE na mesma ordem,
        // o que ajuda muito a testar um ajuste sem a aleatoriedade atrapalhar.
        long seed = Config.getInt("fase1.seed", -1);
        this.rng = (seed < 0) ? new Random() : new Random(seed);
    }

    public void tick() {

        // Os pedidos da conversa sao atendidos ANTES da guarda: e assim
        // que a chefe consegue entrar (ou se transformar) no meio de um
        // dialogo, no momento exato em que a fala manda.
        atenderPedidosDaConversa();

        // Fora isso, enquanto a conversa esta na tela a FASE para.
        //
        // O resto do jogo continua rodando (o fundo rola, o jogador anda,
        // a chefe entra voando) — o que congela e so o avanco da fase:
        // nenhuma onda nova nasce e nenhum estagio vira.
        if (Main.emDialogo()) {
            return;
        }

        switch (stage) {
            case 1: stage1(); break;
            case 2: stage2(); break;
            case 3: stage3(); break;
            case 4: stage4(); break;
            case 5: stage5(); break;
            case 6: stage6(); break;
            case 7: stage7(); break;
            default: break;   // fase acabou
        }

        // A TRILHA DO ESTAGIO, aplicada DEPOIS do estagio rodar.
        //
        // A ordem importa: e neste tick que o estagio pode ter aberto uma
        // conversa, e se a musica fosse pedida antes ela entraria junto
        // com a primeira fala. Perguntando de novo aqui, a faixa so troca
        // quando NAO ha ninguem falando — ou seja, quando a cutscene
        // termina e a luta comeca de fato.
        //
        // (Antes disso, cada estagio trocava a musica dentro do bloco "se
        // nao houve conversa, spawna o chefe direto", que na partida
        // normal nunca roda — quem poe o chefe em cena e o gatilho da
        // fala. Era por isso que o tema so aparecia na transformacao.)
        if (!Main.emDialogo()) {
            aplicarMusicaDoEstagio();
        }

        time++;
    }

    /**
     * Atende o gatilho que a fala atual pediu (ver Cutscene.Gatilho).
     *
     * CHEFE_ENTRA      — cria a chefe do estagio e poe ela voando em cena
     * CHEFE_TRANSFORMA — troca a forma dela sem sair do lugar
     *
     * Antes disso a chefe nascia no primeiro frame do dialogo, o que
     * colocava a Adriana na tela durante o trecho do SANTO JAVA — onde o
     * roteiro nem menciona ela ainda.
     */
    private void atenderPedidosDaConversa() {

        if (!Main.emDialogo()) {
            return;
        }

        Cutscene.Gatilho pedido = Main.cutsceneAtual.consumirGatilho();

        // A guarda do chefeSpawnado e REDE DE SEGURANCA, nao otimizacao.
        // Um gatilho repetido (bug ou cutscene mal montada) criaria uma
        // chefe nova por frame, cada uma com o proprio padrao de bala.
        // Aconteceu de verdade uma vez; nao pode voltar a acontecer.
        if (pedido == Cutscene.Gatilho.CHEFE_ENTRA && !chefeSpawnado) {

            chefeSpawnado = true;
            chefeAtual = criarChefeDoEstagio(false);

            if (chefeAtual != null) {
                chefeAtual.entrarEmDialogo();
                Main.enemies.add(chefeAtual);
            }

        } else if (pedido == Cutscene.Gatilho.CHEFE_TRANSFORMA && chefeAtual != null) {

            transformarChefe();

        } else if (pedido == Cutscene.Gatilho.JOGADOR_GANHA_ARMADURA && Main.player != null) {

            // A armadura NAO e dada aqui: aqui comeca a CERIMONIA.
            //
            // Ela toca a faixa propria, junta energia roxa por uns nove
            // segundos e so entao fecha a armadura, no baque da musica
            // (ver AscensaoDaArmadura). Dar a armadura na hora fazia a
            // fala mais importante do roteiro acontecer num frame.
            if (!Main.player.temArmadura() && Main.ascensoes.isEmpty()) {
                Main.ascensoes.add(new src.AscensaoDaArmadura());
            }

        } else if (pedido == Cutscene.Gatilho.JOGADOR_GANHA_ORIENTACAO && Main.player != null) {

            Main.player.ganharOrientacaoAObjetos();
        }
    }

    /**
     * Troca a chefe pela forma seguinte SEM ela sair do lugar.
     *
     * O sprite em jogo tem que virar no mesmo instante que o retrato da
     * caixa de dialogo — sem isso ela ficava maligna na conversa e na
     * forma base voando no campo, que foi exatamente o que apareceu no
     * teste.
     *
     * Guardar a posicao e o que faz a troca parecer uma transformacao, e
     * nao uma chefe sumindo e outra nascendo.
     */
    private void transformarChefe() {

        double px = (chefeAtual != null) ? chefeAtual.getX() : Main.CAMPO_X + Main.CAMPO_W / 2.0;
        double py = (chefeAtual != null) ? chefeAtual.getY() : Main.CAMPO_Y + 120;

        if (chefeAtual != null) {
            chefeAtual.abandonar();
            Main.enemies.remove(chefeAtual);
        }

        chefeAtual = criarChefeDoEstagio(true);

        if (chefeAtual == null) {
            return;
        }

        chefeAtual.setX(px);
        chefeAtual.setY(py);

        chefeAtual.entrarEmDialogo();
        Main.enemies.add(chefeAtual);

        // VERMELHO: a cor da corrupcao no jogo inteiro (balas inimigas,
        // cachorros, forma maligna). O estouro sai da posicao dela, entao
        // o olho ja esta no lugar certo quando o sprite novo aparece.
        src.Explosao.vermelha(px, py);

        Som.tocar(Som.SPELL_QUEBRA);
    }

    /**
     * A chefe do estagio atual.
     *
     * @param transformada false = a forma com que ela ENTRA no estagio;
     *                     true  = a forma pra qual ela vira no meio da
     *                             conversa. Nos estagios de forma unica os
     *                             dois sao iguais.
     */
    private BossEnemy criarChefeDoEstagio(boolean transformada) {

        switch (stage) {

            case 2: return Adriana.criarFormaBase();

            // Ela COMECA o estagio 3 na forma base (vindo do 2) e so vira
            // maligna quando a fala manda.
            case 3: return transformada ? Adriana.criarFormaMaligna() : Adriana.criarFormaBase();

            case 4: return Clayton.criarFormaBase();
            case 5: return transformada ? Clayton.criarFormaMaligna() : Clayton.criarFormaBase();

            case 6: return Papa.criarFormaBase();
            case 7: return transformada ? Papa.criarFormaIA() : Papa.criarFormaBase();

            default: return null;
        }
    }

    /**
     * A trilha que o estagio atual deve estar tocando.
     *
     * Um lugar so decide isso, e ele roda todo tick. A alternativa (cada
     * estagio trocar a musica no ponto em que spawna o chefe) ja falhou
     * uma vez justamente porque existe MAIS DE UM caminho pro chefe entrar
     * — pela conversa ou direto — e so um deles tinha a linha da musica.
     *
     * Vazio = silencio. E o caso do estagio 1 depois da fase e do trecho
     * entre uma luta e a proxima.
     */
    private void aplicarMusicaDoEstagio() {

        if (Main.musica == null) {
            return;
        }

        // A CERIMONIA DA ARMADURA tem faixa propria e ela manda. Sem esta
        // saida, o tick seguinte ao "ESPANDAAAAA" ja devolvia a musica do
        // estagio por cima dela.
        if (Main.emAscensao()) {
            return;
        }

        String faixa;

        switch (stage) {

            case 1:
                faixa = Config.getString("fase1.musica", "audio/fase1.wav");
                break;

            case 2:
            case 3:
                faixa = temaDeChefe(Config.getString("adriana.musica", "audio/kim_jung.wav"));
                break;

            case 4:
            case 5:
                faixa = temaDeChefe(Config.getString("clayton.musica", "audio/pronounced_rules.wav"));
                break;

            case 6:
                faixa = temaDeChefe(Config.getString("papa.musica", "audio/flower_man.wav"));
                break;

            // A FORMA IA TEM TEMA PROPRIO.
            //
            // E o unico chefe do jogo com duas trilhas, e faz sentido que
            // seja ele: a transformacao do PAPA nao e "o mesmo inimigo com
            // sprite novo" como a da Adriana e a do Clayton — a tela fica
            // branca, o virus toma o corpo por inteiro e o que sobra e
            // outra coisa. Manter a mesma musica dizia o contrario disso.
            case 7:
                faixa = temaDeChefe(Config.getString("papa.musicaIA", "audio/black_knife.wav"));
                break;

            default:
                faixa = Config.getString("musica.arquivo", "");
                break;
        }

        Main.musica.trocarFaixa(faixa);
    }

    /**
     * O tema do chefe, mas SO DEPOIS DO PRIMEIRO ATAQUE COMECAR.
     *
     * Antes disso devolve silencio. A diferenca e de encenacao: o chefe
     * entra voando, a conversa acontece, o nome do spell card aparece — e
     * so quando a primeira bala sai e que a musica entra. A entrada dele
     * deixa de ser "apareceu com trilha" e vira "apareceu, ficou um
     * silencio, e ai comecou".
     *
     * O teste e o tSpell: ele so passa de zero depois do comecarLuta(),
     * que e disparado no fim do dialogo. Enquanto o chefe esta chegando ou
     * conversando, ele fica em zero.
     */
    private String temaDeChefe(String faixa) {

        BossEnemy chefe = Main.chefeEmCena();

        // A TRAVA, e nao "tSpell > 0".
        //
        // O tSpell zera a cada troca de ataque, entao a versao anterior
        // respondia "nao esta lutando" no frame da troca: a musica ia pro
        // silencio e voltava DO COMECO no frame seguinte. Toda spell card
        // nova reiniciava a trilha.
        boolean lutando = chefe != null
                       && chefe.isAlive()
                       && !chefe.isEmDialogo()
                       && chefe.jaComecouAAtacar();

        return lutando ? faixa : Config.getString("musica.arquivo", "");
    }

    /** Troca de estagio e zera o cronometro. Todo estagio termina chamando isso. */
    private void proximoEstagio() {

        stage++;
        time = 0;

        ondasLancadas = 0;
        gruposAtivos.clear();
        proximaOndaEm = 0;

        // Solta o gatilho do chefe: o proximo estagio de boss precisa
        // spawnar a forma DELE. Os flags de cutscene NAO sao zerados de
        // proposito — cada conversa acontece uma vez por partida.
        chefeSpawnado = false;
        chefeAtual = null;
    }

    /* =====================================================
       ESTAGIO 1 - ondas de alunos corrompidos
       ===================================================== */

    /** A conversa com o professor Paiola acontece uma vez por partida. */
    private boolean cutscenePaiolaMostrada = false;

    private void stage1() {

        // A trilha deste estagio (e a de todos os outros) e pedida pelo
        // aplicarMusicaDoEstagio(), la no tick().

        // Respiro inicial antes do primeiro inimigo aparecer.
        if (time < ticksAteComecar) {
            return;
        }

        int t = time - ticksAteComecar;

        // 1) Hora de comecar uma onda nova?
        //    So depois que a anterior terminou de nascer inteira.
        if (ondasLancadas < totalDeOndas && gruposAtivos.isEmpty() && t >= proximaOndaEm) {

            prepararOnda(t);

            ondasLancadas++;
            proximaOndaEm = t + intervaloEntreOndas;
        }

        // 2) Cada grupo nasce na SUA fila, e as filas correm juntas.
        //    Percorrido de tras pra frente porque grupos terminados saem
        //    da lista aqui mesmo.
        for (int i = gruposAtivos.size() - 1; i >= 0; i--) {

            Grupo g = gruposAtivos.get(i);

            if (g.restantes > 0 && t >= g.proximoEm) {

                nascerUm(g);

                g.restantes--;
                g.indice++;
                g.proximoEm = t + atrasoEntreInimigos;
            }

            if (g.restantes == 0) {
                gruposAtivos.remove(i);
            }
        }

        // 3) So avanca quando as ondas acabaram E a tela ficou limpa.
        if (ondasLancadas >= totalDeOndas && gruposAtivos.isEmpty() && Main.enemies.isEmpty()) {

            // O PROFESSOR PAIOLA fecha o estagio 1.
            //
            // Fica aqui, e nao no comeco do estagio 2, porque o estagio 2
            // ja abre com a cutscene da Adriana — duas conversas emendadas
            // no mesmo frame, uma por cima da outra.
            if (!cutscenePaiolaMostrada) {
                cutscenePaiolaMostrada = true;
                Main.mostrarCutscene(Cutscene.criarPaiola());
                return;
            }

            proximoEstagio();
        }
    }

    /**
     * Monta a onda: sorteia quantos grupos, quais padroes e reparte os
     * inimigos entre eles.
     *
     * O TOTAL de inimigos e o mesmo de sempre (a rampa de dificuldade nao
     * mudou); o que este metodo faz e decidir COMO esse total aparece na
     * tela. Uma onda de 6 pode ser seis pendulos em cascata, ou tres
     * pendulos com dois horizontais cruzando e um monitor parado no meio.
     */
    private void prepararOnda(int t) {

        gruposAtivos.clear();

        int total = tamanhoDaOnda(ondasLancadas);
        int quantosGrupos = quantosGruposAgora(total);

        // Os padroes da onda, sem repetir enquanto der.
        java.util.List<Padrao> escolhidos = sortearPadroesSemRepetir(quantosGrupos);

        // Reparte o total entre os grupos: pelo menos 1 pra cada, e o que
        // sobrar cai no primeiro. Repartir por igual deixaria toda onda com
        // grupos do mesmo tamanho, e o resto no primeiro e justamente o que
        // faz existir um padrao "principal" e outros de tempero.
        int base = total / escolhidos.size();
        int sobra = total - base * escolhidos.size();

        for (int i = 0; i < escolhidos.size(); i++) {

            Padrao p = escolhidos.get(i);

            int quantos = Math.max(1, base + (i == 0 ? sobra : 0));

            // MONITOR tem teto proprio.
            //
            // Ele e o unico padrao que PARA no ar: os outros atravessam e
            // saem. Um grupo cheio de monitores viraria uma bateria fixa
            // de seis espirais simultaneas, que nao e dificil — e
            // indesviavel. Dois ja obrigam o jogador a escolher em qual
            // atirar primeiro, que e o ponto deles.
            if (p == Padrao.MONITOR) {
                quantos = Math.min(quantos,
                        Math.max(1, Config.getInt("fase1.maximoDeMonitoresPorOnda", 2)));
            }

            // Cada grupo entra com um atraso proprio. Sem isso os tres
            // nascem no mesmo frame e a onda vira um susto unico em vez de
            // uma formacao que se desenha ao longo de alguns segundos.
            int atraso = (i == 0) ? 0
                       : rng.nextInt(Math.max(1, Config.getInt("fase1.atrasoMaximoEntreGrupos", 70)));

            gruposAtivos.add(new Grupo(p, rng.nextBoolean(), quantos, t + atraso));
        }
    }

    /**
     * Quantos grupos simultaneos esta onda pode ter.
     *
     * Cresce com o andamento da fase, entao o comeco continua sendo de
     * padroes limpos (o jogador esta aprendendo o que cada inimigo faz) e
     * a bagunca so aparece quando ele ja sabe ler cada peca separada.
     *
     * O teto extra e o proprio numero de inimigos: nao adianta pedir tres
     * grupos numa onda de dois bichos.
     */
    private int quantosGruposAgora(int totalDeInimigos) {

        int maximo = Math.max(1, Config.getInt("fase1.maximoDeGruposPorOnda", 3));

        double progresso = (totalDeOndas <= 1) ? 1
                         : ondasLancadas / (totalDeOndas - 1.0);

        progresso = Math.max(0, Math.min(1, progresso));

        // +1 no fim pra o sorteio poder alcancar o maximo; o teto e cortado
        // logo abaixo de qualquer forma.
        int teto = 1 + (int) (progresso * maximo);

        teto = Math.min(teto, maximo);
        teto = Math.min(teto, totalDeInimigos);

        return 1 + rng.nextInt(Math.max(1, teto));
    }

    /**
     * N padroes diferentes, sorteados por peso.
     *
     * Tenta algumas vezes evitar repeticao e desiste se nao conseguir —
     * com pesos muito desbalanceados (um padrao com peso 90) insistir
     * daria loop. Desistir e devolver repetido e melhor que travar o jogo,
     * e o pior caso e uma onda parecida com as antigas.
     */
    private java.util.List<Padrao> sortearPadroesSemRepetir(int quantos) {

        java.util.List<Padrao> saida = new java.util.ArrayList<>();

        for (int i = 0; i < quantos; i++) {

            Padrao p = sortearPadrao();

            for (int tentativa = 0; tentativa < 8 && saida.contains(p); tentativa++) {
                p = sortearPadrao();
            }

            saida.add(p);
        }

        return saida;
    }

    /**
     * Rampa de dificuldade: as ondas crescem de inimigosPorOndaInicio ate
     * inimigosPorOndaFim ao longo da fase.
     *
     * A fase e longa (uns 3 minutos), entao onda de tamanho fixo do comeco
     * ao fim vira sono. Crescer devagar mantem a tensao subindo sem precisar
     * de nenhum inimigo novo.
     *
     * @param indiceDaOnda 0 = primeira onda
     */
    private int tamanhoDaOnda(int indiceDaOnda) {

        if (totalDeOndas <= 1) {
            return inimigosPorOndaInicio;
        }

        double progresso = indiceDaOnda / (totalDeOndas - 1.0);
        progresso = Math.max(0, Math.min(1, progresso));

        return (int) Math.round(
            inimigosPorOndaInicio + (inimigosPorOndaFim - inimigosPorOndaInicio) * progresso
        );
    }

    /**
     * Sorteio por peso. Somando os pesos e tirando um numero nesse intervalo,
     * a chance de cada padrao fica proporcional ao seu peso — e da pra mudar
     * a proporcao no .properties sem tocar em codigo.
     *
     * Com 50/25/25:  0..49 -> PENDULO   50..74 -> ARCO   75..99 -> HORIZONTAL
     */
    private Padrao sortearPadrao() {

        int total = pesoPendulo + pesoArco + pesoHorizontal + pesoDisquete + pesoMonitor;

        if (total <= 0) {
            return Padrao.PENDULO;   // config zerada: nao trava o jogo
        }

        int sorteio = rng.nextInt(total);
        int acumulado = pesoPendulo;

        if (sorteio < acumulado) {
            return Padrao.PENDULO;
        }

        acumulado += pesoArco;

        if (sorteio < acumulado) {
            return Padrao.ARCO;
        }

        acumulado += pesoHorizontal;

        if (sorteio < acumulado) {
            return Padrao.HORIZONTAL;
        }

        acumulado += pesoDisquete;

        if (sorteio < acumulado) {
            return Padrao.DISQUETE;
        }

        return Padrao.MONITOR;
    }

    /** Cria o proximo inimigo DE UM GRUPO, conforme o padrao dele. */
    private void nascerUm(Grupo g) {

        switch (g.padrao) {

            case PENDULO:
                // Espalhados pela largura do campo. Como nascem escalonados,
                // formam uma cascata diagonal em vez de uma linha reta.
                Main.enemies.add(new PendulumEnemy(colunaDoIndice(g)));
                break;

            case ARCO:
                // Todos do mesmo lado e na mesma altura: fila indiana
                // percorrendo exatamente o mesmo arco.
                Main.enemies.add(new ArcEnemy(
                    g.pelaEsquerda,
                    Config.getDouble("inimigo.arco.entradaRelY", 0.55)
                ));
                break;

            case HORIZONTAL:
                // Alturas escalonadas dentro de uma faixa, pra travessia
                // virar uma diagonal e nao uma parede.
                Main.enemies.add(new HorizontalEnemy(g.pelaEsquerda, alturaDoIndice(g)));
                break;

            case DISQUETE:
                // Entram por cima em colunas, mas ALTERNANDO o lado pra
                // onde vao quicar: assim o grupo cobre a tela em X em vez
                // de todos derivarem juntos pro mesmo canto.
                Main.enemies.add(new DisqueteEnemy(colunaDoIndice(g), g.indice % 2 == 0));
                break;

            case MONITOR:
                // Poucos e caros: eles PARAM no ar, entao um grupo cheio
                // deles viraria uma bateria fixa cuspindo espiral, que e
                // exatamente o tipo de coisa que nao da pra desviar.
                Main.enemies.add(new MonitorEnemy(colunaDoIndice(g)));
                break;

            default:
                break;
        }
    }

    /**
     * Distribui os inimigos do grupo pela largura do campo, sem colar nas
     * bordas.
     *
     * O DESLOCAMENTO por grupo e o que faz dois grupos simultaneos nao
     * nascerem exatamente nas mesmas colunas — o que na tela apareceria
     * como um inimigo em cima do outro. Meia coluna de folga basta pra
     * eles intercalarem.
     */
    private double colunaDoIndice(Grupo g) {

        double passo = Main.CAMPO_W / (g.total + 1.0);
        double desloque = g.pelaEsquerda ? 0 : passo * 0.5;

        double x = Main.CAMPO_X + passo * (g.indice + 1) + desloque;

        return Math.max(Main.CAMPO_X + 20, Math.min(Main.CAMPO_X + Main.CAMPO_W - 20, x));
    }

    /** Interpola a altura de entrada dentro da faixa configurada. */
    private double alturaDoIndice(Grupo g) {

        double min = Config.getDouble("inimigo.horizontal.entradaRelYMin", 0.10);
        double max = Config.getDouble("inimigo.horizontal.entradaRelYMax", 0.45);

        if (g.total <= 1) {
            return min;
        }

        return min + (max - min) * g.indice / (g.total - 1.0);
    }

    /* =====================================================
       ESTAGIOS 2 e 3 - ADRIANA (Roteiro.txt linhas 19 a 40)
       =====================================================
       Os dois seguem o mesmo roteiro:

         1. trocar o cenario (com transicao) e SPAWNAR a chefe ja em modo
            de dialogo — ela entra voando, calada e imune
         2. abrir a conversa por cima do jogo rodando
         3. quando a conversa fecha, o Main solta a chefe pra lutar
         4. esperar ELA morrer pra avancar

       A ordem importa: a chefe nasce ANTES do dialogo, e nao depois.
       E o que faz ela chegar voando enquanto a conversa acontece, em vez
       de aparecer do nada quando o texto some.

       Os flags de cutscene evitam repetir a conversa a cada tick — o
       tick() volta a rodar assim que ela fecha.
    */

    /** Flags de "esta cutscene ja foi exibida", pra nao repetir a cada tick. */
    private boolean cutsceneEncontroMostrada = false;
    private boolean cutsceneTransformacaoMostrada = false;
    private boolean cutsceneDerrotaMostrada = false;

    /** Marca que a chefe da forma atual ja nasceu. */
    private boolean chefeSpawnado = false;

    /**
     * O chefe do estagio atual.
     *
     * O estagio avanca quando ELE morre — nao quando Main.enemies esvazia.
     * A diferenca importa: o Clayton invoca claytonlings, que sobrevivem a
     * ele. Esperando a lista esvaziar, o jogador via o chefe morrer e a
     * cutscene so vinha segundos depois, quando os minions terminassem de
     * sair da tela.
     */
    /**
     * Tipado como BossEnemy (e nao Enemy) porque a fase precisa falar com
     * ele como CHEFE: abandonar() no pulo de estagio, por exemplo. Guardar
     * a referencia larga so pra "ser generico" custaria um cast toda vez.
     */
    private BossEnemy chefeAtual = null;

    /** true quando o chefe do estagio ja nasceu e ja morreu. */
    private boolean chefeDerrotado() {
        return chefeSpawnado && chefeAtual != null && !chefeAtual.isAlive();
    }

    /**
     * Limpa o que o chefe deixou pra tras (minions e balas dele).
     * Chamado assim que ele morre, pra a cutscene abrir com a tela limpa.
     */
    private void limparRestosDoChefe() {
        Main.enemies.clear();
        Main.bullets.clear();
    }

    /**
     * ESTAGIO 2 — Adriana, forma base (Roteiro.txt linhas 11 a 26).
     * Cutscene do encontro + ataques de integral e somatorio.
     */
    private void stage2() {

        // A ARMADURA nao e concedida aqui.
        //
        // Quem concede e a propria fala do "ESPANDAAAAA" (Roteiro.txt
        // linha 16), via Gatilho — ver atenderPedidosDaConversa(). Fazer
        // isso na virada do estagio deixava o estudante ja expansivo, com
        // bomba destravada, ANTES do Santo Java falar com ele: a cena
        // inteira virava anuncio de uma coisa que ja tinha acontecido.
        //
        // O caso "cutscene ja vista / pulada com F2" e coberto no passo 2.

        // 1) ABRE A CONVERSA. A chefe NAO nasce aqui: quem manda ela
        //    entrar (e transformar) e uma fala especifica da cena, via
        //    Gatilho — ver atenderPedidosDaConversa(). Foi assim que a
        //    Adriana parou de aparecer durante o trecho do SANTO JAVA.
        if (!cutsceneEncontroMostrada) {
            cutsceneEncontroMostrada = true;
            Main.mostrarCutscene(Cutscene.criarEncontroAdriana());
            return;
        }

        // Sem a conversa (ja vista, ou pulada com F2) ninguem dispara o
        // gatilho — entao a armadura e garantida aqui.
        if (Main.player != null) {
            Main.player.ganharArmadura();
        }

        // 2) Sem conversa (jogador ja viu, ou pulou com F2): a chefe entra
        //    direto, ja lutando.
        if (!chefeSpawnado) {

            chefeSpawnado = true;
            Main.fundo.trocarImagem("sprites/ambient/sala7.png");

            chefeAtual = criarChefeDoEstagio(false);

            if (chefeAtual != null) {
                Main.enemies.add(chefeAtual);
                chefeAtual.comecarLuta();
            }

            return;
        }

        // 3) Ela morreu?
        if (chefeDerrotado()) {

            limparRestosDoChefe();

            proximoEstagio();
        }
    }

    /**
     * ESTAGIO 3 — Adriana, forma integral maligna (Roteiro.txt linhas 27 a 40).
     * Cutscene da transformacao + area de Riemann e a esfera 3D.
     */
    private void stage3() {

        // 1) ABRE A CONVERSA. A chefe NAO nasce aqui: quem manda ela
        //    entrar (e transformar) e uma fala especifica da cena, via
        //    Gatilho — ver atenderPedidosDaConversa(). Foi assim que a
        //    Adriana parou de aparecer durante o trecho do SANTO JAVA.
        if (!cutsceneTransformacaoMostrada) {
            cutsceneTransformacaoMostrada = true;
            Main.mostrarCutscene(Cutscene.criarTransformacaoAdriana());
            return;
        }

        // 2) Sem conversa (jogador ja viu, ou pulou com F2): a chefe entra
        //    direto, ja lutando.
        if (!chefeSpawnado) {

            chefeSpawnado = true;
            Main.fundo.trocarImagem("sprites/ambient/sala7.png");

            chefeAtual = criarChefeDoEstagio(true);

            if (chefeAtual != null) {
                Main.enemies.add(chefeAtual);
                chefeAtual.comecarLuta();
            }

            return;
        }

        // 3) Ela morreu?
        if (chefeDerrotado()) {

            limparRestosDoChefe();

            if (!cutsceneDerrotaMostrada) {
                cutsceneDerrotaMostrada = true;
                Main.mostrarCutscene(Cutscene.criarDerrotaAdriana());
                return;
            }

            // Arco da Adriana fechado: cenario volta ao caminho e a
            // musica PARA. Entre as lutas o jogo fica em silencio — e o
            // que faz o tema do proximo chefe valer alguma coisa quando
            // entrar.
            Main.fundo.trocarImagem(null);

            proximoEstagio();
        }
    }

    /* =====================================================
       ESTAGIOS 4 e 5 - CLAYTON (Roteiro.txt linhas 41 a 57)
       =====================================================
       Mesma estrutura de tres passos dos estagios da Adriana.
    */

    private boolean cutsceneClaytonMostrada = false;
    private boolean cutsceneClaytonTransfMostrada = false;
    private boolean cutsceneClaytonDerrotaMostrada = false;

    /**
     * A lojinha do Perea acontece UMA VEZ por partida.
     *
     * Os dois flags nao sao zerados no proximoEstagio() (que so limpa o
     * que e do estagio), e sim junto com o resto da partida. Zerar aqui
     * faria a loja reabrir toda vez que o jogador voltasse a este ponto —
     * e como o jogo nao tem save, "voltar a este ponto" e sempre uma
     * partida nova.
     */
    private boolean cutscenePereaMostrada = false;
    private boolean lojaVisitada = false;

    /**
     * ESTAGIO 4 — Clayton, forma base (Roteiro.txt linhas 41 a 46).
     * Xadrez e claytonlings.
     */
    private void stage4() {

        // 1) ABRE A CONVERSA. A chefe NAO nasce aqui: quem manda ela
        //    entrar (e transformar) e uma fala especifica da cena, via
        //    Gatilho — ver atenderPedidosDaConversa(). Foi assim que a
        //    Adriana parou de aparecer durante o trecho do SANTO JAVA.
        if (!cutsceneClaytonMostrada) {
            cutsceneClaytonMostrada = true;
            Main.mostrarCutscene(Cutscene.criarEncontroClayton());
            return;
        }

        // 2) Sem conversa (jogador ja viu, ou pulou com F2): a chefe entra
        //    direto, ja lutando.
        if (!chefeSpawnado) {

            chefeSpawnado = true;
            // A luta do Clayton acontece DENTRO do DCO (Roteiro.txt
            // linhas 40-41: "Voce vai devagar para o DCO... chegando la").
            Main.fundo.trocarImagem("sprites/ambient/dco.png");

            chefeAtual = criarChefeDoEstagio(false);

            if (chefeAtual != null) {
                Main.enemies.add(chefeAtual);
                chefeAtual.comecarLuta();
            }

            return;
        }

        // 3) Ela morreu?
        if (chefeDerrotado()) {

            limparRestosDoChefe();

            proximoEstagio();
        }
    }

    /**
     * ESTAGIO 5 — Clayton Tab maligno (Roteiro.txt linhas 48 a 57).
     * O ataque final do LaTeX.
     */
    private void stage5() {

        // 1) ABRE A CONVERSA. A chefe NAO nasce aqui: quem manda ela
        //    entrar (e transformar) e uma fala especifica da cena, via
        //    Gatilho — ver atenderPedidosDaConversa(). Foi assim que a
        //    Adriana parou de aparecer durante o trecho do SANTO JAVA.
        if (!cutsceneClaytonTransfMostrada) {
            cutsceneClaytonTransfMostrada = true;
            Main.mostrarCutscene(Cutscene.criarTransformacaoClayton());
            return;
        }

        // 2) Sem conversa (jogador ja viu, ou pulou com F2): a chefe entra
        //    direto, ja lutando.
        if (!chefeSpawnado) {

            chefeSpawnado = true;
            Main.fundo.trocarImagem("sprites/ambient/dco.png");

            chefeAtual = criarChefeDoEstagio(true);

            if (chefeAtual != null) {
                Main.enemies.add(chefeAtual);
                chefeAtual.comecarLuta();
            }

            return;
        }

        // 3) Ela morreu?
        if (chefeDerrotado()) {

            limparRestosDoChefe();

            if (!cutsceneClaytonDerrotaMostrada) {
                cutsceneClaytonDerrotaMostrada = true;
                Main.mostrarCutscene(Cutscene.criarDerrotaClayton());
                return;
            }


            // A LOJINHA DO PEREA, em dois passos: primeiro a conversa,
            // depois a loja em si.
            //
            // Sao dois 'return' seguidos e nao um bloco so porque a
            // conversa e uma SOBREPOSICAO (o jogo continua rodando por
            // baixo) e a loja e um ESTADO (o jogo congela). Emendar os
            // dois no mesmo frame abriria a loja por cima do primeiro
            // balao de fala.
            if (!cutscenePereaMostrada) {
                cutscenePereaMostrada = true;
                Main.mostrarCutscene(Cutscene.criarPerea());
                return;
            }

            if (!lojaVisitada) {
                lojaVisitada = true;
                Main.abrirLoja();
                return;
            }

            proximoEstagio();
        }
    }

    /* =====================================================
       ESTAGIOS 6 e 7 - PAPA (Roteiro.txt linhas 58 a 82)
       =====================================================
       Mesma estrutura de tres passos dos chefes anteriores. A diferenca
       e que o estagio 7 e o ULTIMO: a cutscene de derrota dele e o final
       do jogo, e por isso ela roda antes do proximoEstagio() que fecha a
       fase (ver acabou()).
    */

    private boolean cutscenePapaMostrada = false;
    private boolean cutscenePapaTransfMostrada = false;
    private boolean cutscenePapaDerrotaMostrada = false;

    /**
     * ESTAGIO 6 — PAPA, forma base (Roteiro.txt linhas 58 a 68).
     * Bandeiras e maquina de Turing.
     */
    private void stage6() {

        // 1) ABRE A CONVERSA. A chefe NAO nasce aqui: quem manda ela
        //    entrar (e transformar) e uma fala especifica da cena, via
        //    Gatilho — ver atenderPedidosDaConversa(). Foi assim que a
        //    Adriana parou de aparecer durante o trecho do SANTO JAVA.
        if (!cutscenePapaMostrada) {
            cutscenePapaMostrada = true;
            Main.mostrarCutscene(Cutscene.criarEncontroPapa());
            return;
        }

        // 2) Sem conversa (jogador ja viu, ou pulou com F2): a chefe entra
        //    direto, ja lutando.
        if (!chefeSpawnado) {

            chefeSpawnado = true;
            // A luta final acontece dentro do LEPEC, com tema proprio.
            Main.fundo.trocarImagem("sprites/ambient/lepec.png");

            chefeAtual = criarChefeDoEstagio(false);

            if (chefeAtual != null) {
                Main.enemies.add(chefeAtual);
                chefeAtual.comecarLuta();
            }

            return;
        }

        // 3) Ela morreu?
        if (chefeDerrotado()) {

            limparRestosDoChefe();

            proximoEstagio();
        }
    }

    /**
     * ESTAGIO 7 — PAPA IA (Roteiro.txt linhas 69 a 82).
     * O Optimum Path Forest, e o final do jogo.
     */
    private void stage7() {

        // 1) ABRE A CONVERSA. A chefe NAO nasce aqui: quem manda ela
        //    entrar (e transformar) e uma fala especifica da cena, via
        //    Gatilho — ver atenderPedidosDaConversa(). Foi assim que a
        //    Adriana parou de aparecer durante o trecho do SANTO JAVA.
        if (!cutscenePapaTransfMostrada) {
            cutscenePapaTransfMostrada = true;
            Main.mostrarCutscene(Cutscene.criarTransformacaoPapa());
            return;
        }

        // 2) Sem conversa (jogador ja viu, ou pulou com F2): a chefe entra
        //    direto, ja lutando.
        if (!chefeSpawnado) {

            chefeSpawnado = true;
            // A luta final acontece dentro do LEPEC, com tema proprio.
            Main.fundo.trocarImagem("sprites/ambient/lepec.png");

            chefeAtual = criarChefeDoEstagio(true);

            if (chefeAtual != null) {
                Main.enemies.add(chefeAtual);
                chefeAtual.comecarLuta();
            }

            return;
        }

        // 3) Ela morreu?
        if (chefeDerrotado()) {

            limparRestosDoChefe();

            if (!cutscenePapaDerrotaMostrada) {
                cutscenePapaDerrotaMostrada = true;
                Main.mostrarCutscene(Cutscene.criarDerrotaPapa());
                return;
            }

            Main.fundo.trocarImagem(null);

            proximoEstagio();
        }
    }

    /* =====================================================
       RENDER
       ===================================================== */

    /** Avisos da fase desenhados por cima do campo. */
    public void render(Graphics2D g) {

        // "ESTAGIO N" nos primeiros segundos de cada estagio.
        if (!acabou() && time < 120) {

            g.setFont(new Font("Monospaced", Font.BOLD, 28));
            g.setColor(new Color(255, 255, 255, 200));

            String texto = "ESTAGIO " + stage;
            g.drawString(texto,
                         Main.CAMPO_X + Main.CAMPO_W / 2 - texto.length() * 8,
                         Main.CAMPO_Y + Main.CAMPO_H / 3);
        }

        // Ajuda de tuning: mostra a onda e a COMPOSICAO dela — quais
        // grupos estao nascendo agora e de que lado cada um entra. Com
        // ondas montadas na hora, saber so o numero da onda nao ajuda a
        // reproduzir o que apareceu na tela.
        if (Main.debugMode) {

            StringBuilder composicao = new StringBuilder();

            for (int i = 0; i < gruposAtivos.size(); i++) {

                Grupo g2 = gruposAtivos.get(i);

                composicao.append(i > 0 ? " + " : "")
                          .append(g2.padrao)
                          .append("x").append(g2.total)
                          .append(g2.pelaEsquerda ? "<" : ">");
            }

            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.setColor(new Color(200, 200, 120));
            g.drawString("onda " + ondasLancadas + "/" + totalDeOndas
                       + "  " + composicao,
                         Main.CAMPO_X + 6, Main.CAMPO_Y + 16);
        }
    }

    /**
     * DEBUG: pula direto pro proximo estagio (tecla F2).
     * Limpa a tela antes, senao as balas e inimigos do estagio anterior
     * continuariam vivos no estagio novo.
     */
    public void pularEstagio() {

        // Avisa o chefe antes de largar a lista: sem isso o spell card
        // ativo nunca recebe encerrar(), e o que ele tiver mexido de fora
        // (a trava de movimento da maquina de Turing, por exemplo) fica
        // ligado pra sempre.
        if (chefeAtual != null) {
            chefeAtual.abandonar();
        }

        Main.enemies.clear();
        Main.bullets.clear();

        proximoEstagio();
    }

    /** true quando todos os estagios acabaram. */
    public boolean acabou() {
        return stage > ULTIMO_ESTAGIO;
    }

    /**
     * Numero do ultimo estagio da fase.
     *
     * Constante e nao numero solto porque ele aparece no acabou() e em
     * qualquer conta de progresso — esquecer de atualizar um dos lugares
     * ao acrescentar um chefe deixaria o jogo terminando cedo demais (foi
     * o que aconteceu quando o Clayton entrou).
     */
    public static final int ULTIMO_ESTAGIO = 7;

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public int getTime() {
        return time;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
        this.time = 0;
        this.ondasLancadas = 0;
        this.gruposAtivos.clear();
    }

    /** Quantos grupos estao nascendo agora (0 = a onda ja saiu inteira). */
    public int getGruposAtivos() {
        return gruposAtivos.size();
    }

    public int getOndasLancadas() {
        return ondasLancadas;
    }
}
