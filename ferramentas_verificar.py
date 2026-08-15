"""Verificador estatico (nao temos JDK aqui): sintaxe, imports, @Override,
visibilidade entre pacotes, chaves de config orfas/faltando, assets."""
import os,re,glob,javalang,sys
from collections import defaultdict

erros=[];avisos=[]
arqs=[os.path.join(r,f) for r,_,fs in os.walk('src') for f in fs if f.endswith('.java')]
arvores={};fonte={}

def sem_comentarios(s):
    s=re.sub(r'/\*.*?\*/','',s,flags=re.S)
    s=re.sub(r'//[^\n]*','',s)
    return re.sub(r'"(\\.|[^"\\])*"','""',s)

for a in arqs:
    s=open(a,encoding='utf-8').read();fonte[a]=s
    try: arvores[a]=javalang.parse.parse(s)
    except Exception as e: erros.append(f"SINTAXE {a}: {e}")

# ---- mapa de classes -> pacote ----
pacote={};classes={}
for a,t in arvores.items():
    pk=t.package.name if t.package else ''
    for ty in t.types:
        pacote[ty.name]=pk; classes[ty.name]=(a,ty)

# ---- imports faltando ----
for a,t in arvores.items():
    pk=t.package.name if t.package else ''
    imp={i.path.split('.')[-1] for i in t.imports}
    # import com .* traz o pacote inteiro
    pacotes_curinga={i.path for i in t.imports if getattr(i,'wildcard',False)}
    corpo=sem_comentarios(fonte[a])
    locais={ty.name for ty in t.types}
    for nome,pkn in pacote.items():
        if nome in locais or nome in imp or pkn==pk or pkn in pacotes_curinga: continue
        if re.search(r'\b%s\b'%nome,corpo) and not re.search(r'\b%s\.%s\b'%(pkn.replace('.','\\.'),nome),corpo):
            erros.append(f"IMPORT {a}: usa {nome} ({pkn}) sem importar")

# ---- @Override valido ----
def metodos(nome):
    while nome in classes:
        _,ty=classes[nome]
        for m in ty.methods: yield m.name,len(m.parameters)
        ext=getattr(ty,'extends',None)
        nome=ext.name if ext and hasattr(ext,'name') else None
# Metodos de Object: sao os unicos que uma classe SEM pai pode sobrescrever.
DE_OBJECT={('toString',0),('hashCode',0),('equals',1),('clone',0),('finalize',0)}

for a,t in arvores.items():
    for ty in t.types:
        ext=getattr(ty,'extends',None)
        pai=ext.name if ext and hasattr(ext,'name') else None

        # Com interface no meio nao da pra resolver sem tabela de tipos:
        # o metodo pode vir de la. Melhor calar do que gritar errado.
        if getattr(ty,'implements',None): continue

        if pai is None:
            # Classe sem pai. @Override so vale pros metodos de Object —
            # foi assim que um @Override invalido passou batido numa
            # classe nova (Explosao), que nao herda de ninguem.
            for m in getattr(ty,'methods',[]):
                if any(getattr(an,'name','')=='Override' for an in m.annotations):
                    if (m.name,len(m.parameters)) not in DE_OBJECT:
                        erros.append(f"OVERRIDE {a}: {ty.name}.{m.name}/{len(m.parameters)}"
                                     f" tem @Override mas a classe nao herda de nada")
            continue

        if pai not in classes: continue
        herdados=set(metodos(pai))
        for m in getattr(ty,'methods',[]):
            if any(getattr(an,'name','')=='Override' for an in m.annotations):
                if (m.name,len(m.parameters)) not in herdados:
                    erros.append(f"OVERRIDE {a}: {ty.name}.{m.name}/{len(m.parameters)} nao existe em {pai}")

# ---- visibilidade entre pacotes: campo/metodo estatico acessado de fora ----
membros={}
tipoDoCampo={}          # "Classe.campo" -> nome do tipo, pra resolver Main.player.x
for n,(a,ty) in classes.items():
    d={}
    for f in getattr(ty,'fields',[]):
        for dec in f.declarators:
            d[dec.name]=set(f.modifiers)
            tn=getattr(f.type,'name',None)
            if tn: tipoDoCampo[n+'.'+dec.name]=tn
    for m in getattr(ty,'methods',[]): d[m.name]=set(m.modifiers)
    membros[n]=d

def temMembro(cls,mem):
    """Procura o membro subindo a cadeia de heranca."""
    vistos=set()
    while cls in classes and cls not in vistos:
        vistos.add(cls)
        if mem in membros.get(cls,{}): return True
        ext=getattr(classes[cls][1],'extends',None)
        cls=ext.name if ext and hasattr(ext,'name') else None
    return False
for a,t in arvores.items():
    pk=t.package.name if t.package else ''
    corpo=sem_comentarios(fonte[a])
    for cls,d in membros.items():
        if pacote[cls]==pk: continue
        for mem,mods in d.items():
            if 'public' in mods: continue
            if re.search(r'\b%s\s*\.\s*%s\b'%(cls,mem),corpo):
                erros.append(f"VISIBILIDADE {a}: {cls}.{mem} nao e public")

# ---- acesso estatico a membro que nao existe (Main.foo(), Som.BAR) ----
# So vale pra classes usadas pelo NOME (acesso estatico); variavel de
# instancia nao da pra resolver sem tabela de tipos, entao fica de fora.
for a,t in arvores.items():
    corpo=sem_comentarios(fonte[a])
    for cls,d in membros.items():
        if cls in {ty.name for ty in t.types}: continue
        for mem in set(re.findall(r'\b%s\s*\.\s*(\w+)'%cls,corpo)):
            if mem in d or mem in {'class','this'}: continue
            # enum/classe aninhada com o mesmo nome tambem conta
            if not temMembro(cls,mem) and not re.search(r'\b(enum|class|interface)\s+%s\b'%mem, sem_comentarios(fonte[classes[cls][0]])):
                erros.append(f"MEMBRO {a}: {cls}.{mem} nao existe")

        # segundo salto: Classe.campo.metodo() — resolve pelo tipo do campo.
        # Cobre o padrao mais comum do projeto (Main.player.x, Main.fundo.y).
        for campo,metodo in re.findall(r'\b%s\s*\.\s*(\w+)\s*\.\s*(\w+)'%cls,corpo):
            alvo=tipoDoCampo.get(cls+'.'+campo)
            if alvo and alvo in classes and not temMembro(alvo,metodo):
                erros.append(f"MEMBRO {a}: {cls}.{campo} e {alvo}, que nao tem \"{metodo}\"")

# ---- classe do JDK usada sem import (java.awt.Font, java.util.Random...) ----
# Lista curta e explicita: so o que este projeto de fato usa. Um "import
# java.awt.*" cobre tudo do pacote, entao o teste respeita curinga.
JDK = {
 'Color':'java.awt','Font':'java.awt','Graphics2D':'java.awt','Rectangle':'java.awt',
 'BasicStroke':'java.awt','Stroke':'java.awt','Composite':'java.awt',
 'AlphaComposite':'java.awt','RenderingHints':'java.awt','Point2D':'java.awt.geom',
 'AffineTransform':'java.awt.geom','BufferedImage':'java.awt.image',
 'Random':'java.util','ArrayList':'java.util','HashMap':'java.util',
 'Map':'java.util','List':'java.util','File':'java.io','IOException':'java.io',
}
for a,t in arvores.items():
    corpo=sem_comentarios(fonte[a])
    imp={i.path.split('.')[-1] for i in t.imports}
    curinga={i.path for i in t.imports if getattr(i,'wildcard',False)}
    locais={ty.name for ty in t.types}
    for nome,pk in JDK.items():
        if nome in imp or nome in locais or pk in curinga: continue
        if re.search(r'\b(new\s+%s\s*[(<]|%s\s*\.\s*[A-Z_]|[(,<]\s*%s\s+\w|^\s*(?:private|public|protected|static|final|\s)*%s\s+\w+\s*[=;])'
                     %(nome,nome,nome,nome), corpo, re.M):
            # java.lang.* nao precisa de import
            if not re.search(r'\b%s\s*\.\s*%s\b'%(pk.replace('.','\\.'),nome),corpo):
                erros.append(f"IMPORT {a}: usa {nome} sem importar {pk}.{nome}")

# ---- variavel chamando metodo que o tipo dela nao tem ----
# Pega o caso chefeAtual.abandonar() com chefeAtual declarado como Enemy.
#
# Cobre CAMPOS e VARIAVEIS LOCAIS. Os locais entraram depois: um teste com
# erro plantado (b.setSpriteXX() numa IntegralBullet local) passou batido,
# porque so os campos eram olhados — e a maior parte do codigo de spell
# card trabalha em cima de variaveis locais, justamente onde erro de nome
# de metodo e mais facil de cometer.
for a,t in arvores.items():
    corpo=sem_comentarios(fonte[a])
    for ty in t.types:
        meus={}
        for f in getattr(ty,'fields',[]):
            tn=getattr(f.type,'name',None)
            if tn:
                for dec in f.declarators: meus[dec.name]=tn
        # Declaracoes locais: "Tipo nome = ..."
        #
        # NOME AMBIGUO E DESCARTADO. O escopo aqui e o ARQUIVO inteiro, e
        # nao o metodo — entao um "Enemy e = ..." num metodo faria o
        # "e.getKeyCode()" do keyPressed(KeyEvent e) virar erro falso, que
        # foi exatamente o que aconteceu na primeira versao deste teste.
        # Quando o mesmo nome aparece com tipos diferentes no arquivo, a
        # gente simplesmente nao opina sobre ele.
        locais={}
        ambiguos=set()

        for tn,nome in re.findall(r'(?<![.\w])([A-Z]\w+)\s+(\w+)\s*=',corpo):
            if tn not in classes: continue
            if nome in locais and locais[nome]!=tn: ambiguos.add(nome)
            locais[nome]=tn

        # Parametro de metodo com o mesmo nome tambem torna ambiguo.
        for nome in re.findall(r'\(\s*(?:final\s+)?[A-Z]\w+(?:<[^>]*>)?\s+(\w+)\s*[,)]',corpo):
            if nome in locais: ambiguos.add(nome)

        for nome,tn in locais.items():
            if nome not in ambiguos and nome not in meus:
                meus[nome]=tn
        for campo,metodo in re.findall(r'(?<![.\w])(\w+)\s*\.\s*(\w+)\s*\(',corpo):
            alvo=meus.get(campo)
            if alvo and alvo in classes and not temMembro(alvo,metodo):
                erros.append(f"MEMBRO {a}: {campo} e {alvo}, que nao tem \"{metodo}\"")

# ---- variavel usada sem NUNCA ter sido declarada no arquivo ----
#
# Pega o caso "rng.nextDouble()" numa classe que nao tem campo rng: eu
# copiei o padrao de outro spell card e esqueci o campo junto. O javac
# grita na hora, mas aqui nao havia nada olhando pra isso.
#
# CONSERVADOR de proposito: so acusa quando o nome nao aparece em NENHUMA
# forma de declaracao no arquivo inteiro (campo, local, parametro, for,
# catch, lambda). Escopo de verdade exigiria uma tabela de simbolos; sem
# ela, "nunca declarado em lugar nenhum" e o unico veredito seguro.
RESERVADAS={'this','super','new','return','if','else','for','while','switch','case',
            'break','continue','try','catch','finally','throw','do','instanceof',
            'true','false','null','int','double','float','boolean','char','long','void'}

for a,t in arvores.items():
    corpo=sem_comentarios(fonte[a])

    declarados=set(RESERVADAS)
    declarados|= {ty.name for ty in t.types}
    declarados|= set(classes)
    declarados|= {i.path.split('.')[-1] for i in t.imports}

    # Qualquer "Tipo nome" (campo, local, parametro, for-each, catch).
    # O tipo pode vir QUALIFICADO (java.util.List) e com generico
    # (List<Clip>, Map.Entry<String, Clip[]>) — foi por nao aceitar essas
    # duas formas que a primeira versao acusou meia duzia de falsos.
    for nome in re.findall(r'(?<![.\w])(?:[a-z]\w*\s*\.\s*)*[A-Z]\w*'
                           r'(?:\s*\.\s*[A-Z]\w*)*'
                           r'(?:\s*<[^;()=]*>)?(?:\s*\[\s*\])?\s+(\w+)\s*[=;,):]', corpo):
        declarados.add(nome)
    for nome in re.findall(r'(?<![.\w])(?:int|double|float|boolean|char|long|byte|short|var)'
                           r'(?:\s*\[\s*\])?\s+(\w+)', corpo):
        declarados.add(nome)
    # lambda de um parametro: x -> ...
    for nome in re.findall(r'\(?\s*(\w+)\s*\)?\s*->', corpo):
        declarados.add(nome)
    # PREFIXO DE PACOTE (src.Explosao.vermelha): "src" nao e variavel.
    # Reconhecido por vir seguido de um nome que comeca com maiuscula.
    for nome in re.findall(r'(?<![.\w])(\w+)\s*\.\s*[A-Z]', corpo):
        declarados.add(nome)
    # Tipo com nome MINUSCULO: o projeto tem a classe "phase1", entao
    # "phase1 fase;" nao casa com a regra do tipo-comeca-com-maiuscula.
    # Aqui as classes conhecidas valem como tipo independente da caixa.
    for cls in classes:
        for nome in re.findall(r'(?<![.\w])%s(?:\s*<[^;()=]*>)?(?:\s*\[\s*\])?\s+(\w+)\s*[=;,):]'
                               % re.escape(cls), corpo):
            declarados.add(nome)

    usados=set()
    for nome in re.findall(r'(?<![.\w])([a-z]\w*)\s*\.\s*\w+\s*\(', corpo):
        usados.add(nome)

    for nome in sorted(usados - declarados):
        erros.append(f"VARIAVEL {a}: \"{nome}\" e usada mas nao foi declarada em lugar nenhum do arquivo")

# ---- toda lista publica do Main tem que estar FIADA no loop ----
# Esqueci de ligar uma lista nova uma vez e o efeito simplesmente nao
# aparecia, sem erro nenhum. Agora e verificado.
if 'src/Main.java' in fonte:
    m=fonte['src/Main.java']
    tick   = m[m.index('private void tickDoJogo'):m.index('private void colidirBalasComInimigos')]
    # Do render() em diante, e nao so do renderCena(): nem toda lista e
    # desenhada dentro da cena. Os estouros de transformacao, por exemplo,
    # sao desenhados DEPOIS da caixa de dialogo de proposito (por baixo
    # dela eles ficavam invisiveis), e isso acontece no render() de cima.
    render = m[m.index('private void render(Graphics2D'):]
    rein   = m[m.index('public static void reiniciarPartida'):]
    for tipo,nome in re.findall(r'public static ArrayList<(\w+)>\s+(\w+)\s*=',m):
        if nome+'.get(i).tick()' not in tick:
            erros.append(f"FIACAO Main.{nome}: nunca recebe tick()")
        if nome+'.removeIf' not in tick:
            erros.append(f"FIACAO Main.{nome}: nunca e limpa (removeIf)")
        if nome+'.get(i).render' not in render:
            erros.append(f"FIACAO Main.{nome}: nunca e desenhada")
        if nome+'.clear()' not in rein:
            erros.append(f"FIACAO Main.{nome}: nao e zerada em reiniciarPartida()")

# ---- gatilhos de cutscene: disparados por alguma fala E tratados ----
# O fluxo de chefe entrando/transformando depende inteiramente disso.
# Um gatilho declarado e nunca disparado (ou disparado e nunca tratado)
# nao da erro de compilacao: so faz a cena nao acontecer.
if 'src/Cutscene.java' in fonte and 'src/phases/phase1.java' in fonte:
    cut = sem_comentarios(fonte['src/Cutscene.java'])
    fase = sem_comentarios(fonte['src/phases/phase1.java'])
    m = re.search(r'public enum Gatilho \{(.*?)\n    \}', cut, re.S)
    if m:
        for nome in re.findall(r'^\s*([A-Z_]+)\s*,?\s*$', m.group(1), re.M):
            if nome == 'NENHUM':
                continue
            if not re.search(r'\.com\(Gatilho\.%s\)' % nome, cut):
                erros.append(f"GATILHO {nome}: nenhuma fala dispara")
            if ('Gatilho.' + nome) not in fase:
                erros.append(f"GATILHO {nome}: a fase nunca trata")

# ---- config: chaves usadas x definidas ----
props={}
for ln in open('config/game.properties',encoding='utf-8'):
    ln=ln.strip()
    if ln and not ln.startswith('#') and '=' in ln: props[ln.split('=',1)[0].strip()]=ln.split('=',1)[1]
usadas=set()
for a,s in fonte.items():
    usadas|=set(re.findall(r'Config\.get\w+\(\s*"([^"]+)"',s))
# Chave montada em tempo de execucao (Config.getX("som.ganho." + nome)):
# o literal termina em ponto e vale como PREFIXO. Sem isto, o prefixo
# apareceria como chave faltando e todas as chaves reais como orfas.
prefixos={k for k in usadas if k.endswith('.')}
usadas-=prefixos
def coberta(k): return any(k.startswith(p) for p in prefixos)
for k in sorted(usadas-set(props)): erros.append(f"CONFIG faltando no properties: {k}")
for k in sorted(set(props)-usadas):
    if not coberta(k): avisos.append(f"CONFIG orfa (no properties, ninguem le): {k}")

# ---- ganho por som: a chave tem que casar com um arquivo REAL ----
# "som.ganho.se_plst00" so faz efeito se existir audio/se_plst00.wav. Um
# erro de digitacao aqui nao quebra nada: o som simplesmente continua no
# volume geral, e a linha no properties parece estar funcionando.
if 'src/Som.java' in fonte:
    som = fonte['src/Som.java']
    for k in props:
        if not k.startswith('som.ganho.'): continue
        nome = k[len('som.ganho.'):]
        if not os.path.exists('audio/%s.wav' % nome):
            erros.append(f"SOM ganho sem arquivo: {k} (nao existe audio/{nome}.wav)")
        elif ('audio/%s.wav' % nome) not in som:
            avisos.append(f"SOM ganho de um arquivo que o Som.java nao usa: {k}")

# ---- assets citados ----
#
# Caminho SEM extensao e um PREFIXO de sequencia de quadros: o codigo
# monta o nome final com "_0.png", "_1.png"... (ver SeguidorBullet). Nesse
# caso a checagem e "existe pelo menos um quadro?" — checar o prefixo como
# se fosse arquivo dava erro falso.
def assetOk(cam):
    if os.path.exists(cam): return True
    if not re.search(r'\.\w+$', cam):
        return bool(glob.glob(cam+'_*.png'))
    return False

for a,s in fonte.items():
    for cam in re.findall(r'"((?:sprites|audio)/[^"]+)"',s):
        if not assetOk(cam): erros.append(f"ASSET some: {cam} (citado em {a})")
for k,v in props.items():
    if re.match(r'^(sprites|audio)/',v) and not assetOk(v):
        erros.append(f"ASSET some: {v} (properties: {k})")

print(f"{len(arqs)} arquivos .java\n")
for e in erros: print("  ERRO  "+e)
for w in avisos: print("  aviso "+w)
print("\nOK, nenhum erro." if not erros else f"\n{len(erros)} erro(s).")
