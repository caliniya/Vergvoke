package caliniya.vergvoke.game.data;

import arc.func.*;
import arc.math.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;

/**
 * 简而言之阵营数据以及相关的工具
 */
public class TeamData {
	public final TeamTypes team;

	public TeamData(TeamTypes team) {
		this.team = team;
	}

	/**
	 * 查找半径内属于本阵营的实体
	 *
	 * @param filter 额外过滤谓词
	 */
	public void find(float x, float y, float r, Boolf<Entity<?, ?>> filter, Cons<Entity<?, ?>> con) {
		intersect(x, y, r, filter, e -> {
			if (Mathf.dst2(e.x, e.y, x, y) <= r * r) {
				con.get(e);
			}
		});
	}

	/** 查找半径内属于本阵营的实体 */
	public void find(float x, float y, float r, Cons<Entity<?, ?>> con) {
		find(x, y, r, null, con);
	}

	/**
	 * 查询包围盒内属于本阵营的实体,单位先于建筑
	 *
	 * @param filter 额外过滤谓词
	 */
	public void intersect(float x, float y, float r, Boolf<Entity<?, ?>> filter, Cons<Entity<?, ?>> con) {
		Boolf<Entity<?, ?>> f = filter != null ? filter : e -> true;
		Boolf<Entity<?, ?>> teamFilter = e -> e.team == team && f.get(e);
		EntityArs.Unit.intersect(x - r, y - r, r * 2, r * 2, teamFilter::get, con::get);
		EntityArs.Building.intersect(x - r, y - r, r * 2, r * 2, teamFilter::get, con::get);
	}
}
