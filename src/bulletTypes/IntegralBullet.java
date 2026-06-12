package src.bulletTypes;

import java.awt.*;

import src.Main;

public class IntegralBullet extends Bullet{
    
    private double dx, dy, d2x, d2y;
    private double radius;

    public IntegralBullet(double x, double y, double dx, double dy, double d2x, double d2y, double radius, boolean hitPlayer){
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.d2x = d2x;
        this.d2y = d2y;
        this.radius = radius;
        this.hitPlayer = hitPlayer;
    }

    @Override
    public void tick(){

        x += dx;
        y += dy;

        dx+=d2x;
        dy+=d2y;

        if(x < 0 || x > Main.WIDTH
            || y < 0 || y > Main.HEIGHT
        ){
            this.isAlive = false;
        }

        if(isAlive && hitPlayer){
            if(Main.getDist(this.x, this.y, Main.player.getX(), Main.player.getY()) <= this.radius + Main.player.getRadius()){
                this.isAlive = false;
                System.exit(0);
            }
        }

    }

    @Override
    public void render(Graphics2D g){

        g.setColor(Color.RED);
        g.drawRect((int)x, (int)y, 0, 0);
        g.fillOval((int)(x-radius), (int)(y-radius), (int)radius*2, (int)radius*2);

    }

    public double getDx() {
        return dx;
    }

    public void setDx(double dx) {
        this.dx = dx;
    }

    public double getDy() {
        return dy;
    }

    public void setDy(double dy) {
        this.dy = dy;
    }

    public double getD2x() {
        return d2x;
    }

    public void setD2x(double d2x) {
        this.d2x = d2x;
    }

    public double getD2y() {
        return d2y;
    }

    public void setD2y(double d2y) {
        this.d2y = d2y;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    

}
