package src;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import src.bulletTypes.IntegralBullet;

/**
 * O estudante expancionista.
 *
 * Controles: WASD/setas move, Z atira, X ou SHIFT segura pra modo foco
 * (anda devagar, tiro fecha o leque, e a hitbox fica visivel).
 *
 * Todos os numeros de jogabilidade sao lidos do game.properties em
 * carregarConfig(), que tambem e chamado no hot-reload (F5). Se voce quer
 * mexer em velocidade/cadencia/dano, mexa no .properties, nao aqui.
 */
public class Player {

    /* =========================
            ESTADO
       ========================= */

    private double x;
    private double y;
    private double speed;

    private int xp = 0;
    private int level = 1;
    private int pontuacao = 0;

    private int vidas;
    private int bombas;

    /** Ticks restantes de invulnerabilidade. > 0 = piscando e imune. */
    private int invulneravel = 0;

    /** Contador regressivo ate o proximo tiro sair. */
    private int shootTime = 0;

    /** Tiro automatico: quando ligado, atira sozinho sem segurar o Z. */
    private boolean autofire;

    /** Estado do C no tick anterior, pra detectar o momento do aperto. */
    private boolean cAnterior = false;

    /** Estado do V no tick anterior, pra detectar o momento do aperto. */
    private boolean vAnterior = false;

    /** Estado do foco no tick anterior, pro som tocar so ao entrar. */
    private boolean focoAnterior = false;

    /** Abertura atual do leque de tiro, em radianos. */
    private double shootRad;

    /** Raio da hitbox real (a bolinha verde). */
    private double radius;

    /** Tamanho do retrato em relacao a hitbox — so aparencia, nao colisao. */
    private double escalaSprite;

    /* =========================
            CONFIGURACAO
       ========================= */

    private double velocidadeNormal;
    private double velocidadeFoco;
    private double raioColeta;
    private double aberturaNormal;
    private double aberturaFoco;
    private int cadenciaTiro;
    private double velocidadeBala;
    private double raioBala;
    private double danoBala;
    private int invulnerabilidadeTicks;

    /* --- progressao de nivel --- */

    /** Quantos itens custa o nivel 2. Os seguintes multiplicam por fatorXp. */
    private int xpBase;

    /** Multiplicador do custo a cada nivel. Maior = progressao mais lenta. */
    private double fatorXp;

    /** Teto de nivel. Sem isso o leque de tiro cresceria sem limite. */
    private int nivelMaximo;

    /** Quanto vale, em pontos, cada item pego ja no nivel maximo. */
    private int xpEmPontos;

    public Player(double x, double y, double radius) {

        this.x = x;
        this.y = y;
        this.radius = radius;

        carregarConfig();

        // Vidas, bombas e autofire so no construtor: recarregar config no
        // meio da partida nao deve devolver vidas nem desligar o autofire
        // que o jogador acabou de ligar na mao.
        this.vidas    = Config.getInt("jogador.vidas", 3);
        this.bombas   = Config.getInt("jogador.bombas", 3);
        this.autofire = Config.getBool("jogador.autofireInicial", true);
    }

    /**
     * (Re)le os ajustes do game.properties.
     * Chamado no construtor e no F5, pra dar pra tunar com o jogo aberto.
     */
    public void carregarConfig() {

        this.velocidadeNormal = Config.getDouble("jogador.velocidade", 80.0);
        this.velocidadeFoco   = Config.getDouble("jogador.velocidadeFoco", 1.75);
        this.radius           = Config.getDouble("jogador.raioHitbox", 5.0);
        this.raioColeta       = Config.getDouble("jogador.raioColeta", 50.0);
        this.escalaSprite     = Config.getDouble("jogador.escalaSprite", 5.6);

        this.aberturaNormal   = Config.getDouble("jogador.aberturaTiro", 0.5);
        this.aberturaFoco     = Config.getDouble("jogador.aberturaTiroFoco", 0.1);
        this.cadenciaTiro     = Config.getInt("jogador.cadenciaTiro", 5);
        this.velocidadeBala   = Config.getDouble("jogador.velocidadeBala", 12.0);
        this.raioBala         = Config.getDouble("jogador.raioBala", 4.0);
        this.danoBala         = Config.getDouble("jogador.danoBala", 1.0);

        this.invulnerabilidadeTicks = Config.getInt("jogador.invulnerabilidadeTicks", 90);

        this.xpBase      = Math.max(1, Config.getInt("jogador.xpBase", 12));
        this.fatorXp     = Math.max(1.0, Config.getDouble("jogador.fatorXpPorNivel", 2.4));
        this.nivelMaximo = Math.max(1, Config.getInt("jogador.nivelMaximo", 6));
        this.xpEmPontos  = Config.getInt("jogador.xpEmPontosNoNivelMaximo", 50);
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        // --- modo foco (X ou SHIFT segurado) ---
        // Borda de subida: o som toca no instante em que entra no foco,
        // senao dispararia 60x por segundo enquanto a tecla ficasse presa.
        if (Main.x && !focoAnterior) {
            Som.tocar(Som.FOCO);
        }
        focoAnterior = Main.x;

        if (Main.x) {
            speed = velocidadeFoco;
            shootRad = aberturaFoco;
        } else {
            speed = velocidadeNormal;
            shootRad = aberturaNormal;
        }

        subirDeNivel();

        // --- movimento ---
        if (Main.up)    y -= speed;
        if (Main.down)  y += speed;
        if (Main.left)  x -= speed;
        if (Main.right) x += speed;

        prenderNoCampo();

        // --- liga/desliga o autofire (tecla C) ---
        // Detecta a BORDA (agora apertado, antes nao): sem isso o tick
        // rodaria a troca 60x por segundo enquanto a tecla ficasse presa.
        if (Main.c && !cAnterior) {
            autofire = !autofire;
        }
        cAnterior = Main.c;

        // --- GPT Expansion (tecla V; tambem pode ser clicada no HUD) ---
        if (Main.v && !vAnterior) {
            usarGptExpansao();
        }
        vAnterior = Main.v;

        // --- tiro ---
        if (shootTime > 0) {
            shootTime--;
        }

        // Com autofire ligado o Z vira opcional; segurar Z continua
        // funcionando normalmente pra quem preferir.
        if ((Main.z || autofire) && shootTime <= 0) {
            atirar();
            shootTime = cadenciaTiro;
        }

        // --- invulnerabilidade escorrendo ---
        if (invulneravel > 0) {
            invulneravel--;
        }

        // --- codigo de teste antigo, ligavel pelo game.properties ---
        if (Config.getBool("debug.pontosDeTeste", false) && shootTime % 5 == 0) {
            Main.points.add(new Point(Main.CAMPO_X + Main.CAMPO_W / 2.0, Main.CAMPO_Y, false));
        }
    }

    /**
     * Ativa a GPT Expansion: gasta uma carga e solta o efeito que se
     * expande a partir do jogador (ver GptExpansion.java), matando todo
     * inimigo e apagando toda bala que ele tocar.
     *
     * Publico porque tanto o teclado (V, aqui em tick()) quanto o clique
     * do mouse no botao do HUD (tratado no Main) chamam este metodo.
     */
    public void usarGptExpansao() {

        if (bombas <= 0) {
            return;
        }

        bombas--;
        Som.tocar(Som.GPT_EXPANSION);
        Main.efeitosGpt.add(new GptExpansion(x, y));
    }

    /**
     * Gasta o XP acumulado em niveis.
     *
     * 'while' e nao 'if': se o jogador pegar um punhado de itens de uma vez
     * (varios inimigos morrendo juntos), ele sobe todos os niveis que der no
     * mesmo tick, em vez de segurar um por frame.
     */
    private void subirDeNivel() {

        while (level < nivelMaximo && xp >= getXpParaProximoNivel()) {
            xp -= getXpParaProximoNivel();
            level++;
            Som.tocar(Som.SUBIR_NIVEL);
        }

        // No teto o XP nao serve mais pra nada, entao vira pontuacao:
        // continuar coletando itens ainda vale a pena.
        if (level >= nivelMaximo && xp > 0) {
            pontuacao += xp * xpEmPontos;
            xp = 0;
        }
    }

    /**
     * Leque de 'level' balas para cima.
     * Nivel 1 = 1 bala reta; a partir dai as balas se espalham dentro
     * do angulo shootRad, distribuidas por igual.
     */
    private void atirar() {

        Som.tocar(Som.TIRO_JOGADOR);

        for (int i = 0; i < level; i++) {

            // Nivel 1 nao tem leque: divisao por (level-1) daria divisao por zero.
            double ang = (level == 1)
                       ? 0
                       : -shootRad / 2.0 + i * (shootRad / (level - 1.0));

            IntegralBullet bala = new IntegralBullet(
                x, y,
                Math.sin(ang) * velocidadeBala,
                -Math.cos(ang) * velocidadeBala,
                0, 0,
                raioBala,
                false,                    // bala do jogador
                new Color(120, 220, 255)
            );

            bala.setDano(danoBala);

            Main.bullets.add(bala);
        }

        if (Config.getBool("debug.balasDosCantos", false)) {
            balasDosCantos();
        }
    }

    /**
     * Codigo de teste do grupo: quatro balas inimigas vindas dos cantos,
     * miradas no jogador. Serve pra testar colisao sem depender de inimigo.
     * Ligue com debug.balasDosCantos=true no game.properties.
     */
    private void balasDosCantos() {

        double spd = 1.5;

        double[][] cantos = {
            { Main.CAMPO_X,               Main.CAMPO_Y },
            { Main.CAMPO_X + Main.CAMPO_W, Main.CAMPO_Y },
            { Main.CAMPO_X,               Main.CAMPO_Y + Main.CAMPO_H },
            { Main.CAMPO_X + Main.CAMPO_W, Main.CAMPO_Y + Main.CAMPO_H }
        };

        for (double[] c : cantos) {

            double dist = Main.getDist(c[0], c[1], x, y);

            if (dist == 0) {
                continue;
            }

            Main.bullets.add(new IntegralBullet(
                c[0], c[1],
                spd * ((x - c[0]) / dist),
                spd * ((y - c[1]) / dist),
                0, 0, 10, true
            ));
        }
    }

    /** Impede o jogador de sair da arena. */
    private void prenderNoCampo() {

        double margem = 8;

        if (x < Main.CAMPO_X + margem)                x = Main.CAMPO_X + margem;
        if (x > Main.CAMPO_X + Main.CAMPO_W - margem) x = Main.CAMPO_X + Main.CAMPO_W - margem;
        if (y < Main.CAMPO_Y + margem)                y = Main.CAMPO_Y + margem;
        if (y > Main.CAMPO_Y + Main.CAMPO_H - margem) y = Main.CAMPO_Y + Main.CAMPO_H - margem;
    }

    /**
     * Leva um hit. Chamado pelas balas inimigas.
     *
     * @return true se o dano foi aplicado de fato; false se o jogador
     *         estava invulneravel (a bala deve atravessar sem sumir)
     */
    public boolean levarDano() {

        // Vidas infinitas no modo debug: a bala atravessa sem tirar vida
        // nem gastar invulnerabilidade, pra dar pra testar um padrao de
        // chefe do inicio ao fim sem morrer no meio.
        if (Main.debugMode) {
            return false;
        }

        if (invulneravel > 0) {
            return false;
        }

        Som.tocar(Som.JOGADOR_DANO);

        vidas--;
        invulneravel = invulnerabilidadeTicks;

        // Limpa a tela ao morrer, senao o jogador renasce dentro do inferno.
        // So MARCA as balas como mortas em vez de remover da lista: este
        // metodo e chamado de dentro do loop de balas do Main, e mexer no
        // tamanho da lista ali no meio faria o loop pular elementos.
        for (int i = 0; i < Main.bullets.size(); i++) {

            if (Main.bullets.get(i).isHitPlayer()) {
                Main.bullets.get(i).setAlive(false);
            }
        }

        if (vidas <= 0) {
            Som.tocar(Som.GAME_OVER);
            Main.gameState = "GameOver";
        }

        return true;
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        // Pisca enquanto invulneravel: some em metade dos frames.
        if (invulneravel > 0 && (invulneravel / 4) % 2 == 0) {
            return;
        }

        // Circulo de coleta de itens (atras do retrato, senao ele cobre tudo)
        g.setColor(new Color(60, 120, 255, 90));
        g.drawOval((int) (x - raioColeta), (int) (y - raioColeta),
                   (int) (raioColeta * 2), (int) (raioColeta * 2));

        renderRetrato(g);

        // Hitbox real. Fica bem visivel no modo foco, que e quando importa.
        // Desenhada por CIMA do retrato de proposito: e o unico circulo que
        // importa pra desviar de bala, entao tem que estar sempre legivel.
        g.setColor(Main.x ? Color.GREEN : new Color(0, 255, 0, 140));
        g.fillOval((int) (x - radius), (int) (y - radius),
                   (int) (radius * 2), (int) (radius * 2));
    }

    /**
     * Retrato do jogador (sprites/player/estudante.png), centrado na
     * hitbox e escalado por 'escalaSprite' — mesma ideia usada nos
     * inimigos (Enemy.escalaSprite): a ARTE pode ser grande sem que a
     * area de colisao real (radius) mude, senao o jogo fica injusto.
     * Sem o PNG, cai num circulo branco simples.
     */
    private void renderRetrato(Graphics2D g) {

        BufferedImage img = Assets.get("sprites/player/estudante.png");

        if (img == null) {
            g.setColor(Color.WHITE);
            g.fillOval((int) (x - 16), (int) (y - 16), 32, 32);
            return;
        }

        int lado = (int) (radius * 2 * escalaSprite);

        // Anel fino: ajuda a separar o retrato do fundo escuro da fase.
        g.setColor(new Color(255, 255, 255, 160));
        g.drawOval((int) (x - lado / 2.0) - 1, (int) (y - lado / 2.0) - 1, lado + 2, lado + 2);

        g.drawImage(img, (int) (x - lado / 2.0), (int) (y - lado / 2.0), lado, lado, null);
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    /**
     * Quantos itens faltam pro proximo nivel.
     *
     *     custo = xpBase * fatorXp ^ (nivel - 1)
     *
     * Com xpBase 12 e fator 2.4:  N2=12  N3=29  N4=69  N5=166  N6=398
     * (cada inimigo dropa 2 itens, entao N2 sai com 6 inimigos e N6 exige
     * umas 340 mortes acumuladas — de propósito, pro leque de tiro nao
     * disparar nos primeiros segundos da fase).
     */
    public int getXpParaProximoNivel() {
        return (int) Math.round(xpBase * Math.pow(fatorXp, level - 1));
    }

    /** true se o jogador ja chegou no teto de nivel. */
    public boolean isNivelMaximo() {
        return level >= nivelMaximo;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getPontuacao() {
        return pontuacao;
    }

    public void setPontuacao(int pontuacao) {
        this.pontuacao = pontuacao;
    }

    public int getVidas() {
        return vidas;
    }

    public void setVidas(int vidas) {
        this.vidas = vidas;
    }

    public int getBombas() {
        return bombas;
    }

    public void setBombas(int bombas) {
        this.bombas = bombas;
    }

    public int getInvulneravel() {
        return invulneravel;
    }

    public void setInvulneravel(int invulneravel) {
        this.invulneravel = invulneravel;
    }

    public int getShootTime() {
        return shootTime;
    }

    public void setShootTime(int shootTime) {
        this.shootTime = shootTime;
    }

    public double getShootRad() {
        return shootRad;
    }

    public void setShootRad(double shootRad) {
        this.shootRad = shootRad;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getRaioColeta() {
        return raioColeta;
    }

    public double getEscalaSprite() {
        return escalaSprite;
    }

    public void setEscalaSprite(double escalaSprite) {
        this.escalaSprite = escalaSprite;
    }

    public int getNivelMaximo() {
        return nivelMaximo;
    }

    public void setNivelMaximo(int nivelMaximo) {
        this.nivelMaximo = nivelMaximo;
    }

    public boolean isAutofire() {
        return autofire;
    }

    public void setAutofire(boolean autofire) {
        this.autofire = autofire;
    }
}
