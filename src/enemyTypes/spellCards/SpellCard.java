package src.enemyTypes.spellCards;

import java.awt.Graphics2D;

import src.enemyTypes.BossEnemy;

/**
 * Um "spell card": um ataque nomeado de chefe, com HP e tempo proprios.
 *
 * E o padrao Strategy — cada padrao de bala vira uma classe separada, e o
 * chefe so troca de estrategia quando uma acaba. Isso quer dizer que:
 *
 *   - pra criar um ataque novo, voce escreve UMA classe e nao mexe em
 *     mais nada; o BossEnemy nem precisa saber que ela existe
 *   - pra mudar a ordem dos ataques, voce reordena um array
 *   - cada padrao pode ser testado/ajustado isolado dos outros
 *
 * O contrato pra quem herda e so o atacar(): ele recebe o tempo decorrido
 * DESTE spell card (zerado a cada troca) e o chefe que esta atacando, pra
 * poder ler a posicao dele.
 */
public abstract class SpellCard {

    /** Nome exibido na tela quando o ataque comeca. */
    protected final String nome;

    /** Quanto de HP o chefe tem enquanto este ataque roda. */
    protected final double hp;

    /** Tempo limite em ticks. Se estourar, o ataque passa mesmo sem matar. */
    protected final int duracao;

    protected SpellCard(String nome, double hp, int duracao) {
        this.nome = nome;
        this.hp = hp;
        this.duracao = Math.max(1, duracao);
    }

    /**
     * Chamado uma vez por tick enquanto este spell card estiver ativo.
     *
     * @param t     ticks desde que ESTE spell card comecou (comeca em 0)
     * @param chefe quem esta atacando — use getX()/getY() pra origem das balas
     */
    public abstract void atacar(int t, BossEnemy chefe);

    /**
     * Chamado uma vez quando o spell card comeca. Sobrescreva se o ataque
     * precisar sortear algo ou zerar estado interno antes de rodar.
     */
    public void iniciar(BossEnemy chefe) {
    }

    /**
     * Chamado uma vez quando o spell card ACABA — por HP zerado, por
     * tempo esgotado ou porque a chefe morreu.
     *
     * Vazio por padrao. Existe pra ataque que mexe em estado de FORA de
     * si mesmo poder desfazer o que fez: a maquina de Turing do PAPA
     * trava o movimento do jogador, e sem este gancho um ataque
     * interrompido no meio deixaria ele preso pro resto da luta.
     */
    public void encerrar(BossEnemy chefe) {
    }

    /**
     * Desenho proprio do ataque, chamado pelo BossEnemy ANTES do sprite
     * da chefe. Vazio por padrao — o ataque comum nao desenha nada, quem
     * aparece na tela sao as balas que ele criou.
     *
     * Existe porque alguns ataques nao sao feitos de bala: o LaTeX do
     * Clayton desenha uma pagina, a maquina de Turing do PAPA desenha uma
     * fita. Sem este gancho, cada chefe desses precisaria de um
     * 'instanceof' no proprio render() — que e exatamente o if por tipo
     * que o padrao Strategy existe pra evitar.
     */
    public void render(Graphics2D g) {
    }

    /* =========================
            GETTERS
       ========================= */

    public String getNome() {
        return nome;
    }

    public double getHp() {
        return hp;
    }

    public int getDuracao() {
        return duracao;
    }
}
