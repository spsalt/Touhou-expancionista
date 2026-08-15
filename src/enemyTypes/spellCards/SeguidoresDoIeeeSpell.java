package src.enemyTypes.spellCards;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Random;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.IntegralBullet;
import src.bulletTypes.SeguidorBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD - "⬖ Seguidores do IEEE"
 *
 * O primeiro ataque do PAPA IA, logo depois da transformacao e antes do
 * Optimum Path Forest.
 *
 * DUAS COISAS ACONTECENDO AO MESMO TEMPO
 * --------------------------------------
 * 1. O CORREDOR. Duas paredes densas de bala descem pela tela deixando um
 *    caminho aberto no meio — o mar se abrindo. O corredor SERPENTEIA
 *    devagar de um lado pro outro, entao ficar parado nele nao funciona:
 *    o jogador tem que acompanhar a abertura.
 *
 * 2. OS LEQUES. Das duas laterais saem leques de simbolos do IEEE, retos,
 *    na direcao do jogador — varias balas abrindo de um mesmo ponto, como
 *    um viseque.
 *
 * POR QUE OS DOIS JUNTOS
 * ----------------------
 * Sozinho, o corredor e um teste de acompanhamento: bonito, mas resolvido
 * uma vez e resolvido pra sempre. Sozinhos, os leques sao um teste de
 * reacao lateral. Juntos eles brigam entre si — a resposta certa pro leque
 * (andar pro lado) e exatamente o que tira voce do corredor, e a resposta
 * certa pro corredor (seguir a abertura) e o que te deixa parado na frente
 * do leque.
 *
 * E o unico ataque do jogo em que as duas ameacas pedem coisas OPOSTAS ao
 * mesmo tempo, e nao alternadas. Por isso ele e curto: cansaria rapido se
 * durasse o que os outros duram.
 *
 * O corredor NAO fecha nunca abaixo de uma largura minima. Um corredor que
 * afina indefinidamente vira morte por chegada, e nao por erro.
 */
public class SeguidoresDoIeeeSpell extends SpellCard {

    /* --- o corredor --- */

    private final int cadenciaDoCorredor;
    private final double velocidadeDoCorredor;
    private final double espacamentoNaParede;
    private final double larguraDoCorredor;
    private final double amplitudeDoSerpenteio;
    private final double velocidadeDoSerpenteio;
    private final double raioDaParede;

    /* --- os leques laterais --- */

    private final int cadenciaDoLeque;
    private final int balasPorLeque;
    private final double aberturaDoLeque;
    private final double velocidadeDoLeque;
    private final double raioDoSimbolo;

    /** Angulo do serpenteio. Anda sozinho e nao depende do 't' do spell. */
    private double faseDoCorredor = 0;

    /** Ticks restantes mostrando o nome dos seguidores. */
    private int anuncio = 0;

    private Random rng;

    public SeguidoresDoIeeeSpell() {

        super("⬖  Seguidores do IEEE",
              Config.getDouble("papa.ieee.hp", 620),
              Config.getInt("papa.ieee.duracao", 1900));

        this.cadenciaDoCorredor     = Math.max(2, Config.getInt("papa.ieee.cadenciaDoCorredor", 7));
        this.velocidadeDoCorredor   = Config.getDouble("papa.ieee.velocidadeDoCorredor", 2.5);
        this.espacamentoNaParede    = Config.getDouble("papa.ieee.espacamentoNaParede", 26);
        this.larguraDoCorredor      = Config.getDouble("papa.ieee.larguraDoCorredor", 130);
        this.amplitudeDoSerpenteio  = Config.getDouble("papa.ieee.amplitudeDoSerpenteio", 120);
        this.velocidadeDoSerpenteio = Config.getDouble("papa.ieee.velocidadeDoSerpenteio", 0.012);
        this.raioDaParede           = Config.getDouble("papa.ieee.raioDaParede", 8);

        this.cadenciaDoLeque   = Math.max(20, Config.getInt("papa.ieee.cadenciaDoLeque", 78));
        this.balasPorLeque     = Math.max(2, Config.getInt("papa.ieee.balasPorLeque", 7));
        this.aberturaDoLeque   = Config.getDouble("papa.ieee.aberturaDoLeque", 0.62);
        this.velocidadeDoLeque = Config.getDouble("papa.ieee.velocidadeDoLeque", 3.4);
        this.raioDoSimbolo     = Config.getDouble("papa.ieee.raioDoSimbolo", 11);
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        long seed = Config.getInt("papa.ieee.seed", -1);
        rng = (seed < 0) ? new Random() : new Random(seed);

        faseDoCorredor = rng.nextDouble() * Math.PI * 2;
        anuncio = Config.getInt("papa.ieee.ticksDoAnuncio", 140);
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (anuncio > 0) {
            anuncio--;
        }

        faseDoCorredor += velocidadeDoSerpenteio;

        if (t % cadenciaDoCorredor == 0) {
            soltarFatiaDoCorredor();
        }

        if (t % cadenciaDoLeque == 0 && t > 0) {
            soltarLeques();
        }
    }

    /**
     * Uma "fatia" do corredor: uma linha horizontal de balas com um buraco.
     *
     * O corredor inteiro e feito de muitas fatias soltas em sequencia,
     * cada uma com o buraco um pouquinho deslocado. Como todas descem na
     * mesma velocidade, o que aparece na tela e uma parede continua com um
     * caminho serpenteando por dentro — a mesma tecnica de uma cortina de
     * agua feita de gotas.
     *
     * Fazer assim (e nao com objetos "parede") tem uma vantagem concreta:
     * cada bala e uma bala normal, entao a bomba limpa, o graze conta e o
     * corredor herda tudo que o resto do jogo ja sabe fazer.
     */
    private void soltarFatiaDoCorredor() {

        double centro = Main.CAMPO_X + Main.CAMPO_W / 2.0
                      + Math.sin(faseDoCorredor) * amplitudeDoSerpenteio;

        double meia = larguraDoCorredor / 2;

        // Prende o corredor dentro do campo. Sem isso o serpenteio empurra
        // a abertura pra fora da tela e o padrao vira uma parede solida
        // que nao da pra atravessar.
        centro = Math.max(Main.CAMPO_X + meia + 30,
                 Math.min(Main.CAMPO_X + Main.CAMPO_W - meia - 30, centro));

        for (double px = Main.CAMPO_X + 8;
             px <= Main.CAMPO_X + Main.CAMPO_W - 8;
             px += espacamentoNaParede) {

            if (Math.abs(px - centro) < meia) {
                continue;
            }

            // AS PAREDES SAO OS PROPRIOS CULTISTAS, DESCENDO A PE.
            //
            // Cada um tem ciclo de caminhada, gingado e fase propria (ver
            // SeguidorBullet). Nao e enfeite: uma parede de balas iguais o
            // olho le como textura, uma multidao de gente andando o olho
            // conta um por um — e o corredor deixa de ser "um vao" pra ser
            // "uma passagem entre duas multidoes".
            //
            // O leque, por contraste, sao os SIMBOLOS do IEEE. Duas artes
            // diferentes pras duas metades do ataque e o que faz dar pra
            // saber, num relance, o que desce e o que vem de lado.
            // O LADO PRA ONDE ELE ANDA sai de onde ele nasceu em relacao
            // ao corredor: quem nasce a esquerda anda pra esquerda, quem
            // nasce a direita anda pra direita. E as duas fileiras se
            // afastando que ABREM o caminho, em vez de o caminho ser um
            // buraco que ja veio pronto.
            Main.bullets.add(new SeguidorBullet(
                px, Main.CAMPO_Y - 6,
                velocidadeDoCorredor,
                raioDaParede,
                (px < centro) ? -1 : 1
            ));
        }
    }

    /**
     * Um leque de simbolos do IEEE saindo de CADA lateral, mirado no
     * jogador.
     *
     * Sai reto — sem aceleracao, sem curva. O leque ja e a dificuldade: e
     * um bloco de balas abrindo em arco, e o jogador tem que achar o vao
     * entre duas delas em vez de simplesmente sair da linha de UMA.
     *
     * A altura de nascimento e sorteada dentro de uma faixa, e nao fixa:
     * com altura fixa o jogador aprendia a ficar sempre abaixo dela e o
     * ataque inteiro deixava de existir.
     */
    private void soltarLeques() {

        if (Main.player == null) {
            return;
        }

        Som.tocar(Som.PAPA_AVANCA);

        double topo  = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("papa.ieee.faixaTopoRelY", 0.15);
        double baixo = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("papa.ieee.faixaBaixoRelY", 0.55);

        for (int lado = -1; lado <= 1; lado += 2) {

            double ox = (lado < 0) ? Main.CAMPO_X + 6
                                   : Main.CAMPO_X + Main.CAMPO_W - 6;

            double oy = topo + rng.nextDouble() * (baixo - topo);

            double base = Math.atan2(Main.player.getY() - oy, Main.player.getX() - ox);

            for (int i = 0; i < balasPorLeque; i++) {

                double f = i / (double) (balasPorLeque - 1);
                double ang = base - aberturaDoLeque / 2 + aberturaDoLeque * f;

                IntegralBullet b = new IntegralBullet(
                    ox, oy,
                    Math.cos(ang) * velocidadeDoLeque,
                    Math.sin(ang) * velocidadeDoLeque,
                    0, 0,
                    raioDoSimbolo,
                    true,
                    new Color(235, 245, 255)
                );

                b.setSprite(Config.getString("papa.ieee.sprite", "sprites/GFX/ieee.png"));

                Main.bullets.add(b);
            }
        }
    }

    /** O nome dos seguidores, no rodape, no comeco do ataque. */
    @Override
    public void render(Graphics2D g) {

        if (anuncio <= 0) {
            return;
        }

        int alpha = Math.min(255, anuncio * 3);

        g.setFont(new Font("Monospaced", Font.BOLD, 15));

        String texto = "OS SEGUIDORES ABREM O CAMINHO";
        int larg = g.getFontMetrics().stringWidth(texto);

        int cx = Main.CAMPO_X + Main.CAMPO_W / 2 - larg / 2;
        int cy = Main.CAMPO_Y + Main.CAMPO_H - 18;

        g.setColor(new Color(0, 0, 0, alpha));
        g.drawString(texto, cx + 1, cy + 1);

        g.setColor(new Color(150, 200, 255, alpha));
        g.drawString(texto, cx, cy);
    }
}
