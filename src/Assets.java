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
     * @param caminho caminho relativo a raiz do projeto, ex "sprites/enemies/tabmaligno.png"
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
