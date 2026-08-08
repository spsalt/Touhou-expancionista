package src.bulletTypes;

import java.awt.*;

import src.Main;

/**
 * Bala com movimento por integracao: guarda velocidade (dx, dy) e
 * aceleracao (d2x, d2y) e integra a cada tick.
 *
 *   dx += d2x;   x += dx;
 *
 * Com isso da pra fazer quase todo padrao do jogo so escolhendo numeros:
 *   - aceleracao 0            -> linha reta
 *   - d2y positivo pequeno    -> bala que "cai" (gravidade)
 *   - d2 apontando pro centro -> curva
 * Nada de if especial por padrao: o padrao mora nos parametros.
 */
public class IntegralBullet extends Bullet {

    private double dx, dy;    // velocidade
    private double d2x, d2y;  // aceleracao

    private Color cor;

    /* --- espalhamento (scatter) --- */

    /**
     * Como o grupo de balas se abre quando o simbolo se desfaz.
     *
     * Os tres sao DETERMINISTICOS: a direcao sai do indice da bala dentro
     * do grupo, nao de sorteio. Isso importa — com angulo aleatorio por
     * bala o resultado vira ruido, o jogador nao consegue prever nada e o
     * desvio vira sorte. Com padrao, ele le a abertura e escolhe a brecha.
     */
    public enum PadraoEspalhamento {
        /** Leque simetrico: as balas se abrem em arco, do -abertura ao +abertura. */
        LEQUE,
        /** Catavento: todas giram progressivamente pro mesmo lado. */
        ESPIRAL,
        /** Duas correntes: pares pra um lado, impares pro outro. */
        DIVERGENTE
    }

    /** Ticks de vida desta bala. */
    private int t = 0;

    /** Quando espalhar. <= 0 desliga o efeito. */
    private int ticksAteEspalhar = 0;

    /** Ja espalhou? Garante que acontece uma vez so. */
    private boolean jaEspalhou = false;

    private PadraoEspalhamento padrao = PadraoEspalhamento.LEQUE;

    /** Posicao desta bala dentro do grupo, e o tamanho do grupo. */
    private int indiceNoGrupo = 0;
    private int tamanhoDoGrupo = 1;

    /** Abertura total do padrao, em radianos. */
    private double aberturaEspalhamento = 0.9;

    /** Multiplicador de velocidade no momento do espalhamento. */
    private double ganhoDeVelocidade = 1.15;

    /**
     * @param x,y       posicao inicial
     * @param dx,dy     velocidade inicial (pixels por tick)
     * @param d2x,d2y   aceleracao (pixels por tick ao quadrado)
     * @param radius    raio de colisao e de desenho
     * @param hitPlayer true se e bala inimiga (machuca o jogador)
     */
    public IntegralBullet(double x, double y, double dx, double dy,
                          double d2x, double d2y, double radius, boolean hitPlayer) {

        this(x, y, dx, dy, d2x, d2y, radius, hitPlayer,
             hitPlayer ? Color.RED : Color.CYAN);
    }

    /** Mesmo construtor, deixando escolher a cor da bala. */
    public IntegralBullet(double x, double y, double dx, double dy,
                          double d2x, double d2y, double radius, boolean hitPlayer,
                          Color cor) {

        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.d2x = d2x;
        this.d2y = d2y;
        this.radius = radius;
        this.hitPlayer = hitPlayer;
        this.cor = cor;
    }

    /**
     * Liga o espalhamento: depois de 'ticks', a bala vira e acelera
     * seguindo um padrao definido pela sua posicao no grupo.
     *
     * Serve pros ataques que desenham um SIMBOLO com balas (a integral, o
     * somatorio): o jogador ve a forma se montar e depois ela se desfaz.
     *
     * @param ticks    quantos ticks ate espalhar (<= 0 desliga)
     * @param padrao   forma da abertura (ver PadraoEspalhamento)
     * @param indice   posicao desta bala no grupo (0 .. total-1)
     * @param total    quantas balas tem o grupo
     * @param abertura abertura total do padrao, em radianos
     * @param ganho    multiplicador de velocidade ao espalhar
     */
    public void configurarEspalhamento(int ticks, PadraoEspalhamento padrao,
                                       int indice, int total,
                                       double abertura, double ganho) {
        this.ticksAteEspalhar = ticks;
        this.padrao = padrao;
        this.indiceNoGrupo = indice;
        this.tamanhoDoGrupo = Math.max(1, total);
        this.aberturaEspalhamento = abertura;
        this.ganhoDeVelocidade = ganho;
    }

    @Override
    public void tick() {

        // 0) hora de espalhar?
        if (!jaEspalhou && ticksAteEspalhar > 0 && t >= ticksAteEspalhar) {
            espalhar();
        }

        t++;

        // 1) integra a posicao e a velocidade
        x += dx;
        y += dy;

        dx += d2x;
        dy += d2y;

        // 2) morre ao sair do campo de jogo (com uma margem, senao balas que
        //    nascem na borda sumiriam no mesmo frame em que nascem)
        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
            this.isAlive = false;
            return;
        }

        // 3) colisao com o jogador. Quem decide o que fazer com o dano e o
        //    Player, nao a bala: aqui so avisamos.
        if (isAlive && hitPlayer && Main.player != null) {

            double dist = Main.getDist(this.x, this.y, Main.player.getX(), Main.player.getY());

            if (dist <= this.radius + Main.player.getRadius()) {

                // So consome a bala se o dano foi aplicado de fato
                // (se o jogador estiver invulneravel, a bala passa reto).
                if (Main.player.levarDano()) {
                    this.isAlive = false;
                }
            }
        }
    }

    /**
     * Gira a velocidade atual num angulo aleatorio e acelera.
     *
     * Trabalha em coordenadas POLARES (angulo + modulo) e nao mexendo em
     * dx/dy direto: assim a bala mantem o sentido geral pra onde ja ia,
     * so abrindo em leque. Sortear dx e dy soltos mandaria bala pra tras.
     */
    private void espalhar() {

        jaEspalhou = true;

        double velocidade = Math.sqrt(dx * dx + dy * dy);

        if (velocidade < 0.01) {
            return;
        }

        double angulo = Math.atan2(dy, dx) + desvioDoPadrao();

        velocidade *= ganhoDeVelocidade;

        dx = Math.cos(angulo) * velocidade;
        dy = Math.sin(angulo) * velocidade;

        // Zera a aceleracao: ela vinha do desenho do simbolo e depois do
        // espalhamento so atrapalharia a leitura da nova direcao.
        d2x = 0;
        d2y = 0;
    }

    /**
     * Quanto esta bala desvia da direcao atual, conforme o padrao.
     *
     * 'f' e a posicao normalizada no grupo (0 na primeira bala, 1 na
     * ultima). Todo padrao e uma funcao de f — nada de sorteio, entao o
     * resultado e sempre o mesmo e o jogador consegue aprender.
     */
    private double desvioDoPadrao() {

        double f = (tamanhoDoGrupo <= 1) ? 0.5 : indiceNoGrupo / (double) (tamanhoDoGrupo - 1);

        switch (padrao) {

            case LEQUE:
                // -abertura/2 na primeira bala, +abertura/2 na ultima:
                // o grupo abre num arco simetrico.
                return -aberturaEspalhamento / 2 + aberturaEspalhamento * f;

            case ESPIRAL:
                // Todas viram pro mesmo lado, cada vez mais: vira catavento.
                return aberturaEspalhamento * f;

            case DIVERGENTE:
                // Pares pra um lado, impares pro outro: o grupo racha em
                // duas correntes e abre um corredor no meio.
                return (indiceNoGrupo % 2 == 0) ? aberturaEspalhamento / 2
                                                : -aberturaEspalhamento / 2;

            default:
                return 0;
        }
    }

    @Override
    public void render(Graphics2D g) {

        int d = (int) (radius * 2);

        // Miolo claro + borda colorida: fica legivel mesmo com a tela cheia de bala.
        g.setColor(cor);
        g.fillOval((int) (x - radius), (int) (y - radius), d, d);

        g.setColor(Color.WHITE);
        g.fillOval((int) (x - radius * 0.45), (int) (y - radius * 0.45),
                   (int) (radius * 0.9), (int) (radius * 0.9));
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getDx() {
        return dx;
    }

    public void setDx(double dx) {
        this.dx = dx;
    }

    public double getDy() {
        return dy;
    }

    public void setDy(double dy) {
        this.dy = dy;
    }

    public double getD2x() {
        return d2x;
    }

    public void setD2x(double d2x) {
        this.d2x = d2x;
    }

    public double getD2y() {
        return d2y;
    }

    public void setD2y(double d2y) {
        this.d2y = d2y;
    }

    public Color getCor() {
        return cor;
    }

    public void setCor(Color cor) {
        this.cor = cor;
    }
}
