package src.enemyTypes;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import src.Assets;
import src.Config;
import src.Main;
import src.Point;
import src.Som;
import src.enemyTypes.spellCards.SpellCard;

/**
 * Base dos chefes (os professores corrompidos do roteiro).
 *
 * Um chefe nao e um inimigo com muito HP: e uma SEQUENCIA de spell cards.
 * Cada spell card tem HP e tempo proprios; quando o jogador zera o HP (ou
 * o tempo estoura), o chefe limpa a tela e passa pro proximo ataque.
 *
 * O que esta classe resolve, e as subclasses ganham de graca:
 *   - percorrer a lista de spell cards
 *   - barra de HP no topo do campo + contador de ataques restantes
 *   - anuncio do nome do spell card quando ele comeca
 *   - janela de invulnerabilidade e limpeza de balas na troca
 *   - movimento de deriva no alto do campo
 *   - morte: limpa a tela, solta itens e libera a fase pra seguir
 *
 * A subclasse (ver Adriana.java) so precisa dizer QUAIS spell cards ela
 * tem e QUAL sprite usar. O padrao de bala em si mora nas classes de
 * spellCards/, nao aqui.
 */
public class BossEnemy extends Enemy {

    /** Os ataques, na ordem em que serao usados. */
    protected final SpellCard[] spellCards;

    /** Indice do spell card ativo. */
    private int spellAtual = 0;

    /** Ticks desde que o spell card atual comecou. */
    private int tSpell = 0;

    /** Ticks restantes de invulnerabilidade (usado na troca de ataque). */
    private int invulneravel = 0;

    /** Ticks restantes mostrando o nome do ataque na tela. */
    private int anuncio = 0;

    /**
     * ENTRANDO EM CENA, enquanto o dialogo rola.
     *
     * Neste estado ele VOA ate a posicao normal, mas nao ataca e nao
     * pode levar dano. E o que permite a conversa acontecer com o jogo
     * rodando por baixo, como na serie: o chefe chega voando, os
     * retratos deslizam, o texto aparece — e nada disso corta pra outra
     * tela.
     *
     * O primeiro spell card so e anunciado quando isto vira false (ver
     * sairDoDialogo), senao o nome do ataque apareceria por cima da
     * conversa e a barra de HP comecaria a descer durante o dialogo.
     */
    private boolean emDialogo = false;

    /**
     * Ele ja soltou a primeira bala desta LUTA (nao deste spell card).
     *
     * E uma TRAVA: liga uma vez e nunca mais desliga enquanto ele viver.
     * Isso e o ponto — a versao anterior perguntava "tSpell > 0", e o
     * tSpell ZERA a cada troca de ataque. Resultado: no frame da troca a
     * resposta virava "ainda nao esta lutando", a musica caia pro silencio
     * e no frame seguinte voltava DO COMECO. Toda spell card nova
     * reiniciava a trilha.
     *
     * Serve pras duas coisas que so valem depois da luta comecar: o tema
     * do chefe entrar e ele comecar a derivar pros lados.
     */
    private boolean jaComecouAAtacar = false;

    /**
     * O jogador morreu ou bombou durante o spell card atual?
     *
     * Em Touhou, "capturar" um spell card e quebra-lo sem perder vida e
     * sem usar bomba — e so a captura vale o bonus. E o que transforma
     * cada ataque num desafio proprio em vez de um pedaco de barra de HP:
     * da pra vencer feio ou vencer limpo, e o placar sabe a diferenca.
     */
    private boolean spellSujo = false;

    /** Ticks restantes mostrando o resultado da captura. */
    private int anuncioBonus = 0;

    /** Texto do resultado ("CAPTURADO +12000" ou "FALHOU"). */
    private String textoBonus = "";

    /** Se o ultimo spell foi capturado limpo (muda a cor do anuncio). */
    private boolean ultimaCapturaLimpa = false;

    /* --- movimento --- */

    /** Altura em que o chefe fica, em pixels absolutos. */
    private double alturaDeVoo;

    /** Amplitude e periodo da deriva horizontal. */
    private double amplitudeDeriva;
    private double periodoDeriva;

    /** X do centro da deriva (meio do campo). */
    private final double centroX;

    /**
     * Multiplicador da LARGURA do sprite. 1.0 = proporcao original.
     *
     * So desenho: nao mexe no raio de colisao, que continua sendo o
     * circulo de 'radius' no centro.
     */
    protected double escalaLargura = 1.0;

    /**
     * Relogio da deriva. Separado do 't' geral porque ele PARA quando a
     * chefe esta plantada — usar o 't' faria ela dar um pulo lateral no
     * instante em que voltasse a andar.
     */
    private int tDeriva = 0;

    /**
     * Quando true a chefe fica parada onde esta.
     *
     * Existe pros ataques cuja geometria sai DELA: se a origem das balas
     * anda enquanto o padrao e desenhado, o padrao sai cisalhado e os
     * corredores que deveriam existir se fecham. Ver
     * SomasDeRiemannSpell, que planta a chefe durante a rajada e a
     * solta no alivio.
     */
    private boolean paradoNoLugar = false;

    /* --- agrotoxico (AGRICULTURA DIGITAL, da lojinha do Perea) --- */

    /**
     * Ticks restantes de veneno. Renovado pelos drones enquanto pulverizam.
     *
     * O envenenado nao "anda mais devagar": ele RECEBE MENOS TICKS. Isso
     * desacelera tudo dele de uma vez — deriva, cadencia de tiro, relogio
     * do spell card — sem precisar de um fator de velocidade dentro de
     * cada um dos onze ataques do jogo.
     */
    private int agrotoxico = 0;

    /**
     * Sobra de tick acumulada.
     *
     * Com fator 0.55, a cada tick a gente soma 0.55 aqui; o chefe so roda
     * um frame quando isso passa de 1. Sem o acumulador, "pular um tick
     * sim, um nao" seria o unico ritmo possivel (fator 0.5 fixo) e
     * qualquer outro valor de config nao teria efeito nenhum.
     */
    private double sobraDeTick = 0;

    /* --- ajustes --- */

    private int ticksInvulnerabilidadeNaTroca;
    private int ticksAnuncio;

    /**
     * RESPIRO ENTRE A ENTRADA DO CHEFE E A PRIMEIRA BALA DELE.
     *
     * Entre um spell card e outro ja existia uma janela de folga
     * (ticksInvulnerabilidadeNaTroca), mas no COMECO da luta nao havia
     * nenhuma: o dialogo fechava e a primeira bala saia no tick seguinte.
     *
     * Isso era pior justamente nas transformacoes. Nas cenas da Adriana e
     * do Clayton o gatilho CHEFE_TRANSFORMA esta na ULTIMA fala — ou seja,
     * a explosao vermelha estourava, a caixa de dialogo sumia e o padrao
     * novo comecava tudo no mesmo instante, com a tela ainda clareando do
     * estouro e o jogador parado onde a conversa o deixou. Nao havia
     * reacao possivel: o dano vinha de uma coisa que ele ainda nem tinha
     * visto na tela.
     *
     * A folga usa a MESMA invulnerabilidade da troca de spell em vez de um
     * cronometro proprio. Duas regras dizendo "o chefe ainda nao esta
     * lutando" acabariam discordando uma da outra na primeira vez que
     * alguem mexesse em so uma delas.
     */
    private int ticksDeCarenciaNoComeco;

    /** Pontos base por capturar um spell card sem morrer nem bombar. */
    private int bonusDeCaptura;

    /** Fracao do bonus que ainda sobra quando o tempo quase acabou. */
    private double bonusMinimo;

    public BossEnemy(SpellCard[] spellCards, String sprite, double escalaSprite) {

        // O HP inicial e o do primeiro spell card. O raio de colisao vem da
        // config: e generoso (o chefe e grande), mas ainda menor que o sprite.
        super(Main.CAMPO_X + Main.CAMPO_W / 2.0,
              Main.CAMPO_Y - 80,
              spellCards.length > 0 ? spellCards[0].getHp() : 100,
              Config.getDouble("chefe.raio", 42.0));

        this.spellCards = spellCards;
        this.sprite = sprite;
        this.escalaSprite = escalaSprite;
        this.centroX = Main.CAMPO_X + Main.CAMPO_W / 2.0;

        this.pontos = Config.getInt("chefe.pontosAoMorrer", 5000);
        this.itens = Config.getInt("chefe.itensAoMorrer", 30);

        carregarConfig();

        // O primeiro spell card NAO comeca aqui. Quem o dispara e
        // comecarLuta(), chamado ou pelo fim do dialogo ou na hora do
        // spawn quando nao ha conversa nenhuma.
    }

    /**
     * Poe o chefe em modo "chegando": voa pra posicao, calado e imune.
     * Chamado pela fase logo depois de criar ele, antes do dialogo.
     */
    public void entrarEmDialogo() {
        emDialogo = true;
    }

    /**
     * Acabou a conversa: comeca a luta de verdade.
     *
     * Idempotente — a fase chama isso quando percebe que a cutscene
     * terminou, e essa percepcao acontece dentro do tick().
     */
    public void comecarLuta() {

        if (!emDialogo && anuncio > 0) {
            return;
        }

        emDialogo = false;

        if (spellCards.length > 0 && spellAtual == 0 && tSpell == 0) {

            anuncio = ticksAnuncio;

            // O RESPIRO. Ver ticksDeCarenciaNoComeco.
            //
            // Enquanto isto for maior que zero o chefe nao atira (e nem
            // pode ser ferido), entao o jogador tem quase dois segundos
            // pra ler o nome do ataque descendo e sair do lugar onde a
            // conversa o deixou plantado.
            //
            // Math.max e nao atribuicao direta: se ele ja estiver com
            // alguma invulnerabilidade rodando, encurta-la aqui seria
            // piorar o que este campo veio consertar.
            invulneravel = Math.max(invulneravel, ticksDeCarenciaNoComeco);

            spellCards[0].iniciar(this);
            Som.tocar(Som.SPELL_INICIA);
        }
    }

    public boolean isEmDialogo() {
        return emDialogo;
    }

    /**
     * Quanto sobrou do HP do spell card atual, de 1 (cheio) a 0.
     *
     * Serve pra um ataque mudar de comportamento conforme APANHA, e nao
     * so conforme o cronometro anda. A diferenca importa: um ataque que
     * so escala no tempo trata igual quem esta atirando e quem esta
     * fugindo, e o jogador nao sente que o que ele faz muda alguma coisa.
     */
    public double getFracaoDeHpDoSpell() {

        if (hpMaximo <= 0) {
            return 0;
        }

        return Math.max(0, Math.min(1, hp / hpMaximo));
    }

    /**
     * true depois da primeira bala DESTA LUTA.
     *
     * Continua true entre um spell card e outro — e justamente pra isso
     * que ela existe (ver o campo jaComecouAAtacar).
     */
    public boolean jaComecouAAtacar() {
        return jaComecouAAtacar;
    }

    private void carregarConfig() {

        this.alturaDeVoo    = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("chefe.alturaRelY", 0.20);
        this.amplitudeDeriva = Config.getDouble("chefe.amplitudeDeriva", 150.0);
        this.periodoDeriva   = Math.max(1, Config.getDouble("chefe.periodoDeriva", 360.0));

        this.ticksInvulnerabilidadeNaTroca = Config.getInt("chefe.ticksInvulnerabilidadeNaTroca", 90);
        this.ticksAnuncio = Config.getInt("chefe.ticksAnuncio", 120);

        this.ticksDeCarenciaNoComeco = Config.getInt("chefe.ticksDeCarenciaNoComeco", 110);

        this.bonusDeCaptura = Config.getInt("chefe.bonusDeCaptura", 12000);
        this.bonusMinimo    = Config.getDouble("chefe.fracaoMinimaDoBonus", 0.25);
    }

    /* =========================
            LOGICA
       ========================= */

    @Override
    public void tick() {

        // ENVENENADO: o frame simplesmente nao acontece.
        //
        // Fica ANTES de tudo, inclusive do mover(), pra a lentidao valer
        // tambem pra deriva — um chefe que atira devagar mas desliza na
        // velocidade normal parece bugado, nao lento.
        if (agrotoxico > 0) {

            agrotoxico--;

            sobraDeTick += Config.getDouble("perea.agricultura.fatorDeLentidao", 0.55);

            if (sobraDeTick < 1.0) {
                return;
            }

            sobraDeTick -= 1.0;
        }

        mover();

        // Durante o dialogo ele so entra em cena: nao atira, nao conta
        // tempo de spell card e nao pode ser ferido (ver levarDano).
        if (emDialogo) {
            t++;
            return;
        }

        encostarNoJogador();

        if (invulneravel > 0) {
            invulneravel--;
        } else {
            // A trava liga aqui: no primeiro frame em que ele de fato
            // ataca. Nao no comecarLuta(), porque entre um e outro ainda
            // ha a janela de invulnerabilidade da troca.
            jaComecouAAtacar = true;
            atirar();
        }

        if (anuncio > 0) {
            anuncio--;
        }

        if (anuncioBonus > 0) {
            anuncioBonus--;
        }

        // Tempo limite do spell card: o ataque passa mesmo sem o jogador
        // ter zerado o HP. Sem isso, jogador com pouco dano ficaria preso
        // no mesmo padrao pra sempre.
        if (temSpellAtivo() && tSpell >= spellCards[spellAtual].getDuracao()) {
            proximoSpellCard();
        }

        tSpell++;
        t++;
    }

    /**
     * Desce ate a altura de voo e depois deriva de um lado pro outro.
     * Nao persegue o jogador de proposito: chefe de bullet hell tem que
     * ser previsivel, o desafio esta nas balas e nao em caca-lo.
     */
    @Override
    protected void mover() {

        if (y < alturaDeVoo) {
            y += 2.0;
            return;
        }

        y = alturaDeVoo;

        // ELE SO COMECA A DERIVAR QUANDO COMECA A ATACAR.
        //
        // Antes ele ja ia e voltava durante a conversa inteira, o que
        // fazia a cena parecer que ja tinha comecado: chefe que anda
        // parece chefe lutando. Parado no meio, a entrada dele fica sendo
        // uma POSE — ele desce, encara, o dialogo acontece, e o primeiro
        // passo pro lado coincide com a primeira bala.
        //
        // Nao precisa de estado novo: o tDeriva simplesmente nao avanca, e
        // como sin(0) = 0, ele fica exatamente no centro ate la.
        if (jaComecouAAtacar && !paradoNoLugar) {
            tDeriva++;
        }

        x = centroX + Math.sin(2 * Math.PI * tDeriva / periodoDeriva) * amplitudeDeriva;
    }

    /**
     * Joga agrotoxico neste chefe: ele fica lento por 'ticks'.
     *
     * Nao acumula (usa max e nao soma) — dois drones pulverizando ao mesmo
     * tempo renovariam o veneno duas vezes por frame e o chefe congelaria.
     */
    public void aplicarAgrotoxico(int ticks) {
        agrotoxico = Math.max(agrotoxico, ticks);
    }

    /** true enquanto o veneno da AGRICULTURA DIGITAL estiver agindo. */
    public boolean estaEnvenenado() {
        return agrotoxico > 0;
    }

    /**
     * O CORPO DO CHEFE MACHUCA.
     *
     * Sem isto existia um buraco que resolvia o jogo inteiro: dava pra
     * colar no chefe e passar todos os ataques encostado nele. Faz sentido
     * geometricamente — quase todo padrao do jogo NASCE nele e vai pra
     * fora, entao o ponto mais seguro do campo era exatamente em cima da
     * origem. Um bullet hell em que o lugar mais seguro e o centro do
     * inimigo nao e um bullet hell.
     *
     * O RAIO DE CONTATO E MENOR QUE O DE TIRO. O de tiro (chefe.raio) e
     * generoso de proposito, pra acertar um alvo grande nao virar teste de
     * pontaria; usar ele aqui puniria chegar perto, e chegar perto e
     * legitimo — e assim que se causa dano rapido. O de contato cobre so o
     * corpo de verdade, entao voce pode ficar colado, mas nao DENTRO.
     *
     * Nao ha cronometro proprio: quem segura a repeticao e a janela de
     * invulnerabilidade do proprio jogador, que ja existe e ja dura dois
     * segundos. Um cronometro aqui seria uma segunda regra dizendo a mesma
     * coisa, e as duas iam brigar quando alguem mexesse numa delas.
     */
    private void encostarNoJogador() {

        // Enquanto ele esta ENTRANDO em cena nao machuca: ele atravessa o
        // campo de cima ate a posicao de voo, e o jogador pode estar
        // parado no caminho sem ter como saber que devia sair.
        if (emDialogo || Main.player == null) {
            return;
        }

        // ENQUANTO ELE E INTOCAVEL, ELE TAMBEM NAO MACHUCA POR ENCOSTO.
        //
        // Mesma regra, os dois lados. O caso que isso conserta e feio: o
        // jogador anda livre durante a conversa e pode acabar parado
        // exatamente embaixo do chefe. No frame em que o dialogo fecha, o
        // dano por contato liga — e ele morre por estar num lugar que ate
        // ali era seguro, sem nada na tela avisando.
        if (invulneravel > 0) {
            return;
        }

        double raioDeContato = radius * Config.getDouble("chefe.fatorDoRaioDeContato", 0.55);

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        if (dist <= raioDeContato + Main.player.getRadius()) {
            Main.player.levarDano();
        }
    }

    /** Planta (ou solta) a chefe. Ver o campo paradoNoLugar. */
    public void setParadoNoLugar(boolean paradoNoLugar) {
        this.paradoNoLugar = paradoNoLugar;
    }

    public boolean isParadoNoLugar() {
        return paradoNoLugar;
    }

    /**
     * O ataque do chefe e sempre delegado pro spell card ativo — quem
     * escolhe o padrao de bala e a estrategia, nao esta classe.
     *
     * Chama-se atirar() (e nao atacar()) pra respeitar o contrato de
     * Enemy: e o mesmo metodo que todo inimigo do jogo sobrescreve.
     */
    @Override
    protected void atirar() {

        if (temSpellAtivo()) {
            spellCards[spellAtual].atacar(tSpell, this);
        }
    }

    private boolean temSpellAtivo() {
        return spellAtual < spellCards.length;
    }

    /**
     * Avisa que o jogador morreu ou bombou: este spell card nao conta
     * mais como capturado.
     *
     * Chamado pelo Player, nao pelo chefe — quem sabe que perdeu uma vida
     * e o jogador.
     */
    public void marcarFalha() {
        spellSujo = true;
    }

    /**
     * Aplica dano, mas so ate o fim do spell card atual: o excedente NAO
     * vaza pro proximo ataque. Cada spell card e uma luta separada.
     */
    @Override
    public boolean levarDano(double dano) {

        if (!isAlive || invulneravel > 0 || emDialogo) {
            return false;
        }

        hp -= dano;

        if (hp <= 0) {
            proximoSpellCard();
            return !isAlive;
        }

        return false;
    }

    /**
     * Fecha o spell card atual: limpa a tela, dropa uma recompensa e
     * comeca o proximo. Se nao houver proximo, o chefe morre.
     */
    private void proximoSpellCard() {

        Som.tocar(Som.SPELL_QUEBRA);

        fecharBonusDeCaptura();

        // Avisa o ataque que ele acabou ANTES de trocar o indice, senao
        // quem receberia o aviso seria o proximo.
        if (temSpellAtivo()) {
            spellCards[spellAtual].encerrar(this);
        }

        // Rede de seguranca: um ataque que plantou a chefe e foi
        // interrompido no meio a deixaria parada pro resto da luta.
        paradoNoLugar = false;

        limparBalasInimigas();
        soltarItens(Config.getInt("chefe.itensPorSpellCard", 8));

        spellAtual++;

        if (!temSpellAtivo()) {
            morrer();
            return;
        }

        hp = spellCards[spellAtual].getHp();
        hpMaximo = hp;

        tSpell = 0;
        spellSujo = false;
        invulneravel = ticksInvulnerabilidadeNaTroca;
        anuncio = ticksAnuncio;

        spellCards[spellAtual].iniciar(this);
        Som.tocar(Som.SPELL_INICIA);
    }

    @Override
    protected void morrer() {

        isAlive = false;

        if (temSpellAtivo()) {
            spellCards[spellAtual].encerrar(this);
        }

        Som.tocar(Som.CHEFE_MORRE);

        limparBalasInimigas();

        // UM CARD DE GPT EXPANSION por chefe derrotado. Um so.
        //
        // A bomba nao se recupera de nenhuma outra forma na partida
        // inteira: comeca com algumas e, quando acabam, acabaram. Isso
        // fazia a jogada otima ser guardar pra sempre — morrer com bomba
        // no bolso e o erro mais comum de quem joga bullet hell, e o jogo
        // estava premiando ele.
        //
        // Um por chefe (e nao por spell card) mantem o peso da decisao:
        // voce recupera, mas so depois de uma luta inteira.
        Main.points.add(new Point(x, y + 20, false, Point.Tipo.CARD_GPT));

        if (Main.player != null) {
            Main.player.setPontuacao(Main.player.getPontuacao() + pontos);
        }

        soltarItens(itens);
    }

    /**
     * Tira o chefe de cena SEM ser por morte nem por troca de ataque —
     * usado pelo pulo de estagio do modo debug (F2).
     *
     * Existe porque simplesmente limpar a lista de inimigos NAO avisa o
     * spell card ativo de que ele acabou, e ataque que mexe em estado de
     * fora (a maquina de Turing trava o movimento do jogador) deixaria a
     * bagunca pra tras. Com o F2 no meio da fita, o jogador ficava preso
     * dentro do cabecote pro resto da partida.
     */
    public void abandonar() {

        if (temSpellAtivo()) {
            spellCards[spellAtual].encerrar(this);
        }

        paradoNoLugar = false;
        isAlive = false;
    }

    /**
     * Fecha a conta do spell card que acabou de terminar.
     *
     * O bonus so sai se o jogador NAO morreu, NAO bombou e quebrou o
     * ataque no HP (estourar o tempo nao e captura — e sobreviver, que ja
     * tem premio proprio: continuar vivo).
     *
     * O valor decai com o tempo gasto, de 100% ate 'fracaoMinimaDoBonus':
     * matar rapido vale mais. Isso importa porque, sem o decaimento, a
     * jogada otima seria ficar desviando sem atirar ate o fim do tempo.
     */
    private void fecharBonusDeCaptura() {

        if (!temSpellAtivo() || Main.player == null) {
            return;
        }

        boolean porTempo = tSpell >= spellCards[spellAtual].getDuracao();
        ultimaCapturaLimpa = !spellSujo && !porTempo;

        anuncioBonus = ticksAnuncio;

        if (!ultimaCapturaLimpa) {
            textoBonus = porTempo ? "TEMPO ESGOTADO" : "FALHOU";
            return;
        }

        double sobra = 1.0 - tSpell / (double) spellCards[spellAtual].getDuracao();
        double fator = bonusMinimo + (1 - bonusMinimo) * Math.max(0, Math.min(1, sobra));

        int pontos = (int) (bonusDeCaptura * fator);

        Main.player.setPontuacao(Main.player.getPontuacao() + pontos);

        textoBonus = "CAPTURADO  +" + pontos;
    }

    /** Apaga toda bala inimiga da tela (sem tocar nas balas do jogador). */
    private void limparBalasInimigas() {

        for (int i = 0; i < Main.bullets.size(); i++) {

            if (Main.bullets.get(i).isHitPlayer()) {
                Main.bullets.get(i).setAlive(false);
            }
        }
    }

    /**
     * Espalha itens em volta do chefe — parte XP, parte MOEDA.
     *
     * Mesma proporcao dos inimigos comuns (ver Enemy.morrer). Deixar o
     * chefe dando so XP seria estranho justamente onde mais cai item: o
     * jogador terminaria a luta mais longa do jogo com a carteira parada.
     */
    private void soltarItens(int quantidade) {

        int aCada = Math.max(1, Config.getInt("moeda.umaMoedaACada", 3));

        for (int i = 0; i < quantidade; i++) {

            double ang = (2 * Math.PI * i) / Math.max(1, quantidade);
            double raio = 30 + (i % 3) * 18;

            Point.Tipo tipo = (i % aCada == aCada - 1) ? Point.Tipo.MOEDA : Point.Tipo.XP;

            Main.points.add(new Point(x + Math.cos(ang) * raio,
                                      y + Math.sin(ang) * raio,
                                      false,
                                      tipo));
        }
    }

    /* =========================
            RENDER
       ========================= */

    @Override
    public void render(Graphics2D g) {

        // O desenho do ataque vem ANTES do sprite pra a chefe nunca ficar
        // escondida atras dele — o jogador precisa ver onde mirar.
        if (getSpellCardAtual() != null) {
            getSpellCardAtual().render(g);
        }

        desenharSprite(g);

        // Nada de HUD de chefe enquanto ele so esta chegando: barra de
        // vida e cronometro anunciariam uma luta que ainda nao comecou, e
        // competiriam com a caixa de dialogo pelo mesmo canto da tela.
        if (!emDialogo) {
            desenharIndicadorNoRodape(g);
            desenharBarraDeVidaDoChefe(g);
            desenharAnuncio(g);
            desenharResultadoDaCaptura(g);
        }
    }

    /**
     * O MARCADOR NO RODAPE dizendo em que coluna o chefe esta.
     *
     * E o mesmo da serie, e ele resolve um problema concreto: o jogador
     * passa a luta inteira na faixa de baixo da tela olhando pras balas
     * que vem na cara dele. Levantar o olho ate o topo pra achar o chefe
     * custa exatamente o instante em que ele mais precisa estar olhando pra
     * baixo — entao, na pratica, ele atira no escuro.
     *
     * Com o marcador, a informacao "onde mirar" fica no MESMO lugar pra
     * onde ele ja esta olhando. Nao e uma ajuda: e tirar do caminho uma
     * dificuldade que nunca foi sobre habilidade.
     *
     * Ele pulsa por dois motivos somados — um seno lento (a respiracao,
     * pra ele nao sumir no fundo) e um brilho extra enquanto o chefe esta
     * invulneravel na troca de ataque, que avisa "nao adianta atirar
     * agora" sem escrever isso em lugar nenhum.
     */
    private void desenharIndicadorNoRodape(Graphics2D g) {

        int y = Main.CAMPO_Y + Main.CAMPO_H - Config.getInt("chefe.alturaDoIndicador", 10);

        double pulso = 0.5 + 0.5 * Math.sin(t * 0.09);
        double largura = Config.getDouble("chefe.larguraDoIndicador", 26);

        // Cor: rosa normal, esbranquicado enquanto invulneravel.
        int r = 255;
        int gg = invulneravel > 0 ? 230 : 110;
        int b = invulneravel > 0 ? 240 : 160;

        // Halo largo e fraco por baixo, marca solida por cima. Uma marca
        // solida sozinha some numa tela cheia de bala; o halo e o que
        // garante que ela seja percebida pelo canto do olho.
        for (int i = 3; i >= 1; i--) {

            double w = largura * (0.4 + i * 0.35) * (0.85 + 0.15 * pulso);
            int alpha = (int) ((30 + 25 * (4 - i)) * (0.6 + 0.4 * pulso));

            g.setColor(new Color(r, gg, b, Math.min(255, alpha)));
            g.fillOval((int) (x - w / 2), y - 5, (int) w, 10);
        }

        g.setColor(new Color(r, gg, b, (int) (180 + 75 * pulso)));
        g.fillOval((int) (x - largura / 4), y - 3, (int) (largura / 2), 6);

        // Risquinho vertical subindo: liga o marcador ao campo, senao ele
        // parece um enfeite solto na borda de baixo.
        g.setColor(new Color(r, gg, b, (int) (60 * pulso)));
        g.drawLine((int) x, y - 6, (int) x, y - 22);
    }

    private void desenharSprite(Graphics2D g) {

        // Pisca durante a invulnerabilidade da troca de ataque.
        if (invulneravel > 0 && (invulneravel / 5) % 2 == 0) {
            return;
        }

        BufferedImage img = (sprite == null) ? null : Assets.get(sprite);

        if (img == null) {
            g.setColor(new Color(220, 60, 60));
            g.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
            return;
        }

        double caixa = radius * 2 * escalaSprite;
        double fator = caixa / Math.max(img.getWidth(), img.getHeight());

        // escalaLargura estica SO na horizontal, sem mexer na altura nem
        // na colisao. Serve pra arte cuja proporcao original nao combina
        // com o personagem — o Clayton fica magro demais no enquadramento
        // padrao. Deformar de proposito e mais barato (e mais facil de
        // ajustar) do que refazer o PNG.
        int larg = (int) (img.getWidth() * fator * escalaLargura);
        int alt  = (int) (img.getHeight() * fator);

        g.drawImage(img, (int) (x - larg / 2.0), (int) (y - alt / 2.0), larg, alt, null);

        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }

    /**
     * Barra de HP no TOPO do campo (nao em cima do chefe): e onde a serie
     * poe, e evita que o sprite grande cubra a propria barra.
     * Ao lado dela, bolinhas indicando quantos ataques ainda faltam.
     */
    private void desenharBarraDeVidaDoChefe(Graphics2D g) {

        int margem = 20;
        int x0 = Main.CAMPO_X + margem;
        int largura = Main.CAMPO_W - margem * 2;
        int y0 = Main.CAMPO_Y + 34;
        int altura = 7;

        g.setColor(new Color(30, 10, 20, 200));
        g.fillRect(x0, y0, largura, altura);

        double frac = (hpMaximo <= 0) ? 0 : Math.max(0, Math.min(1, hp / hpMaximo));

        g.setColor(new Color(230, 70, 90));
        g.fillRect(x0, y0, (int) (largura * frac), altura);

        g.setColor(new Color(150, 150, 170));
        g.drawRect(x0, y0, largura, altura);

        // Ataques restantes, incluindo o atual.
        int restantes = Math.max(0, spellCards.length - spellAtual);

        g.setColor(new Color(255, 220, 120));

        for (int i = 0; i < restantes; i++) {
            g.fillOval(x0 + i * 14, y0 - 16, 9, 9);
        }

        // Nome do ataque em andamento, no canto direito.
        if (temSpellAtivo()) {

            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.setColor(new Color(220, 200, 220));

            String nome = spellCards[spellAtual].getNome();
            int larguraTexto = g.getFontMetrics().stringWidth(nome);

            g.drawString(nome, x0 + largura - larguraTexto, y0 - 8);

            desenharCronometro(g, x0 + largura, y0 + 22);
        }
    }

    /**
     * Segundos que faltam pro spell card estourar, no canto da barra.
     *
     * Existe pelo mesmo motivo da serie inteira ter: o tempo restante e
     * uma informacao TATICA. Faltando pouco, vale mais desviar do que
     * arriscar chegar perto pra dar dano — mas so da pra tomar essa
     * decisao vendo o numero. Fica vermelho nos ultimos 5 segundos.
     */
    private void desenharCronometro(Graphics2D g, int direita, int y) {

        int restam = Math.max(0, spellCards[spellAtual].getDuracao() - tSpell);
        double segundos = restam / 60.0;

        g.setFont(new Font("Monospaced", Font.BOLD, 15));
        g.setColor(segundos <= 5 ? new Color(255, 110, 110) : new Color(235, 230, 245));

        String texto = String.format("%04.1f", segundos);
        int larg = g.getFontMetrics().stringWidth(texto);

        g.drawString(texto, direita - larg, y);
    }

    /** "CAPTURADO +N" ou "FALHOU", logo depois de um spell card fechar. */
    private void desenharResultadoDaCaptura(Graphics2D g) {

        if (anuncioBonus <= 0 || textoBonus.isEmpty()) {
            return;
        }

        int alpha = Math.max(0, Math.min(255, anuncioBonus * 3));

        g.setFont(new Font("Monospaced", Font.BOLD, 20));

        int larg = g.getFontMetrics().stringWidth(textoBonus);
        int cx = Main.CAMPO_X + Main.CAMPO_W / 2 - larg / 2;
        int cy = Main.CAMPO_Y + Main.CAMPO_H / 3 + 40;

        g.setColor(new Color(0, 0, 0, alpha));
        g.drawString(textoBonus, cx + 2, cy + 2);

        g.setColor(ultimaCapturaLimpa ? new Color(160, 255, 190, alpha)
                                      : new Color(255, 170, 150, alpha));
        g.drawString(textoBonus, cx, cy);
    }

    /**
     * O nome do spell card, DESCENDO pela tela.
     *
     * Ele entra por cima do campo, desliza pra baixo ate a altura de
     * descanso, fica ali a maior parte do tempo e no fim sobe de volta,
     * apagando la em cima.
     *
     * Antes ele so aparecia parado no meio do campo e sumia por
     * transparencia. O problema disso nao e ser feio, e ser AMBIGUO: um
     * texto imovel no meio da tela parece parte da interface, e o jogador
     * fica esperando ter que fazer alguma coisa com ele. Um texto que
     * entra, passa e vai embora se le como ANUNCIO — voce sabe, sem
     * ninguem explicar, que aquilo nao vai ficar ali atrapalhando.
     *
     * Os tres tempos sao fracoes do proprio anuncio, entao mexer no
     * chefe.ticksAnuncio estica ou encurta tudo junto sem desmontar o
     * movimento.
     */
    private void desenharAnuncio(Graphics2D g) {

        if (anuncio <= 0 || !temSpellAtivo()) {
            return;
        }

        // 0 no comeco do anuncio, 1 no fim.
        double f = 1 - anuncio / (double) Math.max(1, ticksAnuncio);

        double entrada = 0.22;   // descendo
        double saida   = 0.72;   // subindo de volta

        double yTopo = Main.CAMPO_Y - 40;                      // fora, em cima
        double yDescanso = Main.CAMPO_Y + Main.CAMPO_H / 3.0;  // onde ele para

        double y;
        int alpha;

        if (f < entrada) {

            // DESCE. A curva desacelera no fim (1-(1-t)^3) pra ele
            // "assentar" na posicao em vez de bater e parar seco.
            double t = f / entrada;
            double suave = 1 - Math.pow(1 - t, 3);

            y = yTopo + (yDescanso - yTopo) * suave;
            alpha = (int) (255 * Math.min(1, t * 2));

        } else if (f < saida) {

            y = yDescanso;
            alpha = 255;

        } else {

            // SOBE e apaga. Acelera (t^2): ele sai de cena com pressa, o
            // que e o oposto da entrada e fecha o movimento.
            double t = (f - saida) / (1 - saida);

            y = yDescanso + (yTopo - yDescanso) * (t * t);
            alpha = (int) (255 * (1 - t));
        }

        alpha = Math.max(0, Math.min(255, alpha));

        if (alpha <= 0) {
            return;
        }

        g.setFont(new Font("Monospaced", Font.BOLD, 24));

        String nome = spellCards[spellAtual].getNome();
        int larguraTexto = g.getFontMetrics().stringWidth(nome);
        int cx = Main.CAMPO_X + Main.CAMPO_W / 2 - larguraTexto / 2;

        // Sombra preta atras, pra ler mesmo com a tela cheia de bala.
        g.setColor(new Color(0, 0, 0, alpha));
        g.drawString(nome, cx + 2, (int) y + 2);

        g.setColor(new Color(255, 210, 230, alpha));
        g.drawString(nome, cx, (int) y);
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    /** O spell card ativo, ou null se o chefe ja terminou todos. */
    public SpellCard getSpellCardAtual() {
        return temSpellAtivo() ? spellCards[spellAtual] : null;
    }

    public int getSpellAtual() {
        return spellAtual;
    }

    public int getTotalDeSpellCards() {
        return spellCards.length;
    }

    /** true se o jogador ainda nao morreu nem bombou neste spell card. */
    public boolean isSpellLimpo() {
        return !spellSujo;
    }

    public boolean isInvulneravel() {
        return invulneravel > 0;
    }

    public int getTSpell() {
        return tSpell;
    }
}
