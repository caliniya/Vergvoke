package caliniya.vergvoke.system.render;

import arc.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.type.type.*;

public class BlockRender extends caliniya.vergvoke.system.System<BlockRender> {

	@Override
	public BlockRender init() {
		this.index = 12;
		Events.run(EventType.events.EnterUV, () -> paused = true);
		Events.run(EventType.events.ExitUV, () -> paused = false);
		return super.init(false, false);
	}

	@Override
	public void update(float delta) {
		// 遍历所有建筑
		for (Building b : EntityArs.Building) {
			if (b == null || b.health <= 0f)
				continue;

			BuildingType blk = b.type;
			if (blk == null)
				continue;

			// 剔除检测：如果不在视野内则跳过
			if (shouldDraw(b.x, b.y, blk.psize)) {
				// 绘制建筑（走 Entity.draw → BuildingType.draw）
				b.draw();
				// 阵营名（头顶，b.size 是像素边长，上边缘 = 中心 + 半边长）
				if (UnitRender.showTeam) {
					UnitRender.drawTeamTag(b, b.y + b.size * 0.5f + 8f);
				}
				// 调试绘制
				if (UnitRender.debug) { // 复用 UnitRender 的 debug 开关
					blk.drawDebug(b);
				}
				Draw.color(); // 重置颜色
			}
		}
	}

	// 视野剔除判断
	private boolean shouldDraw(float x, float y, float size) {
		// 使用相机位置进行判断
		float viewX = Core.camera.position.x;
		float viewY = Core.camera.position.y;
		// 稍微扩大缓冲区，防止边缘的建筑突然消失
		float buffer = size;
		float w = Core.camera.width / 2f + buffer;
		float h = Core.camera.height / 2f + buffer;
		return x > viewX - w && x < viewX + w && y > viewY - h && y < viewY + h;
	}
}
