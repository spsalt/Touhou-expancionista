package src.enemyTypes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.EquacaoBullet;

/**
 * CACHORRO QUE SABE CALCULO — o minion da forma maligna da Adriana.
 *
 * "DOS CACHORROS QUE SABEM CÁLCULO! DERIVEM ELE ATÉ O 0!"
 * (Roteiro.txt linha 31)
 *
 * Ele nao persegue o jogador: entra correndo pela lateral, PARA numa
 * altura sorteada e fica ali latindo equacao. A escolha e deliberada —
 * a Adriana ja tem um ataque que cobra posicionamento fino (as Somas de
 * Riemann eram uma parede giratoria); o que faltava era um ataque que
 * cobrasse ATENCAO DIVIDIDA, com varias fontes de tiro em lugares
 * diferentes da tela ao mesmo tempo.
 *
 * O tiro e MIRADO, mas com cadencia lenta e um aviso: o cachorro se
 * agacha (encolhe) por alguns ticks antes de latir. Sem esse tempo de
 * preparo, tres ou quatro cachorros mirando de cantos opostos viraria
 * dano aleatorio.
 *
 * Ele tem HP baixo de proposito. E um alvo, nao um obstaculo: matar os
 * cachorros e a resposta certa, e o jogador tem que decidir em qual
 * atirar primeiro enquanto desvia dos outros.
 */
public class CachorroEnemy extends Enemy {

    private static final Random RNG = new Random();

    /** Velocidade horizontal enquanto ele ainda esta entrando. */
    private final double velocidadeDeEntrada;

    /** X em que ele para. Depois disso so late. */
    private final double xDeParada;

    /** true = entrou pela esquerda (anda pra direita). */
    private final boolean pelaEsquerda;

    private final int cadencia;
    private final int ticksDePreparo;
    private final double velocidadeBala;
    private final double raioBala;
    private final int tamanhoDaFonte;

    /** Ticks ate o proximo latido. */
    private int ateOLatido;

    /** > 0 = agachado, prestes a latir. So aviso visual. */
    private int preparando = 0;

    public CachorroEnemy(double y, boolean pelaEsquerda) {

        super(pelaEsquerda ? Main.CAMPO_X - 40 : Main.CAMPO_X + Main.CAMPO_W + 40,
              y,
              Config.getDouble("adriana.cachorros.hp", 22),
              Config.getDouble("adriana.cachorros.raio", 17));

        this.pelaEsquerda = pelaEsquerda;

        this.velocidadeDeEntrada = Config.getDouble("adriana.cachorros.velocidadeDeEntrada", 3.2);

        // Para em algum ponto do terco do campo do lado por onde entrou:
        // assim eles se espalham em vez de empilhar no meio.
        double margem = Config.getDouble("adriana.cachorros.margemDeParada", 70);
        double faixa = Main.CAMPO_W / 3.0;

        this.xDeParada = pelaEsquerda
                ? Main.CAMPO_X + margem + RNG.nextDouble() * faixa
                : Main.CAMPO_X + Main.CAMPO_W - margem - RNG.nextDouble() * faixa;

        this.cadencia       = Math.max(20, Config.getInt("adriana.cachorros.cadencia", 82));
        this.ticksDePreparo = Math.max(1, Config.getInt("adriana.cachorros.ticksDePreparo", 26));
        this.velocidadeBala = Config.getDouble("adriana.cachorros.velocidadeBala", 3.1);
        this.raioBala       = Config.getDouble("adriana.cachorros.raioBala", 9.0);
        this.tamanhoDaFonte = Math.max(8, Config.getInt("adriana.cachorros.tamanhoDaFonte", 22));

        this.sprite = Config.getString("adriana.cachorros.sprite", "sprites/enemies/cachorro.png");
        this.escalaSprite = Config.getDouble("adriana.cachorros.escalaSprite", 2.4);

        this.pontos = Config.getInt("adriana.cachorros.pontos", 300);
        this.itens  = Config.getInt("adriana.cachorros.itens", 2);

        // Cada um comeca com um atraso diferente, senao a matilha inteira
        // late no mesmo frame e o jogador nao tem como reagir a nada.
        this.ateOLatido = cadencia / 2 + RNG.nextInt(cadencia);
    }

    @Override
    public void tick() {

        mover();
        atirar();

        t++;
    }

    /**
     * Corre pra dentro do campo e para no xDeParada.
     *
     * Nao persegue o jogador de proposito: cachorro que corre atras
     * viraria o Claytonling de novo, e a graca deste ataque e ter varias
     * bocas fixas em lugares diferentes cobrando atencao dividida.
     */
    @Override
    protected void mover() {

        if (pelaEsquerda && x < xDeParada) {
            x += velocidadeDeEntrada;
        } else if (!pelaEsquerda && x > xDeParada) {
            x -= velocidadeDeEntrada;
        }
    }

    @Override
    protected void atirar() {

        // Ainda entrando: nao late. Atirar de fora da tela seria dano
        // vindo do nada.
        if (foraDoCampo()) {
            return;
        }

        if (preparando > 0) {

            preparando--;

            if (preparando == 0) {
                latir();
            }

            return;
        }

        ateOLatido--;

        if (ateOLatido <= 0) {
            preparando = ticksDePreparo;
            ateOLatido = cadencia;
        }
    }

    private boolean foraDoCampo() {
        return x < Main.CAMPO_X || x > Main.CAMPO_X + Main.CAMPO_W;
    }

    /** Cospe um trio de equacoes num leque mirado no jogador. */
    private void latir() {

        if (Main.player == null) {
            return;
        }

        Som.tocar(Som.CACHORRO_LATIDO);

        double base = Math.atan2(Main.player.getY() - y, Main.player.getX() - x);

        int quantas  = Math.max(1, Config.getInt("adriana.cachorros.equacoesPorLatido", 3));
        double abertura = Config.getDouble("adriana.cachorros.abertura", 0.42);

        for (int i = 0; i < quantas; i++) {

            double f = (quantas == 1) ? 0.5 : i / (double) (quantas - 1);
            double ang = base - abertura / 2 + abertura * f;

            String termo = EquacaoBullet.TERMOS[RNG.nextInt(EquacaoBullet.TERMOS.length)];

            Main.bullets.add(new EquacaoBullet(
                x, y,
                Math.cos(ang) * velocidadeBala,
                Math.sin(ang) * velocidadeBala,
                raioBala,
                termo,
                tamanhoDaFonte,
                new Color(255, 170, 190)
            ));
        }
    }

    /**
     * Desenha o cachorro espelhado conforme o lado de entrada, e
     * achatado enquanto se agacha pra latir.
     *
     * O agachamento e o aviso do tiro. Ele encolhe na vertical e cresce
     * um pouco na horizontal — a mesma deformacao que animacao 2D usa
     * pra dizer "esta juntando forca", e que o olho le sem legenda.
     */
    @Override
    public void render(Graphics2D g) {

        java.awt.image.BufferedImage img = src.Assets.get(sprite);

        double agacho = (preparando > 0)
                      ? 1 - 0.28 * Math.sin(Math.PI * (1 - preparando / (double) ticksDePreparo))
                      : 1;

        if (img == null) {
            g.setColor(new Color(180, 50, 60));
            g.fillOval((int) (x - radius), (int) (y - radius),
                       (int) (radius * 2), (int) (radius * 2));
        } else {

            int larg = (int) (radius * 2 * escalaSprite / agacho);
            int alt  = (int) (radius * 2 * escalaSprite * img.getHeight()
                              / (double) img.getWidth() * agacho);

            // Espelha quem entrou pela direita: cachorro andando de re
            // fica esquisito, e a correcao e so inverter a largura.
            int x0 = (int) (x - larg / 2.0);

            if (pelaEsquerda) {
                g.drawImage(img, x0, (int) (y - alt / 2.0), larg, alt, null);
            } else {
                g.drawImage(img, x0 + larg, (int) (y - alt / 2.0), -larg, alt, null);
            }
        }

        // Faisca no focinho enquanto prepara: reforca o aviso pra quem
        // esta olhando pro outro lado da tela.
        if (preparando > 0) {

            int r = 5 + (ticksDePreparo - preparando) / 4;

            g.setColor(new Color(255, 220, 140, 190));
            g.fillOval((int) (x - r), (int) (y - r), r * 2, r * 2);
        }

        renderBarraDeVida(g);

        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius),
                       (int) (radius * 2), (int) (radius * 2));
        }
    }
}
