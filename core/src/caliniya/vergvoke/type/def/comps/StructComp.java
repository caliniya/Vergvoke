package caliniya.vergvoke.type.def.comps;

import arc.util.io.*;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.content.Items;
import caliniya.vergvoke.type.*;

/// 可被建造组件：以"建造"方式生成实体所需的全部数据（挂 BuildingDef，工地/成品共有；无 @Updata，推进由认领单位的 BuildAi 驱动）
@Component(name = "Struct", index = 9)
public class StructComp {

	/// 建造所需累计进度（delta 累积；300 = 5 秒）
	public float buildTime = 300f;

	/// 单次建造物品需求（类型级默认值，后续由 BuildingType 配置覆写）
	public Item cost = new Item(Items.Ge, 10);

	/// 当前进度 0..buildTime
	public float progress;

	/// 完工标记（FINISH 转正后 true）
	public boolean finished;

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
