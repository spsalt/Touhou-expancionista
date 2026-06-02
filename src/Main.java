package src;

import javax.swing.*;

public class Main extends JFrame{

    public static boolean isRunning = true;

    Main(){
        super("Touhou expancionista");
        super.setSize(1080, 720);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
    }

    public static void main(String[] args) {

        new Main();
        run();

    }

    private static void tick(){
        System.out.println("tick\n");
    }

    private static void render(){
        System.out.println("render\n");
    }


    private static void run(){

        while(isRunning){
            
            tick();
            render();

        }

    }

}
