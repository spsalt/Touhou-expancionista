package src.phases;

public class phase1 {
    
    private int time = 0;
    private int stage = 1;

    public void tick(){

        switch(stage){
            case 1:
                stage1();
                break;
            case 2:
                stage2();
                break;
            case 3:
                stage3();
                break;
            case 4:
                stage4();
                break;
            case 5:
                stage5();
                break;
        }

    }

    public static void stage1(){

    }

    public static void stage2(){

    }
    
    public static void stage3(){

    }

    public static void stage4(){

    }
    
    public static void stage5(){

    }

}
