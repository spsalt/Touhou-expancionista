"""Verificador estatico (nao temos JDK aqui): sintaxe, imports, @Override,
visibilidade entre pacotes, chaves de config orfas/faltando, assets."""
import os,re,javalang,sys
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
for a,t in arvores.items():
    for ty in t.types:
        ext=getattr(ty,'extends',None)
        pai=ext.name if ext and hasattr(ext,'name') else None
        if not pai or pai not in classes: continue
        herdados=set(metodos(pai))
        for m in getattr(ty,'methods',[]):
            if any(getattr(an,'name','')=='Override' for an in m.annotations):
                if (m.name,len(m.parameters)) not in herdados:
                    erros.append(f"OVERRIDE {a}: {ty.name}.{m.name}/{len(m.parameters)} nao existe em {pai}")

# ---- visibilidade entre pacotes: campo/metodo estatico acessado de fora ----
membros={}
for n,(a,ty) in classes.items():
    d={}
    for f in getattr(ty,'fields',[]):
        for dec in f.declarators: d[dec.name]=set(f.modifiers)
    for m in getattr(ty,'methods',[]): d[m.name]=set(m.modifiers)
    membros[n]=d
for a,t in arvores.items():
    pk=t.package.name if t.package else ''
    corpo=sem_comentarios(fonte[a])
    for cls,d in membros.items():
        if pacote[cls]==pk: continue
        for mem,mods in d.items():
            if 'public' in mods: continue
            if re.search(r'\b%s\s*\.\s*%s\b'%(cls,mem),corpo):
                erros.append(f"VISIBILIDADE {a}: {cls}.{mem} nao e public")

# ---- config: chaves usadas x definidas ----
props={}
for ln in open('config/game.properties',encoding='utf-8'):
    ln=ln.strip()
    if ln and not ln.startswith('#') and '=' in ln: props[ln.split('=',1)[0].strip()]=ln.split('=',1)[1]
usadas=set()
for a,s in fonte.items():
    usadas|=set(re.findall(r'Config\.get\w+\(\s*"([^"]+)"',s))
for k in sorted(usadas-set(props)): erros.append(f"CONFIG faltando no properties: {k}")
for k in sorted(set(props)-usadas): avisos.append(f"CONFIG orfa (no properties, ninguem le): {k}")

# ---- assets citados ----
for a,s in fonte.items():
    for cam in re.findall(r'"((?:sprites|audio)/[^"]+)"',s):
        if not os.path.exists(cam): erros.append(f"ASSET some: {cam} (citado em {a})")
for k,v in props.items():
    if re.match(r'^(sprites|audio)/',v) and not os.path.exists(v):
        erros.append(f"ASSET some: {v} (properties: {k})")

print(f"{len(arqs)} arquivos .java\n")
for e in erros: print("  ERRO  "+e)
for w in avisos: print("  aviso "+w)
print("\nOK, nenhum erro." if not erros else f"\n{len(erros)} erro(s).")
