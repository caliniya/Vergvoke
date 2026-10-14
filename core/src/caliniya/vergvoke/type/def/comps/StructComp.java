package caliniya.vergvoke.type.def.comps;

import arc.util.io.*;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.type.*;

/// 可被建造组件：以"建造"方式生成实体所需的全部数据（挂 BuildingDef，工地/成品共有；无 @Updata，推进由认领单位的 BuildAi 驱动）
@Component(name = "Struct", index = 9)
public class StructComp {

	/// 建造所需累计进度（delta 累积；300 = 5 秒）
	public float buildTime = 300f;

	/// 单次建造物品需求（类型级默认值，后续由 BuildingType 配置覆写）
	public Item cost = new Item(Items.Ge, 10);

	/// 当前进度 0..buildTime（ai 线程写、主线程渲染读，volatile 保可见）
	public volatile float progress;

	/// 完工标记（FINISH 转正后 true；ai 线程降级拆除时写、主线程渲染读，volatile 保可见）
	public volatile boolean finished;

	/**
	 * 方形占位边长（格；{@link #shape} 为 null 时生效）。
	 *
	 * <p>建造语义的形状权威在本组件，不依赖 BlockComp——建筑侧由
	 * {@code BuildingType.create/rebuild} 从类型配置灌入；未来可建造单位不经
	 * BlockComp 也能凭它得到合理占地区域。读档同走 create 重建，不进序列化。
	 */
	public int structSize = 1;

	/// 占位形状偏移（成对 dx,dy；null = 方形）。异形轮廓/单位占地区域都用它；来源同 {@link #structSize}
	public int[] shape;

	@Write
	public void write(Writes w) {
		w.f(progress);
		w.bool(finished);
	}

	@Read
	public void read(Reads r) {
		progress = r.f();
		finished = r.bool();
	}
}
