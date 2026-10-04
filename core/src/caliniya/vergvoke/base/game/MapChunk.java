package caliniya.vergvoke.base.game;

import arc.Core;
import arc.graphics.Camera;
import arc.graphics.Color;
import arc.graphics.FrameBuffer;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.util.Disposable;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.system.render.MapRender;
import caliniya.vergvoke.world.ENVBlock;
import caliniya.vergvoke.world.Floor;

public class MapChunk implements Disposable {
  public static final int SIZE = 32;
  public static final int PIXEL_SIZE = SIZE * (int) MapRender.TILE_SIZE;

  public int chunkX, chunkY;
  public FrameBuffer fbo;
  public boolean dirty = true;

  // 烘焙摄像机
  private static Camera bakeCamera;

  public MapChunk(int chunkX, int chunkY) {
    this.chunkX = chunkX;
    this.chunkY = chunkY;
    this.fbo = new FrameBuffer(PIXEL_SIZE, PIXEL_SIZE);

    // 懒加载初始化烘焙摄像机 (所有 Chunk 共用一个)
    if (bakeCamera == null) {
      bakeCamera = new Camera();
    }
  }

  public void bake() {
    if (!dirty) return;
    Draw.color();

    // 设置烘焙摄像机
    // 我们希望摄像机正好对准 FBO 的中心，视野大小完全覆盖 PIXEL_SIZE
    // FBO 的本地坐标系是左下角 (0,0)，右上角 (PIXEL_SIZE, PIXEL_SIZE)
    bakeCamera.width = PIXEL_SIZE;
    bakeCamera.height = PIXEL_SIZE;
    bakeCamera.position.set(PIXEL_SIZE / 2f, PIXEL_SIZE / 2f);
    bakeCamera.update();

    // 开始捕获
    fbo.begin(Color.clear);

    // 应用烘焙摄像机的投影矩阵
    // 保存之前的投影矩阵，绘制完后恢复，防止影响屏幕其他内容的绘制
    // Arc 的 Draw.proj() 会设置 Batch 的投影矩阵
    Draw.proj(bakeCamera);

    int startX = chunkX * SIZE;
    int startY = chunkY * SIZE;
    // 边界检查，防止画出黑边或越界
    int endX = Math.min(startX + SIZE, WorldData.world.W);
    int endY = Math.min(startY + SIZE, WorldData.world.H);

    for (int y = startY; y < endY; y++) {
      for (int x = startX; x < endX; x++) {
        // 计算"本地"绘制坐标
        // 在 FBO 里，左下角的格子坐标应该是 (TILE/2, TILE/2)
        float localDrawX = (x - startX) * MapRender.TILE_SIZE + MapRender.TILE_SIZE / 2f;
        float localDrawY = (y - startY) * MapRender.TILE_SIZE + MapRender.TILE_SIZE / 2f;
        
        Floor floor = WorldData.world.getFloor(x,y);
        if (floor != null && floor.region != null) {
          Draw.rect(floor.region, localDrawX, localDrawY, MapRender.TILE_SIZE, MapRender.TILE_SIZE);
        }

        ENVBlock block = WorldData.world.getENVBlock(x,y);
        if (block != null && block.region != null) {
          Draw.rect(block.region, localDrawX, localDrawY, MapRender.TILE_SIZE, MapRender.TILE_SIZE);
        }
      }
    }

    // 强制刷新 Batch，确保所有绘制指令都提交到了 FBO
    Draw.flush();

    fbo.end();
    dirty = false;

    // 恢复主摄像机的投影矩阵
    // 否则会导致后续渲染（如单位）使用烘焙摄像机的矩阵，变得极小或不可见
    Draw.proj(Core.camera);
  }

  public void render() {
    if (dirty) bake();

    TextureRegion region = new TextureRegion(fbo.getTexture());
    // Y 轴翻转
    region.flip(false, true);

    // 计算在世界坐标中的绘制位置
    float worldDrawX = chunkX * PIXEL_SIZE + PIXEL_SIZE / 2f;
    float worldDrawY = chunkY * PIXEL_SIZE + PIXEL_SIZE / 2f;

    Draw.rect(region, worldDrawX, worldDrawY, PIXEL_SIZE, PIXEL_SIZE);
  }

  @Override
  public void dispose() {
    if (fbo != null) fbo.dispose();
  }
}
