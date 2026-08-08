package src.phases;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Random;

import src.Config;
import src.Cutscene;
import src.Main;
import src.enemyTypes.Adriana;
import src.enemyTypes.ArcEnemy;
import src.enemyTypes.Clayton;
import src.enemyTypes.HorizontalEnemy;
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
 * Cada onda sorteia UM padrao de movimento e lanca todos os seus inimigos
 * naquele padrao, um a um, com um atraso entre eles (a "fila"). O sorteio
 * e por peso, configurado no game.properties:
 *
 *     pendulo 50%  |  arco 25%  |  horizontal 25%
 *
 * Sortear por onda (e nao por inimigo) e de proposito: o jogador consegue
 * LER a onda inteira de uma vez e planejar o desvio, em vez de encarar
 * tres padroes embaralhados ao mesmo tempo.
 */
public class phase1 {

    /** Os padroes de movimento que uma onda pode ter. */
    public enum Padrao {
        PENDULO,
        ARCO,
        HORIZONTAL
    }

    /** Cronometro do estagio atual, em ticks. Zera a cada troca de estagio. */
    private int time = 0;

    private int stage = 1;

    /* --- controle das ondas --- */

    private int ondasLancadas = 0;

    /** Padrao sorteado para a onda que esta sendo lancada agora. */
    private Padrao padraoDaOnda = Padrao.PENDULO;

    /** Quantos inimigos da onda atual ainda faltam nascer. */
    private int restantesNaOnda = 0;

    /** Posicao do proximo inimigo dentro da onda (0, 1, 2...). */
    private int indiceNaOnda = 0;

    /** Por qual lado a onda atual entra (vale pra arco e horizontal). */
    private boolean ondaPelaEsquerda = true;

    /** Momento (em tempo de estagio) do proximo nascimento e da proxima onda. */
    private int proximoSpawnEm = 0;
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

    /** Quantos inimigos a onda que esta nascendo agora tem no total. */
    private int inimigosDaOndaAtual;

    private int pesoPendulo;
    private int pesoArco;
    private int pesoHorizontal;

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

        // Semente fixa (>= 0) faz as ondas sairem SEMPRE na mesma ordem,
        // o que ajuda muito a testar um ajuste sem a aleatoriedade atrapalhar.
        long seed = Config.getInt("fase1.seed", -1);
        this.rng = (seed < 0) ? new Random() : new Random(seed);
    }

    public void tick() {

        switch (stage) {
            case 1: stage1(); break;
            case 2: stage2(); break;
            case 3: stage3(); break;
            case 4: stage4(); break;
            case 5: stage5(); break;
            default: break;   // fase acabou
        }

        time++;
    }

    /** Troca de estagio e zera o cronometro. Todo estagio termina chamando isso. */
    private void proximoEstagio() {

        stage++;
        time = 0;

        ondasLancadas = 0;
        restantesNaOnda = 0;
        indiceNaOnda = 0;
        proximoSpawnEm = 0;
        proximaOndaEm = 0;

        // Solta o gatilho do chefe: o proximo estagio de boss precisa
        // spawnar a forma DELE. Os flags de cutscene NAO sao zerados de
        // proposito — cada conversa acontece uma vez por partida.
        chefeSpawnado = false;
    }

    /* =====================================================
       ESTAGIO 1 - ondas de alunos corrompidos
       ===================================================== */

    private void stage1() {

        // Respiro inicial antes do primeiro inimigo aparecer.
        if (time < ticksAteComecar) {
            return;
        }

        int t = time - ticksAteComecar;

        // 1) Hora de comecar uma onda nova?
        //    So depois que a anterior terminou de nascer inteira.
        if (ondasLancadas < totalDeOndas && restantesNaOnda == 0 && t >= proximaOndaEm) {

            prepararOnda(t);

            ondasLancadas++;
            proximaOndaEm = t + intervaloEntreOndas;
        }

        // 2) Nascimento escalonado: um inimigo por vez, em fila.
        if (restantesNaOnda > 0 && t >= proximoSpawnEm) {

            nascerUm();

            restantesNaOnda--;
            indiceNaOnda++;
            proximoSpawnEm = t + atrasoEntreInimigos;
        }

        // 3) So avanca quando as ondas acabaram E a tela ficou limpa.
        if (ondasLancadas >= totalDeOndas && restantesNaOnda == 0 && Main.enemies.isEmpty()) {
            proximoEstagio();
        }
    }

    /** Sorteia o padrao e o lado da proxima onda e arma o contador de nascimentos. */
    private void prepararOnda(int t) {

        padraoDaOnda = sortearPadrao();
        inimigosDaOndaAtual = tamanhoDaOnda(ondasLancadas);

        restantesNaOnda = inimigosDaOndaAtual;
        indiceNaOnda = 0;
        proximoSpawnEm = t;              // o primeiro nasce imediatamente
        ondaPelaEsquerda = rng.nextBoolean();
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

        int total = pesoPendulo + pesoArco + pesoHorizontal;

        if (total <= 0) {
            return Padrao.PENDULO;   // config zerada: nao trava o jogo
        }

        int sorteio = rng.nextInt(total);

        if (sorteio < pesoPendulo) {
            return Padrao.PENDULO;
        }

        if (sorteio < pesoPendulo + pesoArco) {
            return Padrao.ARCO;
        }

        return Padrao.HORIZONTAL;
    }

    /** Cria o proximo inimigo da onda atual, conforme o padrao sorteado. */
    private void nascerUm() {

        switch (padraoDaOnda) {

            case PENDULO:
                // Espalhados pela largura do campo. Como nascem escalonados,
                // formam uma cascata diagonal em vez de uma linha reta.
                Main.enemies.add(new PendulumEnemy(colunaDoIndice()));
                break;

            case ARCO:
                // Todos do mesmo lado e na mesma altura: fila indiana
                // percorrendo exatamente o mesmo arco.
                Main.enemies.add(new ArcEnemy(
                    ondaPelaEsquerda,
                    Config.getDouble("inimigo.arco.entradaRelY", 0.55)
                ));
                break;

            case HORIZONTAL:
                // Alturas escalonadas dentro de uma faixa, pra travessia
                // virar uma diagonal e nao uma parede.
                Main.enemies.add(new HorizontalEnemy(ondaPelaEsquerda, alturaDoIndice()));
                break;

            default:
                break;
        }
    }

    /** Distribui os inimigos por igual na largura do campo, sem colar nas bordas. */
    private double colunaDoIndice() {
        return Main.CAMPO_X + Main.CAMPO_W * (indiceNaOnda + 1.0) / (inimigosDaOndaAtual + 1.0);
    }

    /** Interpola a altura de entrada dentro da faixa configurada. */
    private double alturaDoIndice() {

        double min = Config.getDouble("inimigo.horizontal.entradaRelYMin", 0.10);
        double max = Config.getDouble("inimigo.horizontal.entradaRelYMax", 0.45);

        if (inimigosDaOndaAtual <= 1) {
            return min;
        }

        return min + (max - min) * indiceNaOnda / (inimigosDaOndaAtual - 1.0);
    }

    /* =====================================================
       ESTAGIOS 2 e 3 - ADRIANA (Roteiro.txt linhas 19 a 40)
       =====================================================
       Os dois seguem o mesmo roteiro de tres passos:

         1. mostrar a cutscene (uma vez so — o flag evita repetir quando
            o jogo volta do estado "Cutscene" e o tick recomeca)
         2. spawnar a forma da chefe
         3. esperar Main.enemies esvaziar pra avancar

       O passo 1 CONGELA o jogo: Main.mostrarCutscene troca o gameState,
       entao phase1.tick() nem roda enquanto a conversa acontece. Quando
       ela acaba, o tick volta e o flag ja esta marcado.
    */

    /** Flags de "esta cutscene ja foi exibida", pra nao repetir a cada tick. */
    private boolean cutsceneEncontroMostrada = false;
    private boolean cutsceneTransformacaoMostrada = false;
    private boolean cutsceneDerrotaMostrada = false;

    /** Marca que a chefe da forma atual ja nasceu. */
    private boolean chefeSpawnado = false;

    /**
     * ESTAGIO 2 — Adriana, forma base (Roteiro.txt linhas 11 a 26).
     * Cutscene do encontro + ataques de integral e somatorio.
     */
    private void stage2() {

        if (!cutsceneEncontroMostrada) {
            cutsceneEncontroMostrada = true;
            Main.mostrarCutscene(Cutscene.criarEncontroAdriana());
            return;
        }

        if (!chefeSpawnado) {
            chefeSpawnado = true;
            // A luta acontece na frente da sala 7 — o fundo acompanha.
            Main.fundo.trocarImagem("sprites/ambient/sala7.png");
            Main.enemies.add(Adriana.criarFormaBase());
            return;
        }

        // Espera um pouco antes de checar: no tick do spawn a lista ainda
        // nao refletiu a chefe e o estagio passaria direto.
        if (time > 10 && Main.enemies.isEmpty()) {
            proximoEstagio();
        }
    }

    /**
     * ESTAGIO 3 — Adriana, forma integral maligna (Roteiro.txt linhas 27 a 40).
     * Cutscene da transformacao + area de Riemann e a esfera 3D.
     */
    private void stage3() {

        if (!cutsceneTransformacaoMostrada) {
            cutsceneTransformacaoMostrada = true;
            Main.mostrarCutscene(Cutscene.criarTransformacaoAdriana());
            return;
        }

        if (!chefeSpawnado) {
            chefeSpawnado = true;
            Main.fundo.trocarImagem("sprites/ambient/sala7.png");
            Main.enemies.add(Adriana.criarFormaMaligna());
            return;
        }

        if (time > 10 && Main.enemies.isEmpty()) {

            // Cutscene de derrota antes de fechar o arco da Adriana.
            if (!cutsceneDerrotaMostrada) {
                cutsceneDerrotaMostrada = true;
                Main.mostrarCutscene(Cutscene.criarDerrotaAdriana());
                return;
            }

            // Arco da Adriana fechado: o cenario volta ao caminho do DCO.
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
     * ESTAGIO 4 — Clayton, forma base (Roteiro.txt linhas 41 a 46).
     * Xadrez e claytonlings.
     */
    private void stage4() {

        if (!cutsceneClaytonMostrada) {
            cutsceneClaytonMostrada = true;
            Main.mostrarCutscene(Cutscene.criarEncontroClayton());
            return;
        }

        if (!chefeSpawnado) {
            chefeSpawnado = true;
            Main.enemies.add(Clayton.criarFormaBase());
            return;
        }

        if (time > 10 && Main.enemies.isEmpty()) {
            proximoEstagio();
        }
    }

    /**
     * ESTAGIO 5 — Clayton Tab maligno (Roteiro.txt linhas 48 a 57).
     * O ataque final do LaTeX.
     */
    private void stage5() {

        if (!cutsceneClaytonTransfMostrada) {
            cutsceneClaytonTransfMostrada = true;
            Main.mostrarCutscene(Cutscene.criarTransformacaoClayton());
            return;
        }

        if (!chefeSpawnado) {
            chefeSpawnado = true;
            Main.enemies.add(Clayton.criarFormaMaligna());
            return;
        }

        if (time > 10 && Main.enemies.isEmpty()) {

            if (!cutsceneClaytonDerrotaMostrada) {
                cutsceneClaytonDerrotaMostrada = true;
                Main.mostrarCutscene(Cutscene.criarDerrotaClayton());
                return;
            }

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

        // Ajuda de tuning: mostra qual onda e qual padrao esta rolando.
        if (Main.debugMode) {

            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.setColor(new Color(200, 200, 120));
            g.drawString("onda " + ondasLancadas + "/" + totalDeOndas
                       + "  " + padraoDaOnda
                       + (ondaPelaEsquerda ? " <esq" : " dir>"),
                         Main.CAMPO_X + 6, Main.CAMPO_Y + 16);
        }
    }

    /**
     * DEBUG: pula direto pro proximo estagio (tecla F2).
     * Limpa a tela antes, senao as balas e inimigos do estagio anterior
     * continuariam vivos no estagio novo.
     */
    public void pularEstagio() {

        Main.enemies.clear();
        Main.bullets.clear();

        proximoEstagio();
    }

    /** true quando todos os estagios acabaram. */
    public boolean acabou() {
        return stage > 5;
    }

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
        this.restantesNaOnda = 0;
    }

    public Padrao getPadraoDaOnda() {
        return padraoDaOnda;
    }

    public int getOndasLancadas() {
        return ondasLancadas;
    }
}
