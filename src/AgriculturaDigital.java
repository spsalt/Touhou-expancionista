package src;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import src.enemyTypes.BossEnemy;
import src.enemyTypes.Enemy;

/**
 * AGRICULTURA DIGITAL — o item de apoio da lojinha do Perea.
 *
 * Dois drones saem do estudante a 45 e a 135 graus, sobem, acham o chefe e
 * passam a pulverizar AGROTOXICO verde em cima dele. Enquanto a nuvem
 * estiver ativa, o chefe fica LENTO: os ataques saem mais espacados e a
 * deriva dele arrasta.
 *
 * COMO A LENTIDAO E FEITA, E POR QUE ASSIM
 * ----------------------------------------
 * Nao existe um "fator de velocidade" espalhado por cada spell card — isso
 * exigiria mexer nos onze ataques do jogo, e cada um multiplicaria o
 * proprio jeito. Em vez disso o chefe simplesmente PULA TICKS: ele recebe
 * menos frames de vida por segundo (ver BossEnemy.tick).
 *
 * O efeito colateral e bonito e proposital: como o relogio do spell card
 * tambem para nos ticks pulados, envenenar o chefe da mais tempo de spell
 * card, e nao menos. O agrotoxico nao "adianta" a luta, ele desacelera ela
 * inteira — que e o que faz sentido pra um veneno.
 *
 * O QUE ELE NAO FAZ: dano. E de propósito. O Olho Laser ja e o item de
 * dano; se este tambem machucasse, comprar os dois seria obvio demais e
 * nao haveria escolha nenhuma na loja.
 */
public class AgriculturaDigital {

    /** Uma baforada de agrotoxico. So visual — quem trava o chefe e o tick. */
    private static class Baforada {

        double x, y;
        double dx, dy;
        double raio;
        int vida;
        final int vidaMaxima;

        Baforada(double x, double y, double dx, double dy, double raio, int vida) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.raio = raio;
            this.vida = vida;
            this.vidaMaxima = vida;
        }
    }

    /** Um drone. Sai em diagonal, depois caça o alvo e pulveriza. */
    private static class Drone {

        double x, y;
        double dx, dy;
        final double anguloDeSaida;
        int t = 0;

        Drone(double x, double y, double anguloDeSaida) {
            this.x = x;
            this.y = y;
            this.anguloDeSaida = anguloDeSaida;
        }
    }

    private static final Random RNG = new Random();

    private final Player dono;

    private final Drone[] drones = new Drone[2];
    private final List<Baforada> nuvem = new ArrayList<>();

    private int t = 0;
    private boolean isAlive = true;

    /* --- ajustes --- */

    private final int duracao;
    private final int ticksDeSaida;
    private final double velocidadeDeSaida;
    private final double velocidadeDeCaca;
    private final double distanciaDeTrabalho;
    private final int intervaloDaBaforada;

    public AgriculturaDigital(Player dono) {

        this.dono = dono;

        this.duracao             = Math.max(60, Config.getInt("perea.agricultura.duracao", 600));
        this.ticksDeSaida        = Math.max(5, Config.getInt("perea.agricultura.ticksDeSaida", 26));
        this.velocidadeDeSaida   = Config.getDouble("perea.agricultura.velocidadeDeSaida", 6.0);
        this.velocidadeDeCaca    = Config.getDouble("perea.agricultura.velocidadeDeCaca", 4.2);
        this.distanciaDeTrabalho = Config.getDouble("perea.agricultura.distanciaDeTrabalho", 70);
        this.intervaloDaBaforada = Math.max(2, Config.getInt("perea.agricultura.intervaloDaBaforada", 7));

        // 45 e 135 graus, medidos a partir do "pra cima" da tela. Na
        // matematica de tela o Y cresce pra baixo, entao o pra cima e
        // -PI/2 e as duas diagonais saem simetricas dele.
        drones[0] = new Drone(dono.getX(), dono.getY(), -Math.PI / 4);
        drones[1] = new Drone(dono.getX(), dono.getY(), -3 * Math.PI / 4);

        Som.tocar(Som.DRONE);
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        if (!isAlive) {
            return;
        }

        Enemy alvo = alvoPreferido();

        for (int i = 0; i < drones.length; i++) {
            moverDrone(drones[i], alvo);
        }

        if (alvo != null && t % intervaloDaBaforada == 0) {
            pulverizar(alvo);
        }

        envenenar(alvo);
        atualizarNuvem();

        t++;

        // Enquanto a nuvem nao se dissipar o efeito continua vivo, senao
        // as baforadas do ultimo frame sumiriam de uma vez.
        if (t >= duracao && nuvem.isEmpty()) {
            isAlive = false;
        }
    }

    /**
     * Quem os drones vao perseguir.
     *
     * Chefe primeiro, sempre. Sem isso, os drones gastariam a carga inteira
     * num claytonling que passou na frente — e o item foi comprado pra
     * segurar a luta, nao pra atrapalhar um minion.
     */
    private Enemy alvoPreferido() {

        BossEnemy chefe = Main.chefeEmCena();

        if (chefe != null && chefe.isAlive()) {
            return chefe;
        }

        return Main.inimigoMaisProximo(dono.getX(), dono.getY());
    }

    /**
     * Duas etapas: primeiro a saida em diagonal, depois a caça.
     *
     * A saida existe por leitura: se eles ja partissem direto pro chefe,
     * o jogador veria dois pontos verdes atravessando a tela e nao
     * entenderia que sairam DELE. Os 26 ticks de diagonal sao o tempo de
     * ligar uma coisa na outra.
     */
    private void moverDrone(Drone d, Enemy alvo) {

        d.t++;

        if (d.t <= ticksDeSaida || alvo == null) {

            d.x += Math.cos(d.anguloDeSaida) * velocidadeDeSaida;
            d.y += Math.sin(d.anguloDeSaida) * velocidadeDeSaida;

            // Nao deixa sair do campo enquanto nao tem pra onde ir.
            d.x = Math.max(Main.CAMPO_X + 10, Math.min(Main.CAMPO_X + Main.CAMPO_W - 10, d.x));
            d.y = Math.max(Main.CAMPO_Y + 10, Math.min(Main.CAMPO_Y + Main.CAMPO_H - 10, d.y));

            return;
        }

        // Cada drone fica de um lado do alvo, e nao os dois no mesmo
        // ponto: dois sprites sobrepostos parecem um so, e a nuvem sairia
        // toda de um lugar.
        double lado = (d.anguloDeSaida < -Math.PI / 2) ? -1 : 1;

        double destinoX = alvo.getX() + lado * distanciaDeTrabalho;
        double destinoY = alvo.getY() - distanciaDeTrabalho * 0.35
                        + Math.sin(d.t * 0.06) * 14;   // bamboleio de drone parado

        double dx = destinoX - d.x;
        double dy = destinoY - d.y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist > 1) {
            double v = Math.min(velocidadeDeCaca, dist);
            d.dx = dx / dist * v;
            d.dy = dy / dist * v;
        } else {
            d.dx = 0;
            d.dy = 0;
        }

        d.x += d.dx;
        d.y += d.dy;
    }

    /** Cada drone cospe uma baforada em direcao ao alvo. */
    private void pulverizar(Enemy alvo) {

        for (int i = 0; i < drones.length; i++) {

            Drone d = drones[i];

            // Ainda saindo: nao pulveriza no vazio.
            if (d.t <= ticksDeSaida) {
                continue;
            }

            double dx = alvo.getX() - d.x;
            double dy = alvo.getY() - d.y;
            double dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));

            double vel = Config.getDouble("perea.agricultura.velocidadeDaBaforada", 2.6);

            nuvem.add(new Baforada(
                d.x, d.y,
                dx / dist * vel + (RNG.nextDouble() - 0.5) * 0.8,
                dy / dist * vel + (RNG.nextDouble() - 0.5) * 0.8,
                Config.getDouble("perea.agricultura.raioDaBaforada", 16) * (0.7 + RNG.nextDouble() * 0.6),
                Config.getInt("perea.agricultura.ticksDaBaforada", 46)
            ));
        }
    }

    /**
     * Renova o veneno do chefe.
     *
     * Renova todo tick em vez de aplicar uma vez com duracao fixa: assim o
     * efeito acaba naturalmente alguns ticks depois dos drones pararem, e
     * nao existe estado pra limpar se a luta trocar de chefe no meio.
     */
    private void envenenar(Enemy alvo) {

        if (t >= duracao || !(alvo instanceof BossEnemy)) {
            return;
        }

        // So envenena quando o drone ja chegou perto — senao o chefe ficava
        // lento desde o frame do clique, antes de qualquer fumaca aparecer.
        for (int i = 0; i < drones.length; i++) {

            if (drones[i].t <= ticksDeSaida) {
                continue;
            }

            ((BossEnemy) alvo).aplicarAgrotoxico(
                    Config.getInt("perea.agricultura.ticksDeVeneno", 30));

            return;
        }
    }

    private void atualizarNuvem() {

        for (int i = nuvem.size() - 1; i >= 0; i--) {

            Baforada b = nuvem.get(i);

            b.x += b.dx;
            b.y += b.dy;

            // Freia e incha: e assim que fumaca se comporta, e e o que
            // separa ela visualmente de uma bala verde.
            b.dx *= 0.93;
            b.dy *= 0.93;
            b.raio *= 1.02;

            b.vida--;

            if (b.vida <= 0) {
                nuvem.remove(i);
            }
        }
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        if (!isAlive) {
            return;
        }

        desenharNuvem(g);
        desenharDrones(g);
    }

    /**
     * A fumaca: circulos verdes translucidos que crescem e somem.
     *
     * Alfa baixo e MUITOS circulos sobrepostos, em vez de poucos e opacos.
     * Fumaca opaca em cima do chefe esconderia o alvo, e o jogador precisa
     * continuar vendo onde atirar — o mesmo cuidado do miolo escuro do
     * Olho Laser.
     */
    private void desenharNuvem(Graphics2D g) {

        for (int i = 0; i < nuvem.size(); i++) {

            Baforada b = nuvem.get(i);

            double f = b.vida / (double) b.vidaMaxima;

            int a = (int) (95 * f);

            if (a <= 0) {
                continue;
            }

            g.setColor(new Color(120, 220, 90, a));
            g.fillOval((int) (b.x - b.raio), (int) (b.y - b.raio),
                       (int) (b.raio * 2), (int) (b.raio * 2));

            g.setColor(new Color(200, 255, 150, (int) (a * 0.7)));
            g.drawOval((int) (b.x - b.raio), (int) (b.y - b.raio),
                       (int) (b.raio * 2), (int) (b.raio * 2));
        }
    }

    /**
     * Os drones: um X de helices com um corpinho no meio.
     *
     * Desenhado na mao porque nesse tamanho (uns 14 px) um PNG viraria
     * mancha. As helices giram rapido de proposito — e o unico jeito de
     * um ponto de 14 px ler como "maquina voando" e nao como bala.
     */
    private void desenharDrones(Graphics2D g) {

        // TAMANHO em um lugar so: tudo abaixo e proporcional a ele.
        //
        // Eles nasceram com 9 px de braco e sumiam — a nuvem que eles
        // soltam e larga, e o proprio drone acabava menor que uma
        // baforada. O item custa moeda e o jogador tem que VER o que
        // comprou trabalhando.
        double escala = Config.getDouble("perea.agricultura.tamanhoDoDrone", 2.0);

        double braco = 9 * escala;
        double raioDoCorpo = 5 * escala;

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke((float) (2f * Math.max(1, escala * 0.8))));

        for (int i = 0; i < drones.length; i++) {

            Drone d = drones[i];

            g.setColor(new Color(70, 80, 90));

            for (int k = 0; k < 4; k++) {

                double ang = Math.PI / 4 + k * Math.PI / 2;

                g.drawLine((int) d.x, (int) d.y,
                           (int) (d.x + Math.cos(ang) * braco),
                           (int) (d.y + Math.sin(ang) * braco));
            }

            // Helices: arcos girando, um por braco.
            g.setColor(new Color(190, 230, 255, 150));

            for (int k = 0; k < 4; k++) {

                double ang = Math.PI / 4 + k * Math.PI / 2;
                double hx = d.x + Math.cos(ang) * braco;
                double hy = d.y + Math.sin(ang) * braco;

                // A oscilacao da helice NAO escala junto: ela e o borrao
                // da pa girando, e um borrao proporcional ao tamanho faria
                // o drone grande parecer bater as asas devagar.
                double r = (5 * escala) + 1.5 * Math.sin(d.t * 0.9 + k);

                g.drawOval((int) (hx - r), (int) (hy - r), (int) (r * 2), (int) (r * 2));
            }

            g.setColor(new Color(120, 220, 90));
            g.fillOval((int) (d.x - raioDoCorpo), (int) (d.y - raioDoCorpo),
                       (int) (raioDoCorpo * 2), (int) (raioDoCorpo * 2));

            g.setColor(new Color(30, 40, 30));
            g.drawOval((int) (d.x - raioDoCorpo), (int) (d.y - raioDoCorpo),
                       (int) (raioDoCorpo * 2), (int) (raioDoCorpo * 2));

            // Luzinha piscando no corpo, so no tamanho grande — no
            // pequeno ela virava um pixel isolado no meio do verde.
            if (escala >= 1.5 && (d.t / 12) % 2 == 0) {

                double rl = raioDoCorpo * 0.35;

                g.setColor(new Color(255, 240, 160, 220));
                g.fillOval((int) (d.x - rl), (int) (d.y - rl), (int) (rl * 2), (int) (rl * 2));
            }
        }

        g.setStroke(anterior);
    }

    public boolean isAlive() {
        return isAlive;
    }
}
