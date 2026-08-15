package src;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import src.enemyTypes.BossEnemy;
import src.enemyTypes.Enemy;

/**
 * OLHO LASER DO PEREA — o item mais caro da lojinha.
 *
 * Um raio vermelho sai do estudante PRA FRENTE (pra cima, que e onde ele
 * atira). A ponta avanca ate encostar numa hitbox; ali ela TRAVA e o raio
 * passa a morder o alvo, soltando faisca e a palavra BRUTAL, ate a carga
 * acabar.
 *
 * A COR, QUE E O PONTO
 * --------------------
 * O miolo e vermelho ESCURO e as bordas sao claras — o contrario do laser
 * de desenho animado, e de proposito: e a leitura da Dark Fountain do
 * Deltarune, onde o centro e um vazio e a luz mora na casca. Na pratica
 * sao varias faixas empilhadas, da mais larga e mais clara ate a mais
 * estreita e mais escura, todas pulsando juntas.
 *
 * Isso tambem resolve um problema real de bullet hell: um feixe branco
 * grosso apaga tudo que estiver atras dele. Com o miolo escuro, da pra
 * ver as balas do chefe atraves do proprio raio.
 *
 * POR QUE ELE SEGUE O JOGADOR
 * ---------------------------
 * A origem e lida do Player todo tick, entao andar de lado VARRE o raio
 * pela tela. Um feixe fixo, disparado e esquecido, seria so um numero de
 * dano; seguindo, ele vira uma coisa que se MIRA — e o jogador escolhe
 * entre se posicionar bem pro dano ou se posicionar bem pra sobreviver.
 *
 * Ele nao mata inimigo comum de graca por acidente: da dano, e dano forte,
 * mas passando pelo levarDano() de sempre.
 */
public class OlhoLaser {

    /** Uma palavra "BRUTAL" saindo do ponto de impacto. */
    private static class Brutal {

        double x, y;
        double dx, dy;
        int vida;
        final int vidaMaxima;
        final double escala;

        Brutal(double x, double y, double dx, double dy, int vida, double escala) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.vida = vida;
            this.vidaMaxima = vida;
            this.escala = escala;
        }
    }

    private static final Random RNG = new Random();

    /** De quem o raio sai. Lido todo tick — o raio acompanha o jogador. */
    private final Player dono;

    /** Ate onde a ponta chegou. Cresce ate bater em alguma coisa. */
    private double alcance = 0;

    /** O que a ponta esta mordendo agora, ou null se esta viajando. */
    private Enemy alvo = null;

    private int t = 0;
    private boolean isAlive = true;

    private final List<Brutal> brutais = new ArrayList<>();

    /* --- ajustes --- */

    private final int duracao;
    private final double velocidadeDaPonta;
    private final double largura;
    private final double danoPorTick;
    private final int intervaloDoBrutal;
    private final int camadas;

    public OlhoLaser(Player dono) {

        this.dono = dono;

        this.duracao           = Math.max(10, Config.getInt("perea.olhoLaser.duracao", 150));
        this.velocidadeDaPonta = Config.getDouble("perea.olhoLaser.velocidadeDaPonta", 34.0);
        this.largura           = Config.getDouble("perea.olhoLaser.largura", 30.0);
        this.danoPorTick       = Config.getDouble("perea.olhoLaser.danoPorTick", 2.2);
        this.intervaloDoBrutal = Math.max(4, Config.getInt("perea.olhoLaser.intervaloDoBrutal", 14));
        this.camadas           = Math.max(2, Config.getInt("perea.olhoLaser.camadas", 5));

        Som.tocar(Som.OLHO_LASER);
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        if (!isAlive) {
            return;
        }

        avancarAPonta();
        morder();
        atualizarBrutais();

        t++;

        if (t >= duracao) {
            isAlive = false;
        }
    }

    /**
     * Empurra a ponta pra cima ate ela encostar em alguem.
     *
     * A busca e refeita TODO TICK, e nao so uma vez: o jogador anda, o
     * chefe deriva, e o alvo pode morrer no meio do raio. Guardar o alvo
     * do primeiro frame deixaria o feixe grudado num inimigo que ja saiu
     * da frente.
     */
    private void avancarAPonta() {

        double px = dono.getX();
        double py = dono.getY();

        double limite = py - Main.CAMPO_Y;   // ate o topo do campo

        alcance = Math.min(limite, alcance + velocidadeDaPonta);

        alvo = primeiroNaFrente(px, py);

        if (alvo != null) {

            // Para na BORDA do alvo, nao no centro dele: parar no centro
            // faria o raio atravessar meio chefe e a leitura ficaria
            // errada — parece que passou direto.
            double ateABorda = py - (alvo.getY() + alvo.getRadius());

            if (ateABorda < alcance) {
                alcance = Math.max(0, ateABorda);
            }
        }
    }

    /**
     * O inimigo mais proximo cuja hitbox cruza a coluna do raio.
     *
     * Colisao circulo x faixa vertical: basta o centro dele estar a menos
     * de (raio + meia largura) do X do jogador. E o mesmo teste que a
     * pagina do LaTeX do Clayton usa, so que numa dimensao.
     */
    private Enemy primeiroNaFrente(double px, double py) {

        Enemy melhor = null;
        double menorDistancia = Double.MAX_VALUE;

        double meia = largura / 2;

        for (int i = 0; i < Main.enemies.size(); i++) {

            Enemy e = Main.enemies.get(i);

            if (!e.isAlive() || e.getY() > py) {
                continue;
            }

            if (Math.abs(e.getX() - px) > e.getRadius() + meia) {
                continue;
            }

            double d = py - e.getY();

            if (d < menorDistancia) {
                menorDistancia = d;
                melhor = e;
            }
        }

        return melhor;
    }

    /** Dano contInuo em quem a ponta estiver encostando. */
    private void morder() {

        if (alvo == null) {
            return;
        }

        // Chefe em dialogo e invulneravel; levarDano ja trata isso, mas
        // sem esta saida o raio ficaria cuspindo BRUTAL numa chefe que
        // ainda esta entrando em cena.
        if (alvo instanceof BossEnemy && ((BossEnemy) alvo).isEmDialogo()) {
            return;
        }

        alvo.levarDano(danoPorTick);

        if (t % intervaloDoBrutal == 0) {

            Som.tocar(Som.LASER_BATE);

            double bx = dono.getX();
            double by = dono.getY() - alcance;

            // Sai pros lados e pra cima, nunca pra baixo: pra baixo ele
            // desceria por cima do proprio raio e sumiria dentro dele.
            double ang = -Math.PI / 2 + (RNG.nextDouble() - 0.5) * 2.2;
            double vel = 1.6 + RNG.nextDouble() * 2.0;

            brutais.add(new Brutal(bx, by,
                                   Math.cos(ang) * vel,
                                   Math.sin(ang) * vel,
                                   Config.getInt("perea.olhoLaser.ticksDoBrutal", 40),
                                   0.8 + RNG.nextDouble() * 0.7));
        }
    }

    private void atualizarBrutais() {

        for (int i = brutais.size() - 1; i >= 0; i--) {

            Brutal b = brutais.get(i);

            b.x += b.dx;
            b.y += b.dy;

            // Freia enquanto sobe: a palavra "assenta" no ar em vez de
            // sair voando pra fora da tela.
            b.dx *= 0.94;
            b.dy *= 0.94;

            b.vida--;

            if (b.vida <= 0) {
                brutais.remove(i);
            }
        }
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        if (!isAlive || alcance <= 1) {
            return;
        }

        double px = dono.getX();
        double py = dono.getY();

        desenharFeixe(g, px, py);
        desenharImpacto(g, px, py - alcance);
        desenharBrutais(g);
    }

    /**
     * As camadas do feixe, da mais larga (clara) pra mais estreita (escura).
     *
     * O pulso multiplica a largura de TODAS juntas, entao o raio "respira"
     * inteiro em vez de tremer camada por camada. Duas senoides de
     * frequencias diferentes somadas — uma so daria um pulso mecanico,
     * regular demais pra parecer energia.
     */
    private void desenharFeixe(Graphics2D g, double px, double py) {

        double pulso = 1
                     + 0.10 * Math.sin(t * 0.42)
                     + 0.05 * Math.sin(t * 0.93);

        // Nasce fino e abre nos primeiros ticks: um feixe que aparece ja
        // na largura final parece um retangulo colado na tela.
        double abertura = Math.min(1.0, t / 6.0);

        int topo = (int) (py - alcance);
        int alt = (int) alcance;

        for (int i = 0; i < camadas; i++) {

            // 0 na camada de fora, 1 no miolo.
            double f = i / (double) (camadas - 1);

            double larg = largura * pulso * abertura * (1 - f * 0.82);

            // Rampa do claro (borda) pro escuro (miolo). O alfa sobe junto:
            // a casca e translucida e o nucleo e solido.
            int r = (int) (255 - 135 * f);
            int gg = (int) (170 - 165 * f);
            int b = (int) (160 - 140 * f);
            int a = (int) (110 + 130 * f);

            g.setColor(new Color(clamp(r), clamp(gg), clamp(b), clamp(a)));
            g.fillRect((int) (px - larg / 2), topo, Math.max(1, (int) larg), alt);
        }

        // Linha branca no eixo: da a ponta de brilho que faz o resto ler
        // como luz e nao como uma faixa pintada.
        Stroke anterior = g.getStroke();

        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 230, 225, 150));
        g.drawLine((int) px, topo, (int) px, topo + alt);

        g.setStroke(anterior);
    }

    /** O estouro na ponta: so aparece quando o raio esta mordendo alguem. */
    private void desenharImpacto(Graphics2D g, double x, double y) {

        if (alvo == null) {
            return;
        }

        double pulso = 1 + 0.25 * Math.sin(t * 0.5);

        for (int i = 3; i >= 1; i--) {

            double raio = largura * 0.55 * i * pulso;
            int a = 40 + 45 * i;

            g.setColor(new Color(255, 90 + 40 * i, 70 + 30 * i, Math.min(200, a)));
            g.fillOval((int) (x - raio), (int) (y - raio), (int) (raio * 2), (int) (raio * 2));
        }

        // Lascas saindo do ponto de impacto.
        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(new Color(255, 220, 200, 190));

        for (int i = 0; i < 6; i++) {

            double ang = t * 0.22 + i * Math.PI / 3;
            double r1 = largura * 0.5;
            double r2 = r1 + 14 + 8 * Math.sin(t * 0.4 + i);

            g.drawLine((int) (x + Math.cos(ang) * r1), (int) (y + Math.sin(ang) * r1),
                       (int) (x + Math.cos(ang) * r2), (int) (y + Math.sin(ang) * r2));
        }

        g.setStroke(anterior);
    }

    private void desenharBrutais(Graphics2D g) {

        for (int i = 0; i < brutais.size(); i++) {

            Brutal b = brutais.get(i);

            int alpha = (int) (255 * Math.min(1, b.vida / (double) b.vidaMaxima * 1.8));

            g.setFont(new Font("Monospaced", Font.BOLD, (int) (18 * b.escala)));

            String texto = "BRUTAL";
            int larg = g.getFontMetrics().stringWidth(texto);

            g.setColor(new Color(60, 0, 10, Math.min(255, alpha)));
            g.drawString(texto, (int) b.x - larg / 2 + 2, (int) b.y + 2);

            g.setColor(new Color(255, 210, 90, Math.min(255, alpha)));
            g.drawString(texto, (int) b.x - larg / 2, (int) b.y);
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /* =========================
            GETTERS
       ========================= */

    public boolean isAlive() {
        return isAlive;
    }

    public double getAlcance() {
        return alcance;
    }

    /** O que o raio esta mordendo agora, ou null. */
    public Enemy getAlvo() {
        return alvo;
    }
}
