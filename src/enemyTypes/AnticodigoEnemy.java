package src.enemyTypes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Random;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.BolaBullet;

/**
 * CRIATURA DO ANTICODIGO — a coisa que fica na beirada da tela chutando.
 *
 * Entra por uma das laterais, PARA encostada na parede e passa a lancar
 * bolas de futebol em ARCO no jogador: a bola sobe, perde forca e cai em
 * cima dele (ver BolaBullet).
 *
 * DE ONDE ELA VEM
 * ---------------
 * Ela nasceu pra resolver um problema concreto do spell card dos
 * claytonlings: os minions perseguem o jogador, entao a resposta certa
 * era sempre a mesma — ficar em movimento constante. Quanto mais
 * claytonling na tela, mais isso virava uma so leitura repetida, e mais
 * injusto ficava.
 *
 * A criatura faz o oposto: ela e FIXA e o ataque dela e LENTO, mas cai de
 * cima. Isso pune exatamente o comportamento que os claytonlings premiam
 * (correr em linha reta pelo campo aberto), e recompensa parar e ler. Com
 * os dois na tela, o jogador tem que alternar entre as duas posturas em
 * vez de escolher uma pra luta toda.
 *
 * Ela fica encostada na parede DE PROPOSITO: e um alvo dificil de acertar
 * com o leque (que atira pra cima) e facil com o ricochete (que quica nas
 * laterais). Quem comprou o nivel 4 na lojinha do Perea encontra aqui a
 * primeira situacao em que o ricochete e claramente a ferramenta certa.
 */
public class AnticodigoEnemy extends Enemy {

    private static final Random RNG = new Random();

    /** true = entrou pela esquerda. Define de que lado ela encosta. */
    private final boolean pelaEsquerda;

    /** X em que ela para, colada na parede. */
    private final double xDeParada;

    private final double velocidadeDeEntrada;

    /** Ticks entre um chute e o proximo, e quanto falta pro proximo. */
    private final int cadencia;
    private int ateOChute;

    /** Ticks de preparo antes do chute (o agacho que avisa). */
    private final int ticksDePreparo;
    private int preparando = 0;

    /**
     * Quanto tempo a bola passa no ar, em ticks.
     *
     * E ESTE numero que faz o tiro ser um arco e nao uma reta: a
     * velocidade inicial e calculada de tras pra frente a partir dele (ver
     * chutar). Tempo de voo maior = arco mais alto e mais tempo pro
     * jogador sair de baixo.
     */
    private final int tempoDeVoo;

    private final double gravidade;
    private final double raioDaBola;

    /** Ticks de vida antes de ir embora sozinha. */
    private final int duracao;

    /** Contador proprio do gingado, so estetico. */
    private double fase;

    public AnticodigoEnemy(double y, boolean pelaEsquerda) {

        super(pelaEsquerda ? Main.CAMPO_X - 40 : Main.CAMPO_X + Main.CAMPO_W + 40,
              y,
              Config.getDouble("anticodigo.hp", 34),
              Config.getDouble("anticodigo.raio", 19));

        this.pelaEsquerda = pelaEsquerda;

        double margem = Config.getDouble("anticodigo.margemDaParede", 26);

        this.xDeParada = pelaEsquerda
                ? Main.CAMPO_X + margem
                : Main.CAMPO_X + Main.CAMPO_W - margem;

        this.velocidadeDeEntrada = Config.getDouble("anticodigo.velocidadeDeEntrada", 3.4);

        this.cadencia       = Math.max(20, Config.getInt("anticodigo.cadencia", 96));
        this.ticksDePreparo = Math.max(1, Config.getInt("anticodigo.ticksDePreparo", 30));
        this.tempoDeVoo     = Math.max(20, Config.getInt("anticodigo.tempoDeVoo", 95));
        this.gravidade      = Config.getDouble("anticodigo.gravidade", 0.16);
        this.raioDaBola     = Config.getDouble("anticodigo.raioDaBola", 11);
        this.duracao        = Config.getInt("anticodigo.duracao", 900);

        this.pontos = Config.getInt("anticodigo.pontos", 250);
        this.itens  = Config.getInt("anticodigo.itens", 3);

        // Cada uma comeca com um atraso diferente, senao as duas chutam no
        // mesmo frame e as bolas caem sempre em par.
        this.ateOChute = cadencia / 2 + RNG.nextInt(cadencia);

        this.fase = RNG.nextDouble() * Math.PI * 2;
    }

    @Override
    public void tick() {

        mover();
        atirar();

        if (t > duracao) {
            isAlive = false;
        }

        t++;
    }

    @Override
    protected void mover() {

        fase += 0.08;

        if (pelaEsquerda && x < xDeParada) {
            x += velocidadeDeEntrada;
        } else if (!pelaEsquerda && x > xDeParada) {
            x -= velocidadeDeEntrada;
        }
    }

    @Override
    protected void atirar() {

        // Ainda entrando: chutar de fora seria bola vinda do nada.
        if (x < Main.CAMPO_X || x > Main.CAMPO_X + Main.CAMPO_W) {
            return;
        }

        if (preparando > 0) {

            preparando--;

            if (preparando == 0) {
                chutar();
            }

            return;
        }

        ateOChute--;

        if (ateOChute <= 0) {
            preparando = ticksDePreparo;
            ateOChute = cadencia;
        }
    }

    /**
     * Chuta uma bola que cai EM CIMA de onde o jogador esta agora.
     *
     * A conta e balistica invertida: fixado o tempo de voo T e a gravidade
     * g, a velocidade que leva de (x,y) ate (px,py) em exatamente T ticks e
     *
     *     dx = (px - x) / T
     *     dy = (py - y) / T  -  g*T/2
     *
     * Como o jogador esta ABAIXO da criatura, (py - y)/T e positivo, mas o
     * termo -g*T/2 e maior — o que da um dy NEGATIVO, ou seja, a bola sai
     * pra cima e a gravidade traz ela de volta. E dai que vem o arco: ele
     * nao e desenhado, ele e consequencia de mirar no futuro.
     *
     * Mirar onde o jogador ESTA (e nao onde vai estar) e proposital: a
     * bola demora um segundo e meio pra chegar, entao ela SEMPRE da pra
     * desviar andando. Ela nao existe pra acertar — existe pra tirar o
     * jogador do lugar onde ele estava confortavel.
     */
    private void chutar() {

        if (Main.player == null) {
            return;
        }

        Som.tocar(Som.CHUTE);

        double alvoX = Main.player.getX();
        double alvoY = Main.player.getY();

        double dx = (alvoX - x) / tempoDeVoo;
        double dy = (alvoY - y) / tempoDeVoo - gravidade * tempoDeVoo / 2.0;

        Main.bullets.add(new BolaBullet(x, y, dx, dy, gravidade, raioDaBola));
    }

    /**
     * A criatura: uma mancha escura de anticodigo com olhos e uma perna
     * que arma o chute.
     *
     * Desenhada na mao porque nao ha arte pra ela — e, honestamente,
     * porque uma massa preta tremendo com dois olhos amarelos comunica
     * "coisa errada saindo do codigo" melhor do que qualquer foto que eu
     * pudesse recortar. O tremor e ruido sobre o contorno, no ritmo do
     * proprio relogio dela.
     */
    @Override
    public void render(Graphics2D g) {

        // Agacha antes de chutar: o mesmo aviso do cachorro da Adriana. E
        // o unico jeito de um ataque que cai de cima ser justo.
        double carga = (preparando > 0)
                     ? 1 - preparando / (double) ticksDePreparo
                     : 0;

        double r = radius * (1 + 0.18 * carga);

        // Corpo: varias camadas de escuro com o contorno tremendo.
        for (int camada = 3; camada >= 1; camada--) {

            double rr = r * (0.55 + camada * 0.16);
            int alpha = 90 + camada * 45;

            g.setColor(new Color(18, 30 + camada * 12, 22, Math.min(255, alpha)));

            int lados = 11;
            int[] px = new int[lados];
            int[] py = new int[lados];

            for (int i = 0; i < lados; i++) {

                double a = 2 * Math.PI * i / lados;

                // O ruido usa o angulo E o tempo: o contorno nao gira, ele
                // FERVE, que e o que faz ela parecer instavel em vez de
                // apenas girar.
                double ruido = 1 + 0.16 * Math.sin(fase * 2.4 + i * 2.7 + camada);

                px[i] = (int) (x + Math.cos(a) * rr * ruido);
                py[i] = (int) (y + Math.sin(a) * rr * ruido);
            }

            g.fillPolygon(px, py, lados);
        }

        // Olhos, virados pro lado de dentro do campo.
        double olhar = pelaEsquerda ? 1 : -1;

        for (int i = -1; i <= 1; i += 2) {

            double ox = x + olhar * r * 0.30;
            double oy = y + i * r * 0.28;

            // Ficam vermelhos enquanto arma o chute: o aviso tambem esta
            // na cor, pra quem estiver olhando pro outro canto da tela.
            g.setColor(carga > 0 ? new Color(255, 90, 70) : new Color(230, 220, 90));
            g.fillOval((int) (ox - r * 0.16), (int) (oy - r * 0.16),
                       (int) (r * 0.32), (int) (r * 0.32));
        }

        // A "perna" armando: um risco que se estica na direcao do chute.
        if (carga > 0) {

            Stroke anterior = g.getStroke();

            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(120, 255, 140, 200));

            g.drawLine((int) x, (int) y,
                       (int) (x + olhar * r * (1.0 + carga)),
                       (int) (y - r * carga * 1.2));

            g.setStroke(anterior);
        }

        renderBarraDeVida(g);

        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius),
                       (int) (radius * 2), (int) (radius * 2));
        }
    }

    /** true enquanto ela esta armando o chute (util pro debug). */
    public boolean estaArmando() {
        return preparando > 0;
    }
}
