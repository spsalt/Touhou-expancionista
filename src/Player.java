package src;

import java.awt.Color;
import java.awt.Graphics2D;

import src.bulletTypes.IntegralBullet;

public class Player {

    private double x;
    private double y;
    private double speed;

    private int xp = 0;
    private int level = 1;

    private int shootTime = 10;
    private double shootRad = 0.5;

    private double radius;

    public Player(double x, double y, double radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public void tick() {

        if(Main.x){
            speed = 1.75;
            shootRad = 0.1;
        }else{
            speed = 4;
            shootRad = 0.5;
        }

        if(xp >= Math.pow(4, level)){
            xp -= Math.pow(4, level);
            level ++;
        }

        if(shootTime % 5 == 0 && shootTime >= 0){
            Main.points.add(new Point(Main.WIDTH/2, 0, false));
        }
     
        if(Main.z && shootTime <= 0){

            for(int i = 0; i < level; i++){
                Main.bullets.add(
                    new IntegralBullet(
                        x,y,
                        Math.sin(level == 1? 0: -shootRad / 2.0 + i * (shootRad / (level - 1.0))) * 5,
                        -Math.cos(level == 1? 0: -shootRad / 2.0 + i * (shootRad / (level - 1.0))) * 5,
                        0,0,4,false
                    )
                );
            }

            double spd = 1.5;

            // Top-left corner
            double dist = Main.getDist(0, 0, x, y);
            Main.bullets.add(new IntegralBullet(0, 0, spd * (x / dist), spd * (y / dist), 0, 0, 10, true));
            // Top-right corner
            dist = Main.getDist(Main.WIDTH, 0, x, y);
            Main.bullets.add(new IntegralBullet(Main.WIDTH, 0, spd * ((x - Main.WIDTH) / dist), spd * ((y - 0) / dist), 0, 0, 10, true));
            // Bottom-left corner
            dist = Main.getDist(0, Main.HEIGHT, x, y);
            Main.bullets.add(new IntegralBullet(0, Main.HEIGHT, spd * ((x - 0) / dist), spd * ((y - Main.HEIGHT) / dist), 0, 0, 10, true));
            // Bottom-right corner
            dist = Main.getDist(Main.WIDTH, Main.HEIGHT, x, y);
            Main.bullets.add(new IntegralBullet(Main.WIDTH, Main.HEIGHT, spd * ((x - Main.WIDTH) / dist), spd * ((y - Main.HEIGHT) / dist), 0, 0, 10, true));

            shootTime = 5;
        }
        shootTime--;

        if(Main.up) y-=speed;
        if(Main.down) y+=speed;
        if(Main.left) x-=speed;
        if(Main.right) x+=speed;

    }

    public void render(Graphics2D g) {

        g.setColor(Color.WHITE);
        g.fillRect((int)(x-10), (int)(y-20), 20, 40);
        
        g.setColor(Color.GREEN);
        g.fillOval((int)(x-radius), (int)(y-radius), 2*(int)radius, 2*(int)radius);
        
        g.setColor(Color.RED);
        g.drawRect((int)x, (int)y, 0, 0);
        

        g.setColor(Color.BLUE);
        g.drawOval((int)x-50, (int)y-50, 100, 100);

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

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getShootTime() {
        return shootTime;
    }

    public void setShootTime(int shootTime) {
        this.shootTime = shootTime;
    }

    public double getShootRad() {
        return shootRad;
    }

    public void setShootRad(double shootRad) {
        this.shootRad = shootRad;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    

}