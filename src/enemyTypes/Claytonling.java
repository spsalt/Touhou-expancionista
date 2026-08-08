package src.enemyTypes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import src.Assets;
import src.Config;
import src.Main;

/**
 * CLAYTONLING — o minion do Clayton.
 *
 * Persegue o jogador em linha reta. Mas se o jogador CONSEGUIR DESVIAR, o
 * claytonling perde a coragem e foge da tela.
 *
 * COMO ELE SABE QUE FOI DESVIADO
 * ------------------------------
 * Nao da pra so olhar a distancia atual — ela sobe e desce o tempo todo
 * enquanto os dois se mexem. O que caracteriza um desvio e a distancia
 * ter DIMINUIDO ate um minimo e depois voltar a CRESCER: significa que ele
 * chegou o mais perto que ia chegar e ja passou reto.
 *
 * Entao ele guarda a menor distancia que ja atingiu (menorDistancia) e,
 * quando a distancia atual passa dela por uma margem de folga, entende que
 * errou e foge. A margem existe pra um tremidinha do jogador nao contar
 * como desvio.
 *
 * Ele so pode desistir depois de ter chegado perto (raioDeAproximacao),
 * senao fugiria logo no comeco, quando ainda esta longe e a distancia
 * naturalmente oscila.
 */
public class Claytonling extends Enemy {

    /** O que ele esta fazendo agora. */
    private enum Estado {
        /** Ainda ajustando a rota — da pra ele te seguir se voce andar. */
        APROXIMANDO,
        /** Rota TRAVADA: vai reto e nao corrige mais. E aqui que se desvia. */
        INVESTINDO,
        /** Foi desviado: segue reto pra fora da tela e nao volta. */
        FUGINDO
    }

    private Estado estado = Estado.APROXIMANDO;

    /**
     * Direcao travada da investida. Fica fixa desde o momento em que ele
     * decide atacar — e o que permite desviar: se ele corrigisse a rota o
     * tempo todo, seria impossivel escapar de um perseguidor mais rapido.
     */
    private double investidaX, investidaY;

    /** Distancia em que ele para de corrigir a rota e parte pra cima de voce. */
    private final double raioDeInvestida;

    private final double velocidadePerseguicao;
    private final double velocidadeFuga;

    /** Velocidade durante a investida travada (mais rapida que a aproximacao). */
    private final double velocidadeInvestida;

    /** Menor distancia ate o jogador ja alcancada nesta perseguicao. */
    private double menorDistancia = Double.MAX_VALUE;

    /** So considera "desviado" depois de ter chegado a esta distancia. */
    private final double raioDeAproximacao;

    /** Quanto a distancia precisa crescer, alem do minimo, pra ele desistir. */
    private final double folgaDeDesvio;

    /** Se demorar demais perseguindo, desiste do mesmo jeito. */
    private final int ticksAteDesistir;

    /** Direcao da fuga, travada no momento em que ele desiste. */
    private double fugaX = 0, fugaY = -1;

    public Claytonling(double x, double y) {

        super(x, y,
              Config.getDouble("clayton.claytonling.hp", 4.0),
              Config.getDouble("clayton.claytonling.raio", 13.0));

        this.velocidadePerseguicao = Config.getDouble("clayton.claytonling.velocidade", 2.6);
        this.velocidadeFuga        = Config.getDouble("clayton.claytonling.velocidadeFuga", 7.0);
        this.velocidadeInvestida   = Config.getDouble("clayton.claytonling.velocidadeInvestida", 6.2);
        this.raioDeAproximacao     = Config.getDouble("clayton.claytonling.raioDeAproximacao", 130.0);
        this.raioDeInvestida       = Config.getDouble("clayton.claytonling.raioDeInvestida", 190.0);
        this.folgaDeDesvio         = Config.getDouble("clayton.claytonling.folgaDeDesvio", 45.0);
        this.ticksAteDesistir      = Config.getInt("clayton.claytonling.ticksAteDesistir", 420);

        this.pontos = Config.getInt("clayton.claytonling.pontos", 60);
        this.itens  = Config.getInt("clayton.claytonling.itens", 1);

        this.sprite = Config.getString("clayton.claytonling.sprite", "sprites/bosses/clayton-base.png");
        this.escalaSprite = Config.getDouble("clayton.claytonling.escalaSprite", 2.0);
    }

    /**
     * Alem do movimento herdado, checa o CONTATO com o jogador.
     *
     * Claytonling nao atira — o corpo dele E o ataque. Sem esta checagem
     * ele atravessava o jogador sem fazer nada, e a spell card inteira
     * virava enfeite.
     */
    @Override
    public void tick() {

        super.tick();

        if (isAlive) {
            colidirComJogador();
        }
    }

    /**
     * Machuca ao encostar — mas SO enquanto ainda e uma ameaca.
     * Quem ja foi desviado esta indo embora e nao deve mais dar dano:
     * e a recompensa por ter desviado direito.
     */
    private void colidirComJogador() {

        if (estado == Estado.FUGINDO || Main.player == null) {
            return;
        }

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        if (dist <= radius + Main.player.getRadius()) {
            Main.player.levarDano();
        }
    }

    /**
     * Tres fases: aproximar corrigindo a rota, travar a investida, e (se
     * foi desviado) fugir reto pra fora.
     *
     * A fase de INVESTIDA travada e o que torna o desvio possivel. Antes
     * ele perseguia corrigindo a rota a cada tick — como e mais rapido que
     * o jogador, nao existia desvio, so uma questao de tempo ate encostar.
     */
    @Override
    protected void mover() {

        if (estado == Estado.FUGINDO) {
            x += fugaX * velocidadeFuga;
            y += fugaY * velocidadeFuga;
            return;
        }

        if (Main.player == null) {
            desistir();
            return;
        }

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        if (dist < menorDistancia) {
            menorDistancia = dist;
        }

        // --- APROXIMANDO: ainda mira, mas devagar ---
        if (estado == Estado.APROXIMANDO) {

            if (dist <= raioDeInvestida) {
                travarInvestida();
            } else {
                x += dirXParaJogador() * velocidadePerseguicao;
                y += dirYParaJogador() * velocidadePerseguicao;
            }

            if (t > ticksAteDesistir) {
                desistir();
            }

            return;
        }

        // --- INVESTINDO: rota fixa, sem correcao ---
        x += investidaX * velocidadeInvestida;
        y += investidaY * velocidadeInvestida;

        // Passou reto: chegou perto e agora esta se afastando do ponto
        // mais proximo que alcancou. Perdeu a chance e vai embora.
        boolean chegouPerto = menorDistancia <= raioDeAproximacao;
        boolean estaSeAfastando = dist > menorDistancia + folgaDeDesvio;

        if (chegouPerto && estaSeAfastando) {
            desistir();
        }
    }

    /**
     * Congela a direcao e acelera: e a "investida" que o jogador desvia.
     * O sinal visual (o aro fica vermelho) avisa que a rota travou.
     */
    private void travarInvestida() {

        estado = Estado.INVESTINDO;

        investidaX = dirXParaJogador();
        investidaY = dirYParaJogador();

        // Se por acaso estiver exatamente em cima, desce.
        if (investidaX == 0 && investidaY == 0) {
            investidaY = 1;
        }
    }

    /**
     * Vira a cauda e corre pra fora da tela.
     * A direcao e travada AGORA e nao recalculada: um fujao que ficasse
     * corrigindo a rota pareceria que ainda esta perseguindo.
     */
    private void desistir() {

        estado = Estado.FUGINDO;

        // Se ele ja estava investindo, MANTEM a direcao da investida: ele
        // passou reto por voce e sai pelo mesmo lado, "pelas suas costas".
        // Trocar pra direcao oposta ao jogador aqui faria ele dar um
        // solavanco estranho no ar.
        if (investidaX != 0 || investidaY != 0) {
            fugaX = investidaX;
            fugaY = investidaY;
            return;
        }

        if (Main.player == null) {
            fugaX = 0;
            fugaY = 1;
            return;
        }

        double dx = x - Main.player.getX();
        double dy = y - Main.player.getY();
        double d = Math.sqrt(dx * dx + dy * dy);

        if (d < 0.01) {
            fugaX = 0;
            fugaY = 1;
            return;
        }

        fugaX = dx / d;
        fugaY = dy / d;
    }

    @Override
    public void render(Graphics2D g) {

        BufferedImage img = (sprite == null) ? null : Assets.get(sprite);

        double lado = radius * 2 * escalaSprite;

        if (img != null) {

            double fator = lado / Math.max(img.getWidth(), img.getHeight());
            int larg = (int) (img.getWidth() * fator);
            int alt  = (int) (img.getHeight() * fator);

            g.drawImage(img, (int) (x - larg / 2.0), (int) (y - alt / 2.0), larg, alt, null);

        } else {
            g.setColor(new Color(120, 200, 120));
            g.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }

        // Aro colorido pelo estado: verde perseguindo, azul fugindo.
        // E o unico jeito do jogador saber que aquele ja e inofensivo.
        // Verde = ainda mirando | VERMELHO = rota travada, hora de desviar
        // | azul = ja foi desviado e e inofensivo.
        Color aro;

        switch (estado) {
            case FUGINDO:    aro = new Color(120, 180, 255, 200); break;
            case INVESTINDO: aro = new Color(255, 90, 90, 230);   break;
            default:         aro = new Color(120, 255, 120, 200); break;
        }

        g.setColor(aro);

        g.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));

        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }

    /* =========================
            GETTERS
       ========================= */

    public boolean estaFugindo() {
        return estado == Estado.FUGINDO;
    }

    /** true enquanto a rota esta travada (a janela pra desviar). */
    public boolean estaInvestindo() {
        return estado == Estado.INVESTINDO;
    }
}
