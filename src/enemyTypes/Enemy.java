package src.enemyTypes;

import java.awt.*;
import java.awt.image.BufferedImage;

import src.Assets;
import src.Config;
import src.Main;
import src.Point;
import src.Som;
import src.bulletTypes.IntegralBullet;

/**
 * Classe base de todo inimigo (incluindo os chefoes-professores).
 *
 * O contrato para as subclasses e curto de proposito:
 *   - sobrescreva mover()  -> onde ele vai
 *   - sobrescreva atirar() -> o que ele cospe
 * O resto (contador de tempo, HP, morte, drop de itens, desenho) ja vem pronto.
 *
 * O contador 't' e o tempo de vida do inimigo em ticks, contado a partir do
 * spawn. E a ideia do README: posicao e ataques sao funcoes de t, entao o
 * padrao inteiro cabe numa formula em vez de uma maquina de estados.
 */
public class Enemy {

    protected double x, y;

    protected double hp;
    protected double hpMaximo;

    /** Raio de colisao contra as balas do jogador. */
    protected double radius;

    /** Ticks desde o spawn. Base de todo padrao de movimento/ataque. */
    protected int t = 0;

    protected boolean isAlive = true;

    /** Pontuacao dada ao jogador quando morre. */
    protected int pontos = 100;

    /** Quantos itens de XP dropa ao morrer. */
    protected int itens = 3;

    /** Caminho do PNG. Se null ou nao encontrado, desenha uma forma. */
    protected String sprite = null;

    /**
     * Tamanho do desenho em relacao ao raio de colisao.
     * Separado de propositio: o sprite de um chefe e enorme, mas a hitbox
     * dele tem que continuar pequena pro jogo ser justo.
     */
    protected double escalaSprite = 2.0;

    public Enemy(double x, double y, double hp, double radius) {
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.hpMaximo = hp;
        this.radius = radius;
    }

    /**
     * Ordem fixa: primeiro anda, depois atira, depois checa se saiu da tela.
     * O 't' so avanca no fim, entao no primeiro tick t vale 0.
     */
    public void tick() {

        mover();
        atirar();

        // Inimigo que saiu muito do campo some (sem dar pontos).
        if (Main.foraDoCampo(x, y, 200)) {
            isAlive = false;
        }

        t++;
    }

    /** Sobrescreva: movimento do inimigo. Use o 't' pra fazer formulas. */
    protected void mover() {
    }

    /** Sobrescreva: padrao de tiro. Use "t % cadencia == 0" pra ritmar. */
    protected void atirar() {
    }

    public void render(Graphics2D g) {

        BufferedImage img = (sprite == null) ? null : Assets.get(sprite);

        if (img != null) {

            // Encaixa o PNG numa caixa quadrada SEM esticar: pega o maior
            // lado da imagem como referencia e escala os dois na mesma
            // proporcao. Sem isso um sprite 501x280 sairia achatado.
            double caixa = radius * 2 * escalaSprite;
            double fator = caixa / Math.max(img.getWidth(), img.getHeight());

            int larg = (int) (img.getWidth() * fator);
            int alt  = (int) (img.getHeight() * fator);

            g.drawImage(img, (int) (x - larg / 2.0), (int) (y - alt / 2.0), larg, alt, null);

        } else {
            g.setColor(Color.MAGENTA);
            g.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }

        // Hitbox de debug: mostra exatamente onde as balas do jogador acertam.
        if (Main.debugMode) {
            g.setColor(Color.YELLOW);
            g.drawOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }

        renderBarraDeVida(g);
    }

    /** Barrinha de HP acima do inimigo. Some quando ele esta com a vida cheia. */
    protected void renderBarraDeVida(Graphics2D g) {

        if (hp >= hpMaximo) {
            return;
        }

        int largura = (int) (radius * 2);
        int topo = (int) (y - radius - 8);

        g.setColor(Color.DARK_GRAY);
        g.fillRect((int) (x - radius), topo, largura, 4);

        g.setColor(Color.GREEN);
        g.fillRect((int) (x - radius), topo, (int) (largura * (hp / hpMaximo)), 4);
    }

    /**
     * Aplica dano. Chamado pelo Main quando uma bala do jogador encosta.
     * @return true se este golpe matou o inimigo
     */
    public boolean levarDano(double dano) {

        if (!isAlive) {
            return false;
        }

        hp -= dano;

        if (hp <= 0) {
            morrer();
            return true;
        }

        return false;
    }

    /** Morte "boa": da pontos e dropa itens. Sobrescreva pra chefe (dialogo, fase 2...). */
    protected void morrer() {

        isAlive = false;
        Som.tocar(Som.INIMIGO_MORRE);

        if (Main.player == null) {
            return;
        }

        Main.player.setPontuacao(Main.player.getPontuacao() + pontos);

        // Espalha os itens num circulo pequeno em volta de onde ele morreu.
        for (int i = 0; i < itens; i++) {

            double ang = (2 * Math.PI * i) / Math.max(1, itens);

            Main.points.add(new Point(x + Math.cos(ang) * 12,
                                      y + Math.sin(ang) * 12,
                                      false));
        }
    }

    /* =========================
            HELPERS DE MIRA
       =========================
       Uteis pra qualquer subclasse montar tiro mirado no jogador.
    */

    /** Componente X unitario apontando deste inimigo para o jogador. */
    protected double dirXParaJogador() {

        if (Main.player == null) {
            return 0;
        }

        return Main.getCos(Main.player.getX(), Main.player.getY(), x, y);
    }

    /** Componente Y unitario apontando deste inimigo para o jogador. */
    protected double dirYParaJogador() {

        if (Main.player == null) {
            return 1;
        }

        return Main.getSin(Main.player.getX(), Main.player.getY(), x, y);
    }

    /**
     * Dispara uma bala reta na direcao do jogador.
     * E o tiro mais basico que existe; quase todo inimigo usa este metodo
     * dentro do seu atirar(), so mudando o ritmo.
     */
    protected void atirarMirado(double velocidade, double raio, Color cor) {

        Main.bullets.add(new IntegralBullet(
            x, y,
            dirXParaJogador() * velocidade,
            dirYParaJogador() * velocidade,
            0, 0,
            raio,
            true,          // bala inimiga
            cor
        ));
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

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

    public double getHp() {
        return hp;
    }

    public void setHp(double hp) {
        this.hp = hp;
    }

    public double getHpMaximo() {
        return hpMaximo;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public int getT() {
        return t;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean isAlive) {
        this.isAlive = isAlive;
    }

    public int getPontos() {
        return pontos;
    }

    public void setPontos(int pontos) {
        this.pontos = pontos;
    }

    public int getItens() {
        return itens;
    }

    public void setItens(int itens) {
        this.itens = itens;
    }

    public String getSprite() {
        return sprite;
    }

    public void setSprite(String sprite) {
        this.sprite = sprite;
    }

    public double getEscalaSprite() {
        return escalaSprite;
    }

    public void setEscalaSprite(double escalaSprite) {
        this.escalaSprite = escalaSprite;
    }
}
