package src;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Le os valores de ajuste do jogo do arquivo "config/game.properties".
 *
 * A ideia e simples: nenhum numero de jogabilidade (velocidade, HP, cadencia
 * de tiro...) fica solto no meio do codigo. Todos vem daqui, e todos tem um
 * valor padrao embutido. Se o arquivo sumir ou vier com lixo, o jogo continua
 * rodando com os padroes e so avisa no console.
 *
 * Uso:  double vel = Config.getDouble("jogador.velocidade", 4.0);
 *
 * Classe utilitaria: nao da pra instanciar (construtor privado) e tudo e
 * estatico, porque a configuracao e uma so para o jogo inteiro.
 */
public final class Config {

    /** Caminhos tentados em ordem, para funcionar rodando da raiz ou de dentro de out/. */
    private static final String[] CAMINHOS = {
        "config/game.properties",
        "../config/game.properties",
        "../../config/game.properties"
    };

    private static final Properties props = new Properties();

    /** Onde o arquivo foi achado na ultima carga (null = usando so os padroes). */
    private static String caminhoCarregado = null;

    // Bloco estatico: roda uma unica vez, na primeira vez que alguem usa Config.
    static {
        recarregar();
    }

    /** Construtor privado: esta classe nunca e instanciada. */
    private Config() {
    }

    /**
     * (Re)le o arquivo de configuracao do disco.
     * Pode ser chamado em tempo de execucao (o jogo chama no F5) para
     * testar valores novos sem fechar a janela.
     */
    public static void recarregar() {

        props.clear();
        caminhoCarregado = null;

        for (String caminho : CAMINHOS) {

            File arquivo = new File(caminho);

            if (!arquivo.isFile()) {
                continue;
            }

            // try-with-resources: fecha o stream sozinho, mesmo se der excecao.
            try (InputStream in = new FileInputStream(arquivo)) {

                props.load(in);
                caminhoCarregado = arquivo.getPath();

                System.out.println("[Config] Carregado: " + arquivo.getAbsolutePath());
                return;

            } catch (IOException e) {
                System.err.println("[Config] Falha ao ler " + caminho + ": " + e.getMessage());
            }
        }

        System.err.println("[Config] Nenhum game.properties encontrado. Usando valores padrao.");
    }

    /** @return caminho do arquivo em uso, ou null se estiver so nos padroes. */
    public static String getCaminhoCarregado() {
        return caminhoCarregado;
    }

    /* =========================
            LEITORES
       =========================
       Todos seguem a mesma regra: se a chave nao existe ou o valor nao
       converte, devolve o padrao e avisa. O jogo nunca quebra por causa
       de um typo no .properties.
    */

    public static String getString(String chave, String padrao) {

        String valor = props.getProperty(chave);

        if (valor == null) {
            return padrao;
        }

        return valor.trim();
    }

    public static int getInt(String chave, int padrao) {

        String valor = props.getProperty(chave);

        if (valor == null) {
            return padrao;
        }

        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            avisarValorInvalido(chave, valor, String.valueOf(padrao));
            return padrao;
        }
    }

    public static double getDouble(String chave, double padrao) {

        String valor = props.getProperty(chave);

        if (valor == null) {
            return padrao;
        }

        try {
            return Double.parseDouble(valor.trim());
        } catch (NumberFormatException e) {
            avisarValorInvalido(chave, valor, String.valueOf(padrao));
            return padrao;
        }
    }

    public static boolean getBool(String chave, boolean padrao) {

        String valor = props.getProperty(chave);

        if (valor == null) {
            return padrao;
        }

        valor = valor.trim().toLowerCase();

        if (valor.equals("true") || valor.equals("sim") || valor.equals("1")) {
            return true;
        }

        if (valor.equals("false") || valor.equals("nao") || valor.equals("0")) {
            return false;
        }

        avisarValorInvalido(chave, valor, String.valueOf(padrao));
        return padrao;
    }

    private static void avisarValorInvalido(String chave, String valor, String padrao) {
        System.err.println("[Config] Valor invalido em '" + chave + "' = \"" + valor
                + "\". Usando padrao: " + padrao);
    }
}
