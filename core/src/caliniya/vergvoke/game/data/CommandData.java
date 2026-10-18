package caliniya.vergvoke.game.data;

import arc.*;
import arc.func.*;
import arc.struct.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.*;

/// 包括UI需要的建筑和单位，资源相关数据。
public class CommandData {

	// 当前选中的单位
	public static Ar<Unit> checkedUnits = new Ar<>();

	/** 当前指挥状态（默认移动模式）。 */
	public static CommandMode commandMode = CommandMode.Move;

	public static Mode mode;

	public static BuildMode buildMode = BuildMode.Build;

	/// 当前视图
	public static ViewType viewType;

	// 初始化，也包括重置数据
	public static void init() {
		checkedUnits.clear();
		mode = Mode.Normal;
		commandMode = CommandMode.Move;
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

	public enum CommandMode {
		None, Move, Stop
	}

	public enum BuildMode {
		Build, Remove
	}

	public enum Mode {
		Normal, Command, Build
	}

	public enum ViewType {
		game, uni
	}

}
