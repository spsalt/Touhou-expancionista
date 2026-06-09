package src;

import java.awt.Graphics2D;

public class Player {

    private double x;
    private double y;

    private double radius;

    public Player(double x, double y, double radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public void tick() {

        x++;
        y--;

    }

    public void render(Graphics2D g) {

        g.fillOval((int)x, (int)y, 2*(int)radius, 2*(int)radius);

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
}