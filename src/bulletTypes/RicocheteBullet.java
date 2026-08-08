package src.bulletTypes;

import java.awt.Color;
import java.awt.Graphics2D;

import src.Main;
import src.Som;

/**
 * RICOCHETE — a bala do jogador que quica nas paredes do campo.
 *
 * Sai em diagonal e rebate nas laterais em vez de sair pela borda, entao
 * ela varre a tela em ziguezague. Serve pra pegar inimigo encostado na
 * lateral, que o leque frontal nunca alcanca.
 *
 * O quique gasta uma "carga": depois de N rebatidas ela some. Sem esse
 * limite, uma bala em diagonal perfeita ficaria presa entre as duas
 * paredes pra sempre, e a tela iria acumulando ricochetes eternos.
 *
 * REBATER E SO INVERTER O SINAL do componente que bateu — a componente
 * paralela a parede nao muda. Por isso o angulo de saida e igual ao de
 * entrada, que e o que o olho espera de um quique.
 */
public class RicocheteBullet extends Bullet {

    private double dx, dy;

    /** Quantos quiques ainda restam antes dela sumir. */
    private int quiquesRestantes;

    private final Color cor;

    public RicocheteBullet(double x, double y, double dx, double dy,
                           double raio, double dano, int quiques, Color cor) {

        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.radius = raio;
        this.dano = dano;
        this.quiquesRestantes = Math.max(0, quiques);
        this.cor = cor;
        this.hitPlayer = false;   // bala do jogador

        this.sprite = src.Config.getString("tiro.ricochete.sprite",
                                           "sprites/GFX/bala_ricochete.png");
    }

    @Override
    public void tick() {

        x += dx;
        y += dy;

        quicarNasLaterais();

        // Some ao sair por cima ou por baixo — so as LATERAIS rebatem.
        // Deixar o topo rebater faria a bala voltar pro jogador, o que
        // nao faz sentido nenhum pra uma bala dele.
        if (y < Main.CAMPO_Y - Main.MARGEM_SAIDA_BALA
         || y > Main.CAMPO_Y + Main.CAMPO_H + Main.MARGEM_SAIDA_BALA) {
            isAlive = false;
        }
    }

    private void quicarNasLaterais() {

        double esquerda = Main.CAMPO_X + radius;
        double direita  = Main.CAMPO_X + Main.CAMPO_W - radius;

        boolean bateu = false;

        if (x < esquerda) {
            x = esquerda;
            dx = Math.abs(dx);      // passa a ir pra direita
            bateu = true;

        } else if (x > direita) {
            x = direita;
            dx = -Math.abs(dx);     // passa a ir pra esquerda
            bateu = true;
        }

        if (!bateu) {
            return;
        }

        if (quiquesRestantes <= 0) {
            isAlive = false;
            return;
        }

        quiquesRestantes--;
        Som.tocar(Som.QUIQUE);
    }

    @Override
    public void render(Graphics2D g) {

        if (desenharSprite(g, Math.atan2(dy, dx),
                   src.Config.getDouble("tiro.ricochete.escalaSprite", 1.7))) {
            return;
        }

        // Sem o PNG: losango simples.
        int r = (int) radius;

        int[] px = { (int) x, (int) (x + r), (int) x, (int) (x - r) };
        int[] py = { (int) (y - r), (int) y, (int) (y + r), (int) y };

        g.setColor(cor);
        g.fillPolygon(px, py, 4);

        g.setColor(Color.WHITE);
        g.fillOval((int) (x - r * 0.35), (int) (y - r * 0.35), (int) (r * 0.7), (int) (r * 0.7));
    }

    /* =========================
            GETTERS
       ========================= */

    public int getQuiquesRestantes() {
        return quiquesRestantes;
    }
}
