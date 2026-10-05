import arc.struct.*;

public class TestContainers {
  static int fails = 0;

  static void check(String name, boolean ok) {
    System.out.println((ok ? "PASS " : "FAIL ") + name);
    if (!ok) fails++;
  }

  public static void main(String[] args) {
    // ---- Ar 基础 ----
    Ar<Integer> a = new Ar<>();
    for (int i = 0; i < 100; i++) a.add(i);
    check("Ar.add/size", a.size == 100);
    check("Ar.contains", a.contains(50) && !a.contains(100));
    a.remove(50);
    check("Ar.remove", !a.contains(50) && a.size == 99);
    a.set(a); // 自赋值保护:应无操作(原来会清空)
    check("Ar.set self-guard", a.size == 99);
    Ar<Integer> mapped = Ar.map(new Integer[]{1, 2, 3}, x -> x * 2);
    check("Ar.map(T[],Func)", mapped.size == 3 && mapped.get(2) == 6);
    ObjectMap<String, Ar<Integer>> g = new ObjectMap<>();
    a.each(x -> g.get("k" + (x % 3), Ar::new).add(x));
    check("Ar.groupBy", g.size == 3 && g.get("k0").size > 0);
    check("Ar.with/select", Ar.with(1, 2, 3).select(x -> x > 1).size == 2);

    // ---- ObjectSet(布谷鸟) ----
    ObjectSet<String> s = new ObjectSet<>();
    for (int i = 0; i < 500; i++) s.add("item" + i);
    check("ObjectSet.add/size", s.size == 500);
    check("ObjectSet.contains", s.contains("item250") && !s.contains("nope"));
    check("ObjectSet.add dup false", !s.add("item250"));
    s.remove("item250");
    check("ObjectSet.remove", !s.contains("item250") && s.size == 499);
    check("ObjectSet.copy/find/any/notEmpty", s.copy().size == 499 && s.find(x -> x.equals("item7")).equals("item7") && s.any() && s.notEmpty());
    int cnt = 0;
    for (String x : s) cnt++;
    check("ObjectSet.iterate", cnt == 499);
    // 嵌套迭代检测:持有旧迭代器,再开一轮迭代使其失效,旧迭代器 hasNext 应抛异常
    ObjectSet.ObjectSetIterator<String> first = s.iterator();
    first.next();
    s.iterator();
    boolean threw = false;
    try { first.hasNext(); } catch (RuntimeException e) { threw = true; }
    check("ObjectSet nested-detect throws", threw);
    check("ObjectSet.toString/equals", s.toString().startsWith("{") && s.equals(s.copy()));

    // ---- OrderedSet ----
    OrderedSet<Integer> os = new OrderedSet<>();
    for (int i = 0; i < 20; i++) os.add(i * 10);
    os.remove(50);
    StringBuilder sb = new StringBuilder();
    os.orderedItems().each(x -> sb.append(x).append(","));
    check("OrderedSet order", sb.toString().equals("0,10,20,30,40,60,70,80,90,100,110,120,130,140,150,160,170,180,190,"));
    ObjectSet.ObjectSetIterator<Integer> it = os.iterator();
    int c2 = 0;
    while (it.hasNext) { it.next(); c2++; }
    check("OrderedSet.iterator field-style", c2 == 19);
    os.alter(30, 35);
    check("OrderedSet.alter", os.contains(35) && !os.contains(30));

    // ---- ObjectMap / IntFloatMap(tableSize 静态导入路径) ----
    ObjectMap<String, Integer> m = new ObjectMap<>();
    for (int i = 0; i < 300; i++) m.put("k" + i, i);
    check("ObjectMap", m.size == 300 && m.get("k299") == 299);
    IntFloatMap fm = new IntFloatMap();
    fm.put(1, 1.5f);
    check("IntFloatMap", fm.get(1, -1f) == 1.5f);

    // ---- DelayedRemovalAr ----
    DelayedRemovalAr<Integer> d = new DelayedRemovalAr<>();
    for (int i = 0; i < 10; i++) d.add(i);
    d.begin();
    for (Integer v : d) {
      if (v == 0 || v == 2) d.remove((Integer) v, false); // 容器自身 remove,延迟生效
    }
    d.end();
    check("DelayedRemovalAr", d.size == 8 && !d.contains(0) && !d.contains(2) && d.contains(1));

    // ---- IntAr / FloatAr / BoolAr ----
    IntAr ia = new IntAr();
    ia.addAll(1, 2, 3);
    check("IntAr", ia.pop() == 3 && ia.peek() == 2);
    FloatAr fa = FloatAr.with(0.5f, 1.5f);
    check("FloatAr", fa.size == 2);
    BoolAr ba = new BoolAr();
    ba.add(true);
    check("BoolAr", ba.first());

    // ---- ObjectSet.with(Ar) / toAr ----
    ObjectSet<Integer> ws = ObjectSet.with(Ar.with(1, 2, 2, 3));
    check("ObjectSet.with(Ar)/toAr", ws.size == 3 && ws.toAr().size == 3);

    System.out.println(fails == 0 ? "ALL PASS" : (fails + " FAILURES"));
    System.exit(fails == 0 ? 0 : 1);
  }
}
