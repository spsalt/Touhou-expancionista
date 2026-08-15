package src.enemyTypes;

import src.Config;
import src.enemyTypes.spellCards.BandeirasSpell;
import src.enemyTypes.spellCards.OptimumPathForestSpell;
import src.enemyTypes.spellCards.RedRecognaSpell;
import src.enemyTypes.spellCards.SeguidoresDoIeeeSpell;
import src.enemyTypes.spellCards.SpellCard;
import src.enemyTypes.spellCards.TuringSpell;

/**
 * PAPA — chefe final (Roteiro.txt linhas 58 a 82).
 *
 * O professor que "estava lendo uns disquetes e depois nao lembra de mais
 * nada" (linha 75). Ele nao e o vilao: e o hospedeiro. O virus fala pela
 * boca dele ate ser expulso.
 *
 * A luta tem DUAS FORMAS, como as dos outros chefes:
 *
 *   FORMA BASE (linhas 58 a 68)
 *     - ⚑ Tratado de Nao-Proliferacao  (bandeiras de paises sorteados)
 *     - ⊢ Maquina de Turing            (o jogador vira o cabecote da fita)
 *
 *   FORMA PAPA IA (linhas 69 a 72, depois da tela ficar branca) — o
 *   PAPA com a infeccao NO MAXIMO, nao o virus fora dele
 *     - ⊛ Optimum Path Forest          (o classificador do proprio Papa)
 *
 * A escolha dos ataques segue o roteiro de perto. O primeiro vem da fala
 * "ja sei onde proliferaremos nosso virus depois de te derrotar" (linha
 * 66) — cada bandeira e um pais da lista. Os outros dois vem da area do
 * professor: computacao teorica na forma humana, e o OPF (o classificador
 * de floresta de caminhos otimos que ele ajudou a criar) quando a IA
 * assume e passa a atacar com o proprio artigo.
 *
 * Igual a Adriana e ao Clayton, toda a mecanica de chefe vem de
 * BossEnemy; esta classe so escolhe QUAIS spell cards e QUAL sprite.
 */
public class Papa extends BossEnemy {

    private Papa(SpellCard[] spellCards, String sprite, double escala) {
        super(spellCards, sprite, escala);
    }

    /**
     * Forma base: o PAPA ainda de carne e osso, com o virus falando por
     * ele. Bandeiras e maquina de Turing.
     */
    public static Papa criarFormaBase() {

        return new Papa(
            new SpellCard[] {
                new BandeirasSpell(),
                new TuringSpell()
            },
            Config.getString("papa.sprite", "sprites/bosses/papa-base.png"),
            Config.getDouble("papa.escalaSprite", 3.4)
        );
    }

    /**
     * Forma PAPA IA: depois de "NAOOOOO * tela toda fica branca *"
     * (Roteiro.txt linha 69), o virus toma o PAPA POR INTEIRO.
     *
     * E a infeccao no maximo, e nao o virus abandonando o hospedeiro —
     * por isso e a forma mais forte da luta e nao um resto dela. O virus
     * so e expulso quando ESTA forma cai (linhas 72 a 75).
     *
     * Um unico spell card, o mais pesado do jogo.
     */
    public static Papa criarFormaIA() {

        return new Papa(
            new SpellCard[] {
                new SeguidoresDoIeeeSpell(),
                new RedRecognaSpell(),
                new OptimumPathForestSpell()
            },
            Config.getString("papa.spriteIA", "sprites/bosses/papa-IA_MALIGNA.png"),
            Config.getDouble("papa.escalaSpriteIA", 4.0)
        );
    }
}
