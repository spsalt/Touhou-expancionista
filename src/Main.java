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
 *     "Menu" | "Game" | "Pause" | "GameOver"
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

    /* =========================
            ARRAYLISTS
       ========================= */

    public static ArrayList<Bullet> bullets = new ArrayList<Bullet>();
    public static ArrayList<Point> points = new ArrayList<Point>();
    public static ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    public static ArrayList<GptExpansion> efeitosGpt = new ArrayList<GptExpansion>();

    /* =========================
            STARTUP
       ========================= */

    public static Player player;
    static Menu menu;
    static Hud hud;
    static phase1 fase;
    static Background fundo;
    /** A cutscene sendo exibida agora (null fora do estado "Cutscene"). */
    static Cutscene cutsceneAtual;

    static Musica musica;

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

    /** Trava pra ESC nao alternar pause 60x por segundo enquanto segurado. */
    private boolean escAnterior = false;
    private boolean f5Anterior = false;
    private boolean f2Anterior = false;

    /** FPS medido, mostrado no HUD quando debug.mostrarFps=true. */
    private static int fpsAtual = 0;

    private final GamePanel panel;

    /* =========================
            CONSTRUCTOR
       ========================= */

    public Main() {

        super("Touhou Expancionista");

        // IMPORTANTE: os objetos do jogo nascem ANTES de setVisible(true).
        // O Swing pode pintar o painel assim que a janela aparece, e se
        // player/menu ainda fossem null a render() quebraria com NullPointer.
        player = new Player(
            CAMPO_X + CAMPO_W * Config.getDouble("jogador.spawnRelX", 0.5),
            CAMPO_Y + CAMPO_H * Config.getDouble("jogador.spawnRelY", 0.8),
            Config.getDouble("jogador.raioHitbox", 5.0)
        );

        menu   = new Menu();
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

        } else if (gameState.equals("Cutscene")) {

            cutsceneAtual.tick();

            // A cutscene acabou (ou foi pulada com ESC): volta pra acao.
            // continuar() liga a trilha se ela nao estiver tocando e nao
            // faz nada se ja estiver — entao serve tanto pra intro (onde a
            // musica ainda nem comecou) quanto pras cutscenes do meio da
            // fase, que nao podem reiniciar a musica.
            if (cutsceneAtual.acabou()) {
                gameState = "Game";
                musica.continuar();
            }

        } else if (gameState.equals("Game")) {

            tickDoJogo();

        } else if (gameState.equals("GameOver") || gameState.equals("Vitoria")) {

            // As telas de fim de partida so esperam o ENTER.
            if (enter) {
                enter = false;
                musica.parar();
                reiniciarPartida();
                gameState = "Menu";
            }
        }
        // "Pause" nao atualiza nada de proposito: a cena congela e so
        // o render continua rodando.
    }

    /** Um frame de partida: fase, jogador, inimigos, balas, itens, colisoes. */
    private void tickDoJogo() {

        fundo.tick();
        fase.tick();
        player.tick();

        // Indices em vez de for-each: durante o tick os objetos podem
        // ADICIONAR novos elementos nas listas (um inimigo atira, um chefe
        // divide uma bala), e o for-each lancaria ConcurrentModificationException.
        for (int i = 0; i < enemies.size(); i++) {
            enemies.get(i).tick();
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

        colidirBalasComInimigos();

        // Faxina no fim do frame, nunca no meio dos loops acima.
        bullets.removeIf(b -> !b.isAlive());
        points.removeIf(p -> !p.isAlive());
        enemies.removeIf(e -> !e.isAlive());
        efeitosGpt.removeIf(f -> !f.isAlive());

        if (fase.acabou()) {
            gameState = "Vitoria";
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
        gameState = "Cutscene";
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

        if (hud.getBotaoGptExpansaoBounds().contains(mouseX, mouseY)) {
            player.usarGptExpansao();
        }
    }

    /** ESC pausa/despausa, F5 recarrega os ajustes do disco. */
    private void tratarTeclasGlobais() {

        // Borda de subida: so age no frame em que a tecla foi apertada.
        if (esc && !escAnterior) {

            if (gameState.equals("Game")) {
                gameState = "Pause";
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

        // DEBUG: F2 pula pro proximo estagio. Util pra chegar num chefe
        // sem jogar a fase toda enquanto testa.
        if (f2 && !f2Anterior && gameState.equals("Game")
                && Config.getBool("debug.permitirPularEstagio", true)) {

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

    /** Zera a partida (usado ao sair do menu e no game over). */
    public static void reiniciarPartida() {

        bullets.clear();
        points.clear();
        enemies.clear();
        efeitosGpt.clear();

        player = new Player(
            CAMPO_X + CAMPO_W * Config.getDouble("jogador.spawnRelX", 0.5),
            CAMPO_Y + CAMPO_H * Config.getDouble("jogador.spawnRelY", 0.8),
            Config.getDouble("jogador.raioHitbox", 5.0)
        );

        fase = new phase1();

        // O fundo nao e recriado (recarregar a foto custa caro); so volta
        // pro comeco do loop de rolagem.
        if (fundo != null) {
            fundo.setOffset(0);
        }

        cutsceneAtual = null;
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

            if (gameState.equals("Cutscene") && cutsceneAtual != null) {

                // Cena ocupa a JANELA INTEIRA (nao so o campo de jogo):
                // e o que da o clima de tela cheia, tipo cinema.
                cutsceneAtual.render(g);
                return;
            }

            // "Game", "Pause", "GameOver" e "Vitoria" desenham a mesma cena;
            // os tres ultimos so acrescentam um texto por cima.
            renderCena(g);

            if (gameState.equals("Pause")) {
                renderAviso(g, "PAUSADO", "ESC para voltar");
            } else if (gameState.equals("GameOver")) {
                renderAviso(g, "GAME OVER", "ENTER para voltar ao menu");
            } else if (gameState.equals("Vitoria")) {
                renderAviso(g, "FASE LIMPA", "ENTER para voltar ao menu");
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

            // A explosao da GPT Expansion fica por cima de TUDO, inclusive
            // das balas: e o feedback visual de "isso aqui limpou a tela".
            for (int i = 0; i < efeitosGpt.size(); i++) {
                efeitosGpt.get(i).render(g);
            }

            fase.render(g);

            g.setClip(recorteAnterior);

            if (Config.getBool("debug.mostrarFps", true)) {
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

    @Override
    public void keyPressed(KeyEvent e) {
        setarTecla(e.getKeyCode(), true);
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

            case KeyEvent.VK_X:
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

            default:
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Nao usamos: o jogo le teclas fisicas, nao caracteres digitados.
    }
}
