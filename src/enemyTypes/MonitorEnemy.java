package src.enemyTypes;

import java.awt.Color;

import src.Config;
import src.Main;
import src.Som;
import src.bulletTypes.IntegralBullet;

/**
 * MONITOR DE LABORATORIO POSSUIDO — o CRT do DCO que virou hostil.
 *
 * A faculdade foi evacuada com as maquinas ligadas (Roteiro.txt linha 6),
 * e o que sobrou nos laboratorios continua rodando o anticodigo. Este e
 * um dos monitores: ele desce, PARA no ar, cospe uma espiral de simbolos
 * e depois recua pra fora da tela.
 *
 * DE ONDE VEM A IDEIA
 * -------------------
 * E a fada que "senta" da serie: aquela que entra, freia no meio do
 * campo, solta um padrao inteiro e vai embora. O que ela acrescenta a uma
 * fase e um ALVO QUE FICA PARADO — os outros tres padroes desta fase
 * (pendulo, arco, horizontal) estao sempre atravessando, entao o jogador
 * nunca precisa escolher entre atirar e desviar. Aqui precisa: o monitor
 * fica ali, dando dano contínuo, e some sozinho se voce so desviar.
 *
 * OS TRES TEMPOS
 * --------------
 *   DESCENDO  — entra por cima ate a altura de parada; nao atira
 *   CUSPINDO  — parado, gira e solta a espiral
 *   RECUANDO  — sobe e sai; para de atirar
 *
 * A ESPIRAL e o padrao mais simples que existe e continua sendo bom: um
 * angulo que cresce um tanto fixo por disparo. O que a torna JUSTA aqui e
 * o passo ser irracional em relacao a volta completa — os bracos nunca se
 * sobrepoem, e sempre existe uma brecha andando pela tela.
 */
public class MonitorEnemy extends WaveEnemy {

    private enum Estado {
        DESCENDO,
        CUSPINDO,
        RECUANDO
    }

    private Estado estado = Estado.DESCENDO;

    /** Altura em que ele freia. */
    private final double alturaDeParada;

    private final double velocidadeDeDescida;
    private final double velocidadeDeRecuo;

    /** Ticks que ele passa parado cuspindo. */
    private final int ticksCuspindo;

    /** Ticks decorridos no estado CUSPINDO. */
    private int tCuspindo = 0;

    /** Ticks entre um braco da espiral e o proximo. */
    private final int cadenciaDaEspiral;

    /** Quantos bracos saem por disparo. */
    private final int bracos;

    /** Quanto o angulo avanca a cada disparo, em radianos. */
    private final double passo;

    /** Angulo atual da espiral. */
    private double angulo;

    public MonitorEnemy(double x) {

        super(x, Main.CAMPO_Y - 40);

        this.alturaDeParada = Main.CAMPO_Y + Main.CAMPO_H
                            * Config.getDouble("inimigo.monitor.alturaRelY", 0.26);

        this.velocidadeDeDescida = Config.getDouble("inimigo.monitor.velocidadeDescida", 2.2);
        this.velocidadeDeRecuo   = Config.getDouble("inimigo.monitor.velocidadeRecuo", 3.0);

        this.ticksCuspindo     = Math.max(20, Config.getInt("inimigo.monitor.ticksCuspindo", 260));
        this.cadenciaDaEspiral = Math.max(2, Config.getInt("inimigo.monitor.cadenciaDaEspiral", 9));
        this.bracos            = Math.max(1, Config.getInt("inimigo.monitor.bracos", 3));
        this.passo             = Config.getDouble("inimigo.monitor.passo", 0.41);

        // Comeca num angulo sorteado: dois monitores na mesma onda
        // soltando espirais em fase seriam uma parede, nao dois padroes.
        this.angulo = Math.random() * Math.PI * 2;

        this.hp = Config.getDouble("inimigo.monitor.hp", 26.0);
        this.hpMaximo = this.hp;
        this.radius = Config.getDouble("inimigo.monitor.raio", 15.0);

        this.sprite = Config.getString("inimigo.monitor.sprite", "sprites/enemies/monitor.png");
        this.escalaSprite = Config.getDouble("inimigo.monitor.escalaSprite", 2.3);

        this.velocidadeBala = Config.getDouble("inimigo.monitor.velocidadeBala", 2.4);
        this.raioBala       = Config.getDouble("inimigo.monitor.raioBala", 6.0);
        this.corBala        = new Color(120, 255, 190);

        this.pontos = Config.getInt("inimigo.monitor.pontos", 450);
        this.itens  = Config.getInt("inimigo.monitor.itens", 6);

        // O tiro dele e a espiral; o mirado do WaveEnemy fica desligado.
        this.cadenciaTiro = 0;
    }

    @Override
    protected void mover() {

        switch (estado) {

            case DESCENDO:

                y += velocidadeDeDescida;

                if (y >= alturaDeParada) {
                    y = alturaDeParada;
                    estado = Estado.CUSPINDO;
                }

                break;

            case CUSPINDO:

                tCuspindo++;

                if (tCuspindo >= ticksCuspindo) {
                    estado = Estado.RECUANDO;
                }

                break;

            case RECUANDO:

                y -= velocidadeDeRecuo;

                // Some por cima. Nao vale ponto por deixar ele ir embora:
                // quem quiser o drop tem que matar antes do tempo acabar.
                if (y < Main.CAMPO_Y - 60) {
                    isAlive = false;
                }

                break;

            default:
                break;
        }
    }

    @Override
    protected void atirar() {

        if (estado != Estado.CUSPINDO) {
            return;
        }

        if (tCuspindo % cadenciaDaEspiral != 0) {
            return;
        }

        // Som so a cada terceiro disparo: a cadencia e alta e o efeito
        // viraria um chiado continuo por cima da musica.
        if ((tCuspindo / cadenciaDaEspiral) % 3 == 0) {
            Som.tocar(Som.TIRO_INIMIGO);
        }

        for (int i = 0; i < bracos; i++) {

            double a = angulo + i * 2 * Math.PI / bracos;

            Main.bullets.add(new IntegralBullet(
                x, y,
                Math.cos(a) * velocidadeBala,
                Math.sin(a) * velocidadeBala,
                0, 0,
                raioBala,
                true,
                corBala
            ));
        }

        angulo += passo;
    }

    /** true enquanto ele esta parado no ar cuspindo (util pro debug). */
    public boolean estaCuspindo() {
        return estado == Estado.CUSPINDO;
    }
}
