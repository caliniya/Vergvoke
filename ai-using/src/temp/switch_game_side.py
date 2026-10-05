# -*- coding: utf-8 -*-
# 游戏侧切换:base.tool 容器 import → arc.struct;IconGen 的 Seq → Ar;然后删 tools 四个克隆
import os, re, io

ROOTS = [
    r"D:\project\Vergvoke\core\src",
    r"D:\project\Vergvoke\desktop\src",
    r"D:\project\Vergvoke\android\src",
    r"D:\project\Vergvoke\android-old\src",
    r"D:\project\Vergvoke\annotation\src",
    r"D:\project\Vergvoke\tools\src",
]

changed = 0
for root in ROOTS:
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            p = os.path.join(dirpath, fn)
            s = io.open(p, encoding="utf-8").read()
            ns = s
            ns = ns.replace("import caliniya.vergvoke.base.tool.Ar;", "import arc.struct.Ar;")
            ns = ns.replace("import caliniya.vergvoke.base.tool.IntAr;", "import arc.struct.IntAr;")
            ns = ns.replace("import caliniya.vergvoke.base.tool.FloatAr;", "import arc.struct.FloatAr;")
            ns = ns.replace("import caliniya.vergvoke.base.tool.ObjectSet;", "import arc.struct.ObjectSet;")
            ns = ns.replace("import caliniya.vergvoke.base.tool.*;", "import arc.struct.*;")
            # 去重:替换后可能出现两条相同的 import arc.struct.*; 行
            lines = ns.split("\n")
            seen = set()
            out = []
            for ln in lines:
                key = ln.strip()
                if key.startswith("import ") and key in seen:
                    continue
                if key.startswith("import "):
                    seen.add(key)
                out.append(ln)
            ns = "\n".join(out)
            if ns != s:
                io.open(p, "w", encoding="utf-8", newline="").write(ns)
                changed += 1
print("game-side imports switched in", changed, "files")

# IconGen:Seq → Ar
p = r"D:\project\Vergvoke\tools\src\main\java\caliniya\tools\IconGen.java"
s = io.open(p, encoding="utf-8").read()
s2 = re.sub(r"\bSeq\b", "Ar", s)
io.open(p, "w", encoding="utf-8", newline="").write(s2)
print("IconGen Seq->Ar done")

# 删除 tools 四个克隆
TOOLDIR = r"D:\project\Vergvoke\tools\src\main\java\caliniya\vergvoke\base\tool"
for f in ("Ar.java", "IntAr.java", "FloatAr.java", "ObjectSet.java"):
    os.remove(os.path.join(TOOLDIR, f))
os.rmdir(TOOLDIR)
print("tools clones deleted, base/tool dir removed")

# 复查:全仓库(排除 arc/)不应再有 base.tool 容器引用或 arc Seq 引用
bad = []
for root in ROOTS:
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith(".java"):
                s = io.open(os.path.join(dirpath, fn), encoding="utf-8").read()
                if "caliniya.vergvoke.base.tool" in s:
                    bad.append((os.path.join(dirpath, fn), "base.tool"))
                if re.search(r"\bSeq\b", s):
                    bad.append((os.path.join(dirpath, fn), "Seq"))
                if re.search(r"\bSeq\.java|arc\.struct\.Seq\b", s):
                    bad.append((os.path.join(dirpath, fn), "arc.struct.Seq"))
for b in bad:
    print("RESIDUAL:", b)
print("residual count:", len(bad))
