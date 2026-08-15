package src.enemyTypes.spellCards;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Random;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.EquacaoBullet;
import src.enemyTypes.BossEnemy;
import src.enemyTypes.CachorroEnemy;

/**
 * SPELL CARD - "🐕 Cachorros que Sabem Cálculo"
 *
 * O ataque que o roteiro pedia desde sempre (Roteiro.txt linhas 30 a 32):
 *
 *   "## cachorros.png aparecem, mas vermelho
 *    adriana: DOS CACHORROS QUE SABEM CÁLCULO! DERIVEM ELE ATÉ O 0!
 *    ## cachorros.png finalmente ganham forma"
 *
 * A Adriana invoca uma matilha. Cada cachorro entra correndo por uma
 * lateral, para numa altura diferente e passa a latir EQUACOES miradas no
 * jogador (ver CachorroEnemy e EquacaoBullet).
 *
 * O QUE ESTE ATAQUE COBRA, E QUE NENHUM OUTRO DELA COBRAVA
 * --------------------------------------------------------
 * A Integral e o Somatorio cobram leitura de padrao; o Solido de
 * Revolucao cobra profundidade. Faltava ATENCAO DIVIDIDA: aqui as balas
 * nascem em cinco lugares diferentes da tela, e nenhum deles e a chefe.
 * O jogador tem que escolher onde olhar, e principalmente EM QUEM ATIRAR
 * primeiro — cachorro morre rapido, entao reduzir o numero de bocas e a
 * jogada, mas cada segundo mirando num cachorro e um segundo sem dano na
 * Adriana, e o cronometro do spell card nao para.
 *
 * A propria Adriana quase nao atira durante isso: ela solta so um anel
 * esparso de vez em quando, pra nao deixar o centro do campo virar area
 * de descanso enquanto o jogador limpa a matilha.
 *
 * A MATILHA E REPOSTA: quando sobram poucos cachorros, ela invoca mais.
 * Sem isso, um jogador com bom dano zerava a matilha e o ataque virava
 * uma sala vazia ate o tempo acabar.
 */
public class CachorrosSpell extends SpellCard {

    /** Quantos cachorros ela tenta manter vivos ao mesmo tempo. */
    private final int matilhaAlvo;

    /** Ticks entre uma checagem de reposicao e a proxima. */
    private final int intervaloDeInvocacao;

    /** Quantos ela repoe de cada vez. */
    private final int porInvocacao;

    /* --- o anel esparso da propria Adriana --- */

    private final int cadenciaDoAnel;
    private final int balasDoAnel;
    private final double velocidadeDoAnel;
    private final double raioDoAnel;

    /** Ticks restantes mostrando o grito da invocacao. */
    private int anuncioDoLatido = 0;

    private Random rng;

    public CachorrosSpell() {

        super("🐕  Cachorros que Sabem Cálculo",
              Config.getDouble("adriana.cachorros.hpDoSpell", 360),
              Config.getInt("adriana.cachorros.duracao", 1800));

        this.matilhaAlvo          = Math.max(1, Config.getInt("adriana.cachorros.matilha", 5));
        this.intervaloDeInvocacao = Math.max(20, Config.getInt("adriana.cachorros.intervaloDeInvocacao", 120));
        this.porInvocacao         = Math.max(1, Config.getInt("adriana.cachorros.porInvocacao", 2));

        this.cadenciaDoAnel   = Math.max(20, Config.getInt("adriana.cachorros.cadenciaDoAnel", 96));
        this.balasDoAnel      = Math.max(1, Config.getInt("adriana.cachorros.balasDoAnel", 10));
        this.velocidadeDoAnel = Config.getDouble("adriana.cachorros.velocidadeDoAnel", 2.0);
        this.raioDoAnel       = Config.getDouble("adriana.cachorros.raioDoAnel", 8.0);
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        long seed = Config.getInt("adriana.cachorros.seed", -1);
        rng = (seed < 0) ? new Random() : new Random(seed);

        anuncioDoLatido = Config.getInt("adriana.cachorros.ticksDoGrito", 150);

        // A primeira matilha inteira sai de uma vez, no grito. E o momento
        // que o roteiro descreve: "cachorros.png finalmente ganham forma".
        invocar(matilhaAlvo);
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (anuncioDoLatido > 0) {
            anuncioDoLatido--;
        }

        // Repoe a matilha se ela minguou.
        if (t % intervaloDeInvocacao == 0 && t > 0) {

            int faltam = matilhaAlvo - contarCachorros();

            if (faltam > 0) {
                invocar(Math.min(porInvocacao, faltam));
            }
        }

        // O anel esparso da propria Adriana.
        if (t % cadenciaDoAnel == 0) {
            anelDaAdriana(chefe);
        }
    }

    @Override
    public void encerrar(BossEnemy chefe) {

        // A matilha morre junto com o spell card. Deixar cachorro vivo
        // depois que o ataque acabou faria o proximo padrao comecar com
        // dano vindo de fora dele — o mesmo problema que os claytonlings
        // do Clayton ja deram uma vez.
        for (int i = 0; i < Main.enemies.size(); i++) {

            if (Main.enemies.get(i) instanceof CachorroEnemy) {
                Main.enemies.get(i).setAlive(false);
            }
        }
    }

    /** Quantos cachorros ainda estao vivos na tela. */
    private int contarCachorros() {

        int n = 0;

        for (int i = 0; i < Main.enemies.size(); i++) {
            if (Main.enemies.get(i) instanceof CachorroEnemy) {
                n++;
            }
        }

        return n;
    }

    /**
     * Solta N cachorros, alternando o lado de entrada.
     *
     * Alternar (e nao sortear) garante que nunca saiam cinco pela mesma
     * lateral — o que deixaria metade da tela vazia e a outra metade
     * impossivel.
     */
    private void invocar(int quantos) {

        Som.tocar(Som.CACHORRO_UIVO);

        double topo  = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("adriana.cachorros.faixaTopoRelY", 0.30);
        double baixo = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("adriana.cachorros.faixaBaixoRelY", 0.62);

        for (int i = 0; i < quantos; i++) {

            boolean esquerda = (contarCachorros() + i) % 2 == 0;

            double y = topo + rng.nextDouble() * (baixo - topo);

            Main.enemies.add(new CachorroEnemy(y, esquerda));
        }
    }

    /**
     * Um anel largo e lento saindo da Adriana.
     *
     * Existe so pra o centro do campo nao virar zona segura enquanto o
     * jogador caça cachorro. E propositalmente esparso: se este anel
     * exigisse atencao de verdade, o ataque teria duas coisas dificeis ao
     * mesmo tempo e nenhuma delas seria legivel.
     */
    private void anelDaAdriana(BossEnemy chefe) {

        Som.tocar(Som.ADRIANA_GLIFO);

        for (int i = 0; i < balasDoAnel; i++) {

            double ang = 2 * Math.PI * i / balasDoAnel + rng.nextDouble() * 0.2;

            String termo = EquacaoBullet.TERMOS[rng.nextInt(EquacaoBullet.TERMOS.length)];

            Main.bullets.add(new EquacaoBullet(
                chefe.getX(), chefe.getY() + 30,
                Math.cos(ang) * velocidadeDoAnel,
                Math.sin(ang) * velocidadeDoAnel,
                raioDoAnel,
                termo,
                Config.getInt("adriana.cachorros.tamanhoDaFonteDoAnel", 18),
                new Color(230, 140, 255)
            ));
        }
    }

    /** O grito da invocacao, no comeco do ataque. */
    @Override
    public void render(Graphics2D g) {

        if (anuncioDoLatido <= 0) {
            return;
        }

        int alpha = Math.min(255, anuncioDoLatido * 3);

        g.setFont(new Font("Monospaced", Font.BOLD, 17));

        String texto = "DERIVEM ELE ATÉ O 0!";
        int larg = g.getFontMetrics().stringWidth(texto);

        int cx = Main.CAMPO_X + Main.CAMPO_W / 2 - larg / 2;
        int cy = Main.CAMPO_Y + Main.CAMPO_H / 3 + 70;

        g.setColor(new Color(0, 0, 0, alpha));
        g.drawString(texto, cx + 2, cy + 2);

        g.setColor(new Color(255, 150, 170, alpha));
        g.drawString(texto, cx, cy);
    }
}
