package src.bulletTypes;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import src.Assets;
import src.Config;
import src.Main;

/**
 * CULTISTA DO IEEE — a "bala" que forma as paredes do corredor.
 *
 * Ele nao voa: DESCE ANDANDO. Tem ciclo de caminhada, gingado horizontal e
 * vira pro lado pra onde esta indo, como um inimigo de plataforma 2D.
 *
 * POR QUE ISSO NAO E SO ENFEITE
 * -----------------------------
 * Uma parede de bala identica descendo e um objeto so, e o olho trata ela
 * como uma textura. Um monte de gente ANDANDO e um monte de individuos, e
 * o olho conta cada um. Isso muda a leitura do corredor: em vez de "existe
 * um vao ali", vira "existe uma passagem entre duas multidoes", que e
 * exatamente o que o ataque quer dizer.
 *
 * CADA UM TEM SUA PROPRIA FASE, e e por isso que a coisa toda funciona.
 * Com todos no mesmo quadro da animacao, cem cultistas viram um unico
 * organismo piscando junto — que e pior que nao ter animacao nenhuma,
 * porque chama atencao pra sincronia. Sorteando a fase do passo, a
 * velocidade do passo e a fase do gingado, eles viram uma multidao.
 *
 * O GINGADO E PEQUENO de proposito (uns 12 px contra um corredor de 150).
 * Ele existe pra a fileira nao parecer uma regua; se fosse grande, o
 * corredor mudaria de largura sozinho e o jogador seria punido por uma
 * coisa que nao tinha como prever.
 */
public class SeguidorBullet extends Bullet {

    /** Quantos quadros tem o ciclo de caminhada (arquivos _0 a _N-1). */
    private static final int QUADROS = 6;

    /** X em volta do qual ele ginga. O gingado nao acumula deriva. */
    private final double xBase;

    private final double velocidadeDeDescida;

    /* --- gingado --- */
    private final double amplitudeDoGingado;
    private final double frequenciaDoGingado;
    private final double faseDoGingado;

    /* --- animacao --- */

    /** Em qual quadro do ciclo ele comeca. */
    private final int quadroInicial;

    /** Ticks por quadro. Levemente diferente por cultista. */
    private final double ticksPorQuadro;

    private int t = 0;

    /** Pra que lado ele esta virado agora. Segue o gingado. */
    private boolean paraDireita = true;

    /**
     * -1 = anda pra esquerda, +1 = pra direita.
     *
     * NAO E SORTEADO: e o lado pra onde ele precisa se afastar pra abrir o
     * caminho. Quem nasce a esquerda do corredor anda pra esquerda, quem
     * nasce a direita anda pra direita — os dois lados se abrindo, que e a
     * imagem inteira do ataque.
     */
    private final int lado;

    /** Quanto ele ja se afastou. Limitado, senao a parede se desfaz. */
    private double afastamento = 0;

    private final double velocidadeDeAfastamento;
    private final double afastamentoMaximo;

    public SeguidorBullet(double x, double y, double velocidade, double raio, int lado) {

        this.x = x;
        this.y = y;
        this.xBase = x;
        this.radius = raio;
        this.hitPlayer = true;
        this.lado = (lado >= 0) ? 1 : -1;

        this.velocidadeDeAfastamento = Config.getDouble("papa.ieee.velocidadeDeAfastamento", 0.22);
        this.afastamentoMaximo       = Config.getDouble("papa.ieee.afastamentoMaximo", 34.0);

        this.velocidadeDeDescida = velocidade;

        this.amplitudeDoGingado  = Config.getDouble("papa.ieee.amplitudeDoGingado", 12.0);
        this.frequenciaDoGingado = Config.getDouble("papa.ieee.frequenciaDoGingado", 0.055);

        // AS TRES FONTES DE DESSINCRONIA.
        //
        // So sortear o quadro inicial nao basta: com todos avancando no
        // mesmo ritmo, eles ficam desalinhados mas em compasso, e o olho
        // percebe o compasso. Variando tambem a VELOCIDADE do passo, as
        // fases escorregam uma em relacao a outra o tempo todo e nunca
        // formam padrao.
        this.faseDoGingado  = Math.random() * Math.PI * 2;
        this.quadroInicial  = (int) (Math.random() * QUADROS);
        this.ticksPorQuadro = Config.getDouble("papa.ieee.ticksPorQuadro", 7.0)
                            * (0.75 + Math.random() * 0.5);
    }

    @Override
    public void tick() {

        t++;

        y += velocidadeDeDescida;

        // ELE ANDA PRO LADO DE FORA, ABRINDO O CAMINHO.
        //
        // O afastamento e cumulativo mas LIMITADO. Sem limite, as duas
        // fileiras continuariam se afastando pra sempre e a parede se
        // desfaria nas bordas da tela — o corredor ficaria largo demais
        // embaixo e o ataque perderia a forma. Com o teto, elas dao alguns
        // passos pro lado e ficam: o mar abre e para aberto.
        if (afastamento < afastamentoMaximo) {
            afastamento += velocidadeDeAfastamento;
        }

        // O gingado continua por cima do afastamento, so pra ninguem andar
        // como um bloco. O passo e o afastamento; o gingado e a vida.
        x = xBase + lado * afastamento
          + Math.sin(t * frequenciaDoGingado + faseDoGingado) * amplitudeDoGingado;

        // Ele fica virado pro lado pra onde esta indo. Fixo, e nao seguindo
        // o gingado: com o gingado, ele trocava de lado duas vezes por
        // segundo e parecia estar dancando, nao caminhando.
        paraDireita = (lado > 0);

        if (Main.foraDoCampo(x, y, Main.MARGEM_SAIDA_BALA)) {
            isAlive = false;
            return;
        }

        if (Main.player != null) {

            double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

            if (dist <= radius + Main.player.getRadius()) {
                if (Main.player.levarDano()) {
                    isAlive = false;
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {

        int quadro = (quadroInicial + (int) (t / ticksPorQuadro)) % QUADROS;

        String caminho = Config.getString("papa.ieee.spriteDoSeguidor",
                                          "sprites/GFX/seguidor_ieee") + "_" + quadro + ".png";

        BufferedImage img = Assets.get(caminho);

        if (img == null) {
            // Sem os quadros: cai numa bolinha, como toda bala do jogo faz
            // quando falta arte.
            g.setColor(new java.awt.Color(120, 175, 255));
            g.fillOval((int) (x - radius), (int) (y - radius),
                       (int) (radius * 2), (int) (radius * 2));
            return;
        }

        int alt = (int) (radius * 2 * Config.getDouble("papa.ieee.escalaDoSeguidor", 2.6));
        int larg = Math.max(1, img.getWidth() * alt / img.getHeight());

        // Versao ja redimensionada: sao ate 270 destes na tela ao mesmo
        // tempo, e reescalar o PNG a cada um, a cada frame, custaria caro.
        BufferedImage pronta = Assets.getEscalado(caminho, larg, alt);

        if (pronta != null) {
            img = pronta;
        }

        int x0 = (int) (x - larg / 2.0);
        int y0 = (int) (y - alt / 2.0);

        if (paraDireita) {
            g.drawImage(img, x0, y0, larg, alt, null);
        } else {
            // Espelha invertendo a largura no destino. Mais barato que
            // manter uma segunda copia de cada quadro em memoria.
            g.drawImage(img, x0 + larg, y0, -larg, alt, null);
        }
    }

    /* =========================
            GETTERS
       ========================= */

    public int getQuadroAtual() {
        return (quadroInicial + (int) (t / ticksPorQuadro)) % QUADROS;
    }

    public boolean isParaDireita() {
        return paraDireita;
    }
}
