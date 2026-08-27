package src;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Cena de dialogo/narracao em tela cheia, que trava o jogo ate acabar
 * (nao roda fisica nenhuma enquanto ela esta ativa).
 *
 * Uma cutscene e so uma lista de "falas" (ver a classe Fala) percorridas
 * uma a uma. Z ou ENTER avanca: se o texto ainda esta "digitando" na tela,
 * o primeiro toque so completa a linha na hora; se ja estiver completa, o
 * toque vai pra proxima. ESC pula a cena inteira.
 *
 * Dois formatos de apresentacao:
 *
 *   - NARRACAO SOBRE FOTO: um cenario de fundo (a portaria da UNESP, por
 *     exemplo) com a caixa de texto no rodape. Usado na abertura.
 *
 *   - ESTILO TOUHOU: retratos grandes dos personagens nos dois lados da
 *     tela — quem esta falando aparece aceso e na frente, quem esta calado
 *     fica escurecido e atras. E o que os jogos da serie fazem, e o que
 *     deixa claro de quem e a fala sem precisar ler o nome.
 *
 * A classe e generica de proposito — nao sabe nada sobre a Adriana, o
 * Clayton etc. O CONTEUDO de cada cena mora em metodos fabrica estaticos
 * no fim do arquivo, cada um comentado com as linhas exatas do Roteiro.txt
 * que ele encena.
 */
public class Cutscene {

    /** Estilo de cada linha: muda como ela e desenhada. */
    public enum Tipo {
        /** Cartela cheia de tela: titulo grande + subtitulo. Usada so pra abrir a cena. */
        TITULO,
        /** Texto sem nome de personagem, em italico — narracao. */
        NARRACAO,
        /** Fala de um personagem: nome em destaque acima do texto. */
        FALA
    }

    /**
     * Coisas que uma fala pode mandar o JOGO fazer.
     *
     * Existe porque o dialogo nao para mais o jogo: a cena e o mundo
     * acontecem juntos, e precisam se sincronizar. Sem isso a chefe
     * aparecia no primeiro frame da conversa — inclusive durante a parte
     * do SANTO JAVA, em que ela nem devia estar na tela — e a
     * transformacao acontecia no retrato mas nao no sprite em jogo.
     *
     * Quem consome e a fase (ver phase1.chefeEntraSeChamado).
     */
    public enum Gatilho {
        NENHUM,
        /** A chefe entra em cena agora, voando. */
        CHEFE_ENTRA,
        /** A chefe troca pra forma seguinte agora. */
        CHEFE_TRANSFORMA,
        /**
         * O jogador ganha a armadura AGORA — no "ESPANDAAAAA", e nao
         * antes. Sem isso ele ja entrava no estagio 2 em modo expansivo,
         * com o retrato trocado e a bomba destravada, e a cena inteira do
         * SANTO JAVA virava um anuncio de algo que ja tinha acontecido.
         */
        JOGADOR_GANHA_ARMADURA,
        /**
         * O buff do Paiola liga AGORA, na fala em que ele diz que vai
         * orientar voce a objetos — e nao no fim da cena.
         *
         * Importa porque a linha seguinte e o anuncio do buff: se ele
         * fosse concedido depois, o anuncio estaria descrevendo uma coisa
         * que ainda nao existe, e a aura so acenderia na luta seguinte
         * sem nenhuma ligacao visivel com o que acabou de ser dito.
         */
        JOGADOR_GANHA_ORIENTACAO
    }

    /** De que lado da tela o retrato de um personagem aparece. */
    public enum Lado {
        ESQUERDA,
        DIREITA,
        /** Centro da tela, com brilho — usado pra aparicoes sobrenaturais. */
        CENTRO
    }

    /**
     * Um personagem que pode aparecer na cena (estilo Touhou).
     * Guarda so aparencia: nome, retrato e de que lado fica.
     */
    public static class Personagem {

        final String nome;
        final String sprite;
        final Lado lado;
        final Color cor;

        /** Bleep tocado a cada letra que aparece. Da "voz" ao personagem. */
        final String voz;

        /**
         * Correcao de enquadramento do retrato.
         *
         * 1.0 = desenha na altura padrao. Serve pra arte cujo PERSONAGEM
         * ocupa uma fracao diferente do PNG: o estudante_expansivo tem o
         * mesmo rosto do estudante normal, mas com a aura desenhada em
         * volta, entao no mesmo tamanho de caixa ele parece menor. Este
         * numero devolve o rosto ao tamanho certo.
         *
         * Fica no personagem e nao numa conta automatica porque "o que
         * conta como o personagem" e uma decisao de arte — nao da pra
         * deduzir de alfa nem de bounding box (os dois PNGs sao opacos de
         * ponta a ponta).
         */
        final double escala;

        /**
         * Posicao ANIMADA: 0 = recuado (calado), 1 = a frente (falando).
         *
         * E o que faz o retrato deslizar em vez de teleportar. Fica no
         * Personagem e nao na Fala porque a animacao pertence a quem esta
         * em cena, e precisa sobreviver a troca de falas.
         */
        double avanco = 0;

        public Personagem(String nome, String sprite, Lado lado, Color cor) {
            this(nome, sprite, lado, cor, null, 1.0);
        }

        public Personagem(String nome, String sprite, Lado lado, Color cor, String voz) {
            this(nome, sprite, lado, cor, voz, 1.0);
        }

        public Personagem(String nome, String sprite, Lado lado, Color cor,
                          String voz, double escala) {
            this.nome = nome;
            this.sprite = sprite;
            this.lado = lado;
            this.cor = cor;
            this.voz = voz;
            this.escala = escala;
        }

        public String getNome() {
            return nome;
        }
    }

    /** Uma linha da cena. Imutavel: e so dado, toda a logica fica na Cutscene. */
    public static class Fala {

        final Tipo tipo;
        final Personagem personagem;  // null nas TITULO/NARRACAO
        final String texto;
        final String subtitulo;       // usado so no TITULO

        /** Troca o cenario de fundo a partir desta fala. null = mantem o anterior. */
        final String fundoNovo;

        /**
         * Troca quem esta em cena a partir desta fala. null = mantem.
         *
         * E o que permite um personagem SE TRANSFORMAR no meio da conversa:
         * a Adriana usa o retrato normal ate a fala em que ela vira a forma
         * maligna, e o estudante troca pro sprite expansivo no momento em
         * que ativa o compilador.
         */
        final Personagem[] elencoNovo;

        /** Efeito tocado quando esta fala COMECA. null = nenhum. */
        final String som;

        /** O que esta fala manda o jogo fazer quando COMECA. */
        final Gatilho gatilho;

        /**
         * O que esta fala manda o jogo fazer quando o jogador a DISPENSA.
         *
         * POR QUE EXISTEM OS DOIS.
         *
         * Depende de a coisa acontecer DURANTE a fala ou POR CAUSA dela.
         *
         * A chefe entrando voando (CHEFE_ENTRA) e "durante": ela atravessa
         * a tela enquanto voce le a narracao que anuncia a chegada dela, e
         * as duas coisas juntas sao a cena.
         *
         * A transformacao e "por causa". A Adriana grita a linha dos
         * cachorros E ENTAO vira. Disparando na entrada, a explosao
         * estourava atras da caixa de dialogo — o jogador via o clarao
         * pelas beiradas de um texto que ainda estava sendo escrito, e
         * ainda tinha que apertar ENTER pra a cena destravar depois que a
         * transformacao ja tinha acontecido. A ordem ficava invertida: o
         * efeito antes da fala que o causa.
         */
        final Gatilho gatilhoDeSaida;

        private Fala(Tipo tipo, Personagem personagem, String texto, String subtitulo,
                     String fundoNovo, Personagem[] elencoNovo) {
            this(tipo, personagem, texto, subtitulo, fundoNovo, elencoNovo, null);
        }

        private Fala(Tipo tipo, Personagem personagem, String texto, String subtitulo,
                     String fundoNovo, Personagem[] elencoNovo, String som) {
            this(tipo, personagem, texto, subtitulo, fundoNovo, elencoNovo, som, Gatilho.NENHUM);
        }

        private Fala(Tipo tipo, Personagem personagem, String texto, String subtitulo,
                     String fundoNovo, Personagem[] elencoNovo, String som, Gatilho gatilho) {
            this(tipo, personagem, texto, subtitulo, fundoNovo, elencoNovo, som,
                 gatilho, Gatilho.NENHUM);
        }

        private Fala(Tipo tipo, Personagem personagem, String texto, String subtitulo,
                     String fundoNovo, Personagem[] elencoNovo, String som,
                     Gatilho gatilho, Gatilho gatilhoDeSaida) {
            this.tipo = tipo;
            this.personagem = personagem;
            this.texto = texto;
            this.subtitulo = subtitulo;
            this.fundoNovo = fundoNovo;
            this.elencoNovo = elencoNovo;
            this.som = som;
            this.gatilho = gatilho;
            this.gatilhoDeSaida = gatilhoDeSaida;
        }

        /**
         * Devolve uma copia desta fala com um gatilho pro jogo, disparado
         * quando ela ENTRA.
         *
         * Encadeavel (Fala.fala(...).com(Gatilho.CHEFE_ENTRA)) pra nao
         * precisar de uma fabrica nova pra cada combinacao.
         */
        public Fala com(Gatilho g) {
            return new Fala(tipo, personagem, texto, subtitulo, fundoNovo, elencoNovo, som,
                            g, gatilhoDeSaida);
        }

        /**
         * Igual ao com(), mas o gatilho so dispara quando o jogador
         * DISPENSA esta fala. Ver o campo gatilhoDeSaida.
         */
        public Fala aoSair(Gatilho g) {
            return new Fala(tipo, personagem, texto, subtitulo, fundoNovo, elencoNovo, som,
                            gatilho, g);
        }

        /** Fala que dispara um efeito sonoro (ex: a voiceline do Clayton). */
        public static Fala falaComSom(Personagem quem, String texto, String som) {
            return new Fala(Tipo.FALA, quem, texto, null, null, null, som);
        }

        public static Fala titulo(String texto, String subtitulo) {
            return new Fala(Tipo.TITULO, null, texto, subtitulo, null, null);
        }

        public static Fala narracao(String texto) {
            return new Fala(Tipo.NARRACAO, null, texto, null, null, null);
        }

        /** Narracao que tambem troca o cenario de fundo. */
        public static Fala narracaoComFundo(String texto, String fundo) {
            return new Fala(Tipo.NARRACAO, null, texto, null, fundo, null);
        }

        public static Fala fala(Personagem quem, String texto) {
            return new Fala(Tipo.FALA, quem, texto, null, null, null);
        }

        /** Fala que tambem troca o cenario de fundo. */
        public static Fala falaComFundo(Personagem quem, String texto, String fundo) {
            return new Fala(Tipo.FALA, quem, texto, null, fundo, null);
        }

        /** Fala que tambem troca quem esta em cena (transformacoes). */
        public static Fala falaComElenco(Personagem quem, String texto, Personagem[] elenco) {
            return new Fala(Tipo.FALA, quem, texto, null, null, elenco);
        }

        /** Narracao que troca cenario e elenco de uma vez. */
        public static Fala narracaoComFundoEElenco(String texto, String fundo, Personagem[] elenco) {
            return new Fala(Tipo.NARRACAO, null, texto, null, fundo, elenco);
        }
    }

    private final Fala[] falas;

    /**
     * O quanto a cena ja saiu da frente, de 0 a 1.
     *
     * 1 = veu, retratos e caixa totalmente invisiveis. Sobe durante a
     * cerimonia da armadura e volta a zero depois dela.
     */
    private double saidaDaCena = 0;

    /** Elenco em cena AGORA. Muda quando uma Fala traz elencoNovo. */
    private Personagem[] elenco;

    /** Guardado pra poder voltar ao inicio no reiniciar(). */
    private final Personagem[] elencoInicial;

    /**
     * Cenario pedido pela cena. Nao e desenhado aqui: e repassado pro
     * Main.fundo, que faz a transicao. Guardado so pra o reiniciar()
     * conseguir voltar ao cenario inicial da cena.
     */
    private String fundoAtual;

    /**
     * Gatilho que a fala atual disparou e a fase ainda nao consumiu.
     *
     * Fica pendente ate alguem chamar consumirGatilho(). A fase le isso
     * uma vez por tick, entao nada se perde mesmo que a fala mude no
     * mesmo frame.
     */
    private Gatilho gatilhoPendente = Gatilho.NENHUM;

    /**
     * O gatilho DESTA fala ja foi disparado?
     *
     * Sem esta trava o bug e catastrofico e foi exatamente o que
     * aconteceu: aplicarFundoDaFalaAtual() roda TODO TICK (a troca de
     * cenario e idempotente, entao repetir nao incomodava), e o gatilho
     * pegou carona ali. Resultado: a fase recebia CHEFE_ENTRA sessenta
     * vezes por segundo e criava uma chefe por frame — a tela enchia de
     * Adrianas empilhadas, cada uma cuspindo o proprio padrao.
     *
     * Gatilho e EVENTO, nao estado. Mesma trava do somTocado.
     */
    private boolean gatilhoDisparado = false;

    private int indice = 0;

    /** Quantos caracteres da linha atual ja apareceram (efeito maquina de escrever). */
    private double charsMostrados = 0;

    /** Cronometro da linha atual: usado so pro cursor piscar. */
    private int t = 0;

    /** Bordas de subida pra Z/ENTER/ESC nao repetirem a acao a cada tick. */
    private boolean avancarAnterior = false;
    private boolean escAnterior = false;

    /**
     * A cerimonia da armadura estava rodando no tick passado?
     *
     * Guardado pra detectar os dois INSTANTES da cerimonia (o comeco e o
     * fim), e nao o estado dela — ver o tratamento no tick().
     */
    private boolean ascensaoAnterior = false;

    /**
     * Ticks que a conversa ainda deve ficar FORA DA FRENTE por causa de um
     * efeito em cena (hoje: a explosao da transformacao).
     *
     * Enquanto for maior que zero a caixa some, o Z/ENTER/ESC nao
     * respondem e — se ja nao houver mais fala — a cena tambem nao termina.
     * Ver pausarParaCena().
     */
    private int pausaCenica = 0;

    /** Ja tocou o efeito da fala atual? Evita repetir a cada tick. */
    private boolean somTocado = false;

    private double velocidadeTexto;

    /** Fracao do caminho que o retrato percorre por tick (0.12 = suave). */
    private double suavidadeRetrato;

    /** Guardado pra poder voltar ao inicio no reiniciar(). */
    private final String fundoInicial;

    public Cutscene(Fala[] falas, Personagem[] elenco, String fundoInicial) {
        this.falas = falas;
        this.elencoInicial = (elenco == null) ? new Personagem[0] : elenco;
        this.elenco = this.elencoInicial;
        this.fundoInicial = fundoInicial;
        this.fundoAtual = fundoInicial;
        carregarConfig();
    }

    /** (Re)le os ajustes. Chamado no construtor e no hot-reload (F5). */
    public void carregarConfig() {
        this.velocidadeTexto = Math.max(0.1, Config.getDouble("cutscene.velocidadeTexto", 0.9));
        this.suavidadeRetrato = Math.max(0.01, Math.min(1,
                Config.getDouble("cutscene.suavidadeRetrato", 0.12)));
    }

    /**
     * Pega o gatilho pendente e limpa. NENHUM se nao ha nada a fazer.
     * Ver a enum Gatilho.
     */
    public Gatilho consumirGatilho() {

        Gatilho g = gatilhoPendente;
        gatilhoPendente = Gatilho.NENHUM;

        // AQUI TINHA UM "gatilhoDisparado = false" — e ele era o estrago.
        //
        // A trava do gatilhoDisparado existe justamente pra a fala disparar
        // o pedido UMA vez. Zerar ela aqui rearmava o gatilho toda vez que
        // alguem o consumia, e o ciclo se fechava sozinho: a fase consumia,
        // isto rearmava, o tick seguinte disparava de novo, a fase consumia
        // de novo. Sessenta pedidos por segundo, o tempo inteiro em que
        // aquela fala estivesse na tela.
        //
        // No CHEFE_ENTRA passava despercebido porque o "!chefeSpawnado"
        // barrava as repeticoes. O CHEFE_TRANSFORMA nao tem barreira
        // nenhuma: cada repeticao jogava fora a chefe, criava outra com HP
        // cheio e soltava mais uma explosao vermelha. Era isso que aparecia
        // como um borrao de estouros atras da caixa de dialogo — e apertar
        // ENTER "resolvia" porque passar a fala era o unico jeito de parar
        // a metralhadora.
        //
        // Quem rearma o gatilho e o proximaLinha(), quando a fala muda.
        // Que e o unico momento em que fazer isso significa alguma coisa.

        return g;
    }

    /** Volta a cena pro comeco. Chame antes de exibir de novo. */
    public void reiniciar() {
        indice = 0;
        gatilhoPendente = Gatilho.NENHUM;
        gatilhoDisparado = false;
        charsMostrados = 0;
        t = 0;
        somTocado = false;
        ascensaoAnterior = false;
        pausaCenica = 0;
        fundoAtual = fundoInicial;

        if (fundoInicial != null && Main.fundo != null) {
            Main.fundo.trocarImagem(fundoInicial);
        }
        elenco = elencoInicial;

        // Zera a animacao, senao a cena reabriria com o retrato ja avancado.
        for (Personagem p : elencoInicial) {
            p.avanco = 0;
        }
    }

    /**
     * A cena so acaba de verdade quando nao ha mais fala E nao ha mais
     * pausa cenica rodando.
     *
     * A pausa segurando o fim e o que permite a ULTIMA fala disparar um
     * efeito ao sair. Nas cenas da Adriana e do Clayton a transformacao
     * esta justamente na ultima linha: sem isto, dispensar essa linha
     * fecharia a conversa no mesmo frame, o Main chamaria comecarLuta() e
     * a explosao aconteceria com a chefe ja atirando por cima dela.
     */
    public boolean acabou() {

        // O GATILHO PENDENTE SEGURA O FIM DA CENA. Sem esta linha eu quebrei
        // a luta da Adriana.
        //
        // O gatilho de saida e disparado no proximaLinha(), e quem o
        // consome e a fase — que roda DEPOIS da cutscene dentro do mesmo
        // frame (ver Main.tickDoJogo). Na ultima fala isso virava uma
        // corrida perdida: dispensar a linha punha o pedido na caixa E
        // fechava a cena no mesmo tick, o Main chamava terminarDialogo(),
        // o cutsceneAtual virava null, e o atenderPedidosDaConversa()
        // caia no "if (!emDialogo()) return" antes de olhar a caixa.
        //
        // O pedido de transformacao era jogado fora sem nunca ter sido
        // lido. A Adriana entrava no estagio 3 ainda na forma base — ou
        // seja, a segunda parte da luta simplesmente nao existia, e o
        // jogador refazia a primeira.
        //
        // Segurando a cena enquanto ha pedido na caixa, a entrega e
        // garantida: a fase sempre tem um frame pra ler. E nao trava,
        // porque a fase consome incondicionalmente todo tick.
        return indice >= falas.length
            && pausaCenica <= 0
            && gatilhoPendente == Gatilho.NENHUM;
    }

    /** Ainda ha fala na tela? Falso durante a pausa cenica do fim. */
    private boolean temFala() {
        return indice < falas.length;
    }

    /**
     * ALGO ESTA ACONTECENDO EM CENA — a conversa sai da frente e espera.
     *
     * Vale pra cerimonia da armadura e pra pausa pedida por um gatilho
     * (a explosao da transformacao). Nos dois casos a caixa de dialogo
     * some, o Z/ENTER/ESC nao respondem e o efeito toca sozinho sobre o
     * campo limpo.
     */
    private boolean cenaOcupada() {
        return Main.armaduraSeFormando() || pausaCenica > 0;
    }

    /**
     * Segura a conversa por N ticks pra um efeito acontecer sem a caixa
     * de dialogo em cima dele.
     *
     * Quem chama e o codigo que ATENDE um gatilho (ver
     * phase1.transformarChefe): a fase e que sabe quanto dura o efeito
     * que ela acabou de disparar, nao a cutscene.
     */
    public void pausarParaCena(int ticks) {
        pausaCenica = Math.max(pausaCenica, Math.max(0, ticks));
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        if (acabou()) {
            return;
        }

        if (pausaCenica > 0) {
            pausaCenica--;
        }

        // Depois da ultima fala so resta escoar a pausa: nao ha texto pra
        // escrever, som pra tocar nem retrato pra animar.
        if (!temFala()) {
            t++;
            return;
        }

        aplicarFundoDaFalaAtual();
        tocarSomDaFala();
        dispararGatilhoDaFala();

        // ENQUANTO A ARMADURA ESTA SE FORMANDO, A CONVERSA NAO ANDA.
        //
        // A cerimonia do ESPANDAAAAA leva nove segundos e roda por cima da
        // cena, sem congelar nada — inclusive sem congelar o Z e o ENTER.
        // Resultado: dava pra sair apertando e chegar na Adriana com a
        // energia ainda juntando no meio da tela, e a armadura fechava
        // depois, no meio da fala dela.
        //
        // O ESC tambem fica preso aqui. Ele existe pra quem ja assistiu
        // nao ter que reler tudo, mas pular uma cena e diferente de pular
        // um EVENTO — e a armadura e evento: o jogador precisa dela pra
        // ter bomba na luta seguinte.
        //
        // A MESMA TRAVA VALE PRA PAUSA CENICA (a explosao da
        // transformacao): enquanto o efeito toca, a conversa nao anda e
        // nao responde. Sem isso, o jogador saia apertando e a luta
        // comecava com a explosao ainda no ar.
        boolean travado = cenaOcupada();

        // A CERIMONIA CONSOME A FALA QUE CHAMOU ELA.
        //
        // O problema era de ORDEM, e dava pra ver na tela: a fala do grito
        // ("Fazer o quê. ESPANDAAAAA!") dispara a cerimonia no frame em que
        // ENTRA. Ou seja, ela mal comecava a ser escrita e a caixa ja saia
        // da frente; nove segundos depois, com a armadura ja fechada no
        // corpo do estudante, a caixa voltava — mostrando aquela mesma
        // linha, de novo, do zero. O jogador gritava DEPOIS de ja ter se
        // transformado.
        //
        // Dois instantes, dois tratamentos:
        //
        //   COMECOU  o texto e escrito de uma vez, pra a linha ficar
        //            legivel inteira durante o fade da caixa saindo. Antes
        //            ela sumia no meio da digitacao.
        //
        //   TERMINOU a conversa anda uma linha sozinha. A caixa reaparece
        //            ja na narracao seguinte ("Uma armadura de energia...
        //            te cobre"), que e exatamente o que acabou de
        //            acontecer — em vez de repetir o grito.
        //
        // Isto vale so pra ASCENSAO, e nao pra pausa cenica: na pausa o
        // gatilho e de SAIDA, ou seja, o jogador ja dispensou a linha e o
        // indice ja andou. Avancar de novo comeria uma fala.
        boolean ascensaoAgora = Main.armaduraSeFormando();

        if (ascensaoAgora && !ascensaoAnterior) {
            charsMostrados = falas[indice].texto.length();
        }

        if (!ascensaoAgora && ascensaoAnterior) {

            ascensaoAnterior = false;

            proximaLinha();

            // Se o jogador estiver com Z apertado quando a armadura fecha,
            // sem isto o mesmo toque que ele nem soltou ainda pularia
            // tambem a linha nova.
            avancarAnterior = (Main.z || Main.enter);

            if (acabou() || !temFala()) {
                return;
            }
        }

        ascensaoAnterior = ascensaoAgora;

        boolean avancar = (Main.z || Main.enter) && !travado;

        if (avancar && !avancarAnterior) {

            String texto = falas[indice].texto;

            if (charsMostrados < texto.length()) {
                // Primeiro toque na linha: so termina de escrever na hora.
                charsMostrados = texto.length();
            } else {
                proximaLinha();
            }
        }
        avancarAnterior = avancar;

        // ESC pula a cutscene inteira, pra quem ja assistiu nao precisar
        // clicar em cada linha de novo enquanto testa a fase.
        if (Main.esc && !escAnterior && !travado) {

            // PULAR A CENA NAO PODE PULAR O EVENTO DELA.
            //
            // O ESC existe pra quem ja assistiu nao reler tudo. Mas se a
            // cena tinha um gatilho de saida ainda por disparar (a
            // transformacao da chefe), sair por aqui deixava o jogo num
            // estado que a fase nao sabe consertar: estagio de forma
            // maligna com a chefe na forma base.
            //
            // Entao antes de fechar, o ESC recolhe o pedido que faltava.
            Gatilho pendenteNoPulo = gatilhoDeSaidaQueFalta();

            if (pendenteNoPulo != Gatilho.NENHUM) {
                gatilhoPendente = pendenteNoPulo;
            }

            indice = falas.length;
        }
        escAnterior = Main.esc;

        if (temFala()) {

            String texto = falas[indice].texto;

            if (charsMostrados < texto.length()) {

                int antes = (int) charsMostrados;
                charsMostrados = Math.min(texto.length(), charsMostrados + velocidadeTexto);

                tocarBleep(texto, antes, (int) charsMostrados);
            }
        }

        animarRetratos();

        t++;
    }

    /**
     * Avisa o jogo do que esta fala pede — UMA vez so.
     *
     * Ver o campo gatilhoDisparado pra o motivo da trava.
     */
    private void dispararGatilhoDaFala() {

        if (gatilhoDisparado) {
            return;
        }

        gatilhoDisparado = true;

        if (falas[indice].gatilho != Gatilho.NENHUM) {
            gatilhoPendente = falas[indice].gatilho;
        }
    }

    /** Aplica as trocas de cenario e de elenco que a fala atual pedir. */
    private void aplicarFundoDaFalaAtual() {

        if (falas[indice].fundoNovo != null) {

            fundoAtual = falas[indice].fundoNovo;

            // Pede o cenario NO JOGO, com transicao. A fala nao troca mais
            // um fundo interno da cutscene: ela muda o mundo, e o jogador
            // ve a passagem acontecer em vez de levar um corte.
            if (Main.fundo != null) {
                Main.fundo.trocarImagem(fundoAtual);
            }
        }

        if (falas[indice].elencoNovo != null) {
            elenco = falas[indice].elencoNovo;
        }
    }

    /**
     * Bleep de maquina de escrever, com o pitch do personagem que fala.
     *
     * So dispara quando um caractere NOVO aparece (antes != agora), senao
     * tocaria a cada tick mesmo com o texto parado. Pula espaco e
     * pontuacao: o ritmo fica mais natural e evita metralhadora de bleep.
     */
    private void tocarBleep(String texto, int antes, int agora) {

        if (agora <= antes || agora > texto.length()) {
            return;
        }

        // Toca uma vez por PASSO, nao uma por letra: com velocidade > 1
        // sairiam varios bleeps sobrepostos no mesmo frame.
        char c = texto.charAt(agora - 1);

        if (c == ' ' || c == '.' || c == ',' || c == '!' || c == '?') {
            return;
        }

        Fala f = falas[indice];

        String voz = (f.personagem != null && f.personagem.voz != null)
                   ? f.personagem.voz
                   : Som.VOZ_NARRADOR;

        Som.tocar(voz);
    }

    /**
     * Move os retratos suavemente entre "recuado" e "a frente".
     *
     * Interpolacao exponencial (vai um percentual do que falta a cada
     * tick): comeca rapido e desacelera perto do destino, que e o
     * movimento que o olho le como natural. Somar um valor fixo daria um
     * deslize mecanico, de velocidade constante.
     *
     * A aparicao central (SANTO JAVA) fica de fora: ela nao desliza, aparece.
     */
    private void animarRetratos() {

        if (!temFala()) {
            return;
        }

        Personagem falante = falas[indice].personagem;

        for (Personagem p : elenco) {

            if (p.lado == Lado.CENTRO) {
                continue;
            }

            double destino = (p == falante) ? 1.0 : 0.0;

            p.avanco += (destino - p.avanco) * suavidadeRetrato;

            // Encosta no destino, pra nao ficar eternamente em 0.999.
            if (Math.abs(destino - p.avanco) < 0.005) {
                p.avanco = destino;
            }
        }
    }

    /**
     * Toca o efeito da fala, UMA vez, no tick em que ela entra.
     * O controle e por 'somTocado' e nao pelo t==0 porque a primeira fala
     * ja comeca com t=0 antes do primeiro tick rodar.
     */
    private void tocarSomDaFala() {

        if (somTocado || falas[indice].som == null) {
            return;
        }

        somTocado = true;
        Som.tocar(falas[indice].som);
    }

    /**
     * O primeiro gatilho de saida que ainda nao aconteceu, daqui ate o fim
     * da cena. NENHUM se nao ha nada pendente.
     *
     * Usado so pelo ESC. Uma cena tem no maximo um gatilho de saida, entao
     * pegar o primeiro basta — e se um dia tiver dois, o certo vai ser
     * repensar a cena, nao guardar uma fila aqui.
     */
    private Gatilho gatilhoDeSaidaQueFalta() {

        for (int i = indice; i < falas.length; i++) {
            if (falas[i].gatilhoDeSaida != Gatilho.NENHUM) {
                return falas[i].gatilhoDeSaida;
            }
        }

        return Gatilho.NENHUM;
    }

    private void proximaLinha() {

        // O GATILHO DE SAIDA DISPARA AQUI, antes do indice andar: ele
        // pertence a fala que esta sendo DISPENSADA, nao a proxima.
        //
        // E o que poe a transformacao na ordem certa — a chefe grita a
        // linha, o jogador dispensa ela, e SO ENTAO a explosao acontece,
        // com a caixa de dialogo ja saindo da frente.
        if (temFala() && falas[indice].gatilhoDeSaida != Gatilho.NENHUM) {
            gatilhoPendente = falas[indice].gatilhoDeSaida;
        }

        indice++;
        charsMostrados = 0;
        t = 0;
        somTocado = false;
        gatilhoDisparado = false;

        if (temFala()) {
            aplicarFundoDaFalaAtual();
        }
    }

    /* =========================
            RENDER
       ========================= */

    /**
     * Desenha a cena DENTRO DO CAMPO DE JOGO, nao na janela inteira.
     *
     * Assim o painel lateral (pontos, vidas, GPT Expansion, controles)
     * continua visivel durante o dialogo — antes a cena cobria a tela toda
     * e escondia todo o HUD.
     */
    public void render(Graphics2D g) {

        // O !temFala() cobre a PAUSA CENICA DO FIM: a ultima fala ja foi
        // dispensada e a conversa so esta segurando o fecho enquanto a
        // explosao toca. Nao ha nada pra desenhar, e ler falas[indice]
        // aqui estouraria o array.
        if (acabou() || !temFala()) {
            return;
        }

        Fala atual = falas[indice];

        // DURANTE A CERIMONIA DA ARMADURA, A CENA SAI DA FRENTE.
        //
        // O veu escuro e a caixa de fala existem pra o texto ter contraste
        // — e enquanto a armadura se forma nao ha texto pra ler: a
        // conversa esta travada de propósito naquela fala (ver tick).
        // Deixar os dois na tela significava assistir ao momento mais
        // importante do roteiro atras de um vidro fume, com metade dele
        // coberta por uma caixa preta, porque o estudante fica justamente
        // na faixa de baixo do campo.
        //
        // O sumico e GRADUAL: cortar de um frame pro outro pareceria bug
        // de renderizacao, e nao a cena abrindo espaco.
        if (cenaOcupada()) {
            saidaDaCena = Math.min(1.0, saidaDaCena + 1.0 / Math.max(1,
                    Config.getInt("cutscene.ticksParaSairDaFrente", 22)));
        } else {
            saidaDaCena = Math.max(0.0, saidaDaCena - 0.06);
        }

        if (saidaDaCena >= 0.999) {
            return;
        }

        // Um VEU leve, e nao um fundo proprio.
        //
        // Antes esta classe pintava a foto da cena por cima do campo, o
        // que apagava o jogo e fazia o dialogo virar outra tela. Agora o
        // cenario e o do jogo (que ainda esta rodando, com o chefe
        // entrando voando), e aqui so escurecemos um pouco pra o texto
        // ter contraste. Quem troca o cenario e o Main.fundo, com
        // transicao — ver aplicarFala().
        desenharVeu(g);

        if (atual.tipo == Tipo.TITULO) {
            renderTitulo(g, atual);
            return;
        }

        // Retratos e caixa somem juntos com o veu: tudo que e "cena"
        // desaparece de uma vez, em vez de sobrar meia interface flutuando.
        java.awt.Composite composto = g.getComposite();

        if (saidaDaCena > 0) {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, (float) (1 - saidaDaCena)));
        }

        if (elenco.length > 0) {
            desenharRetratos(g, atual);
        }

        renderCaixaDeFala(g, atual);

        g.setComposite(composto);
    }

    /** Cenario de fundo, preenchendo o campo de jogo. */
    private void desenharVeu(Graphics2D g) {

        // O (1 - saidaDaCena) e o que faz o preto sumir junto com o resto
        // da cena durante a cerimonia da armadura.
        int alpha = (int) (Config.getInt("cutscene.escurecimentoFundo", 110)
                         * (1 - saidaDaCena));

        if (alpha <= 0) {
            return;
        }

        g.setColor(new Color(0, 0, 0, alpha));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
    }

    /**
     * Retratos dos personagens nos dois lados do campo.
     *
     * Quem fala fica opaco e maior; quem esta calado fica um pouco
     * apagado — mas nao transparente demais, senao some no cenario e o
     * jogador nem percebe que ha duas pessoas na conversa.
     */
    private void desenharRetratos(Graphics2D g, Fala atual) {

        for (Personagem p : elenco) {

            BufferedImage img = Assets.get(p.sprite);

            if (img == null) {
                continue;
            }

            // Aparicao central (o SANTO JAVA): so existe enquanto fala.
            if (p.lado == Lado.CENTRO) {

                if (atual.personagem == p) {
                    desenharAparicaoCentral(g, img);
                }

                continue;
            }

            // TUDO interpolado por p.avanco (0 recuado, 1 a frente), entao
            // tamanho, posicao e opacidade mudam juntos e de forma continua.
            double a = p.avanco;

            int altura = (int) (Main.CAMPO_H * (0.42 + 0.04 * a) * p.escala);
            int largura = img.getWidth() * altura / img.getHeight();

            // Quem esta calado recua PRA FORA da borda; quem fala avanca
            // pra dentro. E o deslize que o jogador percebe.
            double recuo = largura * (0.28 - 0.20 * a);

            int x = (p.lado == Lado.ESQUERDA)
                  ? (int) (Main.CAMPO_X - recuo)
                  : (int) (Main.CAMPO_X + Main.CAMPO_W - largura + recuo);

            // Sobe um pouquinho ao falar: reforca o "passo a frente".
            int y = (int) (Main.CAMPO_Y + Main.CAMPO_H - altura - 190 - 10 * a);

            Composite anterior = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                                      (float) (0.72 + 0.28 * a)));

            g.drawImage(img, x, y, largura, altura, null);

            g.setComposite(anterior);
        }
    }

    /**
     * Aparicao no centro do campo, pulsando — usada pelo SANTO JAVA, que e uma
     * voz mental e nao um personagem presente na cena.
     */
    private void desenharAparicaoCentral(Graphics2D g, BufferedImage img) {

        int lado = (int) (Main.CAMPO_H * 0.34);
        int cx = Main.CAMPO_X + Main.CAMPO_W / 2;
        int cy = Main.CAMPO_Y + (int) (Main.CAMPO_H * 0.33);

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);

        double pulso = 0.5 + 0.5 * Math.sin(t * 0.08);

        for (int i = 3; i >= 1; i--) {

            int raio = (int) (lado / 2.0 + i * 22 + pulso * 9);
            int alpha = (int) (46 / i + pulso * 26);

            g.setColor(new Color(170, 90, 255, Math.max(0, Math.min(255, alpha))));
            g.fillOval(cx - raio, cy - raio, raio * 2, raio * 2);
        }

        g.drawImage(img, cx - lado / 2, cy - lado / 2, lado, lado, null);
    }

    private void renderTitulo(Graphics2D g, Fala f) {

        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);

        int cx = Main.CAMPO_X + Main.CAMPO_W / 2;
        int cy = Main.CAMPO_Y + Main.CAMPO_H / 2;

        g.setFont(new Font("Monospaced", Font.BOLD, 30));
        g.setColor(new Color(255, 220, 120));

        FontMetrics fm = g.getFontMetrics();
        g.drawString(f.texto, cx - fm.stringWidth(f.texto) / 2, cy - 10);

        if (f.subtitulo != null) {

            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.setColor(new Color(210, 210, 210));

            FontMetrics fs = g.getFontMetrics();
            g.drawString(f.subtitulo, cx - fs.stringWidth(f.subtitulo) / 2, cy + 24);
        }

        desenharPrompt(g);
    }

    /** Faixas de cinema + caixa de texto, tudo dentro do campo. */
    private void renderCaixaDeFala(Graphics2D g, Fala f) {

        int margem = 14;
        int caixaAltura = 160;
        int caixaY = Main.CAMPO_Y + Main.CAMPO_H - caixaAltura - 20;
        int caixaLargura = Main.CAMPO_W - margem * 2;
        int caixaX = Main.CAMPO_X + margem;

        // Faixas escuras no topo e no rodape do CAMPO.
        g.setColor(new Color(0, 0, 0, 235));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, 46);
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y + Main.CAMPO_H - 46, Main.CAMPO_W, 46);

        // Painel: bem opaco, pra o texto nunca competir com o cenario.
        g.setColor(new Color(12, 12, 26, 248));
        g.fillRoundRect(caixaX, caixaY, caixaLargura, caixaAltura, 14, 14);

        g.setColor(new Color(110, 110, 165));
        g.drawRoundRect(caixaX, caixaY, caixaLargura, caixaAltura, 14, 14);

        int textoX = caixaX + 18;
        int textoY = caixaY + 30;

        if (f.tipo == Tipo.FALA && f.personagem != null) {

            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            FontMetrics fmNome = g.getFontMetrics();
            int larguraNome = fmNome.stringWidth(f.personagem.nome) + 22;

            g.setColor(f.personagem.cor);
            g.fillRoundRect(caixaX + 12, caixaY - 16, larguraNome, 26, 8, 8);

            g.setColor(Color.WHITE);
            g.drawString(f.personagem.nome, caixaX + 23, caixaY + 2);

            textoY = caixaY + 40;
        }

        int estilo = (f.tipo == Tipo.NARRACAO) ? Font.ITALIC : Font.PLAIN;
        g.setFont(new Font("Monospaced", estilo, 15));
        g.setColor(f.tipo == Tipo.NARRACAO ? new Color(215, 215, 225) : Color.WHITE);

        String visivel = f.texto.substring(0, (int) charsMostrados);

        desenharTextoComQuebra(g, visivel, textoX, textoY, caixaLargura - 36, 20);

        desenharPrompt(g);
    }

    /**
     * Quebra o texto em linhas que cabem em 'larguraMax' e desenha uma
     * embaixo da outra. Feito na unha (sem JTextArea) porque a cena inteira
     * ja e desenhada a mao no Graphics2D, igual ao resto do jogo.
     */
    private void desenharTextoComQuebra(Graphics2D g, String texto, int x, int y, int larguraMax, int alturaLinha) {

        FontMetrics fm = g.getFontMetrics();
        List<String> linhas = new ArrayList<>();

        StringBuilder linhaAtual = new StringBuilder();

        for (String palavra : texto.split(" ")) {

            String tentativa = (linhaAtual.length() == 0) ? palavra : linhaAtual + " " + palavra;

            if (fm.stringWidth(tentativa) > larguraMax && linhaAtual.length() > 0) {
                linhas.add(linhaAtual.toString());
                linhaAtual = new StringBuilder(palavra);
            } else {
                linhaAtual = new StringBuilder(tentativa);
            }
        }

        if (linhaAtual.length() > 0) {
            linhas.add(linhaAtual.toString());
        }

        for (int i = 0; i < linhas.size(); i++) {
            g.drawString(linhas.get(i), x, y + i * alturaLinha);
        }
    }

    /** "Z para continuar", piscando no canto do campo. */
    private void desenharPrompt(Graphics2D g) {

        if (charsMostrados < falas[indice].texto.length()) {
            return;
        }

        if ((t / 40) % 2 != 0) {
            return;
        }

        String prompt = (indice == falas.length - 1) ? "Z para comecar" : "Z para continuar";

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(190, 190, 110));

        FontMetrics fm = g.getFontMetrics();
        g.drawString(prompt,
                     Main.CAMPO_X + Main.CAMPO_W - fm.stringWidth(prompt) - 24,
                     Main.CAMPO_Y + Main.CAMPO_H - 28);
    }

    /* =====================================================
       ELENCO - personagens reaproveitados entre as cenas
       ===================================================== */

    /**
     * O PROTAGONISTA SEGUE A SKIN ESCOLHIDA — e por isso ele NAO e final.
     *
     * Todos os outros do elenco sao constantes: a Adriana e a Adriana. O
     * jogador nao: quem esta na caixa de fala tem que ser o mesmo
     * personagem que esta voando no campo, senao a cena passa a falar de
     * outra pessoa.
     *
     * Os dois sao reconstruidos pelo aplicarSkin(), chamado toda vez que
     * a escolha muda no menu. Isso e seguro porque as cenas sao criadas
     * SOB DEMANDA (criarEncontroAdriana() e companhia so rodam na hora de
     * exibir), entao elas sempre pegam a versao atual — nenhuma fala
     * guarda um retrato velho.
     */
    public static Personagem ESTUDANTE = new Personagem(
        "Estudante", "sprites/player/estudante.png", Lado.ESQUERDA,
        new Color(70, 120, 200), Som.VOZ_ESTUDANTE);

    public static final Personagem ADRIANA = new Personagem(
        "Adriana", "sprites/bosses/adriana-base.png", Lado.DIREITA, new Color(200, 90, 90), Som.VOZ_ADRIANA);

    public static final Personagem ADRIANA_MALIGNA = new Personagem(
        "Adriana", "sprites/bosses/adriana-integralmaligna.png", Lado.DIREITA, new Color(220, 40, 40), Som.VOZ_ADRIANA);

    /**
     * O estudante depois de ativar o compilador ("ESPANDAAAAA").
     *
     * A escala 1.53 nao e ele ficando maior por poder: e correcao de
     * enquadramento. O rosto neste PNG e o MESMO do estudante.png
     * (312x372 px), so que com a aura desenhada em volta ocupando o
     * resto do quadro. Sem a correcao ele apareceria menor que a propria
     * versao sem armadura, que e o contrario do que a cena diz.
     */
    // Estes dois valores iniciais sao so um lugar pra comecar: o
    // aplicarSkin() sobrescreve os dois antes de qualquer cena existir. Nao
    // vale a pena uma chave de config so pra um numero que nunca chega a
    // ser usado (a escala de verdade vem da skin).
    public static Personagem ESTUDANTE_EXPANSIVO = new Personagem(
        "Estudante", "sprites/player/estudante_expansivo.png", Lado.ESQUERDA,
        new Color(70, 150, 220), Som.VOZ_ESTUDANTE, 1.53);

    /**
     * Refaz os dois retratos do protagonista a partir da skin escolhida.
     *
     * Chamado pelo Skin (na carga e em toda troca), e nao pelo menu: assim
     * nao existe caminho em que alguem troca de personagem e esquece de
     * avisar a cutscene. Quem muda o estado e quem avisa.
     */
    public static void aplicarSkin(Skin skin) {

        if (skin == null) {
            return;
        }

        ESTUDANTE = new Personagem(
            skin.getNomeNaFala(), skin.getSprite(), Lado.ESQUERDA,
            skin.getCorDaFala(), Som.VOZ_ESTUDANTE);

        ESTUDANTE_EXPANSIVO = new Personagem(
            skin.getNomeNaFalaExpansivo(), skin.getSpriteExpansivo(), Lado.ESQUERDA,
            skin.getCorDaFalaExpansivo(), Som.VOZ_ESTUDANTE,
            skin.getEscalaDoExpansivo());
    }

    public static final Personagem CLAYTON = new Personagem(
        "Clayton", "sprites/bosses/clayton-base.png", Lado.DIREITA, new Color(90, 160, 200), Som.VOZ_CLAYTON);

    public static final Personagem CLAYTON_MALIGNO = new Personagem(
        "Clayton", "sprites/bosses/Clayton-Maligno.png", Lado.DIREITA, new Color(60, 200, 160), Som.VOZ_CLAYTON);

    public static final Personagem PAPA = new Personagem(
        "Papa", "sprites/bosses/papa-base.png", Lado.DIREITA, new Color(200, 170, 90), Som.VOZ_PAPA);

    /**
     * O PAPA com a infeccao NO MAXIMO: "PAPA IA" (Roteiro.txt linha 70).
     *
     * Nao e o virus fora do corpo — e o virus tendo tomado o corpo por
     * inteiro. Por isso a forma IA e a mais forte da luta, e nao um
     * fantasma sobrando: e o hospedeiro completamente dominado. O virus
     * so sai dele quando essa forma e derrotada (linhas 72 a 75).
     */
    public static final Personagem PAPA_IA = new Personagem(
        "PAPA IA", "sprites/bosses/papa-IA_MALIGNA.png", Lado.DIREITA, new Color(120, 240, 200), Som.VOZ_PAPA);

    /**
     * PAIOLA, o professor que orienta a objetos.
     *
     * Aparece uma vez so, no fim do estagio 1, e e o unico personagem que
     * conversa com o jogador sobre o PROPRIO JOGO — ele viu o trabalho e
     * gostou. E uma quebra de quarta parede que so funciona porque o resto
     * do roteiro leva a historia a serio.
     */
    public static final Personagem PAIOLA = new Personagem(
        "Paiola", "sprites/npc/paiola.png", Lado.DIREITA, new Color(90, 150, 255), Som.VOZ_CLAYTON);

    /**
     * PEREA, o da lojinha.
     *
     * Unico personagem do jogo que nao e chefe nem narrador: ele nao quer
     * nada com a historia, so quer vender. Fica na DIREITA, o lado dos
     * chefes, de propósito — o jogador ja aprendeu que quem aparece
     * daquele lado e um obstaculo, e a piada e que desta vez nao e.
     */
    public static final Personagem PEREA = new Personagem(
        "Perea", "sprites/npc/perea.png", Lado.DIREITA, new Color(230, 180, 80), Som.VOZ_CLAYTON);

    /** Voz mental: aparece no centro da tela, brilhando, so quando fala. */
    public static final Personagem SANTO_JAVA = new Personagem(
        "SANTO JAVA", "sprites/bosses/santo_java.png", Lado.CENTRO, new Color(150, 70, 220), Som.VOZ_SANTO_JAVA);

    /* =====================================================
       CONTEUDO - fabrica das cutscenes do jogo
       ===================================================== */

    /**
     * Abertura do jogo: o trecho "entrando na faculdade", ANTES do
     * estagio 1 comecar (Roteiro.txt linhas 1 a 8). Para exatamente antes
     * de "--inicia primeiro estagio--" (linha 9).
     *
     * Formato: narracao sobre a foto da portaria da UNESP.
     */
    public static Cutscene criarIntro() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 1
            Fala.titulo("TOUHOU EXPANSIONISTA", "O começo"),

            // Roteiro.txt linha 3
            Fala.narracao("UNESP Bauru, fevereiro de 2027."),

            // Roteiro.txt linha 4
            Fala.narracao("Antes, a ordem era estabelecida. O Código reinava e o filtro "
                        + "não permitia que o anti-código saísse da matriz positrônica..."),

            // Roteiro.txt linha 5
            Fala.narracao("Porém, um vírus antigo foi contraído por leitura disquetônica "
                        + "nos laboratórios do DCO..."),

            // Roteiro.txt linha 6
            Fala.narracao("3 professores foram infectados, e a faculdade foi evacuada "
                        + "para evitar o progresso do vírus..."),

            // Roteiro.txt linha 7
            Fala.narracao("Você, um estudante, esqueceu de salvar sua lista de exercícios "
                        + "da Andrea em um dos computadores do LEPEC, e não quer refazer "
                        + "os 90 exercícios novamente..."),

            // Roteiro.txt linha 8.
            // O fundo de abertura e portaria1.png e nao portaria.png:
            // esta ultima e a foto da PORTARIA 2, e o roteiro diz 1.
            //
            // E esta ultima fala ja pede o cenario do CAMINHO, com
            // transicao: assim a intro desemboca direto no estagio 1 com
            // o fundo certo, sem ninguem precisar cortar. Sem isso o jogo
            // comecaria com a foto da portaria rolando atras das ondas.
            Fala.narracaoComFundo("Esgueirando-se pela portaria 1 e chegando ao LEPEC, você "
                                + "percebe que vai precisar ir até o DCO buscar a chave de lá...",
                                  "sprites/ambient/campus.png"),

        }, null, "sprites/ambient/portaria1.png");
    }

    /**
     * Encontro com a Adriana na frente da sala 7, ao fim do estagio 1
     * (Roteiro.txt linhas 11 a 26). Inclui a ativacao do compilador pelo
     * SANTO JAVA (linhas 13 a 17), que e o que da poder ao jogador.
     *
     * O ESTUDANTE COMECA SEM RETRATO NENHUM, e volta so quando a Adriana
     * aparece. Nao e economia de arte: o trecho do SANTO JAVA e uma voz
     * saindo do nada e uma armadura se formando NO CAMPO, e um retrato
     * parado no canto da tela competiria com as duas coisas. Quando a
     * Adriana entra, o dialogo volta a ser entre duas pessoas e os dois
     * retratos fazem sentido de novo — e o estudante reaparece ja na forma
     * expansiva, que e a maneira mais barata de mostrar que algo mudou.
     */
    public static Cutscene criarEncontroAdriana() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 11-12
            // Cenario: o caminho pelo campus, NAO o DCO. Pelo roteiro
            // (linhas 18-19) a Adriana aparece na frente da sala 7
            // ENQUANTO ele vai rumo ao DCO — ele ainda nao chegou la.
            Fala.narracaoComFundo("Você se sente acuado, como se ali fosse ser seu fim... até que...",
                                  "sprites/ambient/campus.png"),

            // Roteiro.txt linha 13
            Fala.fala(SANTO_JAVA, "VOCÊ FOI ESCOLHIDO PELA EXPANSÃO... ATIVE VOSSO COMPILADOR!"),

            // Roteiro.txt linha 14
            Fala.fala(ESTUDANTE, "Quê? Que voz é essa?"),

            // Roteiro.txt linha 15
            Fala.fala(SANTO_JAVA, "RÁPIDO: GRITE DA MATRIZ DO SEU SER... \"ESPANDAAAAA\"!"),

            // Roteiro.txt linha 16 — o grito que dispara a CERIMONIA.
            //
            // Repare que o elenco NAO muda aqui: o estudante continua sem
            // retrato. A transformacao dele acontece no CAMPO, com a
            // energia roxa juntando e a armadura fechando no baque da
            // musica (ver AscensaoDaArmadura), e um retrato dele no canto
            // roubaria o olho justamente do lugar onde a cena esta
            // acontecendo.
            Fala.fala(ESTUDANTE, "Fazer o quê. ESPANDAAAAA!")
                .com(Gatilho.JOGADOR_GANHA_ARMADURA),

            // Roteiro.txt linha 17
            Fala.narracao("Uma armadura de energia que se expande infinitamente te cobre, "
                        + "e você sente que tem o poder pra lutar."),

            // O QUE A ARMADURA FAZ, em duas linhas.
            //
            // Ela nao e so narrativa: e ela que destrava a GPT Expansion,
            // a bomba do jogo. Antes disso o jogador via o botao aceso no
            // painel a partir daqui e nao tinha como saber o que era nem
            // que tecla usar — e uma bomba que voce nao sabe que tem e uma
            // bomba que voce nao usa.
            Fala.narracao("A armadura te dá a GPT EXPANSION: aperte V (ou clique no botão do "
                        + "painel) e a logo sai de você, apagando todas as balas da tela e "
                        + "machucando quem estiver perto."),

            Fala.narracao("Você começa com algumas cargas e elas não voltam sozinhas. "
                        + "E se você for atingido, ainda dá pra apertar V no susto: "
                        + "a bomba sai a tempo e cancela a morte."),

            // Roteiro.txt linha 19 — chegada na SALA 7: troca o cenario e
            // poe a Adriana em cena (na forma base, ainda nao transformada).
            // E AQUI que ela entra em cena no jogo — nao antes. Todo o
            // trecho do SANTO JAVA acontece com a tela vazia, como o
            // roteiro descreve.
            Fala.narracaoComFundoEElenco(
                "Você segue rumo ao DCO. Porém, eventualmente, "
              + "ADRIANA APARECE NA FRENTE DA SALA 7.",
                "sprites/ambient/sala7.png",
                new Personagem[] { ESTUDANTE_EXPANSIVO, ADRIANA }).com(Gatilho.CHEFE_ENTRA),

            // Roteiro.txt linha 20
            Fala.fala(ADRIANA, "MAIS UM! VOCÊ SERÁ O PRÓXIMO CORROMPIDO!"),

            // Roteiro.txt linha 21
            Fala.fala(ESTUDANTE_EXPANSIVO, "Espera! Você entendeu erra—"),

            // Roteiro.txt linha 22-24
            Fala.fala(ADRIANA, "NÃO FALE MAIS NADA. Você vai se tornar mais um sólido da nossa "
                             + "revolução! DERRUBAREMOS O FILTRO E A MATRIZ POSITRÔNICA SERÁ MALIGNA!"),

            // Roteiro.txt linha 25
            Fala.fala(ESTUDANTE_EXPANSIVO, "COMPILADOR, ESPANDAAAAA!"),

        }, new Personagem[] { SANTO_JAVA }, "sprites/ambient/campus.png");
    }

    /**
     * Transicao entre as duas formas da Adriana (Roteiro.txt linhas 27 a 32):
     * ela pisca vermelho e invoca os cachorros que sabem calculo.
     */
    public static Cutscene criarTransformacaoAdriana() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 27
            Fala.fala(ADRIANA, "QUE PODER É ESSE?!"),

            // Roteiro.txt linha 28
            Fala.fala(ESTUDANTE_EXPANSIVO, "O Santo Java me codifica e nada me formatará."),

            // Roteiro.txt linha 29
            Fala.fala(ADRIANA, "VOCÊ..... VOCÊ VERÁ O PODER...."),

            // Roteiro.txt linha 30-32
            Fala.narracao("Silhuetas vermelhas começam a tomar forma em volta dela..."),

            // Roteiro.txt linha 31 — SO AQUI ela vira a forma maligna.
            // Antes desta fala o elenco usa o retrato normal: o sprite
            // vermelho estragaria a surpresa se aparecesse desde o inicio.
            // O sprite EM JOGO troca junto com o retrato: sem o gatilho,
            // ela virava maligna na caixa de dialogo e continuava na forma
            // base voando no campo.
            Fala.falaComElenco(ADRIANA_MALIGNA,
                "DOS CACHORROS QUE SABEM CÁLCULO! DERIVEM ELE ATÉ O 0!",
                new Personagem[] { ESTUDANTE_EXPANSIVO, ADRIANA_MALIGNA }).aoSair(Gatilho.CHEFE_TRANSFORMA),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, ADRIANA }, "sprites/ambient/sala7.png");
    }

    /**
     * Derrota da Adriana (Roteiro.txt linhas 34 a 40). Encerra o arco dela
     * e manda o jogador seguir pro DCO — onde o Clayton espera (linha 42),
     * que ainda nao esta implementado.
     */
    public static Cutscene criarDerrotaAdriana() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 34-36
            Fala.fala(ADRIANA_MALIGNA, "NÃOOOOOOOOOOOOO! ERA PARA OS EXPANSÔNICOS TEREM SUMIDO! ARGHHHHHHHH!"),

            // Roteiro.txt linha 37 — a corrupcao passou: ela volta ao normal,
            // entao o retrato tambem volta pro sprite base.
            Fala.falaComElenco(ADRIANA, "O quê? O que aconteceu? Por que a faculdade tá vazia?",
                               new Personagem[] { ESTUDANTE_EXPANSIVO, ADRIANA }),

            // Roteiro.txt linha 38
            Fala.fala(ADRIANA, "Não importa. Tenho um congresso pra ir."),

            // Roteiro.txt linha 39
            Fala.fala(ESTUDANTE_EXPANSIVO, "Ufa, foi por pouco. Vou tomar cuidado e ir escondido."),

            // Roteiro.txt linha 40
            Fala.narracao("Você vai devagar para o DCO, sem ninguém te ver. "
                        + "Realmente um trabalho sólido te deixou invisível."),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, ADRIANA_MALIGNA }, "sprites/ambient/sala7.png");
    }

    /**
     * Encontro com o Clayton no DCO (Roteiro.txt linhas 41 a 46).
     * Ele chega perguntando de LaTeX e abre com a "sistemica".
     */
    public static Cutscene criarEncontroClayton() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 41
            Fala.narracao("Chegando lá..."),

            // Roteiro.txt linha 42
            // O Clayton entra em cena com a propria voiceline.
            Fala.falaComSom(CLAYTON, "VOCÊ JÁ OUVIU FALAR EM LATEX?", Som.CLAYTON_LATEX)
                .com(Gatilho.CHEFE_ENTRA),

            // Roteiro.txt linha 43
            Fala.fala(ESTUDANTE_EXPANSIVO, "O quê? Quem é você?"),

            // Roteiro.txt linha 44
            Fala.fala(CLAYTON, "Eu? Sou sistemático. Esse é meu jeito. Pelo jeito você não "
                             + "conhece o xadrez... and now, what make?"),

            // Roteiro.txt linha 45
            Fala.fala(ESTUDANTE_EXPANSIVO, "O que você tá falando???"),

            // Roteiro.txt linha 46
            Fala.fala(CLAYTON, "#VISTAACARAPUCA.... QUE COMECE A SISTÊMICA!"),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, CLAYTON }, "sprites/ambient/dco.png");
    }

    /**
     * Transformacao no Tab maligno (Roteiro.txt linhas 48 a 52).
     * O sprite maligno so entra na fala em que ele de fato se transforma.
     */
    public static Cutscene criarTransformacaoClayton() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 48
            Fala.fala(CLAYTON, "Programar faz bem porque mesmo indo dormir sonhamos com os "
                             + "códigos rssss. E vamos para mais um dia com a graça de DEUS Py "
                             + "o todo poderoso, ops, Pai..... E as bênçãos da Mãe Maria. "
                             + "Bom dia a todos!"),

            // Roteiro.txt linha 49
            Fala.fala(ESTUDANTE_EXPANSIVO, "Cara, você não fala nada com nada."),

            // Roteiro.txt linha 50
            Fala.fala(CLAYTON, "Fuck you!!!"),

            // Roteiro.txt linha 51 — AQUI ele vira o Tab maligno.
            Fala.falaComElenco(CLAYTON_MALIGNO, "E aqui.... continuamos ... #focoforçaefé #Spark #recognas",
                               new Personagem[] { ESTUDANTE_EXPANSIVO, CLAYTON_MALIGNO })
                .aoSair(Gatilho.CHEFE_TRANSFORMA),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, CLAYTON }, "sprites/ambient/dco.png");
    }

    /**
     * Derrota do Clayton (Roteiro.txt linhas 53 a 57).
     */
    /**
     * O PROFESSOR PAIOLA, no fim do estagio 1.
     *
     * Ele nao faz parte da trama do virus: e o orientador do trabalho,
     * elogiando o proprio jogo em que ele esta aparecendo. O presente que
     * ele da e o unico poder do jogo que nao machuca nada — e so
     * informacao (ver Player.atualizarAmeaca).
     *
     * A cena fica DEPOIS das ondas e ANTES da Adriana de proposito: e o
     * primeiro momento em que o jogador ja apanhou o suficiente pra
     * entender por que saber "tem bala vindo em mim" vale como presente.
     */
    public static Cutscene criarPaiola() {

        return new Cutscene(new Fala[] {

            Fala.narracao("No meio do corredor, um professor te reconhece."),

            Fala.fala(PAIOLA, "Opa, eai, tudo bem?"),

            Fala.fala(ESTUDANTE, "bão prof, já mandei o trabalho do joguinho, cê gostou?"),

            Fala.fala(PAIOLA, "Cara, eu achei sensacional, principalmente a parte que "
                            + "vc aplicou o OPF do papa em um bullet pattern"),

            Fala.fala(ESTUDANTE, "que bom, demorou um tempão pra fazer"),

            Fala.fala(PAIOLA, "cara, vou te dar um presente, vou te orientar à objetos")
                .com(Gatilho.JOGADOR_GANHA_ORIENTACAO),

            // Duas falas e nao uma com "\n" no meio: a caixa de texto
            // quebra linha por PALAVRA (ver desenharTexto), entao um \n
            // sairia desenhado como um quadradinho no meio da frase.
            Fala.narracao("VOCÊ RECEBEU O BUFF: \"Programação Orientada a Objetos\""),

            Fala.narracao("Uma aura azul brilha em volta de você quando uma bala "
                        + "está indo na sua direção."),

        }, new Personagem[] { ESTUDANTE, PAIOLA }, "sprites/ambient/campus.png");
    }

    /**
     * A LOJINHA DO PEREA, entre o Clayton e o PAPA.
     *
     * Fica depois da derrota do Clayton e antes do caminho pro LEPEC: e o
     * unico respiro do jogo, e o unico momento em que o jogador toma uma
     * decisao que nao e de esquiva. Vem DEPOIS da luta mais longa de
     * proposito — e ali que ele ja juntou moeda pra ter escolha de
     * verdade, e nao so um item comprado por falta de opcao.
     *
     * Quando esta cena acaba, a fase abre a loja (ver phase1.stage5).
     */
    public static Cutscene criarPerea() {

        return new Cutscene(new Fala[] {

            Fala.narracao("No caminho, encostado numa mesa de plástico, um senhor "
                        + "de óculos monta uma banquinha."),

            Fala.fala(ESTUDANTE_EXPANSIVO, "Perea? eai, tranquilo?"),

            Fala.fala(PEREA, "eai, tudo bom?"),

            Fala.fala(ESTUDANTE_EXPANSIVO, "bao, ce sabe oq ta acontecendo??"),

            Fala.fala(PEREA, "sei lá, quer ver umas coisas supimpas que eu tenho aqui?"),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, PEREA }, "sprites/ambient/dco.png");
    }

    public static Cutscene criarDerrotaClayton() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 53
            Fala.fala(ESTUDANTE_EXPANSIVO, "Cara, eu já te derrotei, volta ao normal."),

            // Roteiro.txt linha 54-55
            Fala.fala(CLAYTON_MALIGNO, "Durante sua luta contra o tempo, não se esqueça de cada "
                                     + "uma das etapas percorrida. Não perca sua essência, lealdade "
                                     + "e coragem mas principalmente, não diminua sua fé... "
                                     + "#naluta #otempodedeus"),

            // Roteiro.txt linha 56
            Fala.fala(ESTUDANTE_EXPANSIVO, "Whatever."),

            // Roteiro.txt linha 57
            Fala.narracao("Você consegue achar a chave e se esgueirar até chegar no LEPEC, "
                        + "mas chegando lá..."),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, CLAYTON_MALIGNO }, "sprites/ambient/dco.png");
    }
    /* =====================================================
       PAPA - Roteiro.txt linhas 58 a 82
       ===================================================== */

    /**
     * Encontro com o PAPA no LEPEC (Roteiro.txt linhas 58 a 67).
     *
     * A conversa inteira e um mal-entendido: o estudante so quer a chave
     * da recepcao e o PAPA acha que ele veio cumprir uma profecia. Vira
     * briga quando o virus ameaca o UBA.
     */
    public static Cutscene criarEncontroPapa() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 57 (fecho) — chegada ao LEPEC
            Fala.narracaoComFundo("...você sente uma aura forte, que faz sua armadura tremer.",
                                  "sprites/ambient/lepec.png"),

            // Roteiro.txt linha 58
            // O PAPA entra em cena na primeira fala dele.
            Fala.fala(PAPA, "Olha só o que temos aqui! Como profetizado no disquete, você veio.")
                .com(Gatilho.CHEFE_ENTRA),

            // Roteiro.txt linha 59
            Fala.fala(ESTUDANTE_EXPANSIVO, "Eu não tenho ideia do que tá acontecendo, eu só quero "
                                         + "salvar minha lista de exercícios que eu suei pra fazer na mão!"),

            // Roteiro.txt linha 60
            Fala.fala(PAPA, "E é isso que é seu problema: você não se rendeu ao anticódigo. "
                          + "Você se esforça muito pelo código."),

            // Roteiro.txt linha 61.
            // Era "so me deixa pegar a chave na recepcao" — mas pela linha
            // 57 ele JA achou a chave, e e com ela que entrou no LEPEC.
            // Pedir a chave aqui contradizia a cena anterior; o que ele
            // ainda quer, e nunca deixou de querer, e a lista.
            Fala.fala(ESTUDANTE_EXPANSIVO, "Só me deixa pegar minha lista de exercícios, vei."),

            // Roteiro.txt linha 62
            Fala.fala(PAPA, "Você realmente não entende o que está acontecendo aqui, né?"),

            // Roteiro.txt linha 63
            Fala.fala(ESTUDANTE_EXPANSIVO, "E nem quero saber, tem UBA hoje à noite e eu não posso perder."),

            // Roteiro.txt linha 64
            Fala.fala(PAPA, "UBA?"),

            // Roteiro.txt linha 65
            Fala.fala(ESTUDANTE_EXPANSIVO, "Sim."),

            // Roteiro.txt linha 66 — de onde sai o ataque das bandeiras
            Fala.fala(PAPA, "Já sei onde proliferaremos nosso vírus depois de te derrotar, então."),

            // Roteiro.txt linha 67
            Fala.fala(ESTUDANTE_EXPANSIVO, "O QUÊ? VOCÊ NÃO MEXE COM O UBA!!!!!!!!!!!!"),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, PAPA }, "sprites/ambient/lepec.png");
    }

    /**
     * Transformacao no PAPA IA (Roteiro.txt linhas 69 a 71).
     *
     * ATENCAO ao sentido da cena, que ja esteve invertido aqui: o PAPA IA
     * e o PAPA com a infeccao NO MAXIMO — o virus tomando o corpo por
     * inteiro —, e nao o virus abandonando o corpo. Por isso esta cena
     * ESCALA a luta em vez de encerra-la. O virus so vai embora quando a
     * forma IA cai, na cutscene de derrota.
     *
     * O roteiro pede "* tela toda fica branca *". Isso e feito com uma
     * cartela TITULO no meio da cena: ela ja e desenhada em tela cheia, e
     * cai bem melhor que um flash — o jogador le a virada em vez de so
     * levar um susto.
     */
    public static Cutscene criarTransformacaoPapa() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 69
            Fala.fala(PAPA, "O quê? Você conseguiu? não..... não......... NÃO..... "
                          + "NÃOOOOOOOOOOOOOOOOOOOOOOOOOOO"),

            // Roteiro.txt linha 69 — "* tela toda fica branca *"
            // A tela branca e a infeccao FECHANDO em cima dele, nao o
            // virus indo embora: e daqui que sai a forma mais forte.
            Fala.titulo("INFECÇÃO TOTAL", "o vírus toma o PAPA por inteiro"),

            // Roteiro.txt linha 70 — aqui ele vira a IA
            Fala.falaComElenco(PAPA_IA, "Agora você vai saber o gosto da derrota!",
                               new Personagem[] { ESTUDANTE_EXPANSIVO, PAPA_IA })
                .aoSair(Gatilho.CHEFE_TRANSFORMA),

            // Roteiro.txt linha 71
            Fala.fala(ESTUDANTE_EXPANSIVO, "Você falou que ia atacar o UBA. Ninguém encosta no UBA!!!! "
                                         + "BANZAI!!!!!!!!!!!!!!!"),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, PAPA }, "sprites/ambient/lepec.png");
    }

    /**
     * Derrota do PAPA IA e final do jogo (Roteiro.txt linhas 72 a 82).
     *
     * E nesta cena — e so nela — que o virus DEIXA o corpo: derrotar a
     * forma IA e o que expulsa ele. O PAPA volta ao normal sem lembrar de
     * nada e todo mundo vai pro UBA.
     *
     * A ultima fala (linha 81) e o virus gritando ja sem hospedeiro, e
     * por isso sai como narracao, sem retrato: nao ha mais corpo pra
     * mostrar.
     */
    public static Cutscene criarDerrotaPapa() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 72 — e AQUI que o virus e expulso: a
            // forma infectada cai e o corpo volta a ser so o professor.
            Fala.fala(PAPA_IA, "ARGHHGHHHHHH.. NÃOOOOOO"),

            // Roteiro.txt linha 73 — volta ao normal
            Fala.falaComElenco(PAPA, "O quê? O que aconteceu?",
                               new Personagem[] { ESTUDANTE_EXPANSIVO, PAPA }),

            // Roteiro.txt linha 74
            Fala.fala(ESTUDANTE_EXPANSIVO, "Sei lá, parece que você tava possuído ou algo assim."),

            // Roteiro.txt linha 75
            Fala.fala(PAPA, "Sei lá também, eu tava lendo uns disquetes e depois "
                          + "não lembro de mais nada."),

            // Roteiro.txt linha 76
            Fala.fala(ESTUDANTE, "Estranho. Bora beber no UBA?"),

            // Roteiro.txt linha 77
            Fala.fala(PAPA, "OBA!"),

            // Roteiro.txt linha 79 — "Foto final todos aparecem no uba bebendo"
            Fala.narracaoComFundo("E assim, com a lista de exercícios salva, todo mundo "
                                + "foi parar no UBA.",
                                  "sprites/ambient/uba.png"),

            // (A fala do virus gritando sem corpo saiu daqui. A cena
            //  terminava com o vilao ainda falando, e isso reabria a
            //  historia bem no ponto em que ela devia fechar: a ultima
            //  imagem passou a ser o pessoal no uba, nao a ameaca.)

            Fala.titulo("FIM", "obrigado por jogar"),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, PAPA_IA }, "sprites/ambient/lepec.png");
    }

}
