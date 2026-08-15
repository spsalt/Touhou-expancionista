package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * O FIM: a foto no uba e a tela de agradecimento.
 *
 * "Foto final todos aparecem no uba bebendo" (Roteiro.txt linha 79). A
 * sequencia toda e uma so, em cinco tempos encadeados:
 *
 *   1. ESCURECE     a cena que estava na tela vira preto
 *   2. ABRE         o preto some revelando o RODAPE da foto
 *   3. SOBE         a foto rola pra cima ate mostrar o topo
 *   4. FECHA        volta pro preto
 *   5. OBRIGADO     "Obrigado por jogar!" e os numeros da partida
 *
 * POR QUE COMECAR PELO RODAPE E SUBIR
 * -----------------------------------
 * A foto e alta e estreita (1086x1448) e nao cabe inteira na tela sem
 * encolher a ponto de ninguem reconhecer as caras. Mostrar ela rolando
 * resolve o enquadramento e, de brinde, faz a coisa certa
 * dramaticamente: comeca no chao (as folhas de exercicio na mesa, os
 * cachorros) e termina nas pessoas. E a ordem em que a historia
 * aconteceu — a lista de exercicios primeiro, o pessoal reunido depois.
 *
 * TODO O TEMPO EM TICKS, encadeado por soma: cada fase sabe onde a
 * anterior acabou. Mexer na duracao de uma nao desalinha as outras.
 */
public class Creditos {

    private int t = 0;
    private boolean acabou = false;

    /* --- os cinco tempos, em ticks --- */

    private final int escurece;
    private final int abre;
    private final int sobe;
    private final int fecha;
    private final int obrigado;

    /** Escala da foto na tela, calculada uma vez. */
    private double escala = 1;

    /** Congelado no comeco: os numeros nao podem mudar durante os creditos. */
    private final int pontos;
    private final int graze;
    private final int continues;
    private final int nivel;
    private final int vidas;
    private final int moedas;

    public Creditos() {

        this.escurece = Math.max(1, Config.getInt("creditos.ticksEscurece", 60));
        this.abre     = Math.max(1, Config.getInt("creditos.ticksAbre", 70));
        this.sobe     = Math.max(1, Config.getInt("creditos.ticksSobe", 460));
        this.fecha    = Math.max(1, Config.getInt("creditos.ticksFecha", 70));
        this.obrigado = Math.max(1, Config.getInt("creditos.ticksObrigado", 90));

        // A PARTIDA E FOTOGRAFADA AQUI, e nao lida na hora de desenhar.
        //
        // Durante os creditos o jogo continua existindo por baixo; se a
        // tela lesse o Player a cada frame, qualquer coisa que ainda
        // mexesse nele mudaria os numeros no meio da leitura.
        this.pontos    = (Main.player != null) ? Main.player.getPontuacao() : 0;
        this.graze     = (Main.player != null) ? Main.player.getGraze() : 0;
        this.nivel     = (Main.player != null) ? Main.player.getLevel() : 1;
        this.vidas     = (Main.player != null) ? Main.player.getVidas() : 0;
        this.moedas    = (Main.player != null) ? Main.player.getMoedas() : 0;
        this.continues = Main.continuesUsados;
    }

    public void tick() {

        if (acabou) {
            return;
        }

        t++;

        if (t >= escurece + abre + sobe + fecha + obrigado) {

            // Depois do agradecimento, ENTER fecha. Sem isso a tela
            // sumiria sozinha e ninguem leria os proprios numeros.
            if (Main.enter || Main.z) {
                Main.enter = false;
                Main.z = false;
                acabou = true;
            }
        }
    }

    public boolean acabou() {
        return acabou;
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        BufferedImage foto = Assets.get(Config.getString("creditos.foto",
                                                         "sprites/ambient/uba_final.png"));

        int fim1 = escurece;
        int fim2 = fim1 + abre;
        int fim3 = fim2 + sobe;
        int fim4 = fim3 + fecha;

        // 1) A CENA ESCURECE.
        if (t < fim1) {
            preto(g, t / (double) escurece);
            return;
        }

        // 2 e 3) A FOTO, revelada e depois rolando.
        if (t < fim3) {

            desenharFoto(g, foto, t);

            if (t < fim2) {
                // O preto SAINDO: 1 -> 0 ao longo da abertura.
                preto(g, 1 - (t - fim1) / (double) abre);
            }

            return;
        }

        // 4) FECHA de novo.
        if (t < fim4) {
            desenharFoto(g, foto, fim3);
            preto(g, (t - fim3) / (double) fecha);
            return;
        }

        // 5) OBRIGADO.
        preto(g, 1);
        desenharObrigado(g, (t - fim4) / (double) obrigado);
    }

    private void preto(Graphics2D g, double f) {

        int a = (int) (255 * Math.max(0, Math.min(1, f)));

        if (a <= 0) {
            return;
        }

        g.setColor(new Color(0, 0, 0, a));
        g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);
    }

    /**
     * A foto, ancorada no RODAPE e subindo.
     *
     * A escala e calculada pela LARGURA: a foto preenche a tela de lado a
     * lado e sobra altura, que e justamente o que rola. Escalar pela
     * altura deixaria tarjas pretas nas laterais e nao teria o que rolar.
     */
    private void desenharFoto(Graphics2D g, BufferedImage foto, int tick) {

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);

        if (foto == null) {
            return;
        }

        escala = Main.WIDTH / (double) foto.getWidth();

        int larg = Main.WIDTH;
        int alt = (int) (foto.getHeight() * escala);

        // Quanto sobra pra rolar. Se a foto couber na tela, nao rola nada.
        int excedente = Math.max(0, alt - Main.HEIGHT);

        int fim1 = escurece;
        int fim2 = fim1 + abre;

        // A rolagem so comeca DEPOIS da abertura: revelar e rolar ao mesmo
        // tempo faria as duas leituras competirem, e a foto passaria antes
        // de dar pra enxergar.
        double f = (tick <= fim2) ? 0
                 : Math.min(1, (tick - fim2) / (double) sobe);

        // Suaviza as pontas (aceleracao e freada): rolagem linear comeca e
        // para de supetao, o que num plano de camera parece corte.
        double suave = f * f * (3 - 2 * f);

        int y = -(int) (excedente * suave);

        // Ancorado no RODAPE: y comeca mostrando o fim da foto.
        g.drawImage(foto, 0, Main.HEIGHT - alt - y, larg, alt, null);
    }

    /**
     * "Obrigado por jogar!" e os numeros da partida.
     *
     * Os numeros nao sao enfeite: eles sao a unica coisa nesta tela que e
     * SUA. A foto e sempre a mesma; a partida foi diferente.
     */
    private void desenharObrigado(Graphics2D g, double f) {

        int a = (int) (255 * Math.max(0, Math.min(1, f)));

        int cx = Main.WIDTH / 2;
        int cy = Main.HEIGHT / 2;

        g.setFont(new Font("Monospaced", Font.BOLD, 40));
        centralizado(g, "Obrigado por jogar!", cx, cy - 130, new Color(255, 225, 150, a));

        g.setFont(new Font("Monospaced", Font.PLAIN, 15));
        centralizado(g, "Touhou 67: Antimony of Recogna's Expansion",
                     cx, cy - 96, new Color(190, 190, 215, a));

        // A linha que resume a partida em uma frase, antes dos numeros.
        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        centralizado(g, veredito(), cx, cy - 50, new Color(200, 255, 210, a));

        g.setFont(new Font("Monospaced", Font.PLAIN, 17));

        String[] linhas = {
            "pontuação final .... " + String.format("%09d", pontos),
            "graze .............. " + graze,
            "nível do tiro ...... " + nivel,
            "vidas restantes .... " + vidas,
            "pesos cubanos ...... " + moedas,
            "continues usados ... " + continues
        };

        for (int i = 0; i < linhas.length; i++) {
            centralizado(g, linhas[i], cx, cy + i * 26, new Color(220, 220, 235, a));
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        centralizado(g, "ENTER para voltar ao menu", cx, cy + 210,
                     new Color(140, 140, 165, a));
    }

    /**
     * Uma frase sobre COMO a partida foi.
     *
     * A ordem dos testes importa: o continue e o que mais define a
     * partida, entao ele fala primeiro mesmo que a pontuacao seja alta.
     */
    private String veredito() {

        if (continues > 0) {
            return "Você chegou ao fim. Da próxima, sem continue.";
        }

        if (vidas >= 5) {
            return "Sem continue e quase sem levar dano. Absurdo.";
        }

        if (graze >= 2000) {
            return "Sem continue, e passando raspando o tempo todo.";
        }

        return "Você derrotou o jogo. Limpo.";
    }

    private void centralizado(Graphics2D g, String texto, int cx, int y, Color cor) {

        int larg = g.getFontMetrics().stringWidth(texto);

        g.setColor(new Color(0, 0, 0, cor.getAlpha()));
        g.drawString(texto, cx - larg / 2 + 2, y + 2);

        g.setColor(cor);
        g.drawString(texto, cx - larg / 2, y);
    }
}
