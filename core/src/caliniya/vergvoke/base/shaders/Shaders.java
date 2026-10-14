package caliniya.vergvoke.base.shaders;

public class Shaders {
  
  public static SpaceShader spaceShader;

  /// 建造/拆除进度着色器（BlockBuildShader）
  public static BlockBuildShader blockbuild;
  
	public static void load() {
		spaceShader = new SpaceShader();
		blockbuild = new BlockBuildShader();
	}
}
