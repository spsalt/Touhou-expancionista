package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A LOJINHA DO PEREA.
 *
 * Abre uma vez, depois do Clayton, e e o unico lugar do jogo onde o
 * dinheiro serve pra alguma coisa. Enquanto ela esta na tela o mundo fica
 * congelado — igual ao menu de continue.
 *
 * POR QUE ELA EXISTE, DO PONTO DE VISTA DE JOGO
 * ---------------------------------------------
 * O sistema de niveis do jogo subia sozinho ate o topo: quem jogava bem
 * chegava no PAPA com tudo desbloqueado, e nao havia decisao nenhuma no
 * meio do caminho. Agora os itens param no nivel 3 e os dois ultimos
 * niveis estao AQUI, competindo com os tres itens do Perea pelo mesmo
 * dinheiro. E a primeira escolha de construcao que o jogo pede: mais tiro,
 * mais pontos, ou ferramentas pra luta final.
 *
 * O ESTOQUE
 * ---------
 *   NIVEL             +1 nivel, ate o teto de verdade. Compravel duas vezes.
 *   CULTURA MAKER     passiva: inimigo vale mais ponto.
 *   OLHO LASER        +1 uso do raio (tecla 1).
 *   AGRICULTURA       +1 uso dos drones (tecla 2).
 *
 * Os precos ficam no game.properties. Eles foram calibrados contra o
 * quanto de moeda a fase inteira produz ate aqui — ver o bloco do Perea
 * no properties.
 */
public class LojaDoPerea {

    /** O que da pra comprar. A ordem e a ordem na tela. */
    private enum Produto {
        NIVEL,
        CULTURA_MAKER,
        OLHO_LASER,
        AGRICULTURA,
        SAIR
    }

    private final Produto[] estoque = Produto.values();

    private int selecionado = 0;

    /** Cronometro proprio, so pro brilho pulsar. */
    private int t = 0;

    /** Mensagem curta do Perea depois de uma acao ("supimpa!", "ta pobre"). */
    private String fala = "";
    private int ticksDaFala = 0;

    /** Zera tudo. Chamado toda vez que a loja abre. */
    public void reiniciar() {

        selecionado = 0;
        t = 0;
        fala = "chega mais, olha só o que eu tenho";
        ticksDaFala = 240;
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        t++;

        if (ticksDaFala > 0) {
            ticksDaFala--;
        }

        // As flags sao zeradas na hora de usar, senao um toque anda varias
        // casas — o tick roda 60x por segundo. Mesma solucao do Menu e do
        // MenuDeContinue.
        if (Main.up) {

            Main.up = false;
            Som.tocar(Som.MENU_MOVER);
            selecionado = (selecionado + estoque.length - 1) % estoque.length;

        } else if (Main.down) {

            Main.down = false;
            Som.tocar(Som.MENU_MOVER);
            selecionado = (selecionado + 1) % estoque.length;

        } else if (Main.enter || Main.z) {

            Main.enter = false;
            Main.z = false;

            confirmar();

        } else if (Main.esc) {

            Main.esc = false;
            fechar();
        }
    }

    private void confirmar() {

        Produto p = estoque[selecionado];

        if (p == Produto.SAIR) {
            Som.tocar(Som.MENU_OK);
            fechar();
            return;
        }

        if (esgotado(p)) {
            recusar("isso aí acabou, meu");
            return;
        }

        int preco = precoDe(p);

        // A ORDEM IMPORTA: so cobra depois de saber que a entrega vai dar
        // certo. Cobrar primeiro e entregar depois deixaria o jogador sem
        // moeda e sem item se alguma checagem falhasse no meio.
        if (Main.player.getMoedas() < preco) {
            recusar("tá sem peso aí, parceiro");
            return;
        }

        if (!entregar(p)) {
            recusar("isso aí acabou, meu");
            return;
        }

        Main.player.gastarMoedas(preco);

        Som.tocar(Som.COMPRA);
        dizer("supimpa! leva mais coisa");
    }

    /** Efetiva a compra. false = nao deu (ja estava no teto). */
    private boolean entregar(Produto p) {

        switch (p) {

            case NIVEL:
                return Main.player.comprarNivel();

            case CULTURA_MAKER:
                Main.player.setCulturaMaker(true);
                return true;

            case OLHO_LASER:
                Main.player.darUsosDoOlhoLaser(
                        Config.getInt("perea.olhoLaser.usosPorCompra", 1));
                return true;

            case AGRICULTURA:
                Main.player.darUsosDaAgricultura(
                        Config.getInt("perea.agricultura.usosPorCompra", 1));
                return true;

            default:
                return false;
        }
    }

    private void recusar(String motivo) {
        Som.tocar(Som.SEM_GRANA);
        dizer(motivo);
    }

    private void dizer(String texto) {
        fala = texto;
        ticksDaFala = 200;
    }

    private void fechar() {
        Main.gameState = "Game";
    }

    /* =========================
            O ESTOQUE
       ========================= */

    private int precoDe(Produto p) {

        switch (p) {

            case NIVEL:
                // Sobe a cada nivel ja comprado: o primeiro upgrade e
                // acessivel, o segundo e uma decisao de verdade contra os
                // outros itens.
                int comprados = Math.max(0, Main.player.getLevel() - Main.player.getTetoPorItem());
                return Config.getInt("perea.preco.nivel", 14)
                     + comprados * Config.getInt("perea.preco.nivelAumento", 8);

            case CULTURA_MAKER:
                return Config.getInt("perea.preco.culturaMaker", 20);

            case OLHO_LASER:
                return Config.getInt("perea.preco.olhoLaser", 12);

            case AGRICULTURA:
                return Config.getInt("perea.preco.agricultura", 9);

            default:
                return 0;
        }
    }

    /** true quando nao faz mais sentido vender aquilo. */
    private boolean esgotado(Produto p) {

        if (p == Produto.NIVEL) {
            return Main.player.isNivelMaximo();
        }

        if (p == Produto.CULTURA_MAKER) {
            return Main.player.temCulturaMaker();
        }

        return false;
    }

    private String nomeDe(Produto p) {

        switch (p) {
            case NIVEL:         return "SUBIR DE NÍVEL";
            case CULTURA_MAKER: return "CULTURA MAKER";
            case OLHO_LASER:    return "OLHO LASER DO PEREA";
            case AGRICULTURA:   return "AGRICULTURA DIGITAL";
            default:            return "IR EMBORA";
        }
    }

    private String descricaoDe(Produto p) {

        switch (p) {

            case NIVEL:
                return "nível " + (Main.player.getLevel() + 1)
                     + " — item só te leva até o " + Main.player.getTetoPorItem();

            case CULTURA_MAKER:
                return "passiva: inimigo vale "
                     + Config.getDouble("perea.culturaMaker.fator", 2.0) + "x ponto";

            case OLHO_LASER:
                return "[1] raio que trava no alvo · você tem "
                     + Main.player.getUsosDoOlhoLaser();

            case AGRICULTURA:
                return "[2] drones que deixam o chefe lento · você tem "
                     + Main.player.getUsosDaAgricultura();

            default:
                return "seguir pro LEPEC";
        }
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        // Veu bem escuro: aqui, ao contrario do menu de continue, NAO
        // interessa ver o campo atras. E uma pausa na historia, e a tela
        // tem que dizer isso.
        g.setColor(new Color(6, 8, 14, 232));
        g.fillRect(0, 0, Main.WIDTH, Main.HEIGHT);

        int cx = Main.CAMPO_X + Main.CAMPO_W / 2;
        int topo = Main.CAMPO_Y + 40;

        desenharPerea(g, cx, topo);
        desenharCabecalho(g, cx, topo);
        desenharEstoque(g, topo + 250);
        desenharRodape(g);
    }

    /** A foto do Perea, com uma moldura de barraquinha. */
    private void desenharPerea(Graphics2D g, int cx, int topo) {

        int lado = Config.getInt("perea.tamanhoDoRetrato", 150);

        int x0 = cx - lado / 2;
        int y0 = topo + 26;

        BufferedImage img = Assets.get(Config.getString("perea.sprite", "sprites/npc/perea.png"));

        if (img != null) {
            g.drawImage(img, x0, y0, lado, lado, null);
        } else {
            g.setColor(new Color(60, 60, 80));
            g.fillRect(x0, y0, lado, lado);
        }

        // Moldura pulsando de leve, cor de toldo de barraca.
        int brilho = 190 + (int) (50 * Math.sin(t * 0.06));

        g.setColor(new Color(255, Math.min(255, brilho), 90));
        g.drawRect(x0 - 2, y0 - 2, lado + 4, lado + 4);
        g.drawRect(x0 - 4, y0 - 4, lado + 8, lado + 8);
    }

    private void desenharCabecalho(Graphics2D g, int cx, int topo) {

        g.setFont(new Font("Monospaced", Font.BOLD, 26));
        centralizado(g, "LOJINHA DO PEREA", cx, topo, new Color(255, 215, 120));

        // A fala dele, embaixo do retrato.
        if (ticksDaFala > 0 && !fala.isEmpty()) {

            g.setFont(new Font("Monospaced", Font.PLAIN, 14));

            int alpha = Math.min(255, ticksDaFala * 4);
            centralizado(g, "\"" + fala + "\"", cx, topo + 205,
                         new Color(210, 230, 255, alpha));
        }
    }

    private void desenharEstoque(Graphics2D g, int y0) {

        int x0 = Main.CAMPO_X + 40;
        int larg = Main.CAMPO_W - 80;

        for (int i = 0; i < estoque.length; i++) {

            Produto p = estoque[i];

            boolean ativo = (i == selecionado);
            boolean fim = esgotado(p);
            boolean caro = !fim && p != Produto.SAIR
                         && Main.player.getMoedas() < precoDe(p);

            int y = y0 + i * 54;

            if (ativo) {
                g.setColor(new Color(70, 60, 20, 200));
                g.fillRect(x0 - 8, y - 22, larg + 16, 46);
            }

            // Tres estados de cor, e cada um diz uma coisa diferente:
            // esgotado (nao existe mais), caro (existe mas nao da) e
            // disponivel. Sem essa separacao o jogador fica apertando
            // ENTER num item sem entender por que nada acontece.
            Color cor;

            if (fim) {
                cor = new Color(90, 90, 100);
            } else if (caro) {
                cor = new Color(150, 100, 100);
            } else if (ativo) {
                cor = new Color(255, 235, 150);
            } else {
                cor = new Color(200, 200, 215);
            }

            g.setFont(new Font("Monospaced", Font.BOLD, 17));
            g.setColor(cor);
            g.drawString((ativo ? "> " : "  ") + nomeDe(p), x0, y);

            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.setColor(fim ? new Color(80, 80, 90) : new Color(160, 165, 185));
            g.drawString("   " + descricaoDe(p), x0, y + 16);

            if (p == Produto.SAIR) {
                continue;
            }

            String etiqueta = fim ? "esgotado" : (precoDe(p) + " ★");

            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            g.setColor(fim ? new Color(90, 90, 100)
                           : (caro ? new Color(190, 110, 110) : new Color(255, 210, 120)));

            int lt = g.getFontMetrics().stringWidth(etiqueta);
            g.drawString(etiqueta, x0 + larg - lt, y);
        }
    }

    private void desenharRodape(Graphics2D g) {

        int cx = Main.CAMPO_X + Main.CAMPO_W / 2;
        int y = Main.CAMPO_Y + Main.CAMPO_H - 54;

        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        centralizado(g, "carteira: " + Main.player.getMoedas() + " ★",
                     cx, y, new Color(255, 220, 140));

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        centralizado(g, "↑↓ escolher · Z/ENTER comprar · ESC sair",
                     cx, y + 24, new Color(150, 150, 170));
    }

    private void centralizado(Graphics2D g, String texto, int cx, int y, Color cor) {

        int larg = g.getFontMetrics().stringWidth(texto);

        g.setColor(new Color(0, 0, 0, Math.min(255, cor.getAlpha())));
        g.drawString(texto, cx - larg / 2 + 1, y + 1);

        g.setColor(cor);
        g.drawString(texto, cx - larg / 2, y);
    }
}
