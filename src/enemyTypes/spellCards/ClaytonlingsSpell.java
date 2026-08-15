package src.enemyTypes.spellCards;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.BolaBullet;
import src.enemyTypes.AnticodigoEnemy;
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
 * POR QUE ELES DIMINUIRAM, E O QUE ENTROU NO LUGAR
 * ------------------------------------------------
 * Com muitos claytonlings na tela o ataque tinha UMA resposta certa
 * (correr sem parar) e ela ia ficando menos possivel a cada leva — nao
 * mais dificil, menos possivel, que e outra coisa. A quantidade caiu pela
 * metade e no lugar entraram as CRIATURAS DO ANTICODIGO, que ficam
 * paradas nas laterais chutando bolas de futebol em arco.
 *
 * As duas ameacas pedem posturas OPOSTAS: o claytonling premia quem corre,
 * a bola que cai de cima pune quem corre em linha reta sem olhar pro alto.
 * O ataque deixou de ser um teste de resistencia e virou um de alternar
 * entre duas leituras — que e o que um spell card devia cobrar.
 */
public class ClaytonlingsSpell extends SpellCard {

    private final int cadencia;
    private final int porLevada;
    private final int limiteNaTela;

    /* --- as criaturas do anticodigo --- */

    private final int cadenciaDasCriaturas;
    private final int limiteDeCriaturas;

    /** De quantos em quantos claytonlings sai um chutador de bola. */
    private final int umChutadorACada;

    /** Alterna o lado de nascimento: nunca as duas na mesma parede. */
    private boolean proximaPelaEsquerda = true;

    public ClaytonlingsSpell() {

        super("Claytonlings",
              Config.getDouble("clayton.claytonlings.hp", 340),
              Config.getInt("clayton.claytonlings.duracao", 2000));

        this.cadencia     = Math.max(1, Config.getInt("clayton.claytonlings.cadencia", 110));
        this.porLevada    = Math.max(1, Config.getInt("clayton.claytonlings.porLevada", 3));
        this.limiteNaTela = Math.max(1, Config.getInt("clayton.claytonlings.limiteNaTela", 12));

        this.cadenciaDasCriaturas = Math.max(30, Config.getInt("clayton.anticodigo.cadencia", 260));
        this.limiteDeCriaturas    = Math.max(1, Config.getInt("clayton.anticodigo.limiteNaTela", 2));
        this.umChutadorACada      = Math.max(1, Config.getInt("clayton.claytonling.umChutadorACada", 2));
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        proximaPelaEsquerda = Math.random() < 0.5;

        // A primeira criatura entra JUNTO com o ataque, e nao depois da
        // primeira cadencia: ela e metade da ideia do spell card, e o
        // jogador precisa ver as duas ameacas desde o comeco pra entender
        // que tem que alternar entre elas.
        soltarCriatura();
    }

    @Override
    public void encerrar(BossEnemy chefe) {

        // As criaturas morrem junto com o ataque. Deixar uma viva faria o
        // proximo spell card comecar com bola caindo do nada — o mesmo
        // problema que os cachorros da Adriana ja deram uma vez.
        for (int i = 0; i < Main.enemies.size(); i++) {

            if (Main.enemies.get(i) instanceof AnticodigoEnemy) {
                Main.enemies.get(i).setAlive(false);
            }
        }
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (t % cadenciaDasCriaturas == 0 && t > 0) {
            soltarCriatura();
        }

        // OS CHUTOES, DO COMECO AO FIM DO ATAQUE.
        //
        // Comecou como um chutao unico fechando o spell card, mas ele
        // funciona melhor como uma BATIDA que atravessa o turno inteiro:
        // de tempos em tempos a tela abre um rasgo, e o jogador aprende a
        // esperar por ele. Um evento unico voce ve uma vez; um ritmo voce
        // incorpora na forma de jogar.
        int intervalo = Math.max(60, Config.getInt("clayton.bolaGigante.intervalo", 420));
        int primeiro  = Math.max(0, Config.getInt("clayton.bolaGigante.primeiroEm", 180));

        if (t >= primeiro && (t - primeiro) % intervalo == 0) {
            chutarBolaGigante(chefe);
        }

        if (t % cadencia != 0) {
            return;
        }

        // Teto de seguranca: se o jogador nao estiver desviando, os
        // claytonlings acumulam e a tela vira uma parede de minions.
        if (contarClaytonlings() >= limiteNaTela) {
            return;
        }

        Som.tocar(Som.CLAYTONLING);

        for (int i = 0; i < porLevada; i++) {

            // Saem espalhados na largura, na altura do chefe.
            double x = Main.CAMPO_X + Main.CAMPO_W * (i + 1.0) / (porLevada + 1.0);
            double y = chefe.getY() + 50;

            // UM A CADA N e chutador. Fixo por posicao e nao sorteado: o
            // jogador consegue aprender "o do meio chuta" em vez de ter
            // que redescobrir a cada leva quem faz o que.
            boolean chutador = (i % Math.max(1, umChutadorACada)) == 0;

            Main.enemies.add(new Claytonling(x, y, chutador));
        }
    }

    /**
     * O CHUTAO: uma bola de futebol gigante em cima do jogador.
     *
     * Ela nao e mais uma bala — ela FURA o ataque. Apaga toda bala inimiga
     * que encosta nela enquanto atravessa (ver BolaBullet), abrindo um
     * rasgo limpo no meio do padrao. O corredor que ela deixa pra tras e o
     * lugar certo pra estar; o problema e que pra chegar la voce tem que
     * passar pela propria bola.
     *
     * Ela vem em INTERVALO FIXO, do comeco ao fim do ataque. Fixo e nao
     * sorteado porque o valor dela e virar ritmo: o jogador conta os
     * segundos, sabe que vem outra e organiza a esquiva em volta disso.
     * Sorteada, seria so mais uma surpresa no meio de um ataque que ja
     * tem surpresa demais.
     *
     * Ela e LENTA de proposito. Uma bola desse tamanho vindo rapido nao
     * seria um obstaculo, seria um dado sendo jogado.
     */
    private void chutarBolaGigante(BossEnemy chefe) {

        if (Main.player == null) {
            return;
        }

        Som.tocar(Som.CHUTE);

        double raio = Config.getDouble("clayton.bolaGigante.raio", 46);
        double vel  = Config.getDouble("clayton.bolaGigante.velocidade", 3.2);

        double dx = Main.player.getX() - chefe.getX();
        double dy = Main.player.getY() - chefe.getY();
        double d = Math.max(1, Math.sqrt(dx * dx + dy * dy));

        // Gravidade ZERO: esta nao arqueia. As outras bolas caem porque
        // sao lancadas de longe e o arco e a leitura delas; esta vem em
        // linha reta porque a leitura dela e "sai da frente", e uma
        // parabola so tornaria o ponto de impacto mais dificil de julgar.
        BolaBullet bola = new BolaBullet(
            chefe.getX(), chefe.getY(),
            dx / d * vel, dy / d * vel,
            0, raio);

        bola.marcarComoGigante();

        Main.bullets.add(bola);
    }

    /**
     * Poe mais uma criatura na parede, alternando o lado.
     *
     * A ALTURA e sorteada dentro de uma faixa do meio do campo. Nem
     * colada no topo (a bola cairia quase reta, sem arco nenhum) nem
     * embaixo (o arco nao teria altura pra subir antes de cair).
     */
    private void soltarCriatura() {

        if (contarCriaturas() >= limiteDeCriaturas) {
            return;
        }

        double topo  = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("clayton.anticodigo.faixaTopoRelY", 0.22);
        double baixo = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("clayton.anticodigo.faixaBaixoRelY", 0.48);

        double y = topo + Math.random() * (baixo - topo);

        Main.enemies.add(new AnticodigoEnemy(y, proximaPelaEsquerda));

        proximaPelaEsquerda = !proximaPelaEsquerda;
    }

    private int contarCriaturas() {

        int total = 0;

        for (int i = 0; i < Main.enemies.size(); i++) {
            if (Main.enemies.get(i) instanceof AnticodigoEnemy) {
                total++;
            }
        }

        return total;
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
