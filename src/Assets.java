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
    }
}
