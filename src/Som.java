package src;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Efeitos sonoros curtos (tiros), separados da Musica de fundo.
 *
 * POR QUE UM POOL DE CLIPS: um Clip so toca uma instancia por vez — pedir
 * play() enquanto ele ainda esta soando simplesmente nao faz nada. Como o
 * jogador atira ~12x por segundo, com um Clip unico a maioria dos tiros
 * sairia muda. Entao cada som guarda VARIOS Clips iguais e o play() usa o
 * primeiro que estiver livre, em rodizio.
 *
 * Igual a Musica, e blindado contra falha: sem placa de som, sem arquivo
 * ou com formato nao suportado, o jogo segue mudo e so avisa no console.
 */
public final class Som {

    /* =========================
            CATALOGO DE EFEITOS
       =========================
       Sao os SE da propria serie, que ja vem mixados entre si — tiro e
       discreto, explosao e forte. O volume global cuida da maior parte,
       mas cada som pode ter um GANHO PROPRIO no properties
       ("som.ganho.<nome do arquivo sem extensao>"): ha efeitos que tocam
       doze vezes por segundo e efeitos que tocam uma vez por luta, e um
       volume unico nao serve pros dois. Ver ganhoDe().

       Os arquivos vem em 8 e 16 bits e em varias taxas (11k/22k/44k). O
       javax.sound aceita todos, e quem falhar cai no tratamento de erro
       do carregar() sem derrubar nada.
    */

    /* --- combate --- */
    public static final String TIRO_JOGADOR   = "audio/se_plst00.wav";
    public static final String TIRO_PONTEIRO  = "audio/se_msl.wav";
    public static final String TIRO_RICOCHETE = "audio/se_pin00.wav";
    public static final String QUIQUE         = "audio/se_pin01.wav";
    public static final String TIRO_INIMIGO   = "audio/se_tan00.wav";
    public static final String INIMIGO_MORRE  = "audio/se_enep00.wav";
    public static final String JOGADOR_DANO   = "audio/se_pldead00.wav";

    /** Foi atingido, mas a janela de deathbomb ainda esta aberta. */
    public static final String QUASE_MORRE    = "audio/se_damage00.wav";
    public static final String GPT_EXPANSION  = "audio/se_slash.wav";
    public static final String FOCO           = "audio/se_focusin.wav";

    /** Rocou numa bala. Bem curtinho: toca muito, nao pode cansar. */
    public static final String GRAZE          = "audio/se_graze.wav";

    /* --- progressao --- */
    public static final String ITEM           = "audio/se_item00.wav";
    public static final String SUBIR_NIVEL    = "audio/se_powerup.wav";

    /** Peso cubano coletado. Diferente do item de XP DE PROPOSITO: os dois
     *  caem juntos, e o ouvido separa os dois mais rapido que o olho. */
    public static final String MOEDA          = "audio/se_cardget.wav";

    /** Compra fechada na lojinha do Perea. */
    public static final String COMPRA         = "audio/se_ch00.wav";

    /** Sem dinheiro pra comprar. */
    public static final String SEM_GRANA      = "audio/se_cancel00.wav";

    /** O OLHO LASER acendendo. */
    public static final String OLHO_LASER     = "audio/se_lazer00.wav";

    /** O raio batendo e mordendo o alvo. Toca em intervalos, nao todo tick. */
    public static final String LASER_BATE     = "audio/se_lazer01.wav";

    /** Os drones da AGRICULTURA DIGITAL decolando. */
    public static final String DRONE          = "audio/se_nep00.wav";

    /* --- chefe --- */
    public static final String SPELL_INICIA   = "audio/se_cat00.wav";
    public static final String SPELL_QUEBRA   = "audio/se_cardget.wav";
    public static final String CHEFE_MORRE    = "audio/se_enep01.wav";
    /**
     * Voiceline do Clayton: "VOCE JA OUVIU FALAR EM LATEX?"
     *
     * E o som mais alto do jogo inteiro, de proposito (ver o ganho dele no
     * properties). Nao e efeito: e a fala que ABRE o ataque final dele, e
     * ela precisa passar por cima da musica e da tela cheia de bala.
     */
    public static final String CLAYTON_LATEX  = "audio/latex_voz.wav";

    /* =========================================================
            UM SOM POR ATAQUE
       =========================================================
       Cada spell card tem efeito proprio, e nao um "tiro de inimigo"
       generico. O motivo e de leitura, nao de enfeite: numa tela cheia,
       o ouvido percebe que ALGO NOVO comecou antes do olho achar o que
       foi. Som repetido entre ataques desperdica esse aviso.

       TODOS vem do pacote oficial de efeitos da serie. Houve uma tentativa
       de sintetizar efeitos tematicos aqui (uma "turbina" pro solido de
       revolucao, bipes de dados pro OPF) e o resultado destoava feio: som
       gerado com seno e ruido branco nao combina com sample de jogo real,
       e a diferenca de qualidade fica gritante quando os dois tocam
       seguidos. Escolher bem dentro do pacote deu um resultado melhor do
       que sintetizar — o pacote tem mais de cem efeitos, e quase sempre
       existe um que serve.

       As UNICAS coisas ainda sintetizadas sao as vozes das cutscenes
       (voz_*.wav), que sao bleeps de uma nota por personagem — outro
       papel, e propositalmente cru.
    */

    /* --- ADRIANA --- */

    /** Integral e Somatorio: o glifo sendo tracado (varredura pra cima). */
    public static final String ADRIANA_GLIFO     = "audio/se_tan02.wav";

    /** Solido de Revolucao: turbina girando, com a modulacao do "revolver". */
    public static final String ADRIANA_REVOLUCAO = "audio/se_ufo.wav";

    /**
     * Latido de cada tiro. Cachorro DE VERDADE, nao tiro generico.
     *
     * Feito a partir do se_wolf.wav do proprio pacote: o uivo original e
     * um som continuo de 2,5 s sem ataque nenhum, entao cortar um pedaco
     * soaria como fragmento de rosnado. O que esta aqui e uma fatia do
     * trecho mais encorpado, reamostrada pra cima (uivo de lobo -> latido
     * de cachorro), com glide descendente e envelope de ataque
     * instantaneo e queda rapida — que e a forma de onda de um latido.
     *
     * Continua sendo sample real, so esculpido. Sintetizar do zero foi
     * tentado antes e destoou feio do resto.
     */
    public static final String CACHORRO_LATIDO   = "audio/cachorro_latido.wav";

    /** O uivo da invocacao da matilha. Acontece uma vez, entao pode ser longo. */
    public static final String CACHORRO_UIVO     = "audio/se_wolf.wav";

    /* --- CLAYTON --- */

    /** Peca de xadrez batendo no tabuleiro. */
    public static final String XADREZ_LANCE      = "audio/se_don00.wav";

    /** Claytonling investindo. */
    public static final String CLAYTONLING       = "audio/se_piyo.wav";

    /** A criatura do anticodigo chutando a bola. Um baque seco. */
    public static final String CHUTE             = "audio/se_tan01.wav";

    /* --- PAPA --- */

    /**
     * Bandeira fechou a mira e travou a direcao.
     *
     * Um "clinc" curto, e tocado UMA VEZ por leva (ver BandeirasSpell):
     * seis bandeiras travam no mesmo frame, e seis copias de um alarme de
     * tres segundos era exatamente o tipo de barulho que nao informa nada.
     */
    public static final String PAPA_MIRA     = "audio/se_ice.wav";

    /** Bandeira saiu. */
    public static final String PAPA_AVANCA   = "audio/se_kira00.wav";

    /** Simbolo certo digitado na fita da maquina de Turing. */
    public static final String TURING_OK     = "audio/se_ok00.wav";

    /** Simbolo errado, ou o relogio zerou. */
    public static final String TURING_ERRO   = "audio/se_cancel00.wav";

    /** Um no foi conquistado por um prototipo, no Optimum Path Forest. */
    public static final String OPF_CONQUISTA = "audio/se_ch03.wav";

    /** A aresta candidata comecou a piscar: um "scan" antes do disparo. */
    public static final String OPF_SCAN      = "audio/se_ch01.wav";

    /** O veredito sobre o jogador: alarme descendente. */
    public static final String OPF_VEREDITO  = "audio/se_ufoalert.wav";

    /* --- vozes de cutscene (bleeps 8-bit, um pitch por personagem) --- */
    public static final String VOZ_ESTUDANTE = "audio/voz_estudante.wav";
    public static final String VOZ_ADRIANA   = "audio/voz_adriana.wav";
    public static final String VOZ_CLAYTON   = "audio/voz_clayton.wav";
    public static final String VOZ_SANTO_JAVA    = "audio/voz_santo_java.wav";
    public static final String VOZ_NARRADOR  = "audio/voz_narrador.wav";
    public static final String VOZ_PAPA      = "audio/voz_papa.wav";

    /* --- interface --- */
    public static final String MENU_MOVER     = "audio/se_select00.wav";
    public static final String MENU_OK        = "audio/se_ok00.wav";
    public static final String PAUSA          = "audio/se_pause.wav";
    public static final String GAME_OVER      = "audio/se_playerdead.wav";

    /**
     * Um pool de Clips por arquivo.
     *
     * LinkedHashMap em modo ACCESS-ORDER: ele reordena sozinho a cada
     * consulta, entao o primeiro elemento e sempre o som usado ha mais
     * tempo. E o que permite despejar o menos usado quando estouramos o
     * limite de linhas de audio (ver garantirEspaco).
     */
    private static final Map<String, Clip[]> pools =
            new java.util.LinkedHashMap<>(16, 0.75f, true);

    /** Quantos Clips estao abertos agora, somando todos os pools. */
    private static int clipsAbertos = 0;

    /** Proximo Clip a tentar, por arquivo (rodizio). */
    private static final Map<String, Integer> proximo = new HashMap<>();

    private static boolean ativado = true;
    private static float volume = 0.25f;

    private Som() {
    }

    /** (Re)le os ajustes e descarrega o cache. Chamado no F5. */
    public static void carregarConfig() {

        ativado = Config.getBool("som.ativado", true);
        volume  = (float) Config.getDouble("som.volume", 0.35);

        for (Clip[] pool : pools.values()) {
            for (Clip c : pool) {
                c.stop();
                c.close();
            }
        }

        pools.clear();
        proximo.clear();
        clipsAbertos = 0;
    }

    /**
     * Toca um efeito. Se todos os Clips do pool estiverem ocupados,
     * reaproveita o mais antigo (corta e recomeca) — melhor um tiro
     * cortado que um tiro mudo.
     */
    public static void tocar(String caminho) {

        if (!ativado) {
            return;
        }

        Clip[] pool = pools.get(caminho);

        if (pool == null) {
            pool = carregar(caminho);
            pools.put(caminho, pool);
            proximo.put(caminho, 0);
        }

        if (pool.length == 0) {
            return;
        }

        // Procura um Clip parado.
        for (Clip c : pool) {
            if (!c.isRunning()) {
                c.setFramePosition(0);
                c.start();
                return;
            }
        }

        // Todos ocupados: corta o da vez do rodizio.
        int i = proximo.get(caminho);
        pool[i].stop();
        pool[i].setFramePosition(0);
        pool[i].start();

        proximo.put(caminho, (i + 1) % pool.length);
    }

    /**
     * Abre ate N copias do mesmo arquivo. Pool vazio = som desligado.
     *
     * DOIS CUIDADOS QUE PARECEM PARANOIA E NAO SAO:
     *
     * 1) Se uma das copias falhar por falta de linha de audio, ficamos
     *    com as que ja abriram em vez de desistir do som inteiro. A
     *    versao anterior devolvia pool VAZIO nesse caso, e o efeito ficava
     *    mudo pra sempre — foi assim que o som de graze sumiu: ele e um
     *    dos ultimos a ser carregado (so toca quando voce roça a
     *    primeira bala) e ate la o mixer ja nao tinha linha sobrando.
     *
     * 2) O total de Clips abertos e limitado. O jogo tem mais de trinta
     *    efeitos; com varias copias de cada, passa fácil do que a placa
     *    aceita, e a partir dai TODO som novo falha. Antes de abrir,
     *    despejamos o pool do som menos usado.
     */
    private static Clip[] carregar(String caminho) {

        File arquivo = Assets.resolverArquivo(caminho);

        if (arquivo == null) {
            System.err.println("[Som] Nao encontrei \"" + caminho + "\". Segue sem este efeito.");
            return new Clip[0];
        }

        int desejado = Math.max(1, Config.getInt("som.vozes", 3));

        garantirEspaco(desejado);

        java.util.List<Clip> abertos = new java.util.ArrayList<>();

        for (int i = 0; i < desejado; i++) {

            try (AudioInputStream stream = AudioSystem.getAudioInputStream(arquivo)) {

                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                ajustarVolume(clip, ganhoDe(caminho));

                abertos.add(clip);
                clipsAbertos++;

            } catch (LineUnavailableException e) {

                // Sem linha sobrando: fica com o que deu. Uma copia so ja
                // toca o som, mesmo que cortando quando repetir rapido.
                System.err.println("[Som] Sem linha pra mais copias de " + caminho
                                 + " (abriu " + abertos.size() + "). Segue assim.");
                break;

            } catch (UnsupportedAudioFileException | IOException e) {

                // Esses dois sao problema do ARQUIVO, nao do mixer: nao
                // adianta tentar de novo.
                System.err.println("[Som] Falha carregando " + caminho + ": " + e.getMessage());
                break;
            }
        }

        if (abertos.isEmpty()) {
            return new Clip[0];
        }

        System.out.println("[Som] Carregado: " + arquivo.getPath()
                         + " (" + abertos.size() + " vozes, " + clipsAbertos + " no total)");

        return abertos.toArray(new Clip[0]);
    }

    /**
     * Fecha pools antigos ate caber mais 'quantos' Clips no orcamento.
     *
     * Despeja o som usado ha mais tempo (o primeiro do LinkedHashMap em
     * access-order). Som despejado nao some do jogo: da proxima vez que
     * tocar, ele recarrega — custa um engasgo de milissegundos numa vez
     * so, o que e infinitamente melhor que ficar mudo.
     */
    private static void garantirEspaco(int quantos) {

        int limite = Math.max(8, Config.getInt("som.maximoDeClips", 48));

        java.util.Iterator<Map.Entry<String, Clip[]>> it = pools.entrySet().iterator();

        while (clipsAbertos + quantos > limite && it.hasNext()) {

            Map.Entry<String, Clip[]> velho = it.next();

            for (Clip c : velho.getValue()) {
                c.stop();
                c.close();
                clipsAbertos--;
            }

            proximo.remove(velho.getKey());
            it.remove();
        }
    }

    /**
     * O ganho proprio de um efeito, lido de "som.ganho.<nome do arquivo>".
     *
     * POR QUE EXISTE: o volume dos efeitos e baixo porque o tiro do jogador
     * sai umas doze vezes por segundo e, alto, ele vira um chiado continuo
     * que cobre a musica. So que o MESMO numero valia pra coisas que tocam
     * uma vez por luta — a voz do Clayton, o spell card quebrando — e essas
     * sumiam. Um multiplicador por som resolve os dois casos sem precisar
     * reeditar arquivo nenhum.
     *
     * Sem chave no properties o ganho e 1.0, ou seja, tudo continua como
     * estava. So os poucos sons que destoam precisam de linha la.
     */
    private static float ganhoDe(String caminho) {

        String nome = caminho;

        int barra = nome.lastIndexOf('/');

        if (barra >= 0) {
            nome = nome.substring(barra + 1);
        }

        int ponto = nome.lastIndexOf('.');

        if (ponto > 0) {
            nome = nome.substring(0, ponto);
        }

        return (float) Config.getDouble("som.ganho." + nome, 1.0);
    }

    private static void ajustarVolume(Clip clip, float ganho) {

        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        FloatControl controle = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        // MASTER_GAIN e em decibeis: converte o volume linear e prende
        // dentro do que o controle aceita (senao da IllegalArgumentException).
        //
        // O teto de 1.0 e do proprio controle: ele so ATENUA, nunca
        // amplifica alem do arquivo. Por isso ganho alto nao "estoura" —
        // ele apenas leva o som ate o volume original do wav. Quem quiser
        // um efeito mais alto que isso tem que mexer no arquivo, e foi
        // exatamente o que fizemos com a voz do LaTeX.
        float linear = Math.max(0.0001f, Math.min(1f, volume * ganho));
        float db = (float) (20.0 * Math.log10(linear));

        controle.setValue(Math.max(controle.getMinimum(), Math.min(controle.getMaximum(), db)));
    }
}
