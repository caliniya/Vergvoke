package caliniya.vergvoke.base.shaders;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;

/**
 * 建造进度着色器（Mindustry blockbuild 移植，frag 文件零改动）。
 *
 * <p>三层效果：中心擦除遮挡（progress）→ 前沿 2px 亮边 → 轮廓扫描条纹（time 滚动）。
 * 亮边/扫描线颜色 = 绘制时的 batch color（{@code Draw.color}）。
 *
 * <p>用法：设 progress/time/region → {@code Draw.shader(this)} → {@code Draw.rect(...)}
 * → {@code Draw.shader()} 恢复（切批后 uniform 才对下一批生效）。
 */
public class BlockBuildShader extends Shader {

	/// 建造进度 0..1（遮挡比例：0 全挡、1 全显）
	public float progress;

	/// 整体不透明度
	public float alpha = 1f;

	/// 全局时间（扫描条纹滚动）
	public float time;

	/// 当前绘制的贴图区域（每批前设）
	public TextureRegion region = new TextureRegion();


	public BlockBuildShader() {
		super(Core.files.internal("shaders/default2.vert"), Core.files.internal("shaders/blockbuild.frag"));
	}

	@Override
	public void apply() {
		setUniformf("u_progress", progress);
		setUniformf("u_time", time);
		setUniformf("u_alpha", alpha);

		if (region.texture == null) {
			setUniformf("u_uv", 0f, 0f);
			setUniformf("u_uv2", 1f, 1f);
			setUniformf("u_texsize", 1, 1);
		} else {
			setUniformf("u_uv", region.u, region.v);
			setUniformf("u_uv2", region.u2, region.v2);
			setUniformf("u_texsize", region.texture.width, region.texture.height);
		}
	}
}
