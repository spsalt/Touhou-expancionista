package src;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Fundo rolante do campo de jogo, no estilo dos estagios de Touhou.
 *
 * Sao cinco camadas desenhadas nesta ordem:
 *
 *   1. FOTO rolando pra baixo, em loop  -> da a sensacao de estar avancando
 *   2. ESCURECIMENTO chapado             -> foto crua "come" as balas na leitura
 *   3. TINTA colorida pulsante           -> unifica a paleta e cria clima
 *   4. PARTICULAS subindo                -> profundidade (parallax barato)
 *   5. VINHETA nas bordas                -> puxa o olho pro centro
 *
 * O motivo das camadas 2/3/5 nao e enfeite: em bullet hell o jogador PRECISA
 * ler dezenas de projeteis por cima do fundo. Foto sem tratamento deixa o
 * jogo injogavel. Se quiser ver a foto original, e so zerar
 * "fundo.escurecimento" e "fundo.tintAlpha" no game.properties.
 *
 * Custo: a foto e reescalada UMA vez no construtor e a vinheta e desenhada
 * UMA vez numa imagem de memoria. Por frame so sobram os desenhos prontos.
 */
public class Background {

    /** Foto ja reescalada pra largura do campo. null = usa cor chapada. */
    private BufferedImage imagem;

    /**
     * A foto ANTERIOR, mantida viva durante a transicao.
     *
     * Trocar de cenario com um corte seco denuncia que houve uma "cena":
     * o jogo pisca e o jogador sente que saiu do mundo. Guardando a foto
     * velha e desvanecendo ela por cima da nova, a passagem do caminho pra
     * sala 7 acontece SEM interromper nada — e o que a serie faz entre os
     * trechos de um estagio.
     */
    private BufferedImage imagemAnterior;

    /** Ticks restantes da transicao. 0 = so a foto atual. */
    private int transicao = 0;

    /** Duracao total da transicao em andamento, pro calculo do alfa. */
    private int transicaoTotal = 1;

    /**
     * Deslocamento proprio da foto antiga.
     *
     * Ela continua ROLANDO durante o fade, com o mesmo offset da nova.
     * Congelar a antiga faria a imagem que some parecer uma foto colada
     * na tela, e o corte que a gente estava tentando evitar voltaria
     * disfarcado de transicao.
     */
    private double offsetAnterior = 0;

    /** Vinheta pre-desenhada. null = desligada. */
    private BufferedImage vinheta;

    /** Quanto a foto ja rolou, em pixels. Cresce sempre e da a volta. */
    private double offset = 0;

    /** Cronometro proprio, usado no pulso da tinta. */
    private int t = 0;

    private final Random rng = new Random();

    private Particula[] particulas;

    /* --- ajustes lidos do game.properties --- */

    private double velocidadeScroll;
    private int escurecimento;
    private Color tinta;
    private int tintAlpha;
    private double pulsoAmplitude;
    private double pulsoPeriodo;

    public Background() {
        carregarConfig();
    }

    /** (Re)le os ajustes e reconstroi as camadas. Chamado no F5. */
    public void carregarConfig() {

        this.velocidadeScroll = Config.getDouble("fundo.velocidadeScroll", 0.35);
        this.escurecimento    = limitarAlpha(Config.getInt("fundo.escurecimento", 150));
        this.tintAlpha        = limitarAlpha(Config.getInt("fundo.tintAlpha", 70));
        this.pulsoAmplitude   = Config.getDouble("fundo.pulsoAmplitude", 25);
        this.pulsoPeriodo     = Math.max(1, Config.getDouble("fundo.pulsoPeriodo", 240));

        this.tinta = new Color(
            limitarAlpha(Config.getInt("fundo.tintR", 40)),
            limitarAlpha(Config.getInt("fundo.tintG", 30)),
            limitarAlpha(Config.getInt("fundo.tintB", 90))
        );

        carregarImagem();
        criarVinheta(limitarAlpha(Config.getInt("fundo.vinheta", 150)));
        criarParticulas();
    }

    /**
     * Carrega a foto, reescala pra largura do campo e prepara o bloco que
     * vai rolar em loop.
     *
     * Reescalar aqui (e nao no render) e o que segura o FPS: escalar uma
     * imagem grande 60 vezes por segundo derruba o jogo.
     */
    /**
     * Troca o cenario em tempo de execucao (usado quando comeca a luta de
     * chefe, pra o fundo virar a sala 7). Passar null volta pro padrao.
     */
    public void trocarImagem(String caminho) {

        // Pedir o cenario que ja esta no ar nao faz nada. As fases chamam
        // isso dentro do tick(), ou seja, sessenta vezes por segundo —
        // sem esta guarda a transicao reiniciaria a cada frame e o fundo
        // ficaria congelado no meio do fade pra sempre.
        if (caminho == null ? caminhoForcado == null : caminho.equals(caminhoForcado)) {
            return;
        }

        this.imagemAnterior = this.imagem;
        this.offsetAnterior = this.offset;

        this.caminhoForcado = caminho;
        carregarImagem();

        // So faz sentido desvanecer se havia alguma coisa antes.
        if (imagemAnterior != null) {
            transicaoTotal = Math.max(1, Config.getInt("fundo.ticksDeTransicao", 90));
            transicao = transicaoTotal;
        } else {
            transicao = 0;
        }

        // A rolagem NAO zera: zerar daria um salto vertical no exato
        // momento da troca, que e o corte que estamos evitando.
    }

    /** true enquanto o cenario ainda esta desvanecendo pro novo. */
    public boolean emTransicao() {
        return transicao > 0;
    }

    /** Cenario pedido em runtime. null = usa o do game.properties. */
    private String caminhoForcado = null;

    private void carregarImagem() {

        String caminho = (caminhoForcado != null)
                       ? caminhoForcado
                       : Config.getString("fundo.imagem", "sprites/ambient/dco.png");

        BufferedImage original = Assets.get(caminho);

        if (original == null || original.getWidth() <= 0) {
            imagem = null;
            return;
        }

        int largura = Main.CAMPO_W;
        int altura = Math.max(1, original.getHeight() * largura / original.getWidth());

        BufferedImage escalada = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = escalada.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, largura, altura, null);
        g.dispose();

        imagem = fecharOCiclo(escalada, Config.getInt("fundo.faixaDeEmenda", 250));
    }

    /**
     * Deixa a foto emendavel com ela mesma, pro loop nao ter costura.
     *
     * O PROBLEMA: a foto nao fecha sozinha. Emendando o rodape (asfalto) no
     * topo (ceu) aparece um corte horizontal gritante a cada volta.
     *
     * A SOLUCAO: cortar as ultimas 'faixa' linhas e, no lugar delas,
     * dissolver o rodape da foto por cima do topo dela:
     *
     *     bloco[y] = mistura( foto[y + alturaBloco], foto[y], y / faixa )
     *
     * O que isso garante:
     *   - a PRIMEIRA linha do bloco vale foto[alturaBloco]
     *   - a ULTIMA linha do bloco vale foto[alturaBloco - 1]
     * Ou seja: ao dar a volta, a ultima linha cai exatamente na linha
     * seguinte dela na foto original. A emenda vira uma transicao qualquer
     * do meio da foto, e some.
     *
     * (Medido nesta foto: a descontinuidade cai de 84.1 pra 5.68, sendo que
     * duas linhas vizinhas quaisquer ja diferem 5.45 entre si.)
     *
     * De quebra o efeito visual e bom: o asfalto se dissolve na neblina do
     * ceu, que e bem a cara de um estagio de Touhou.
     *
     * @param faixa altura da dissolucao em pixels; 0 desliga o tratamento
     */
    private static BufferedImage fecharOCiclo(BufferedImage foto, int faixa) {

        int largura = foto.getWidth();
        int altura = foto.getHeight();

        // Faixa invalida: devolve a foto crua (o corte vai aparecer, mas
        // e melhor que travar ou gerar imagem de tamanho zero).
        if (faixa <= 0 || faixa >= altura) {
            return foto;
        }

        int alturaBloco = altura - faixa;

        BufferedImage bloco = new BufferedImage(largura, alturaBloco, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < alturaBloco; y++) {

            // Fora da faixa e copia direta.
            if (y >= faixa) {

                for (int x = 0; x < largura; x++) {
                    bloco.setRGB(x, y, foto.getRGB(x, y));
                }

                continue;
            }

            // Dentro da faixa: 0 = so o rodape, 1 = so o topo.
            double peso = y / (double) faixa;

            for (int x = 0; x < largura; x++) {
                bloco.setRGB(x, y, misturar(foto.getRGB(x, y + alturaBloco),
                                            foto.getRGB(x, y),
                                            peso));
            }
        }

        return bloco;
    }

    /** Mistura duas cores RGB: peso 0 devolve cor1, peso 1 devolve cor2. */
    private static int misturar(int cor1, int cor2, double peso) {

        int r = (int) (((cor1 >> 16) & 0xFF) * (1 - peso) + ((cor2 >> 16) & 0xFF) * peso);
        int g = (int) (((cor1 >> 8)  & 0xFF) * (1 - peso) + ((cor2 >> 8)  & 0xFF) * peso);
        int b = (int) (( cor1        & 0xFF) * (1 - peso) + ( cor2        & 0xFF) * peso);

        return (r << 16) | (g << 8) | b;
    }

    /** Desenha a vinheta uma unica vez numa imagem transparente. */
    private void criarVinheta(int forca) {

        if (forca <= 0) {
            vinheta = null;
            return;
        }

        vinheta = new BufferedImage(Main.CAMPO_W, Main.CAMPO_H, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = vinheta.createGraphics();

        // Gradiente radial: transparente no centro, escuro nas pontas.
        RadialGradientPaint pintura = new RadialGradientPaint(
            new Point2D.Float(Main.CAMPO_W / 2f, Main.CAMPO_H / 2f),
            Math.max(Main.CAMPO_W, Main.CAMPO_H) * 0.75f,
            new float[] { 0f, 0.55f, 1f },
            new Color[] {
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, forca / 3),
                new Color(0, 0, 0, forca)
            }
        );

        g.setPaint(pintura);
        g.fillRect(0, 0, Main.CAMPO_W, Main.CAMPO_H);
        g.dispose();
    }

    private void criarParticulas() {

        int quantidade = Math.max(0, Config.getInt("fundo.particulas", 45));

        particulas = new Particula[quantidade];

        for (int i = 0; i < quantidade; i++) {
            particulas[i] = new Particula(rng, true);
        }
    }

    /* =========================
            LOGICA
       ========================= */

    public void tick() {

        offset += velocidadeScroll;

        // Evita o offset crescer pra sempre e estourar a precisao do double
        // numa partida longa.
        if (imagem != null && offset >= imagem.getHeight()) {
            offset -= imagem.getHeight();
        }

        // A foto que esta saindo continua rolando junto, no mesmo ritmo.
        if (transicao > 0) {

            offsetAnterior += velocidadeScroll;

            if (imagemAnterior != null && offsetAnterior >= imagemAnterior.getHeight()) {
                offsetAnterior -= imagemAnterior.getHeight();
            }

            transicao--;

            // Acabou: solta a referencia pra o garbage collector poder
            // recolher a foto antiga (elas sao grandes).
            if (transicao == 0) {
                imagemAnterior = null;
            }
        }

        for (int i = 0; i < particulas.length; i++) {
            particulas[i].tick(rng);
        }

        t++;
    }

    /* =========================
            RENDER
       ========================= */

    public void render(Graphics2D g) {

        desenharFoto(g);

        // Camada 2: escurece tudo por igual.
        if (escurecimento > 0) {
            g.setColor(new Color(0, 0, 0, escurecimento));
            g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
        }

        // Camada 3: tinta colorida, respirando devagar.
        int alpha = limitarAlpha(
            (int) (tintAlpha + pulsoAmplitude * Math.sin(2 * Math.PI * t / pulsoPeriodo))
        );

        if (alpha > 0) {
            g.setColor(new Color(tinta.getRed(), tinta.getGreen(), tinta.getBlue(), alpha));
            g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
        }

        // Camada 4: particulas subindo.
        for (int i = 0; i < particulas.length; i++) {
            particulas[i].render(g);
        }

        // Camada 5: vinheta.
        if (vinheta != null) {
            g.drawImage(vinheta, Main.CAMPO_X, Main.CAMPO_Y, null);
        }
    }

    /**
     * Desenha a foto repetida verticalmente ate cobrir o campo.
     * Comeca uma altura ACIMA do topo, entao nao existe frame nenhum em que
     * apareca um buraco entre uma copia e a seguinte.
     */
    private void desenharFoto(Graphics2D g) {

        if (imagem == null && imagemAnterior == null) {
            g.setColor(new Color(18, 18, 34));
            g.fillRect(Main.CAMPO_X, Main.CAMPO_Y, Main.CAMPO_W, Main.CAMPO_H);
            return;
        }

        // A NOVA vai por baixo, opaca. A ANTIGA vai por cima, sumindo.
        //
        // Nesta ordem o fade nunca deixa buraco: em qualquer instante ha
        // uma imagem opaca cobrindo o campo. Fazendo o contrario (a nova
        // aparecendo por cima da antiga) o meio da transicao mostraria as
        // duas meio transparentes, e o campo escureceria no caminho.
        desenharCamada(g, imagem, offset, 1f);

        if (transicao > 0 && imagemAnterior != null) {

            float alpha = transicao / (float) transicaoTotal;

            desenharCamada(g, imagemAnterior, offsetAnterior, alpha);
        }
    }

    /** Desenha uma foto repetida verticalmente, com a opacidade pedida. */
    private void desenharCamada(Graphics2D g, BufferedImage img, double desloc, float alpha) {

        if (img == null || alpha <= 0) {
            return;
        }

        java.awt.Composite anterior = g.getComposite();

        if (alpha < 1f) {
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, alpha));
        }

        int altura = img.getHeight();
        int inicio = Main.CAMPO_Y + (int) desloc - altura;

        for (int y = inicio; y < Main.CAMPO_Y + Main.CAMPO_H; y += altura) {
            g.drawImage(img, Main.CAMPO_X, y, null);
        }

        g.setComposite(anterior);
    }

    /** Prende um valor na faixa 0..255 (que e o que Color aceita). */
    private static int limitarAlpha(int valor) {
        return Math.max(0, Math.min(255, valor));
    }

    /* =====================================================
       PARTICULA - poeira luminosa subindo no fundo
       =====================================================
       Sobe mais rapido que a foto desce, entao os dois movimentos se
       somam e dao sensacao de profundidade. E o parallax mais barato
       que existe: nao precisa de segunda imagem.
    */
    private static class Particula {

        private double x, y;
        private double velocidade;
        private double tamanho;
        private int alpha;

        /** Fase propria, pro bamboleio de cada uma ser diferente. */
        private double fase;

        Particula(Random rng, boolean espalharNaTela) {
            reiniciar(rng, espalharNaTela);
        }

        /**
         * @param espalharNaTela true = nasce em qualquer altura (usado so na
         *        criacao, pra tela nao comecar vazia); false = nasce embaixo
         */
        private void reiniciar(Random rng, boolean espalharNaTela) {

            double velMin = Config.getDouble("fundo.particulaVelocidadeMin", 0.4);
            double velMax = Config.getDouble("fundo.particulaVelocidadeMax", 1.6);
            double tamMin = Config.getDouble("fundo.particulaTamanhoMin", 2);
            double tamMax = Config.getDouble("fundo.particulaTamanhoMax", 6);

            this.x = Main.CAMPO_X + rng.nextDouble() * Main.CAMPO_W;
            this.y = espalharNaTela
                   ? Main.CAMPO_Y + rng.nextDouble() * Main.CAMPO_H
                   : Main.CAMPO_Y + Main.CAMPO_H + rng.nextDouble() * 40;

            this.velocidade = velMin + rng.nextDouble() * Math.max(0, velMax - velMin);
            this.tamanho    = tamMin + rng.nextDouble() * Math.max(0, tamMax - tamMin);
            this.fase       = rng.nextDouble() * Math.PI * 2;

            // Particula pequena = mais apagada: reforca a ideia de estar longe.
            this.alpha = 30 + (int) (rng.nextDouble() * 70);
        }

        void tick(Random rng) {

            y -= velocidade;
            x += Math.sin((y + fase) * 0.02) * 0.4;   // bamboleio suave

            if (y < Main.CAMPO_Y - 10) {
                reiniciar(rng, false);
            }
        }

        void render(Graphics2D g) {

            g.setColor(new Color(200, 220, 255, alpha));
            g.fillOval((int) (x - tamanho / 2), (int) (y - tamanho / 2),
                       (int) tamanho, (int) tamanho);
        }
    }

    /* =========================
            GETTERS E SETTERS
       ========================= */

    public double getOffset() {
        return offset;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public double getVelocidadeScroll() {
        return velocidadeScroll;
    }

    public void setVelocidadeScroll(double velocidadeScroll) {
        this.velocidadeScroll = velocidadeScroll;
    }

    public int getEscurecimento() {
        return escurecimento;
    }

    public void setEscurecimento(int escurecimento) {
        this.escurecimento = limitarAlpha(escurecimento);
    }
}
