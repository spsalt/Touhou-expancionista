package src;

import javax.swing.*;

import src.bulletTypes.*;
import src.enemyTypes.Enemy;
import src.phases.phase1;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * Janela, game loop e orquestracao geral.
 *
 * Estrutura (ver README):
 *   - tudo se resume a tick() (logica) e render() (desenho)
 *   - a tela atual e a String estatica gameState:
 *     "Menu" | "Game" | "Pause" | "Continue" | "GameOver" | "Vitoria"
 *
 * NAO existe estado "Cutscene": dialogo e uma SOBREPOSICAO desenhada por
 * cima do jogo rodando (ver mostrarCutscene e emDialogo).
 *   - o loop roda numa Thread propria a ~60 ticks/s, e so pede repaint()
 *     ao JPanel; o desenho em si acontece na thread do Swing
 *
 * Teclas globais: ESC pausa, F5 recarrega o game.properties sem fechar o jogo.
 */
public class Main extends JFrame implements Runnable, KeyListener {

    /* =========================
            CONTROLS
       =========================
       Flags de teclado. O KeyListener so liga/desliga; quem interpreta
       sao os tick() de cada objeto. Assim o input nao depende da taxa
       de repeticao do teclado do sistema.
    */

    public static boolean up = false;
    public static boolean down = false;
    public static boolean left = false;
    public static boolean right = false;

    public static boolean z = false;
    public static boolean x = false;
    public static boolean c = false;
    public static boolean v = false;
    public static boolean enter = false;
    public static boolean esc = false;

    /**
     * ULTIMA LETRA/NUMERO DIGITADO, esperando ser lido.
     *
     * O resto do jogo trata teclado como ESTADO (a tecla esta apertada ou
     * nao) porque e isso que movimento e tiro precisam. A maquina de
     * Turing do PAPA precisa do contrario: um EVENTO, "a tecla K foi
     * apertada agora", uma vez so por aperto.
     *
     * Por isso ela vive aqui como uma caixinha de uma posicao: o
     * keyPressed deposita, quem le CONSOME com consumirTeclaDigitada() e
     * a caixa volta a ficar vazia. Sem esse consumo, segurar a tecla
     * preencheria a fita inteira sozinho.
     *
     * volatile porque quem escreve e a thread de eventos do Swing e quem
     * le e a thread do jogo: sem isso a segunda pode nunca enxergar o
     * valor que a primeira gravou.
     */
    private static volatile char teclaDigitada = 0;

    /* =========================
            ARRAYLISTS
       ========================= */

    public static ArrayList<Bullet> bullets = new ArrayList<Bullet>();
    public static ArrayList<Point> points = new ArrayList<Point>();
    public static ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    public static ArrayList<GptExpansion> efeitosGpt = new ArrayList<GptExpansion>();

    /**
     * As tralhas que voam do estudante quando ele morre (ver Destroco).
     *
     * Lista separada das balas de proposito: destroco nao colide com
     * nada, entao passar ele pelos loops de colisao seria trabalho jogado
     * fora sessenta vezes por segundo.
     */
    public static ArrayList<Destroco> destrocos = new ArrayList<Destroco>();

    /**
     * Os estouros de cor das transformacoes (ver Explosao).
     *
     * Lista propria pelo mesmo motivo dos destrocos: explosao nao colide
     * com nada, entao passar ela pelos loops de colisao seria trabalho
     * jogado fora sessenta vezes por segundo.
     */
    public static ArrayList<Explosao> explosoes = new ArrayList<Explosao>();

    /**
     * Os itens comprados na lojinha do Perea, em acao.
     *
     * Listas separadas (e nao uma so de "efeitos") porque os dois tem
     * regras de unicidade proprias: so pode haver UM raio e UMA nuvem por
     * vez, e o Player checa isso olhando se a lista esta vazia.
     */
    public static ArrayList<OlhoLaser> olhosLaser = new ArrayList<OlhoLaser>();

    /**
     * A cerimonia do "ESPANDAAAAA". Lista de um elemento so, na pratica —
     * ela e uma lista pra herdar de graca o tick/render/limpeza que todo
     * efeito do jogo ja tem, em vez de virar mais um campo com if.
     */
    public static ArrayList<AscensaoDaArmadura> ascensoes = new ArrayList<AscensaoDaArmadura>();
    public static ArrayList<AgriculturaDigital> agriculturas = new ArrayList<AgriculturaDigital>();

    /* =========================
            STARTUP
       ========================= */

    public static Player player;
    static Menu menu;

    /** A tela de "continue?" que aparece quando as vidas acabam. */
    public static MenuDeContinue menuDeContinue;

    /** A lojinha do Perea. Abre uma vez, entre o Clayton e o PAPA. */
    public static LojaDoPerea loja;

    /**
     * A sequencia final: a foto no uba e o agradecimento.
     *
     * Criada NO STARTUP junto com os outros menus, e nao na hora de
     * mostrar. Isso nao e organizacao, e correcao de bug: o objeto era
     * criado na thread do JOGO e lido na thread do SWING, sem nenhuma
     * barreira de memoria entre as duas. A thread do desenho podia
     * enxergar o gameState ja em "Creditos" e o campo ainda em null —
     * NullPointerException no primeiro frame da tela final, de forma
     * intermitente e impossivel de reproduzir de proposito.
     *
     * Existindo desde o inicio, nao ha instante nenhum em que ela seja
     * null. E o mesmo padrao do menu de continue e da lojinha, que nunca
     * deram problema justamente por isso.
     */
    public static Creditos creditos = new Creditos();

    /**
     * Quantos continues o jogador gastou nesta partida.
     *
     * Zero = a partida ainda pode terminar como vitoria limpa. Qualquer
     * numero acima disso muda a tela de vitoria: o jogo chegou ao fim,
     * mas nao foi derrotado. E so por isso que este contador existe — nao
     * ha nenhuma penalidade mecanica ligada a ele.
     */
    public static int continuesUsados = 0;
    static Hud hud;
    static phase1 fase;
    /** Publico porque as fases trocam o cenario (ver phase1: a luta da
     *  Adriana acontece na sala 7). Mesma visibilidade de player/bullets. */
    public static Background fundo;
    /**
     * A conversa na tela agora, ou null. E sobreposicao, nao estado.
     *
     * Publica porque a fase precisa ler os GATILHOS dela (a fala que manda
     * a chefe entrar ou se transformar). Mesma visibilidade de player e
     * bullets, pelo mesmo motivo: o mundo inteiro conversa com ela.
     */
    public static Cutscene cutsceneAtual;

    /** Publica porque as fases trocam o tema (o PAPA tem o proprio). */
    public static Musica musica;

    /* =========================
            CONFIGURATION
       =========================
       Vem do config/game.properties. Sao final porque mexer no tamanho da
       janela e do campo no meio da partida nao faz sentido; o resto dos
       ajustes (velocidade, HP, cadencia) e recarregavel em tempo real.
    */

    public static final int WIDTH  = Config.getInt("janela.largura", 1080);
    public static final int HEIGHT = Config.getInt("janela.altura", 720);
    public static final int TARGET_FPS = Config.getInt("janela.fps", 60);

    /** Campo de jogo: a arena onde as balas valem. O resto e painel lateral. */
    public static final int CAMPO_X = Config.getInt("campo.x", 32);
    public static final int CAMPO_Y = Config.getInt("campo.y", 16);
    public static final int CAMPO_W = Config.getInt("campo.largura", 600);
    public static final int CAMPO_H = Config.getInt("campo.altura", 688);

    public static final double MARGEM_SAIDA_BALA = Config.getDouble("bala.margemSaida", 64);

    /* =========================
            GAME STATE
       ========================= */

    private Thread gameThread;
    private volatile boolean running;

    public static String gameState = "Menu";

    /**
     * MODO DEBUG (tecla F3): chave-mestra de todas as ajudas de
     * desenvolvimento — FPS, hitbox dos inimigos, info da onda, painel de
     * estado e o pulo de estagio (F2).
     *
     * Comeca no valor de debug.modoDebug, mas da pra ligar/desligar em
     * jogo, entao nao precisa reiniciar pra apresentar.
     */
    public static boolean debugMode = Config.getBool("debug.modoDebug", false);

    /** Trava pra ESC nao alternar pause 60x por segundo enquanto segurado. */
    private boolean escAnterior = false;
    private boolean f5Anterior = false;
    private boolean f2Anterior = false;
    private boolean f3Anterior = false;

    /** FPS medido, mostrado no HUD quando debug.mostrarFps=true. */
    private static int fpsAtual = 0;

    private final GamePanel panel;

    /* =========================
            CONSTRUCTOR
       ========================= */

    public Main() {

        super("Touhou Expancionista");

        // AS SKINS PRIMEIRO, antes de tudo que desenha.
        //
        // Carregar aqui e nao "na primeira vez que alguem pedir" e o que
        // garante que os retratos do protagonista na cutscene (que sao
        // reescritos pelo Skin.carregar) ja estejam certos mesmo que a
        // primeira coisa a acontecer seja a cena de introducao.
        Skin.carregar();

        // IMPORTANTE: os objetos do jogo nascem ANTES de setVisible(true).
        // O Swing pode pintar o painel assim que a janela aparece, e se
        // player/menu ainda fossem null a render() quebraria com NullPointer.
        player = new Player(
            CAMPO_X + CAMPO_W * Config.getDouble("jogador.spawnRelX", 0.5),
            CAMPO_Y + CAMPO_H * Config.getDouble("jogador.spawnRelY", 0.8),
            Config.getDouble("jogador.raioHitbox", 5.0)
        );

        menu   = new Menu();
        menuDeContinue = new MenuDeContinue();
        loja = new LojaDoPerea();
        hud    = new Hud();
        fase   = new phase1();
        fundo  = new Background();
        musica = new Musica();

        panel = new GamePanel();

        // setPreferredSize + pack: garante que a AREA DE DESENHO tenha
        // WIDTH x HEIGHT. Com setSize() a borda da janela come alguns pixels
        // e o campo de jogo fica cortado no canto inferior.
        panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        add(panel);
        pack();

        // Clique do mouse no botao da GPT Expansion no HUD. Vai direto no
        // 'panel' (nao no JFrame): assim (e.getX(), e.getY()) ja vem nas
        // mesmas coordenadas que Hud.getBotaoGptExpansaoBounds() usa pra
        // desenhar, sem precisar descontar borda/titulo da janela.
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                tratarCliqueDoMouse(e.getX(), e.getY());
            }
        });

        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        addKeyListener(this);
        setVisible(true);
        setFocusable(true);
        requestFocusInWindow();
    }

    public static void main(String[] args) {

        Main game = new Main();
        game.start();
    }

    public void start() {

        if (running) {
            return;
        }

        running = true;

        gameThread = new Thread(this, "GameThread");
        gameThread.start();
    }

    public void stop() {
        running = false;
    }

    /* =========================
            GAME LOOP
       ========================= */

    @Override
    public void run() {

        final double nsPerFrame = 1_000_000_000.0 / TARGET_FPS;

        long lastTime = System.nanoTime();
        double delta = 0;

        long timer = System.currentTimeMillis();
        int fps = 0;

        while (running) {

            long now = System.nanoTime();

            delta += (now - lastTime) / nsPerFrame;
            lastTime = now;

            // delta-- (e nao delta = 0): se um frame demorar demais, o loop
            // "paga a divida" rodando os ticks atrasados, e a velocidade do
            // jogo continua a mesma em qualquer maquina.
            while (delta >= 1) {

                tick();
                panel.repaint();

                fps++;
                delta--;
            }

            if (System.currentTimeMillis() - timer >= 1000) {

                fpsAtual = fps;
                fps = 0;
                timer += 1000;
            }

            // Devolve a CPU pro sistema entre frames. Sem isso a thread
            // gira em busy-wait e o notebook vira um secador de cabelo.
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    /* =========================
           GAME UPDATE
       ========================= */

    private void tick() {

        tratarTeclasGlobais();

        if (gameState.equals("Menu")) {

            menu.tick();

        } else if (gameState.equals("Game")) {

            tickDoJogo();

        } else if (gameState.equals("Continue")) {

            // O mundo fica parado: so o menu responde. As balas
            // continuam onde estavam, a vista do que matou o jogador.
            menuDeContinue.tick();

        } else if (gameState.equals("Creditos")) {

            creditos.tick();

            if (creditos.acabou()) {
                reiniciarPartida();
                musica.parar();
                gameState = "Menu";
            }

        } else if (gameState.equals("Loja")) {

            // Mesma ideia do Continue: o jogo congela e so a loja
            // responde. A fase nao avanca porque tickDoJogo() nem roda —
            // e o que segura o estagio 5 esperando o jogador terminar as
            // compras antes de seguir pro PAPA.
            loja.tick();

        } else if (gameState.equals("GameOver") || gameState.equals("Vitoria")) {

            // As telas de fim de partida so esperam o ENTER.
            if (enter) {
                enter = false;

                // reiniciarPartida() devolve a trilha da fase E COMECA a
                // tocar; por isso o parar() vem DEPOIS dela. Na ordem
                // inversa, quem morresse pro PAPA voltava pro menu com o
                // tema dele tocando.
                reiniciarPartida();
                musica.parar();

                gameState = "Menu";
            }
        }
        // "Pause" nao atualiza nada de proposito: a cena congela e so
        // o render continua rodando.
    }

    /** Um frame de partida: fase, jogador, inimigos, balas, itens, colisoes. */
    private void tickDoJogo() {

        // O DIALOGO roda POR CIMA do jogo, e nao no lugar dele.
        //
        // Tudo continua vivo: o fundo rola, o jogador anda e atira, o
        // chefe entra voando. O que a conversa segura e so o avanco da
        // FASE (nenhuma onda nova nasce) e o ataque do chefe.
        if (emDialogo()) {

            cutsceneAtual.tick();

            if (cutsceneAtual.acabou()) {
                terminarDialogo();
            }
        }

        fundo.tick();
        fase.tick();
        player.tick();

        // Indices em vez de for-each: durante o tick os objetos podem
        // ADICIONAR novos elementos nas listas (um inimigo atira, um chefe
        // divide uma bala), e o for-each lancaria ConcurrentModificationException.
        for (int i = 0; i < enemies.size(); i++) {
            enemies.get(i).tick();
        }

        // A posicao e guardada ANTES do tick pra a bala poder responder
        // depois quanto andou neste frame. E dai que sai a velocidade real
        // de qualquer bala, inclusive das que curvam e das que perseguem
        // (ver Bullet.guardarPosicao e o buff do Paiola).
        for (int i = 0; i < bullets.size(); i++) {
            bullets.get(i).guardarPosicao();
        }

        for (int i = 0; i < bullets.size(); i++) {
            bullets.get(i).tick();
        }

        for (int i = 0; i < points.size(); i++) {
            points.get(i).tick();
        }

        for (int i = 0; i < efeitosGpt.size(); i++) {
            efeitosGpt.get(i).tick();
        }

        for (int i = 0; i < destrocos.size(); i++) {
            destrocos.get(i).tick();
        }

        for (int i = 0; i < explosoes.size(); i++) {
            explosoes.get(i).tick();
        }

        for (int i = 0; i < olhosLaser.size(); i++) {
            olhosLaser.get(i).tick();
        }

        for (int i = 0; i < ascensoes.size(); i++) {
            ascensoes.get(i).tick();
        }

        for (int i = 0; i < agriculturas.size(); i++) {
            agriculturas.get(i).tick();
        }

        colidirBalasComInimigos();

        // Faxina no fim do frame, nunca no meio dos loops acima.
        bullets.removeIf(b -> !b.isAlive());
        points.removeIf(p -> !p.isAlive());
        enemies.removeIf(e -> !e.isAlive());
        efeitosGpt.removeIf(f -> !f.isAlive());
        destrocos.removeIf(d -> !d.isAlive());
        explosoes.removeIf(e -> !e.isAlive());
        olhosLaser.removeIf(o -> !o.isAlive());
        ascensoes.removeIf(a -> !a.isAlive());
        agriculturas.removeIf(a -> !a.isAlive());

        if (fase.acabou()) {

            // Acabou a historia: em vez da cartela de "FASE LIMPA", a foto
            // do pessoal no uba e os numeros da partida (ver Creditos).
            // A tela antiga dizia que voce venceu; esta mostra por que
            // valeu a pena.
            mostrarCreditos();
        }
    }

    /**
     * Balas do jogador (hitPlayer == false) contra inimigos.
     * Colisao circulo-circulo: encosta se a distancia for menor que a soma
     * dos raios. Uma bala some no primeiro inimigo que acerta.
     */
    private void colidirBalasComInimigos() {

        for (int i = 0; i < bullets.size(); i++) {

            Bullet bala = bullets.get(i);

            if (bala.isHitPlayer() || !bala.isAlive()) {
                continue;
            }

            for (int j = 0; j < enemies.size(); j++) {

                Enemy inimigo = enemies.get(j);

                if (!inimigo.isAlive()) {
                    continue;
                }

                double dist = getDist(bala.getX(), bala.getY(), inimigo.getX(), inimigo.getY());

                if (dist <= bala.getRadius() + inimigo.getRadius()) {

                    inimigo.levarDano(bala.getDano());
                    bala.setAlive(false);

                    break;
                }
            }
        }
    }

    /**
     * Exibe uma cutscene e congela o jogo ate ela acabar.
     *
     * Quem chama nao precisa se preocupar em voltar pro jogo: quando a
     * cena termina, o tick() devolve o gameState pra "Game" sozinho. E o
     * que deixa a fase (phase1) pedir uma cutscene no meio de um estagio
     * com uma linha so.
     */
    public static void mostrarCutscene(Cutscene cena) {

        if (cena == null) {
            return;
        }

        cutsceneAtual = cena;
        cutsceneAtual.reiniciar();

        // NAO troca de estado. O dialogo e uma SOBREPOSICAO: o jogo
        // continua rodando por baixo — o fundo rola, o jogador anda, o
        // chefe entra voando. E o que a serie faz, e e o que impede a
        // conversa de parecer um recorte colado no meio da partida.
        //
        // Quem segura a fase (nao nasce onda nova durante a conversa) e o
        // proprio phase1.tick(), e quem segura o chefe e o modo de
        // dialogo do BossEnemy.
        gameState = "Game";

        // Limpa o que ja estava voando: bala inimiga sobrando cairia em
        // cima do jogador enquanto ele le, sem que nada na tela explique
        // de onde veio.
        for (int i = 0; i < bullets.size(); i++) {
            if (bullets.get(i).isHitPlayer()) {
                bullets.get(i).setAlive(false);
            }
        }
    }

    /**
     * Abre a lojinha do Perea e congela o jogo ate ela fechar.
     *
     * Diferente da cutscene, que e sobreposicao: aqui o mundo PARA de
     * verdade (o tick() nem chama tickDoJogo). E o unico momento do jogo
     * em que isso acontece fora do pause — e proposital, porque comprar
     * upgrade enquanto bala voa nao seria uma decisao, seria um acidente.
     */
    /**
     * Fecha o jogo: a foto no uba e o agradecimento.
     *
     * Substitui a tela de "FASE LIMPA". Aquela dizia que voce venceu; esta
     * mostra POR QUE valeu a pena — o pessoal reunido — e devolve os
     * numeros da sua partida.
     */
    public static void mostrarCreditos() {

        // reiniciar() e nao "new": ver o comentario do campo. O objeto
        // existe desde o startup; aqui ele so volta pro comeco e
        // fotografa os numeros desta partida.
        creditos.reiniciar();
        gameState = "Creditos";
    }

    public static void abrirLoja() {

        loja.reiniciar();
        gameState = "Loja";
    }

    /** true enquanto a cerimonia do ESPANDAAAAA estiver rodando. */
    public static boolean emAscensao() {
        return !ascensoes.isEmpty();
    }

    /**
     * true so enquanto a energia ainda esta JUNTANDO — ou seja, ate a
     * armadura fechar.
     *
     * A cerimonia continua viva alguns segundos depois disso (o rabo da
     * musica, os ultimos fiapos sumindo). Travar a conversa por todo esse
     * tempo deixaria o jogador olhando pra uma tela onde nao acontece mais
     * nada, sem poder avancar. O que precisa ser protegido e o EVENTO, nao
     * o efeito sonoro dele.
     */
    public static boolean armaduraSeFormando() {

        for (int i = 0; i < ascensoes.size(); i++) {
            if (ascensoes.get(i).estaJuntandoEnergia()) {
                return true;
            }
        }

        return false;
    }

    /** true enquanto uma conversa esta na tela. */
    public static boolean emDialogo() {
        return cutsceneAtual != null && !cutsceneAtual.acabou();
    }

    /**
     * Clique do mouse: hoje so serve pro botao da GPT Expansion no HUD,
     * mas fica separado do resto do input de teclado de proposito — e o
     * unico lugar do jogo que precisa de coordenada de mouse.
     */
    private void tratarCliqueDoMouse(int mouseX, int mouseY) {

        if (!gameState.equals("Game") || player == null) {
            return;
        }

        // Travado (maquina de Turing) o botao tambem nao responde — senao
        // o clique seria uma brecha pra burlar a trava do teclado.
        if (!player.isTravado() && hud.getBotaoGptExpansaoBounds().contains(mouseX, mouseY)) {
            player.usarGptExpansao();
        }
    }

    /** ESC pausa/despausa, F5 recarrega os ajustes do disco. */
    private void tratarTeclasGlobais() {

        // Borda de subida: so age no frame em que a tecla foi apertada.
        if (esc && !escAnterior) {

            if (gameState.equals("Game")) {
                gameState = "Pause";
                Som.tocar(Som.PAUSA);
                musica.pausar();
            } else if (gameState.equals("Pause")) {
                gameState = "Game";
                musica.continuar();
            }
        }
        escAnterior = esc;

        if (f5 && !f5Anterior) {
            recarregarAjustes();
        }
        f5Anterior = f5;

        // F3 liga/desliga o modo debug.
        if (f3 && !f3Anterior) {
            debugMode = !debugMode;
            System.out.println("[Main] Modo debug: " + (debugMode ? "ON" : "OFF"));
        }
        f3Anterior = f3;

        // F2 pula pro proximo estagio. So no modo debug, pra ninguem pular
        // a fase sem querer durante a apresentacao.
        if (f2 && !f2Anterior && debugMode && gameState.equals("Game")) {
            fase.pularEstagio();
            System.out.println("[Main] Estagio pulado (F2).");
        }
        f2Anterior = f2;
    }

    /**
     * Hot-reload: rele o game.properties e os sprites sem fechar o jogo.
     * Serve pra tunar jogabilidade rapido. Nao reinicia a partida.
     */
    private void recarregarAjustes() {

        Config.recarregar();
        Assets.limparCache();

        if (player != null) {
            player.carregarConfig();
        }

        if (fase != null) {
            fase.carregarConfig();
        }

        if (fundo != null) {
            fundo.carregarConfig();
        }

        if (cutsceneAtual != null) {
            cutsceneAtual.carregarConfig();
        }

        Som.carregarConfig();

        // As skins tambem: assim da pra mexer na paleta de um personagem e
        // ver o resultado no mesmo F5, sem fechar o jogo. A ESCOLHA nao se
        // perde — ver o comentario no Skin.carregar().
        Skin.carregar();

        if (musica != null) {
            // Reabrir o clip corta a musica que estava tocando. Se a partida
            // ja estava em andamento, retoma do zero em vez de ficar mudo.
            musica.carregarConfig();

            if (gameState.equals("Game")) {
                musica.tocarDoInicio();
            }
        }

        System.out.println("[Main] Ajustes recarregados.");
    }

    /**
     * Retoma a partida depois do jogador aceitar o continue.
     *
     * Limpa so as balas INIMIGAS (as dele podem ficar), devolve vidas e
     * espalha os simbolos dourados de FULL em volta dele. Nao mexe na
     * fase nem no chefe: o ponto do continue e voltar exatamente pra onde
     * voce estava, e nao recomecar o estagio.
     */
    public static void continuarPartida() {

        if (player == null) {
            return;
        }

        continuesUsados++;

        for (int i = 0; i < bullets.size(); i++) {
            if (bullets.get(i).isHitPlayer()) {
                bullets.get(i).setAlive(false);
            }
        }

        player.reviver(Config.getInt("continue.vidas", 4),
                       Config.getInt("continue.bombas", 3));

        soltarSimbolosFull(player.getX(), player.getY());

        Som.tocar(Som.SPELL_INICIA);

        gameState = "Game";
    }

    /**
     * Espalha os simbolos dourados da UNESP em volta de (x, y).
     *
     * Eles nascem espalhados numa linha ABAIXO do jogador e SOBEM (ver
     * Point.tickFull). Nao vem atras dele: sao ele que tem que ir atras,
     * subindo o campo enquanto desvia. O poder maximo continua sendo a
     * recompensa por continuar, mas nao cai no colo.
     *
     * Nascer abaixo e nao em volta importa: assim os simbolos PASSAM pelo
     * jogador subindo, e ele tem uma janela pra interceptar cada um em vez
     * de precisar decidir tudo no mesmo instante.
     */
    private static void soltarSimbolosFull(double x, double y) {

        int quantidade = Math.max(1, Config.getInt("continue.simbolosFull", 6));

        double largura = Config.getDouble("continue.larguraDoLeque", 320);
        double abaixo  = Config.getDouble("continue.alturaDeSaida", 90);

        for (int i = 0; i < quantidade; i++) {

            // Distribuidos por igual na largura, e nao sorteados: dois
            // simbolos nascendo colados seriam um so na pratica.
            double f = (quantidade == 1) ? 0.5 : i / (double) (quantidade - 1);
            double px = x - largura / 2 + largura * f;

            px = Math.max(CAMPO_X + 24, Math.min(CAMPO_X + CAMPO_W - 24, px));

            // Escalona a altura em zigue-zague pra eles nao subirem em
            // bloco: assim da pra pegar um, descer, e pegar o proximo.
            double py = y + abaixo + (i % 2) * 46;

            py = Math.min(CAMPO_Y + CAMPO_H - 20, py);

            points.add(new Point(px, py, false, Point.Tipo.FULL));
        }
    }

    /** Zera a partida (usado ao sair do menu e no game over). */
    public static void reiniciarPartida() {

        bullets.clear();
        points.clear();
        enemies.clear();
        efeitosGpt.clear();
        destrocos.clear();
        explosoes.clear();
        olhosLaser.clear();
        agriculturas.clear();
        ascensoes.clear();

        player = new Player(
            CAMPO_X + CAMPO_W * Config.getDouble("jogador.spawnRelX", 0.5),
            CAMPO_Y + CAMPO_H * Config.getDouble("jogador.spawnRelY", 0.8),
            Config.getDouble("jogador.raioHitbox", 5.0)
        );

        fase = new phase1();

        // Rede de seguranca: nenhum ataque pode deixar o jogador travado
        // atravessando o reinicio (ver a maquina de Turing do PAPA).
        player.setTravado(false);

        // E nenhum PROGRESSO pode atravessar tambem: buff do Paiola,
        // armadura, compras do Perea, moedas e nivel. O player acima ja e
        // novo em folha, mas dizer isso em voz alta e o que impede o bug
        // de voltar no dia em que alguem reaproveitar o objeto.
        player.zerarProgressoDaPartida();

        // A trilha volta pro que a fase usa fora das lutas — hoje,
        // SILENCIO. Sem isso, morrer pro PAPA e comecar de novo deixaria
        // o tema DELE tocando desde o estagio 1.
        if (musica != null) {
            musica.trocarFaixa(Config.getString("musica.arquivo", ""));
        }

        // O fundo volta pro cenario padrao. Sem esta linha, morrer na
        // Adriana e reiniciar deixava a sala 7 de fundo desde o estagio 1
        // — o cenario e trocado pela fase, mas quem zera a partida e aqui.
        if (fundo != null) {
            fundo.trocarImagem(null);
            fundo.setOffset(0);
        }

        cutsceneAtual = null;
        continuesUsados = 0;
    }

    /* =========================
           MATEMATICA AUXILIAR
       ========================= */

    public static double getDist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    /** Componente Y unitario do vetor que vai de (x2,y2) ate (x1,y1). */
    public static double getSin(double x1, double y1, double x2, double y2) {

        double d = getDist(x1, y1, x2, y2);

        if (d == 0) {
            return 0;   // evita divisao por zero quando os pontos coincidem
        }

        return (y1 - y2) / d;
    }

    /** Componente X unitario do vetor que vai de (x2,y2) ate (x1,y1). */
    public static double getCos(double x1, double y1, double x2, double y2) {

        double d = getDist(x1, y1, x2, y2);

        if (d == 0) {
            return 0;
        }

        return (x1 - x2) / d;
    }

    /**
     * O inimigo vivo mais proximo de (px, py), ou null se nao houver
     * nenhum. Usado pelas balas teleguiadas do jogador.
     *
     * Compara a distancia AO QUADRADO em vez da distancia: evita uma raiz
     * quadrada por inimigo por bala por tick, e a ordem e a mesma.
     */
    /**
     * Fecha a conversa: solta o chefe pra lutar e volta a trilha.
     *
     * continuar() liga a musica se ela nao estiver tocando e nao faz nada
     * se ja estiver — serve tanto pra intro (onde a trilha ainda nem
     * comecou) quanto pras conversas do meio da fase, que nao podem
     * reiniciar a musica no meio.
     */
    private void terminarDialogo() {

        cutsceneAtual = null;

        Som.tocar(Som.PAUSA);
        musica.continuar();

        if (chefeEmCena() != null) {
            chefeEmCena().comecarLuta();
        }
    }

    /**
     * O chefe que esta em cena agora, ou null se a fase esta em ondas.
     *
     * Existe pro jogador poder avisar o chefe de que "sujou" o spell card
     * atual (morreu ou bombou). Percorrer a lista e barato: ela tem menos
     * de uma duzia de itens, e so uma pode ser chefe.
     */
    public static src.enemyTypes.BossEnemy chefeEmCena() {

        for (int i = 0; i < enemies.size(); i++) {

            if (enemies.get(i) instanceof src.enemyTypes.BossEnemy) {
                return (src.enemyTypes.BossEnemy) enemies.get(i);
            }
        }

        return null;
    }

    public static Enemy inimigoMaisProximo(double px, double py) {

        Enemy melhor = null;
        double menorDist2 = Double.MAX_VALUE;

        for (int i = 0; i < enemies.size(); i++) {

            Enemy e = enemies.get(i);

            if (!e.isAlive()) {
                continue;
            }

            double dx = e.getX() - px;
            double dy = e.getY() - py;
            double d2 = dx * dx + dy * dy;

            if (d2 < menorDist2) {
                menorDist2 = d2;
                melhor = e;
            }
        }

        return melhor;
    }

    /** true se o ponto esta fora do campo de jogo, considerando uma margem. */
    public static boolean foraDoCampo(double px, double py, double margem) {

        return px < CAMPO_X - margem
            || px > CAMPO_X + CAMPO_W + margem
            || py < CAMPO_Y - margem
            || py > CAMPO_Y + CAMPO_H + margem;
    }

    /* =========================
           RENDER PANEL
       ========================= */

    private static class GamePanel extends JPanel {

        public GamePanel() {
            setBackground(Color.BLACK);
            setDoubleBuffered(true);
        }

        @Override
        protected void paintComponent(Graphics g) {

            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g;

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);

            render(g2);

            Toolkit.getDefaultToolkit().sync();
        }

        private void render(Graphics2D g) {

            g.setColor(Color.WHITE);

            if (gameState.equals("Menu")) {

                menu.render(g);
                return;
            }

            // "Game", "Pause", "Continue", "GameOver" e "Vitoria" desenham
            // a mesma cena; os outros so acrescentam algo por cima.
            renderCena(g);

            // O DIALOGO vem por cima da cena viva, recortado no campo pra
            // nao vazar no painel lateral. Nao ha mais um estado separado
            // pra ele: o jogo continua rodando por baixo.
            if (emDialogo()) {

                Shape recorte = g.getClip();
                g.setClip(CAMPO_X, CAMPO_Y, CAMPO_W, CAMPO_H);

                cutsceneAtual.render(g);

                g.setClip(recorte);
            }

            // OS ESTOUROS DE TRANSFORMACAO, POR CIMA ATE DA CONVERSA.
            //
            // Eles so acontecem em dois momentos, os dois dentro de um
            // dialogo: a chefe virando maligna e o estudante virando
            // expansivo. Desenhar por baixo da caixa de fala (que joga um
            // veu escuro no campo inteiro) e o que fazia eles sumirem.
            //
            // Nao ha risco de tapar texto: o estouro dura pouco mais de um
            // segundo e o miolo dele e translucido.
            if (!explosoes.isEmpty() || !ascensoes.isEmpty()) {

                Shape recorte = g.getClip();
                g.setClip(CAMPO_X, CAMPO_Y, CAMPO_W, CAMPO_H);

                // A cerimonia da armadura vem ANTES dos estouros: ela e o
                // ambiente (veu, energia juntando) e eles sao o clímax.
                for (int i = 0; i < ascensoes.size(); i++) {
                    ascensoes.get(i).render(g);
                }

                for (int i = 0; i < explosoes.size(); i++) {
                    explosoes.get(i).render(g);
                }

                g.setClip(recorte);
            }

            if (gameState.equals("Pause")) {

                renderAviso(g, "PAUSADO", "ESC para voltar");

            } else if (gameState.equals("Continue")) {

                menuDeContinue.render(g);

            } else if (gameState.equals("Creditos")) {

                // AQUI ESTAVA O BUG DA CENA FINAL.
                //
                // Este ramo tinha, colado por engano, o MESMO bloco que ja
                // existe no tick(): creditos.tick(), o teste do acabou(), o
                // reiniciarPartida() e a volta pro menu. Nao havia nenhuma
                // linha desenhando os creditos.
                //
                // O resultado era exatamente o que apareceu no jogo: o
                // gameState virava "Creditos", a fase parava de existir e a
                // tela continuava mostrando a ULTIMA cena renderizada — o
                // UBA de fundo, o estudante parado, zero inimigos, sem
                // caixa de fala e sem "ESTAGIO N" (que some quando a fase
                // acabou). Parecia o jogo travado num estagio vazio; na
                // verdade os creditos estavam rodando invisiveis.
                //
                // De quebra, o bloco mexia em estado do jogo (reiniciar a
                // partida, trocar de estado, parar a musica) de dentro do
                // paintComponent, ou seja, na thread do Swing enquanto a
                // thread do jogo mexia nas mesmas variaveis. O avanco do
                // tempo tambem corria dobrado, uma vez por tick e outra por
                // frame.
                //
                // O render agora so DESENHA. Quem faz os creditos andarem e
                // o tick(), como todo o resto do jogo.
                creditos.render(g);

            } else if (gameState.equals("Loja")) {

                loja.render(g);

            } else if (gameState.equals("GameOver")) {

                renderAviso(g, "GAME OVER", "ENTER para voltar ao menu");

            } else if (gameState.equals("Vitoria")) {

                // Duas vitorias diferentes. Quem usou continue chegou ao
                // fim, mas nao derrotou o jogo — e a tela diz isso.
                if (continuesUsados > 0) {
                    renderAviso(g, "FIM (COM CONTINUE)",
                                continuesUsados + (continuesUsados == 1 ? " continue usado" : " continues usados")
                                + " · ENTER para voltar ao menu");
                } else {
                    renderAviso(g, "FASE LIMPA", "sem continues · ENTER para voltar ao menu");
                }
            }
        }

        private void renderCena(Graphics2D g) {

            hud.render(g);

            // Recorta o desenho no campo de jogo: assim nada vaza pro
            // painel lateral, mesmo bala nascendo fora da area.
            Shape recorteAnterior = g.getClip();
            g.setClip(CAMPO_X, CAMPO_Y, CAMPO_W, CAMPO_H);

            // Fundo primeiro: tudo o que vem depois desenha por cima dele.
            fundo.render(g);

            for (int i = 0; i < points.size(); i++) {
                points.get(i).render(g);
            }

            for (int i = 0; i < enemies.size(); i++) {
                enemies.get(i).render(g);
            }

            player.render(g);

            // Balas por ultimo (antes da GPT Expansion): em bullet hell elas
            // tem que ficar por cima de tudo, senao o jogador nao consegue
            // ler a tela.
            for (int i = 0; i < bullets.size(); i++) {
                bullets.get(i).render(g);
            }

            // As tralhas da morte vao por cima das balas: o ponto delas e
            // justamente ser vistas no meio de uma tela cheia.
            for (int i = 0; i < destrocos.size(); i++) {
                destrocos.get(i).render(g);
            }

            // A AGRICULTURA DIGITAL fica ANTES das balas do proximo bloco
            // por um motivo pratico: a fumaca e larga, e por cima das balas
            // ela emborraria justamente a coisa que o jogador precisa ler.
            for (int i = 0; i < agriculturas.size(); i++) {
                agriculturas.get(i).render(g);
            }

            // O OLHO LASER vai por cima: ele e opaco no miolo, e mesmo
            // assim da pra ver as balas atraves dele porque o nucleo e
            // escuro em vez de branco. Ver OlhoLaser.desenharFeixe.
            for (int i = 0; i < olhosLaser.size(); i++) {
                olhosLaser.get(i).render(g);
            }

            // OS ESTOUROS DE TRANSFORMACAO NAO SAO DESENHADOS AQUI.
            //
            // Eles acontecem SEMPRE no meio de um dialogo (e a hora em que
            // a chefe vira maligna, ou em que o estudante grita
            // "ESPANDAAAAA"), e a caixa de conversa cobre o campo com um
            // veu escuro. Desenhados aqui, ficavam por baixo desse veu e
            // na pratica nao apareciam — foi por isso que os estouros
            // "nao existiam" em jogo mesmo estando ligados e rodando.
            // Ver o render() logo abaixo, depois da cutscene.

            // A explosao da GPT Expansion fica por cima de TUDO, inclusive
            // das balas: e o feedback visual de "isso aqui limpou a tela".
            for (int i = 0; i < efeitosGpt.size(); i++) {
                efeitosGpt.get(i).render(g);
            }

            fase.render(g);

            g.setClip(recorteAnterior);

            if (debugMode) {
                g.setFont(new Font("Monospaced", Font.PLAIN, 12));
                g.setColor(new Color(120, 255, 120));
                g.drawString("FPS " + fpsAtual
                           + "  balas " + bullets.size()
                           + "  inimigos " + enemies.size(),
                             CAMPO_X, CAMPO_Y + CAMPO_H + 14);
            }
        }

        private void renderAviso(Graphics2D g, String titulo, String subtitulo) {

            // Escurece o campo pra destacar o texto.
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(CAMPO_X, CAMPO_Y, CAMPO_W, CAMPO_H);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Monospaced", Font.BOLD, 36));
            g.drawString(titulo,
                         CAMPO_X + CAMPO_W / 2 - titulo.length() * 11,
                         CAMPO_Y + CAMPO_H / 2);

            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.setColor(new Color(200, 200, 200));
            g.drawString(subtitulo,
                         CAMPO_X + CAMPO_W / 2 - subtitulo.length() * 5,
                         CAMPO_Y + CAMPO_H / 2 + 34);
        }
    }

    /* =========================
           EVENTOS DE TECLADO
       ========================= */

    public static boolean f5 = false;
    public static boolean f2 = false;
    public static boolean f3 = false;

    /** Teclas 1 e 2: os itens comprados na lojinha do Perea. */
    public static boolean tecla1 = false;
    public static boolean tecla2 = false;

    @Override
    public void keyPressed(KeyEvent e) {

        setarTecla(e.getKeyCode(), true);

        // Letras e digitos tambem viram evento de digitacao. Nao ha
        // conflito com o resto: a maquina de Turing so le a caixinha
        // enquanto esta ativa, e nela o jogador fica travado no lugar.
        char c = Character.toUpperCase(e.getKeyChar());

        if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')|| c == '_') {
            teclaDigitada = c;
        }
    }

    /**
     * Pega a ultima tecla digitada e ESVAZIA a caixa.
     *
     * @return a letra/digito, ou 0 se nada foi digitado desde a ultima
     *         chamada
     */
    public static char consumirTeclaDigitada() {

        char c = teclaDigitada;
        teclaDigitada = 0;

        return c;
    }

    /** Joga fora o que estiver na caixa, sem ler. Usado ao (re)comecar algo. */
    public static void limparTeclaDigitada() {
        teclaDigitada = 0;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        setarTecla(e.getKeyCode(), false);
    }

    /**
     * Um metodo so pra pressionar e soltar: o mapa de teclas fica em um
     * lugar unico, entao adicionar uma tecla nova e mexer em uma linha.
     */
    private void setarTecla(int keyCode, boolean valor) {

        switch (keyCode) {

            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                up = valor;
                break;

            // Os itens do Perea. Numeros e nao letras porque as letras
            // vizinhas do WASD ja estao todas ocupadas (Z tiro, X foco,
            // C autofire, V bomba) e porque a maquina de Turing do PAPA
            // usa o teclado inteiro pra digitar a fita — numero e o que
            // sobra sem conflito.
            case KeyEvent.VK_1:
            case KeyEvent.VK_NUMPAD1:
                tecla1 = valor;
                break;

            case KeyEvent.VK_2:
            case KeyEvent.VK_NUMPAD2:
                tecla2 = valor;
                break;

            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                down = valor;
                break;

            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                left = valor;
                break;

            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                right = valor;
                break;

            case KeyEvent.VK_Z:
                z = valor;
                break;

            // SHIFT tambem entra em modo foco: e a tecla padrao da serie,
            // entao quem ja jogou Touhou tenta ela por instinto.
            case KeyEvent.VK_X:
            case KeyEvent.VK_SHIFT:
                x = valor;
                break;

            case KeyEvent.VK_C:
                c = valor;
                break;

            case KeyEvent.VK_V:
                v = valor;
                break;

            case KeyEvent.VK_ENTER:
                enter = valor;
                break;

            case KeyEvent.VK_ESCAPE:
                esc = valor;
                break;

            case KeyEvent.VK_F5:
                f5 = valor;
                break;

            case KeyEvent.VK_F2:
                f2 = valor;
                break;

            case KeyEvent.VK_F3:
                f3 = valor;
                break;

            default:
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Nao usamos: o jogo le teclas fisicas, nao caracteres digitados.
    }
}
