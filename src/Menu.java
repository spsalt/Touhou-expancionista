package src;

import java.awt.Graphics2D;

public class Menu {

    private int time = 0; // Só esperar uns segundois pra ver a troca do gameState

    public Menu() {
        
    }

    public void tick() {

        time++;

        if(time == 180){
            Main.gameState = "Game";
        }

    }

    public void render(Graphics2D g) {

        g.drawString("Menu", 20, 20);

    }

    

}