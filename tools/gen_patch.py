"""Generates the Pd patch from named objects (no hand-numbered wiring)."""
import sys
objs, conns = [], []
def o(name, text, kind="obj"):
    objs.append((name, kind, text)); return name
def c(a, b, outlet=0, inlet=0): conns.append((a, outlet, b, inlet))

# ---------- controls (sliders from the app) ----------
o("rdrown", "r drown")
o("fbMul", "* 0.37"); o("fbAdd", "+ 0.45"); o("fbPk", "pack f 200"); o("fbSig", "line~")
c("rdrown","fbMul"); c("fbMul","fbAdd"); c("fbAdd","fbPk"); c("fbPk","fbSig")
o("ecMul", "* 0.55"); o("ecAdd", "+ 0.35"); o("ecPk", "pack f 200"); o("ecSig", "line~")
c("rdrown","ecMul"); c("ecMul","ecAdd"); c("ecAdd","ecPk"); c("ecPk","ecSig")
o("rroom", "r roomlvl"); o("rmMul", "* 3"); o("rmPk", "pack f 100"); o("rmSig", "line~")
c("rroom","rmMul"); c("rmMul","rmPk"); c("rmPk","rmSig")
o("rsong", "r songlvl"); o("sgPk", "pack f 150"); o("sgSig", "line~")
c("rsong","sgPk"); c("sgPk","sgSig")
o("rsrc", "r source"); o("srcEq", "== 1"); o("srcPk", "pack f 150"); o("srcSig", "line~")
c("rsrc","srcEq"); c("srcEq","srcPk"); c("srcPk","srcSig")
o("songGain", "*~"); c("srcSig","songGain"); c("sgSig","songGain",0,1)

# ---------- mic (the room) ----------
o("adc", "adc~ 1"); o("micHp", "hip~ 60"); o("micIn", "*~")
c("adc","micHp"); c("micHp","micIn"); c("rmSig","micIn",0,1)

# ---------- song ----------
o("rsf", "readsf~ 2")
o("tL", "*~"); o("tR", "*~")
c("rsf","tL",0,0); c("songGain","tL",0,1); c("rsf","tR",1,0); c("songGain","tR",0,1)
o("tLs", "lop~ 6000"); o("tRs", "lop~ 6000"); c("tL","tLs"); c("tR","tRs")
o("tSum", "+~"); o("tMono", "*~ 0.5"); c("tLs","tSum"); c("tRs","tSum",0,1); c("tSum","tMono")
o("dryL", "*~ 0.35"); o("dryR", "*~ 0.35"); c("tLs","dryL"); c("tRs","dryR")

# ---------- ONE shared long echo bus: room + song flow into the same tail ----------
o("tSend", "*~ 0.6"); c("tMono","tSend")
o("busA", "+~"); c("micIn","busA"); c("tSend","busA",0,1)
o("busB", "+~"); c("busA","busB")
o("dw", "delwrite~ md 5000"); c("busB","dw")
o("lfoL", "osc~ 0.03"); o("lfoLs", "*~ 200"); o("tmL", "+~ 650"); o("tapL", "vd~ md")
c("lfoL","lfoLs"); c("lfoLs","tmL"); c("tmL","tapL")
o("lfoR", "osc~ 0.041"); o("lfoRs", "*~ 200"); o("tmR", "+~ 820"); o("tapR", "vd~ md")
c("lfoR","lfoRs"); c("lfoRs","tmR"); c("tmR","tapR")
o("tapSum", "+~"); c("tapL","tapSum"); c("tapR","tapSum",0,1)
o("fbHalf", "*~ 0.5"); o("fbG", "*~"); o("fbLp", "lop~ 3000"); o("fbHp", "hip~ 120")
c("tapSum","fbHalf"); c("fbHalf","fbG"); c("fbSig","fbG",0,1); c("fbG","fbLp"); c("fbLp","fbHp")
c("fbHp","busB",0,1)

# ---------- reverb: room + song + the echoes all wash into the song's space ----------
o("micRev", "*~ 0.6"); o("tRev", "*~ 0.6"); o("tapRev", "*~ 0.45")
c("micIn","micRev"); c("tMono","tRev"); c("tapSum","tapRev")
o("revA", "+~"); o("revB", "+~"); c("micRev","revA"); c("tRev","revA",0,1)
c("revA","revB"); c("tapRev","revB",0,1)
o("rev", "e_platereverb rv1"); c("revB","rev")

# ---------- shimmer ----------
o("shim", "e_pitchshift"); o("shimMsg", "pitch 12", "msg")
o("shimLp", "lop~ 5000"); o("shimLv", "*~ 0.22")
c("rev","shim",0,0); c("shimMsg","shim"); c("shim","shimLp"); c("shimLp","shimLv")

# ---------- output mix ----------
o("ecL", "*~"); o("ecR", "*~"); c("tapL","ecL"); c("ecSig","ecL",0,1); c("tapR","ecR"); c("ecSig","ecR",0,1)
o("micDlp", "lop~ 5000"); o("micD", "*~ 0.2"); c("micIn","micDlp"); c("micDlp","micD")
o("revLw", "*~ 0.7"); o("revRw", "*~ 0.7"); c("rev","revLw",0); c("rev","revRw",1)
for side, dry, ec, rw in (("L","dryL","ecL","revLw"), ("R","dryR","ecR","revRw")):
    a=o("s1"+side,"+~"); b=o("s2"+side,"+~"); d=o("s3"+side,"+~"); e=o("s4"+side,"+~")
    m=o("mst"+side,"*~ 0.85"); cl=o("clp"+side,"clip~ -0.95 0.95")
    c(dry,a); c(ec,a,0,1); c(a,b); c(rw,b,0,1); c(b,d); c("shimLv",d,0,1); c(d,e); c("micD",e,0,1)
    c(e,m); c(m,cl)
o("dac", "dac~ 1 2"); c("clpL","dac",0,0); c("clpR","dac",0,1)

# ---------- startup ----------
o("lb", "loadbang"); o("dsp", r"\; pd dsp 1", "msg"); o("dl", "del 300")
o("open", "open ./mytrack.wav \\, 1", "msg")
c("lb","dsp"); c("lb","dl"); c("dl","open"); c("open","rsf"); c("rsf","open",2,0)
for nm, val, dest in (("d1","1","source"),("d2","0.7","drown"),("d3","0.75","songlvl"),("d4","0.7","roomlvl")):
    o(nm, val, "msg"); o(nm+"s", "s "+dest); c("lb",nm); c(nm,nm+"s")
c("lb","shimMsg")

# ---------- emit ----------
idx = {n:i for i,(n,_,_) in enumerate(objs)}
assert len(idx)==len(objs), "duplicate names"
out = ["#N canvas 40 30 1100 860 10;", "#X declare -path rjlib/rj;"]
for i,(n,k,t) in enumerate(objs):
    x, y = 20 + (i % 10) * 105, 20 + (i // 10) * 40
    out.append(f"#X {k} {x} {y} {t};")
for a,oa,b,ib in conns:
    out.append(f"#X connect {idx[a]} {oa} {idx[b]} {ib};")
open(sys.argv[1],"w").write("\n".join(out)+"\n")
print(f"{len(objs)} objects, {len(conns)} connections")
