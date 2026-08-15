package src.enemyTypes.spellCards;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Random;

import src.Config;
import src.Main;
import src.Som;
import src.enemyTypes.BossEnemy;

/**
 * SPELL CARD - "⊢ Máquina de Turing"
 *
 * O unico ataque do jogo que nao e desviado: e EXECUTADO.
 *
 * O PAPA prende o jogador dentro de um CABECOTE — uma capsula que corre
 * sobre uma fita de simbolos. Cada celula da fita tem um simbolo (uma
 * letra ou um comando) e um relogio proprio. Enquanto o relogio nao
 * zera, o jogador tem que DIGITAR aquele simbolo no teclado; acertando,
 * o cabecote anda pra proxima celula. Errando, ou deixando o relogio
 * zerar, ele leva dano.
 *
 * POR QUE ISSO CABE NUM BULLET HELL
 * ---------------------------------
 * Bullet hell e, no fundo, um teste de reacao sob pressao de tempo. A
 * fita e o mesmo teste com outra gramatica: em vez de "onde eu ponho o
 * corpo antes do relogio zerar", vira "que tecla eu aperto antes do
 * relogio zerar". A capsula existe justamente pra deixar isso explicito
 * — com o movimento travado, nao ha ambiguidade sobre o que o jogo esta
 * pedindo naquele momento.
 *
 * O TIRO FICA DESLIGADO durante este ataque, e a bomba tambem. Nao e
 * castigo: e o que define o ataque. A unica coisa que machuca o PAPA aqui
 * e digitar certo, e o HP dele foi acertado pra ser EXATAMENTE
 *
 *     tamanhoDaFita * danoPorAcerto
 *
 * ou seja, a barra de vida dele E a barra de progresso da fita, e o
 * ataque so acaba quando o programa terminar de rodar. Nao ha como sair
 * dele atirando, nem esperando o tempo passar (a duracao e proposital-
 * mente enorme). Errar nao volta o cabecote — so custa tempo ou vida.
 *
 * A FITA E MAIOR QUE A TELA
 * -------------------------
 * Ela e desenhada em ROLAGEM: o cabecote fica sempre no meio do campo e
 * quem anda e a fita, por baixo dele. E o que uma maquina de Turing de
 * verdade faz, e evita ter que caber a fita inteira na largura da tela.
 *
 * NOTA sobre o alfabeto: as letras C, V, X e Z ficam de fora por
 * padrao porque ja sao teclas de acao (foco, bomba, tiro). Se voce
 * mexer em 'papa.turing.alfabeto', mantenha essa regra.
 */
public class TuringSpell extends SpellCard {

    /** Simbolos que podem cair numa celula. */
    private final String alfabeto;

    /** Quantas celulas o programa tem. */
    private final int tamanhoDaFita;

    /** Ticks de relogio da primeira celula. */
    private final int tempoInicial;

    /** Ticks tirados do relogio a cada celula resolvida (a fita acelera). */
    private final double aceleracao;

    /** Piso do relogio, pra nao virar impossivel no fim. */
    private final int tempoMinimo;

    /** Dano no PAPA por simbolo certo. */
    private final double danoPorAcerto;

    /** Largura de uma celula desenhada. */
    private final int larguraCelula;

    /* --- estado --- */

    private char[] fita;

    /** Em que celula o cabecote esta. */
    private int cabecote = 0;

    /** Ticks restantes no relogio da celula atual. */
    private int relogio = 0;

    /** Ticks totais do relogio da celula atual (pro arco do relogio). */
    private int relogioCheio = 1;

    /** Deslocamento suavizado da fita, pra ela deslizar em vez de pular. */
    private double rolagem = 0;

    /** > 0 = piscando de vermelho (errou). Só efeito visual. */
    private int flashErro = 0;

    /** > 0 = piscando de verde (acertou). */
    private int flashAcerto = 0;

    /** Em que estado a maquina estava em cada celula. So pra mostrar. */
    private String[] estados;

    /** Guarda a posicao do jogador antes da trava, pra devolver depois. */
    private double xAntes, yAntes;

    private boolean travouOJogador = false;

    /**
     * O TRACO DE EXECUCAO de um programa de verdade.
     *
     * A fita do ataque nao e mais sorteada: e a lista de passos de uma
     * MaquinaDeTuring real rodando o reconhecedor de 0ⁿ1ⁿ. Cada celula que
     * o jogador digita e um simbolo que a maquina de fato escreveu, na
     * ordem em que escreveu.
     *
     * Calculado UMA VEZ e guardado: o construtor precisa do tamanho dele
     * antes do super(), e rodar a maquina de novo a cada luta seria
     * trabalho repetido pra dar sempre o mesmo resultado (a maquina e
     * deterministica — e essa e a graca).
     */
    private static java.util.List<src.MaquinaDeTuring.Passo> tracoCache = null;

    private static java.util.List<src.MaquinaDeTuring.Passo> traco() {

        if (tracoCache == null) {

            int n = Math.max(1, Config.getInt("papa.turing.n", 4));

            tracoCache = src.MaquinaDeTuring.reconhecedor0n1n()
                    .executar(src.MaquinaDeTuring.entrada0n1n(n), 4000);
        }

        return tracoCache;
    }

    public TuringSpell() {

        // O HP E O TAMANHO DA COMPUTACAO.
        //
        // Durante este ataque o tiro esta desligado, entao digitar e a
        // UNICA fonte de dano — a barra de vida do chefe e literalmente a
        // barra de progresso da fita. Derivar o HP do traco em vez de
        // deixar solto no properties elimina a chance de os dois
        // desalinharem: com HP a mais o ataque nunca terminaria, com HP a
        // menos ele acabaria no meio do programa.
        super("⊢  Máquina de Turing",
              traco().size() * Config.getDouble("papa.turing.danoPorAcerto", 10),
              Config.getInt("papa.turing.duracao", 999999));

        this.alfabeto      = Config.getString("papa.turing.alfabeto", "01ABDEFGHIJKLMNOPQRSTUWY");
        this.tamanhoDaFita = traco().size();

        this.tempoInicial = Math.max(20, Config.getInt("papa.turing.tempoInicial", 130));
        this.aceleracao   = Config.getDouble("papa.turing.aceleracao", 3.5);
        this.tempoMinimo  = Math.max(15, Config.getInt("papa.turing.tempoMinimo", 55));

        this.danoPorAcerto = Config.getDouble("papa.turing.danoPorAcerto", 10);
        this.larguraCelula = Math.max(24, Config.getInt("papa.turing.larguraCelula", 62));
    }

    /* =========================
            CICLO DE VIDA
       ========================= */

    @Override
    public void iniciar(BossEnemy chefe) {

        // A FITA E O QUE A MAQUINA ESCREVEU, passo a passo.
        //
        // Antes eram letras sorteadas do alfabeto: funcionava como jogo,
        // mas era mentira — nao havia programa nenhum ali, so ruido com
        // cara de computacao. Num trabalho de um curso que ENSINA maquina
        // de Turing, e o tipo de detalhe que o professor nota em dois
        // segundos.
        java.util.List<src.MaquinaDeTuring.Passo> passos = traco();

        fita = new char[tamanhoDaFita];
        estados = new String[tamanhoDaFita];

        for (int i = 0; i < tamanhoDaFita; i++) {
            fita[i] = passos.get(i).escrito;
            estados[i] = passos.get(i).estado;
        }

        cabecote = 0;
        rolagem = 0;
        flashErro = 0;
        flashAcerto = 0;

        armarRelogio();

        // Limpa a caixa de tecla: se o jogador estava com o dedo em cima
        // de uma letra quando o ataque comecou, ela nao pode contar.
        Main.limparTeclaDigitada();

        prenderOJogador();
    }

    @Override
    public void atacar(int t, BossEnemy chefe) {

        // A trava e reaplicada todo tick de proposito: se o jogador levar
        // dano e for reposicionado, ou o F5 recarregar a config, ele volta
        // pro cabecote em vez de ficar solto no meio da fita.
        prenderOJogador();

        if (flashErro > 0)   flashErro--;
        if (flashAcerto > 0) flashAcerto--;

        atualizarVoadores();

        // A fita desliza ate a posicao do cabecote atual (12% do que falta
        // por tick): o mesmo amortecimento usado nos retratos da cutscene.
        rolagem += (cabecote - rolagem) * 0.12;

        if (terminou()) {
            return;
        }

        lerTeclado(chefe);

        relogio--;

        if (relogio <= 0) {
            falhar();
        }
    }

    /** Le a caixinha de tecla do Main e julga o simbolo. */
    private void lerTeclado(BossEnemy chefe) {

        char digitada = Main.consumirTeclaDigitada();

        if (digitada == 0) {
            return;
        }

        if (digitada == fita[cabecote]) {
            acertar(chefe);
        } else {
            errarPorTeclaErrada();
        }
    }

    /** Simbolo certo: anda uma celula, machuca o PAPA e aperta o relogio. */
    private void acertar(BossEnemy chefe) {

        Som.tocar(Som.TURING_OK);

        flashAcerto = 10;

        // O SIMBOLO DIGITADO VIRA UM PROJETIL E VOA NO CHEFE.
        //
        // O dano ja acontecia — a barra dele descia a cada letra — mas nada
        // LIGAVA as duas coisas na tela: voce digitava aqui embaixo e um
        // numero mudia la em cima. Sem esse elo, o ataque parecia um
        // minigame parado no meio da luta, e nao a luta em si.
        //
        // O projetil sai da celula, sobe ate o chefe e estoura nele (ver
        // SimboloVoador). O dano em si continua sendo aplicado AQUI, na
        // hora do acerto, e nao quando o projetil chega: atrasar o dano
        // pelo tempo de voo faria a barra descer fora de compasso com a
        // digitacao, que e justamente a sincronia que a gente quer.
        dispararSimbolo(chefe);

        chefe.levarDano(danoPorAcerto);

        cabecote++;

        if (terminou()) {
            soltarOJogador();
            return;
        }

        armarRelogio();
    }

    /**
     * Tecla errada NAO custa vida — so tempo.
     *
     * Cobrar dano por letra errada tornaria o ataque um teste de nao
     * encostar no teclado, e quem digita rapido seria punido por tentar.
     * Perder metade do relogio ja e uma penalidade que assusta.
     */
    private void errarPorTeclaErrada() {

        Som.tocar(Som.TURING_ERRO);

        flashErro = 14;
        relogio = Math.max(1, relogio / 2);
    }

    /** Relogio zerou: ai sim custa vida, e a celula continua a mesma. */
    private void falhar() {

        Som.tocar(Som.TURING_ERRO);

        flashErro = 24;

        if (Main.player != null) {
            Main.player.levarDano();
        }

        armarRelogio();
    }

    /**
     * Cria o projetil do simbolo recem-digitado, saindo da celula do
     * cabecote em direcao ao chefe.
     */
    private void dispararSimbolo(BossEnemy chefe) {

        double cx = Main.CAMPO_X + Main.CAMPO_W / 2.0;
        double cy = alturaDaFita();

        voadores.add(new SimboloVoador(cx, cy, chefe.getX(), chefe.getY(),
                                       fita[cabecote]));
    }

    /**
     * Um simbolo voando da fita ate o chefe, com o estouro no fim.
     *
     * Vive dentro do proprio spell card em vez de virar uma bala do jogo:
     * ele nao colide com nada, nao pode ser apagado por bomba e nao deve
     * aparecer em nenhum loop de colisao. E puro feedback.
     */
    private static class SimboloVoador {

        final double x0, y0, x1, y1;
        final char simbolo;

        int t = 0;
        final int voo;

        SimboloVoador(double x0, double y0, double x1, double y1, char simbolo) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.simbolo = simbolo;
            this.voo = Math.max(6, Config.getInt("papa.turing.ticksDeVooDoSimbolo", 16));
        }

        boolean acabou() {
            return t > voo + Config.getInt("papa.turing.ticksDoEstouro", 14);
        }

        /** 0 a 1 durante o voo; 1 depois que chegou. */
        double progresso() {
            return Math.min(1.0, t / (double) voo);
        }

        boolean estourando() {
            return t > voo;
        }
    }

    /** Os simbolos em voo agora. */
    private final java.util.List<SimboloVoador> voadores = new java.util.ArrayList<>();

    /** Anda com os projeteis e descarta os que ja estouraram. */
    private void atualizarVoadores() {

        for (int i = voadores.size() - 1; i >= 0; i--) {

            voadores.get(i).t++;

            if (voadores.get(i).acabou()) {
                voadores.remove(i);
            }
        }
    }

    /**
     * Desenha os simbolos voando e o estouro na chegada.
     *
     * O voo tem um ARCO: o projetil sobe desviando pro lado antes de
     * cair no chefe. Linha reta entre dois pontos que estao quase na
     * mesma vertical viraria um risco sem leitura; o arco mostra a
     * trajetoria inteira em duas dezenas de frames.
     */
    private void desenharVoadores(java.awt.Graphics2D g) {

        for (int i = 0; i < voadores.size(); i++) {

            SimboloVoador v = voadores.get(i);

            double f = v.progresso();

            double x = v.x0 + (v.x1 - v.x0) * f;
            double y = v.y0 + (v.y1 - v.y0) * f;

            // Arco: um seno somado na horizontal, maximo no meio do voo.
            x += Math.sin(f * Math.PI) * 70 * ((i % 2 == 0) ? 1 : -1);

            if (!v.estourando()) {

                // Rastro.
                g.setColor(new Color(150, 255, 200, 90));
                g.fillOval((int) x - 9, (int) y - 9, 18, 18);

                g.setFont(new Font("Monospaced", Font.BOLD, 22));
                g.setColor(new Color(220, 255, 235));

                String texto = String.valueOf(v.simbolo);
                int larg = g.getFontMetrics().stringWidth(texto);

                g.drawString(texto, (int) (x - larg / 2.0), (int) y + 8);

                continue;
            }

            // ESTOURO no chefe: aneis abrindo e apagando.
            double fe = (v.t - v.voo)
                      / (double) Math.max(1, Config.getInt("papa.turing.ticksDoEstouro", 14));

            for (int k = 3; k >= 1; k--) {

                double r = 10 + 34 * fe * k * 0.45;
                int a = (int) (200 * (1 - fe) / k);

                if (a <= 0) {
                    continue;
                }

                g.setColor(new Color(180, 255, 210, Math.min(255, a)));
                g.drawOval((int) (v.x1 - r), (int) (v.y1 - r), (int) (r * 2), (int) (r * 2));
            }
        }
    }

    /** Relogio da celula atual, encurtando conforme a fita avanca. */
    private void armarRelogio() {

        int t = (int) (tempoInicial - aceleracao * cabecote);

        relogioCheio = Math.max(tempoMinimo, t);
        relogio = relogioCheio;
    }

    /**
     * O estado da maquina na celula atual, pra mostrar no rodape.
     *
     * Sem isto a fita continuaria parecendo uma sequencia arbitraria: o
     * estado mudando (q0 -> q1 -> q2) e o que deixa visivel que existe uma
     * MAQUINA rodando ali, e nao um gerador de letras.
     */
    private String estadoAtual() {

        if (estados == null || cabecote < 0 || cabecote >= estados.length) {
            return "";
        }

        return estados[cabecote];
    }

    private boolean terminou() {
        return cabecote >= tamanhoDaFita;
    }

    /* =========================
            A CAPSULA
       ========================= */

    /** Leva o jogador pro centro do campo e desliga o movimento dele. */
    private void prenderOJogador() {

        if (Main.player == null) {
            return;
        }

        if (!travouOJogador) {
            xAntes = Main.player.getX();
            yAntes = Main.player.getY();
            travouOJogador = true;
        }

        Main.player.setTravado(true);
        Main.player.setX(Main.CAMPO_X + Main.CAMPO_W / 2.0);

        // ACIMA da fita, nao em cima dela. Com o jogador centrado na
        // celula, o retrato dele cobria exatamente o simbolo que ele
        // precisava ler — o ataque pedia uma informacao e escondia ela.
        Main.player.setY(alturaDaFita() - alturaDoCabecote());
    }

    /**
     * Devolve o controle.
     *
     * Chamado quando a fita acaba. O BossEnemy tambem chama isso ao
     * trocar de spell card (ver encerrar()), senao um ataque interrompido
     * no meio deixaria o jogador travado pro resto da luta — o pior bug
     * possivel neste ataque.
     */
    private void soltarOJogador() {

        if (Main.player == null || !travouOJogador) {
            return;
        }

        Main.player.setTravado(false);
        Main.player.setX(xAntes);
        Main.player.setY(yAntes);

        travouOJogador = false;
    }

    @Override
    public void encerrar(BossEnemy chefe) {
        soltarOJogador();
    }

    private double alturaDaFita() {
        return Main.CAMPO_Y + Main.CAMPO_H * Config.getDouble("papa.turing.alturaRelY", 0.62);
    }

    /**
     * Quanto o cabecote fica ACIMA do centro da celula.
     *
     * Tem que ser maior que meia celula mais meio retrato, senao o
     * jogador volta a encostar no simbolo. O padrao (78) da folga pro
     * retrato de 62 px sobre celulas de 62.
     */
    private double alturaDoCabecote() {
        return Config.getDouble("papa.turing.alturaDoCabecote", 78);
    }

    /* =========================
            RENDER
       ========================= */

    @Override
    public void render(Graphics2D g) {

        if (fita == null) {
            return;
        }

        double eixoY = alturaDaFita();
        double centroX = Main.CAMPO_X + Main.CAMPO_W / 2.0;

        desenharTrilho(g, eixoY);

        // So as celulas visiveis: percorrer a fita inteira desenharia
        // dezenas de retangulos fora da tela a cada frame.
        int primeira = (int) Math.floor(rolagem) - 5;
        int ultima   = (int) Math.ceil(rolagem) + 5;

        for (int i = Math.max(0, primeira); i < Math.min(fita.length, ultima + 1); i++) {
            desenharCelula(g, i, centroX + (i - rolagem) * larguraCelula, eixoY);
        }

        desenharCabecote(g, centroX, eixoY);

        // Os simbolos voando pro chefe vao POR CIMA da fita: eles saem
        // dela, entao passar por baixo pareceria que sumiram.
        desenharVoadores(g);

        // O relogio fica ao LADO do cabecote, e nao acima da celula: em
        // cima da celula ele disputaria espaco com o proprio cabecote.
        if (!terminou()) {
            desenharRelogio(g,
                            centroX + larguraCelula * 0.85,
                            eixoY - alturaDoCabecote());
        }

        desenharProgresso(g, eixoY);
    }

    private void desenharTrilho(Graphics2D g, double eixoY) {

        g.setColor(new Color(10, 8, 20, 190));
        g.fillRect(Main.CAMPO_X, (int) (eixoY - 40), Main.CAMPO_W, 80);

        g.setColor(new Color(120, 110, 160, 120));
        g.drawLine(Main.CAMPO_X, (int) (eixoY - 40), Main.CAMPO_X + Main.CAMPO_W, (int) (eixoY - 40));
        g.drawLine(Main.CAMPO_X, (int) (eixoY + 40), Main.CAMPO_X + Main.CAMPO_W, (int) (eixoY + 40));
    }

    private void desenharCelula(Graphics2D g, int i, double cx, double eixoY) {

        int lado = larguraCelula - 6;
        int x0 = (int) (cx - lado / 2.0);
        int y0 = (int) (eixoY - lado / 2.0);

        boolean atual = (i == cabecote);
        boolean feita = (i < cabecote);

        // Celula ja resolvida some aos poucos: a fita atras do cabecote
        // vira historico, e nao pode competir por atencao com a da vez.
        Color fundo = feita ? new Color(26, 44, 30)
                            : (atual ? new Color(48, 32, 62) : new Color(24, 22, 40));

        g.setColor(fundo);
        g.fillRect(x0, y0, lado, lado);

        g.setColor(atual ? new Color(255, 220, 120) : new Color(110, 100, 140));
        g.drawRect(x0, y0, lado, lado);

        // O simbolo.
        g.setFont(new Font("Monospaced", Font.BOLD, atual ? 34 : 24));

        String simbolo = String.valueOf(fita[i]);
        int larg = g.getFontMetrics().stringWidth(simbolo);
        int alturaTexto = g.getFontMetrics().getAscent();

        if (feita) {
            g.setColor(new Color(110, 170, 120));
        } else if (atual) {
            g.setColor(flashErro > 0 ? new Color(255, 90, 90)
                                     : (flashAcerto > 0 ? new Color(140, 255, 160)
                                                        : Color.WHITE));
        } else {
            g.setColor(new Color(180, 175, 210));
        }

        g.drawString(simbolo, (int) (cx - larg / 2.0), (int) (eixoY + alturaTexto / 2.5));
    }

    /**
     * O relogio da celula: um arco que vai se fechando.
     *
     * Arco e nao barra porque ele fica ACIMA da celula, num espaco
     * quadrado, e porque a forma de relogio ja diz sozinha o que
     * significa — nao precisa de legenda.
     */
    private void desenharRelogio(Graphics2D g, double cx, double cy) {

        int r = 19;
        double frac = Math.max(0, Math.min(1, relogio / (double) relogioCheio));

        g.setColor(new Color(20, 16, 30));
        g.fillOval((int) (cx - r), (int) (cy - r), r * 2, r * 2);

        // Verde -> amarelo -> vermelho conforme aperta.
        Color c = (frac > 0.5) ? new Color(120, 230, 140)
                : (frac > 0.25) ? new Color(255, 220, 110)
                                : new Color(255, 90, 90);

        g.setColor(c);
        g.fillArc((int) (cx - r), (int) (cy - r), r * 2, r * 2, 90, (int) (-360 * frac));

        g.setColor(new Color(200, 195, 230));
        g.drawOval((int) (cx - r), (int) (cy - r), r * 2, r * 2);
    }

    /**
     * A capsula em volta do jogador.
     *
     * Ela e desenhada AQUI e nao no Player porque o cabecote e uma coisa
     * deste ataque; acabou o ataque, some. Fica so o contorno (nao um
     * retangulo cheio), senao o sprite do jogador ficaria escondido
     * dentro dela.
     */
    /**
     * A capsula do cabecote, desenhada ACIMA da celula atual, com uma
     * agulha apontando pra ela.
     *
     * A agulha nao e enfeite: com a capsula fora da fita, precisa ficar
     * obvio QUAL celula esta sob leitura. E tambem e o desenho certo —
     * numa maquina de Turing de verdade o cabecote paira sobre a fita, e
     * nao dentro dela.
     */
    private void desenharCabecote(Graphics2D g, double cx, double eixoY) {

        double cy = eixoY - alturaDoCabecote();

        int larg = larguraCelula + 16;
        int alt = larguraCelula + 30;

        Stroke anterior = g.getStroke();
        g.setStroke(new BasicStroke(3f));

        Color borda = flashErro > 0 ? new Color(255, 80, 80)
                                    : new Color(150, 220, 255);

        g.setColor(borda);
        g.drawRoundRect((int) (cx - larg / 2.0), (int) (cy - alt / 2.0), larg, alt, 14, 14);

        // "Pescoco" ligando a capsula ao topo do campo: deixa claro que
        // quem segura o jogador e a maquina, e nao ele que escolheu parar.
        g.drawLine((int) cx, (int) (cy - alt / 2.0), (int) cx, Main.CAMPO_Y + 60);

        // AGULHA: desce da capsula ate a borda de cima da celula lida.
        double topoDaCelula = eixoY - (larguraCelula - 6) / 2.0;

        g.setStroke(new BasicStroke(2f));
        g.drawLine((int) cx, (int) (cy + alt / 2.0), (int) cx, (int) topoDaCelula - 4);

        int[] px = { (int) cx, (int) cx - 6, (int) cx + 6 };
        int[] py = { (int) topoDaCelula, (int) topoDaCelula - 9, (int) topoDaCelula - 9 };

        g.fillPolygon(px, py, 3);

        g.setStroke(anterior);

        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.setColor(new Color(150, 220, 255));

        String rotulo = "CABEÇOTE";
        int largRotulo = g.getFontMetrics().stringWidth(rotulo);

        g.drawString(rotulo, (int) (cx - largRotulo / 2.0), (int) (cy - alt / 2.0 - 8));
    }

    /** Quantas celulas faltam, no rodape. */
    private void desenharProgresso(Graphics2D g, double eixoY) {

        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(new Color(220, 210, 240));

        String texto = terminou()
                     ? "PROGRAMA ACEITO"
                     : "DIGITE O SÍMBOLO   " + cabecote + " / " + tamanhoDaFita
                       + "        " + estadoAtual() + "   ·   reconhecendo 0\u207F1\u207F"
                       + "     (tiro travado)";

        int larg = g.getFontMetrics().stringWidth(texto);

        g.drawString(texto,
                     Main.CAMPO_X + Main.CAMPO_W / 2 - larg / 2,
                     (int) (eixoY + larguraCelula / 2.0 + 48));
    }
}
