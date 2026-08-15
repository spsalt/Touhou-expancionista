package src.enemyTypes;

import src.Config;
import src.enemyTypes.spellCards.CachorrosSpell;
import src.enemyTypes.spellCards.EsferaSpell;
import src.enemyTypes.spellCards.IntegralSpell;
import src.enemyTypes.spellCards.SomatorioSpell;
import src.enemyTypes.spellCards.SpellCard;

/**
 * ADRIANA — primeira chefe (Roteiro.txt linhas 19 a 38).
 *
 * A professora de calculo corrompida pelo virus. Aparece na frente da
 * sala 7 e ataca com o conteudo que ela mesma passou: integrais,
 * somatorios, area sob a curva e solidos de revolucao.
 *
 * A luta tem DUAS FORMAS, seguindo o roteiro:
 *
 *   FORMA BASE (linhas 20 a 28)
 *     - ∫ Integral Indefinida
 *     - Σ Somatório de Faltas
 *
 *   FORMA INTEGRAL MALIGNA (linhas 29 a 36, os "cachorros que sabem cálculo")
 *     - 🐕 Cachorros que Sabem Calculo
 *     - ∮ Sólido de Revolução  (o ataque em 3D)
 *
 * Cada forma e uma instancia separada, criada pelos metodos fabrica
 * abaixo. Quem controla a ordem e a fase (phases/phase1.java), que mostra
 * a cutscene de transformacao entre uma e outra.
 *
 * Toda a mecanica de chefe (spell cards, barra de HP, invulnerabilidade na
 * troca, anuncio do ataque) vem de BossEnemy — esta classe so escolhe
 * QUAIS ataques e QUAL sprite. Os padroes de bala moram em spellCards/.
 */
public class Adriana extends BossEnemy {

    private Adriana(SpellCard[] spellCards, String sprite, double escala) {
        super(spellCards, sprite, escala);
    }

    /**
     * Forma base: a Adriana ainda "normal", so corrompida.
     * Ataques com o basico de calculo — integral e somatorio.
     */
    public static Adriana criarFormaBase() {

        return new Adriana(
            new SpellCard[] {
                new IntegralSpell(),
                new SomatorioSpell()
            },
            "sprites/bosses/adriana-base.png",
            Config.getDouble("adriana.escalaSprite", 3.4)
        );
    }

    /**
     * Forma integral maligna: depois de "DOS CACHORROS QUE SABEM CÁLCULO!".
     * Os cachorros que sabem calculo (linha 31) e o solido de revolucao.
     */
    public static Adriana criarFormaMaligna() {

        return new Adriana(
            new SpellCard[] {
                new CachorrosSpell(),
                new EsferaSpell()
            },
            "sprites/bosses/adriana-integralmaligna.png",
            Config.getDouble("adriana.escalaSpriteMaligna", 3.8)
        );
    }
}
