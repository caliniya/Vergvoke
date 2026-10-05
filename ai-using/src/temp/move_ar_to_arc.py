# -*- coding: utf-8 -*-
# 把 tools 的 Ar/IntAr/FloatAr 搬进 arc/arc-core/src/arc/struct/
# Ar 需要的转换:包名、去 arc.struct import、删 toSeq、set 自赋值保护、补 3 个方法、修 javadoc 乱码
# IntAr/FloatAr:纯改名,只换包名
import re, io, os

TOOLS = r"D:\project\Vergvoke\tools\src\main\java\caliniya\vergvoke\base\tool"
DST = r"D:\project\Vergvoke\arc\arc-core\src\arc\struct"

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="") as f:
        f.write(s)

# ---------- IntAr / FloatAr:纯改名 ----------
for name in ("IntAr", "FloatAr"):
    s = read(os.path.join(TOOLS, name + ".java"))
    assert "package caliniya.vergvoke.base.tool;" in s, name
    s = s.replace("package caliniya.vergvoke.base.tool;", "package arc.struct;", 1)
    write(os.path.join(DST, name + ".java"), s)
    print(name, "written, package fixed")

# ---------- Ar:多步转换 ----------
s = read(os.path.join(TOOLS, "Ar.java"))
assert "package caliniya.vergvoke.base.tool;" in s
s = s.replace("package caliniya.vergvoke.base.tool;", "package arc.struct;", 1)

# 删 import arc.struct.*(Ar 在 arc.struct 内部,不需要也不允许自引用)
s = s.replace("import arc.struct.*;\n", "", 1)

# 删 toSeq 桥接方法
tosq = re.search(r"  public Seq<T> toSeq\(\) \{.*?\n  \}\n\n", s, re.S)
assert tosq, "toSeq not found"
s = s[:tosq.start()] + s[tosq.end():]

# set 自赋值保护
old_set = """  public void set(Ar<? extends T> array) {
    clear();
    addAll(array);
  }"""
new_set = """  public void set(Ar<? extends T> array) {
    if(array == this) return;
    clear();
    addAll(array);
  }"""
assert old_set in s, "set() not found"
s = s.replace(old_set, new_set, 1)

# 补 3 个 Seq 有而 Ar 没有的方法(从 Seq.java 移植,Seq→Ar)
extra = """
  public static <T, V> Ar<V> map(T[] array, Func<T, V> mapper){
    Ar<V> result = new Ar<>(array.length);
    for(int i = 0; i < array.length; i++){
      result.add(mapper.get(array[i]));
    }
    return result;
  }

  public <K> ObjectMap<K, Ar<T>> groupBy(Func<T, K> keygen){
    ObjectMap<K, Ar<T>> map = new ObjectMap<>();
    for(int i = 0; i < size; i++){
      T item = items[i];
      map.get(keygen.get(item), Ar::new).add(item);
    }
    return map;
  }

  public <K> ObjectIntMap<K> groupByCount(Func<T, K> keygen){
    ObjectIntMap<K> map = new ObjectIntMap<>();
    for(int i = 0; i < size; i++){
      map.increment(keygen.get(items[i]));
    }
    return map;
  }
"""
# 插在类结束的最后一个 } 之前(内部类之后,Java 允许)
assert s.rstrip().endswith("}")
idx = s.rstrip().rfind("}")
s = s[:idx] + extra + s[idx:]

# 修 javadoc 乱码(全局 Seq→Ar 替换的产物)
s = s.replace("subAruent", "subsequent")
s = s.replace("Aruence", "sequence")

write(os.path.join(DST, "Ar.java"), s)
print("Ar written: toSeq removed, set guard added, 3 methods ported")

# 验证
a = read(os.path.join(DST, "Ar.java"))
for must in ("package arc.struct;", "if(array == this) return;", "groupByCount", "static <T, V> Ar<V> map"):
    assert must in a, must
for must_not in ("toSeq", "import arc.struct.*", "caliniya"):
    assert must_not not in a, must_not
print("Ar verification OK")
