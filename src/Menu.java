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

    private String[] options = { "Jogar", "Personagem", "Sair" };
    private int selected = 0;

    /**
     * Quantos ticks faz que a skin mudou. So pra o brilho do aviso.
     *
     * Sem nenhuma resposta visual, apertar a seta com o retrato no canto
     * da tela nao parece ter feito nada — o olho esta na lista de opcoes,
     * nao no retrato.
     */
    private int piscaDaTroca = 0;

    public Menu() {
    }

    public void tick() {

        if (piscaDaTroca > 0) {
            piscaDaTroca--;
        }

        // O SELETOR DE PERSONAGEM RESPONDE ESQUERDA/DIREITA.
        //
        // Nao e uma tela separada de proposito. Escolher personagem e uma
        // decisao de um segundo, e mandar o jogador entrar num submenu,
        // escolher e voltar custa mais atencao do que a decisao vale. Na
        // propria linha, ele ve o nome mudando e o retrato trocando junto.
        if (options[selected].equals("Personagem") && (Main.left || Main.right)) {

            int passo = Main.left ? -1 : 1;

            Main.left = false;
            Main.right = false;

            Skin.trocar(passo);
            Som.tocar(Som.MENU_MOVER);

            piscaDaTroca = 24;
            return;
        }

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

            case "Personagem":
                // ENTER aqui so avanca pro proximo, pra quem nao percebeu
                // que da pra usar as setas nao ficar preso na linha.
                Skin.trocar(1);
                piscaDaTroca = 24;
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

            String texto = options[i];

            // A LINHA DO PERSONAGEM MOSTRA A ESCOLHA NELA MESMA.
            //
            // "Personagem  < Lucas >" diz o que esta escolhido E como
            // trocar, em uma linha. Escrever so "Personagem" obrigaria uma
            // legenda em outro canto explicando as setas.
            if (texto.equals("Personagem")) {
                texto = texto + "   < " + Skin.atual().getNome() + " >";
            }

            g.setColor(ativa ? new Color(255, 90, 90) : Color.WHITE);
            g.drawString((ativa ? "> " : "  ") + texto, centroX - 60, 420 + i * 40);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(120, 120, 120));
        g.drawString("Setas/WASD para navegar - ENTER para confirmar", centroX - 190, Main.HEIGHT - 40);

        if (options[selected].equals("Personagem")) {
            g.setColor(new Color(255, 200, 120));
            g.drawString("esquerda/direita troca de personagem",
                         centroX - 150, Main.HEIGHT - 62);
        }
    }

    /**
     * A CARTA DE PERSONAGEM: retrato, nome e a paleta dos tiros dele.
     *
     * As tres bolinhas de cor embaixo nao sao enfeite — sao a informacao
     * que muda de verdade entre um personagem e outro. O rosto voce
     * reconhece na hora; ja "qual vai ser a cor dos meus tiros" so daria
     * pra descobrir entrando na fase, e voltar pro menu pra trocar depois
     * de ver e exatamente o passeio que este painel evita.
     */
    private void desenharRetratoDoJogador(Graphics2D g) {

        Skin skin = Skin.atual();

        BufferedImage retrato = Assets.get(skin.getSprite());

        if (retrato == null) {
            return;
        }

        int tam = 130;
        int x = Main.WIDTH - tam - 36;
        int y = 30;

        // A moldura acende por um instante quando voce troca: o olho esta
        // na lista de opcoes, e sem isso a troca acontece fora do campo de
        // visao e parece que a tecla nao fez nada.
        int brilho = (int) (120 * (piscaDaTroca / 24.0));

        g.setColor(new Color(255,
                             Math.min(255, 220 + brilho / 4),
                             Math.min(255, 120 + brilho)));
        g.fillOval(x - 4, y - 4, tam + 8, tam + 8);

        g.drawImage(retrato, x, y, tam, tam, null);

        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(new Color(235, 235, 235));

        FontMetrics fm = g.getFontMetrics();
        String nome = skin.getNome();
        g.drawString(nome, x + (tam - fm.stringWidth(nome)) / 2, y + tam + 18);

        // A paleta: leque, ponteiro, ricochete — na mesma ordem em que o
        // jogador destrava os tres tiros.
        Color[] paleta = { skin.getCorDoLeque(), skin.getCorDoPonteiro(), skin.getCorDoRicochete() };

        int d = 12;
        int larguraTotal = paleta.length * d + (paleta.length - 1) * 6;
        int px = x + (tam - larguraTotal) / 2;
        int py = y + tam + 28;

        for (int i = 0; i < paleta.length; i++) {

            g.setColor(paleta[i]);
            g.fillOval(px + i * (d + 6), py, d, d);

            g.setColor(new Color(0, 0, 0, 120));
            g.drawOval(px + i * (d + 6), py, d, d);
        }
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
