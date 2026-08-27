package src;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Carrega e guarda em cache os sprites do jogo.
 *
 * Regra importante: se um PNG nao existir, get() devolve null em vez de
 * explodir. Quem desenha deve checar null e cair pra uma forma geometrica.
 * Assim da pra ir codando as fases antes da arte ficar pronta, e o jogo
 * nunca trava por falta de arquivo.
 *
 * Uso:  BufferedImage img = Assets.get("sprites/bosses/adriana-base.png");
 *       if (img != null) g.drawImage(...); else g.fillOval(...);
 */
public final class Assets {

    /** Prefixos tentados, pra funcionar rodando da raiz do repo ou de out/. */
    private static final String[] RAIZES = { "", "../", "../../" };

    /** Cache: caminho pedido -> imagem (ou null se ja tentamos e falhou). */
    private static final Map<String, BufferedImage> cache = new HashMap<>();

    private Assets() {
    }

    /**
     * Devolve a imagem do caminho pedido, carregando do disco na primeira vez.
     *
     * @param caminho caminho relativo a raiz do projeto, ex "sprites/enemies/inimigo.png"
     * @return a imagem, ou null se nao foi possivel carregar
     */
    public static BufferedImage get(String caminho) {

        // containsKey e nao != null: guardamos o null tambem, pra nao ficar
        // tentando ler do disco um arquivo que ja sabemos que nao existe.
        if (cache.containsKey(caminho)) {
            return cache.get(caminho);
        }

        BufferedImage img = carregar(caminho);
        cache.put(caminho, img);

        return img;
    }

    /** Cache das versoes JA REDIMENSIONADAS: "caminho@LxA" -> imagem. */
    private static final Map<String, BufferedImage> cacheEscalado = new HashMap<>();

    /**
     * A imagem ja no tamanho pedido, redimensionada UMA VEZ e reaproveitada.
     *
     * POR QUE ISSO EXISTE: o corredor dos Seguidores do IEEE chega a 270
     * balas na tela, cada uma com sprite. Chamando drawImage com largura e
     * altura, o Java2D reescala o PNG A CADA CHAMADA — sao 270
     * redimensionamentos por frame, 60 vezes por segundo, sempre pro mesmo
     * tamanho. Guardando o resultado, o desenho vira uma copia direta.
     *
     * O cache e por TAMANHO INTEIRO, entao balas de raios diferentes nao
     * brigam entre si, e o numero de entradas e pequeno por natureza (um
     * tipo de bala usa um ou dois tamanhos a vida toda).
     *
     * Devolve null nas mesmas condicoes do get(): sem arquivo, sem imagem.
     */
    public static BufferedImage getEscalado(String caminho, int largura, int altura) {

        if (largura <= 0 || altura <= 0) {
            return null;
        }

        String chave = caminho + "@" + largura + "x" + altura;

        if (cacheEscalado.containsKey(chave)) {
            return cacheEscalado.get(chave);
        }

        BufferedImage original = get(caminho);

        if (original == null) {
            cacheEscalado.put(chave, null);
            return null;
        }

        BufferedImage saida = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = saida.createGraphics();

        // Vizinho mais proximo: os sprites das balas sao pixel art, e
        // interpolacao suave borraria justamente as bordas duras que fazem
        // elas serem legiveis no meio de uma tela cheia.
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g.drawImage(original, 0, 0, largura, altura, null);
        g.dispose();

        cacheEscalado.put(chave, saida);

        return saida;
    }

    private static final Map<String, BufferedImage> cacheTingido = new HashMap<>();

    /**
     * A imagem RECOLORIDA pra uma cor, ja no tamanho pedido.
     *
     * POR QUE ISSO PRECISOU EXISTIR
     * -----------------------------
     * As balas do jogador tem sprite (bala_leque.png e companhia), e o
     * desenharSprite() do Bullet, quando acha o PNG, desenha ELE e ignora
     * a cor da bala por completo. Ou seja: a paleta da skin estava sendo
     * calculada, guardada e passada adiante — e nao aparecia em lugar
     * nenhum, porque o PNG azul era desenhado por cima de tudo.
     *
     * COMO A RECOLORACAO E FEITA: troca o MATIZ, mantem o BRILHO.
     *
     * Nao da pra so multiplicar pela cor nova: os PNGs ja sao coloridos
     * (azul, verde, laranja), e multiplicar cor por cor da lama. Aqui cada
     * pixel e convertido pra HSB, recebe o matiz da cor nova, e a
     * saturacao dele e MULTIPLICADA pela da cor nova em vez de
     * substituida.
     *
     * Essa multiplicacao e o detalhe que faz a coisa parecer desenhada e
     * nao filtrada: o miolo branco da bala tem saturacao quase zero, entao
     * ele continua branco; so as bordas, que sao saturadas, assumem a cor
     * nova. O brilho (B) nao e tocado em ponto nenhum, entao o volume e as
     * bordas duras do pixel art sobrevivem inteiros.
     *
     * O cache e por caminho + cor + tamanho. Ele nao cresce: sao tres
     * sprites de bala vezes as skins que existirem.
     */
    public static BufferedImage getTingido(String caminho, java.awt.Color cor,
                                           int largura, int altura) {

        if (cor == null || largura <= 0 || altura <= 0) {
            return getEscalado(caminho, largura, altura);
        }

        String chave = caminho + "#" + cor.getRGB() + "@" + largura + "x" + altura;

        if (cacheTingido.containsKey(chave)) {
            return cacheTingido.get(chave);
        }

        BufferedImage base = getEscalado(caminho, largura, altura);

        if (base == null) {
            cacheTingido.put(chave, null);
            return null;
        }

        float[] alvo = java.awt.Color.RGBtoHSB(cor.getRed(), cor.getGreen(), cor.getBlue(), null);

        // BRILHO NORMALIZADO PELO DA COR ESCOLHIDA.
        //
        // So trocar o matiz nao basta: o PNG do ponteiro e um verde de
        // brilho medio, e pintar aquele mesmo brilho de amarelo da CAQUI —
        // uma cor que nao parece nem o verde original nem o ambar do menu.
        //
        // Medindo o brilho medio do sprite e reescalando pro brilho da cor
        // alvo, a bala sai com a claridade que a cor promete. Uma cor
        // clara clareia o sprite inteiro, uma escura escurece.
        float somaBrilho = 0;
        int opacos = 0;

        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {

                int argb = base.getRGB(x, y);

                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }

                float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF,
                                                      (argb >> 8) & 0xFF,
                                                      argb & 0xFF, null);
                somaBrilho += hsb[2];
                opacos++;
            }
        }

        float medio = (opacos > 0) ? somaBrilho / opacos : 1f;
        float ganho = (medio > 0.01f) ? alvo[2] / medio : 1f;

        BufferedImage saida = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {

                int argb = base.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;

                if (a == 0) {
                    continue;
                }

                float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF,
                                                      (argb >> 8) & 0xFF,
                                                      argb & 0xFF, null);

                // O 1.25 e compensacao: multiplicar duas saturacoes sempre
                // da um numero menor que as duas, entao a bala saia mais
                // lavada do que a cor escolhida no menu. O min() garante
                // que o miolo branco (saturacao ~0) continue branco — e
                // ele que da o "nucleo quente" das balas.
                float sat = Math.min(1f, hsb[1] * alvo[1] * 1.25f);

                float bri = Math.min(1f, hsb[2] * ganho);

                int rgb = java.awt.Color.HSBtoRGB(alvo[0], sat, bri);

                saida.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
            }
        }

        cacheTingido.put(chave, saida);

        return saida;
    }

    private static BufferedImage carregar(String caminho) {

        File arquivo = resolverArquivo(caminho);

        if (arquivo == null) {
            System.err.println("[Assets] Nao encontrei \"" + caminho + "\". Vou desenhar uma forma no lugar.");
            return null;
        }

        try {
            BufferedImage img = ImageIO.read(arquivo);

            if (img != null) {
                System.out.println("[Assets] Carregado: " + arquivo.getPath());
            }

            return img;

        } catch (IOException e) {
            System.err.println("[Assets] Erro lendo " + arquivo.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Acha um arquivo qualquer (imagem, audio...) tentando os mesmos
     * prefixos de pasta que as imagens usam. Publico porque outras classes
     * de carregamento (como Musica) precisam do mesmo truque, sem duplicar
     * a busca em RAIZES.
     *
     * @return o arquivo, ou null se nao existir em nenhuma das raizes
     */
    public static File resolverArquivo(String caminho) {

        for (String raiz : RAIZES) {

            File arquivo = new File(raiz + caminho);

            if (arquivo.isFile()) {
                return arquivo;
            }
        }

        return null;
    }

    /** Esvazia o cache, forcando reler os PNGs do disco (usado no hot-reload F5). */
    public static void limparCache() {
        cache.clear();

        // Os derivados TAMBEM: eles guardam copias da imagem antiga
        // redimensionada e recolorida. Sem limpar aqui, trocar um PNG (ou
        // uma cor de skin) e apertar F5 continuaria mostrando o antigo,
        // porque o cache responderia antes de alguem reler o arquivo.
        cacheEscalado.clear();
        cacheTingido.clear();
    }
}
