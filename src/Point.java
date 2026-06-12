package src;

import java.awt.*;

public class Point {
    
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

    public void tick() {

        y += dy;
        x += dx;

        if(y > Main.HEIGHT)
            isAlive = false;

        if(Main.getDist(x, y, Main.player.getX(), Main.player.getY()) < 5){
            isAlive = false;
            Main.player.setXp(Main.player.getXp()+1);
        }

        if(Main.getDist(x, y, Main.player.getX(), Main.player.getY()) < 50){
            isCatch = true;
        }

        if(isCatch){
            dx = Main.getCos(Main.player.getX(), Main.player.getY(), x, y) * 3;
            dy = Main.getSin(Main.player.getX(), Main.player.getY(), x, y) * 3;
        }

    }

    public void render(Graphics2D g){

        g.setColor(Color.BLUE);

        g.drawRect((int)x, (int)y, 0, 0);
        g.drawRect((int)x-10, (int)y-10, 20, 20);

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
