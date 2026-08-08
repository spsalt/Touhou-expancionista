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
       discreto, explosao e forte. Por isso um volume global basta e nao
       foi preciso equalizar som por som.

       Os arquivos vem em 8 e 16 bits e em varias taxas (11k/22k/44k). O
       javax.sound aceita todos, e quem falhar cai no tratamento de erro
       do carregar() sem derrubar nada.
    */

    /* --- combate --- */
    public static final String TIRO_JOGADOR   = "audio/se_plst00.wav";
    public static final String TIRO_PONTEIRO  = "audio/tiro_ponteiro.wav";
    public static final String TIRO_RICOCHETE = "audio/tiro_ricochete.wav";
    public static final String QUIQUE         = "audio/quique.wav";
    public static final String TIRO_INIMIGO   = "audio/se_tan00.wav";
    public static final String INIMIGO_MORRE  = "audio/se_enep00.wav";
    public static final String JOGADOR_DANO   = "audio/se_pldead00.wav";
    public static final String GPT_EXPANSION  = "audio/se_slash.wav";
    public static final String FOCO           = "audio/se_focusin.wav";

    /* --- progressao --- */
    public static final String ITEM           = "audio/se_item00.wav";
    public static final String SUBIR_NIVEL    = "audio/se_powerup.wav";

    /* --- chefe --- */
    public static final String SPELL_INICIA   = "audio/se_cat00.wav";
    public static final String SPELL_QUEBRA   = "audio/se_cardget.wav";
    public static final String CHEFE_MORRE    = "audio/se_enep01.wav";
    /** Voiceline do Clayton: "VOCE JA OUVIU FALAR EM LATEX?" */
    public static final String CLAYTON_LATEX  = "audio/latex_voz.wav";

    /* --- PAPA --- */

    /** Bandeira fechou a mira e travou a direcao. */
    public static final String PAPA_MIRA     = "audio/se_alert.wav";

    /** Bandeira saiu. */
    public static final String PAPA_AVANCA   = "audio/se_kira00.wav";

    /** Simbolo certo digitado na fita da maquina de Turing. */
    public static final String TURING_OK     = "audio/se_ok00.wav";

    /** Simbolo errado, ou o relogio zerou. */
    public static final String TURING_ERRO   = "audio/se_cancel00.wav";

    /** Um no foi conquistado por um prototipo, no Optimum Path Forest. */
    public static final String OPF_CONQUISTA = "audio/se_ch00.wav";

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

    /** Um pool de Clips por arquivo. */
    private static final Map<String, Clip[]> pools = new HashMap<>();

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

    /** Abre N copias do mesmo arquivo. Pool vazio = som desligado. */
    private static Clip[] carregar(String caminho) {

        File arquivo = Assets.resolverArquivo(caminho);

        if (arquivo == null) {
            System.err.println("[Som] Nao encontrei \"" + caminho + "\". Segue sem este efeito.");
            return new Clip[0];
        }

        int tamanho = Math.max(1, Config.getInt("som.vozes", 6));
        Clip[] pool = new Clip[tamanho];

        for (int i = 0; i < tamanho; i++) {

            try (AudioInputStream stream = AudioSystem.getAudioInputStream(arquivo)) {

                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                ajustarVolume(clip);
                pool[i] = clip;

            } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
                System.err.println("[Som] Falha carregando " + caminho + ": " + e.getMessage());
                return new Clip[0];
            }
        }

        System.out.println("[Som] Carregado: " + arquivo.getPath() + " (" + tamanho + " vozes)");
        return pool;
    }

    private static void ajustarVolume(Clip clip) {

        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        FloatControl controle = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        // MASTER_GAIN e em decibeis: converte o volume linear e prende
        // dentro do que o controle aceita (senao da IllegalArgumentException).
        float linear = Math.max(0.0001f, Math.min(1f, volume));
        float db = (float) (20.0 * Math.log10(linear));

        controle.setValue(Math.max(controle.getMinimum(), Math.min(controle.getMaximum(), db)));
    }
}
