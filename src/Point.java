package src;

import java.awt.*;

/**
 * Item de XP que os inimigos dropam ao morrer.
 *
 * Cai devagar; se o jogador chegar dentro do raio de coleta, ele passa a
 * ser atraido e vai atras do jogador ate encostar (isCatch = true).
 */
public class Point {

    /**
     * O que este item faz ao ser pego.
     *
     * XP    — o item comum que os inimigos dropam; enche a barra de nivel.
     * MOEDA — peso cubano, a moeda da lojinha do Perea. Cai no lugar de
     *         parte dos itens de XP (ver Enemy.morrer), entao pegar
     *         dinheiro e a MESMA acao de pegar poder: o jogador nao
     *         escolhe entre os dois, ele so recebe menos XP do que
     *         recebia antes. E o outro lado do nivel ter sido enfraquecido.
     * FULL  — o simbolo dourado da UNESP com "FULL" escrito, que so nasce
     *         quando o jogador usa um continue. Ele nao da um pouco de
     *         poder: da TUDO de uma vez (nivel maximo e bombas cheias).
     *
     * Sao o mesmo objeto e nao tres classes porque a diferenca e so o
     * efeito de um metodo e a cor do desenho — herdar aqui daria classes
     * quase identicas pra economizar um switch de tres linhas.
     */
    public enum Tipo {
        XP,
        MOEDA,
        /**
         * CARD DE GPT EXPANSION: uma carga de bomba, dropada por chefe
         * derrotado. Um por chefe, nao mais.
         *
         * Existe porque a bomba nao se recupera de jeito nenhum durante a
         * partida: voce comeca com algumas e quando acabam, acabaram. Com
         * uma luta inteira separando cada carga, gastar deixa de ser uma
         * decisao definitiva e passa a ser uma decisao com prazo — que e o
         * que faz o jogador realmente USAR a bomba em vez de morrer com
         * ela guardada.
         */
        CARD_GPT,
        FULL
    }

    private Tipo tipo = Tipo.XP;

    /**
     * Versao dourada da insignia, montada UMA vez e reaproveitada.
     *
     * A tentativa ingenua — desenhar a arte normal e jogar um circulo
     * dourado translucido por cima — nao funciona: fillOval pinta o
     * retangulo/circulo INTEIRO, inclusive onde a insignia e transparente,
     * e o resultado e um disco marrom com a insignia dentro. Tingir tem
     * que ser pixel a pixel, respeitando o alfa original.
     */
    private static java.awt.image.BufferedImage dourado = null;
    private static boolean tentouDourar = false;

    /** Cronometro proprio, so pro brilho do FULL pulsar. */
    private int t = 0;

    private double x;
    private double y;
    private double dx = 0;
    private double dy = 1;
    private boolean isCatch;
    private boolean isAlive = true;

    public Point(double x, double y, boolean isCatch){
        this.x = x;
        this.y = y;
        this.isCatch = isCatch;
    }

    /** Item de tipo escolhido (ver Tipo). */
    public Point(double x, double y, boolean isCatch, Tipo tipo){
        this(x, y, isCatch);
        this.tipo = tipo;
    }

    public void tick() {

        t++;

        // O FULL tem fisica propria: ele SOBE e nao persegue ninguem.
        if (tipo == Tipo.FULL) {
            tickFull();
            return;
        }

        y += dy;
        x += dx;

        // Saiu pelo fundo do campo: perdido.
        if(y > Main.CAMPO_Y + Main.CAMPO_H)
            isAlive = false;

        if(Main.player == null)
            return;

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        // Encostou.
        if(dist < 8){

            isAlive = false;

            if (tipo == Tipo.FULL) {
                Som.tocar(Som.SUBIR_NIVEL);
                Main.player.encherPoderes();
            } else if (tipo == Tipo.MOEDA) {
                Som.tocar(Som.MOEDA);
                Main.player.receberMoedas(1);
            } else if (tipo == Tipo.CARD_GPT) {
                Som.tocar(Som.SUBIR_NIVEL);
                Main.player.setBombas(Main.player.getBombas() + 1);
            } else {
                Som.tocar(Som.ITEM);
                Main.player.setXp(Main.player.getXp()+1);
            }

            return;
        }

        // Entrou no raio de coleta: a partir daqui persegue o jogador pra sempre.
        if(dist < Main.player.getRaioColeta()){
            isCatch = true;
        }

        if(isCatch){
            dx = Main.getCos(Main.player.getX(), Main.player.getY(), x, y) * 5;
            dy = Main.getSin(Main.player.getX(), Main.player.getY(), x, y) * 5;
        }

    }

    public void render(Graphics2D g){

        java.awt.image.BufferedImage img = Assets.get("sprites/GFX/unesp_item.png");

        int lado = Config.getInt("item.tamanho", 18);

        if (tipo == Tipo.FULL) {
            renderFull(g, img);
            return;
        }

        if (tipo == Tipo.MOEDA) {
            renderMoeda(g);
            return;
        }

        if (tipo == Tipo.CARD_GPT) {
            renderCardGpt(g);
            return;
        }

        if (img != null) {

            // Brilho por tras quando esta sendo atraido, pra dar feedback
            // de que o item ja e seu.
            if (isCatch) {
                g.setColor(new Color(120, 220, 255, 90));
                g.fillOval((int)x - lado, (int)y - lado, lado*2, lado*2);
            }

            g.drawImage(img, (int)x - lado/2, (int)y - lado/2, lado, lado, null);
            return;
        }

        // Sem o PNG: quadradinho simples.
        g.setColor(isCatch ? Color.CYAN : new Color(80, 120, 255));
        g.fillRect((int)x-5, (int)y-5, 10, 10);
        g.setColor(Color.WHITE);
        g.drawRect((int)x-5, (int)y-5, 10, 10);

    }

    /**
     * O CARD DE GPT EXPANSION: a logo dentro de uma cartinha brilhando.
     *
     * Desenhado como CARTA (retangulo) e nao como disco de propósito: os
     * outros dois itens que caem sao redondos, e no meio de uma chuva de
     * drop de chefe a forma e o que separa "mais um item" de "aquilo ali e
     * outra coisa". Cor e brilho ninguem compara no susto; silhueta, sim.
     */
    private void renderCardGpt(Graphics2D g) {

        int alt = (int) (Config.getInt("item.tamanho", 18)
                       * Config.getDouble("cardGpt.escala", 1.9));
        int larg = (int) (alt * 0.72);

        double pulso = 1 + 0.10 * Math.sin(t * 0.12);

        int x0 = (int) x - larg / 2;
        int y0 = (int) y - alt / 2;

        // Halo pulsando por tras.
        int halo = (int) (alt * 0.85 * pulso);

        g.setColor(new Color(255, 225, 150, 60));
        g.fillOval((int) x - halo, (int) y - halo, halo * 2, halo * 2);

        // A carta.
        g.setColor(new Color(30, 28, 40, 230));
        g.fillRect(x0, y0, larg, alt);

        g.setColor(new Color(255, 220, 130));
        g.drawRect(x0, y0, larg, alt);
        g.drawRect(x0 + 1, y0 + 1, larg - 2, alt - 2);

        // A logo da GPT dentro dela.
        java.awt.image.BufferedImage logo = Assets.get("sprites/GFX/gpt_logo.png");

        if (logo != null) {

            int lado = (int) (larg * 0.78);

            java.awt.image.BufferedImage pronta =
                    Assets.getEscalado("sprites/GFX/gpt_logo.png", lado, lado);

            g.drawImage(pronta != null ? pronta : logo,
                        (int) x - lado / 2, (int) y - lado / 2, lado, lado, null);
        }
    }

    /**
     * A moeda de peso cubano.
     *
     * Desenhada na mao e nao com PNG: e um disco com uma ESTRELA, que e o
     * que a moeda cubana de verdade tem, e desse tamanho (18 px) um
     * desenho vetorial fica mais limpo do que uma foto reduzida.
     *
     * Gira: a largura oscila com o tempo enquanto a altura fica, o que da
     * a impressao de um disco rodando no proprio eixo. E o mesmo truque de
     * moeda de plataforma 2D — custa um seno e evita seis quadros de
     * animacao.
     *
     * A cor e propositalmente DIFERENTE do item de XP (azul/branco): numa
     * tela cheia, o jogador precisa saber num relance se aquilo ali e
     * poder ou dinheiro, porque as duas coisas agora competem.
     */
    private void renderMoeda(Graphics2D g) {

        int lado = (int) (Config.getInt("item.tamanho", 18)
                        * Config.getDouble("moeda.escala", 1.05));

        // |sin| e nao sin: o disco nunca "vira do avesso", so achata e
        // volta. Com o seno cru ele passaria por larguras negativas.
        double giro = Math.abs(Math.sin(t * Config.getDouble("moeda.velocidadeDoGiro", 0.07)));
        int larg = Math.max(2, (int) (lado * (0.25 + 0.75 * giro)));

        int x0 = (int) x - larg / 2;
        int y0 = (int) y - lado / 2;

        if (isCatch) {
            g.setColor(new Color(255, 210, 120, 80));
            g.fillOval((int) x - lado, (int) y - lado, lado * 2, lado * 2);
        }

        g.setColor(new Color(196, 148, 45));
        g.fillOval(x0, y0, larg, lado);

        g.setColor(new Color(255, 226, 150));
        g.drawOval(x0, y0, larg, lado);

        // A estrela so aparece quando o disco esta largo o bastante pra
        // ela caber; de perfil, a moeda e so uma lasca.
        if (larg >= lado * 0.6) {

            g.setFont(new Font("Monospaced", Font.BOLD, Math.max(8, lado - 6)));

            String estrela = "★";
            int lt = g.getFontMetrics().stringWidth(estrela);

            g.setColor(new Color(120, 80, 10));
            g.drawString(estrela, (int) x - lt / 2, (int) y + lado / 2 - 3);
        }
    }

    /**
     * O FULL sobe, oscilando de leve, e o jogador tem que ALCANCAR ele.
     *
     * Nao persegue de proposito. O poder maximo e a recompensa por
     * aceitar continuar, mas nao pode cair no colo: fazer os simbolos
     * subirem obriga o jogador a ir atras deles pro alto do campo, que e
     * a faixa mais perigosa da tela. Assim ate a "recompensa" pede uma
     * jogada, e nao so um aperto de ENTER.
     *
     * Ele ignora o isCatch justamente por isso — nem o Point of
     * Collection puxa um FULL. Quem sobe e voce.
     */
    private void tickFull() {

        y -= Config.getDouble("continue.velocidadeDeSubida", 1.6);

        // Bamboleio: uma senoide na horizontal. Serve pra ele nao subir
        // numa linha reta previsivel, o que tornaria a captura trivial.
        x += Math.sin(t * Config.getDouble("continue.frequenciaDoBamboleio", 0.06))
           * Config.getDouble("continue.amplitudeDoBamboleio", 0.9);

        // Prende dentro do campo: um simbolo saindo pela lateral seria
        // poder perdido por um motivo que o jogador nao controla.
        double margem = 20;
        x = Math.max(Main.CAMPO_X + margem,
                     Math.min(Main.CAMPO_X + Main.CAMPO_W - margem, x));

        // Escapou pelo topo: perdido de vez.
        if (y < Main.CAMPO_Y - 30) {
            isAlive = false;
            return;
        }

        if (Main.player == null) {
            return;
        }

        // Raio de captura maior que o do item comum: ele esta subindo e o
        // jogador esta desviando de bala ao mesmo tempo. Exigir precisao
        // de pixel aqui seria crueldade, nao desafio.
        double alcance = Config.getDouble("continue.raioDeCaptura", 26);

        if (Main.getDist(x, y, Main.player.getX(), Main.player.getY()) < alcance) {
            isAlive = false;
            Som.tocar(Som.SUBIR_NIVEL);
            Main.player.encherPoderes();
        }
    }

    /**
     * O simbolo dourado com "FULL" escrito.
     *
     * Desenhado maior que o item comum e com um halo pulsante: ele nasce
     * no meio de uma tela que o jogador acabou de perder, entao precisa
     * gritar mais alto que tudo que estiver ali.
     *
     * O dourado vem de um AlphaComposite por cima da mesma arte do item
     * normal — nao existe um PNG separado, e nao precisa: e a MESMA
     * insignia da UNESP, so que valendo tudo.
     */
    private void renderFull(Graphics2D g, java.awt.image.BufferedImage img){

        int lado = (int) (Config.getInt("item.tamanho", 18)
                        * Config.getDouble("continue.escalaDoItemFull", 1.8));

        double pulso = 1 + 0.12 * Math.sin(t * 0.14);
        int halo = (int) (lado * 1.25 * pulso);

        // Halo fraco no miolo e anel forte na borda. Um disco cheio e
        // opaco viraria uma mancha marrom por cima do cenario — o que
        // chama atencao aqui e o CONTORNO brilhando, nao o preenchimento.
        g.setColor(new Color(255, 205, 70, 45));
        g.fillOval((int)x - halo, (int)y - halo, halo*2, halo*2);

        g.setColor(new Color(255, 240, 175, 200));
        g.drawOval((int)x - halo, (int)y - halo, halo*2, halo*2);
        g.drawOval((int)x - halo + 1, (int)y - halo + 1, halo*2 - 2, halo*2 - 2);

        java.awt.image.BufferedImage ouro = dourar(img);

        if (ouro != null) {
            g.drawImage(ouro, (int)x - lado/2, (int)y - lado/2, lado, lado, null);
        } else {
            g.setColor(new Color(255, 205, 70));
            g.fillOval((int)x - lado/2, (int)y - lado/2, lado, lado);
        }

        g.setFont(new Font("Monospaced", Font.BOLD, 13));

        String texto = "FULL";
        int larg = g.getFontMetrics().stringWidth(texto);

        g.setColor(new Color(40, 25, 0));
        g.drawString(texto, (int)x - larg/2 + 1, (int)y - lado/2 - 5);

        g.setColor(new Color(255, 235, 160));
        g.drawString(texto, (int)x - larg/2, (int)y - lado/2 - 6);
    }

    /**
     * Devolve a insignia tingida de dourado, montando na primeira vez.
     *
     * Cada pixel e puxado na direcao do ouro mantendo o proprio brilho
     * (a luminancia vira o fator), e o ALFA e copiado intacto — e isso
     * que preserva o recorte da insignia em vez de virar um disco.
     */
    private static java.awt.image.BufferedImage dourar(java.awt.image.BufferedImage base) {

        if (tentouDourar) {
            return dourado;
        }

        tentouDourar = true;

        if (base == null) {
            return null;
        }

        int larg = base.getWidth();
        int alt = base.getHeight();

        java.awt.image.BufferedImage saida =
                new java.awt.image.BufferedImage(larg, alt,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);

        for (int px = 0; px < larg; px++) {
            for (int py = 0; py < alt; py++) {

                int argb = base.getRGB(px, py);
                int a = (argb >>> 24) & 0xFF;

                if (a == 0) {
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int gr = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // Luminancia perceptual: o verde pesa mais que o azul pro
                // olho, e usar a media crua achataria o desenho.
                double lum = (0.299 * r + 0.587 * gr + 0.114 * b) / 255.0;

                // Rampa do marrom escuro ao amarelo claro.
                int nr = (int) (90 + 165 * lum);
                int ng = (int) (55 + 180 * lum);
                int nb = (int) (10 + 90 * lum);

                saida.setRGB(px, py, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }

        dourado = saida;

        return dourado;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public boolean isCatch() {
        return isCatch;
    }

    public void setCatch(boolean isCatch) {
        this.isCatch = isCatch;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean isAlive) {
        this.isAlive = isAlive;
    }

    

}
