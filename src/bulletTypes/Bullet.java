package src.bulletTypes;

import java.awt.*;

/**
 * Classe base de todo projetil do jogo.
 *
 * Guarda so o que TODA bala precisa ter: posicao, raio de colisao, dano,
 * de quem ela e (jogador ou inimigo) e se ainda esta viva.
 * O movimento fica nas subclasses (ver IntegralBullet), que sobrescrevem
 * tick(). O Main trata todas como Bullet, sem saber o tipo concreto:
 * isso e o polimorfismo que segura o loop principal simples.
 */
public class Bullet {

    protected double x, y;

    /** Raio usado na colisao (circulo contra circulo). */
    protected double radius = 4;

    /** Quanto de HP essa bala tira de quem ela acertar. */
    protected double dano = 1;

    protected boolean isAlive = true;

    /** true = bala inimiga (machuca o jogador); false = bala do jogador (machuca inimigos). */
    protected boolean hitPlayer = false;

    /** PNG do desenho. null = a subclasse desenha na mao (circulo, glifo...). */
    protected String sprite = null;

    /** Opacidade do sprite, 0 a 1. Usada pra marcar bala fraca. */
    protected float opacidadeSprite = 1f;

    /**
     * Esta bala ja foi "rocada" (graze) pelo jogador?
     *
     * Cada bala so vale graze UMA vez. Sem essa marca, ficar parado ao
     * lado de uma bala lenta renderia pontos a 60 por segundo, e o
     * incentivo viraria "encoste e espere" em vez de "passe raspando".
     */
    protected boolean rocada = false;

    /**
     * Onde esta bala estava no frame anterior.
     *
     * Existe pro buff "Programacao Orientada a Objetos" (ver Player):
     * pra saber se uma bala vem na sua direcao e preciso saber PRA ONDE
     * ela vai, e cada tipo de bala guarda isso de um jeito diferente — a
     * IntegralBullet tem dx/dy, o PonteiroBullet recalcula a direcao todo
     * tick perseguindo alguem, o XadrezBullet salta, a Bullet3D anda em
     * perspectiva.
     *
     * Em vez de pedir a cada subclasse que exponha a propria velocidade
     * (e confiar que todas as oito respondam certo), a classe base MEDE:
     * a velocidade real e simplesmente onde ela esta menos onde estava.
     * Funciona pra qualquer bala que exista hoje e pra qualquer uma que a
     * gente escreva depois, sem ela precisar saber que o buff existe.
     */
    protected double xAnterior, yAnterior;

    public Bullet() {
    }

    /**
     * Guarda a posicao atual antes do tick. Chamado pelo Main uma vez por
     * frame, pra todas as balas.
     */
    public void guardarPosicao() {
        this.xAnterior = x;
        this.yAnterior = y;
    }

    /** Quanto ela andou no ultimo frame, em pixels por tick. */
    public double getVelX() {
        return x - xAnterior;
    }

    public double getVelY() {
        return y - yAnterior;
    }

    /** Logica por frame: mover, checar limites, checar colisao. */
    public void tick() {
    }

    /** Somente desenho, sem logica. */
    public void render(Graphics2D g) {
    }

    /**
     * Desenha o sprite da bala girado pra apontar na direcao 'angulo'.
     *
     * O PNG e desenhado apontando PRA CIMA; aqui somamos PI/2 porque na
     * tela o angulo 0 aponta pra direita. Sem essa correcao toda bala
     * sairia deitada 90 graus.
     *
     * @param escalaAoRaio quantas vezes o raio de colisao o desenho ocupa
     * @return true se desenhou (havia sprite); false pra subclasse cair
     *         no desenho manual dela
     */
    protected boolean desenharSprite(Graphics2D g, double angulo, double escalaAoRaio) {

        if (sprite == null) {
            return false;
        }

        java.awt.image.BufferedImage img = src.Assets.get(sprite);

        if (img == null) {
            return false;
        }

        int alt = (int) (radius * 2 * escalaAoRaio);
        int larg = Math.max(1, img.getWidth() * alt / img.getHeight());

        // Pega a versao JA no tamanho certo. Sem isso, o Java2D reescala o
        // PNG a cada chamada — e o corredor dos Seguidores do IEEE chega a
        // 270 balas com sprite na tela, ou seja, 270 redimensionamentos
        // por frame sempre pro mesmo tamanho.
        java.awt.image.BufferedImage pronta = src.Assets.getEscalado(sprite, larg, alt);

        if (pronta != null) {
            img = pronta;
        }

        java.awt.geom.AffineTransform anterior = g.getTransform();
        java.awt.Composite composto = g.getComposite();

        if (opacidadeSprite < 1f) {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, opacidadeSprite));
        }

        g.rotate(angulo + Math.PI / 2, x, y);
        g.drawImage(img, (int) (x - larg / 2.0), (int) (y - alt / 2.0), larg, alt, null);

        g.setTransform(anterior);
        g.setComposite(composto);

        return true;
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

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getDano() {
        return dano;
    }

    public void setDano(double dano) {
        this.dano = dano;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean isAlive) {
        this.isAlive = isAlive;
    }

    public String getSprite() {
        return sprite;
    }

    public void setSprite(String sprite) {
        this.sprite = sprite;
    }

    public void setOpacidadeSprite(float opacidadeSprite) {
        this.opacidadeSprite = Math.max(0f, Math.min(1f, opacidadeSprite));
    }

    public boolean isRocada() {
        return rocada;
    }

    public void setRocada(boolean rocada) {
        this.rocada = rocada;
    }

    public boolean isHitPlayer() {
        return hitPlayer;
    }

    public void setHitPlayer(boolean hitPlayer) {
        this.hitPlayer = hitPlayer;
    }
}
