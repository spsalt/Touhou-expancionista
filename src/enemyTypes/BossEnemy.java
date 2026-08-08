package src.enemyTypes;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import src.Assets;
import src.Config;
import src.Main;
import src.Point;
import src.Som;
import src.enemyTypes.spellCards.SpellCard;

/**
 * Base dos chefes (os professores corrompidos do roteiro).
 *
 * Um chefe nao e um inimigo com muito HP: e uma SEQUENCIA de spell cards.
 * Cada spell card tem HP e tempo proprios; quando o jogador zera o HP (ou
 * o tempo estoura), o chefe limpa a tela e passa pro proximo ataque.
 *
 * O que esta classe resolve, e as subclasses ganham de graca:
 *   - percorrer a lista de spell cards
 *   - barra de HP no topo do campo + contador de ataques restantes
 *   - anuncio do nome do spell card quando ele comeca
 *   - janela de invulnerabilidade e limpeza de balas na troca
 *   - movimento de deriva no alto do campo
 *   - morte: limpa a tela, solta itens e libera a fase pra seguir
 *
 * A subclasse (ver Adriana.java) so precisa dizer QUAIS spell cards ela
 * tem e QUAL sprite usar. O padrao de bala em si mora nas classes de
 * spellCards/, nao aqui.
 */
public class BossEnemy extends Enemy {

    /** Os ataques, na ordem em que serao usados. */
    protected final SpellCard[] spellCards;

    /** Indice do spell card ativo. */
    private int spellAtual = 0;

    /** Ticks desde que o spell card atual comecou. */
    private int tSpell = 0;

    /** Ticks restantes de invulnerabilidade (usado na troca de ataque). */
    private int invulneravel = 0;

    /** Ticks restantes mostrando o nome do ataque na tela. */
    private int anuncio = 0;

    /* --- movimento --- */

    /** Altura em que o chefe fica, em pixels absolutos. */
    private double alturaDeVoo;

    /** Amplitude e periodo da deriva horizontal. */
    private double amplitudeDeriva;
    private double periodoDeriva;

    /** X do centro da deriva (meio do campo). */
    private final double centroX;

    /* --- ajustes --- */

    private int ticksInvulnerabilidadeNaTroca;
    private int ticksAnuncio;

    public BossEnemy(SpellCard[] spellCards, String sprite, double escalaSprite) {

        // O HP inicial e o do primeiro spell card. O raio de colisao vem da
        // config: e generoso (o chefe e grande), mas ainda menor que o sprite.
        super(Main.CAMPO_X + Main.CAMPO_W / 2.0,
              Main.CAMPO_Y - 80,
              spellCards.length > 0 ? spellCards[0].getHp() : 100,
              Config.getDouble("chefe.raio", 42.0));

        this.spellCards = spellCards;
        this.sprite = sprite;
        this.escalaSprite = escalaSprite;
        this.centroX = Main.CAMPO_X + Main.CAMPO_W / 2.0;

        this.pontos = Config.getInt("chefe.pontosAoMorrer", 5000);
        this.itens = Config.getInt("chefe.itensAoMorrer", 30);

        carregarConfig();

        if (spellCards.length > 0) {
            anuncio = ticksAnuncio;
            spellCards[0].iniciar(this);
            Som.tocar(Som.SPELL_INICIA);
        }
    }

    private void carregarConfig() {

        this.alturaDeVoo    = Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("chefe.alturaRelY", 0.20);
        this.amplitudeDeriva = Config.getDouble("chefe.amplitudeDeriva", 150.0);
        this.periodoDeriva   = Math.max(1, Config.getDouble("chefe.periodoDeriva", 360.0));

        this.ticksInvulnerabilidadeNaTroca = Config.getInt("chefe.ticksInvulnerabilidadeNaTroca", 90);
        this.ticksAnuncio = Config.getInt("chefe.ticksAnuncio", 120);
    }

    /* =========================
            LOGICA
       ========================= */

    @Override
    public void tick() {

        mover();

        if (invulneravel > 0) {
            invulneravel--;
        } else {
            atirar();
        }

        if (anuncio > 0) {
            anuncio--;
        }

        // Tempo limite do spell card: o ataque passa mesmo sem o jogador
        // ter zerado o HP. Sem isso, jogador com pouco dano ficaria preso
        // no mesmo padrao pra sempre.
        if (temSpellAtivo() && tSpell >= spellCards[spellAtual].getDuracao()) {
            proximoSpellCard();
        }

        tSpell++;
        t++;
    }

    /**
     * Desce ate a altura de voo e depois deriva de um lado pro outro.
     * Nao persegue o jogador de proposito: chefe de bullet hell tem que
     * ser previsivel, o desafio esta nas balas e nao em caca-lo.
     */
    @Override
    protected void mover() {

        if (y < alturaDeVoo) {
            y += 2.0;
            return;
        }

        y = alturaDeVoo;
        x = centroX + Math.sin(2 * Math.PI * t / periodoDeriva) * amplitudeDeriva;
    }

    /**
     * O ataque do chefe e sempre delegado pro spell card ativo — quem
     * escolhe o padrao de bala e a estrategia, nao esta classe.
     *
     * Chama-se atirar() (e nao atacar()) pra respeitar o contrato de
     * Enemy: e o mesmo metodo que todo inimigo do jogo sobrescreve.
     */
    @Override
    protected void atirar() {

        if (temSpellAtivo()) {
            spellCards[spellAtual].atacar(tSpell, this);
        }
    }

    private boolean temSpellAtivo() {
        return spellAtual < spellCards.length;
    }

    /**
     * Aplica dano, mas so ate o fim do spell card atual: o excedente NAO
     * vaza pro proximo ataque. Cada spell card e uma luta separada.
     */
    @Override
    public boolean levarDano(double dano) {

        if (!isAlive || invulneravel > 0) {
            return false;
        }

        hp -= dano;

        if (hp <= 0) {
            proximoSpellCard();
            return !isAlive;
        }

        return false;
    }

    /**
     * Fecha o spell card atual: limpa a tela, dropa uma recompensa e
     * comeca o proximo. Se nao houver proximo, o chefe morre.
     */
    private void proximoSpellCard() {

        Som.tocar(Som.SPELL_QUEBRA);

        // Avisa o ataque que ele acabou ANTES de trocar o indice, senao
        // quem receberia o aviso seria o proximo.
        if (temSpellAtivo()) {
            spellCards[spellAtual].encerrar(this);
        }

        limparBalasInimigas();
        soltarItens(Config.getInt("chefe.itensPorSpellCard", 8));

        spellAtual++;

        if (!temSpellAtivo()) {
            morrer();
            return;
        }

        hp = spellCards[spellAtual].getHp();
        hpMaximo = hp;

        tSpell = 0;
        invulneravel = ticksInvulnerabilidadeNaTroca;
        anuncio = ticksAnuncio;

        spellCards[spellAtual].iniciar(this);
        Som.tocar(Som.SPELL_INICIA);
    }

    @Override
    protected void morrer() {

        isAlive = false;

        if (temSpellAtivo()) {
            spellCards[spellAtual].encerrar(this);
        }

        Som.tocar(Som.CHEFE_MORRE);

        limparBalasInimigas();

        if (Main.player != null) {
            Main.player.setPontuacao(Main.player.getPontuacao() + pontos);
        }

        soltarItens(itens);
    }

    /** Apaga toda bala inimiga da tela (sem tocar nas balas do jogador). */
    private void limparBalasInimigas() {

        for (int i = 0; i < Main.bullets.size(); i++) {

            if (Main.bullets.get(i).isHitPlayer()) {
                Main.bullets.get(i).setAlive(false);
            }
        }
    }

    /** Espalha itens de XP em volta do chefe. */
    private void soltarItens(int quantidade) {

        for (int i = 0; i < quantidade; i++) {

            double ang = (2 * Math.PI * i) / Math.max(1, quantidade);
            double raio = 30 + (i % 3) * 18;

            Main.points.add(new Point(x + Math.cos(ang) * raio,
                                      y + Math.sin(ang) * raio,
                                      false));
        }
    }

    /* =========================
            RENDER
       ========================= */

    @Override
    public void render(Graphics2D g) {

        // O desenho do ataque vem ANTES do sprite pra a chefe nunca ficar
        // escondida atras dele — o jogador precisa ver onde mirar.
        if (getSpellCardAtual() != null) {
            getSpellCardAtual().render(g);
        }

        desenharSprite(g);
        desenharBarraDeVidaDoChefe(g);
        desenharAnuncio(g);
    }

    private void desenharSprite(Graphics2D g) {

        // Pisca durante a invulnerabilidade da troca de ataque.
        if (invulneravel > 0 && (invulneravel / 5) % 2 == 0) {
            return;
        }

        BufferedImage img = (sprite == null) ? null : Assets.get(sprite);

        if (img == null) {
            g.setColor(new Color(220, 60, 60));
            g.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
            return;
        }

        double caixa = radius * 2 * escalaSprite;
        double fator = caixa / Math.max(img.getWidth(), img.getHeight());

        int larg = (int) (img.getWidth() * fator);
        int alt  = (int) (img.getHeight() * fator);

        g.drawImage(img, (int) (x - larg / 2.0), (int) (y - alt / 2.0), larg, alt, null);

        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }

    /**
     * Barra de HP no TOPO do campo (nao em cima do chefe): e onde a serie
     * poe, e evita que o sprite grande cubra a propria barra.
     * Ao lado dela, bolinhas indicando quantos ataques ainda faltam.
     */
    private void desenharBarraDeVidaDoChefe(Graphics2D g) {

        int margem = 20;
        int x0 = Main.CAMPO_X + margem;
        int largura = Main.CAMPO_W - margem * 2;
        int y0 = Main.CAMPO_Y + 34;
        int altura = 7;

        g.setColor(new Color(30, 10, 20, 200));
        g.fillRect(x0, y0, largura, altura);

        double frac = (hpMaximo <= 0) ? 0 : Math.max(0, Math.min(1, hp / hpMaximo));

        g.setColor(new Color(230, 70, 90));
        g.fillRect(x0, y0, (int) (largura * frac), altura);

        g.setColor(new Color(150, 150, 170));
        g.drawRect(x0, y0, largura, altura);

        // Ataques restantes, incluindo o atual.
        int restantes = Math.max(0, spellCards.length - spellAtual);

        g.setColor(new Color(255, 220, 120));

        for (int i = 0; i < restantes; i++) {
            g.fillOval(x0 + i * 14, y0 - 16, 9, 9);
        }

        // Nome do ataque em andamento, no canto direito.
        if (temSpellAtivo()) {

            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.setColor(new Color(220, 200, 220));

            String nome = spellCards[spellAtual].getNome();
            int larguraTexto = g.getFontMetrics().stringWidth(nome);

            g.drawString(nome, x0 + largura - larguraTexto, y0 - 8);
        }
    }

    /** Nome do spell card em destaque no meio do campo, quando ele comeca. */
    private void desenharAnuncio(Graphics2D g) {

        if (anuncio <= 0 || !temSpellAtivo()) {
            return;
        }

        // Some suavemente no fim do anuncio.
        int alpha = (int) (255 * Math.min(1.0, anuncio / (double) Math.max(1, ticksAnuncio / 2)));
        alpha = Math.max(0, Math.min(255, alpha));

        g.setFont(new Font("Monospaced", Font.BOLD, 24));

        String nome = spellCards[spellAtual].getNome();
        int larguraTexto = g.getFontMetrics().stringWidth(nome);
        int cx = Main.CAMPO_X + Main.CAMPO_W / 2 - larguraTexto / 2;
        int cy = Main.CAMPO_Y + Main.CAMPO_H / 3;

        // Sombra preta atras, pra ler mesmo com a tela cheia de bala.
        g.setColor(new Color(0, 0, 0, alpha));
        g.drawString(nome, cx + 2, cy + 2);

        g.setColor(new Color(255, 210, 230, alpha));
        g.drawString(nome, cx, cy);
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    /** O spell card ativo, ou null se o chefe ja terminou todos. */
    public SpellCard getSpellCardAtual() {
        return temSpellAtivo() ? spellCards[spellAtual] : null;
    }

    public int getSpellAtual() {
        return spellAtual;
    }

    public int getTotalDeSpellCards() {
        return spellCards.length;
    }

    public boolean isInvulneravel() {
        return invulneravel > 0;
    }

    public int getTSpell() {
        return tSpell;
    }
}
