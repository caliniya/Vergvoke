# -*- coding: utf-8 -*-
# ObjectSet:以项目克隆版(布谷鸟哈希)为基底替换 arc 版,合并 arc 版便利方法
# 关键点:迭代器必须保留 arc 版的静态结构(set/valid 字段、带参构造、嵌套检测),
#         因为 OrderedSet.OrderedSetIterator extends ObjectSetIterator 且直接访问这些成员
import io, re

SRC = r"D:\project\Vergvoke\tools\src\main\java\caliniya\vergvoke\base\tool\ObjectSet.java"
DST = r"D:\project\Vergvoke\arc\arc-core\src\arc\struct\ObjectSet.java"

s = io.open(SRC, encoding="utf-8").read()

# 1. 包名 + 删死 import
assert "package caliniya.vergvoke.base.tool;" in s
s = s.replace("package caliniya.vergvoke.base.tool;", "package arc.struct;", 1)
s = s.replace("import arc.assets.AssetDescriptor;\n", "", 1)

# 2. 拷贝构造泛型通配(对齐 arc 版签名,OrderedSet 的 super(set) 依赖)
s = s.replace("public ObjectSet(ObjectSet set) {", "public ObjectSet(ObjectSet<? extends T> set) {", 1)

# 3. 补 arc 版便利方法(copy/find/any/notEmpty),插在 toAr() 之后
anchor = """  public Ar<T> toAr() {
    return iterator().toAr();
  }
"""
assert anchor in s
extra = anchor + """
  public ObjectSet<T> copy() {
    ObjectSet<T> result = new ObjectSet<>();
    result.addAll(this);
    return result;
  }

  public T find(Boolf<T> predicate) {
    for (T t : this) {
      if (predicate.get(t)) return t;
    }
    return null;
  }

  public boolean any() {
    return size > 0;
  }

  /** Returns true if the set has one or more items. */
  public boolean notEmpty() {
    return size > 0;
  }
"""
s = s.replace(anchor, extra, 1)

# 4. addAll 返回 boolean(对齐 arc 版,内部有调用方依赖返回值)
old = """  public void addAll(T... array) {
    addAll(array, 0, array.length);
  }

  public void addAll(T[] array, int offset, int length) {
    ensureCapacity(length);
    for (int i = offset, n = i + length; i < n; i++)
      add(array[i]);
  }"""
new = """  public boolean addAll(T... array) {
    return addAll(array, 0, array.length);
  }

  public boolean addAll(T[] array, int offset, int length) {
    ensureCapacity(length);
    int oldSize = size;
    for (int i = offset, n = i + length; i < n; i++)
      add(array[i]);
    return oldSize != size;
  }"""
assert old in s
s = s.replace(old, new, 1)

# 5. 迭代器整体换成 arc 版静态结构(布谷鸟遍历逻辑):
#    OrderedSetIterator extends ObjectSetIterator 并访问 set/valid/带参构造,结构不能变
it_start = s.index("  /**\n   * Returns an iterator for the keys in the set.")
it_end = s.rstrip().rfind("}")  # 类结束
old_iter = s[it_start:it_end]
new_iter = """  /**
   * Returns an iterator for the keys in the set. Remove is supported.
   * <p>
   * Use the {@link ObjectSetIterator} constructor for nested or multithreaded iteration.
   */
  public ObjectSetIterator<T> iterator() {
    if (iterator1 == null) {
      iterator1 = new ObjectSetIterator(this);
      iterator2 = new ObjectSetIterator(this);
    }

    if (!iterator1.valid) {
      iterator1.reset();
      iterator1.valid = true;
      iterator2.valid = false;
      return iterator1;
    }

    iterator2.reset();
    iterator2.valid = true;
    iterator1.valid = false;
    return iterator2;
  }

  public static class ObjectSetIterator<K> implements Iterable<K>, Iterator<K> {
    public boolean hasNext;

    final ObjectSet<K> set;
    int nextIndex, currentIndex;
    boolean valid = true;

    public ObjectSetIterator(ObjectSet<K> set) {
      this.set = set;
      reset();
    }

    public void reset() {
      currentIndex = -1;
      nextIndex = -1;
      findNextIndex();
    }

    private void findNextIndex() {
      hasNext = false;
      int n = set.capacity + set.stashSize;
      while (++nextIndex < n) {
        if (set.keyTable[nextIndex] != null) {
          hasNext = true;
          break;
        }
      }
    }

    @Override
    public void remove() {
      int i = currentIndex;
      if (i < 0) throw new IllegalStateException("next must be called before remove.");
      if (i >= set.capacity) {
        // stash 区:摘除后把最后一个 stash 项挪过来
        set.removeStashIndex(i);
        nextIndex = i - 1;
        findNextIndex();
      } else {
        set.keyTable[i] = null;
      }
      currentIndex = -1;
      set.size--;
    }

    @Override
    public boolean hasNext() {
      if (!valid) throw new ArcRuntimeException("#iterator() cannot be used nested.");
      return hasNext;
    }

    @Override
    public K next() {
      if (!hasNext) throw new NoSuchElementException();
      if (!valid) throw new ArcRuntimeException("#iterator() cannot be used nested.");
      K key = set.keyTable[nextIndex];
      currentIndex = nextIndex;
      findNextIndex();
      return key;
    }

    @Override
    public ObjectSetIterator<K> iterator() {
      return this;
    }

    /** Adds the remaining values to the array. */
    public Ar<K> toAr(Ar<K> array) {
      while (hasNext)
        array.add(next());
      return array;
    }

    /** Returns a new array containing the remaining values. */
    public Ar<K> toAr() {
      return toAr(new Ar<>(true, set.size));
    }
  }
"""
s = s[:it_start] + new_iter + s[it_end:]

io.open(DST, "w", encoding="utf-8", newline="").write(s)

# 验证
v = io.open(DST, encoding="utf-8").read()
for must in ("package arc.struct;", "ObjectSet<? extends T> set", "public boolean addAll(T... array)",
             "static class ObjectSetIterator<K>", "final ObjectSet<K> set;", "removeStashIndex(i)",
             "public ObjectSet<T> copy()", "notEmpty()"):
    assert must in v, must
for must_not in ("caliniya", "AssetDescriptor", "toSeq"):
    assert must_not not in v, must_not
# 迭代器旧内部结构不应残留
assert "public class ObjectSetIterator implements" not in v
assert v.count("class ObjectSetIterator") == 1
print("ObjectSet merged and verified")
