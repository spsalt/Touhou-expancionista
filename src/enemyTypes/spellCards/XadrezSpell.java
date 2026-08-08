package src.enemyTypes.spellCards;

import java.awt.Color;
import java.util.Random;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.XadrezBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD 1 DO CLAYTON - "Xeque-Mate Sistematico"
 *
 * "Pelo jeito voce nao conhece o xadrez..." (Roteiro.txt linha 44)
 *
 * Solta um punhado de pecas de xadrez no campo. Cada uma anda em LANCES,
 * seguindo as regras da propria peca (ver XadrezBullet), parando entre um
 * movimento e outro.
 *
 * O desafio nao e a quantidade de balas — sao poucas — e sim ANTECIPAR:
 * cada peca so pode ir pra certos lugares, entao da pra deduzir onde e
 * seguro ficar. Quem conhece xadrez le o tabuleiro; quem nao conhece
 * apanha, exatamente como o Clayton insinua na fala.
 */
public class XadrezSpell extends SpellCard {

    /** As pecas sao sorteadas desta lista, entao da pra pesar a mistura. */
    private static final XadrezBullet.Peca[] REPERTORIO = {
        XadrezBullet.Peca.TORRE,
        XadrezBullet.Peca.BISPO,
        XadrezBullet.Peca.CAVALO,
        XadrezBullet.Peca.TORRE,
        XadrezBullet.Peca.BISPO,
        XadrezBullet.Peca.RAINHA
    };

    private final int cadencia;
    private final int pecasPorLevada;
    private final double casa;
    private final double velocidade;
    private final int pausaEntreLances;
    private final int lancesPorPeca;
    private final double raio;

    private final Random rng = new Random();

    private int disparos = 0;

    public XadrezSpell() {

        super("♞  Xeque-Mate Sistemático",
              Config.getDouble("clayton.xadrez.hp", 300),
              Config.getInt("clayton.xadrez.duracao", 1900));

        this.cadencia         = Math.max(1, Config.getInt("clayton.xadrez.cadencia", 150));
        this.pecasPorLevada   = Math.max(1, Config.getInt("clayton.xadrez.pecasPorLevada", 5));
        this.casa             = Config.getDouble("clayton.xadrez.tamanhoDaCasa", 72);
        this.velocidade       = Config.getDouble("clayton.xadrez.velocidade", 4.2);
        this.pausaEntreLances = Config.getInt("clayton.xadrez.pausaEntreLances", 40);
        this.lancesPorPeca    = Config.getInt("clayton.xadrez.lancesPorPeca", 7);
        this.raio             = Config.getDouble("clayton.xadrez.raio", 11);
    }

    @Override
    public void iniciar(BossEnemy chefe) {
        disparos = 0;
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (t % cadencia != 0) {
            return;
        }

        Som.tocar(Som.TIRO_INIMIGO);

        for (int i = 0; i < pecasPorLevada; i++) {

            // Nascem espalhadas na largura, um pouco abaixo do chefe.
            double x = Main.CAMPO_X + Main.CAMPO_W * (i + 1.0) / (pecasPorLevada + 1.0);
            // Bem abaixo do chefe: nascendo colado nele, o sprite grande
            // escondia as pecas e o jogador nem via o lance comecar.
            double y = chefe.getY() + 110;

            XadrezBullet.Peca peca = REPERTORIO[rng.nextInt(REPERTORIO.length)];

            // Cada peca leva sua propria semente: assim o sorteio de lances
            // dela nao depende da ordem em que as outras foram criadas.
            Main.bullets.add(new XadrezBullet(
                x, y, peca, casa, velocidade, pausaEntreLances, lancesPorPeca,
                raio, corDa(peca), rng.nextLong()
            ));
        }

        disparos++;
    }

    /** Cor por tipo de peca: ajuda a lembrar como cada uma se move. */
    private Color corDa(XadrezBullet.Peca peca) {

        switch (peca) {
            case TORRE:  return new Color(255, 150, 90);
            case BISPO:  return new Color(150, 220, 255);
            case CAVALO: return new Color(200, 160, 255);
            default:     return new Color(255, 220, 120);   // RAINHA
        }
    }
}
