package caliniya.vergvoke.core.meta.stat;

import arc.func.Floatp;
import arc.func.Cons;
import arc.util.Nullable;
import caliniya.vergvoke.base.api.*;
import arc.struct.Ar;

/** 统计值单元 */
public class StatData {

  // 自身的信息类型
  public Stat stat;
  // 所属统计组，默认情况下 我们用通用
  public StatType type = StatType.general;
  // 对于布尔类型 除了"1"以外的值都视为否
  public float value;
  // 可选的最大值，默认情况下为-1
  public float valueMax = -1f;
  // 用于指定的缩进指数
  public int level = 1;
  // 是否显式设置过 level（add 时不再自动覆盖）
  public boolean levelSet;
  // 单位
  public StatUnit unit;
  // 处理完毕后的值，应该是包含缩进的
  public String data;
  // 原始文本（不含缩进），add 重新生成缩进时使用
  public String raw = "";

  /** 可选身份标签：区分内容相同（如同名能力）的条目；null 表示不参与匹配。 */
  public Object tag;

  /** 动态数值源：非 null 时渲染端每帧经 {@link #getData()} 取最新值（静态条目保持 null）。 */
  public @Nullable Floatp live;

  /** 显示拼接缓冲（单线程：仅渲染路径使用；返回给 Label 后立即被复制消费）。 */
  private final StringBuilder builder = new StringBuilder(64);

  // 分组的另一种解决方法
  // 自身作为分组标题，该分组所属的内容 直接加入到自身
  public Ar<StatData> datas = new Ar<>();

  public StatData(Stat stat, float value) {
    this(stat, value, stat.unit);
  }

  public StatData(Stat stat, float value, float valueMax) {
    this(stat, value, stat.unit, 1, valueMax);
  }

  public StatData(Stat stat, float value, StatUnit unit) {
    this(stat, value, unit, 1, -1f);
  }

  public StatData(Stat stat, float value, StatUnit unit, int level, float valueMax) {
    this.stat = stat;
    this.value = value;
    this.unit = unit;
    this.level = level;
    this.valueMax = valueMax;
    this.type = stat != null ? stat.type : StatType.none;
    this.raw = (stat != null ? stat.localizedName + ": " + unit.format(value) : "");
    this.data = indent() + raw;
  }

  /** 纯文本(比如说是介绍) */
  public StatData(String data) {
    this(data, 1, StatType.none);
  }

  public StatData(String data, int level) {
    this(data, level, StatType.none);
  }

  public StatData(String data, StatType type) {
    this(data, 1, type);
  }

  /** 纯文本 + 组/层级信息（标题等）。 */
  public StatData(String data, int level, StatType type) {
    this.level = level;
    this.type = type;
    this.raw = data;
    this.data = indent() + data;
  }

  public static StatData with(Stat stat, float value, StatUnit unit) {
    return new StatData(stat, value, unit);
  }

  public static StatData with(Stat stat, float value, StatUnit unit, int level, float valueMax) {
    return new StatData(stat, value, unit, level, valueMax);
  }

  public static StatData with(Stat stat, float value, float valueMax) {
    return new StatData(stat, value, valueMax);
  }

  public static StatData with(Stat stat, float value) {
    return new StatData(stat, value);
  }

  public static StatData with(String data) {
    return new StatData(data);
  }

  public static StatData with(String data, int level) {
    return new StatData(data, level);
  }

  public static StatData with(String data, int level, StatType type) {
    return new StatData(data, level, type);
  }

  public static StatData with(String data, StatType type) {
    return new StatData(data, type);
  }

  public StatData set(float value) {
    return set(value, -1);
  }

  public StatData set(float value, float valueMax) {
    // 值未变化时短路：不重算文本，避免每帧做无谓的字符串分配
    if (this.value == value && this.valueMax == valueMax) return this;
    this.value = value;
    this.valueMax = valueMax;
    if (stat != null && unit != null) {
      this.raw = stat.localizedName + ": " + unit.format(value);
      this.data = indent() + raw;
    }
    return this;
  }

  // 所有的find 返回的都是第1个匹配选项
  public StatData find(Stat stat, Object tag) {
    for (StatData d : datas) {
      if (d.stat == stat && (tag == null || java.util.Objects.equals(d.tag, tag))) return d;
      StatData found = d.find(stat, tag);
      if (found != null) return found;
    }
    return null;
  }

  // 因为字符大多数情况下会被颜色格式化，所以不能用完全匹配 而应该用包含
  public StatData find(String raw, Object tag) {
    for (StatData d : datas) {
      if (d.stat == null
          && d.raw.equals(raw)
          && (tag == null || java.util.Objects.equals(d.tag, tag))) return d;
      StatData found = d.find(raw, tag);
      if (found != null) return found;
    }
    return null;
  }

  /** 插入子元素：自动 level+1 并重新生成含缩进的 data。 */
  public StatData add(StatData child) {
    if (!child.levelSet) {
      child.level = this.level + 1;
    }
    child.data = child.indent() + child.raw;
    datas.add(child);
    return this;
  }

  public String indent() {
    return level <= 0 ? "   " : "   " + StringApi.repeat("\u3000\u3000", level);
  }

  /** 对于查找命中的 会自动设置，未命中的则会新建并插入 */
  public StatData get(Stat stat, float value, StatUnit unit, int level, float valueMax) {
    for (StatData d : datas) {
      if (d.stat == stat) {
        d.set(value, valueMax);
        return d;
      }
    }
    StatData d = new StatData(stat, value, unit, level, valueMax);
    add(d);
    return d;
  }

  public StatData get(String raw, int level, StatType type) {
    for (StatData d : datas) {
      if (d.stat == null && d.raw.equals(raw)) return d;
    }
    StatData d = new StatData(raw, level, type);
    add(d);
    return d;
  }

  public StatData get(Stat stat, float value) {
    return get(stat, value, stat.unit, 1, -1f);
  }

  public StatData get(Stat stat, float value, StatUnit unit) {
    return get(stat, value, unit, 1, -1f);
  }

  public StatData get(Stat stat, float value, float valueMax) {
    return get(stat, value, stat.unit, 1, valueMax);
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

  // 包含自身以及子元素的递归
  public void each(Cons<StatData> con) {
    con.get(this);
    datas.each(d -> d.each(con));
  }

  /** 统一取显示文本：静态条目直接返回 data；动态条目（live 非 null）先按最新值就地刷新 （{@link #set} 自带值未变短路，零分配），再把最新文本交给渲染端。 */
  public CharSequence getData() {
    if (live != null) {
      set(live.get(), valueMax);
    }
    // 有最大值的数值条目：用 StringBuilder 组装「值 / 最大 (百分比)」，缓冲复用、零中间对象
    if (stat != null && unit != null && valueMax > 0f) {
      builder.setLength(0);
      builder
          .append(indent())
          .append(stat.localizedName)
          .append(": ")
          .append(unit.format(value))
          .append(" / ")
          .append(unit.format(valueMax))
          .append(" (")
          .append(StringApi.autoFixed(value / valueMax * 100f, 1))
          .append("%)");
      return builder;
    }
    return data;
  }

  public StatData setLevel(int level) {
    // 已在相同层级：短路，避免每帧（链式 setLevel）无谓重算
    if (this.levelSet && this.level == level) return this;
    this.level = level;
    this.levelSet = true;
    this.data = indent() + raw;
    return this;
  }
}
