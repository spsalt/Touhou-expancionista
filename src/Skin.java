package src;

import java.awt.Color;

/**
 * UM PERSONAGEM JOGAVEL: os dois sprites dele e a paleta dos tiros dele.
 *
 * Ate aqui o jogo tinha um estudante so, e os caminhos dos sprites e as
 * cores das balas estavam escritos a mao em dois lugares diferentes — o
 * Player pra desenhar e atirar, o Cutscene pros retratos. Trocar de
 * personagem seria caçar constante em arquivo.
 *
 * Uma skin junta tudo isso num objeto so, e a lista de skins e montada a
 * partir do game.properties por PREFIXO. Pra acrescentar um personagem
 * novo nao se mexe em codigo nenhum: escreve um bloco de chaves no
 * .properties, poe os dois PNG na pasta e ele aparece no seletor.
 *
 * POR QUE A COR DO TIRO FAZ PARTE DA SKIN
 * ---------------------------------------
 * Ela nao e enfeite. Os tres tiros do jogo (leque, ponteiro, ricochete)
 * se distinguem PELA COR no meio de uma tela cheia — e por isso a paleta
 * de cada personagem mantem os tres SEPARADOS entre si, em vez de pintar
 * tudo do mesmo tom. O laranja do Lucas e um laranja claro pro leque,
 * ambar pro ponteiro e vermelho-alaranjado pro ricochete: e a cor dele,
 * mas voce continua sabendo qual tiro e qual.
 *
 * A skin escolhida NAO faz parte da partida: ela vive no menu e sobrevive
 * ao reiniciarPartida(). Escolher personagem e uma decisao de antes de
 * jogar, e perde-la ao morrer seria so irritante.
 */
public class Skin {

    /** Nome curto, do jeito que aparece no seletor. */
    private final String nome;

    /** Nome que aparece na caixa de fala nas duas formas. */
    private final String nomeNaFala;
    private final String nomeNaFalaExpansivo;

    private final String sprite;
    private final String spriteExpansivo;

    /**
     * Onde fica o "centro" do desenho, de 0 a 1.
     *
     * Nao e o meio da imagem: e o ponto do desenho que deve coincidir com
     * a posicao real do jogador. Um sprite com moldura em volta (o do
     * Lucas expansivo tem) fica com o rosto deslocado dentro do PNG, e sem
     * isto a hitbox andaria fora da cara dele.
     */
    private final double centroX, centroY;
    private final double centroXExpansivo, centroYExpansivo;

    /** Tamanho do sprite expansivo em relacao ao normal. */
    private final double escalaDoExpansivo;

    /* --- a paleta --- */

    private final Color corDoLeque;
    private final Color corDoPonteiro;
    private final Color corDoRicochete;

    /** Cor do retrato na caixa de fala, nas duas formas. */
    private final Color corDaFala;
    private final Color corDaFalaExpansivo;

    /**
     * A COR DA TRANSFORMACAO: a cerimonia do "ESPANDAAAAA" inteira sai
     * dela — o veu, os fiapos de energia, o halo, o grito e a explosao.
     *
     * O roxo era a cor do estudante, nao a cor da mecanica. Com um
     * personagem laranja em cena, a cena mais importante do roteiro
     * continuava roxa e passava a falar de outra pessoa.
     */
    private final Color corDaAura;

    /**
     * @param prefixo o comeco das chaves no .properties, JA COM O PONTO
     *                no fim ("skin.lucas."). O ponto fica aqui e nao nas
     *                chaves porque e assim que o verificador reconhece uma
     *                chave montada em tempo de execucao — sem ele, todas
     *                as chaves de skin apareceriam como orfas.
     */
    public Skin(String prefixo,
                String nomePadrao, String falaPadrao, String falaExpPadrao,
                String spritePadrao, String spriteExpPadrao,
                Color lequePadrao, Color ponteiroPadrao, Color ricochetePadrao,
                Color falaCorPadrao, Color falaCorExpPadrao,
                Color auraPadrao) {

        this.nome = Config.getString(prefixo + "nome", nomePadrao);
        this.nomeNaFala = Config.getString(prefixo + "nomeNaFala", falaPadrao);
        this.nomeNaFalaExpansivo = Config.getString(prefixo + "nomeNaFalaExpansivo", falaExpPadrao);

        this.sprite = Config.getString(prefixo + "sprite", spritePadrao);
        this.spriteExpansivo = Config.getString(prefixo + "spriteExpansivo", spriteExpPadrao);

        this.centroX = Config.getDouble(prefixo + "centroX", 0.5);
        this.centroY = Config.getDouble(prefixo + "centroY", 0.5);
        this.centroXExpansivo = Config.getDouble(prefixo + "centroXExpansivo", 0.5);
        this.centroYExpansivo = Config.getDouble(prefixo + "centroYExpansivo", 0.5);

        this.escalaDoExpansivo = Config.getDouble(prefixo + "escalaDoExpansivo", 1.53);

        this.corDoLeque     = cor(prefixo + "corDoLeque", lequePadrao);
        this.corDoPonteiro  = cor(prefixo + "corDoPonteiro", ponteiroPadrao);
        this.corDoRicochete = cor(prefixo + "corDoRicochete", ricochetePadrao);

        this.corDaFala          = cor(prefixo + "corDaFala", falaCorPadrao);
        this.corDaFalaExpansivo = cor(prefixo + "corDaFalaExpansivo", falaCorExpPadrao);

        this.corDaAura = cor(prefixo + "corDaAura", auraPadrao);
    }

    /**
     * Le uma cor escrita como "R,G,B" no .properties.
     *
     * Texto e nao tres chaves separadas porque cor e UMA decisao: com
     * corDoLeque.r/.g/.b daria pra deixar duas atualizadas e uma nao, e o
     * resultado seria uma cor que ninguem escolheu.
     */
    private static Color cor(String chave, Color padrao) {

        String txt = Config.getString(chave, "");

        if (txt == null || txt.trim().isEmpty()) {
            return padrao;
        }

        String[] partes = txt.split(",");

        if (partes.length < 3) {
            return padrao;
        }

        try {
            return new Color(
                Math.max(0, Math.min(255, Integer.parseInt(partes[0].trim()))),
                Math.max(0, Math.min(255, Integer.parseInt(partes[1].trim()))),
                Math.max(0, Math.min(255, Integer.parseInt(partes[2].trim()))));

        } catch (NumberFormatException e) {
            // Config errado nao pode derrubar o jogo: cai no padrao e
            // segue. O sprite faltando ja e tratado assim no Assets.
            return padrao;
        }
    }

    /* =========================
            AS SKINS DO JOGO
       ========================= */

    /**
     * A lista de personagens, na ordem em que aparecem no seletor.
     *
     * Estatica e criada uma vez: os objetos sao so leitura e sao lidos
     * todo frame pelo Player. Recriar por frame seria lixo de graça.
     */
    private static Skin[] todas;

    /** Qual esta escolhida agora. Indice na lista acima. */
    private static int escolhida = 0;

    /** Ja carregou alguma vez? Ver o final deste metodo. */
    private static boolean jaCarregou = false;

    public static void carregar() {

        todas = new Skin[] {

            new Skin("skin.estudante.",
                     "Henrique", "Henrique", "Henrique",
                     "sprites/player/estudante.png",
                     "sprites/player/estudante_expansivo.png",
                     // TRES ROXOS, pela mesma regra do Lucas: a cor e o
                     // que separa um tiro do outro no meio da tela cheia.
                     new Color(198, 150, 255),   // leque: lilas claro
                     new Color(245, 130, 240),   // ponteiro: magenta
                     new Color(140, 70, 255),    // ricochete: violeta fundo
                     new Color(70, 120, 200),
                     new Color(70, 150, 220),
                     new Color(150, 70, 255)),   // aura roxa

            new Skin("skin.lucas.",
                     "Lucas", "Lucas", "Lucas Expansivo",
                     "sprites/player/lucas.png",
                     "sprites/player/lucas_expansivo.png",
                     // TRES LARANJAS, e nao um laranja tres vezes.
                     // Ver o cabecalho da classe: a cor e o que separa um
                     // tiro do outro quando a tela enche.
                     new Color(255, 150, 60),    // leque: laranja
                     new Color(255, 170, 20),    // ponteiro: ouro
                     new Color(240, 90, 25),     // ricochete: laranja fundo
                     new Color(200, 120, 30),
                     new Color(235, 145, 25),
                     new Color(255, 140, 30)),   // aura laranja

            new Skin("skin.chico.",
                     "Chico", "Chico", "Chico",
                     "sprites/player/chico.png",
                     "sprites/player/chico_expansivo.png",
                     new Color(120, 255, 110),   // leque: verde neon
                     new Color(205, 255, 70),    // ponteiro: verde-limao
                     new Color(20, 200, 80),     // ricochete: verde fundo
                     new Color(40, 170, 60),
                     new Color(50, 215, 60),
                     new Color(60, 255, 70))     // aura verde neon
        };

        // A ESCOLHA DO JOGADOR SOBREVIVE AO F5.
        //
        // O hot-reload chama isto de novo pra a paleta nova valer na hora.
        // Reler skin.escolhida ali jogaria fora o personagem que a pessoa
        // acabou de escolher no menu — ela mexeu numa cor e perdeu a
        // escolha, sem entender por que.
        if (!jaCarregou) {
            escolhida = Config.getInt("skin.escolhida", 0);
            jaCarregou = true;
        }

        escolhida = Math.max(0, Math.min(todas.length - 1, escolhida));

        Cutscene.aplicarSkin(todas[escolhida]);
    }

    /** A skin em uso. Nunca devolve null: carrega na primeira chamada. */
    public static Skin atual() {

        if (todas == null) {
            carregar();
        }

        return todas[escolhida];
    }

    public static Skin[] todas() {

        if (todas == null) {
            carregar();
        }

        return todas;
    }

    public static int getEscolhida() {
        return escolhida;
    }

    /**
     * Troca de personagem. O passo da a volta nas pontas, entao segurar
     * pra um lado so sempre acha o que voce quer.
     */
    public static void trocar(int passo) {

        if (todas == null) {
            carregar();
        }

        escolhida = ((escolhida + passo) % todas.length + todas.length) % todas.length;

        // Os retratos da conversa acompanham na mesma linha. Deixar isso
        // pro menu abriria a porta pra trocar de personagem em algum outro
        // lugar e as cutscenes continuarem mostrando o antigo.
        Cutscene.aplicarSkin(todas[escolhida]);
    }

    /* =========================
            GETTERS
       ========================= */

    public String getNome() {
        return nome;
    }

    public String getNomeNaFala() {
        return nomeNaFala;
    }

    public String getNomeNaFalaExpansivo() {
        return nomeNaFalaExpansivo;
    }

    public String getSprite() {
        return sprite;
    }

    public String getSpriteExpansivo() {
        return spriteExpansivo;
    }

    public double getCentroX() {
        return centroX;
    }

    public double getCentroY() {
        return centroY;
    }

    public double getCentroXExpansivo() {
        return centroXExpansivo;
    }

    public double getCentroYExpansivo() {
        return centroYExpansivo;
    }

    public double getEscalaDoExpansivo() {
        return escalaDoExpansivo;
    }

    public Color getCorDoLeque() {
        return corDoLeque;
    }

    public Color getCorDoPonteiro() {
        return corDoPonteiro;
    }

    public Color getCorDoRicochete() {
        return corDoRicochete;
    }

    public Color getCorDaFala() {
        return corDaFala;
    }

    public Color getCorDaFalaExpansivo() {
        return corDaFalaExpansivo;
    }

    public Color getCorDaAura() {
        return corDaAura;
    }

    /**
     * A mesma cor com outra saturacao e outro brilho.
     *
     * Serve pra montar uma FAMILIA a partir de uma cor so: o veu escuro, o
     * halo, o miolo quase branco do grito e os quatro tons da explosao sao
     * todos derivados da corDaAura por aqui.
     *
     * Trabalha em HSB e nao multiplicando o RGB direto porque multiplicar
     * RGB desloca o matiz: um roxo escurecido assim puxa pro azul, e um
     * laranja puxa pro vermelho. Em HSB o matiz e a unica coisa que nao se
     * mexe — que e justamente a identidade da cor.
     */
    public static Color variar(Color base, double fatorSaturacao, double fatorBrilho, int alfa) {

        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);

        float s = (float) Math.max(0, Math.min(1, hsb[1] * fatorSaturacao));
        float b = (float) Math.max(0, Math.min(1, hsb[2] * fatorBrilho));

        Color c = new Color(Color.HSBtoRGB(hsb[0], s, b));

        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                         Math.max(0, Math.min(255, alfa)));
    }
}
