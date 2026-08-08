package src;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import src.bulletTypes.IntegralBullet;
import src.bulletTypes.PonteiroBullet;
import src.bulletTypes.RicocheteBullet;

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

    /**
     * Quando true, WASD/setas nao movem mais o jogador — quem manda na
     * posicao dele e outra coisa (a maquina de Turing do PAPA prende ele
     * dentro do cabecote da fita).
     *
     * Travado tambem NAO ATIRA e NAO SOLTA BOMBA. Isso e proposital: a
     * maquina de Turing e o unico ataque do jogo em que a resposta certa
     * nao e mexer no personagem, e deixar o tiro ligado faria o jogador
     * segurar o Z por reflexo em vez de olhar pra fita. Sem poder atacar,
     * a unica saida e executar o programa — que e exatamente o que o
     * ataque cobra.
     */
    private boolean travado = false;

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

    /* --- tipos de tiro desbloqueados por nivel --- */

    private int nivelPonteiro;
    private int nivelRicochete;
    private int maxBalasDoLeque;
    private int maxPonteiros;

    private double velocidadePonteiro;
    private double taxaDeGiroPonteiro;
    private double raioPonteiro;
    private double fatorDanoPonteiro;

    private double velocidadeRicochete;
    private double raioRicochete;
    private double fatorDanoRicochete;
    private double anguloRicochete;
    private int quiquesRicochete;

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

        this.nivelPonteiro   = Config.getInt("tiro.ponteiro.nivel", 2);
        this.nivelRicochete  = Config.getInt("tiro.ricochete.nivel", 4);
        this.maxBalasDoLeque = Math.max(1, Config.getInt("tiro.leque.maximo", 4));
        this.maxPonteiros    = Math.max(1, Config.getInt("tiro.ponteiro.maximo", 3));

        this.velocidadePonteiro = Config.getDouble("tiro.ponteiro.velocidade", 7.5);
        this.taxaDeGiroPonteiro = Config.getDouble("tiro.ponteiro.taxaDeGiro", 0.10);
        this.raioPonteiro       = Config.getDouble("tiro.ponteiro.raio", 4.5);
        this.fatorDanoPonteiro  = Config.getDouble("tiro.ponteiro.fatorDano", 0.35);

        this.velocidadeRicochete = Config.getDouble("tiro.ricochete.velocidade", 9.0);
        this.raioRicochete       = Config.getDouble("tiro.ricochete.raio", 6.0);
        this.fatorDanoRicochete  = Config.getDouble("tiro.ricochete.fatorDano", 0.7);
        this.anguloRicochete     = Config.getDouble("tiro.ricochete.anguloGraus", 38);
        this.quiquesRicochete    = Config.getInt("tiro.ricochete.quiques", 3);

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
        if (!travado) {

            if (Main.up)    y -= speed;
            if (Main.down)  y += speed;
            if (Main.left)  x -= speed;
            if (Main.right) x += speed;

            prenderNoCampo();
        }

        // --- liga/desliga o autofire (tecla C) ---
        // Detecta a BORDA (agora apertado, antes nao): sem isso o tick
        // rodaria a troca 60x por segundo enquanto a tecla ficasse presa.
        if (Main.c && !cAnterior) {
            autofire = !autofire;
        }
        cAnterior = Main.c;

        // --- GPT Expansion (tecla V; tambem pode ser clicada no HUD) ---
        if (Main.v && !vAnterior && !travado) {
            usarGptExpansao();
        }
        vAnterior = Main.v;

        // --- tiro ---
        if (shootTime > 0) {
            shootTime--;
        }

        // Com autofire ligado o Z vira opcional; segurar Z continua
        // funcionando normalmente pra quem preferir.
        if ((Main.z || autofire) && shootTime <= 0 && !travado) {
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
     * expande a partir do jogador (ver GptExpansion.java): mata inimigo
     * comum, apaga as balas e fere o chefe sem mata-lo.
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
     * O disparo do jogador. Cada nivel DESBLOQUEIA UM TIPO DE TIRO novo,
     * em vez de so somar mais uma bala no leque.
     *
     *   N1  leque
     *   N2  leque + PONTEIROS   (teleguiados, fracos)
     *   N3  leque mais largo + ponteiros
     *   N4  + RICOCHETES        (quicam nas laterais)
     *   N5  tudo, com o leque cheio
     *
     * A diferenca nao e so de numero: cada tipo resolve um problema
     * diferente. O leque e o dano de frente; o ponteiro caça quem fugiu
     * pro canto; o ricochete alcança quem esta colado na parede. Somar
     * balas iguais so aumentava um numero — isto muda COMO se joga.
     */
    private void atirar() {

        Som.tocar(Som.TIRO_JOGADOR);

        dispararLeque();

        if (level >= nivelPonteiro) {
            dispararPonteiros();
        }

        if (level >= nivelRicochete) {
            dispararRicochetes();
        }

        if (Config.getBool("debug.balasDosCantos", false)) {
            balasDosCantos();
        }
    }

    /**
     * O tiro principal: um leque de balas retas pra cima.
     *
     * A quantidade cresce com o nivel, mas devagar (ver balasDoLeque):
     * o grosso do poder novo vem dos TIPOS que os niveis desbloqueiam,
     * nao da contagem.
     */
    private void dispararLeque() {

        int quantidade = balasDoLeque();

        for (int i = 0; i < quantidade; i++) {

            // Uma bala so nao tem leque: dividir por (quantidade-1) daria
            // divisao por zero.
            double ang = (quantidade == 1)
                       ? 0
                       : -shootRad / 2.0 + i * (shootRad / (quantidade - 1.0));

            IntegralBullet bala = new IntegralBullet(
                x, y,
                Math.sin(ang) * velocidadeBala,
                -Math.cos(ang) * velocidadeBala,
                0, 0,
                raioBala,
                false,
                new Color(120, 220, 255)
            );

            bala.setDano(danoBala);
            bala.setSprite(Config.getString("tiro.leque.sprite", "sprites/GFX/bala_leque.png"));

            Main.bullets.add(bala);
        }
    }

    /** Quantas balas o leque tem no nivel atual. */
    private int balasDoLeque() {
        return Math.min(level, maxBalasDoLeque);
    }

    /**
     * PONTEIROS: saem em diagonal pra cima e depois curvam atras do
     * inimigo mais proximo. Dano baixo — o valor deles e a cobertura.
     *
     * Saem inclinados (e nao retos) pra nao competirem com o leque no
     * mesmo espaco: eles abrem, procuram e voltam.
     */
    private void dispararPonteiros() {

        Som.tocar(Som.TIRO_PONTEIRO);

        int quantidade = 1 + (level - nivelPonteiro);
        quantidade = Math.max(1, Math.min(quantidade, maxPonteiros));

        for (int i = 0; i < quantidade; i++) {

            // Alterna os lados: -90 graus e a vertical pra cima na tela.
            double lado = (i % 2 == 0) ? -1 : 1;
            double abertura = 0.6 + 0.25 * (i / 2);

            double ang = -Math.PI / 2 + lado * abertura;

            Main.bullets.add(new PonteiroBullet(
                x, y, ang,
                velocidadePonteiro,
                taxaDeGiroPonteiro,
                raioPonteiro,
                danoBala * fatorDanoPonteiro,
                new Color(150, 255, 170)
            ));
        }
    }

    /**
     * RICOCHETES: dois losangos em diagonal que quicam nas laterais.
     * Dano medio, mas varrem a tela em ziguezague e pegam quem esta
     * encostado na borda.
     */
    private void dispararRicochetes() {

        Som.tocar(Som.TIRO_RICOCHETE);

        double ang = Math.toRadians(anguloRicochete);

        for (int lado = -1; lado <= 1; lado += 2) {

            Main.bullets.add(new RicocheteBullet(
                x, y,
                Math.sin(ang) * velocidadeRicochete * lado,
                -Math.cos(ang) * velocidadeRicochete,
                raioRicochete,
                danoBala * fatorDanoRicochete,
                quiquesRicochete,
                new Color(255, 180, 90)
            ));
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

        if (invulneravel > 0) {
            return false;
        }

        Som.tocar(Som.JOGADOR_DANO);

        vidas--;
        invulneravel = invulnerabilidadeTicks;

        // NAO limpa a tela: as balas continuam onde estao e o jogador
        // atravessa elas durante a invulnerabilidade. Apagar tudo a cada
        // dano deixava a luta sem tensao e sumia com o padrao que o
        // jogador estava lendo — a janela de invencibilidade ja e a
        // protecao suficiente pra ele se reposicionar.

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

    public boolean isTravado() {
        return travado;
    }

    /** Trava/destrava o movimento. Quem trava e responsavel por destravar. */
    public void setTravado(boolean travado) {
        this.travado = travado;
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
