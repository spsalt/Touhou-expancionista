package src;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

/**
 * Painel lateral de status (direita da tela) e moldura do campo de jogo.
 *
 * So desenha: nao tem tick, nao muda estado nenhum. Le tudo do Main.player.
 *
 * As posicoes verticais de cada secao sao CONSTANTES (Y_*) em vez de uma
 * variavel "linha" acumulada dentro de render(). E o que deixa possivel
 * expor getBotaoGptExpansaoBounds() pro Main saber onde fica o botao pra
 * testar clique do mouse, sem duplicar a conta em dois lugares.
 */
public class Hud {

    private static final Font FONTE_TITULO = new Font("Monospaced", Font.BOLD, 16);
    private static final Font FONTE_TEXTO  = new Font("Monospaced", Font.PLAIN, 14);

    /* --- layout vertical, em pixels a partir de Main.CAMPO_Y --- */

    private static final int Y_TITULO        = 40;
    private static final int Y_SUBTITULO      = 60;
    private static final int Y_PONTOS_LABEL   = 104;
    private static final int Y_PONTOS_VALOR   = 122;
    private static final int Y_GRAZE          = 140;
    private static final int Y_VIDAS_LABEL    = 158;
    private static final int Y_VIDAS_ICONES   = 176;
    private static final int Y_GPT_LABEL      = 206;
    private static final int Y_GPT_BOTAO      = 218;
    private static final int Y_NIVEL_LABEL    = 270;
    private static final int Y_BARRA_XP       = 280;
    private static final int Y_AUTOFIRE       = 356;
    private static final int Y_CARTEIRA       = 386;

    private static final int TAMANHO_BOTAO_GPT = 34;

    /** Onde comeca o painel lateral (logo depois do campo, com um respiro). */
    private final int painelX;

    public Hud() {
        this.painelX = Main.CAMPO_X + Main.CAMPO_W + 24;
    }

    public void render(Graphics2D g) {

        desenharMolduraDoCampo(g);

        if (Main.player == null) {
            return;
        }

        g.setFont(FONTE_TITULO);
        g.setColor(new Color(255, 220, 120));
        g.drawString("TOUHOU 67", painelX, Main.CAMPO_Y + Y_TITULO);

        g.setFont(FONTE_TEXTO);
        g.setColor(new Color(180, 180, 180));
        g.drawString("Expancionista", painelX, Main.CAMPO_Y + Y_SUBTITULO);

        g.setColor(Color.WHITE);
        g.drawString("Pontos", painelX, Main.CAMPO_Y + Y_PONTOS_LABEL);

        g.setColor(new Color(255, 230, 150));
        g.drawString(String.format("%09d", Main.player.getPontuacao()),
                     painelX, Main.CAMPO_Y + Y_PONTOS_VALOR);

        // Graze ao lado da pontuacao: as duas contam a mesma coisa (o
        // quao bem voce jogou), e ficam juntas de proposito.
        g.setColor(new Color(150, 210, 255));
        g.drawString("Graze " + Main.player.getGraze(), painelX, Main.CAMPO_Y + Y_GRAZE);

        g.setColor(Color.WHITE);
        g.drawString("Vidas", painelX, Main.CAMPO_Y + Y_VIDAS_LABEL);
        desenharIcones(g, painelX, Main.CAMPO_Y + Y_VIDAS_ICONES, Main.player.getVidas(),
                       new Color(255, 90, 90));

        desenharSecaoGptExpansao(g);

        g.setFont(FONTE_TEXTO);
        g.setColor(Color.WHITE);
        g.drawString("Nivel " + Main.player.getLevel()
                   + (Main.player.isNivelMaximo() ? " MAX" : ""),
                     painelX, Main.CAMPO_Y + Y_NIVEL_LABEL);

        desenharBarraDeXp(g, painelX, Main.CAMPO_Y + Y_BARRA_XP);
        desenharTiposDeTiro(g);
        desenharCarteiraEItens(g);

        // Estado do tiro automatico: verde ligado, cinza desligado.
        boolean auto = Main.player.isAutofire();

        g.setColor(auto ? new Color(140, 255, 140) : new Color(110, 110, 110));
        g.drawString("AUTO " + (auto ? "ON " : "OFF") + "  [C]", painelX, Main.CAMPO_Y + Y_AUTOFIRE);

        desenharControles(g);

        // Por ULTIMO e fora de qualquer recorte: e o aviso mais urgente da
        // tela e nao pode ficar por baixo de nada.
        desenharAvisoDeDeathbomb(g);
    }

    /**
     * O "V" GIGANTE no canto inferior direito enquanto a morte esta marcada.
     *
     * O deathbomb ja existia: quando voce e atingido, ainda ha alguns
     * frames em que apertar V cancela a morte. O problema e que a janela
     * dura 12 ticks — um quinto de segundo — e o unico aviso ficava DENTRO
     * do campo, em cima do jogador, no meio de uma parede de bala. Ou
     * seja, exatamente onde o olho ja esta sobrecarregado e no instante em
     * que ele mais se perde.
     *
     * Aqui o aviso fica FORA do campo, num canto que nunca tem nada, e
     * gigante. Nao e uma ajuda de dificuldade: a janela continua com o
     * mesmo tamanho e a decisao continua sendo do jogador. O que muda e
     * ele ficar sabendo que a decisao existe.
     *
     * O V PULSA e ENCOLHE junto com o tempo restante. Um V estatico diria
     * "aperte V"; encolhendo, ele diz "aperte V AGORA", que e outra coisa.
     */
    private void desenharAvisoDeDeathbomb(Graphics2D g) {

        int restam = Main.player.getMorteEm();

        if (restam <= 0) {
            return;
        }

        // Sem armadura ou sem bomba nao ha saida — anunciar uma tecla que
        // nao faz nada seria pior que nao anunciar nada.
        if (!Main.player.temArmadura() || Main.player.getBombas() <= 0) {
            return;
        }

        double f = restam / (double) Math.max(1, Main.player.getTicksDeathbomb());

        int tamanho = (int) (Config.getInt("hud.tamanhoDoAvisoV", 150) * (0.75 + 0.25 * f));

        int cx = Main.WIDTH - Config.getInt("hud.margemDoAvisoV", 90);
        int cy = Main.HEIGHT - Config.getInt("hud.margemDoAvisoV", 90);

        // Halo pulsando por tras: no canto escuro do painel, o contorno
        // sozinho nao chamaria atencao periferica.
        for (int i = 3; i >= 1; i--) {

            int raio = (int) (tamanho * 0.42 * i * (0.8 + 0.2 * f));
            int alpha = (int) (70 * f / i);

            g.setColor(new Color(255, 230, 120, Math.max(0, Math.min(255, alpha))));
            g.fillOval(cx - raio, cy - raio, raio * 2, raio * 2);
        }

        g.setFont(new Font("Monospaced", Font.BOLD, tamanho));

        String texto = "V";
        FontMetrics fm = g.getFontMetrics();

        int tx = cx - fm.stringWidth(texto) / 2;
        int ty = cy + fm.getAscent() / 3;

        // Contorno preto grosso desenhando o texto deslocado em 8 direcoes:
        // o V aparece por cima de qualquer coisa que esteja no painel.
        g.setColor(new Color(0, 0, 0, 220));

        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dy = -3; dy <= 3; dy += 3) {
                if (dx != 0 || dy != 0) {
                    g.drawString(texto, tx + dx, ty + dy);
                }
            }
        }

        g.setColor(new Color(255, 235, 130));
        g.drawString(texto, tx, ty);

        // A palavra embaixo, pequena: o V sozinho diz a tecla, nao diz o
        // que ela faz. Quem ja sabe nem le; quem nao sabe, le uma vez.
        g.setFont(new Font("Monospaced", Font.BOLD, 15));

        String rotulo = "BOMBA!";
        int lr = g.getFontMetrics().stringWidth(rotulo);

        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(rotulo, cx - lr / 2 + 1, cy + tamanho / 2 + 9);

        g.setColor(new Color(255, 200, 120));
        g.drawString(rotulo, cx - lr / 2, cy + tamanho / 2 + 8);
    }

    /**
     * A linha do Point of Collection: uma marca tracejada mostrando ate
     * onde subir pra puxar todos os itens.
     *
     * Uma mecanica que o jogador nao consegue VER nao existe. Ela some
     * quando ele ja esta acima dela — nesse ponto ja cumpriu o papel, e
     * uma linha atravessando a tela no meio de um padrao de bala so
     * atrapalharia a leitura.
     */
    private void desenharLinhaDeColeta(Graphics2D g) {

        if (Main.player == null || !Main.gameState.equals("Game")) {
            return;
        }

        int y = (int) Main.player.getLinhaDeColetaTotal();

        if (Main.player.getY() <= y) {
            return;
        }

        g.setColor(new Color(120, 200, 255, 55));

        for (int x = Main.CAMPO_X; x < Main.CAMPO_X + Main.CAMPO_W; x += 16) {
            g.drawLine(x, y, x + 8, y);
        }
    }

    /**
     * A carteira de pesos cubanos e os itens comprados do Perea.
     *
     * Os dois itens ativaveis so aparecem DEPOIS de comprados. Mostrar
     * "[1] OLHO LASER 0" desde o comeco do jogo anunciaria uma mecanica
     * que o jogador ainda nao tem como usar, e a lojinha so existe la na
     * metade — o HUD ia estar mentindo por metade da partida.
     */
    private void desenharCarteiraEItens(Graphics2D g) {

        int y = Main.CAMPO_Y + Y_CARTEIRA;

        g.setFont(FONTE_TEXTO);
        g.setColor(new Color(255, 210, 120));
        g.drawString("★ " + Main.player.getMoedas() + " pesos", painelX, y);

        y += 20;

        if (Main.player.temCulturaMaker()) {
            g.setColor(new Color(150, 255, 180));
            g.drawString("CULTURA MAKER", painelX, y);
            y += 18;
        }

        if (Main.player.getUsosDoOlhoLaser() > 0) {
            g.setColor(new Color(255, 130, 130));
            g.drawString("[1] OLHO LASER  " + Main.player.getUsosDoOlhoLaser(), painelX, y);
            y += 18;
        }

        if (Main.player.getUsosDaAgricultura() > 0) {
            g.setColor(new Color(150, 230, 110));
            g.drawString("[2] AGRICULTURA " + Main.player.getUsosDaAgricultura(), painelX, y);
        }
    }

    /**
     * Rotulo "GPT Expansion" + botao com a logo, clicavel, mostrando
     * quantas cargas restam. Fica escuro/acinzentado quando nao ha carga.
     */
    private void desenharSecaoGptExpansao(Graphics2D g) {

        g.setFont(FONTE_TEXTO);
        g.setColor(Color.WHITE);
        g.drawString("GPT Expansion", painelX, Main.CAMPO_Y + Y_GPT_LABEL);

        Rectangle botao = getBotaoGptExpansaoBounds();

        // Antes da armadura (fim do estagio 1) o poder nem existe. O
        // botao continua desenhado, apagado, pra o jogador saber que ele
        // vai aparecer — sumir e reaparecer confundiria mais.
        boolean temArmadura = Main.player.temArmadura();
        boolean disponivel = temArmadura && Main.player.getBombas() > 0;

        Color corBorda = disponivel ? new Color(255, 220, 120) : new Color(80, 80, 90);
        Color corFundo = disponivel ? new Color(70, 55, 15) : new Color(30, 30, 36);

        g.setColor(corFundo);
        g.fillRoundRect(botao.x, botao.y, botao.width, botao.height, 10, 10);

        g.setColor(corBorda);
        g.drawRoundRect(botao.x, botao.y, botao.width, botao.height, 10, 10);

        BufferedImage logo = Assets.get("sprites/GFX/gpt_logo.png");
        int margem = 5;

        if (logo != null) {

            // Sem carga: desenha em cinza (mistura pro cinza) em vez de
            // sumir, pra continuar dando pra ver ONDE clicar quando recarregar.
            g.drawImage(logo, botao.x + margem, botao.y + margem,
                       botao.width - margem * 2, botao.height - margem * 2, null);

            if (!disponivel) {
                g.setColor(new Color(20, 20, 26, 170));
                g.fillRoundRect(botao.x, botao.y, botao.width, botao.height, 10, 10);
            }
        }

        // Contador de cargas, ao lado do botao.
        g.setColor(disponivel ? new Color(255, 230, 150) : new Color(120, 120, 120));

        String rotulo = temArmadura ? ("x" + Main.player.getBombas()) : "TRAVADO";

        g.drawString(rotulo, botao.x + botao.width + 10, botao.y + botao.height - 10);
    }

    /**
     * Onde o botao da GPT Expansion fica na tela, em coordenadas do painel
     * (as mesmas usadas pelo mouse do Swing). Publico pra o Main testar
     * clique sem duplicar a conta de posicao.
     */
    public Rectangle getBotaoGptExpansaoBounds() {
        return new Rectangle(painelX, Main.CAMPO_Y + Y_GPT_BOTAO, TAMANHO_BOTAO_GPT, TAMANHO_BOTAO_GPT);
    }

    private void desenharControles(Graphics2D g) {

        int rodape = Main.CAMPO_Y + Main.CAMPO_H - 108;

        g.setColor(new Color(140, 140, 140));
        g.drawString("Z  atirar",       painelX, rodape);
        g.drawString("X/Shift foco",    painelX, rodape + 18);
        g.drawString("C  autofire",     painelX, rodape + 36);
        g.drawString("V / clique GPT",  painelX, rodape + 54);
        g.drawString("ESC pausar",      painelX, rodape + 72);
        g.drawString("F5 recarregar",   painelX, rodape + 90);

        g.setColor(new Color(120, 120, 130));
        g.drawString("F3 debug",      painelX, rodape + 108);

        if (Main.debugMode) {
            g.setColor(new Color(180, 160, 100));
            g.drawString("F2 pular fase", painelX, rodape + 126);
        }
    }

    /**
     * Etiquetas dos tipos de tiro ja desbloqueados, com a cor de cada um.
     * Sem isso o jogador sobe de nivel, ve balas novas na tela e nao faz
     * ideia do que ganhou.
     */
    private void desenharTiposDeTiro(Graphics2D g) {

        int y = Main.CAMPO_Y + Y_BARRA_XP + 22;
        int nivel = Main.player.getLevel();

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));

        g.setColor(new Color(120, 220, 255));
        g.drawString("- leque", painelX, y);

        if (nivel >= Config.getInt("tiro.ponteiro.nivel", 2)) {
            y += 14;
            g.setColor(new Color(150, 255, 170));
            g.drawString("- ponteiro", painelX, y);
        }

        if (nivel >= Config.getInt("tiro.ricochete.nivel", 4)) {
            y += 14;
            g.setColor(new Color(255, 180, 90));
            g.drawString("- ricochete", painelX, y);
        }
    }

    /** Contorno da arena, pra deixar claro onde as balas valem. */
    private void desenharMolduraDoCampo(Graphics2D g) {

        desenharLinhaDeColeta(g);

        g.setColor(new Color(70, 70, 110));
        g.drawRect(Main.CAMPO_X - 1, Main.CAMPO_Y - 1, Main.CAMPO_W + 1, Main.CAMPO_H + 1);

        g.setColor(new Color(12, 12, 26));
        g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
    }

    /** Desenha N quadradinhos lado a lado (vidas). */
    private void desenharIcones(Graphics2D g, int x, int y, int quantidade, Color cor) {

        g.setColor(cor);

        for (int i = 0; i < quantidade; i++) {
            g.fillRect(x + i * 18, y - 10, 12, 12);
        }
    }

    private void desenharBarraDeXp(Graphics2D g, int x, int y) {

        int largura = 140;
        int altura = 8;

        // No teto DOS ITENS a barra fica cheia, em vez de travada em zero
        // pra sempre — e la que o XP para de virar nivel e passa a virar
        // pontuacao. Sem isto o jogador via uma barra parada e concluia
        // que estava com bug.
        boolean noTeto = Main.player.isNoTetoDosItens();

        double progresso = noTeto
                         ? 1.0
                         : Main.player.getXp() / (double) Main.player.getXpParaProximoNivel();

        progresso = Math.max(0, Math.min(1, progresso));

        g.setColor(new Color(40, 40, 60));
        g.fillRect(x, y, largura, altura);

        g.setColor(noTeto ? new Color(255, 220, 120) : new Color(150, 255, 150));
        g.fillRect(x, y, (int) (largura * progresso), altura);

        g.setColor(new Color(90, 90, 110));
        g.drawRect(x, y, largura, altura);
    }
}
