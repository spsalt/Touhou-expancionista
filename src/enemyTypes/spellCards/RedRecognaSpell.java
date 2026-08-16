package src.enemyTypes.spellCards;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.SolBullet;
import src.bulletTypes.NotaBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD - "☀ RED RECOGNA"
 *
 * O sol do PAPA IA, depois dos Seguidores do IEEE.
 *
 * Um simbolo cresce brilhando no meio do campo, PUXA o jogador pra dentro
 * dele, e anel atras de anel de bala vermelha CAI de fora pra ser comido
 * por ele. E a leitura do Subterranean Sun da Utsuho, com o nome que o
 * Clayton ja usava no roteiro ("#focoforçaefé #Spark #recognas", linha 52).
 *
 * TRES TENTATIVAS ATE ENTENDER O ATAQUE
 * -------------------------------------
 * As duas primeiras versoes cuspiam bala PRA FORA do centro, e as duas
 * foram reclamadas com a mesma frase: "e so ficar parado num lugar certo".
 * Eu respondi as duas mexendo nas balas — dobrei a densidade, torci a
 * trajetoria, fechei uma rosquinha segura que existia no meio. Melhorou de
 * pouquinho em pouquinho e o problema ficou.
 *
 * Ele ficou porque nao era ajuste, era geometria. Num anel que se afasta,
 * a distancia entre duas balas vizinhas vale raio x passo angular: ela
 * CRESCE junto com o raio. La embaixo, onde o jogador fica, sempre ia
 * sobrar espaco entre elas, e um vao entre dois raios que divergem nunca
 * mais fecha. Achar esse vao e um exercicio que se faz uma vez.
 *
 * Fui ler como o ataque original resolve. Ele nao resolve pelas balas:
 *
 *   1. O SOL ATRAI o personagem (ver puxarJogadorParaOSol). "Ficar parado"
 *      deixa de ser uma jogada possivel — nao porque o lugar seguro sumiu,
 *      mas porque nao da mais pra ficar em lugar nenhum.
 *
 *   2. AS BALAS CAEM PRA DENTRO e sao consumidas pelo sol (ver SolBullet).
 *      A mesma conta de antes passa a trabalhar a favor: o raio diminui,
 *      entao o vao entre duas vizinhas FECHA sozinho. Parar num buraco
 *      ganha prazo de validade.
 *
 *   3. O SOL CRESCE PELO DANO, nao so pelo relogio, e o crescimento
 *      aumenta o puxao, a boca que queima e a abertura da espiral.
 *
 * Medido depois: 0 posicoes 100% seguras entre 418 varridas (antes eram
 * 61), e um bot que anda continua passando sem morrer. Ou seja, ficou
 * dificil pelo motivo certo — exige jogar, nao exige sorte.
 *
 * O QUE SOBROU DA VERSAO ANTIGA
 * -----------------------------
 * A regra dos aneis: N balas por anel, um anel a cada X ticks, e o angulo
 * inicial de cada anel avanca um PASSO FIXO.
 *
 * O que faz o padrao funcionar e o passo ser IRRACIONAL em relacao a
 * volta completa. Se o passo fosse, digamos, um doze avos de volta, os
 * aneis se alinhariam a cada doze levas e o campo viraria doze corredores
 * retos e permanentes — trivial. Com um passo incomensuravel, dois aneis
 * nunca coincidem, os vaos escorregam continuamente, e o jogador nao pode
 * decorar uma posicao: ele tem que ler o vao que esta chegando AGORA.
 *
 * O passo padrao daqui e o angulo aureo (~2,39965 rad), o mesmo que a
 * natureza usa pra empacotar semente de girassol sem sobrepor nenhuma. E
 * literalmente o arranjo mais anti-alinhamento que existe.
 *
 * AS BALAS SAO GRANDES E VEM DE LONGE. Bala rapida obriga reacao; bala
 * grande vinda de fora obriga PLANEJAMENTO — voce ve a parede inteira
 * descendo e tem que escolher, com antecedencia, por qual fresta vai sair,
 * sabendo que a fresta esta se fechando enquanto voce decide. E por isso
 * que o simbolo no meio pode crescer tanto sem ser injusto: ele nao
 * esconde nada que voce ainda precise ver.
 */
public class RedRecognaSpell extends SpellCard {

    /* --- os aneis --- */

    private final int balasPorAnel;
    private final int intervaloEntreAneis;
    private final double passoAngular;
    private final double velocidadeDaBala;
    private final double raioDaBala;

    /** De onde as balas nascem, medido do centro do simbolo. */
    private final double raioDeNascimento;

    /* --- o simbolo --- */

    private final double raioMaximoDoSimbolo;
    private final int ticksParaCrescer;

    /** Onde o sol fica. Nao e a posicao do chefe: e o centro do campo. */
    private double centroX, centroY;

    /** Angulo do proximo anel. Avanca o passo a cada leva. */
    private double angulo = 0;

    /** Quanto do crescimento ja passou, de 0 a 1. */
    private double crescimento = 0;

    /** Giro proprio do simbolo, so visual. */
    private double giro = 0;

    /** Ticks ate o sol poder queimar de novo. Ver queimarQuemEncostar. */
    private int cooldownDoDano = 0;

    /* --- as notas brancas --- */

    private final int intervaloDasNotas;
    private final int notasPorLeva;
    private final double velocidadeDasNotas;
    private final double curvaturaDasNotas;
    private final double passoDasNotas;
    private final double raioDaNota;

    /** Angulo da proxima leva de notas. Anda ao CONTRARIO dos aneis. */
    private double anguloDasNotas = 0;

    /**
     * Sobra da versao em que a bala saia do centro com a velocidade
     * girada. Hoje quem abre a espiral e a velocidadeLateral do SolBullet;
     * este campo continua lido do config so pra a chave nao virar orfa
     * antes de eu ter certeza de que a nova versao ficou boa.
     */
    private final double inclinacao;

    /** Contador de aneis, so pra alternar o lado da inclinacao. */
    private int aneisSoltos = 0;

    public RedRecognaSpell() {

        super("☀  RED RECOGNA",
              Config.getDouble("papa.recogna.hp", 900),
              Config.getInt("papa.recogna.duracao", 2400));

        this.balasPorAnel        = Math.max(3, Config.getInt("papa.recogna.balasPorAnel", 26));
        this.intervaloEntreAneis = Math.max(2, Config.getInt("papa.recogna.intervaloEntreAneis", 9));
        this.passoAngular        = Config.getDouble("papa.recogna.passoAngular", 2.39996);
        this.velocidadeDaBala    = Config.getDouble("papa.recogna.velocidadeDaBala", 1.35);
        this.raioDaBala          = Config.getDouble("papa.recogna.raioDaBala", 9.0);
        this.raioDeNascimento    = Config.getDouble("papa.recogna.raioDeNascimento", 70);

        this.raioMaximoDoSimbolo = Config.getDouble("papa.recogna.raioDoSimbolo", 130);
        this.ticksParaCrescer    = Math.max(1, Config.getInt("papa.recogna.ticksParaCrescer", 420));

        this.intervaloDasNotas  = Math.max(10, Config.getInt("papa.recogna.intervaloDasNotas", 46));
        this.notasPorLeva       = Math.max(1, Config.getInt("papa.recogna.notasPorLeva", 5));
        this.velocidadeDasNotas = Config.getDouble("papa.recogna.velocidadeDasNotas", 1.15);
        this.curvaturaDasNotas  = Config.getDouble("papa.recogna.curvaturaDasNotas", 0.026);
        this.passoDasNotas      = Config.getDouble("papa.recogna.passoDasNotas", 0.55);
        this.raioDaNota         = Config.getDouble("papa.recogna.raioDaNota", 8.5);
        this.inclinacao         = Config.getDouble("papa.recogna.inclinacao", 0.55);
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        // O SOL FICA NO CENTRO DO CAMPO, e nao em cima do chefe.
        //
        // O chefe deriva de um lado pro outro; um sol preso nele arrastaria
        // o centro dos aneis junto, e aneis com centros diferentes se
        // cruzam — os corredores fechariam sozinhos em lugares que o
        // jogador nao teria como prever. Foi exatamente o defeito que as
        // Somas de Riemann da Adriana tiveram, e a correcao la foi a
        // mesma: plantar a origem do padrao.
        centroX = Main.CAMPO_X + Main.CAMPO_W / 2.0;
        centroY = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("papa.recogna.alturaRelY", 0.34);

        angulo = 0;
        anguloDasNotas = 0;
        aneisSoltos = 0;
        crescimento = 0;
        giro = 0;

        // O PAPA VAI PRO MEIO DO SOL E FICA LA.
        //
        // Ele E o sol: o RED RECOGNA sai dele, nao de um ponto qualquer do
        // campo. Com o chefe derivando la em cima e os aneis nascendo no
        // meio, eram duas coisas soltas acontecendo ao mesmo tempo — e
        // pior, mirar nele obrigava o jogador a tirar o olho do padrao.
        //
        // Agora o alvo e o centro do padrao, que e onde o olho ja esta.
        // O sprite fica ATRAS do sol (a coroa e desenhada por cima dele,
        // ver o render do BossEnemy) e aparece por entre as camadas
        // translucidas: da pra ver que tem alguem ali dentro.
        chefe.setX(centroX);
        chefe.setY(centroY);
        chefe.setParadoNoLugar(true);

        Som.tocar(Som.OPF_VEREDITO);
    }

    /**
     * O FIM DA LUTA: o sol arrebenta.
     *
     * Este e o ultimo spell card do ultimo chefe, entao "encerrar" aqui
     * quer dizer que o jogo acabou. Uma unica explosao no chefe seria a
     * mesma coisa que qualquer outra morte da fase — e este momento nao e
     * qualquer outro.
     *
     * Sao varios estouros ESCALONADOS: um no centro do sol, um no chefe e
     * mais alguns em anel em volta. Escalonados no espaco e nao no tempo
     * porque a Explosao ja tem vida propria de 78 ticks; disparados juntos
     * em pontos diferentes, eles se sobrepoem e leem como UM estouro
     * grande e irregular, que e o que se quer. Um so, centralizado, leria
     * como um circulo — bonito e pequeno.
     */
    @Override
    public void encerrar(BossEnemy chefe) {

        chefe.setParadoNoLugar(false);


        src.Explosao.vermelha(centroX, centroY);
        src.Explosao.vermelha(chefe.getX(), chefe.getY());

        int quantos = Math.max(0, Config.getInt("papa.recogna.estourosDoFim", 5));

        for (int i = 0; i < quantos; i++) {

            double a = 2 * Math.PI * i / Math.max(1, quantos);
            double r = raioMaximoDoSimbolo * 0.85;

            src.Explosao.vermelha(centroX + Math.cos(a) * r,
                                  centroY + Math.sin(a) * r);
        }

        Som.tocar(Som.CHEFE_MORRE);
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        // Reancorado TODO TICK: o mover() do BossEnemy escreve o x dele a
        // partir da senoide de deriva, entao mandar ele pro centro uma vez
        // so no iniciar() nao segura — no frame seguinte ele voltaria pra
        // formula. Plantar aqui e o unico jeito que sobrevive ao dono do
        // movimento ser outra classe.
        chefe.setX(centroX);
        chefe.setY(centroY);

        // O SOL CRESCE PELO DANO, e nao so pelo relogio.
        //
        // A versao anterior inchava sozinha em 420 ticks fizesse o jogador
        // o que fizesse. Agora o crescimento e o MAIOR entre o tempo e o
        // quanto ele ja tirou de HP: quem atira ve o sol abrir mais rapido
        // e o ataque ficar mais bravo, quem so foge chega no mesmo lugar
        // devagar. As duas maneiras de jogar levam ao clímax; so que uma
        // delas e escolha e a outra e espera.
        double porDano  = 1 - chefe.getFracaoDeHpDoSpell();
        double porTempo = t / (double) ticksParaCrescer;

        crescimento = Math.min(1.0, Math.max(porTempo, porDano));

        giro += Config.getDouble("papa.recogna.velocidadeDoGiro", 0.011);

        puxarJogadorParaOSol();

        queimarQuemEncostar();

        if (t % intervaloDasNotas == 0 && t > 0) {
            soltarNotas();
        }

        if (t % intervaloEntreAneis != 0) {
            return;
        }

        // Um som a cada varios aneis: a cadencia e alta e um efeito por
        // leva viraria um chiado continuo por cima da musica.
        if ((t / intervaloEntreAneis) % 4 == 0) {
            Som.tocar(Som.ADRIANA_GLIFO);
        }

        soltarAnel();

        angulo += passoAngular;
    }

    /**
     * O SOL QUEIMA QUEM ENCOSTA NELE.
     *
     * Sem isto o ataque tinha um buraco enorme: as balas nascem na BORDA
     * do simbolo e vao pra fora, entao o miolo era uma sala segura
     * permanente. Dava pra sentar em cima do R e esperar o cronometro
     * zerar sem tomar um arranhao — o padrao mais bonito do jogo virava
     * decoracao.
     *
     * A area que queima e menor que o desenho (fatorDeDano) porque o
     * desenho tem coroa e brilho, e cobrar dano pelo brilho seria injusto:
     * o jogador julga distancia pelo NUCLEO, que e a parte solida.
     *
     * E ela CRESCE junto com o sol. Isso da sentido mecanico ao
     * crescimento, que antes era so estetica: o campo seguro vai
     * encolhendo, e ficar perto do centro deixa de ser uma opcao
     * conforme o ataque avanca.
     */
    /**
     * O SOL PUXA O JOGADOR PRA DENTRO DELE.
     *
     * ESTA E A CORRECAO DE VERDADE, e eu demorei tres rodadas pra chegar
     * nela porque estava olhando pro lugar errado.
     *
     * Voce reclamou duas vezes que dava pra "ficar parado num lugar certo".
     * Eu respondi as duas com mais bala: dobrei a densidade, torci a
     * trajetoria, fechei a rosquinha do meio. Melhorou de pouquinho em
     * pouquinho e o problema continuou, porque num padrao radial que sai do
     * centro o ponto seguro nao e um descuido de ajuste — ele e uma
     * CONSEQUENCIA. Sempre vai existir algum lugar onde as balas passam
     * longe, e achar esse lugar e um exercicio que o jogador faz uma vez e
     * nunca mais precisa repetir.
     *
     * Fui ler como o ataque original resolve isso. Ele nao resolve pelas
     * balas: o sol ATRAI o personagem. Com atracao, "ficar parado" deixa de
     * ser uma jogada possivel — nao porque o ponto seguro sumiu, mas porque
     * voce nao consegue mais ficar em ponto nenhum. A resposta certa e
     * andar o tempo todo, e ai o padrao volta a ser sobre desviar.
     *
     * O PUXAO NAO E UMA ARMADILHA. Ele vai no maximo a uns 0,9 px por tick,
     * contra 1,75 do modo foco e 4,0 do movimento normal: da pra vencer
     * ele andando, sempre, ate no foco. Ele nao tira o controle do jogador,
     * tira a opcao de nao fazer nada — que era o problema inteiro.
     *
     * A forca cresce PERTO DO SOL e some longe. Assim as beiradas do campo
     * continuam sendo um lugar onde da pra respirar (e o jogador precisa de
     * um), e a aproximacao vai ficando cada vez mais cara.
     */
    private void puxarJogadorParaOSol() {

        if (Main.player == null) {
            return;
        }

        double dx = centroX - Main.player.getX();
        double dy = centroY - Main.player.getY();

        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1) {
            return;
        }

        double alcance = Config.getDouble("papa.recogna.alcanceDoPuxao", 430);

        if (dist >= alcance) {
            return;
        }

        // 1 encostado no sol, 0 na borda do alcance. Ao QUADRADO pra a
        // queda ser sentida: linear, a diferenca entre estar perto e estar
        // longe ficava sutil demais pra o jogador perceber que se
        // aproximar tem preco.
        double proximidade = 1 - dist / alcance;
        proximidade *= proximidade;

        double forca = Config.getDouble("papa.recogna.forcaDoPuxao", 0.92)
                     * proximidade
                     * (0.45 + 0.55 * suavizar(crescimento));

        Main.player.puxar(dx / dist * forca, dy / dist * forca);
    }

    private void queimarQuemEncostar() {

        if (Main.player == null) {
            return;
        }

        if (cooldownDoDano > 0) {
            cooldownDoDano--;
            return;
        }

        // O FOGO VAI ATE ONDE AS BALAS NASCEM. Nem um pixel a menos.
        //
        // Aqui estava o furo que tornava o ataque trivial, e nao era o
        // padrao: era GEOMETRIA. O fogo cobria 47 px do centro e as balas
        // nasciam a partir de 70 px, indo PRA FORA. Sobrava uma
        // ROSQUINHA entre os dois — fora do fogo, dentro do ponto de
        // nascimento — onde nenhuma bala passava nunca. Bastava parar ali
        // e esperar o cronometro: 61 pontos do campo eram 100% seguros
        // por 40 segundos, medidos.
        //
        // Amarrar o raio do fogo ao raioDeNascimento fecha a rosquinha e
        // se mantem fechado sozinho: mexer em um move o outro junto. Com
        // um fator solto, qualquer ajuste futuro reabriria o buraco sem
        // ninguem perceber.
        double raio = raioDeConsumo() + Config.getDouble("papa.recogna.folgaDoFogo", 6);

        double dist = Main.getDist(centroX, centroY,
                                   Main.player.getX(), Main.player.getY());

        if (dist <= raio + Main.player.getRadius()) {

            Main.player.levarDano();

            // O mesmo intervalo da pagina do LaTeX, e pelo mesmo motivo:
            // isto e uma AREA e nao uma bala. Sem intervalo, encostar
            // custaria uma vida por frame — sessenta por segundo.
            cooldownDoDano = Config.getInt("papa.recogna.intervaloDeDano", 30);
        }
    }

    /**
     * AS NOTAS: balas brancas de contorno preto, em ESPIRAL LENTA.
     *
     * As marcas do Recogna vinham pelas laterais miradas no jogador, e
     * juntas com os aneis o ataque ficou impossivel — nao por serem
     * muitas, mas porque as duas coisas cobravam a MESMA resposta ao mesmo
     * tempo: sair da linha. Voce fugia do anel pro lado e entrava na
     * marca; fugia da marca e voltava pro anel. Nao havia jogada certa,
     * so sorte.
     *
     * As notas resolvem isso obedecendo a outra regra. Elas saem do sol
     * numa espiral CONTRARIA a rotacao dos aneis e vao devagar, entao em
     * vez de fechar os corredores elas ATRAVESSAM eles — e como sao
     * brancas com contorno preto no meio de uma tela toda vermelha, da pra
     * rastrear cada uma sem confundir com o resto.
     *
     * O jogador continua tendo o corredor do anel pra seguir; o que as
     * notas cobram e nao ficar parado DENTRO dele.
     */
    private void soltarNotas() {

        Som.tocar(Som.PAPA_AVANCA);

        // Espiral no sentido oposto ao dos aneis: e o contraste de sentido
        // que faz o olho separar as duas camadas na hora.
        anguloDasNotas -= passoDasNotas;

        for (int i = 0; i < notasPorLeva; i++) {

            double a = anguloDasNotas + 2 * Math.PI * i / notasPorLeva;

            double r0 = raioDeNascimento * 0.8;

            // Aceleracao TANGENCIAL (perpendicular a direcao de saida):
            // e ela que encurva a trajetoria e faz a nota desenhar um
            // arco em vez de uma reta. Uma bala reta a mais seria so mais
            // uma bala; o arco e o que da a ela um padrao proprio.
            double perpX = -Math.sin(a);
            double perpY = Math.cos(a);

            Main.bullets.add(new NotaBullet(
                centroX + Math.cos(a) * r0,
                centroY + Math.sin(a) * r0,
                Math.cos(a) * velocidadeDasNotas,
                Math.sin(a) * velocidadeDasNotas,
                perpX * curvaturaDasNotas,
                perpY * curvaturaDasNotas,
                raioDaNota
            ));
        }
    }

    /**
     * A BOCA DO SOL: onde ele come as balas e onde ele queima o jogador.
     *
     * Um numero so pras duas coisas, de proposito. Se a bala some num raio
     * e o fogo comeca em outro, sobra uma faixa que ou e uma sala segura
     * escondida (o furo que este ataque ja teve) ou e uma morte sem aviso.
     * Amarrados, os dois se movem juntos pra sempre.
     */
    private double raioDeConsumo() {
        return raioDeNascimento + raioMaximoDoSimbolo * crescimento * 0.55;
    }

    /**
     * De onde as balas caem: FORA do campo.
     *
     * Elas precisam entrar pelas beiradas ja em movimento, e nao aparecer
     * do nada dentro da area de jogo. O canto mais distante do campo em
     * relacao ao centro do sol fica a uns 545 px; nascer alem disso garante
     * que toda bala atravessa a borda vindo de fora, venha ela de que
     * angulo for.
     */
    private double raioDeQueda() {
        return Config.getDouble("papa.recogna.raioDeQueda", 580);
    }

    /**
     * Um anel completo de balas, saindo do simbolo.
     *
     * Elas nascem NA BORDA do sol e nao no centro dele: nascendo no meio,
     * passariam por baixo do desenho e o jogador veria bala aparecendo do
     * nada na beirada, sem ter visto a origem.
     */
    private void soltarAnel() {

        // AS BALAS AGORA VEM DE FORA E CAEM NO SOL.
        //
        // O anel nasce fora do campo e desce espiralando ate ser comido
        // pelo nucleo (ver SolBullet). A conta que decide isso e simples e
        // e a raiz de tudo: a distancia entre duas vizinhas de um mesmo
        // anel vale raio x passoAngular.
        //
        //   SAINDO DO CENTRO  o raio cresce, o vao ABRE. Um buraco
        //                     encontrado la embaixo continua sendo buraco
        //                     pra sempre — e era exatamente o que voce
        //                     estava fazendo.
        //
        //   CAINDO PRA DENTRO o raio diminui, o vao FECHA. Parar num
        //                     buraco passa a ter prazo de validade, porque
        //                     ele se fecha em volta de quem esta la.
        //
        // Junto com o puxao, some o "achei o lugar": o lugar existe por
        // alguns segundos e depois deixa de existir sozinho.
        double r = raioDeQueda();

        // O SENTIDO DA ESPIRAL TROCA A CADA ANEL.
        //
        // Aneis consecutivos girando pro mesmo lado descem paralelos, e
        // paralelo quer dizer corredor: o jogador entra num e desce junto
        // com ele. Alternando, as trajetorias se CRUZAM e cada vao e
        // atravessado pelo anel de tras.
        int sinal = (aneisSoltos % 2 == 0) ? 1 : -1;

        aneisSoltos++;

        // Espiral mais aberta conforme o sol cresce: no fim do ataque a
        // mesma quantidade de bala varre mais campo no caminho de descida.
        double velTangencial = sinal
                             * Config.getDouble("papa.recogna.velocidadeLateral", 1.15)
                             * (0.7 + 0.5 * suavizar(crescimento));

        for (int i = 0; i < balasPorAnel; i++) {

            double a = angulo + 2 * Math.PI * i / balasPorAnel;

            Main.bullets.add(new SolBullet(
                centroX, centroY,
                r, a,
                velocidadeDaBala,
                velTangencial,
                raioDeConsumo(),
                raioDaBala,
                new Color(255, 60, 60)
            ));
        }
    }

    /* =========================
            RENDER
       ========================= */

    /**
     * O sol: nucleo branco incandescente, coroa vermelha e o simbolo
     * RECOGNA girando por dentro.
     *
     * Desenhado do ESCURO pro CLARO, de fora pra dentro. E o mesmo empilhar
     * de circulos translucidos que o Olho Laser usa, e pelo mesmo motivo:
     * um degrade de verdade custaria um Paint por frame, e o empilhamento
     * da a mesma leitura por muito menos.
     */
    @Override
    public void render(Graphics2D g) {

        double raio = raioMaximoDoSimbolo * suavizar(crescimento);

        if (raio < 2) {
            return;
        }

        double pulso = 1 + 0.045 * Math.sin(giro * 6.5);

        desenharCoroa(g, raio * pulso);
        desenharSimbolo(g, raio * pulso);
        desenharBordaQuente(g);
    }

    /**
     * O CONTORNO DO FOGO.
     *
     * Agora que a area quente vai ate onde as balas nascem, ela e bem
     * maior que o desenho do sol — e o jogador nao teria como saber onde
     * ela termina. Um anel tracejado pulsando resolve: a regra passa a ser
     * visivel, e morrer ali vira erro seu e nao surpresa do jogo.
     */
    private void desenharBordaQuente(Graphics2D g) {

        double raio = raioDeConsumo() + Config.getDouble("papa.recogna.folgaDoFogo", 6);

        double pulso = 0.5 + 0.5 * Math.sin(giro * 5);

        Stroke anterior = g.getStroke();

        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                    10f, new float[] { 9f, 11f }, (float) (giro * 30)));

        g.setColor(new Color(255, 170, 120, (int) (80 + 70 * pulso)));
        g.drawOval((int) (centroX - raio), (int) (centroY - raio),
                   (int) (raio * 2), (int) (raio * 2));

        g.setStroke(anterior);
    }

    /** Curva de crescimento: acelera no comeco e assenta no fim. */
    private double suavizar(double f) {
        return 1 - Math.pow(1 - Math.max(0, Math.min(1, f)), 2.2);
    }

    private void desenharCoroa(Graphics2D g, double raio) {

        // Camadas de fora pra dentro: vermelho escuro -> laranja -> branco.
        int camadas = 7;

        for (int i = camadas; i >= 1; i--) {

            double f = i / (double) camadas;
            double r = raio * f;

            int vermelho = 255;
            int verde = (int) (250 - 230 * f);
            int azul  = (int) (235 - 235 * f);
            int alpha = (int) (40 + 150 * (1 - f));

            g.setColor(new Color(vermelho,
                                 Math.max(0, Math.min(255, verde)),
                                 Math.max(0, Math.min(255, azul)),
                                 Math.max(0, Math.min(255, alpha))));

            g.fillOval((int) (centroX - r), (int) (centroY - r), (int) (r * 2), (int) (r * 2));
        }

        // Nucleo branco estourado.
        double nucleo = raio * 0.34;

        g.setColor(new Color(255, 255, 255, 235));
        g.fillOval((int) (centroX - nucleo), (int) (centroY - nucleo),
                   (int) (nucleo * 2), (int) (nucleo * 2));
    }

    /**
     * O SIMBOLO RECOGNA: um anel de nos ligados por arestas, com o R no
     * meio.
     *
     * A forma nao e arbitraria — e a floresta de caminhos otimos do proprio
     * PAPA vista de cima: prototipos em volta, arestas ligando, tudo
     * girando em torno de um centro. O ataque seguinte (o OPF) e essa
     * mesma figura virando mecanica.
     */
    private void desenharSimbolo(Graphics2D g, double raio) {

        // A MARCA DO RECOGNA no miolo: o cerebro com a digital, em brasa.
        //
        // Ela e a arte de verdade do grupo, entao ela manda. O grafo
        // desenhado a mao continua em volta, girando — mas agora como
        // moldura, e nao como o simbolo em si.
        java.awt.image.BufferedImage marca =
                src.Assets.get(Config.getString("papa.recogna.sprite", "sprites/GFX/recogna.png"));

        int nos = Config.getInt("papa.recogna.nosDoSimbolo", 8);
        double rAnel = raio * 0.60;

        if (marca != null) {

            int lado = (int) (raio * Config.getDouble("papa.recogna.escalaDaMarca", 0.92));

            java.awt.image.BufferedImage pronta =
                    src.Assets.getEscalado(Config.getString("papa.recogna.sprite",
                                                            "sprites/GFX/recogna.png"),
                                           lado, lado);

            g.drawImage(pronta != null ? pronta : marca,
                        (int) (centroX - lado / 2.0), (int) (centroY - lado / 2.0),
                        lado, lado, null);
        }

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(2.5f));

        // Arestas: cada no ligado ao seguinte e ao oposto. O cruzamento no
        // meio e o que faz a figura parecer um grafo e nao um relogio.
        for (int i = 0; i < nos; i++) {

            double a1 = giro + 2 * Math.PI * i / nos;
            double a2 = giro + 2 * Math.PI * (i + 1) / nos;
            double a3 = giro + 2 * Math.PI * (i + nos / 2) / nos;

            g.setColor(new Color(255, 235, 200, 150));
            linha(g, a1, a2, rAnel);

            g.setColor(new Color(255, 180, 140, 70));
            linha(g, a1, a3, rAnel);
        }

        for (int i = 0; i < nos; i++) {

            double a = giro + 2 * Math.PI * i / nos;

            double nx = centroX + Math.cos(a) * rAnel;
            double ny = centroY + Math.sin(a) * rAnel;

            double rn = raio * 0.075;

            g.setColor(new Color(255, 245, 220, 240));
            g.fillOval((int) (nx - rn), (int) (ny - rn), (int) (rn * 2), (int) (rn * 2));
        }

        g.setStroke(anterior);

        // Sem a arte, cai no "R" desenhado — o jogo nunca depende de um
        // PNG estar no lugar certo pra rodar.
        if (marca == null) {

            int tamanho = (int) Math.max(10, raio * 0.55);

            g.setFont(new Font("Monospaced", Font.BOLD, tamanho));

            String letra = "R";
            int larg = g.getFontMetrics().stringWidth(letra);
            int alt = g.getFontMetrics().getAscent();

            g.setColor(new Color(150, 20, 20, 230));
            g.drawString(letra, (int) (centroX - larg / 2.0), (int) (centroY + alt / 2.4));
        }
    }

    private void linha(Graphics2D g, double a1, double a2, double r) {
        g.drawLine((int) (centroX + Math.cos(a1) * r), (int) (centroY + Math.sin(a1) * r),
                   (int) (centroX + Math.cos(a2) * r), (int) (centroY + Math.sin(a2) * r));
    }
}
