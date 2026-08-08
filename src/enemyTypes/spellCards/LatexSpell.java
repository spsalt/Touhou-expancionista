package src.enemyTypes.spellCards;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import src.Config;
import src.Main;
import src.Som;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD FINAL DO CLAYTON - "LATEX"
 *
 * "VOCE JA OUVIU FALAR EM LATEX?" (Roteiro.txt linha 42)
 *
 * Um artigo em LaTeX toma a tela inteira e desce. As LETRAS machucam; os
 * ESPACOS entre palavras, as entrelinhas e as margens sao seguros. O
 * jogador tem que se enfiar entre as palavras enquanto continua atirando.
 *
 * COMO A COLISAO FUNCIONA
 * -----------------------
 * Testar o jogador contra cada letra seria inviavel — sao centenas de
 * glifos, cada um com formato irregular. Em vez disso o texto e
 * RASTERIZADO UMA VEZ numa imagem transparente (a "mascara"), e a colisao
 * vira uma consulta de pixel:
 *
 *     alpha do pixel sob o jogador > 0  ->  ele esta encostando em tinta
 *
 * Ou seja, a hitbox e o proprio desenho, letra por letra, de graca e com
 * custo constante. E o mesmo truque de mascara de colisao que jogos 2D
 * usam pra cenario destrutivel.
 *
 * A mascara e desenhada uma unica vez no iniciar() e depois so rola pra
 * baixo — nao ha rasterizacao por frame.
 *
 * O texto e um lorem ipsum (texto de preenchimento classico, sem dono)
 * marcado com comandos de LaTeX de verdade, pra parecer um artigo real.
 */
public class LatexSpell extends SpellCard {

    /**
     * O "artigo". Lorem ipsum com marcacao LaTeX legitima — o conteudo e
     * proposital nao ter sentido, igual ao papel que o Clayton mandaria
     * voce formatar.
     */
    private static final String[] LINHAS = {
        "\\documentclass[12pt]{article}",
        "\\usepackage[utf8]{inputenc}",
        "\\usepackage{amsmath,graphicx}",
        "",
        "\\title{Lorem Ipsum Sistematico}",
        "\\author{C. Clayton \\and DCO}",
        "\\date{\\today} \\maketitle",
        "",
        "\\section{Introducao}",
        "Lorem ipsum dolor sit amet,",
        "consectetur adipiscing elit,",
        "sed do eiusmod tempor ut",
        "labore et dolore magna aliqua.",
        "",
        "\\begin{equation}",
        "  \\int_{0}^{1} f(x) dx =",
        "  \\lim_{n\\to\\infty} \\sum f(x_i)",
        "\\end{equation}",
        "",
        "\\subsection{Metodologia}",
        "Duis aute irure dolor in",
        "reprehenderit in voluptate",
        "velit esse cillum dolore eu",
        "fugiat nulla pariatur sint.",
        "",
        "\\begin{itemize}",
        "  \\item Sunt in culpa qui",
        "  \\item Anim id est laborum",
        "  \\item Nam libero tempore",
        "\\end{itemize}",
        "",
        "\\section{Resultados}",
        "Sed ut perspiciatis unde",
        "omnis iste natus error sit",
        "voluptatem accusantium et",
        "doloremque laudantium totam.",
        "",
        "\\begin{equation}",
        "  \\frac{\\partial u}{\\partial t}",
        "  = \\alpha \\nabla^{2} u",
        "\\end{equation}",
        "",
        "\\begin{tabular}{|l|c|r|}",
        "  \\hline Lorem & Ipsum \\\\",
        "  \\hline Sit & Amet \\\\ \\hline",
        "\\end{tabular}",
        "",
        "\\section{Conclusao}",
        "Nemo enim ipsam voluptatem",
        "quia voluptas sit aspernatur",
        "aut odit aut fugit, sed quia",
        "consequuntur magni dolores.",
        "",
        "\\bibliographystyle{plain}",
        "\\end{document}"
    };

    /** A pagina rasterizada. Alpha > 0 = tinta = machuca. */
    private BufferedImage mascara;

    /** Quanto a pagina ja rolou, em pixels. */
    private double offset;

    private final double velocidade;
    private final int tamanhoFonte;
    private final int alturaLinha;
    private final int margemEsquerda;

    /** Pixels a mais entre cada letra (abre frestas finas no meio da palavra). */
    private final int espacoEntreLetras;

    /** Pixels a mais em cada espaco (abre os corredores entre palavras). */
    private final int espacoExtraPalavra;

    /** Recuo da coluna de numeros de linha em relacao a borda direita. */
    private final int margemDireita;

    /** Ticks antes do texto comecar a descer (deixa a voiceline soar). */
    private final int ticksDeEspera;

    /** Dano por tick encostado na tinta e o contador do intervalo. */
    private int intervaloDeDano;
    private int cooldownDano = 0;

    /** Quanto do raio do jogador conta contra a tinta (0 a 1). */
    private final double fatorHitbox;

    public LatexSpell() {

        super("LATEX",
              Config.getDouble("clayton.latex.hp", 460),
              Config.getInt("clayton.latex.duracao", 2600));

        this.velocidade      = Config.getDouble("clayton.latex.velocidade", 1.15);
        this.tamanhoFonte    = Config.getInt("clayton.latex.tamanhoFonte", 20);
        this.alturaLinha     = Config.getInt("clayton.latex.alturaLinha", 34);
        this.margemEsquerda  = Config.getInt("clayton.latex.margemEsquerda", 18);
        this.espacoEntreLetras  = Config.getInt("clayton.latex.espacoEntreLetras", 2);
        this.espacoExtraPalavra = Config.getInt("clayton.latex.espacoExtraPalavra", 7);
        this.margemDireita      = Config.getInt("clayton.latex.margemDireita", 14);
        this.ticksDeEspera   = Config.getInt("clayton.latex.ticksDeEspera", 90);
        this.intervaloDeDano = Math.max(1, Config.getInt("clayton.latex.intervaloDeDano", 30));
        this.fatorHitbox = Math.max(0.05, Math.min(1,
                Config.getDouble("clayton.latex.fatorHitbox", 0.55)));
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        // "VOCE JA OUVIU FALAR EM LATEX?"
        Som.tocar(Som.CLAYTON_LATEX);

        offset = 0;
        cooldownDano = 0;

        rasterizarPagina();
    }

    /**
     * Desenha o artigo inteiro numa imagem transparente, uma unica vez.
     * Fonte monoespacada de proposito: e o que faz o texto parecer codigo
     * e, na pratica, deixa os espacos entre palavras com largura regular —
     * corredores previsiveis pro jogador se enfiar.
     */
    private void rasterizarPagina() {

        int altura = Math.max(1, LINHAS.length * alturaLinha + alturaLinha);

        mascara = new BufferedImage(Main.CAMPO_W, altura, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = mascara.createGraphics();

        // Antialiasing DESLIGADO de proposito: com ele as bordas das letras
        // viram pixels semi-transparentes, e o jogador levaria dano de uma
        // "sombra" que mal da pra ver. Sem ele, tinta e tinta.
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_OFF);

        // NEGRITO e proposital: engrossa o traco de cada glifo, entao a
        // parede de texto fica solida em vez de fiapo. Com traco fino, a
        // colisao por pixel dava a impressao de que da pra atravessar letra.
        //
        // ATENCAO ao mexer no tamanho: em negrito cabem ~42 caracteres por
        // linha nos 600px do campo. Fonte maior faz as linhas do artigo
        // estourarem a largura e serem cortadas na borda da mascara.
        Font fonte = new Font("Monospaced", Font.BOLD, tamanhoFonte);
        g.setFont(fonte);

        FontMetrics fm = g.getFontMetrics();

        for (int i = 0; i < LINHAS.length; i++) {

            String linha = LINHAS[i];

            if (linha.isEmpty()) {
                continue;
            }

            // Comandos LaTeX (comecam com \) saem em outra cor, como num
            // editor de verdade. Machucam igual — e so leitura.
            boolean comando = linha.trim().startsWith("\\");

            g.setColor(comando ? new Color(120, 200, 255) : new Color(235, 235, 245));

            int linhaBase = alturaLinha * (i + 1) - (alturaLinha - fm.getAscent()) / 2;
            desenharComEspacamento(g, fm, linha, margemEsquerda, linhaBase);

            desenharNumeroDaLinha(g, fm, i, linhaBase);
        }

        g.dispose();
    }

    /**
     * Desenha a linha UM CARACTERE POR VEZ, controlando o avanco do cursor.
     *
     * drawString normal cola as letras umas nas outras e usa a largura
     * padrao do espaco — o resultado era uma parede quase continua, sem
     * frestas por onde passar. Aqui:
     *
     *   - cada letra avanca um pouco a mais (espacoEntreLetras), o que abre
     *     uma fresta fina entre glifo e glifo
     *   - o espaco entre palavras avanca MUITO mais (espacoExtraPalavra),
     *     virando um corredor vertical de verdade
     *
     * E o que transforma o texto de "parede" em "labirinto".
     *
     * ATENCAO: mexer nestes dois valores muda a LARGURA das linhas. Com os
     * atuais cabem ~34 caracteres por linha nos 600px do campo; passando
     * disso o artigo e cortado na borda da mascara.
     */
    private void desenharComEspacamento(Graphics2D g, FontMetrics fm,
                                        String linha, int x, int y) {

        double cursor = x;

        for (int i = 0; i < linha.length(); i++) {

            char c = linha.charAt(i);

            if (c != ' ') {
                g.drawString(String.valueOf(c), (int) cursor, y);
            }

            cursor += fm.charWidth(c) + espacoEntreLetras;

            if (c == ' ') {
                cursor += espacoExtraPalavra;
            }
        }
    }

    /**
     * Numero da linha, alinhado a DIREITA da pagina (como num editor).
     *
     * Nao e enfeite: o artigo tem linhas curtas, entao a faixa direita do
     * campo ficava vazia e virava um corredor seguro onde dava pra ficar
     * parado o ataque inteiro. Esta coluna ocupa aquela faixa e obriga o
     * jogador a se mexer tambem la.
     */
    private void desenharNumeroDaLinha(Graphics2D g, FontMetrics fm, int indice, int y) {

        String marca = "% " + (indice + 1);

        // Alinhado pela DIREITA: calcula a largura e recua a partir da borda.
        int largura = 0;

        for (int i = 0; i < marca.length(); i++) {
            largura += fm.charWidth(marca.charAt(i)) + espacoEntreLetras;
        }

        int x = Main.CAMPO_W - margemDireita - largura;

        g.setColor(new Color(120, 200, 255));
        desenharComEspacamento(g, fm, marca, x, y);
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (t < ticksDeEspera || mascara == null) {
            return;
        }

        offset += velocidade;

        // Rola em loop: o artigo nunca acaba, igual a lista de correcoes.
        if (offset >= mascara.getHeight()) {
            offset -= mascara.getHeight();
        }

        if (cooldownDano > 0) {
            cooldownDano--;
        }

        verificarColisaoComOTexto();
    }

    /**
     * Le o pixel da mascara sob o jogador. Alpha > 0 = encostou em letra.
     *
     * Testa alguns pontos ao redor do centro (nao so um) porque a hitbox
     * do jogador tem raio: com um pixel so, ele passaria "de raspao" por
     * dentro de uma letra fina sem levar dano.
     */
    private void verificarColisaoComOTexto() {

        if (Main.player == null || cooldownDano > 0) {
            return;
        }

        double px = Main.player.getX();
        double py = Main.player.getY();

        // Raio ENCOLHIDO pelo fator: as letras continuam do mesmo tamanho
        // na tela, mas o jogador precisa entrar mais fundo na tinta pra
        // levar dano. E o que deixa o ataque generoso sem mudar o visual.
        double r = Main.player.getRadius() * fatorHitbox;

        double[][] pontos = {
            { px, py }, { px - r, py }, { px + r, py }, { px, py - r }, { px, py + r }
        };

        for (double[] p : pontos) {

            if (temTinta(p[0], p[1]) && Main.player.levarDano()) {
                cooldownDano = intervaloDeDano;
                return;
            }
        }
    }

    /** true se a posicao de tela cai em cima de tinta da pagina. */
    private boolean temTinta(double telaX, double telaY) {

        int mx = (int) (telaX - Main.CAMPO_X);

        // Coordenada Y dentro da pagina, considerando a rolagem em loop.
        int my = (int) (telaY - Main.CAMPO_Y - offset + mascara.getHeight()) % mascara.getHeight();

        if (mx < 0 || mx >= mascara.getWidth() || my < 0 || my >= mascara.getHeight()) {
            return false;
        }

        return (mascara.getRGB(mx, my) >>> 24) > 0;
    }

    /**
     * Desenha a pagina descendo. Chamado pelo Clayton no render dele —
     * spell card normalmente nao desenha nada, mas este ataque E o desenho.
     */
    @Override
    public void render(Graphics2D g) {

        if (mascara == null) {
            return;
        }

        int h = mascara.getHeight();
        int y0 = Main.CAMPO_Y + (int) offset - h;

        // Duas copias emendadas cobrem o campo em qualquer posicao do loop.
        for (int y = y0; y < Main.CAMPO_Y + Main.CAMPO_H; y += h) {
            g.drawImage(mascara, Main.CAMPO_X, y, null);
        }
    }
}
