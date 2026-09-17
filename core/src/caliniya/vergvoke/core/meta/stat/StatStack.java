package caliniya.vergvoke.core.meta.stat;

import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import java.util.Objects;
import caliniya.vergvoke.base.tool.Ar;

/** 一个完整的信息组 */
public class StatStack {

    /** 有序map和有序数组。 */
    private final OrderedMap<StatType, Ar<StatData>> types = new OrderedMap<>();

    public static ObjectMap<StatType, StatData> cache;

    static {
        cache = new ObjectMap<>();
        for (StatType s : StatType.values()) {
            cache.put(s, new StatData(s.localizedName, 0));
        }
    }

    public StatStack() {
        for (StatType s : StatType.values()) {
            types.put(s, new Ar<StatData>());
        }
    }

    public StatStack add(Stat stat, float value) {
        return add(stat, value, stat.unit);
    }

    public StatStack add(Stat stat, float value, StatUnit unit) {
        return add(stat, value, unit, 1, -1f);
    }

    public StatStack add(Stat stat, float value, StatUnit unit, float valueMax) {
        return add(stat, value, unit, 1, valueMax);
    }

    public StatStack add(Stat stat, float value, StatUnit unit, int level) {
        return add(stat, value, unit, level, -1f);
    }

    public StatStack add(Stat stat, float value, StatUnit unit, int level, float valueMax) {
        types.get(stat.type).add(new StatData(stat, value, unit, level, valueMax));
        return this;
    }

    public StatStack add(StatData data) {
        types.get(data.type).add(data);
        return this;
    }

    // 注意一下 我们默认是加在支持组里面
    public StatStack add(String raw) {
        types.get(StatType.function).add(new StatData(raw));
        return this;
    }

    public StatStack add(String raw, int level) {
        types.get(StatType.function).add(new StatData(raw, level));
        return this;
    }

    public StatStack add(String raw, StatType type) {
        types.get(type).add(new StatData(raw));
        return this;
    }

    public StatStack add(String raw, int level, StatType type) {
        types.get(type).add(new StatData(raw, level, type));
        return this;
    }

    /** 按 stat 精确查找，命中则就地更新并返回，未命中则新建插入并返回。 */
    public StatData get(Stat stat, float value, StatUnit unit, int level, float valueMax) {
        Ar<StatData> list = types.get(stat.type);
        for (int i = 0; i < list.size; i++) {
            StatData d = list.get(i);
            if (d.stat == stat) {
                d.set(value, valueMax);
                return d;
            }
        }
        StatData d = new StatData(stat, value, unit, level, valueMax);
        list.add(d);
        return d;
    }

    /** 按原始文本精确查找纯文本条目，命中则返回，未命中则新建插入并返回。 */
    public StatData get(String raw, int level, StatType type) {
        Ar<StatData> list = types.get(type);
        for (int i = 0; i < list.size; i++) {
            StatData d = list.get(i);
            if (d.stat == null && d.raw.equals(raw))
                return d;
        }
        StatData d = new StatData(raw, level, type);
        list.add(d);
        return d;
    }

    /** 按 stat 精确查找（带 tag 匹配），命中则就地更新并返回，未命中则新建插入并返回。 */
    public StatData get(
            Stat stat, float value, StatUnit unit, int level, float valueMax, Object tag) {
        Ar<StatData> list = types.get(stat.type);
        for (int i = 0; i < list.size; i++) {
            StatData d = list.get(i);
            if (d.stat == stat && Objects.equals(d.tag, tag)) {
                d.set(value, valueMax);
                return d;
            }
        }
        StatData d = new StatData(stat, value, unit, level, valueMax);
        d.tag = tag;
        list.add(d);
        return d;
    }

    /** 按原始文本精确查找纯文本条目（带 tag 匹配），命中则返回，未命中则新建插入并返回。 */
    public StatData get(String raw, int level, StatType type, Object tag) {
        Ar<StatData> list = types.get(type);
        for (int i = 0; i < list.size; i++) {
            StatData d = list.get(i);
            if (d.stat == null && d.raw.equals(raw) && Objects.equals(d.tag, tag))
                return d;
        }
        StatData d = new StatData(raw, level, type);
        d.tag = tag;
        list.add(d);
        return d;
    }

    /** 按 tag 查找纯文本标题条目（默认 function 分组）：命中则就地替换文本，未命中则新建插入。 */
    public StatData get(Object tag, String text) {
        return get(tag, text, 1, StatType.function);
    }

    /** 带缩进数量版（用于新建时的层级）。 */
    public StatData get(Object tag, String text, int level) {
        return get(tag, text, level, StatType.function);
    }

    /**
     * 完整版：在指定分组内按 tag（身份标识，如能力实例自身）查找纯文本条目；
     * 命中则把文本替换为 text（保持原有缩进），未命中则新建插入（层级用 level）。
     */
    public StatData get(Object tag, String text, int level, StatType type) {
        Ar<StatData> list = types.get(type);
        for (int i = 0; i < list.size; i++) {
            StatData d = list.get(i);
            if (d.stat == null && Objects.equals(d.tag, tag)) {
                d.raw = text;
                d.data = d.indent() + text;
                return d;
            }
        }
        StatData d = new StatData(text, level, type);
        d.tag = tag;
        list.add(d);
        return d;
    }

    public StatData get(Stat stat, float value, float valueMax) {
        return get(stat, value, stat.unit, 1, valueMax, null);
    }

    public StatData get(Stat stat, float value, Object tag) {
        return get(stat, value, stat.unit, 1, -1f, tag);
    }

    public StatData get(Stat stat, float value, StatUnit unit, Object tag) {
        return get(stat, value, unit, 1, -1f, tag);
    }

    public StatData get(Stat stat, float value, StatUnit unit, float valueMax, Object tag) {
        return get(stat, value, unit, 1, valueMax, tag);
    }

    public StatData get(Stat stat, float value, StatUnit unit, int level, Object tag) {
        return get(stat, value, unit, level, -1f, tag);
    }

    public StatData get(String raw, Object tag) {
        return get(raw, 1, StatType.function, tag);
    }

    public StatData get(String raw, int level, Object tag) {
        return get(raw, level, StatType.function, tag);
    }

    public StatData get(String raw, StatType type, Object tag) {
        return get(raw, 1, type, tag);
    }

    public StatData get(Stat stat, float value) {
        return get(stat, value, stat.unit, 1, -1f);
    }

    public StatData get(Stat stat, float value, StatUnit unit) {
        return get(stat, value, unit, 1, -1f);
    }

    public StatData get(Stat stat, float value, StatUnit unit, float valueMax) {
        return get(stat, value, unit, 1, valueMax);
    }

    public StatData get(Stat stat, float value, StatUnit unit, int level) {
        return get(stat, value, unit, level, -1f);
    }

    public StatData get(String raw) {
        return get(raw, 1, StatType.function);
    }

    public StatData get(String raw, int level) {
        return get(raw, level, StatType.function);
    }

    public StatData get(String raw, StatType type) {
        return get(raw, 1, type);
    }

    // 按照预设分组自动查找匹配
    public StatData find(Stat stat) {
        for (StatData data : types.get(stat.type)) {
            if (data.stat == stat)
                return data;
        }
        return null;
    }

    // 带有特定匹配
    public StatData find(Stat stat, Object tag) {
        for (StatData data : types.get(stat.type)) {
            if (data.stat == stat || data.tag == tag)
                return data;
        }
        return null;
    }

    public StatData find(String raw, StatType type) {
        for (StatData data : types.get(type)) {
            if (data.raw.contains(raw))
                return data;
        }
        return null;
    }

    public StatData find(String raw, StatType type, Object tag) {
        for (StatData data : types.get(type)) {
            if (data.raw.contains(raw) && Objects.equals(data.tag, tag))
                return data;
        }
        return null;
    }

    /** 清空所有分组内容（保留分组结构，可复用）。 */
    public StatStack clear() {
        types.each((K, V) -> V.clear());
        return this;
    }

    /** 递归提供全部 StatData（含 StatType 标题、无分组空标题、能力标题、参数）。 data 已含缩进，渲染端直接显示。 */
    public void each(Cons<StatData> cons) {
        types.each(
                (K, V) -> {
                    if (!V.any()) {
                        return;
                    }
                    cons.get(cache.get(K));
                    V.each(d -> d.each(a -> cons.get(a)));
                });
    }
}
