package src;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import src.bulletTypes.Bullet;
import src.enemyTypes.Enemy;

/**
 * O ataque especial do jogo ("ative seu GPT interior e destrua todos os
 * trabalhos", como diz a proposta original): a logo do GPT sai do jogador,
 * gira, expande e limpa tudo que encostar nela — mata todo inimigo e
 * apaga toda bala que tocar, do jogador ou inimiga.
 *
 * Ciclo de vida em 3 fases, todo em cima do contador 't' (mesma ideia de
 * Enemy/phase1: nada de maquina de estado, so formula em cima do tempo):
 *
 *   1. EXPANSAO   (ticksExpansao)     — raio cresce de 0 ate o maximo
 *   2. SUSTENTACAO (ticksSustentacao) — fica parado no tamanho maximo
 *   3. FADE        (ticksFade)        — desaparece aos poucos
 *
 * A colisao e verificada TODO TICK contra o raio ATUAL (nao so uma vez no
 * fim), entao o efeito vai matando conforme cresce — da a sensacao de
 * onda de choque em vez de uma area fixa aparecendo instantaneamente.
 */
public class GptExpansion {

    /** Onde a explosao nasceu. Fica parada aqui — nao segue o jogador depois. */
    private final double x, y;

    private double raioAtual = 0;
    private double anguloRotacao = 0;

    private int t = 0;
    private boolean isAlive = true;

    /* --- ajustes lidos do game.properties --- */

    private final double raioMaximo;
    private final int ticksExpansao;
    private final int ticksSustentacao;
    private final int ticksFade;
    private final double velocidadeRotacao;

    private static final String SPRITE = "sprites/GFX/gpt_logo.png";

    public GptExpansion(double x, double y) {

        this.x = x;
        this.y = y;

        // O alcance cresce com o nivel do jogador: quem investiu em subir de
        // nivel ve a recompensa tambem na bomba, nao so no leque de tiro.
        int nivel = (Main.player != null) ? Main.player.getLevel() : 1;
        double bonus = 1 + (nivel - 1) * Config.getDouble("gptExpansao.bonusPorNivel", 0.18);

        this.raioMaximo       = Config.getDouble("gptExpansao.raioMaximo", 260.0) * bonus;
        this.ticksExpansao    = Math.max(1, Config.getInt("gptExpansao.ticksExpansao", 26));
        this.ticksSustentacao = Config.getInt("gptExpansao.ticksSustentacao", 12);
        this.ticksFade        = Math.max(1, Config.getInt("gptExpansao.ticksFade", 18));
        this.velocidadeRotacao = Config.getDouble("gptExpansao.velocidadeRotacao", 0.16);
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        if (!isAlive) {
            return;
        }

        atualizarRaio();

        // Gira o tempo todo, inclusive na sustentacao e no fade — e o que
        // da a sensacao de "energia" continuando ativa ate sumir de vez.
        anguloRotacao += velocidadeRotacao;

        matarOQueEstiverDentro();

        if (t >= ticksExpansao + ticksSustentacao + ticksFade) {
            isAlive = false;
        }

        t++;
    }

    /** Raio cresce em raiz quadrada do progresso: rapido no inicio, suave no fim. */
    private void atualizarRaio() {

        if (t <= ticksExpansao) {
            double progresso = t / (double) ticksExpansao;
            raioAtual = raioMaximo * Math.sqrt(progresso);
        } else {
            raioAtual = raioMaximo;
        }
    }

    /**
     * Varre inimigos e balas contra o raio atual. Colisao circulo-circulo,
     * igual ao resto do jogo (ver Main.colidirBalasComInimigos).
     */
    private void matarOQueEstiverDentro() {

        for (int i = 0; i < Main.enemies.size(); i++) {

            Enemy inimigo = Main.enemies.get(i);

            if (!inimigo.isAlive()) {
                continue;
            }

            double dist = Main.getDist(x, y, inimigo.getX(), inimigo.getY());

            if (dist <= raioAtual + inimigo.getRadius()) {
                // Dano igual ao HP atual mata na hora, mas continua passando
                // pelo levarDano() normal — pontos e drop de item saem de graca.
                inimigo.levarDano(inimigo.getHp());
            }
        }

        for (int i = 0; i < Main.bullets.size(); i++) {

            Bullet bala = Main.bullets.get(i);

            if (!bala.isAlive()) {
                continue;
            }

            double dist = Main.getDist(x, y, bala.getX(), bala.getY());

            if (dist <= raioAtual + bala.getRadius()) {
                bala.setAlive(false);
            }
        }
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        if (!isAlive || raioAtual <= 0) {
            return;
        }

        double alpha = calcularAlpha();

        if (alpha <= 0) {
            return;
        }

        Composite composicaoAnterior = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));

        // Halo dourado atras da logo — sozinha a logo (so contorno) fica
        // fraca demais pra ler como "explosao" a distancia.
        int diametro = (int) (raioAtual * 2);

        g.setColor(new Color(255, 225, 140, 80));
        g.fillOval((int) (x - raioAtual), (int) (y - raioAtual), diametro, diametro);

        desenharLogoGirando(g);

        g.setComposite(composicaoAnterior);
    }

    private void desenharLogoGirando(Graphics2D g) {

        BufferedImage img = Assets.get(SPRITE);

        // A logo fica um pouco menor que o halo, senao as pontas do
        // desenho ficam recortadas na borda do circulo de dano.
        int lado = (int) (raioAtual * 1.5);

        if (img == null) {

            g.setColor(new Color(255, 225, 140, 200));
            g.setStroke(new java.awt.BasicStroke(4));
            g.drawOval((int) (x - lado / 2.0), (int) (y - lado / 2.0), lado, lado);
            return;
        }

        AffineTransform transformAnterior = g.getTransform();

        g.rotate(anguloRotacao, x, y);
        g.drawImage(img, (int) (x - lado / 2.0), (int) (y - lado / 2.0), lado, lado, null);

        g.setTransform(transformAnterior);
    }

    /** 1.0 nas fases de expansao/sustentacao; cai ate 0 durante o fade. */
    private double calcularAlpha() {

        int inicioFade = ticksExpansao + ticksSustentacao;

        if (t < inicioFade) {
            return 1.0;
        }

        double progressoFade = (t - inicioFade) / (double) ticksFade;

        return Math.max(0, 1.0 - Math.min(1.0, progressoFade));
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getRaioAtual() {
        return raioAtual;
    }

    public double getRaioMaximo() {
        return raioMaximo;
    }

    public int getT() {
        return t;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean isAlive) {
        this.isAlive = isAlive;
    }
}
