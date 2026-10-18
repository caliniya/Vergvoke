package caliniya.vergvoke.world.building;

import arc.struct.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.type.type.*;

import java.util.*;

/// 适用于某个队伍的计划表
public class BuildPlan {

	public static final Object lock = new Object();

	public TeamType team;

	public Ar<Plan> plans;

	public Ar<Plan> freePlan;

	public BuildPlan(TeamType team) {
		this.team = team;
		plans = new Ar<>();
		freePlan = new Ar<>();
	}

	public void add(int x, int y, BuildingType type) {
		if (freePlan.isEmpty()) {
			plans.add(new Plan(x, y, type));
		} else {
			plans.add(freePlan.first().set(x, y, type));
			freePlan.remove(freePlan.first());
		}
	}

	public void remove(Plan plan) {
		plans.remove(plan);
		freePlan.add(plan);
	}


	public static class Plan {
		public int x;
		public int y;
		public BuildingType type;

		public Plan(int x, int y, BuildingType type) {
			this.x = x;
			this.y = y;
			this.type = type;
		}

		public Plan set(int x, int y, BuildingType type) {
			this.x = x;
			this.y = y;
			this.type = type;
			return this;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || obj.getClass() != this.getClass()) return false;
			var that = (Plan) obj;
			return this.x == that.x &&
					this.y == that.y &&
					Objects.equals(this.type, that.type);
		}

		@Override
		public int hashCode() {
			return Objects.hash(x, y, type);
		}

		@Override
		public String toString() {
			return "Plan[" +
					"x=" + x + ", " +
					"y=" + y + ", " +
					"type=" + type + ']';
		}


	}

}
