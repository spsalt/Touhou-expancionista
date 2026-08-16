package src;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A CERIMONIA DA ARMADURA — o "ESPANDAAAAA".
 *
 * Depois que o SANTO JAVA sai do meio da tela, energia roxa comeca a
 * escorrer de todos os cantos pro estudante. Ela vai ficando mais densa,
 * mais rapida e mais brilhante, ate que no clímax da musica a armadura se
 * fecha de uma vez.
 *
 * O TEMPO DA ARMADURA E O TEMPO DA MUSICA
 * ---------------------------------------
 * O momento exato da transformacao nao foi escolhido no olho: eu medi o
 * envelope de energia da faixa. O maior salto do arquivo inteiro esta em
 * 8,97 s — a musica constroi, DA UMA RESPIRADA por volta dos 7 s e entao
 * estoura. E ali que a armadura fecha.
 *
 * Isso importa mais do que parece. Um efeito que acontece meio segundo
 * antes ou depois do baque da trilha nao le como "a musica marcou o
 * momento", le como bug de sincronia — e o jogador nao sabe dizer por que,
 * so sente que ficou errado.
 *
 * A cerimonia NAO congela o jogo. Ela roda por cima da conversa, como todo
 * o resto: o fundo rola, o jogador anda. O que ela faz e ocupar a tela.
 */
public class AscensaoDaArmadura {

    /** Um fiapo de energia indo pro estudante. */
    private static class Fiapo {

        double x, y;
        double vx, vy;

        /** De onde saiu, pro rastro saber onde comecar. */
        double xInicial, yInicial;

        int vida;
        final int vidaMaxima;

        final float matiz;   // varia do roxo ao azul, pra nao ficar chapado

        Fiapo(double x, double y, double vx, double vy, int vida, float matiz) {
            this.x = x;
            this.y = y;
            this.xInicial = x;
            this.yInicial = y;
            this.vx = vx;
            this.vy = vy;
            this.vida = vida;
            this.vidaMaxima = vida;
            this.matiz = matiz;
        }
    }

    private static final Random RNG = new Random();

    /* --- o crescimento do grito, em numeros com nome --- */

    /** Tamanho relativo com que o grito entra. */
    private static final double GRITO_ESCALA_INICIAL = 0.94;

    /** Quanto ele cresce do inicio ate o fim do tempo em tela. */
    private static final double GRITO_CRESCIMENTO = 0.10;

    /** O maior que ele fica. E por este que a largura e conferida. */
    private static final double ESCALA_FINAL_DO_GRITO =
            GRITO_ESCALA_INICIAL + GRITO_CRESCIMENTO;

    private final List<Fiapo> fiapos = new ArrayList<>();

    private int t = 0;
    private boolean isAlive = true;

    /** Ja fechou a armadura? Garante que acontece uma vez so. */
    private boolean armaduraFechada = false;

    /* --- ajustes --- */

    private final int atraso;
    private final int tickDoEstouro;
    private final int duracao;
    private final int fiaposPorTick;

    public AscensaoDaArmadura() {

        this.atraso        = Math.max(0, Config.getInt("ascensao.ticksDeAtraso", 30));
        this.tickDoEstouro = Math.max(30, Config.getInt("ascensao.tickDoEstouro", 538));
        this.duracao       = Math.max(tickDoEstouro + 30, Config.getInt("ascensao.duracao", 900));
        this.fiaposPorTick = Math.max(1, Config.getInt("ascensao.fiaposPorTick", 3));

        // A faixa toca do inicio e UMA VEZ SO (o false).
        //
        // Ela tem 16,8 s com clímax aos 9 e um rabo de silencio no fim; em
        // loop, recomecava do zero no meio da cena — a musica que devia
        // MARCAR o momento passava a atrapalhar ele.
        //
        // Quem devolve a musica da fase e o proprio fim da cerimonia (ver
        // phase1.aplicarMusicaDoEstagio, que sai de fininho enquanto isto
        // estiver rodando).
        if (Main.musica != null) {
            Main.musica.trocarFaixa(Config.getString("ascensao.musica", "audio/ascension.wav"), false);
        }

        // A TELA FICA LIMPA PRA CERIMONIA.
        //
        // Bala inimiga sobrando e inimigo perdido na tela transformavam o
        // momento numa cena acontecendo POR TRAS de um combate. Nao ha
        // nada pra desviar aqui — a fase esta travada — entao o que sobrar
        // e so ruido visual competindo com a energia roxa.
        for (int i = 0; i < Main.bullets.size(); i++) {
            if (Main.bullets.get(i).isHitPlayer()) {
                Main.bullets.get(i).setAlive(false);
            }
        }

        Main.enemies.clear();
        Main.destrocos.clear();
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        if (!isAlive) {
            return;
        }

        t++;

        // O ATRASO existe pro SANTO JAVA sair do meio da tela primeiro.
        // Energia surgindo com o retrato dele ainda no centro faria as
        // duas coisas competirem pelo mesmo lugar.
        if (t > atraso && !armaduraFechada) {
            nascerFiapos();
        }

        moverFiapos();

        if (!armaduraFechada && t >= tickDoEstouro) {
            fecharArmadura();
        }

        if (t >= duracao) {
            isAlive = false;
        }
    }

    /**
     * Quantidade de energia agora, de 0 a 1.
     *
     * Cresce ao QUADRADO ate o estouro. Crescimento linear parecia uma
     * torneira aberta no mesmo volume o tempo todo; ao quadrado, os
     * primeiros segundos sao um fiapo aqui e ali e os ultimos sao uma
     * enxurrada — que e a forma da propria musica.
     */
    private double intensidade() {

        double f = Math.max(0, Math.min(1, (t - atraso) / (double) (tickDoEstouro - atraso)));

        return f * f;
    }

    private void nascerFiapos() {

        if (Main.player == null) {
            return;
        }

        int quantos = (int) Math.ceil(fiaposPorTick * intensidade());

        for (int i = 0; i < quantos; i++) {

            // Nascem na BORDA do campo, num ponto sorteado do perimetro.
            // Nascendo perto do jogador, a energia apareceria do nada a
            // meio metro dele; vindo da borda, ela atravessa a tela e da
            // pra ver de onde vem.
            double ang = RNG.nextDouble() * Math.PI * 2;
            double raio = Math.max(Main.CAMPO_W, Main.CAMPO_H) * 0.62;

            double px = Main.player.getX() + Math.cos(ang) * raio;
            double py = Main.player.getY() + Math.sin(ang) * raio;

            int vida = Config.getInt("ascensao.ticksDoFiapo", 46);

            // Velocidade calculada pra chegar no jogador quando a vida
            // acabar. E o que faz todos convergirem no mesmo ponto em vez
            // de passarem reto por perto dele.
            double vx = (Main.player.getX() - px) / vida;
            double vy = (Main.player.getY() - py) / vida;

            fiapos.add(new Fiapo(px, py, vx, vy, vida, RNG.nextFloat()));
        }
    }

    private void moverFiapos() {

        for (int i = fiapos.size() - 1; i >= 0; i--) {

            Fiapo f = fiapos.get(i);

            f.x += f.vx;
            f.y += f.vy;

            // Acelera perto do fim: a energia e "sugada" nos ultimos
            // pixels em vez de chegar no mesmo passo com que saiu.
            f.vx *= 1.012;
            f.vy *= 1.012;

            f.vida--;

            if (f.vida <= 0) {
                fiapos.remove(i);
            }
        }
    }

    /** O clímax: a armadura se fecha, junto com o baque da musica. */
    private void fecharArmadura() {

        armaduraFechada = true;

        if (Main.player == null) {
            return;
        }

        // O ganharArmadura ja solta a explosao roxa e o som. Aqui a gente
        // so garante que ele acontece NESTE tick — que e o do estouro.
        Main.player.ganharArmadura();

        fiapos.clear();
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        if (!isAlive || Main.player == null) {
            return;
        }

        if (!armaduraFechada) {
            desenharVeu(g);
            desenharFiapos(g);
            desenharAuraCrescendo(g);
        }

        desenharGrito(g);
    }

    /**
     * O "ESPANDAAAAA!!!!!!!!!" atravessando a tela.
     *
     * Ele saiu da caixa de dialogo por um motivo simples: caixa de fala e
     * onde se PASSA INFORMACAO, e essa linha nao e informacao, e um grito.
     * Numa caixa, ela vinha com nome do personagem, letrinha por letrinha
     * e um "aperte Z" implicito — o mesmo tratamento de "bão prof, cê
     * gostou?". Solta na tela, em corpo enorme, ela e o que e.
     *
     * ENTRA RAPIDO E SAI RAPIDO. Um fade lento pareceria legenda; o corte
     * curto nos dois lados faz ler como impacto. E ela aparece DEPOIS do
     * atraso, junto com os primeiros fiapos — o grito e o que chama a
     * energia, entao vir antes dela inverteria a causa.
     */
    private void desenharGrito(Graphics2D g) {

        int inicio = atraso;
        int entra  = Config.getInt("ascensao.gritoEntra", 10);
        int fica   = Config.getInt("ascensao.gritoFica", 95);
        int sai    = Config.getInt("ascensao.gritoSai", 22);

        int dt = t - inicio;

        if (dt < 0 || dt > entra + fica + sai) {
            return;
        }

        double alpha;

        if (dt < entra) {
            alpha = dt / (double) Math.max(1, entra);
        } else if (dt < entra + fica) {
            alpha = 1;
        } else {
            alpha = 1 - (dt - entra - fica) / (double) Math.max(1, sai);
        }

        alpha = Math.max(0, Math.min(1, alpha));

        // Cresce um tiquinho enquanto esta na tela: um texto imovel de
        // corpo 54 parece um cartaz colado; crescendo devagar, ele empurra.
        double escala = GRITO_ESCALA_INICIAL
                      + GRITO_CRESCIMENTO * Math.min(1, dt / (double) (entra + fica));

        String texto = Config.getString("ascensao.grito", "ESPANDAAAAA!!!!!!!!!");

        // O GRITO SE AJUSTA PRA CABER NO CAMPO.
        //
        // O corpo 54 foi escolhido no olho e vazava pelos dois lados: sao
        // 20 caracteres, e em Monospaced cada um ocupa perto de 0,6 do
        // corpo — 20 x 32 = 648 px num campo de 600. Sobrava pra fora
        // justamente o comeco e o fim da palavra.
        //
        // Podia ser so um numero menor no config, mas ai o proximo grito
        // que alguem escrevesse (ou um "!" a mais) voltaria a vazar. Medir
        // e encolher resolve pra qualquer texto.
        //
        // A MEDIDA E FEITA NO PICO, nao no tamanho de agora: o grito
        // CRESCE enquanto esta na tela, entao ajustar pelo tamanho atual
        // faria ele caber no comeco e transbordar bem no fim, que e onde
        // ele esta mais visivel.
        int margem = Config.getInt("ascensao.margemDoGrito", 18);
        int larguraMax = Math.max(40, Main.CAMPO_W - 2 * margem);

        int tamanhoConfig = Math.max(8, Config.getInt("ascensao.tamanhoDoGrito", 54));
        int tamanhoNoPico = Math.max(8, (int) (tamanhoConfig * ESCALA_FINAL_DO_GRITO));

        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, tamanhoNoPico));

        int larguraNoPico = g.getFontMetrics().stringWidth(texto);

        double couber = (larguraNoPico > larguraMax)
                      ? larguraMax / (double) larguraNoPico
                      : 1.0;

        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD,
                                    Math.max(8, (int) (tamanhoConfig * couber * escala))));

        java.awt.FontMetrics fm = g.getFontMetrics();

        int larg = fm.stringWidth(texto);
        int cx = Main.CAMPO_X + Main.CAMPO_W / 2 - larg / 2;
        int cy = Main.CAMPO_Y + (int) (Main.CAMPO_H * 0.34);

        int a = (int) (255 * alpha);

        // Contorno preto em oito direcoes: o grito passa por cima da
        // energia roxa, do veu e do que mais estiver na tela.
        g.setColor(new Color(0, 0, 0, a));

        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dy = -3; dy <= 3; dy += 3) {
                if (dx != 0 || dy != 0) {
                    g.drawString(texto, cx + dx, cy + dy);
                }
            }
        }

        g.setColor(new Color(235, 205, 255, a));
        g.drawString(texto, cx, cy);
    }

    /**
     * Um veu roxo cobrindo o campo, que escurece conforme a energia junta.
     *
     * Serve pra dar contraste: os fiapos sao finos, e sobre o cenario
     * normal eles sumiam. Com o campo puxado pro roxo escuro, cada risco
     * de luz aparece.
     */
    private void desenharVeu(Graphics2D g) {

        int alpha = (int) (Config.getInt("ascensao.veuMaximo", 120) * intensidade());

        if (alpha <= 0) {
            return;
        }

        g.setColor(new Color(28, 6, 48, Math.min(255, alpha)));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
    }

    /**
     * Os fiapos, desenhados como RISCOS e nao pontos.
     *
     * Mesmo truque das faiscas da Explosao: o risco vai da posicao atual
     * ate onde ela estava alguns frames atras, reconstruido pela
     * velocidade. Um ponto de 3 px atravessando a tela a 12 px por tick
     * vira uma sequencia de pontinhos separados; o risco vira um raio.
     */
    private void desenharFiapos(Graphics2D g) {

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < fiapos.size(); i++) {

            Fiapo f = fiapos.get(i);

            // Fica mais forte conforme chega perto do destino.
            double perto = 1 - f.vida / (double) f.vidaMaxima;

            int alpha = (int) (90 + 165 * perto);

            int r = (int) (150 + 90 * f.matiz);
            int gg = (int) (60 + 40 * (1 - f.matiz));
            int b = 255;

            g.setColor(new Color(Math.min(255, r), Math.min(255, gg), b, Math.min(255, alpha)));

            g.drawLine((int) f.x, (int) f.y,
                       (int) (f.x - f.vx * 3.2), (int) (f.y - f.vy * 3.2));
        }

        g.setStroke(anterior);
    }

    /** O halo em volta do estudante, engordando junto com a musica. */
    private void desenharAuraCrescendo(Graphics2D g) {

        double f = intensidade();

        if (f <= 0.01) {
            return;
        }

        double base = Config.getDouble("ascensao.raioDaAura", 90) * f;
        double pulso = 1 + 0.08 * Math.sin(t * 0.22);

        double x = Main.player.getX();
        double y = Main.player.getY();

        for (int i = 3; i >= 1; i--) {

            double raio = base * (0.45 + i * 0.22) * pulso;
            int alpha = (int) (f * (40 + 40 * (4 - i)));

            g.setColor(new Color(150, 70, 255, Math.min(190, alpha)));
            g.fillOval((int) (x - raio), (int) (y - raio), (int) (raio * 2), (int) (raio * 2));
        }

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(2f));

        double raio = base * pulso;

        g.setColor(new Color(215, 180, 255, (int) (f * 220)));
        g.drawOval((int) (x - raio), (int) (y - raio), (int) (raio * 2), (int) (raio * 2));

        g.setStroke(anterior);
    }

    public boolean isAlive() {
        return isAlive;
    }

    /** true enquanto a armadura ainda nao fechou. */
    public boolean estaJuntandoEnergia() {
        return isAlive && !armaduraFechada;
    }
}
