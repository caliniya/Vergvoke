package caliniya.vergvoke.game.data;

import arc.Events;
import arc.func.*;
import arc.struct.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.type.*;

/// 包括UI需要的建筑和单位，资源相关数据。
public class CommandData {

	// 当前选中的单位
	public static Ar<Unit> checkedUnits = new Ar<>();

	/// 交互模式：普通 / 指挥 / 建造（原 commanding 布尔，枚举后放置与拆除接入）
	public static Mode mode = Mode.nor;

	/** 当前指挥状态（默认移动模式）。 */
	public static CommandType commandType = CommandType.Move;

	/// 当前视图
	public static ViewType viewType;

	// 初始化，也包括重置数据
	public static void init() {
		checkedUnits.clear();
		mode = Mode.nor;
		commandType = CommandType.Move;
		viewType = ViewType.game;

		// 视图切换统一在这里维护：所有切视图入口都走 EnterUV/ExitUV 事件
		Events.run(EventType.events.EnterUV, () -> viewType = ViewType.uni);
		Events.run(EventType.events.ExitUV, () -> viewType = ViewType.game);
	}

	public static void select(float x, float y, Boolf<Entity<?, ?>> filter, Cons<Entity<?, ?>> e) {
		Game.team.data().find(x, y, 1f, filter, e);
	}

	public static void select(float x, float y, Cons<Entity<?, ?>> e) {
		select(x, y, entity -> true, e);
	}

	public enum CommandType {
		None, Move, Stop
	}

	/// 交互模式
	public enum Mode {
		nor, cmd
	}

	public enum ViewType {
		game, uni
	}

}
