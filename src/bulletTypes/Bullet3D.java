package src.bulletTypes;

import java.awt.Color;
import java.awt.Graphics2D;

import src.Main;

/**
 * Bala que vive num espaco TRIDIMENSIONAL e e projetada na tela.
 *
 * O resto do jogo e 2D puro. Esta classe existe pro ataque da esfera da
 * Adriana: as balas nascem na superficie de uma esfera, ela gira, e o que
 * aparece na tela e a projecao em perspectiva desse volume.
 *
 * COMO FUNCIONA A PROJECAO
 * ------------------------
 * Cada bala guarda (x3, y3, z3), a posicao em 3D relativa ao centro da
 * esfera. O eixo Z aponta pra dentro da tela. A camera esta a uma
 * distancia D do plano da tela, entao:
 *
 *     escala = D / (D + z3)
 *     xTela  = centroX + x3 * escala
 *     yTela  = centroY + y3 * escala
 *
 * O que isso da de graca:
 *   - bala LONGE (z3 grande)  -> escala pequena -> perto do centro e miuda
 *   - bala PERTO (z3 negativo)-> escala grande  -> longe do centro e grande
 * E exatamente o que o olho espera de perspectiva.
 *
 * O RAIO DE COLISAO acompanha a escala (this.radius e recalculado todo
 * tick). Isso importa: sem isso a bala desenhada pequena la no fundo
 * continuaria matando com o tamanho cheio, e o jogador levaria dano de
 * uma coisa que nem parecia estar perto.
 *
 * Balas que passam POR TRAS da camera (z3 <= -D) sao descartadas, senao a
 * divisao inverteria o sinal e elas apareceriam espelhadas na tela.
 */
public class Bullet3D extends Bullet {

    /** Posicao no espaco 3D, relativa ao centro da esfera. */
    private double x3, y3, z3;

    /** Velocidade no espaco 3D (pixels por tick em cada eixo). */
    private double vx3, vy3, vz3;

    /** Onde o centro da esfera fica na tela. */
    private final double centroX, centroY;

    /** Distancia da camera ao plano da tela. Menor = perspectiva mais forte. */
    private final double distanciaCamera;

    /** Velocidade de giro da esfera em torno do eixo vertical (rad/tick). */
    private final double velocidadeGiro;

    /** Raio do desenho quando a bala esta exatamente no plano da tela. */
    private final double raioBase;

    private final Color corBase;

    /** Distancia do centro em que a bala nasceu (raio inicial da esfera). */
    private final double distanciaInicial;

    /** Distancia percorrida a partir dai em que a cor chega no violeta. */
    private final double distanciaAteVioleta;

    /**
     * Distancia 3D maxima do centro. Passou disso, a bala morre.
     *
     * POR QUE ISSO E NECESSARIO: sair do campo (o teste 2D) NAO basta.
     * Uma bala que viaja quase toda no eixo Z se afasta "pra dentro da
     * tela" — o x/y projetado converge pro centro em vez de ir pra borda,
     * entao ela encolhe pra sempre sem nunca sair do campo. Metade da
     * esfera ficava viva indefinidamente e a tela ia entupindo.
     */
    private final double alcanceMaximo;

    /** Tempo de vida em ticks. Teto duro, pra esfera limpar antes da proxima. */
    private final int vidaMaxima;

    /** Ticks finais em que a bala vai sumindo, pra nao apagar de estalo. */
    private final int ticksDeFade;

    /** Ticks de vida ja decorridos. */
    private int t = 0;

    /** Escala de perspectiva do frame atual. Guardada pro render usar. */
    private double escala = 1;

    public Bullet3D(double centroX, double centroY,
                    double x3, double y3, double z3,
                    double vx3, double vy3, double vz3,
                    double raioBase, double distanciaCamera, double velocidadeGiro,
                    Color corBase, double distanciaAteVioleta,
                    double alcanceMaximo, int vidaMaxima, int ticksDeFade) {

        this.centroX = centroX;
        this.centroY = centroY;

        this.x3 = x3;
        this.y3 = y3;
        this.z3 = z3;

        this.vx3 = vx3;
        this.vy3 = vy3;
        this.vz3 = vz3;

        this.raioBase = raioBase;
        this.distanciaCamera = Math.max(1, distanciaCamera);
        this.velocidadeGiro = velocidadeGiro;
        this.corBase = corBase;

        this.distanciaInicial = Math.sqrt(x3 * x3 + y3 * y3 + z3 * z3);
        this.distanciaAteVioleta = Math.max(1, distanciaAteVioleta);

        this.alcanceMaximo = Math.max(1, alcanceMaximo);
        this.vidaMaxima = Math.max(1, vidaMaxima);
        this.ticksDeFade = Math.max(1, Math.min(ticksDeFade, this.vidaMaxima));

        this.hitPlayer = true;
        this.radius = raioBase;

        projetar();
    }

    @Override
    public void tick() {

        // 1) anda no espaco 3D
        x3 += vx3;
        y3 += vy3;
        z3 += vz3;

        // 2) gira a esfera inteira em torno do eixo Y (o vertical da tela).
        //    Rotacao classica no plano XZ:
        //        x' = x*cos - z*sin
        //        z' = x*sin + z*cos
        if (velocidadeGiro != 0) {

            double cos = Math.cos(velocidadeGiro);
            double sin = Math.sin(velocidadeGiro);

            double novoX = x3 * cos - z3 * sin;
            double novoZ = x3 * sin + z3 * cos;

            x3 = novoX;
            z3 = novoZ;

            // A velocidade tambem precisa girar junto, senao a bala sairia
            // da rotacao numa direcao que nao bate com onde ela esta.
            double novoVx = vx3 * cos - vz3 * sin;
            double novoVz = vx3 * sin + vz3 * cos;

            vx3 = novoVx;
            vz3 = novoVz;
        }

        t++;

        // 3) passou por tras da camera: descarta (a projecao inverteria)
        if (z3 <= -distanciaCamera + 1) {
            isAlive = false;
            return;
        }

        // 4) alcance esgotado: e este teste, e nao o de sair do campo, que
        //    mata as balas que viajam pelo eixo Z (elas encolhem rumo ao
        //    centro da tela e nunca cruzariam a borda).
        if (Math.sqrt(x3 * x3 + y3 * y3 + z3 * z3) > alcanceMaximo) {
            isAlive = false;
            return;
        }

        // 5) teto de tempo: garante que a esfera limpa antes da proxima
        //    nascer, senao elas se acumulam e a tela entope.
        if (t >= vidaMaxima) {
            isAlive = false;
            return;
        }

        projetar();

        // 6) saiu do campo de jogo
        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
            isAlive = false;
            return;
        }

        // 7) colisao com o jogador, usando o raio JA escalado
        if (isAlive && hitPlayer && Main.player != null) {

            double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

            if (dist <= radius + Main.player.getRadius()) {

                if (Main.player.levarDano()) {
                    isAlive = false;
                }
            }
        }
    }

    /** Converte a posicao 3D em posicao de tela (x, y herdados de Bullet). */
    private void projetar() {

        escala = distanciaCamera / (distanciaCamera + z3);

        x = centroX + x3 * escala;
        y = centroY + y3 * escala;

        // O raio de colisao acompanha o desenho.
        radius = Math.max(1, raioBase * escala);
    }

    @Override
    public void render(Graphics2D g) {

        int d = (int) (radius * 2);

        // Escurece o que esta longe: e o que faz o olho ler o volume da
        // esfera em vez de um monte de bolinha solta na tela.
        double brilho = Math.max(0.35, Math.min(1.4, escala));

        Color base = corPorExpansao();

        Color cor = new Color(
            limitar((int) (base.getRed()   * brilho)),
            limitar((int) (base.getGreen() * brilho)),
            limitar((int) (base.getBlue()  * brilho))
        );

        // Nos ultimos ticks de vida a bala vai sumindo, em vez de apagar de
        // estalo. Alem de ficar melhor, avisa o jogador que aquela area vai
        // liberar — sumico instantaneo confunde a leitura da tela.
        double opacidade = opacidadePorTempo();

        if (opacidade < 1.0) {
            cor = new Color(cor.getRed(), cor.getGreen(), cor.getBlue(),
                            limitar((int) (255 * opacidade)));
        }

        g.setColor(cor);
        g.fillOval((int) (x - radius), (int) (y - radius), d, d);

        // Miolo claro so nas balas da frente: nas de tras ele viraria
        // ruido branco e atrapalharia a leitura de profundidade.
        if (escala > 0.9) {
            g.setColor(new Color(255, 255, 255, limitar((int) (255 * opacidade))));
            g.fillOval((int) (x - radius * 0.45), (int) (y - radius * 0.45),
                       (int) (radius * 0.9), (int) (radius * 0.9));
        }
    }

    /** 1.0 na maior parte da vida; cai ate 0 nos ultimos 'ticksDeFade'. */
    private double opacidadePorTempo() {

        int inicioDoFade = vidaMaxima - ticksDeFade;

        if (t < inicioDoFade) {
            return 1.0;
        }

        return Math.max(0, 1.0 - (t - inicioDoFade) / (double) ticksDeFade);
    }

    /**
     * Cor em funcao de QUANTO a bala ja se afastou do ponto onde nasceu:
     * vai da cor base (vermelho) ate o violeta.
     *
     * Serve de leitura de perigo — bala recem-nascida ainda esta perto do
     * chefe, bala violeta ja e a que esta chegando no jogador. E medido em
     * distancia 3D real, nao em tempo, entao vale mesmo com a esfera
     * girando ou com raios iniciais diferentes.
     */
    private Color corPorExpansao() {

        double distancia = Math.sqrt(x3 * x3 + y3 * y3 + z3 * z3) - distanciaInicial;

        double f = Math.max(0, Math.min(1, distancia / distanciaAteVioleta));

        final int VIOLETA_R = 170, VIOLETA_G = 60, VIOLETA_B = 255;

        return new Color(
            limitar((int) (corBase.getRed()   + (VIOLETA_R - corBase.getRed())   * f)),
            limitar((int) (corBase.getGreen() + (VIOLETA_G - corBase.getGreen()) * f)),
            limitar((int) (corBase.getBlue()  + (VIOLETA_B - corBase.getBlue())  * f))
        );
    }

    private static int limitar(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getX3() {
        return x3;
    }

    public double getY3() {
        return y3;
    }

    public double getZ3() {
        return z3;
    }

    /** Escala de perspectiva atual: > 1 esta na frente, < 1 esta no fundo. */
    public double getEscala() {
        return escala;
    }
}
