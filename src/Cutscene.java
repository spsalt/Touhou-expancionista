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

        public Personagem(String nome, String sprite, Lado lado, Color cor) {
            this.nome = nome;
            this.sprite = sprite;
            this.lado = lado;
            this.cor = cor;
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

        private Fala(Tipo tipo, Personagem personagem, String texto, String subtitulo,
                     String fundoNovo, Personagem[] elencoNovo) {
            this(tipo, personagem, texto, subtitulo, fundoNovo, elencoNovo, null);
        }

        private Fala(Tipo tipo, Personagem personagem, String texto, String subtitulo,
                     String fundoNovo, Personagem[] elencoNovo, String som) {
            this.tipo = tipo;
            this.personagem = personagem;
            this.texto = texto;
            this.subtitulo = subtitulo;
            this.fundoNovo = fundoNovo;
            this.elencoNovo = elencoNovo;
            this.som = som;
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

    /** Elenco em cena AGORA. Muda quando uma Fala traz elencoNovo. */
    private Personagem[] elenco;

    /** Guardado pra poder voltar ao inicio no reiniciar(). */
    private final Personagem[] elencoInicial;

    /** Cenario de fundo atual. Muda quando uma Fala traz fundoNovo. */
    private String fundoAtual;

    private int indice = 0;

    /** Quantos caracteres da linha atual ja apareceram (efeito maquina de escrever). */
    private double charsMostrados = 0;

    /** Cronometro da linha atual: usado so pro cursor piscar. */
    private int t = 0;

    /** Bordas de subida pra Z/ENTER/ESC nao repetirem a acao a cada tick. */
    private boolean avancarAnterior = false;
    private boolean escAnterior = false;

    /** Ja tocou o efeito da fala atual? Evita repetir a cada tick. */
    private boolean somTocado = false;

    private double velocidadeTexto;

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
    }

    /** Volta a cena pro comeco. Chame antes de exibir de novo. */
    public void reiniciar() {
        indice = 0;
        charsMostrados = 0;
        t = 0;
        somTocado = false;
        fundoAtual = fundoInicial;
        elenco = elencoInicial;
    }

    public boolean acabou() {
        return indice >= falas.length;
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        if (acabou()) {
            return;
        }

        aplicarFundoDaFalaAtual();
        tocarSomDaFala();

        boolean avancar = Main.z || Main.enter;

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
        if (Main.esc && !escAnterior) {
            indice = falas.length;
        }
        escAnterior = Main.esc;

        if (!acabou()) {

            String texto = falas[indice].texto;

            if (charsMostrados < texto.length()) {
                charsMostrados = Math.min(texto.length(), charsMostrados + velocidadeTexto);
            }
        }

        t++;
    }

    /** Aplica as trocas de cenario e de elenco que a fala atual pedir. */
    private void aplicarFundoDaFalaAtual() {

        if (falas[indice].fundoNovo != null) {
            fundoAtual = falas[indice].fundoNovo;
        }

        if (falas[indice].elencoNovo != null) {
            elenco = falas[indice].elencoNovo;
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

    private void proximaLinha() {
        indice++;
        charsMostrados = 0;
        t = 0;
        somTocado = false;

        if (!acabou()) {
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

        if (acabou()) {
            return;
        }

        Fala atual = falas[indice];

        desenharFundo(g);

        if (atual.tipo == Tipo.TITULO) {
            renderTitulo(g, atual);
            return;
        }

        if (elenco.length > 0) {
            desenharRetratos(g, atual);
        }

        renderCaixaDeFala(g, atual);
    }

    /** Cenario de fundo, preenchendo o campo de jogo. */
    private void desenharFundo(Graphics2D g) {

        BufferedImage img = (fundoAtual == null) ? null : Assets.get(fundoAtual);

        if (img == null) {
            g.setColor(Color.BLACK);
            g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
            return;
        }

        g.drawImage(img, Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H, null);

        // Escurece a foto: sem isso o texto branco some no ceu claro.
        g.setColor(new Color(0, 0, 0, Config.getInt("cutscene.escurecimentoFundo", 110)));
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

            boolean falando = (atual.personagem == p);

            // Aparicao central (o JAJAVA): so existe enquanto fala.
            if (p.lado == Lado.CENTRO) {

                if (falando) {
                    desenharAparicaoCentral(g, img);
                }

                continue;
            }

            int altura = (int) (Main.CAMPO_H * (falando ? 0.46 : 0.42));
            int largura = img.getWidth() * altura / img.getHeight();

            // Levemente pra fora da borda do campo, so o suficiente pra dar
            // a sensacao de que continuam alem da tela.
            int x = (p.lado == Lado.ESQUERDA)
                  ? Main.CAMPO_X - largura / 12
                  : Main.CAMPO_X + Main.CAMPO_W - largura + largura / 12;

            int y = Main.CAMPO_Y + Main.CAMPO_H - altura - 190;

            Composite anterior = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                                      falando ? 1f : 0.8f));

            g.drawImage(img, x, y, largura, altura, null);

            g.setComposite(anterior);
        }
    }

    /**
     * Aparicao no centro do campo, pulsando — usada pelo JAJAVA, que e uma
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

    private static final String SPRITE_ESTUDANTE = "sprites/player/estudante.png";

    public static final Personagem ESTUDANTE = new Personagem(
        "Estudante", SPRITE_ESTUDANTE, Lado.ESQUERDA, new Color(70, 120, 200));

    public static final Personagem ADRIANA = new Personagem(
        "Adriana", "sprites/bosses/adriana-base.png", Lado.DIREITA, new Color(200, 90, 90));

    public static final Personagem ADRIANA_MALIGNA = new Personagem(
        "Adriana", "sprites/bosses/adriana-integralmaligna.png", Lado.DIREITA, new Color(220, 40, 40));

    /** O estudante depois de ativar o compilador ("ESPANDAAAAA"). */
    public static final Personagem ESTUDANTE_EXPANSIVO = new Personagem(
        "Estudante", "sprites/player/estudante_expansivo.png", Lado.ESQUERDA, new Color(70, 150, 220));

    public static final Personagem CLAYTON = new Personagem(
        "Clayton", "sprites/bosses/clayton-base.png", Lado.DIREITA, new Color(90, 160, 200));

    public static final Personagem CLAYTON_MALIGNO = new Personagem(
        "Clayton", "sprites/bosses/Clayton-Maligno.png", Lado.DIREITA, new Color(60, 200, 160));

    /** Voz mental: aparece no centro da tela, brilhando, so quando fala. */
    public static final Personagem JAJAVA = new Personagem(
        "JAJAVA", "sprites/bosses/jajava.png", Lado.CENTRO, new Color(150, 70, 220));

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

            // Roteiro.txt linha 8
            Fala.narracao("Esgueirando-se pela portaria 1 e chegando ao LEPEC, você "
                        + "percebe que vai precisar ir até o DCO buscar a chave de lá..."),

        }, null, "sprites/ambient/portaria.png");
    }

    /**
     * Encontro com a Adriana na frente da sala 7, ao fim do estagio 1
     * (Roteiro.txt linhas 11 a 26). Inclui a ativacao do compilador pelo
     * IVAN JAJAVA (linhas 13 a 17), que e o que da poder ao jogador.
     *
     * Formato: estilo Touhou, com retratos dos dois lados.
     */
    public static Cutscene criarEncontroAdriana() {

        return new Cutscene(new Fala[] {

            // Roteiro.txt linha 11-12
            Fala.narracaoComFundo("Você se sente acuado, como se ali fosse ser seu fim... até que...",
                                  "sprites/ambient/dco.png"),

            // Roteiro.txt linha 13
            Fala.fala(JAJAVA, "VOCÊ FOI ESCOLHIDO PELA EXPANSÃO... ATIVE VOSSO COMPILADOR!"),

            // Roteiro.txt linha 14
            Fala.fala(ESTUDANTE, "Quê? Que voz é essa?"),

            // Roteiro.txt linha 15
            Fala.fala(JAJAVA, "RÁPIDO: GRITE DA MATRIZ DO SEU SER... \"ESPANDAAAAA\"!"),

            // Roteiro.txt linha 16 — aqui o estudante ATIVA o poder, entao
            // o retrato dele troca pro sprite expansivo a partir desta fala.
            Fala.falaComElenco(ESTUDANTE, "Fazer o quê. ESPANDAAAAA!",
                               new Personagem[] { ESTUDANTE_EXPANSIVO, JAJAVA }),

            // Roteiro.txt linha 17
            Fala.narracao("Uma armadura de energia que se expande infinitamente te cobre, "
                        + "e você sente que tem o poder pra lutar."),

            // Roteiro.txt linha 19 — chegada na SALA 7: troca o cenario e
            // poe a Adriana em cena (na forma base, ainda nao transformada).
            Fala.narracaoComFundoEElenco(
                "Você segue rumo ao DCO. Porém, eventualmente, "
              + "ADRIANA APARECE NA FRENTE DA SALA 7.",
                "sprites/ambient/sala7.png",
                new Personagem[] { ESTUDANTE_EXPANSIVO, ADRIANA }),

            // Roteiro.txt linha 20
            Fala.fala(ADRIANA, "MAIS UM! VOCÊ SERÁ O PRÓXIMO CORROMPIDO!"),

            // Roteiro.txt linha 21
            Fala.fala(ESTUDANTE_EXPANSIVO, "Espera! Você entendeu erra—"),

            // Roteiro.txt linha 22-24
            Fala.fala(ADRIANA, "NÃO FALE MAIS NADA. Você vai se tornar mais um sólido da nossa "
                             + "revolução! DERRUBAREMOS O FILTRO E A MATRIZ POSITRÔNICA SERÁ MALIGNA!"),

            // Roteiro.txt linha 25
            Fala.fala(ESTUDANTE_EXPANSIVO, "COMPILADOR, ESPANDAAAAA!"),

        }, new Personagem[] { ESTUDANTE, JAJAVA }, "sprites/ambient/dco.png");
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
            Fala.fala(ESTUDANTE_EXPANSIVO, "O Jajava me codifica e nada me formatará."),

            // Roteiro.txt linha 29
            Fala.fala(ADRIANA, "VOCÊ..... VOCÊ VERÁ O PODER...."),

            // Roteiro.txt linha 30-32
            Fala.narracao("Silhuetas vermelhas começam a tomar forma em volta dela..."),

            // Roteiro.txt linha 31 — SO AQUI ela vira a forma maligna.
            // Antes desta fala o elenco usa o retrato normal: o sprite
            // vermelho estragaria a surpresa se aparecesse desde o inicio.
            Fala.falaComElenco(ADRIANA_MALIGNA,
                "DOS CACHORROS QUE SABEM CÁLCULO! DERIVEM ELE ATÉ O 0!",
                new Personagem[] { ESTUDANTE_EXPANSIVO, ADRIANA_MALIGNA }),

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
            Fala.falaComSom(CLAYTON, "VOCÊ JÁ OUVIU FALAR EM LATEX?", Som.CLAYTON_LATEX),

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
                             + "o todo poderoso, ops, Pai..... Bom dia a todos!"),

            // Roteiro.txt linha 49
            Fala.fala(ESTUDANTE_EXPANSIVO, "Cara, você não fala nada com nada."),

            // Roteiro.txt linha 50
            Fala.fala(CLAYTON, "Fuck you!!!"),

            // Roteiro.txt linha 51 — AQUI ele vira o Tab maligno.
            Fala.falaComElenco(CLAYTON_MALIGNO, "E aqui.... continuamos ... #focoforçaefé #Spark #recognas",
                               new Personagem[] { ESTUDANTE_EXPANSIVO, CLAYTON_MALIGNO }),

        }, new Personagem[] { ESTUDANTE_EXPANSIVO, CLAYTON }, "sprites/ambient/dco.png");
    }

    /**
     * Derrota do Clayton (Roteiro.txt linhas 53 a 57).
     */
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
}
