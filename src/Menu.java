package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Tela inicial.
 *
 * Navegacao com cima/baixo, confirma com ENTER.
 * Para adicionar uma opcao nova: coloque o texto em 'options' e trate o
 * nome dela no switch de confirmar(). O render se ajusta sozinho.
 */
public class Menu {

    private String[] options = { "Jogar", "Sair" };
    private int selected = 0;

    public Menu() {
    }

    public void tick() {

        // As flags sao zeradas na hora de usar pra o menu nao andar
        // varias casas com um toque so (o tick roda 60x por segundo).
        if (Main.up) {

            Main.up = false;
            Som.tocar(Som.MENU_MOVER);
            selected--;

            if (selected < 0) {
                selected = options.length - 1;
            }

        } else if (Main.down) {

            Main.down = false;
            Som.tocar(Som.MENU_MOVER);
            selected++;

            if (selected > options.length - 1) {
                selected = 0;
            }

        } else if (Main.enter) {

            Main.enter = false;
            Som.tocar(Som.MENU_OK);
            confirmar();
        }
    }

    private void confirmar() {

        switch (options[selected]) {

            case "Jogar":
                // Comeca sempre do zero, inclusive vindo de um game over.
                Main.reiniciarPartida();

                // debug.pularCutscenes=true entra direto na fase, util pra
                // testar jogabilidade sem clicar nos dialogos toda hora.
                if (Config.getBool("debug.pularCutscenes", false)) {
                    Main.gameState = "Game";
                    Main.musica.tocarDoInicio();
                } else {
                    Main.mostrarCutscene(Cutscene.criarIntro());
                }
                break;

            case "Sair":
                System.exit(0);
                break;

            default:
                break;
        }
    }

    public void render(Graphics2D g) {

        int centroX = Main.WIDTH / 2;

        // Foto da portaria 1 como pano de fundo do menu.
        BufferedImage fundo = Assets.get("sprites/ambient/portaria1.png");

        if (fundo != null) {
            g.drawImage(fundo, 0, 0, Main.WIDTH, Main.HEIGHT, null);
            // Escurece: sem isso o titulo e as opcoes somem no ceu claro.
            g.setColor(new Color(0, 0, 0, Config.getInt("menu.escurecimentoFundo", 150)));
            g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);
        }

        // Simbolo do menu, se o PNG existir.
        BufferedImage simbolo = Assets.get("simbolo_menu.png");

        if (simbolo != null) {
            g.drawImage(simbolo, centroX - 80, 90, 160, 160, null);
        }

        desenharRetratoDoJogador(g);

        g.setFont(new Font("Monospaced", Font.BOLD, 40));
        g.setColor(new Color(255, 220, 120));
        g.drawString("TOUHOU 67", centroX - 120, 300);

        g.setFont(new Font("Monospaced", Font.PLAIN, 20));
        g.setColor(new Color(190, 190, 190));
        g.drawString("Antimony of Recogna's Expansion", centroX - 180, 332);

        // Opcoes: a selecionada fica vermelha e ganha um cursor.
        g.setFont(new Font("Monospaced", Font.BOLD, 24));

        for (int i = 0; i < options.length; i++) {

            boolean ativa = (i == selected);

            g.setColor(ativa ? new Color(255, 90, 90) : Color.WHITE);
            g.drawString((ativa ? "> " : "  ") + options[i], centroX - 60, 420 + i * 40);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(120, 120, 120));
        g.drawString("Setas/WASD para navegar - ENTER para confirmar", centroX - 190, Main.HEIGHT - 40);
    }

    /** Retrato do estudante (protagonista) no canto, tipo "carta de personagem". */
    private void desenharRetratoDoJogador(Graphics2D g) {

        BufferedImage retrato = Assets.get("sprites/player/estudante.png");

        if (retrato == null) {
            return;
        }

        int tam = 130;
        int x = Main.WIDTH - tam - 36;
        int y = 30;

        // Anel dourado por baixo do retrato, meio pixel maior que ele —
        // fica parecendo uma moldura em vez de so colar a foto na tela.
        g.setColor(new Color(255, 220, 120));
        g.fillOval(x - 4, y - 4, tam + 8, tam + 8);

        g.drawImage(retrato, x, y, tam, tam, null);

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(180, 180, 180));

        String legenda = "voce";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(legenda, x + (tam - fm.stringWidth(legenda)) / 2, y + tam + 18);
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public int getSelected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    public String[] getOptions() {
        return options;
    }
}
