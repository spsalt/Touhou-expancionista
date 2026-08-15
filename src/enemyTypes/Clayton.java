package src.enemyTypes;

import src.Config;
import src.enemyTypes.spellCards.ClaytonlingsSpell;
import src.enemyTypes.spellCards.LatexSpell;
import src.enemyTypes.spellCards.SpellCard;
import src.enemyTypes.spellCards.XadrezSpell;

/**
 * CLAYTON — segundo chefe (Roteiro.txt linhas 42 a 56).
 *
 * O professor sistematico. Ataca com as tres coisas que ele nao para de
 * falar: xadrez, os proprios clones e LaTeX.
 *
 *   FORMA BASE (linhas 42 a 50)
 *     - ♞ Xeque-Mate Sistematico   (pecas de xadrez andando em lances)
 *     - Claytonlings               (minions covardes que fogem se voce desvia)
 *
 *   FORMA TAB MALIGNO (linhas 51 a 56)
 *     - LATEX                      (o artigo que toma a tela)
 *
 * Igual a Adriana, toda a mecanica de chefe vem de BossEnemy; esta classe
 * so escolhe QUAIS spell cards e QUAL sprite.
 *
 * O LatexSpell precisa DESENHAR (a pagina que desce e o ataque em si,
 * nao um monte de bala). Isso nao aparece aqui: quem chama o render() do
 * ataque e o BossEnemy, pra todo chefe igual.
 */
public class Clayton extends BossEnemy {

    private Clayton(SpellCard[] spellCards, String sprite, double escala) {

        super(spellCards, sprite, escala);

        // Ele fica magro demais no enquadramento padrao: a foto e mais
        // alta que larga, e como a caixa e quadrada sobra altura e falta
        // corpo. Estica so a horizontal — a altura e a colisao ficam
        // como estao.
        this.escalaLargura = Config.getDouble("clayton.escalaLargura", 1.35);
    }

    /**
     * Forma base: xadrez e claytonlings.
     * "Eu? Sou sistematico. Esse e meu jeito." (Roteiro.txt linha 44)
     */
    public static Clayton criarFormaBase() {

        return new Clayton(
            new SpellCard[] {
                new XadrezSpell(),
                new ClaytonlingsSpell()
            },
            Config.getString("clayton.sprite", "sprites/bosses/clayton-base.png"),
            Config.getDouble("clayton.escalaSprite", 3.4)
        );
    }

    /**
     * Forma Tab maligno: o ataque final do LaTeX.
     * "Clayton Tab maligno aparece" (Roteiro.txt linha 51)
     */
    public static Clayton criarFormaMaligna() {

        return new Clayton(
            new SpellCard[] {
                new LatexSpell()
            },
            Config.getString("clayton.spriteMaligno", "sprites/bosses/Clayton-Maligno.png"),
            Config.getDouble("clayton.escalaSpriteMaligna", 3.8)
        );
    }
}
