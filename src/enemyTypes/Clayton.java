package src.enemyTypes;

import java.awt.Graphics2D;

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
 * A UNICA diferenca de estrutura: o LatexSpell precisa DESENHAR (a pagina
 * que desce e o ataque em si, nao um monte de bala). Spell card comum nao
 * desenha nada, entao o render() aqui repassa o desenho pro spell ativo
 * quando ele for o do LaTeX.
 */
public class Clayton extends BossEnemy {

    private Clayton(SpellCard[] spellCards, String sprite, double escala) {
        super(spellCards, sprite, escala);
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

    /**
     * Desenha o chefe e, se o ataque ativo for o do LaTeX, a pagina dele.
     *
     * A pagina vai ANTES do sprite pra o Clayton nao ficar escondido atras
     * do proprio texto — o jogador precisa ver onde ele esta pra mirar.
     */
    @Override
    public void render(Graphics2D g) {

        SpellCard atual = getSpellCardAtual();

        if (atual instanceof LatexSpell) {
            ((LatexSpell) atual).render(g);
        }

        super.render(g);
    }
}
