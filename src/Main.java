package src;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class Main extends JFrame implements Runnable, KeyListener {


    public static boolean up = false;
    public static boolean down = false;
    public static boolean left = false;
    public static boolean right = false;

    public static boolean z = false;
    public static boolean x = false;
    public static boolean enter = false;
    public static boolean esc = false;


    /* =========================
            CONFIGURATION
       ========================= */

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 720;
    private static final int TARGET_FPS = 60;

    /* =========================
            GAME STATE
       ========================= */

    private Thread gameThread;
    private volatile boolean running;
    public static String gameState = "Menu";

    private final GamePanel panel;

    /* =========================
            CONSTRUCTOR
       ========================= */

    public Main() {

        super("Touhou Expancionista");

        panel = new GamePanel();

        add(panel);

        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        addKeyListener(this);
        setVisible(true);
        setFocusable(true);
        requestFocusInWindow();
    }

    /* =========================
             STARTUP
       ========================= */

    static Player player;
    static Menu menu;

    public static void main(String[] args) {

        Main game = new Main();

        player = new Player( WIDTH / 2.0, HEIGHT / 2.0, 5);
        menu = new Menu();

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

            while (delta >= 1) {

                tick();
                panel.repaint();

                fps++;
                delta = 0;
            }

            if (System.currentTimeMillis() - timer >= 1000) {

                System.out.println("FPS: " + fps);

                fps = 0;
                timer += 1000;
            }
        }
    }

    /* =========================
           GAME UPDATE
       ========================= */

    private void tick() {

        // Update game logic here

        if(gameState.equals("Menu")){

            menu.tick();

        }else if(gameState.equals("Game")){

            player.tick();

        }

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

            render((Graphics2D) g);

            Toolkit.getDefaultToolkit().sync();
        }

        private void render(Graphics2D g) {

            // Draw game objects here

            g.setColor(Color.WHITE);
            
            if(gameState.equals("Menu")){

                menu.render(g);

            }else if(gameState.equals("Game")){

                player.render(g);

            }

        }
    }

    @Override
    public void keyPressed(KeyEvent e) {

        switch (e.getKeyCode()) {

            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                up = true;
                break;

            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                down = true;
                break;

            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                left = true;
                break;

            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                right = true;
                break;

            case KeyEvent.VK_Z:
                z = true;
                break;

            case KeyEvent.VK_X:
                x = true;
                break;

            case KeyEvent.VK_ENTER:
                enter = true;
                break;

            case KeyEvent.VK_ESCAPE:
                esc = true;
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

        switch (e.getKeyCode()) {

            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                up = false;
                break;

            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                down = false;
                break;

            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                left = false;
                break;

            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                right = false;
                break;

            case KeyEvent.VK_Z:
                z = false;
                break;

            case KeyEvent.VK_X:
                x = false;
                break;

            case KeyEvent.VK_ENTER:
                enter = false;
                break;

            case KeyEvent.VK_ESCAPE:
                esc = false;
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'keyTyped'");
    }


}