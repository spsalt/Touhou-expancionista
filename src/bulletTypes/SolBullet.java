package src.bulletTypes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

import src.Main;

/**
 * A BALA QUE CAI PRA DENTRO DO SOL.
 *
 * Ela nasce longe, na borda do campo, e espirala em direcao ao centro ate
 * ser CONSUMIDA pelo sol. E o contrario do que este ataque fazia antes.
 *
 * POR QUE PRA DENTRO E NAO PRA FORA
 * ---------------------------------
 * Numa chuva radial que sai do centro, a distancia entre duas balas
 * vizinhas de um mesmo anel e r * dTheta — ou seja, ela CRESCE junto com o
 * raio. La embaixo, onde o jogador fica, os vizinhos estao a centenas de
 * pixels um do outro, e um vao entre dois raios que se afastam nunca mais
 * fecha. Foi por isso que o ataque continuou tendo ponto seguro mesmo
 * depois de eu dobrar a quantidade de balas e torcer a trajetoria: eu
 * estava tratando o sintoma de uma coisa que era geometria.
 *
 * Invertendo o sentido, a mesma conta trabalha a favor: o vao entre duas
 * vizinhas FECHA sozinho conforme elas descem. Parar num buraco deixa de
 * ser uma solucao permanente e passa a ser uma decisao com prazo.
 *
 * COMO ELA ANDA: EM POLAR, NAO EM XY
 * ----------------------------------
 * Guardar (raio, angulo) e converter pra tela a cada tick, em vez de somar
 * velocidade no x e no y. Uma espiral escrita em velocidade cartesiana
 * precisa girar o vetor todo frame e acumula erro; em polar ela e so
 * "diminui o raio, aumenta o angulo" — exata, estavel, e os dois numeros
 * que definem o padrao ficam legiveis no depurador.
 */
public class SolBullet extends Bullet {

    /** O sol pra onde ela cai. */
    private final double centroX, centroY;

    private double raio;
    private double angulo;

    /** Quanto o raio encolhe por tick (o "cair"). */
    private final double velocidadeRadial;

    /**
     * Quanto ela anda DE LADO por tick, em pixels — nao em radianos.
     *
     * A diferenca e grande. Com velocidade angular fixa, a velocidade real
     * de lado vale angulo x raio: la fora, a 400 px do centro, a bala
     * varreria mais de 10 px por frame (ela passaria voando, indesviavel) e
     * perto do centro ela quase pararia. Guardando a velocidade em pixels e
     * convertendo pra angulo a cada tick, ela anda sempre no mesmo ritmo, e
     * o desenho que sai disso e uma espiral de verdade.
     */
    private final double velocidadeTangencial;

    /** Raio em que o sol come a bala. */
    private final double raioDeConsumo;

    private final Color cor;

    public SolBullet(double centroX, double centroY,
                     double raio, double angulo,
                     double velocidadeRadial, double velocidadeTangencial,
                     double raioDeConsumo,
                     double raioDaBala, Color cor) {

        this.centroX = centroX;
        this.centroY = centroY;
        this.raio = raio;
        this.angulo = angulo;
        this.velocidadeRadial = velocidadeRadial;
        this.velocidadeTangencial = velocidadeTangencial;
        this.raioDeConsumo = raioDeConsumo;
        this.cor = cor;

        this.radius = raioDaBala;
        this.hitPlayer = true;

        posicionar();
    }

    private void posicionar() {
        this.x = centroX + Math.cos(angulo) * raio;
        this.y = centroY + Math.sin(angulo) * raio;
    }

    @Override
    public void tick() {

        raio -= velocidadeRadial;

        // Pixels por tick viram radianos por tick aqui: andar 'v' de lado
        // num circulo de raio 'r' e andar v/r de angulo. O max() evita a
        // divisao explodir quando ela chega quase no centro (que e
        // justamente onde ela esta prestes a ser comida).
        angulo += velocidadeTangencial / Math.max(12, raio);

        // CONSUMIDA PELO SOL, e nao "saiu da tela".
        //
        // E o que fecha o ciclo do ataque: as balas vem de fora, caem no
        // sol e somem la dentro. Deixar elas atravessarem o centro faria
        // cada uma sair pelo outro lado e o padrao viraria um ida-e-volta
        // sem leitura nenhuma.
        if (raio <= raioDeConsumo) {
            isAlive = false;
            return;
        }

        posicionar();

        // O DANO E RESPONSABILIDADE DA PROPRIA BALA.
        //
        // Eu escrevi esta classe inteira e esqueci este bloco. O resultado
        // foi um ataque bonito e completamente inofensivo: as balas
        // desciam, encostavam no jogador e passavam direto.
        //
        // Nao da erro nenhum e nao chama atencao sozinho, porque o Main so
        // cuida do graze (Player.contarGraze) e da colisao das balas DO
        // JOGADOR contra inimigos. Quem confere "encostei no jogador?" e
        // cada tipo de bala, no proprio tick — como o NotaBullet, o
        // IntegralBullet e todos os outros fazem.
        if (Main.player == null) {
            return;
        }

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        if (dist <= radius + Main.player.getRadius()) {

            // So some se o dano foi ACEITO. Se o jogador estava
            // invulneravel, a bala atravessa em vez de sumir de graca — do
            // contrario, tomar um hit limparia um corredor inteiro.
            if (Main.player.levarDano()) {
                isAlive = false;
            }
        }
    }

    @Override
    public void render(Graphics2D g) {

        int r = (int) radius;

        // Halo: a bala e vermelha num campo vermelho, entao ela precisa de
        // um contorno claro pra existir contra o fundo. O mesmo problema
        // que as notas brancas resolvem pelo lado oposto do espectro.
        g.setColor(new Color(255, 220, 200, 90));
        g.fillOval((int) x - r - 3, (int) y - r - 3, (r + 3) * 2, (r + 3) * 2);

        g.setColor(cor);
        g.fillOval((int) x - r, (int) y - r, r * 2, r * 2);

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(1.6f));

        g.setColor(new Color(255, 245, 235, 220));
        g.drawOval((int) x - r, (int) y - r, r * 2, r * 2);

        // Miolo claro: da a ela um centro visivel, que e o que o olho usa
        // pra julgar distancia numa bala grande.
        int nucleo = Math.max(1, (int) (radius * 0.42));

        g.setColor(new Color(255, 250, 240, 235));
        g.fillOval((int) x - nucleo, (int) y - nucleo, nucleo * 2, nucleo * 2);

        g.setStroke(anterior);
    }

    /** Onde ela vai estar daqui a 'ticks' frames — usado pelo aviso de rota. */
    public double raioDaqui(int ticks) {
        return raio - velocidadeRadial * ticks;
    }

    public double getRaioAtual() {
        return raio;
    }

    /** Fora do campo por uma margem larga? (o nascimento acontece la fora) */
    public boolean longeDemais() {
        return raio > Math.max(Main.CAMPO_W, Main.CAMPO_H) * 1.4;
    }
}
