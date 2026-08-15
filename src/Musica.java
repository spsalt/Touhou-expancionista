package src;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Tocador de musica de fundo, usando so o que vem no JDK (javax.sound).
 *
 * IMPORTANTE: o javax.sound.sampled puro so le WAV/AU/AIFF, nao MP3.
 * Por isso a trilha do jogo fica em audio/*.wav (convertida uma vez com
 * ffmpeg) em vez do arquivo original — assim ninguem precisa adicionar
 * biblioteca externa nenhuma so pra tocar musica.
 *
 * Toda esta classe e blindada contra falha: se o arquivo nao existir, se o
 * formato nao for suportado, ou se a maquina simplesmente nao tiver placa
 * de som (comum em ambiente de teste/CI), o jogo continua rodando mudo e
 * so avisa no console. Nunca trava a partida por causa de audio.
 */
public class Musica {

    private Clip clip;

    /** true so quando o clip foi aberto com sucesso e pode tocar. */
    private boolean pronta = false;

    private boolean ligada;
    private float volume;

    /** Caminho da faixa aberta agora. Evita reabrir a mesma a toa. */
    private String faixaAtual = null;

    /**
     * A faixa atual repete quando chega ao fim?
     *
     * Quase todas repetem — sao trilha de ambiente. A excecao e a musica
     * da cerimonia da armadura, que e um trecho com clímax e fim: em loop
     * ela recomecava do zero no meio da cena.
     */
    private boolean repetir = true;

    public Musica() {
        carregarConfig();
    }

    /** (Re)le os ajustes e recarrega o arquivo. Chamado no construtor e no F5. */
    public void carregarConfig() {

        this.ligada = Config.getBool("musica.ativada", true);
        this.volume = (float) Config.getDouble("musica.volume", 0.6);

        pararEFechar();

        if (!ligada) {
            return;
        }

        String inicial = Config.getString("musica.arquivo", "");

        if (inicial != null && !inicial.trim().isEmpty()) {
            abrir(inicial);
            ajustarVolume();
        }
    }

    /**
     * Troca a trilha em andamento por outra e ja comeca a tocar.
     *
     * Serve pra dar tema proprio a um chefe (o PAPA tem o dele). Se a
     * faixa pedida ja for a que esta tocando, NAO FAZ NADA — reabrir o
     * Clip cortaria a musica no meio, e a fase chama isso dentro do
     * tick(), ou seja, sessenta vezes por segundo.
     */
    public void trocarFaixa(String caminho) {
        trocarFaixa(caminho, true);
    }

    /**
     * Igual ao anterior, mas deixando escolher se a faixa REPETE.
     *
     * Quase toda trilha do jogo e ambiente e tem que repetir pra sempre.
     * A da cerimonia da armadura nao: ela e um trecho com comeco, clímax e
     * fim, com uns oito segundos de silencio no rabo. Em loop, ela
     * recomecava do zero no meio da cena e a musica que devia MARCAR o
     * momento passava a atrapalhar ele.
     */
    public void trocarFaixa(String caminho, boolean emLoop) {

        this.repetir = emLoop;

        if (!ligada) {
            return;
        }

        // Normaliza "sem trilha": null e string vazia sao a mesma coisa.
        // O properties nao tem como escrever null, entao a chave vazia
        // (musica.arquivo=) precisa significar SILENCIO.
        String pedido = (caminho == null || caminho.trim().isEmpty()) ? null : caminho;

        if (pedido == null ? faixaAtual == null : pedido.equals(faixaAtual)) {
            return;
        }

        pararEFechar();

        // Pediram silencio: fecha e nao abre nada. E o caso dos trechos
        // ENTRE as lutas — cada tema de chefe entra numa tela sem musica,
        // o que faz a entrada dele valer muito mais.
        if (pedido == null) {
            return;
        }

        abrir(pedido);
        ajustarVolume();

        tocarDoInicio();
    }

    /** Abre o arquivo e prepara o Clip. Qualquer falha so desliga a musica. */
    private void abrir(String caminho) {

        File arquivo = Assets.resolverArquivo(caminho);

        if (arquivo == null) {
            System.err.println("[Musica] Nao encontrei \"" + caminho + "\". Jogo segue sem trilha.");
            return;
        }

        // try-with-resources: o AudioInputStream fecha sozinho, mesmo se
        // AudioSystem.getClip() lancar excecao no meio.
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(arquivo)) {

            clip = AudioSystem.getClip();
            clip.open(stream);
            pronta = true;
            faixaAtual = caminho;

            System.out.println("[Musica] Carregada: " + arquivo.getPath());

        } catch (UnsupportedAudioFileException e) {
            System.err.println("[Musica] Formato nao suportado em " + caminho + ": " + e.getMessage());
        } catch (LineUnavailableException e) {
            System.err.println("[Musica] Sem saida de audio disponivel nesta maquina: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[Musica] Erro lendo " + caminho + ": " + e.getMessage());
        }
    }

    /* =========================
            CONTROLES
       ========================= */

    /** Toca do inicio. Repete ou nao conforme a faixa (ver 'repetir'). */
    public void tocarDoInicio() {

        if (!pronta) {
            return;
        }

        clip.setFramePosition(0);

        if (repetir) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else {
            // start() toca UMA vez e para sozinho no fim.
            clip.start();
        }
    }

    /** Retoma de onde parou (usado ao sair do Pause). */
    public void continuar() {

        if (!pronta || clip.isRunning()) {
            return;
        }

        // Retomar uma faixa que NAO repete e delicado: se ela ja tinha
        // acabado antes do pause, um loop aqui a faria comecar de novo do
        // nada. Por isso a de uma vez so retoma com start(), que nao
        // reinicia nada se o clipe ja chegou ao fim.
        if (repetir) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else {
            clip.start();
        }
    }

    /** Congela sem voltar ao inicio (usado no Pause). */
    public void pausar() {

        if (pronta && clip.isRunning()) {
            clip.stop();
        }
    }

    /** Para e rebobina (usado ao voltar pro Menu). */
    public void parar() {

        if (pronta) {
            clip.stop();
            clip.setFramePosition(0);
        }
    }

    private void ajustarVolume() {

        if (!pronta || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        FloatControl controle = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        // MASTER_GAIN e em decibeis, nao 0..1. Converte volume linear pra dB
        // e prende dentro do que o controle aceita, senao da IllegalArgumentException.
        float linear = Math.max(0.0001f, Math.min(1f, volume));
        float db = (float) (20.0 * Math.log10(linear));

        db = Math.max(controle.getMinimum(), Math.min(controle.getMaximum(), db));

        controle.setValue(db);
    }

    private void pararEFechar() {

        if (clip != null) {
            clip.stop();
            clip.close();
        }

        clip = null;
        pronta = false;
        faixaAtual = null;
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public boolean isPronta() {
        return pronta;
    }

    public boolean isLigada() {
        return ligada;
    }

    public float getVolume() {
        return volume;
    }

    /** Caminho da faixa tocando agora, ou null se nao ha nenhuma. */
    public String getFaixaAtual() {
        return faixaAtual;
    }
}
