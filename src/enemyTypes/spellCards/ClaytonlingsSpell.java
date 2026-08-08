package src.enemyTypes.spellCards;

import src.Config;
import src.Main;
import src.Som;
import src.enemyTypes.BossEnemy;
import src.enemyTypes.Claytonling;

/**
 * SPELL CARD 2 DO CLAYTON - "Claytonlings"
 *
 * Invoca copias pequenas do Clayton que perseguem o jogador. A graca e
 * que eles sao COVARDES: se voce consegue desviar, eles perdem a coragem
 * e correm pra fora da tela (a logica de "fui desviado" esta em
 * Claytonling).
 *
 * Isso inverte o instinto do jogador. Nos outros padroes voce foge das
 * balas; aqui vale a pena deixar eles chegarem perto pra depois cortar
 * pro lado — o desvio bem feito limpa a tela sozinho. Quem entra em panico
 * e fica correndo longe mantem todos eles em perseguicao.
 *
 * O chefe tambem NAO spawna todos de uma vez: eles vem em levas, senao
 * um unico desvio bem dado resolveria a spell card inteira.
 */
public class ClaytonlingsSpell extends SpellCard {

    private final int cadencia;
    private final int porLevada;
    private final int limiteNaTela;

    public ClaytonlingsSpell() {

        super("Claytonlings",
              Config.getDouble("clayton.claytonlings.hp", 340),
              Config.getInt("clayton.claytonlings.duracao", 2000));

        this.cadencia     = Math.max(1, Config.getInt("clayton.claytonlings.cadencia", 110));
        this.porLevada    = Math.max(1, Config.getInt("clayton.claytonlings.porLevada", 3));
        this.limiteNaTela = Math.max(1, Config.getInt("clayton.claytonlings.limiteNaTela", 12));
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (t % cadencia != 0) {
            return;
        }

        // Teto de seguranca: se o jogador nao estiver desviando, os
        // claytonlings acumulam e a tela vira uma parede de minions.
        if (contarClaytonlings() >= limiteNaTela) {
            return;
        }

        Som.tocar(Som.TIRO_INIMIGO);

        for (int i = 0; i < porLevada; i++) {

            // Saem espalhados na largura, na altura do chefe.
            double x = Main.CAMPO_X + Main.CAMPO_W * (i + 1.0) / (porLevada + 1.0);
            double y = chefe.getY() + 50;

            Main.enemies.add(new Claytonling(x, y));
        }
    }

    /** Quantos claytonlings ainda estao vivos no campo. */
    private int contarClaytonlings() {

        int total = 0;

        for (int i = 0; i < Main.enemies.size(); i++) {
            if (Main.enemies.get(i) instanceof Claytonling) {
                total++;
            }
        }

        return total;
    }
}
