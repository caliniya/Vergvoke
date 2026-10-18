package caliniya.vergvoke.type.ai;

import caliniya.vergvoke.base.ecs.*;

public class BuildAI extends AI {


	@Override
	public void update(Unit u, float dt) {

	}

	@Override
	public AI copy() {
		return new BuildAI();
	}
}
