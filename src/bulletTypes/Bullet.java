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

    public Bullet() {
    }

    /** Logica por frame: mover, checar limites, checar colisao. */
    public void tick() {
    }

    /** Somente desenho, sem logica. */
    public void render(Graphics2D g) {
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

    public boolean isHitPlayer() {
        return hitPlayer;
    }

    public void setHitPlayer(boolean hitPlayer) {
        this.hitPlayer = hitPlayer;
    }
}
