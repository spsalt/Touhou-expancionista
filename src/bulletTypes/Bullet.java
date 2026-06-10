package src.bulletTypes;

import java.awt.*;

public class Bullet{
    
    protected double x, y;
    protected boolean isAlive = true, hitPlayer = false;

    public Bullet() {

    }

    public void tick(){

    }

    public void render(Graphics2D g){
        
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean isAlive) {
        this.isAlive = isAlive;
    }

    

}
