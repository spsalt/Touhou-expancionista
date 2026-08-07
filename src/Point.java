package src;

import java.awt.*;

/**
 * Item de XP que os inimigos dropam ao morrer.
 *
 * Cai devagar; se o jogador chegar dentro do raio de coleta, ele passa a
 * ser atraido e vai atras do jogador ate encostar (isCatch = true).
 */
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

        // Saiu pelo fundo do campo: perdido.
        if(y > Main.CAMPO_Y + Main.CAMPO_H)
            isAlive = false;

        if(Main.player == null)
            return;

        double dist = Main.getDist(x, y, Main.player.getX(), Main.player.getY());

        // Encostou: vira XP.
        if(dist < 8){
            isAlive = false;
            Main.player.setXp(Main.player.getXp()+1);
            return;
        }

        // Entrou no raio de coleta: a partir daqui persegue o jogador pra sempre.
        if(dist < Main.player.getRaioColeta()){
            isCatch = true;
        }

        if(isCatch){
            dx = Main.getCos(Main.player.getX(), Main.player.getY(), x, y) * 5;
            dy = Main.getSin(Main.player.getX(), Main.player.getY(), x, y) * 5;
        }

    }

    public void render(Graphics2D g){

        // Muda de cor quando esta sendo atraido, pra dar feedback da coleta.
        g.setColor(isCatch ? Color.CYAN : new Color(80, 120, 255));

        g.fillRect((int)x-5, (int)y-5, 10, 10);

        g.setColor(Color.WHITE);
        g.drawRect((int)x-5, (int)y-5, 10, 10);

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
