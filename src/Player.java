package src;

import java.awt.Color;
import java.awt.Graphics2D;

import src.bulletTypes.LinearBullet;

public class Player {

    private double x;
    private double y;
    private double speed;

    private int shootTime = 10;

    private double radius;

    public Player(double x, double y, double radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public void tick() {

        if(Main.x)
            speed = 1.75;
        else
            speed = 4;

        if(Main.z && shootTime <= 0){

            Main.bullets.add(new LinearBullet(x, y, 0, -5, 0, 0, 4, false));

            double spd = 1.5;

            // Top-left corner
            double dist = Main.getDist(0, 0, x, y);
            Main.bullets.add(new LinearBullet(0, 0, spd * (x / dist), spd * (y / dist), 0, 0, 10, true));
            // Top-right corner
            dist = Main.getDist(Main.WIDTH, 0, x, y);
            Main.bullets.add(new LinearBullet(Main.WIDTH, 0, spd * ((x - Main.WIDTH) / dist), spd * ((y - 0) / dist), 0, 0, 10, true));
            // Bottom-left corner
            dist = Main.getDist(0, Main.HEIGHT, x, y);
            Main.bullets.add(new LinearBullet(0, Main.HEIGHT, spd * ((x - 0) / dist), spd * ((y - Main.HEIGHT) / dist), 0, 0, 10, true));
            // Bottom-right corner
            dist = Main.getDist(Main.WIDTH, Main.HEIGHT, x, y);
            Main.bullets.add(new LinearBullet(Main.WIDTH, Main.HEIGHT, spd * ((x - Main.WIDTH) / dist), spd * ((y - Main.HEIGHT) / dist), 0, 0, 10, true));
                        
            shootTime = 40;
        }
        shootTime--;

        if(Main.up) y-=speed;
        if(Main.down) y+=speed;
        if(Main.left) x-=speed;
        if(Main.right) x+=speed;

    }

    public void render(Graphics2D g) {

        g.setColor(Color.RED);
        g.drawRect((int)x, (int)y, 0, 0);
        g.drawOval((int)(x-radius), (int)(y-radius), 2*(int)radius, 2*(int)radius);

        g.setColor(Color.WHITE);
        g.drawRect((int)(x-10), (int)(y-20), 20, 40);

    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getRadius() {
        return radius;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    

}