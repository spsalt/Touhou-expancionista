package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import src.bulletTypes.IntegralBullet;
import src.bulletTypes.PonteiroBullet;
import src.bulletTypes.RicocheteBullet;

/**
 * O estudante expancionista.
 *
 * Controles: WASD/setas move, Z atira, X ou SHIFT segura pra modo foco
 * (anda devagar, tiro fecha o leque, e a hitbox fica visivel).
 *
 * Todos os numeros de jogabilidade sao lidos do game.properties em
 * carregarConfig(), que tambem e chamado no hot-reload (F5). Se voce quer
 * mexer em velocidade/cadencia/dano, mexa no .properties, nao aqui.
 */
public class Player {

    /* =========================
            ESTADO
       ========================= */

    private double x;
    private double y;
    private double speed;

    private int xp = 0;
    private int level = 1;
    private int pontuacao = 0;

    /** Quantas balas ja foram rocadas nesta partida (contador de graze). */
    private int graze = 0;

    private int vidas;
    private int bombas;

    /** Ticks restantes de invulnerabilidade. > 0 = piscando e imune. */
    private int invulneravel = 0;

    /**
     * DEATHBOMB: ticks que faltam pra morte se concretizar.
     *
     * Em Touhou, ser atingido nao mata na hora — existe uma janela de
     * alguns frames em que apertar a bomba ainda salva. E a diferenca
     * entre um jogo que pune reflexo e um que pune desatencao: quem
     * PERCEBEU que ia tomar ainda tem uma saida, mas paga uma bomba
     * por ela.
     *
     * 0 = ninguem foi atingido. > 0 = a morte esta marcada e escorrendo.
     */
    private int morteEm = 0;

    private int ticksDeathbomb;

    /**
     * Quando true, WASD/setas nao movem mais o jogador — quem manda na
     * posicao dele e outra coisa (a maquina de Turing do PAPA prende ele
     * dentro do cabecote da fita).
     *
     * Travado tambem NAO ATIRA e NAO SOLTA BOMBA. Isso e proposital: a
     * maquina de Turing e o unico ataque do jogo em que a resposta certa
     * nao e mexer no personagem, e deixar o tiro ligado faria o jogador
     * segurar o Z por reflexo em vez de olhar pra fita. Sem poder atacar,
     * a unica saida e executar o programa — que e exatamente o que o
     * ataque cobra.
     */
    private boolean travado = false;

    /** Contador regressivo ate o proximo tiro sair. */
    private int shootTime = 0;

    /** Tiro automatico: quando ligado, atira sozinho sem segurar o Z. */
    private boolean autofire;

    /** Estado do C no tick anterior, pra detectar o momento do aperto. */
    private boolean cAnterior = false;

    /** Estado do V no tick anterior, pra detectar o momento do aperto. */
    private boolean vAnterior = false;

    /** Estado do foco no tick anterior, pro som tocar so ao entrar. */
    private boolean focoAnterior = false;

    /** Ticks ate o som de graze poder tocar de novo. */
    private int esperaSomDeGraze = 0;

    /** Cronometro proprio da alma, so pro pulso do brilho. */
    private int tAlma = 0;

    /**
     * A ARMADURA DE ENERGIA que o Santo Java concede (Roteiro.txt linha
     * 17: "uma armadura de energia que se expande infinitamente te cobre
     * e voce sente que tem o poder pra lutar").
     *
     * Comeca DESLIGADA. No estagio 1 o estudante e um cara normal
     * fugindo de bala — sem armadura e sem GPT Expansion. Ela liga na
     * virada pro estagio 2, que e exatamente onde o roteiro coloca o
     * "ESPANDAAAAA".
     *
     * Isso conserta duas coisas de uma vez: a armadura, que o roteiro
     * cita duas vezes e o jogo nunca mostrava, e a bomba, que o jogador
     * tinha desde o menu apesar de so ganhar o poder no fim do primeiro
     * estagio.
     */
    private boolean armadura = false;

    /** Abertura atual do leque de tiro, em radianos. */
    private double shootRad;

    /** Raio da hitbox real (a bolinha verde). */
    private double radius;

    /**
     * Lado do retrato do jogador, EM PIXELS.
     *
     * Absoluto, e nao um multiplicador do raio da hitbox. Isso importa:
     * enquanto era escala, toda vez que a hitbox era apertada pra deixar
     * o jogo mais justo (5.0 -> 3.0 -> 2.0) o personagem encolhia junto,
     * ate virar um confete. Sao duas coisas independentes — o tamanho do
     * DESENHO e uma decisao de arte, o da COLISAO e de jogabilidade — e
     * agora cada uma tem o proprio numero.
     */
    private double tamanhoSprite;

    /* =========================
            CONFIGURACAO
       ========================= */

    private double velocidadeNormal;
    private double velocidadeFoco;
    private double raioColeta;

    /* --- graze e coleta automatica (mecanicas classicas de Touhou) --- */

    /** Distancia ate a borda da bala que ainda conta como "rocou". */
    private double raioGraze;

    /** Pontos ganhos por bala rocada. */
    private int pontosPorGraze;

    /**
     * Altura (relativa ao campo) acima da qual TODOS os itens vem pra voce.
     *
     * E o "Point of Collection" da serie: subir ate a faixa de cima e o
     * lugar mais perigoso do campo, porque e de la que os inimigos e as
     * balas vem. A recompensa por aceitar esse risco e nao precisar caçar
     * item nenhum — eles vem sozinhos.
     */
    private double alturaColetaTotal;
    private double aberturaNormal;
    private double aberturaFoco;
    private int cadenciaTiro;
    private double velocidadeBala;
    private double raioBala;
    private double danoBala;
    private int invulnerabilidadeTicks;

    /* --- tipos de tiro desbloqueados por nivel --- */

    private int nivelPonteiro;
    private int nivelRicochete;
    private int maxBalasDoLeque;
    private int maxPonteiros;

    private double velocidadePonteiro;
    private double taxaDeGiroPonteiro;
    private double raioPonteiro;
    private double fatorDanoPonteiro;

    private double velocidadeRicochete;
    private double raioRicochete;
    private double fatorDanoRicochete;
    private double anguloRicochete;
    private int quiquesRicochete;

    /* --- progressao de nivel --- */

    /** Quantos itens custa o nivel 2. Os seguintes multiplicam por fatorXp. */
    private int xpBase;

    /** Multiplicador do custo a cada nivel. Maior = progressao mais lenta. */
    private double fatorXp;

    /** Teto de nivel. Sem isso o leque de tiro cresceria sem limite. */
    private int nivelMaximo;

    /**
     * Ate onde os ITENS levam sozinhos. Daqui pra cima, so comprando.
     *
     * O jogo nasceu com os itens levando ate o topo, e isso fazia a
     * lojinha do Perea nao ter razao de existir: quem joga bem chega no
     * PAPA com tudo desbloqueado e nenhum motivo pra gastar moeda. Com o
     * teto em 3, os dois ultimos niveis (ricochete e leque cheio) viram
     * COMPRA — e a moeda passa a valer alguma coisa.
     *
     * Repare que isso nao torna o comeco do jogo mais dificil: os niveis 1
     * a 3 continuam saindo na mesma velocidade, e a fase 1 inteira
     * acontece antes do Perea existir. O que muda e o teto de quem chega
     * na segunda metade sem ter juntado nada.
     */
    private int tetoPorItem;

    /* --- a carteira e as compras da lojinha do Perea --- */

    /** Pesos cubanos no bolso. */
    private int moedas = 0;

    /** CULTURA MAKER: passiva, multiplica os pontos que os inimigos dao. */
    private boolean culturaMaker = false;

    /** Usos restantes do OLHO LASER DO PEREA (tecla 1). */
    private int usosDoOlhoLaser = 0;

    /** Usos restantes da AGRICULTURA DIGITAL (tecla 2). */
    private int usosDaAgricultura = 0;

    /** Bordas de subida das teclas 1 e 2 (mesma ideia do C e do V). */
    private boolean um1Anterior = false;
    private boolean um2Anterior = false;

    /* --- o buff do professor Paiola --- */

    /** "Programacao Orientada a Objetos": liga a aura de aviso. */
    private boolean orientadoAObjetos = false;

    /**
     * O quao perto de tomar bala voce esta, de 0 a 1.
     *
     * Recalculado todo tick (ver atualizarAmeaca) e usado so pelo desenho
     * da aura. Guardado num campo em vez de calculado no render porque o
     * render pode ser chamado mais de uma vez por tick quando o jogo
     * recupera frames atrasados, e varrer todas as balas duas vezes seria
     * trabalho jogado fora.
     */
    private double ameacaAtual = 0;

    /**
     * Quantos niveis o jogador perde ao morrer.
     *
     * Em Touhou, morrer derruba o seu Power — e ISSO, e nao a contagem de
     * vidas, e o que faz uma morte doer: voce volta atirando menos e leva
     * mais tempo pra matar o que te matou. Sem alguma perda desse tipo, a
     * morte custa so um numerinho no canto da tela e o jogo fica sem
     * consequencia.
     *
     * 0 desliga (comportamento antigo).
     */
    private int niveisPerdidosAoMorrer;

    /** Quanto vale, em pontos, cada item pego ja no nivel maximo. */
    private int xpEmPontos;

    public Player(double x, double y, double radius) {

        this.x = x;
        this.y = y;
        this.radius = radius;

        carregarConfig();

        // Vidas, bombas e autofire so no construtor: recarregar config no
        // meio da partida nao deve devolver vidas nem desligar o autofire
        // que o jogador acabou de ligar na mao.
        this.vidas    = Config.getInt("jogador.vidas", 3);
        this.bombas   = Config.getInt("jogador.bombas", 3);
        this.autofire = Config.getBool("jogador.autofireInicial", true);
    }

    /**
     * (Re)le os ajustes do game.properties.
     * Chamado no construtor e no F5, pra dar pra tunar com o jogo aberto.
     */
    public void carregarConfig() {

        this.velocidadeNormal = Config.getDouble("jogador.velocidade", 80.0);
        this.velocidadeFoco   = Config.getDouble("jogador.velocidadeFoco", 1.75);
        this.radius           = Config.getDouble("jogador.raioHitbox", 5.0);
        this.raioColeta       = Config.getDouble("jogador.raioColeta", 50.0);

        this.raioGraze         = Config.getDouble("jogador.raioGraze", 26.0);
        this.pontosPorGraze    = Config.getInt("jogador.pontosPorGraze", 60);
        this.alturaColetaTotal = Config.getDouble("jogador.alturaColetaTotalRelY", 0.22);
        this.tamanhoSprite    = Config.getDouble("jogador.tamanhoSprite", 62.0);

        this.aberturaNormal   = Config.getDouble("jogador.aberturaTiro", 0.5);
        this.aberturaFoco     = Config.getDouble("jogador.aberturaTiroFoco", 0.1);
        this.cadenciaTiro     = Config.getInt("jogador.cadenciaTiro", 5);
        this.velocidadeBala   = Config.getDouble("jogador.velocidadeBala", 12.0);
        this.raioBala         = Config.getDouble("jogador.raioBala", 4.0);
        this.danoBala         = Config.getDouble("jogador.danoBala", 1.0);

        this.invulnerabilidadeTicks = Config.getInt("jogador.invulnerabilidadeTicks", 90);
        this.ticksDeathbomb         = Config.getInt("jogador.ticksDeathbomb", 12);

        this.nivelPonteiro   = Config.getInt("tiro.ponteiro.nivel", 2);
        this.nivelRicochete  = Config.getInt("tiro.ricochete.nivel", 4);
        this.maxBalasDoLeque = Math.max(1, Config.getInt("tiro.leque.maximo", 4));
        this.maxPonteiros    = Math.max(1, Config.getInt("tiro.ponteiro.maximo", 3));

        this.velocidadePonteiro = Config.getDouble("tiro.ponteiro.velocidade", 7.5);
        this.taxaDeGiroPonteiro = Config.getDouble("tiro.ponteiro.taxaDeGiro", 0.10);
        this.raioPonteiro       = Config.getDouble("tiro.ponteiro.raio", 4.5);
        this.fatorDanoPonteiro  = Config.getDouble("tiro.ponteiro.fatorDano", 0.35);

        this.velocidadeRicochete = Config.getDouble("tiro.ricochete.velocidade", 9.0);
        this.raioRicochete       = Config.getDouble("tiro.ricochete.raio", 6.0);
        this.fatorDanoRicochete  = Config.getDouble("tiro.ricochete.fatorDano", 0.7);
        this.anguloRicochete     = Config.getDouble("tiro.ricochete.anguloGraus", 38);
        this.quiquesRicochete    = Config.getInt("tiro.ricochete.quiques", 3);

        this.xpBase      = Math.max(1, Config.getInt("jogador.xpBase", 12));
        this.fatorXp     = Math.max(1.0, Config.getDouble("jogador.fatorXpPorNivel", 2.4));
        this.nivelMaximo = Math.max(1, Config.getInt("jogador.nivelMaximo", 6));
        this.tetoPorItem = Math.max(1, Math.min(nivelMaximo,
                                    Config.getInt("jogador.tetoDeNivelPorItem", 3)));
        this.xpEmPontos  = Config.getInt("jogador.xpEmPontosNoNivelMaximo", 50);

        this.niveisPerdidosAoMorrer = Math.max(0, Config.getInt("jogador.niveisPerdidosAoMorrer", 1));
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        // --- modo foco (X ou SHIFT segurado) ---
        // Borda de subida: o som toca no instante em que entra no foco,
        // senao dispararia 60x por segundo enquanto a tecla ficasse presa.
        if (Main.x && !focoAnterior) {
            Som.tocar(Som.FOCO);
        }
        focoAnterior = Main.x;

        if (Main.x) {
            speed = velocidadeFoco;
            shootRad = aberturaFoco;
        } else {
            speed = velocidadeNormal;
            shootRad = aberturaNormal;
        }

        subirDeNivel();

        // --- movimento ---
        if (!travado) {

            if (Main.up)    y -= speed;
            if (Main.down)  y += speed;
            if (Main.left)  x -= speed;
            if (Main.right) x += speed;

            prenderNoCampo();
        }

        tAlma++;

        contarGraze();
        coletaTotalNoTopo();
        atualizarAmeaca();

        // --- liga/desliga o autofire (tecla C) ---
        // Detecta a BORDA (agora apertado, antes nao): sem isso o tick
        // rodaria a troca 60x por segundo enquanto a tecla ficasse presa.
        if (Main.c && !cAnterior) {
            autofire = !autofire;
        }
        cAnterior = Main.c;

        // --- GPT Expansion (tecla V; tambem pode ser clicada no HUD) ---
        // Se a morte esta marcada, o MESMO V vira deathbomb. Nao sao duas
        // teclas: e a mesma decisao ("gasto uma bomba agora?") tomada num
        // momento mais apertado.
        if (Main.v && !vAnterior && !travado && !Main.emDialogo()) {

            if (morteEm > 0) {
                deathbomb();
            } else {
                usarGptExpansao();
            }
        }
        vAnterior = Main.v;

        // --- os itens comprados do Perea (teclas 1 e 2) ---
        // Mesma leitura por borda das outras teclas. Eles NAO funcionam
        // durante dialogo nem com o jogador travado (a maquina de Turing
        // trava tudo), pelo mesmo motivo do tiro: e um momento de leitura,
        // nao de acao.
        if (Main.tecla1 && !um1Anterior && !travado && !Main.emDialogo()) {
            usarOlhoLaser();
        }
        um1Anterior = Main.tecla1;

        if (Main.tecla2 && !um2Anterior && !travado && !Main.emDialogo()) {
            usarAgriculturaDigital();
        }
        um2Anterior = Main.tecla2;

        // A janela escorre DEPOIS de ler a tecla: assim o ultimo frame
        // dela ainda e aproveitavel, em vez de ser perdido por ordem de
        // execucao.
        if (morteEm > 0) {

            morteEm--;

            if (morteEm == 0) {
                perderVida();
            }
        }

        // --- tiro ---
        if (shootTime > 0) {
            shootTime--;
        }

        // Com autofire ligado o Z vira opcional; segurar Z continua
        // funcionando normalmente pra quem preferir.
        // Nao atira durante a conversa.
        //
        // O dialogo virou sobreposicao (o jogo continua rodando por
        // baixo), e sem esta guarda o autofire mantinha o jogador
        // metralhando o vazio enquanto le — barulho e rastro de bala
        // competindo com o texto, num momento que era pra ser de leitura.
        // Mover continua liberado: e o que a serie faz.
        if ((Main.z || autofire) && shootTime <= 0 && !travado && !Main.emDialogo()) {
            atirar();
            shootTime = cadenciaTiro;
        }

        // --- invulnerabilidade escorrendo ---
        if (invulneravel > 0) {
            invulneravel--;
        }

        // --- codigo de teste antigo, ligavel pelo game.properties ---
        if (Config.getBool("debug.pontosDeTeste", false) && shootTime % 5 == 0) {
            Main.points.add(new Point(Main.CAMPO_X + Main.CAMPO_W / 2.0, Main.CAMPO_Y, false));
        }
    }

    /**
     * GRAZE: passar raspando numa bala inimiga sem ser atingido.
     *
     * E a mecanica que faz Touhou ser Touhou. Sem ela, o desvio otimo e
     * ficar o mais LONGE possivel de tudo, o que e chato de jogar e chato
     * de ver. Com ela, a jogada que mais pontua e exatamente a mais
     * arriscada: passar a um fio da bala.
     *
     * A conta e a distancia entre os centros menos o raio da bala, ou
     * seja, a folga ate a BORDA dela — usar o centro faria bala grande
     * valer graze de longe e bala pequena quase nunca.
     */
    private void contarGraze() {

        if (esperaSomDeGraze > 0) {
            esperaSomDeGraze--;
        }

        for (int i = 0; i < Main.bullets.size(); i++) {

            src.bulletTypes.Bullet bala = Main.bullets.get(i);

            if (!bala.isHitPlayer() || bala.isRocada()) {
                continue;
            }

            double folga = Main.getDist(x, y, bala.getX(), bala.getY()) - bala.getRadius();

            // Menor que o raio da hitbox nao e graze, e dano — e quem
            // trata isso e a propria bala.
            if (folga > radius && folga <= raioGraze) {

                bala.setRocada(true);

                graze++;
                pontuacao += pontosPorGraze;

                // Numa parede de bala da pra rocar dezenas no MESMO tick.
                // Sem esta espera, o efeito vira um ruido branco por cima
                // da musica em vez de um "tec" que informa alguma coisa.
                if (esperaSomDeGraze <= 0) {
                    Som.tocar(Som.GRAZE);
                    esperaSomDeGraze = 6;
                }
            }
        }
    }

    /**
     * POINT OF COLLECTION: no topo do campo, todo item vem pra voce.
     *
     * Chamar isso todo tick e barato (uma comparacao e, quando vale, um
     * setCatch por item). O item ja sabe se mover sozinho depois de
     * marcado, entao aqui so acendemos o interruptor.
     */
    private void coletaTotalNoTopo() {

        if (y > Main.CAMPO_Y + Main.CAMPO_H * alturaColetaTotal) {
            return;
        }

        for (int i = 0; i < Main.points.size(); i++) {
            Main.points.get(i).setCatch(true);
        }
    }

    /**
     * Ativa a GPT Expansion: gasta uma carga e solta o efeito que se
     * expande a partir do jogador (ver GptExpansion.java): mata inimigo
     * comum, apaga as balas e fere o chefe sem mata-lo.
     *
     * Publico porque tanto o teclado (V, aqui em tick()) quanto o clique
     * do mouse no botao do HUD (tratado no Main) chamam este metodo.
     */
    public void usarGptExpansao() {

        // Sem armadura nao ha compilador pra expandir.
        if (!armadura || bombas <= 0) {
            return;
        }

        bombas--;
        Som.tocar(Som.GPT_EXPANSION);
        Main.efeitosGpt.add(new GptExpansion(x, y));

        // A bomba PROTEGE enquanto acontece. Isso e regra da serie e nao
        // enfeite: a bomba existe pra tirar voce de uma situacao perdida,
        // e limpar a tela nao resolve nada se o padrao recomeca a cuspir
        // bala no frame seguinte e voce morre dentro da propria bomba.
        // So AUMENTA a invulnerabilidade — nunca encurta uma que ja
        // estava rodando (o caso do deathbomb, que ja tinha dado a janela
        // cheia antes de chamar aqui).
        invulneravel = Math.max(invulneravel,
                                Config.getInt("gptExpansao.ticksInvulneravel", 70));

        if (Main.chefeEmCena() != null) {
            Main.chefeEmCena().marcarFalha();
        }
    }

    /**
     * Gasta o XP acumulado em niveis.
     *
     * 'while' e nao 'if': se o jogador pegar um punhado de itens de uma vez
     * (varios inimigos morrendo juntos), ele sobe todos os niveis que der no
     * mesmo tick, em vez de segurar um por frame.
     */
    private void subirDeNivel() {

        // O teto aqui e o tetoPorItem, e nao o nivelMaximo: item leva ate
        // o 3, os niveis acima disso saem da lojinha do Perea (ver
        // comprarNivel). Note que a comparacao continua sendo com o
        // 'level' geral — quem COMPROU o nivel 4 nao volta a subir sozinho.
        while (level < tetoPorItem && xp >= getXpParaProximoNivel()) {
            xp -= getXpParaProximoNivel();
            level++;
            Som.tocar(Som.SUBIR_NIVEL);
        }

        // No teto o XP nao serve mais pra nada, entao vira pontuacao:
        // continuar coletando itens ainda vale a pena.
        if (level >= tetoPorItem && xp > 0) {
            pontuacao += xp * xpEmPontos;
            xp = 0;
        }
    }

    /* =========================
            A LOJINHA DO PEREA
       ========================= */

    /** Guarda moeda no bolso. Chamado pelo Point ao encostar. */
    public void receberMoedas(int quantas) {
        moedas = Math.max(0, moedas + quantas);
    }

    /**
     * Tenta pagar. Devolve false (e nao desconta nada) se faltar dinheiro.
     *
     * Quem decide se a compra vale e a loja; aqui so mexe na carteira. E o
     * mesmo motivo de levarDano nao saber o que e um chefe: cada classe
     * cuida do que e dela.
     */
    public boolean gastarMoedas(int quanto) {

        if (quanto > moedas) {
            return false;
        }

        moedas -= quanto;
        return true;
    }

    /**
     * Sobe um nivel COMPRADO, passando por cima do teto dos itens.
     *
     * Vai ate o nivelMaximo de verdade. Devolve false se ja estiver no
     * topo, pra loja poder mostrar o item como esgotado em vez de deixar
     * o jogador jogar moeda fora.
     */
    public boolean comprarNivel() {

        if (level >= nivelMaximo) {
            return false;
        }

        level++;
        Som.tocar(Som.SUBIR_NIVEL);

        return true;
    }

    /* =========================
            O BUFF DO PAIOLA
       ========================= */

    /**
     * "PROGRAMACAO ORIENTADA A OBJETOS", o presente do professor Paiola.
     *
     * Nao da dano, nao da vida e nao desvia por voce: acende uma aura azul
     * quando ALGUMA bala esta em rota de colisao com voce dentro do
     * proximo segundo. E puro AVISO.
     *
     * POR QUE ISSO E UM BUFF DE VERDADE
     * ---------------------------------
     * Numa tela com cem balas, a informacao mais cara de obter e "qual
     * delas e comigo". A maioria passa longe; a que mata e uma so, e ela
     * nao parece diferente das outras. A aura nao diz QUAL bala e — isso
     * seria resolver o jogo pelo jogador — ela diz apenas que existe uma,
     * agora. O trabalho de achar continua sendo seu; o que some e o
     * cansaco de vigiar a tela inteira o tempo todo.
     *
     * O nome nao e so piada: a implementacao e literalmente polimorfismo.
     * A checagem nao sabe se esta olhando uma integral, uma bandeira, uma
     * peca de xadrez ou uma bola de futebol — ela pergunta a MESMA coisa
     * pra todas (onde voce esta, pra onde vai) e cada uma responde do seu
     * jeito. Adicionar um tipo de bala novo ao jogo nao pede uma linha
     * sequer aqui.
     */
    private void atualizarAmeaca() {

        if (!orientadoAObjetos) {
            ameacaAtual = 0;
            return;
        }

        int horizonte = Math.max(1, Config.getInt("paiola.ticksDeAntecedencia", 60));
        double folga = Config.getDouble("paiola.folgaDaAmeaca", 14.0);

        double pior = 0;

        for (int i = 0; i < Main.bullets.size(); i++) {

            src.bulletTypes.Bullet b = Main.bullets.get(i);

            // Bala do jogador nao ameaca o jogador.
            if (!b.isAlive() || !b.isHitPlayer()) {
                continue;
            }

            double t = tempoAteEncostar(b, horizonte, folga);

            if (t < 0) {
                continue;
            }

            // Quanto MENOS tempo falta, mais forte a aura. Uma aura de
            // intensidade fixa avisaria tarde demais e cedo demais do
            // mesmo jeito; assim ela cresce conforme a coisa se aproxima,
            // e o jogador aprende a ler urgencia sem nenhum numero.
            double intensidade = 1.0 - t / horizonte;

            if (intensidade > pior) {
                pior = intensidade;
            }
        }

        ameacaAtual = pior;
    }

    /**
     * Em quantos ticks esta bala encosta em mim, ou -1 se nao encosta.
     *
     * MOVIMENTO RELATIVO, com o jogador tratado como PARADO. Prever pra
     * onde o jogador vai andar seria chute: ele muda de direcao a qualquer
     * frame, e a aura acenderia por causa de um movimento que ele nem
     * pretende fazer. Parado, a pergunta vira honesta: "se eu ficar
     * exatamente aqui, isso me acerta?"
     *
     * A conta e o instante de MENOR DISTANCIA entre dois pontos que se
     * movem em linha reta:
     *
     *     t* = -(p . v) / (v . v)
     *
     * onde p e a posicao relativa e v a velocidade relativa. Se p.v for
     * positivo a bala ja esta se afastando, e nem precisa continuar.
     */
    private double tempoAteEncostar(src.bulletTypes.Bullet b, int horizonte, double folga) {

        double px = b.getX() - x;
        double py = b.getY() - y;

        double vx = b.getVelX();
        double vy = b.getVelY();

        double vv = vx * vx + vy * vy;

        // Bala parada (ou no primeiro frame de vida, antes do primeiro
        // guardarPosicao): nao da pra dizer pra onde ela vai.
        if (vv < 0.0001) {
            return -1;
        }

        double pv = px * vx + py * vy;

        // Ja esta se afastando.
        if (pv >= 0) {
            return -1;
        }

        double t = -pv / vv;

        if (t > horizonte) {
            return -1;
        }

        // Distancia no instante mais proximo.
        double dx = px + vx * t;
        double dy = py + vy * t;

        double alcance = b.getRadius() + radius + folga;

        if (dx * dx + dy * dy > alcance * alcance) {
            return -1;
        }

        return t;
    }

    /**
     * O halo azul. Intensidade e tamanho seguem a urgencia da ameaca.
     *
     * Tres aneis concentricos em vez de um disco cheio: disco opaco em
     * volta do personagem esconderia as balas que passam colado nele, que
     * sao exatamente as que interessam.
     */
    private void desenharAuraDeOrientacao(Graphics2D g) {

        if (ameacaAtual <= 0.01) {
            return;
        }

        double pulso = 1 + 0.10 * Math.sin(tAlma * 0.35);
        double base = Config.getDouble("paiola.raioDaAura", 34.0);

        for (int i = 3; i >= 1; i--) {

            double raio = base * (0.55 + i * 0.20) * pulso * (0.75 + 0.25 * ameacaAtual);

            int alpha = (int) (ameacaAtual * (30 + 45 * (4 - i)));

            if (alpha <= 0) {
                continue;
            }

            g.setColor(new Color(90, 150, 255, Math.min(190, alpha)));
            g.fillOval((int) (x - raio), (int) (y - raio),
                       (int) (raio * 2), (int) (raio * 2));
        }

        // Anel de contorno bem fino, pra a aura ter borda e nao virar
        // mancha azul quando estiver fraca.
        double raio = base * pulso;

        g.setColor(new Color(170, 210, 255, (int) (Math.min(1, ameacaAtual) * 200)));
        g.drawOval((int) (x - raio), (int) (y - raio), (int) (raio * 2), (int) (raio * 2));
    }

    /**
     * Apaga TUDO que foi ganho ao longo da partida.
     *
     * Buff do Paiola, armadura, compras do Perea e a carteira. Chamado no
     * reinicio da partida.
     *
     * Isto e redundante hoje — o reiniciarPartida() cria um Player novo, e
     * um Player novo ja nasce sem nada. Existe assim mesmo por dois
     * motivos: deixa explicito no codigo O QUE e progresso de partida (a
     * lista estava so implicita nos valores iniciais dos campos), e
     * protege o dia em que alguem resolver reaproveitar o objeto em vez de
     * criar outro — que e exatamente o caminho pelo qual esse tipo de bug
     * costuma voltar.
     */
    public void zerarProgressoDaPartida() {

        orientadoAObjetos = false;
        ameacaAtual = 0;

        armadura = false;

        moedas = 0;
        culturaMaker = false;
        usosDoOlhoLaser = 0;
        usosDaAgricultura = 0;

        level = 1;
        xp = 0;
    }

    /** Liga o buff do Paiola. Chamado pelo gatilho da cutscene. */
    public void ganharOrientacaoAObjetos() {

        if (orientadoAObjetos) {
            return;
        }

        orientadoAObjetos = true;
        Som.tocar(Som.SUBIR_NIVEL);
    }

    public boolean temOrientacaoAObjetos() {
        return orientadoAObjetos;
    }

    /** 0 = ninguem vindo; 1 = bala encostando agora. */
    public double getAmeacaAtual() {
        return ameacaAtual;
    }

    /**
     * OLHO LASER DO PEREA (tecla 1): gasta um uso e acende o raio.
     *
     * So um raio por vez. Deixar empilhar transformaria "guardei cinco
     * usos" em "apago o chefe em dois segundos", e o item deixaria de ser
     * uma decisao pra ser um botao de pular luta.
     */
    private void usarOlhoLaser() {

        if (usosDoOlhoLaser <= 0 || !Main.olhosLaser.isEmpty()) {
            return;
        }

        usosDoOlhoLaser--;
        Main.olhosLaser.add(new OlhoLaser(this));
    }

    /** AGRICULTURA DIGITAL (tecla 2): gasta um uso e solta os dois drones. */
    private void usarAgriculturaDigital() {

        if (usosDaAgricultura <= 0 || !Main.agriculturas.isEmpty()) {
            return;
        }

        usosDaAgricultura--;
        Main.agriculturas.add(new AgriculturaDigital(this));
    }

    /**
     * Pontos ganhos por matar alguma coisa, ja com a CULTURA MAKER.
     *
     * Todo lugar que dava pontuacao por inimigo passou a chamar isto. A
     * passiva NAO se aplica a graze nem ao bonus de captura de spell card
     * de proposito: ela e "voce faz mais pontos DOS INIMIGOS", que foi o
     * pedido, e deixar ela multiplicar tudo transformaria uma compra de
     * loja no unico fator que importa no placar.
     */
    public void ganharPontos(int base) {

        double fator = culturaMaker
                     ? Config.getDouble("perea.culturaMaker.fator", 2.0)
                     : 1.0;

        pontuacao += (int) (base * fator);
    }

    /**
     * O disparo do jogador. Cada nivel DESBLOQUEIA UM TIPO DE TIRO novo,
     * em vez de so somar mais uma bala no leque.
     *
     *   N1  leque
     *   N2  leque + PONTEIROS   (teleguiados, fracos)
     *   N3  leque mais largo + ponteiros
     *   N4  + RICOCHETES        (quicam nas laterais)
     *   N5  tudo, com o leque cheio
     *
     * A diferenca nao e so de numero: cada tipo resolve um problema
     * diferente. O leque e o dano de frente; o ponteiro caça quem fugiu
     * pro canto; o ricochete alcança quem esta colado na parede. Somar
     * balas iguais so aumentava um numero — isto muda COMO se joga.
     */
    private void atirar() {

        Som.tocar(Som.TIRO_JOGADOR);

        dispararLeque();

        if (level >= nivelPonteiro) {
            dispararPonteiros();
        }

        if (level >= nivelRicochete) {
            dispararRicochetes();
        }

        if (Config.getBool("debug.balasDosCantos", false)) {
            balasDosCantos();
        }
    }

    /**
     * O tiro principal: um leque de balas retas pra cima.
     *
     * A quantidade cresce com o nivel, mas devagar (ver balasDoLeque):
     * o grosso do poder novo vem dos TIPOS que os niveis desbloqueiam,
     * nao da contagem.
     */
    private void dispararLeque() {

        int quantidade = balasDoLeque();

        for (int i = 0; i < quantidade; i++) {

            // Uma bala so nao tem leque: dividir por (quantidade-1) daria
            // divisao por zero.
            double ang = (quantidade == 1)
                       ? 0
                       : -shootRad / 2.0 + i * (shootRad / (quantidade - 1.0));

            IntegralBullet bala = new IntegralBullet(
                x, y,
                Math.sin(ang) * velocidadeBala,
                -Math.cos(ang) * velocidadeBala,
                0, 0,
                raioBala,
                false,
                new Color(120, 220, 255)
            );

            bala.setDano(danoBala);
            bala.setSprite(Config.getString("tiro.leque.sprite", "sprites/GFX/bala_leque.png"));

            Main.bullets.add(bala);
        }
    }

    /** Quantas balas o leque tem no nivel atual. */
    private int balasDoLeque() {
        return Math.min(level, maxBalasDoLeque);
    }

    /**
     * PONTEIROS: saem em diagonal pra cima e depois curvam atras do
     * inimigo mais proximo. Dano baixo — o valor deles e a cobertura.
     *
     * Saem inclinados (e nao retos) pra nao competirem com o leque no
     * mesmo espaco: eles abrem, procuram e voltam.
     */
    private void dispararPonteiros() {

        Som.tocar(Som.TIRO_PONTEIRO);

        int quantidade = 1 + (level - nivelPonteiro);
        quantidade = Math.max(1, Math.min(quantidade, maxPonteiros));

        for (int i = 0; i < quantidade; i++) {

            // Alterna os lados: -90 graus e a vertical pra cima na tela.
            double lado = (i % 2 == 0) ? -1 : 1;
            double abertura = 0.6 + 0.25 * (i / 2);

            double ang = -Math.PI / 2 + lado * abertura;

            Main.bullets.add(new PonteiroBullet(
                x, y, ang,
                velocidadePonteiro,
                taxaDeGiroPonteiro,
                raioPonteiro,
                danoBala * fatorDanoPonteiro,
                new Color(150, 255, 170)
            ));
        }
    }

    /**
     * RICOCHETES: dois losangos em diagonal que quicam nas laterais.
     * Dano medio, mas varrem a tela em ziguezague e pegam quem esta
     * encostado na borda.
     */
    private void dispararRicochetes() {

        Som.tocar(Som.TIRO_RICOCHETE);

        double ang = Math.toRadians(anguloRicochete);

        for (int lado = -1; lado <= 1; lado += 2) {

            Main.bullets.add(new RicocheteBullet(
                x, y,
                Math.sin(ang) * velocidadeRicochete * lado,
                -Math.cos(ang) * velocidadeRicochete,
                raioRicochete,
                danoBala * fatorDanoRicochete,
                quiquesRicochete,
                new Color(255, 180, 90)
            ));
        }
    }

    /**
     * Codigo de teste do grupo: quatro balas inimigas vindas dos cantos,
     * miradas no jogador. Serve pra testar colisao sem depender de inimigo.
     * Ligue com debug.balasDosCantos=true no game.properties.
     */
    private void balasDosCantos() {

        double spd = 1.5;

        double[][] cantos = {
            { Main.CAMPO_X,               Main.CAMPO_Y },
            { Main.CAMPO_X + Main.CAMPO_W, Main.CAMPO_Y },
            { Main.CAMPO_X,               Main.CAMPO_Y + Main.CAMPO_H },
            { Main.CAMPO_X + Main.CAMPO_W, Main.CAMPO_Y + Main.CAMPO_H }
        };

        for (double[] c : cantos) {

            double dist = Main.getDist(c[0], c[1], x, y);

            if (dist == 0) {
                continue;
            }

            Main.bullets.add(new IntegralBullet(
                c[0], c[1],
                spd * ((x - c[0]) / dist),
                spd * ((y - c[1]) / dist),
                0, 0, 10, true
            ));
        }
    }

    /** Impede o jogador de sair da arena. */
    private void prenderNoCampo() {

        double margem = 8;

        if (x < Main.CAMPO_X + margem)                x = Main.CAMPO_X + margem;
        if (x > Main.CAMPO_X + Main.CAMPO_W - margem) x = Main.CAMPO_X + Main.CAMPO_W - margem;
        if (y < Main.CAMPO_Y + margem)                y = Main.CAMPO_Y + margem;
        if (y > Main.CAMPO_Y + Main.CAMPO_H - margem) y = Main.CAMPO_Y + Main.CAMPO_H - margem;
    }

    /**
     * Leva um hit. Chamado pelas balas inimigas.
     *
     * @return true se o dano foi aplicado de fato; false se o jogador
     *         estava invulneravel (a bala deve atravessar sem sumir)
     */
    /**
     * Registra um acerto. NAO mata na hora: abre a janela de deathbomb.
     *
     * Devolve true quando o acerto foi aceito (a bala que chamou deve
     * sumir). Devolve false se o jogador esta invulneravel ou se ja tem
     * uma morte marcada — nesse caso a bala passa reto, senao um segundo
     * projetil chegando no mesmo frame cobraria duas vidas por um erro so.
     */
    public boolean levarDano() {

        if (invulneravel > 0 || morteEm > 0) {
            return false;
        }

        // Janela desligada no properties: morre na hora, como antes.
        if (ticksDeathbomb <= 0) {
            perderVida();
            return true;
        }

        Som.tocar(Som.QUASE_MORRE);

        morteEm = ticksDeathbomb;

        return true;
    }

    /**
     * Gasta uma bomba pra cancelar a morte marcada.
     *
     * Sem bomba nao ha salvacao — a janela simplesmente corre ate o fim.
     */
    private void deathbomb() {

        if (!armadura || bombas <= 0) {
            return;
        }

        morteEm = 0;
        invulneravel = invulnerabilidadeTicks;

        usarGptExpansao();
    }

    /** A morte de fato: tira a vida e, se acabou, encerra a partida. */
    private void perderVida() {

        Som.tocar(Som.JOGADOR_DANO);

        // As tralhas do estudante voam pra todo lado. E o unico aviso
        // realmente visivel de que voce morreu quando a tela esta cheia.
        Destroco.explodir(x, y);

        vidas--;
        invulneravel = invulnerabilidadeTicks;

        // Perde poder de fogo junto com a vida (ver niveisPerdidosAoMorrer).
        // O XP acumulado no nivel atual tambem vai embora: senao dava pra
        // morrer e subir de novo no tick seguinte.
        if (niveisPerdidosAoMorrer > 0) {
            level = Math.max(1, level - niveisPerdidosAoMorrer);
            xp = 0;
        }

        // Avisa o chefe que este spell card foi "sujado": quem morre
        // durante um ataque perde o bonus de captura dele.
        if (Main.chefeEmCena() != null) {
            Main.chefeEmCena().marcarFalha();
        }

        // NAO limpa a tela: as balas continuam onde estao e o jogador
        // atravessa elas durante a invulnerabilidade. Apagar tudo a cada
        // dano deixava a luta sem tensao e sumia com o padrao que o
        // jogador estava lendo — a janela de invencibilidade ja e a
        // protecao suficiente pra ele se reposicionar.

        if (vidas <= 0) {
            Som.tocar(Som.GAME_OVER);

            // Nao vai direto pro game over: a partida fica congelada e o
            // jogador decide se aceita continuar sujando o final.
            Main.gameState = "Continue";
            Main.menuDeContinue.reiniciar();
        }
    }

    /**
     * Volta a partida depois de um continue aceito.
     *
     * Devolve vidas e da uma janela de invulnerabilidade generosa: o
     * jogador renasce exatamente no lugar onde morreu, e sem essa janela
     * a mesma bala que o matou o mataria de novo no primeiro frame.
     *
     * Os PODERES nao vem daqui — vem dos itens FULL que o Main espalha.
     * A diferenca importa: assim o jogador ve de onde o poder veio, em
     * vez de a barra encher sozinha por magica.
     */
    public void reviver(int vidasNovas, int bombasNovas) {

        vidas = Math.max(1, vidasNovas);
        bombas = Math.max(0, bombasNovas);

        morteEm = 0;
        invulneravel = Config.getInt("continue.ticksInvulneravel", 180);

        travado = false;
    }

    /**
     * O efeito do item FULL: tudo no maximo, de uma vez.
     *
     * Nivel maximo destrava TODOS os tipos de tiro (leque cheio,
     * ponteiros e ricochetes) porque a condicao deles e `level >= N` —
     * ver atirar(). Nao ha nada extra a ligar aqui.
     */
    public void encherPoderes() {

        level = nivelMaximo;
        xp = 0;

        bombas = Math.max(bombas, Config.getInt("continue.bombasCheias", 3));
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        // A AURA DO PAIOLA vem antes de tudo: ela e um halo ATRAS do
        // personagem, e desenhada depois ficaria por cima do rosto.
        //
        // Fica fora do "pisca quando invulneravel" de proposito. Enquanto
        // pisca voce nao ve o proprio personagem — e justamente quando
        // mais precisa saber que tem bala vindo.
        desenharAuraDeOrientacao(g);

        // A janela de deathbomb e desenhada ANTES do piscar, senao ela
        // sumiria justamente nos frames em que o jogador precisa ve-la.
        if (morteEm > 0) {
            desenharJanelaDeDeathbomb(g);
        }

        // Pisca enquanto invulneravel: some em metade dos frames.
        if (invulneravel > 0 && (invulneravel / 4) % 2 == 0) {
            return;
        }

        // O circulo de coleta so aparece em modo debug (F3). Ele e uma
        // ferramenta de tuning, nao informacao de jogo: em cena ele
        // competia por atencao com as balas e nao dizia nada que o
        // jogador precisasse saber no momento de desviar.
        if (Main.debugMode) {
            g.setColor(new Color(60, 120, 255, 90));
            g.drawOval((int) (x - raioColeta), (int) (y - raioColeta),
                       (int) (raioColeta * 2), (int) (raioColeta * 2));
        }

        // NO FOCO o corpo fica translucido e pulsando.
        //
        // Serve pra alma poder ser lida: ela e um ponto de 2 px no meio de
        // um retrato de 60, e competindo com a cara inteira em opacidade
        // total ela sumia. Com o corpo desbotado, o olho vai direto no
        // unico ponto que importa pra desviar. E o pulso avisa, sem
        // legenda, que voce esta num modo diferente.
        java.awt.Composite composto = g.getComposite();

        if (Main.x) {

            double pulso = 0.5 + 0.5 * Math.sin(tAlma * 0.13);

            float alpha = (float) (Config.getDouble("jogador.opacidadeNoFocoMin", 0.30)
                    + (Config.getDouble("jogador.opacidadeNoFocoMax", 0.62)
                     - Config.getDouble("jogador.opacidadeNoFocoMin", 0.30)) * pulso);

            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, alpha));
        }

        if (armadura) {
            desenharArmadura(g);
        }

        renderRetrato(g);

        // A alma vem DEPOIS de devolver a opacidade cheia: ela nunca
        // desbota, e justamente por isso salta do corpo apagado.
        g.setComposite(composto);

        desenharAlma(g);
    }

    /**
     * A armadura de energia: dois aneis hexagonais girando em sentidos
     * opostos em volta do estudante.
     *
     * Hexagono e nao circulo porque circulo ja e a linguagem das balas e
     * da hitbox — precisava de uma forma que o olho nao confundisse com
     * "coisa que machuca". Os dois sentidos de giro dao a sensacao de
     * campo de forca sem custar nada alem de dois drawPolygon.
     *
     * Ela e desenhada ATRAS do retrato: na frente, esconderia o sprite.
     */
    private void desenharArmadura(Graphics2D g) {

        double raioBase = tamanhoSprite * 0.5 * 0.62;

        java.awt.Stroke anterior = g.getStroke();
        g.setStroke(new java.awt.BasicStroke(1.6f));

        for (int anel = 0; anel < 2; anel++) {

            // Um gira pra um lado, o outro pro contrario.
            double giro = tAlma * (anel == 0 ? 0.014 : -0.020);
            double r = raioBase * (anel == 0 ? 1.0 : 0.78);

            // Respira devagar, pra nao virar um adesivo parado na tela.
            r *= 1 + 0.05 * Math.sin(tAlma * 0.05 + anel);

            int[] px = new int[6];
            int[] py = new int[6];

            for (int i = 0; i < 6; i++) {
                double a = giro + i * Math.PI / 3;
                px[i] = (int) (x + Math.cos(a) * r);
                py[i] = (int) (y + Math.sin(a) * r);
            }

            g.setColor(new Color(120, 200, 255, anel == 0 ? 95 : 60));
            g.drawPolygon(px, py, 6);
        }

        g.setStroke(anterior);
    }

    /**
     * A ALMA: o pontinho que E a hitbox de verdade.
     *
     * So aparece com o foco segurado (X ou SHIFT), como na serie. O
     * motivo nao e estetico: mostrar a hitbox o tempo todo entrega a
     * resposta de graca e tira o peso do foco, que deixa de ser uma
     * escolha ("ando devagar em troca de enxergar exatamente onde estou")
     * pra virar um botao de andar devagar.
     *
     * O brilho e feito com tres circulos concentricos de alpha
     * decrescente. Nao e desfoque de verdade — Graphics2D nao tem um
     * barato — mas a olho nu, num ponto de 3 px de raio, o resultado e o
     * mesmo e custa tres fillOval.
     */
    private void desenharAlma(Graphics2D g) {

        if (!Main.x) {
            return;
        }

        double pulso = 1 + 0.16 * Math.sin(tAlma * 0.16);

        // "MAIOR SEM SER MAIOR": tudo o que cresce aqui e HALO — brilho,
        // anel e faiscas. O nucleo solido continua exatamente do tamanho
        // da hitbox de verdade (radius).
        //
        // Essa separacao nao e frescura: se o desenho CHEIO fosse maior
        // que a colisao, o jogador aprenderia a desviar de uma bolinha
        // que nao existe, e todo ajuste de raioHitbox viraria mentira. Com
        // halo grande e nucleo verdadeiro, a alma ocupa a tela e continua
        // dizendo a verdade sobre onde ela termina.
        double raioHalo = radius * Config.getDouble("jogador.escalaDoHaloDaAlma", 4.2) * pulso;

        // 1) brilho em camadas, do mais largo e fraco ao mais apertado.
        for (int i = 4; i >= 1; i--) {

            double r = raioHalo * i / 4.0;

            g.setColor(new Color(168, 92, 255, 20 + (4 - i) * 16));
            g.fillOval((int) (x - r), (int) (y - r), (int) (r * 2), (int) (r * 2));
        }

        // 2) anel externo girando: e o detalhe que faz ela parecer uma
        // ALMA e nao um pontinho de mira.
        java.awt.Stroke anterior = g.getStroke();
        g.setStroke(new java.awt.BasicStroke(1.4f));

        g.setColor(new Color(210, 150, 255, 150));
        g.drawOval((int) (x - raioHalo * 0.62), (int) (y - raioHalo * 0.62),
                   (int) (raioHalo * 1.24), (int) (raioHalo * 1.24));

        // 3) quatro faiscas nas pontas do anel, girando devagar.
        double giro = tAlma * 0.035;

        for (int i = 0; i < 4; i++) {

            double a = giro + i * Math.PI / 2;

            double fx = x + Math.cos(a) * raioHalo * 0.62;
            double fy = y + Math.sin(a) * raioHalo * 0.62;

            double rf = raioHalo * 0.16;

            g.setColor(new Color(235, 195, 255, 210));
            g.fillOval((int) (fx - rf), (int) (fy - rf), (int) (rf * 2), (int) (rf * 2));
        }

        g.setStroke(anterior);

        // 4) NUCLEO — do tamanho real da hitbox, e nada alem disso.
        g.setColor(new Color(150, 60, 235));
        g.fillOval((int) (x - radius), (int) (y - radius),
                   (int) (radius * 2), (int) (radius * 2));

        g.setColor(new Color(245, 230, 255));
        g.fillOval((int) (x - radius * 0.5), (int) (y - radius * 0.5),
                   (int) (radius), (int) (radius));
    }

    /**
     * O aviso da janela de deathbomb: um anel que fecha em cima do
     * jogador e um "V!" piscando.
     *
     * O anel FECHANDO (e nao abrindo) mostra quanto tempo resta sem
     * precisar de numero na tela — e um relogio, so que centrado em quem
     * importa. Doze frames e um quinto de segundo: se o aviso nao estiver
     * exatamente em cima do jogador, ninguem ve.
     */
    private void desenharJanelaDeDeathbomb(Graphics2D g) {

        double frac = morteEm / (double) Math.max(1, ticksDeathbomb);
        int r = (int) (18 + 44 * frac);

        java.awt.Stroke anterior = g.getStroke();
        g.setStroke(new java.awt.BasicStroke(3f));

        boolean temSaida = armadura && bombas > 0 && !travado;

        g.setColor(temSaida ? new Color(255, 240, 120) : new Color(255, 80, 80));
        g.drawOval((int) (x - r), (int) (y - r), r * 2, r * 2);

        g.setStroke(anterior);

        // So oferece a tecla se ela de fato salva. Travado (maquina de
        // Turing) a bomba esta desligada, entao anunciar ela seria mentira.
        if (temSaida && (morteEm / 2) % 2 == 0) {

            g.setFont(new Font("Monospaced", Font.BOLD, 20));
            g.setColor(new Color(255, 245, 150));
            g.drawString("V!", (int) (x + r + 4), (int) (y - r));
        }
    }

    /**
     * Retrato do jogador (sprites/player/estudante.png), centrado na
     * hitbox e desenhado com 'tamanhoSprite' pixels de lado. A ARTE nao
     * tem nada a ver com a area de colisao real (radius): a hitbox pode
     * ser minuscula e o retrato continuar grande, que e exatamente como
     * a serie faz.
     * Sem o PNG, cai num circulo branco simples.
     */
    private void renderRetrato(Graphics2D g) {

        // Com a armadura ligada, o retrato passa a ser o expansivo — e o
        // mesmo sprite usado nas cutscenes depois do "ESPANDAAAAA", entao
        // a troca e coerente com o que o jogador acabou de ver.
        BufferedImage img = Assets.get(armadura
                ? Config.getString("jogador.spriteExpansivo", "sprites/player/estudante_expansivo.png")
                : Config.getString("jogador.sprite", "sprites/player/estudante.png"));

        if (img == null) {
            g.setColor(Color.WHITE);
            g.fillOval((int) (x - 16), (int) (y - 16), 32, 32);
            return;
        }

        // CORRECAO DE ENQUADRAMENTO.
        //
        // O estudante_expansivo.png tem EXATAMENTE o mesmo rosto do
        // estudante.png (312x372 px nos dois), so que com a aura
        // desenhada em volta — o PNG e maior, mas o rosto ocupa so ~65%
        // do quadro. Encaixando os dois na mesma caixa, o expansivo
        // parece ter encolhido, quando na verdade so ganhou moldura.
        //
        // Entao ele e desenhado maior por um fator que devolve o rosto ao
        // tamanho original. A aura passa a extrapolar a caixa, que e
        // exatamente o que se quer: a armadura tem que parecer maior que
        // o cara sem armadura.
        double correcao = armadura
                        ? Config.getDouble("jogador.escalaDoExpansivo", 1.53)
                        : 1.0;

        int lado = (int) (tamanhoSprite * correcao);

        // ALINHAMENTO DO ROSTO.
        //
        // Desenhar o PNG centrado na posicao do jogador so acerta se o
        // rosto estiver no meio do arquivo — e no expansivo ele NAO esta:
        // medindo os pixels nao-magenta, o centro do rosto fica a 44,3%
        // da largura em vez de 50%. Resultado: a alma, que e desenhada na
        // posicao real, caia no olho esquerdo dele em vez do meio da cara.
        //
        // Entao o desenho e ancorado pelo CENTRO DO ROSTO, e nao pelo
        // centro da imagem.
        double centroX = armadura
                ? Config.getDouble("jogador.spriteExpansivo.centroX", 0.443)
                : Config.getDouble("jogador.sprite.centroX", 0.5);

        double centroY = armadura
                ? Config.getDouble("jogador.spriteExpansivo.centroY", 0.486)
                : Config.getDouble("jogador.sprite.centroY", 0.5);

        int x0 = (int) (x - lado * centroX);
        int y0 = (int) (y - lado * centroY);

        // Anel fino: ajuda a separar o retrato do fundo escuro da fase.
        g.setColor(new Color(255, 255, 255, 160));
        g.drawOval(x0 - 1, y0 - 1, lado + 2, lado + 2);

        g.drawImage(img, x0, y0, lado, lado, null);
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /** > 0 enquanto a janela de deathbomb esta aberta. */
    public int getMorteEm() {
        return morteEm;
    }

    public int getTicksDeathbomb() {
        return ticksDeathbomb;
    }

    public int getGraze() {
        return graze;
    }

    /** Altura absoluta da linha de coleta total, pro HUD desenhar ela. */
    public double getLinhaDeColetaTotal() {
        return Main.CAMPO_Y + Main.CAMPO_H * alturaColetaTotal;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    /**
     * Quantos itens faltam pro proximo nivel.
     *
     *     custo = xpBase * fatorXp ^ (nivel - 1)
     *
     * Com xpBase 12 e fator 2.4:  N2=12  N3=29  N4=69  N5=166  N6=398
     * (cada inimigo dropa 2 itens, entao N2 sai com 6 inimigos e N6 exige
     * umas 340 mortes acumuladas — de propósito, pro leque de tiro nao
     * disparar nos primeiros segundos da fase).
     */
    public int getXpParaProximoNivel() {
        return (int) Math.round(xpBase * Math.pow(fatorXp, level - 1));
    }

    /** true se o jogador ja chegou no teto de nivel. */
    public boolean isNivelMaximo() {
        return level >= nivelMaximo;
    }

    /**
     * true se os ITENS nao levam mais a lugar nenhum.
     *
     * Serve pro HUD avisar "daqui pra cima e com o Perea" em vez de
     * mostrar uma barra de XP que nunca enche — que era exatamente como
     * ficava quando eu abaixei o teto sem mexer no HUD.
     */
    public boolean isNoTetoDosItens() {
        return level >= tetoPorItem;
    }

    public int getTetoPorItem() {
        return tetoPorItem;
    }

    public int getMoedas() {
        return moedas;
    }

    public boolean temCulturaMaker() {
        return culturaMaker;
    }

    public void setCulturaMaker(boolean culturaMaker) {
        this.culturaMaker = culturaMaker;
    }

    public int getUsosDoOlhoLaser() {
        return usosDoOlhoLaser;
    }

    public void darUsosDoOlhoLaser(int quantos) {
        this.usosDoOlhoLaser += quantos;
    }

    public int getUsosDaAgricultura() {
        return usosDaAgricultura;
    }

    public void darUsosDaAgricultura(int quantos) {
        this.usosDaAgricultura += quantos;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getPontuacao() {
        return pontuacao;
    }

    public void setPontuacao(int pontuacao) {
        this.pontuacao = pontuacao;
    }

    public int getVidas() {
        return vidas;
    }

    public void setVidas(int vidas) {
        this.vidas = vidas;
    }

    public int getBombas() {
        return bombas;
    }

    public void setBombas(int bombas) {
        this.bombas = bombas;
    }

    public int getInvulneravel() {
        return invulneravel;
    }

    public void setInvulneravel(int invulneravel) {
        this.invulneravel = invulneravel;
    }

    public int getShootTime() {
        return shootTime;
    }

    public void setShootTime(int shootTime) {
        this.shootTime = shootTime;
    }

    public double getShootRad() {
        return shootRad;
    }

    public void setShootRad(double shootRad) {
        this.shootRad = shootRad;
    }

    /** true depois do "ESPANDAAAAA" (fim do estagio 1). */
    public boolean temArmadura() {
        return armadura;
    }

    /**
     * Liga a armadura. Chamado pela fase na virada pro estagio 2.
     *
     * Idempotente de proposito: a fase chama isso todo tick do estagio 2
     * e nao deve tocar o som nem re-anunciar nada quando ja esta ligada.
     */
    public void ganharArmadura() {

        if (armadura) {
            return;
        }

        armadura = true;

        // ROXO: a cor da alma e do Santo Java — o poder que vem DELE, em
        // oposicao ao vermelho da corrupcao que vem dos chefes.
        Explosao.roxa(x, y);

        Som.tocar(Som.SUBIR_NIVEL);
    }

    public boolean isTravado() {
        return travado;
    }

    /** Trava/destrava o movimento. Quem trava e responsavel por destravar. */
    public void setTravado(boolean travado) {
        this.travado = travado;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getRaioColeta() {
        return raioColeta;
    }

    public double getTamanhoSprite() {
        return tamanhoSprite;
    }

    public void setTamanhoSprite(double tamanhoSprite) {
        this.tamanhoSprite = tamanhoSprite;
    }

    public int getNivelMaximo() {
        return nivelMaximo;
    }

    public void setNivelMaximo(int nivelMaximo) {
        this.nivelMaximo = nivelMaximo;
    }

    public boolean isAutofire() {
        return autofire;
    }

    public void setAutofire(boolean autofire) {
        this.autofire = autofire;
    }
}
