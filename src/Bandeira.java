package src;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Catalogo de bandeiras de paises, DESENHADAS POR RECEITA.
 *
 * O ataque do PAPA sorteia paises do mundo inteiro. Guardar um PNG por
 * pais daria mais de cem arquivos pra versionar — entao aqui a bandeira e
 * descrita como DADO (um layout + ate quatro cores) e a imagem e montada
 * na primeira vez que o pais e sorteado, ficando em cache depois.
 *
 * A maioria esmagadora das bandeiras do mundo cai em meia duzia de
 * layouts: tribanda (vertical ou horizontal), bicolor, cruz nordica,
 * disco central e cantao. Com esses cinco moldes da pra representar bem
 * uns sessenta paises sem desenhar nada na mao.
 *
 * HONESTIDADE: bandeiras com brasao, estrela ou emblema (Brasil, EUA,
 * Reino Unido...) nao estao no catalogo — a receita nao alcanca esse
 * nivel de detalhe, e uma versao errada seria pior que a ausencia. Pra
 * adicionar um pais novo, basta uma linha no vetor PAISES.
 */
public final class Bandeira {

    /** Os moldes. Cada um sabe se desenhar em desenhar(). */
    public enum Layout {
        /** Tres faixas verticais. Ex: Franca, Italia, Irlanda. */
        TRIBANDA_VERTICAL,
        /** Tres faixas horizontais. Ex: Alemanha, Russia, Hungria. */
        TRIBANDA_HORIZONTAL,
        /** Duas faixas horizontais. Ex: Polonia, Ucrania, Indonesia. */
        BICOLOR_HORIZONTAL,
        /** Duas faixas verticais. Ex: Portugal (aproximado), Argelia. */
        BICOLOR_VERTICAL,
        /** Cruz deslocada pra esquerda. Ex: Suecia, Dinamarca, Finlandia. */
        CRUZ_NORDICA,
        /** Cruz centralizada. Ex: Suica. */
        CRUZ_CENTRAL,
        /** Fundo liso com um disco no meio. Ex: Japao, Bangladesh. */
        DISCO,
        /** Tribanda horizontal com disco por cima. Ex: India, Niger. */
        TRIBANDA_HORIZONTAL_DISCO,
        /** Fundo liso com um retangulo no canto superior esquerdo. Ex: Taiwan. */
        CANTAO
    }

    /* --- paleta reaproveitada, pra as linhas do catalogo caberem numa linha --- */

    private static final int VERMELHO = 0xCE1126;
    private static final int VERMELHO_ESCURO = 0x9E1B32;
    private static final int AZUL     = 0x0033A0;
    private static final int AZUL_CLARO = 0x4EA1DF;
    private static final int AZUL_ESCURO = 0x002868;
    private static final int VERDE    = 0x009639;
    private static final int VERDE_ESCURO = 0x006A4E;
    private static final int AMARELO  = 0xFFD100;
    private static final int LARANJA  = 0xFF7900;
    private static final int BRANCO   = 0xFFFFFF;
    private static final int PRETO    = 0x141414;
    private static final int DOURADO  = 0xFFCE00;
    private static final int VINHO    = 0x9D2235;

    /* =========================
            O CATALOGO
       ========================= */

    /**
     * Os paises sorteaveis. Ordem nao importa — o sorteio e uniforme.
     *
     * Pra acrescentar um pais: copie uma linha, troque o nome, o layout e
     * as cores. Nao precisa mexer em mais nada; o ataque le este vetor.
     */
    public static final Bandeira[] PAISES = {

        // --- tribanda vertical ---
        new Bandeira("Franca",           Layout.TRIBANDA_VERTICAL, AZUL, BRANCO, VERMELHO),
        new Bandeira("Italia",           Layout.TRIBANDA_VERTICAL, VERDE, BRANCO, VERMELHO),
        new Bandeira("Irlanda",          Layout.TRIBANDA_VERTICAL, VERDE, BRANCO, LARANJA),
        new Bandeira("Belgica",          Layout.TRIBANDA_VERTICAL, PRETO, AMARELO, VERMELHO),
        new Bandeira("Romenia",          Layout.TRIBANDA_VERTICAL, AZUL, AMARELO, VERMELHO),
        new Bandeira("Chade",            Layout.TRIBANDA_VERTICAL, AZUL_ESCURO, AMARELO, VERMELHO),
        new Bandeira("Mali",             Layout.TRIBANDA_VERTICAL, VERDE, AMARELO, VERMELHO),
        new Bandeira("Guine",            Layout.TRIBANDA_VERTICAL, VERMELHO, AMARELO, VERDE),
        new Bandeira("Costa do Marfim",  Layout.TRIBANDA_VERTICAL, LARANJA, BRANCO, VERDE),
        new Bandeira("Nigeria",          Layout.TRIBANDA_VERTICAL, VERDE, BRANCO, VERDE),
        new Bandeira("Peru",             Layout.TRIBANDA_VERTICAL, VERMELHO, BRANCO, VERMELHO),
        new Bandeira("Mexico",           Layout.TRIBANDA_VERTICAL, VERDE, BRANCO, VERMELHO),
        new Bandeira("Guine-Bissau",     Layout.TRIBANDA_VERTICAL, VERMELHO, AMARELO, VERDE),
        new Bandeira("Andorra",          Layout.TRIBANDA_VERTICAL, AZUL, AMARELO, VERMELHO),

        // --- tribanda horizontal ---
        new Bandeira("Alemanha",         Layout.TRIBANDA_HORIZONTAL, PRETO, VERMELHO, DOURADO),
        new Bandeira("Holanda",          Layout.TRIBANDA_HORIZONTAL, VERMELHO, BRANCO, AZUL),
        new Bandeira("Russia",           Layout.TRIBANDA_HORIZONTAL, BRANCO, AZUL, VERMELHO),
        new Bandeira("Colombia",         Layout.TRIBANDA_HORIZONTAL, AMARELO, AZUL, VERMELHO),
        new Bandeira("Austria",          Layout.TRIBANDA_HORIZONTAL, VERMELHO, BRANCO, VERMELHO),
        new Bandeira("Letonia",          Layout.TRIBANDA_HORIZONTAL, VINHO, BRANCO, VINHO),
        new Bandeira("Hungria",          Layout.TRIBANDA_HORIZONTAL, VERMELHO, BRANCO, VERDE),
        new Bandeira("Bulgaria",         Layout.TRIBANDA_HORIZONTAL, BRANCO, VERDE, VERMELHO),
        new Bandeira("Estonia",          Layout.TRIBANDA_HORIZONTAL, AZUL_CLARO, PRETO, BRANCO),
        new Bandeira("Lituania",         Layout.TRIBANDA_HORIZONTAL, AMARELO, VERDE, VERMELHO),
        new Bandeira("Gabao",            Layout.TRIBANDA_HORIZONTAL, VERDE, AMARELO, AZUL),
        new Bandeira("Armenia",          Layout.TRIBANDA_HORIZONTAL, VERMELHO, AZUL, LARANJA),
        new Bandeira("Serra Leoa",       Layout.TRIBANDA_HORIZONTAL, VERDE, BRANCO, AZUL_CLARO),
        new Bandeira("Iemen",            Layout.TRIBANDA_HORIZONTAL, VERMELHO, BRANCO, PRETO),
        new Bandeira("Luxemburgo",       Layout.TRIBANDA_HORIZONTAL, VERMELHO, BRANCO, AZUL_CLARO),
        new Bandeira("Paraguai",         Layout.TRIBANDA_HORIZONTAL, VERMELHO, BRANCO, AZUL),
        new Bandeira("Bolivia",          Layout.TRIBANDA_HORIZONTAL, VERMELHO, AMARELO, VERDE),
        new Bandeira("Sudao do Sul",     Layout.TRIBANDA_HORIZONTAL, PRETO, VERMELHO, VERDE),

        // --- bicolor ---
        new Bandeira("Polonia",          Layout.BICOLOR_HORIZONTAL, BRANCO, VERMELHO),
        new Bandeira("Indonesia",        Layout.BICOLOR_HORIZONTAL, VERMELHO, BRANCO),
        new Bandeira("Monaco",           Layout.BICOLOR_HORIZONTAL, VERMELHO, BRANCO),
        new Bandeira("Ucrania",          Layout.BICOLOR_HORIZONTAL, AZUL_CLARO, AMARELO),
        new Bandeira("San Marino",       Layout.BICOLOR_HORIZONTAL, BRANCO, AZUL_CLARO),
        new Bandeira("Haiti",            Layout.BICOLOR_HORIZONTAL, AZUL, VERMELHO),
        new Bandeira("Portugal",         Layout.BICOLOR_VERTICAL, VERDE_ESCURO, VERMELHO),
        new Bandeira("Argelia",          Layout.BICOLOR_VERTICAL, VERDE, BRANCO),
        new Bandeira("Malta",            Layout.BICOLOR_VERTICAL, BRANCO, VERMELHO),

        // --- cruzes ---
        new Bandeira("Suecia",           Layout.CRUZ_NORDICA, AZUL, AMARELO),
        new Bandeira("Dinamarca",        Layout.CRUZ_NORDICA, VERMELHO, BRANCO),
        new Bandeira("Finlandia",        Layout.CRUZ_NORDICA, BRANCO, AZUL),
        new Bandeira("Islandia",         Layout.CRUZ_NORDICA, AZUL, BRANCO),
        new Bandeira("Suica",            Layout.CRUZ_CENTRAL, VERMELHO, BRANCO),
        new Bandeira("Tonga",            Layout.CANTAO, VERMELHO, BRANCO),
        new Bandeira("Taiwan",           Layout.CANTAO, VERMELHO, AZUL),

        // --- discos ---
        new Bandeira("Japao",            Layout.DISCO, BRANCO, VERMELHO),
        new Bandeira("Bangladesh",       Layout.DISCO, VERDE_ESCURO, VERMELHO),
        new Bandeira("Palau",            Layout.DISCO, AZUL_CLARO, AMARELO),
        new Bandeira("India",            Layout.TRIBANDA_HORIZONTAL_DISCO, LARANJA, BRANCO, VERDE, AZUL),
        new Bandeira("Niger",            Layout.TRIBANDA_HORIZONTAL_DISCO, LARANJA, BRANCO, VERDE, LARANJA),
        new Bandeira("Laos",             Layout.TRIBANDA_HORIZONTAL_DISCO, VERMELHO_ESCURO, AZUL, VERMELHO_ESCURO, BRANCO),
    };

    /* =========================
            INSTANCIA
       ========================= */

    private final String nome;
    private final Layout layout;

    /** Ate quatro cores; quais sao usadas depende do layout. */
    private final int cor1, cor2, cor3, cor4;

    /** Cache da imagem montada. Uma bandeira sorteada mil vezes desenha uma so. */
    private static final Map<String, BufferedImage> cache = new HashMap<>();

    private Bandeira(String nome, Layout layout, int cor1, int cor2) {
        this(nome, layout, cor1, cor2, cor1, cor2);
    }

    private Bandeira(String nome, Layout layout, int cor1, int cor2, int cor3) {
        this(nome, layout, cor1, cor2, cor3, cor1);
    }

    private Bandeira(String nome, Layout layout, int cor1, int cor2, int cor3, int cor4) {
        this.nome = nome;
        this.layout = layout;
        this.cor1 = cor1;
        this.cor2 = cor2;
        this.cor3 = cor3;
        this.cor4 = cor4;
    }

    /** Sorteia um pais do catalogo. */
    public static Bandeira sortear(Random rng) {
        return PAISES[rng.nextInt(PAISES.length)];
    }

    /**
     * A imagem da bandeira, montada na primeira chamada.
     *
     * Sempre no mesmo tamanho base (LARGURA x ALTURA); quem desenha
     * redimensiona. Manter um tamanho unico e o que permite o cache — se
     * cada bandeira fosse montada no tamanho do momento, o cache nao
     * serviria pra nada.
     */
    public BufferedImage imagem() {

        BufferedImage pronta = cache.get(nome);

        if (pronta != null) {
            return pronta;
        }

        BufferedImage img = new BufferedImage(LARGURA, ALTURA, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        desenhar(g);

        // Contorno escuro: sem ele, bandeira de fundo branco some contra o
        // ceu claro do cenario e o jogador nao ve o que vem pra cima dele.
        g.setColor(new Color(20, 20, 30));
        g.drawRect(0, 0, LARGURA - 1, ALTURA - 1);

        g.dispose();

        cache.put(nome, img);

        return img;
    }

    /** Tamanho base da imagem montada. Proporcao 3:2, a mais comum do mundo. */
    public static final int LARGURA = 36;
    public static final int ALTURA  = 24;

    private void desenhar(Graphics2D g) {

        int L = LARGURA;
        int A = ALTURA;

        switch (layout) {

            case TRIBANDA_VERTICAL:
                faixa(g, cor1, 0,       0, L / 3,         A);
                faixa(g, cor2, L / 3,   0, L / 3,         A);
                faixa(g, cor3, 2 * L / 3, 0, L - 2 * L / 3, A);
                break;

            case TRIBANDA_HORIZONTAL:
                faixa(g, cor1, 0, 0,         L, A / 3);
                faixa(g, cor2, 0, A / 3,     L, A / 3);
                faixa(g, cor3, 0, 2 * A / 3, L, A - 2 * A / 3);
                break;

            case BICOLOR_HORIZONTAL:
                faixa(g, cor1, 0, 0,     L, A / 2);
                faixa(g, cor2, 0, A / 2, L, A - A / 2);
                break;

            case BICOLOR_VERTICAL:
                // 2/5 e 3/5: e a proporcao de Portugal, e fica melhor que
                // meio a meio nas bandeiras verticais de duas cores.
                faixa(g, cor1, 0,           0, (2 * L) / 5, A);
                faixa(g, cor2, (2 * L) / 5, 0, L - (2 * L) / 5, A);
                break;

            case CRUZ_NORDICA:
                // A cruz nordica nao e centralizada: o braco vertical fica
                // deslocado pra esquerda (a 3/8 da largura). E exatamente
                // isso que distingue a familia escandinava de uma cruz comum.
                faixa(g, cor1, 0, 0, L, A);
                faixa(g, cor2, 0, A / 2 - 3, L, 6);
                faixa(g, cor2, (3 * L) / 8 - 3, 0, 6, A);
                break;

            case CRUZ_CENTRAL:
                faixa(g, cor1, 0, 0, L, A);
                faixa(g, cor2, L / 2 - 3, A / 4, 6, A / 2);
                faixa(g, cor2, L / 4, A / 2 - 3, L / 2, 6);
                break;

            case DISCO:
                faixa(g, cor1, 0, 0, L, A);
                disco(g, cor2, L / 2, A / 2, A / 3);
                break;

            case TRIBANDA_HORIZONTAL_DISCO:
                faixa(g, cor1, 0, 0,         L, A / 3);
                faixa(g, cor2, 0, A / 3,     L, A / 3);
                faixa(g, cor3, 0, 2 * A / 3, L, A - 2 * A / 3);
                disco(g, cor4, L / 2, A / 2, A / 5);
                break;

            case CANTAO:
                faixa(g, cor1, 0, 0, L, A);
                faixa(g, cor2, 0, 0, L / 2, A / 2);
                break;

            default:
                faixa(g, cor1, 0, 0, L, A);
                break;
        }
    }

    private void faixa(Graphics2D g, int rgb, int x, int y, int larg, int alt) {
        g.setColor(new Color(rgb));
        g.fillRect(x, y, larg, alt);
    }

    private void disco(Graphics2D g, int rgb, int cx, int cy, int raio) {
        g.setColor(new Color(rgb));
        g.fillOval(cx - raio, cy - raio, raio * 2, raio * 2);
    }

    /* =========================
            GETTERS
       ========================= */

    public String getNome() {
        return nome;
    }

    public Layout getLayout() {
        return layout;
    }

    /** Cor principal — usada pra tingir o rastro e o aviso de mira. */
    public Color getCorPrincipal() {
        return new Color(cor1);
    }
}
