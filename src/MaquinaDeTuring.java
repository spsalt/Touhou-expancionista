package src;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UMA MAQUINA DE TURING DE VERDADE.
 *
 * Fita infinita pros dois lados, um cabecote, uma tabela de transicao. A
 * cada passo ela le o simbolo sob o cabecote, procura a regra
 * (estado, lido) na tabela e faz o que ela manda: escreve um simbolo, anda
 * pra um lado e troca de estado.
 *
 * POR QUE ISTO EXISTE
 * -------------------
 * O ataque do PAPA mostrava uma fita de LETRAS SORTEADAS. Funcionava como
 * jogo — voce digita o que aparece — mas era mentira: nao havia programa
 * nenhum ali, so ruido com cara de computacao. Num trabalho de um curso
 * que ENSINA maquina de Turing, isso e o tipo de detalhe que um professor
 * nota em dois segundos.
 *
 * Agora a fita do ataque e o TRACO DE EXECUCAO desta classe rodando um
 * programa real. Cada celula que o jogador digita e um simbolo que a
 * maquina de fato escreveu, na ordem em que escreveu.
 *
 * O PROGRAMA QUE ELA RODA (ver reconhecedor0n1n) e o exemplo canonico de
 * qualquer curso de computacao teorica: reconhecer a linguagem 0ⁿ1ⁿ, ou
 * seja, decidir se uma cadeia tem o mesmo tanto de zeros e de uns, nessa
 * ordem. E a linguagem mais famosa que NAO e regular — nenhum automato
 * finito consegue, porque teria que "contar" quantos zeros viu, e contar
 * exige memoria ilimitada. A maquina de Turing consegue justamente porque
 * pode escrever na fita: ela marca um zero, procura um um, marca, volta, e
 * repete ate nao sobrar nenhum.
 *
 * SOBRE O ALFABETO: os simbolos foram escolhidos entre os que o jogador
 * consegue digitar sem efeito colateral. O 'A' e um zero ja pareado e o
 * 'B' e um um ja pareado — na notacao dos livros seriam X e Y, mas X e
 * tecla de foco no jogo e Z e tecla de tiro.
 */
public class MaquinaDeTuring {

    /** Fim de fita: o que existe onde a maquina nunca escreveu. */
    public static final char BRANCO = '_';

    /** Um passo da execucao: o que ela leu, o que escreveu e onde estava. */
    public static class Passo {

        public final String estado;
        public final char lido;
        public final char escrito;
        public final int posicao;

        Passo(String estado, char lido, char escrito, int posicao) {
            this.estado = estado;
            this.lido = lido;
            this.escrito = escrito;
            this.posicao = posicao;
        }
    }

    /** O que fazer ao ler um simbolo num estado. */
    private static class Regra {

        final char escreve;
        final int passo;          // +1 direita, -1 esquerda, 0 parado
        final String proximo;

        Regra(char escreve, int passo, String proximo) {
            this.escreve = escreve;
            this.passo = passo;
            this.proximo = proximo;
        }
    }

    /** Estado em que a maquina para e aceita a entrada. */
    public static final String ACEITA = "ACEITA";

    private final Map<String, Regra> tabela = new HashMap<>();
    private final String estadoInicial;

    public MaquinaDeTuring(String estadoInicial) {
        this.estadoInicial = estadoInicial;
    }

    /** Adiciona uma regra: no estado X, lendo Y, escreva Z, ande e va pro W. */
    public MaquinaDeTuring regra(String estado, char lido,
                                 char escreve, int passo, String proximo) {

        tabela.put(estado + "|" + lido, new Regra(escreve, passo, proximo));

        return this;
    }

    /**
     * Roda a maquina e devolve o TRACO: a lista de passos, em ordem.
     *
     * O teto de passos nao e paranoia: uma maquina de Turing pode nao
     * parar nunca, e esse e o Problema da Parada — nao existe jeito de
     * saber de antemao se ela vai terminar. Num jogo, isso significa que
     * um erro na tabela travaria a partida. O teto transforma "trava pra
     * sempre" em "para e devolve o que deu".
     */
    public List<Passo> executar(String entrada, int maximoDePassos) {

        List<Character> fita = new ArrayList<>();

        for (int i = 0; i < entrada.length(); i++) {
            fita.add(entrada.charAt(i));
        }

        List<Passo> traco = new ArrayList<>();

        int pos = 0;
        String estado = estadoInicial;

        for (int n = 0; n < maximoDePassos; n++) {

            // Fita infinita a direita: onde nunca se escreveu, e branco.
            while (pos >= fita.size()) {
                fita.add(BRANCO);
            }

            if (pos < 0) {
                break;
            }

            char lido = fita.get(pos);

            Regra r = tabela.get(estado + "|" + lido);

            // Sem regra = a maquina trava e REJEITA. E um final legitimo,
            // nao um erro do programa.
            if (r == null) {
                break;
            }

            traco.add(new Passo(estado, lido, r.escreve, pos));

            fita.set(pos, r.escreve);

            if (ACEITA.equals(r.proximo)) {
                break;
            }

            pos += r.passo;
            estado = r.proximo;
        }

        return traco;
    }

    /* =========================================================
            O PROGRAMA
       ========================================================= */

    /**
     * A maquina que reconhece 0ⁿ1ⁿ.
     *
     * A ideia, em uma frase: marque o primeiro zero livre, ande pra
     * direita ate achar o primeiro um livre, marque ele tambem, volte e
     * repita. Se acabarem juntos, aceita.
     *
     *   q0  procura o proximo 0 livre (marca com A e vai pro q1)
     *   q1  anda pra direita procurando um 1 livre (marca com B, volta)
     *   q2  volta pra esquerda ate o ultimo 0 marcado
     *   q3  todos os 0 marcados: confere se so sobrou B ate o fim
     *
     * A -> zero ja pareado. B -> um ja pareado. (Nos livros seriam X e Y;
     * aqui X e tecla de foco e Z e tecla de tiro.)
     */
    public static MaquinaDeTuring reconhecedor0n1n() {

        MaquinaDeTuring m = new MaquinaDeTuring("q0");

        // q0: achou 0 livre -> marca e sai procurando o 1
        m.regra("q0", '0', 'A',  1, "q1");
        // q0: so tem marcado pra frente -> hora de conferir o resto
        m.regra("q0", 'B', 'B',  1, "q3");

        // q1: anda pra direita por cima do que ja passou
        m.regra("q1", '0', '0',  1, "q1");
        m.regra("q1", 'B', 'B',  1, "q1");
        // q1: achou o 1 livre -> marca e volta
        m.regra("q1", '1', 'B', -1, "q2");

        // q2: volta ate encontrar o ultimo 0 marcado
        m.regra("q2", '0', '0', -1, "q2");
        m.regra("q2", 'B', 'B', -1, "q2");
        m.regra("q2", 'A', 'A',  1, "q0");

        // q3: so pode sobrar B ate o fim da fita
        m.regra("q3", 'B', 'B',  1, "q3");
        m.regra("q3", BRANCO, BRANCO, 0, ACEITA);

        return m;
    }

    /** A entrada canonica: n zeros seguidos de n uns. */
    public static String entrada0n1n(int n) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < n; i++) {
            sb.append('0');
        }

        for (int i = 0; i < n; i++) {
            sb.append('1');
        }

        return sb.toString();
    }
}
