package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public class Menu {

    private String[] options = {"Jogar", "Sair"};
    private int selected = 0;

    public Menu() {
        
    }

    public void tick() {

        if(Main.up){

            Main.up = false;
            selected--;
            if(selected < 0)
                selected = options.length-1;

        }else if(Main.down){

            Main.down = false;
            selected++;
            if(selected > options.length-1)
                selected = 0;

        }else if(Main.enter){

            Main.enter = false;

            if(options[selected].equals("Jogar"))
                Main.gameState = "Game";
            else if(options[selected].equals("Sair"))
                System.exit(0);

        }

    }

    public void render(Graphics2D g) {

        g.setFont(new Font("Serif", Font.PLAIN, 18));

        g.drawString("Menu", 20, 20);

        g.setColor(Color.WHITE);
        if(selected == 0)
            g.setColor(Color.RED);
        g.drawString("Jogar", 20, 50);

        g.setColor(Color.WHITE);
        if(selected == 1)
            g.setColor(Color.RED);
        g.drawString("Sair", 20, 70);

    }

    

}