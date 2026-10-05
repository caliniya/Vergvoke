# -*- coding: utf-8 -*-
# arc 内部全量替换:Seq 家族 → Ar 家族(词边界,CharSequence/SequenceAction 等天然不受影响)
import os, re, io

ROOTS = [r"D:\project\Vergvoke\arc\arc-core", r"D:\project\Vergvoke\arc\backends"]
STRUCT = r"D:\project\Vergvoke\arc\arc-core\src\arc\struct"

# 顺序无关:词边界保证 IntSeq/BoolSeq 等复合名不会被 \bSeq\b 误碰
PATTERNS = [
    (re.compile(r"\bFloatSeq\b"), "FloatAr"),
    (re.compile(r"\bIntSeq\b"), "IntAr"),
    (re.compile(r"\bBoolSeq\b"), "BoolAr"),
    (re.compile(r"\bByteSeq\b"), "ByteAr"),
    (re.compile(r"\bLongSeq\b"), "LongAr"),
    (re.compile(r"\bShortSeq\b"), "ShortAr"),
    (re.compile(r"\bDelayedRemovalSeq\b"), "DelayedRemovalAr"),
    (re.compile(r"\bSnapshotSeq\b"), "SnapshotAr"),
    (re.compile(r"\bSeq\b"), "Ar"),
]

changed_files = 0
total_hits = 0
for root in ROOTS:
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            p = os.path.join(dirpath, fn)
            s = io.open(p, encoding="utf-8").read()
            ns = s
            hits = 0
            for pat, rep in PATTERNS:
                ns, n = pat.subn(rep, ns)
                hits += n
            if hits:
                io.open(p, "w", encoding="utf-8", newline="").write(ns)
                changed_files += 1
                total_hits += hits

print("files changed:", changed_files, "tokens replaced:", total_hits)

# 删除被 Ar/IntAr/FloatAr 取代的旧文件
for old in ("Seq.java", "IntSeq.java", "FloatSeq.java"):
    p = os.path.join(STRUCT, old)
    os.remove(p)
    print("deleted", old)

# 其余 Seq 家族文件改名(类名已随替换变成 *Ar)
for old, new in (("BoolSeq.java", "BoolAr.java"), ("ByteSeq.java", "ByteAr.java"),
                 ("LongSeq.java", "LongAr.java"), ("ShortSeq.java", "ShortAr.java"),
                 ("DelayedRemovalSeq.java", "DelayedRemovalAr.java"),
                 ("SnapshotSeq.java", "SnapshotAr.java")):
    os.rename(os.path.join(STRUCT, old), os.path.join(STRUCT, new))
    print("renamed", old, "->", new)

# 复查:arc 内不应再有 \bSeq\b 词(任何残留都是漏网点)
left = 0
for root in ROOTS:
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith(".java"):
                s = io.open(os.path.join(dirpath, fn), encoding="utf-8").read()
                n = len(re.findall(r"\bSeq\b", s))
                if n:
                    left += n
                    print("RESIDUAL", os.path.join(dirpath, fn), n)
print("residual \\bSeq\\b:", left)

# 复查:不应有误伤产物
bad = 0
for root in ROOTS:
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith(".java"):
                s = io.open(os.path.join(dirpath, fn), encoding="utf-8").read()
                for pat in (r"\bCharAr\b", r"\bSequenceAr\b"):
                    n = len(re.findall(pat, s))
                    if n:
                        bad += n
                        print("BAD REPLACE", pat, os.path.join(dirpath, fn), n)
print("bad replacements:", bad)
