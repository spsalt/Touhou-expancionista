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
        PERSEGUINDO,
        FUGINDO
    }

    private Estado estado = Estado.PERSEGUINDO;

    private final double velocidadePerseguicao;
    private final double velocidadeFuga;

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
        this.raioDeAproximacao     = Config.getDouble("clayton.claytonling.raioDeAproximacao", 130.0);
        this.folgaDeDesvio         = Config.getDouble("clayton.claytonling.folgaDeDesvio", 45.0);
        this.ticksAteDesistir      = Config.getInt("clayton.claytonling.ticksAteDesistir", 420);

        this.pontos = Config.getInt("clayton.claytonling.pontos", 60);
        this.itens  = Config.getInt("clayton.claytonling.itens", 1);

        this.sprite = Config.getString("clayton.claytonling.sprite", "sprites/bosses/clayton-base.png");
        this.escalaSprite = Config.getDouble("clayton.claytonling.escalaSprite", 2.0);
    }

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

        // Guarda o quao perto ele ja chegou.
        if (dist < menorDistancia) {
            menorDistancia = dist;
        }

        // Foi desviado: chegou perto, mas agora esta se afastando do
        // ponto mais proximo que alcancou.
        boolean chegouPerto = menorDistancia <= raioDeAproximacao;
        boolean estaSeAfastando = dist > menorDistancia + folgaDeDesvio;

        if (chegouPerto && estaSeAfastando) {
            desistir();
            return;
        }

        // Cansou de perseguir sem nunca encostar.
        if (t > ticksAteDesistir) {
            desistir();
            return;
        }

        // Perseguicao: vai reto na direcao do jogador.
        x += dirXParaJogador() * velocidadePerseguicao;
        y += dirYParaJogador() * velocidadePerseguicao;
    }

    /**
     * Vira a cauda e corre pra fora da tela.
     * A direcao e travada AGORA e nao recalculada: um fujao que ficasse
     * corrigindo a rota pareceria que ainda esta perseguindo.
     */
    private void desistir() {

        estado = Estado.FUGINDO;

        if (Main.player == null) {
            fugaY = -1;
            fugaX = 0;
            return;
        }

        // Foge no sentido OPOSTO ao jogador.
        double dx = x - Main.player.getX();
        double dy = y - Main.player.getY();
        double d = Math.sqrt(dx * dx + dy * dy);

        if (d < 0.01) {
            fugaX = 0;
            fugaY = -1;
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
        g.setColor(estado == Estado.FUGINDO
                 ? new Color(120, 180, 255, 200)
                 : new Color(120, 255, 120, 200));

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
}
