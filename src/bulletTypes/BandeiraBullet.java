package src.bulletTypes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import src.Bandeira;
import src.Config;
import src.Main;
import src.Som;

/**
 * BANDEIRA — o projetil do primeiro ataque do PAPA.
 *
 * Uma bandeira de um pais sorteado nasce parada, GIRA acompanhando o
 * jogador por alguns ticks e entao AVANCA em linha reta, na direcao em
 * que ele estava no ultimo tick da mira. Depois de travada ela nao
 * corrige mais nada.
 *
 * OS TRES ESTADOS
 * ---------------
 *   MIRANDO  — gira suavemente pra apontar pro jogador; nao machuca ainda
 *   TRAVADA  — para de girar e pisca; a direcao ja esta decidida
 *   AVANCANDO— sai em linha reta e machuca
 *
 * A fase TRAVADA e curta mas nao e enfeite: e o aviso justo. Sem ela a
 * bandeira sairia no mesmo frame em que parou de girar e o jogador nao
 * teria como saber que a mira fechou. Com ela, quem prestou atencao tem
 * uma janela pra sair da linha de tiro.
 *
 * E o giro tem TAXA LIMITADA (igual ao PonteiroBullet) de proposito: se
 * a bandeira apontasse instantaneamente, ficar parado seria a unica
 * jogada errada e andar de lado sempre funcionaria. Com giro limitado,
 * andar rapido de lado no fim da mira e o que realmente engana ela.
 */
public class BandeiraBullet extends Bullet {

    /** Os tres momentos de vida da bandeira. */
    private enum Estado {
        MIRANDO,
        TRAVADA,
        AVANCANDO
    }

    private final Bandeira bandeira;

    /** Direcao pra onde ela aponta (e depois avanca), em radianos. */
    private double angulo;

    private Estado estado = Estado.MIRANDO;

    /** Ticks restantes no estado atual. */
    private int restam;

    private final int ticksDeMira;
    private final int ticksTravada;
    private final double taxaDeGiro;
    private final double velocidade;

    /** Lado do quadrado que a bandeira ocupa na tela. */
    private final double tamanho;

    /**
     * Esta bandeira e a "porta-voz" da leva?
     *
     * Uma leva inteira trava e sai no MESMO frame. Se cada uma tocasse o
     * proprio efeito, seriam seis copias empilhadas do mesmo som — que
     * nao soa seis vezes mais importante, soa quebrado. So a primeira
     * fala pelo grupo.
     */
    private final boolean comSom;

    public BandeiraBullet(double x, double y, Bandeira bandeira, double anguloInicial,
                          int ticksDeMira, int ticksTravada,
                          double taxaDeGiro, double velocidade, double raio) {

        this(x, y, bandeira, anguloInicial, ticksDeMira, ticksTravada,
             taxaDeGiro, velocidade, raio, true);
    }

    public BandeiraBullet(double x, double y, Bandeira bandeira, double anguloInicial,
                          int ticksDeMira, int ticksTravada,
                          double taxaDeGiro, double velocidade, double raio,
                          boolean comSom) {

        this.comSom = comSom;

        this.x = x;
        this.y = y;
        this.bandeira = bandeira;
        this.angulo = anguloInicial;

        this.ticksDeMira  = Math.max(1, ticksDeMira);
        this.ticksTravada = Math.max(1, ticksTravada);
        this.taxaDeGiro   = taxaDeGiro;
        this.velocidade   = velocidade;

        this.radius = raio;
        this.tamanho = raio * 2 * Config.getDouble("papa.bandeiras.escalaSprite", 1.9);

        this.hitPlayer = true;
        this.restam = this.ticksDeMira;
    }

    @Override
    public void tick() {

        restam--;

        switch (estado) {

            case MIRANDO:
                mirar();
                if (restam <= 0) {
                    estado = Estado.TRAVADA;
                    restam = ticksTravada;

                    if (comSom) {
                        Som.tocar(Som.PAPA_MIRA);
                    }
                }
                break;

            case TRAVADA:
                if (restam <= 0) {
                    estado = Estado.AVANCANDO;

                    if (comSom) {
                        Som.tocar(Som.PAPA_AVANCA);
                    }
                }
                break;

            case AVANCANDO:
                x += Math.cos(angulo) * velocidade;
                y += Math.sin(angulo) * velocidade;
                break;

            default:
                break;
        }

        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA + tamanho)) {
            isAlive = false;
            return;
        }

        // So machuca depois de sair: uma bandeira parada em cima do
        // jogador enquanto mira seria dano impossivel de evitar.
        if (estado == Estado.AVANCANDO) {
            checarJogador();
        }
    }

    /** Gira ate no maximo taxaDeGiro por tick na direcao do jogador. */
    private void mirar() {

        if (Main.player == null) {
            return;
        }

        double desejado = Math.atan2(Main.player.getY() - y, Main.player.getX() - x);
        double diferenca = desejado - angulo;

        // Normaliza pra -PI..PI, senao ela daria a volta pelo lado longo.
        while (diferenca > Math.PI)  diferenca -= 2 * Math.PI;
        while (diferenca < -Math.PI) diferenca += 2 * Math.PI;

        angulo += Math.max(-taxaDeGiro, Math.min(taxaDeGiro, diferenca));
    }

    private void checarJogador() {

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        if (dist <= radius + Main.player.getRadius() && Main.player.levarDano()) {
            isAlive = false;
        }
    }

    /* =========================
            RENDER
       ========================= */

    @Override
    public void render(Graphics2D g) {

        if (estado == Estado.MIRANDO || estado == Estado.TRAVADA) {
            desenharLinhaDeMira(g);
        }

        desenharPano(g);

        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }

    /**
     * O raio de mira: mostra pra onde ela vai sair.
     *
     * Fica cada vez mais forte conforme a mira fecha, e pisca grosso
     * quando trava. E o unico jeito de o ataque ser justo — a informacao
     * "vai sair por aqui" tem que estar na tela antes de sair.
     */
    private void desenharLinhaDeMira(Graphics2D g) {

        double alcance = Main.CAMPO_W + Main.CAMPO_H;

        int alpha;
        float grossura;

        if (estado == Estado.TRAVADA) {
            // Pisca: 2 frames aceso, 2 apagados.
            alpha = ((restam / 2) % 2 == 0) ? 210 : 90;
            grossura = 2.4f;
        } else {
            double progresso = 1 - restam / (double) ticksDeMira;
            alpha = 40 + (int) (110 * progresso);
            grossura = 1.2f;
        }

        Color c = bandeira.getCorPrincipal();

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(grossura));
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));

        g.drawLine((int) x, (int) y,
                   (int) (x + Math.cos(angulo) * alcance),
                   (int) (y + Math.sin(angulo) * alcance));

        g.setStroke(anterior);
    }

    /**
     * A bandeira em si, girada.
     *
     * Ela e desenhada apontando pra DIREITA (como um mastro a esquerda e
     * o pano tremulando pra frente), entao o angulo entra direto, sem a
     * correcao de PI/2 que as balas verticais precisam.
     */
    private void desenharPano(Graphics2D g) {

        BufferedImage img = bandeira.imagem();

        int larg = (int) tamanho;
        int alt  = (int) (tamanho * Bandeira.ALTURA / (double) Bandeira.LARGURA);

        AffineTransform anterior = g.getTransform();

        g.rotate(angulo, x, y);
        g.drawImage(img, (int) (x - larg / 2.0), (int) (y - alt / 2.0), larg, alt, null);

        g.setTransform(anterior);
    }

    /* =========================
            GETTERS
       ========================= */

    public Bandeira getBandeira() {
        return bandeira;
    }

    public double getAngulo() {
        return angulo;
    }

    /** true enquanto ela ainda nao saiu (util pro debug e pra contagem). */
    public boolean estaMirando() {
        return estado != Estado.AVANCANDO;
    }
}
