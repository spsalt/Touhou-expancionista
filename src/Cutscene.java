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
        DIREITA
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

        private Fala(Tipo tipo, Personagem personagem, String texto, String subtitulo, String fundoNovo) {
            this.tipo = tipo;
            this.personagem = personagem;
            this.texto = texto;
            this.subtitulo = subtitulo;
            this.fundoNovo = fundoNovo;
        }

        public static Fala titulo(String texto, String subtitulo) {
            return new Fala(Tipo.TITULO, null, texto, subtitulo, null);
        }

        public static Fala narracao(String texto) {
            return new Fala(Tipo.NARRACAO, null, texto, null, null);
        }

        /** Narracao que tambem troca o cenario de fundo. */
        public static Fala narracaoComFundo(String texto, String fundo) {
            return new Fala(Tipo.NARRACAO, null, texto, null, fundo);
        }

        public static Fala fala(Personagem quem, String texto) {
            return new Fala(Tipo.FALA, quem, texto, null, null);
        }

        /** Fala que tambem troca o cenario de fundo. */
        public static Fala falaComFundo(Personagem quem, String texto, String fundo) {
            return new Fala(Tipo.FALA, quem, texto, null, fundo);
        }
    }

    private final Fala[] falas;

    /** Personagens que aparecem de retrato nesta cena (vazio = so narracao). */
    private final Personagem[] elenco;

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

    private double velocidadeTexto;

    /** Guardado pra poder voltar ao inicio no reiniciar(). */
    private final String fundoInicial;

    public Cutscene(Fala[] falas, Personagem[] elenco, String fundoInicial) {
        this.falas = falas;
        this.elenco = (elenco == null) ? new Personagem[0] : elenco;
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
        fundoAtual = fundoInicial;
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

    private void aplicarFundoDaFalaAtual() {

        if (falas[indice].fundoNovo != null) {
            fundoAtual = falas[indice].fundoNovo;
        }
    }

    private void proximaLinha() {
        indice++;
        charsMostrados = 0;
        t = 0;

        if (!acabou()) {
            aplicarFundoDaFalaAtual();
        }
    }

    /* =========================
            RENDER
       ========================= */

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

        // Retratos so aparecem se a cena tiver elenco (estilo Touhou).
        if (elenco.length > 0) {
            desenharRetratos(g, atual);
        }

        renderCaixaDeFala(g, atual);
    }

    /** Cenario de fundo, ou preto se a cena nao tiver foto. */
    private void desenharFundo(Graphics2D g) {

        BufferedImage img = (fundoAtual == null) ? null : Assets.get(fundoAtual);

        if (img == null) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);
            return;
        }

        g.drawImage(img, 0, 0, Main.WIDTH, Main.HEIGHT, null);

        // Escurece a foto: sem isso o texto branco some no ceu claro.
        g.setColor(new Color(0, 0, 0, Config.getInt("cutscene.escurecimentoFundo", 90)));
        g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);
    }

    /**
     * Retratos dos personagens nos dois lados, estilo Touhou.
     *
     * Quem esta falando fica aceso e um pouco maior; quem esta calado fica
     * escurecido. E o truque visual que faz a conversa ser legivel de
     * relance, sem depender de ler o nome na caixa.
     */
    private void desenharRetratos(Graphics2D g, Fala atual) {

        for (Personagem p : elenco) {

            BufferedImage img = Assets.get(p.sprite);

            if (img == null) {
                continue;
            }

            boolean falando = (atual.personagem == p);

            // Quem fala aparece um pouco maior. Os valores sao contidos de
            // proposito: os retratos aqui sao recortes de rosto, nao
            // ilustracoes de corpo inteiro como na serie original — passando
            // de ~0.6 da altura da tela eles saem cortados pelas bordas e
            // parece defeito em vez de enquadramento.
            int altura = (int) (Main.HEIGHT * (falando ? 0.55 : 0.50));
            int largura = img.getWidth() * altura / img.getHeight();

            // Levemente pra fora da borda, so o suficiente pra dar a
            // sensacao de que continuam alem da tela.
            int x = (p.lado == Lado.ESQUERDA)
                  ? -largura / 12
                  : Main.WIDTH - largura + largura / 12;

            int y = Main.HEIGHT - altura - 170;

            // Quem esta calado fica mais transparente, e so.
            //
            // A versao anterior desenhava um veu escuro por cima pra
            // "apagar" o personagem — mas fillRect pinta o RETANGULO
            // inteiro, nao a silhueta do sprite, entao o retrato virava
            // um bloco preto. Baixar o alpha da propria imagem resolve
            // com uma linha e respeita o recorte do PNG.
            Composite anterior = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, falando ? 1f : 0.45f));

            g.drawImage(img, x, y, largura, altura, null);

            g.setComposite(anterior);
        }
    }

    private void renderTitulo(Graphics2D g, Fala f) {

        // Cartela de titulo sempre em fundo preto solido, pra dar impacto.
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);

        g.setFont(new Font("Monospaced", Font.BOLD, 44));
        g.setColor(new Color(255, 220, 120));

        FontMetrics fm = g.getFontMetrics();
        g.drawString(f.texto, (Main.WIDTH - fm.stringWidth(f.texto)) / 2, Main.HEIGHT / 2 - 10);

        if (f.subtitulo != null) {

            g.setFont(new Font("Monospaced", Font.PLAIN, 20));
            g.setColor(new Color(200, 200, 200));

            FontMetrics fmSub = g.getFontMetrics();
            g.drawString(f.subtitulo, (Main.WIDTH - fmSub.stringWidth(f.subtitulo)) / 2, Main.HEIGHT / 2 + 28);
        }

        desenharPrompt(g);
    }

    /** Barras pretas em cima/embaixo + caixa de texto no rodape, estilo visual novel. */
    private void renderCaixaDeFala(Graphics2D g, Fala f) {

        int margem = 60;
        int caixaAltura = 150;
        int caixaY = Main.HEIGHT - caixaAltura - 40;
        int caixaLargura = Main.WIDTH - margem * 2;

        // Faixas escuras acima e abaixo, pra dar o clima "cinema".
        g.setColor(new Color(0, 0, 0, 230));
        g.fillRect(0, 0, Main.WIDTH, 70);
        g.fillRect(0, Main.HEIGHT - 70, Main.WIDTH, 70);

        // Painel da caixa de texto.
        g.setColor(new Color(15, 15, 30, 235));
        g.fillRoundRect(margem, caixaY, caixaLargura, caixaAltura, 16, 16);

        g.setColor(new Color(90, 90, 140));
        g.drawRoundRect(margem, caixaY, caixaLargura, caixaAltura, 16, 16);

        int textoX = margem + 24;
        int textoY = caixaY + 34;

        if (f.tipo == Tipo.FALA && f.personagem != null) {

            // Etiqueta com o nome do personagem, encostada no topo da caixa.
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            FontMetrics fmNome = g.getFontMetrics();
            int larguraNome = fmNome.stringWidth(f.personagem.nome) + 24;

            g.setColor(f.personagem.cor);
            g.fillRoundRect(margem + 16, caixaY - 18, larguraNome, 28, 8, 8);

            g.setColor(Color.WHITE);
            g.drawString(f.personagem.nome, margem + 28, caixaY);

            textoY = caixaY + 46;
        }

        // Corpo do texto: italico pra narracao, normal pra fala.
        int estilo = (f.tipo == Tipo.NARRACAO) ? Font.ITALIC : Font.PLAIN;
        g.setFont(new Font("Monospaced", estilo, 18));
        g.setColor(f.tipo == Tipo.NARRACAO ? new Color(210, 210, 220) : Color.WHITE);

        String visivel = f.texto.substring(0, (int) charsMostrados);

        desenharTextoComQuebra(g, visivel, textoX, textoY, caixaLargura - 48, 24);

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

    /** "Z para continuar", piscando — some enquanto o texto ainda esta digitando. */
    private void desenharPrompt(Graphics2D g) {

        if (charsMostrados < falas[indice].texto.length()) {
            return;
        }

        // Pisca: visivel em metade dos ciclos de 40 ticks.
        if ((t / 40) % 2 != 0) {
            return;
        }

        String prompt = (indice == falas.length - 1) ? "Z para comecar" : "Z para continuar";

        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(180, 180, 100));

        FontMetrics fm = g.getFontMetrics();
        g.drawString(prompt, Main.WIDTH - fm.stringWidth(prompt) - 60, Main.HEIGHT - 50);
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

    public static final Personagem IVAN = new Personagem(
        "IVAN JAJAVA", SPRITE_ESTUDANTE, Lado.ESQUERDA, new Color(180, 140, 60));

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
            Fala.fala(IVAN, "VOCÊ FOI ESCOLHIDO PELA EXPANSÃO... ATIVE VOSSO COMPILADOR!"),

            // Roteiro.txt linha 14
            Fala.fala(ESTUDANTE, "Quê? Que voz é essa?"),

            // Roteiro.txt linha 15
            Fala.fala(IVAN, "RÁPIDO: GRITE DA MATRIZ DO SEU SER... \"ESPANDAAAAA\"!"),

            // Roteiro.txt linha 16
            Fala.fala(ESTUDANTE, "Fazer o quê. ESPANDAAAAA!"),

            // Roteiro.txt linha 17
            Fala.narracao("Uma armadura de energia que se expande infinitamente te cobre, "
                        + "e você sente que tem o poder pra lutar."),

            // Roteiro.txt linha 19
            Fala.narracao("Você segue rumo ao DCO. Porém, eventualmente, "
                        + "ADRIANA APARECE NA FRENTE DA SALA 7."),

            // Roteiro.txt linha 20
            Fala.fala(ADRIANA, "MAIS UM! VOCÊ SERÁ O PRÓXIMO CORROMPIDO!"),

            // Roteiro.txt linha 21
            Fala.fala(ESTUDANTE, "Espera! Você entendeu erra—"),

            // Roteiro.txt linha 22-24
            Fala.fala(ADRIANA, "NÃO FALE MAIS NADA. Você vai se tornar mais um sólido da nossa "
                             + "revolução! DERRUBAREMOS O FILTRO E A MATRIZ POSITRÔNICA SERÁ MALIGNA!"),

            // Roteiro.txt linha 25
            Fala.fala(ESTUDANTE, "COMPILADOR, ESPANDAAAAA!"),

        }, new Personagem[] { ESTUDANTE, ADRIANA }, "sprites/ambient/dco.png");
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
            Fala.fala(ESTUDANTE, "O Jajava me codifica e nada me formatará."),

            // Roteiro.txt linha 29
            Fala.fala(ADRIANA, "VOCÊ..... VOCÊ VERÁ O PODER...."),

            // Roteiro.txt linha 30-32
            Fala.narracao("Silhuetas vermelhas começam a tomar forma em volta dela..."),

            // Roteiro.txt linha 31
            Fala.fala(ADRIANA_MALIGNA, "DOS CACHORROS QUE SABEM CÁLCULO! DERIVEM ELE ATÉ O 0!"),

        }, new Personagem[] { ESTUDANTE, ADRIANA_MALIGNA }, "sprites/ambient/dco.png");
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

            // Roteiro.txt linha 37
            Fala.fala(ADRIANA, "O quê? O que aconteceu? Por que a faculdade tá vazia?"),

            // Roteiro.txt linha 38
            Fala.fala(ADRIANA, "Não importa. Tenho um congresso pra ir."),

            // Roteiro.txt linha 39
            Fala.fala(ESTUDANTE, "Ufa, foi por pouco. Vou tomar cuidado e ir escondido."),

            // Roteiro.txt linha 40
            Fala.narracao("Você vai devagar para o DCO, sem ninguém te ver. "
                        + "Realmente um trabalho sólido te deixou invisível."),

        }, new Personagem[] { ESTUDANTE, ADRIANA }, "sprites/ambient/dco.png");
    }
}
