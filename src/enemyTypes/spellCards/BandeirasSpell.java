package src.enemyTypes.spellCards;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Random;

import src.Bandeira;
import src.Config;
import src.Main;
import src.bulletTypes.BandeiraBullet;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD - "⚑ Tratado de Não-Proliferação"
 *
 * O PAPA convoca bandeiras de paises sorteados. Cada uma nasce num ponto
 * do campo, gira mirando o jogador e, passados alguns ticks, avanca em
 * linha reta na direcao em que ele estava no ultimo tick da mira.
 *
 * O tema vem do roteiro: o PAPA quer "proliferar o virus" pelo mundo
 * (linha 66). Cada bandeira e um pais na lista dele.
 *
 * COMO ISSO VIRA UM PADRAO E NAO SO RUIDO
 * ---------------------------------------
 * As bandeiras nascem em LEVAS, nao uma a uma. Uma leva inteira mira ao
 * mesmo tempo e sai ao mesmo tempo, entao o jogador enxerga um leque de
 * linhas de mira convergindo nele e tem uma decisao clara: por qual lado
 * escapar. Se elas nascessem espalhadas no tempo, cada uma exigiria uma
 * reacao separada e o ataque viraria um chuvisco sem leitura.
 *
 * As posicoes de nascimento sao sorteadas em ARCO em volta da chefe, o
 * que garante que a leva sempre venha de cima e nunca nasca em cima do
 * jogador (nascer colado nele seria dano sem defesa).
 *
 * O sorteio dos paises usa a semente de 'papa.bandeiras.seed' quando ela
 * e >= 0 — util pra testar um ajuste sempre com a mesma sequencia.
 */
public class BandeirasSpell extends SpellCard {

    /** Quantas bandeiras por leva. */
    private final int porLeva;

    /** Ticks entre uma leva e a proxima. */
    private final int intervaloEntreLevas;

    private final int ticksDeMira;
    private final int ticksTravada;
    private final double taxaDeGiro;
    private final double velocidade;
    private final double raio;

    /** Raio do arco em volta da chefe onde as bandeiras nascem. */
    private final double raioDeSpawn;

    /** Abertura do arco de nascimento, em radianos. */
    private final double aberturaDoArco;

    private Random rng;

    /** Nome do ultimo pais sorteado, so pra mostrar na tela. */
    private String ultimoPais = "";

    /** Ticks restantes mostrando o nome do pais. */
    private int mostrarNome = 0;

    public BandeirasSpell() {

        super("⚑  Tratado de Não-Proliferação",
              Config.getDouble("papa.bandeiras.hp", 420),
              Config.getInt("papa.bandeiras.duracao", 2000));

        this.porLeva             = Math.max(1, Config.getInt("papa.bandeiras.porLeva", 5));
        this.intervaloEntreLevas = Math.max(10, Config.getInt("papa.bandeiras.intervaloEntreLevas", 110));

        this.ticksDeMira  = Config.getInt("papa.bandeiras.ticksDeMira", 70);
        this.ticksTravada = Config.getInt("papa.bandeiras.ticksTravada", 22);
        this.taxaDeGiro   = Config.getDouble("papa.bandeiras.taxaDeGiro", 0.045);
        this.velocidade   = Config.getDouble("papa.bandeiras.velocidade", 5.2);
        this.raio         = Config.getDouble("papa.bandeiras.raio", 11.0);

        this.raioDeSpawn    = Config.getDouble("papa.bandeiras.raioDeSpawn", 170);
        this.aberturaDoArco = Config.getDouble("papa.bandeiras.aberturaDoArco", 2.4);
    }

    @Override
    public void iniciar(BossEnemy chefe) {

        long seed = Config.getInt("papa.bandeiras.seed", -1);
        rng = (seed < 0) ? new Random() : new Random(seed);

        ultimoPais = "";
        mostrarNome = 0;
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        if (mostrarNome > 0) {
            mostrarNome--;
        }

        if (t % intervaloEntreLevas != 0) {
            return;
        }

        lancarLeva(chefe);
    }

    /**
     * Solta uma leva inteira de uma vez.
     *
     * Cada bandeira nasce num ponto do arco (distribuidos por igual, sem
     * sorteio na POSICAO) e ja aponta pra baixo. O sorteio fica so no
     * PAIS — assim a forma do ataque e previsivel e a variedade e visual,
     * que e o que a gente quer: o jogador aprende o padrao, mas a tela
     * nunca fica igual duas vezes.
     */
    private void lancarLeva(BossEnemy chefe) {

        for (int i = 0; i < porLeva; i++) {

            // -abertura/2 .. +abertura/2 em volta do "pra baixo" (PI/2).
            double f = (porLeva == 1) ? 0.5 : i / (double) (porLeva - 1);
            double anguloNoArco = Math.PI / 2 - aberturaDoArco / 2 + aberturaDoArco * f;

            double px = chefe.getX() + Math.cos(anguloNoArco) * raioDeSpawn;
            double py = chefe.getY() + Math.sin(anguloNoArco) * raioDeSpawn;

            // Prende dentro do campo: com a chefe derivando pras laterais,
            // parte do arco cairia fora e a bandeira nasceria invisivel.
            px = Math.max(Main.CAMPO_X + raio, Math.min(Main.CAMPO_X + Main.CAMPO_W - raio, px));
            py = Math.max(Main.CAMPO_Y + raio, py);

            Bandeira pais = Bandeira.sortear(rng);

            ultimoPais = pais.getNome();
            mostrarNome = Config.getInt("papa.bandeiras.ticksNomeNaTela", 90);

            Main.bullets.add(new BandeiraBullet(
                px, py, pais,
                anguloNoArco,           // ja comeca apontando pra baixo
                ticksDeMira, ticksTravada,
                taxaDeGiro, velocidade, raio
            ));
        }
    }

    /**
     * Mostra o nome do ultimo pais convocado, no rodape do campo.
     *
     * Existe por dois motivos: e a piada do ataque (o PAPA anunciando
     * onde vai proliferar o virus) e e uma dica visual de que uma leva
     * acabou de nascer, util quando a tela ja esta cheia.
     */
    @Override
    public void render(Graphics2D g) {

        if (mostrarNome <= 0 || ultimoPais.isEmpty()) {
            return;
        }

        int alpha = Math.min(255, mostrarNome * 3);

        g.setFont(new Font("Monospaced", Font.BOLD, 13));

        String texto = "PROLIFERANDO EM: " + ultimoPais.toUpperCase();
        int larg = g.getFontMetrics().stringWidth(texto);

        int cx = Main.CAMPO_X + Main.CAMPO_W / 2 - larg / 2;
        int cy = Main.CAMPO_Y + Main.CAMPO_H - 16;

        g.setColor(new Color(0, 0, 0, alpha));
        g.drawString(texto, cx + 1, cy + 1);

        g.setColor(new Color(255, 230, 160, alpha));
        g.drawString(texto, cx, cy);
    }
}
