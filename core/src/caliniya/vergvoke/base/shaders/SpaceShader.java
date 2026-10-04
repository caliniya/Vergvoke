package caliniya.vergvoke.base.shaders;

import arc.Core;
import arc.func.*;
import arc.graphics.Camera;
import arc.graphics.Texture;
import arc.graphics.TextureWrap;
import arc.graphics.g2d.Draw;
import arc.graphics.Shader;
import caliniya.vergvoke.core.Render;

//在绑定着色器之前任何的的setUniformf是无效的
public class SpaceShader extends Shader {

  private Texture texture;

  // 视差系数
  public float parallaxScale = 0.2f;
  // 基础缩放
  public float baseScale = 0.7f;

  public Camera cam;
  public float zoom;

  public Floatp get;

  public SpaceShader() {
    this(Core.camera, () -> Render.currentZoom);
  }

  public SpaceShader(Camera cam, Floatp get) {
    super(Core.files.internal("shaders/default.vert"), Core.files.internal("shaders/space.frag"));

    texture = new Texture(Core.files.internal("sprites/space.png"));
    texture.setWrap(TextureWrap.repeat, TextureWrap.repeat);
    this.cam = cam;
    this.zoom = get.get();
    this.get = get;
  }

  @Override
  public void apply() {
    zoom = get.get();
    float z = zoom * baseScale;

    setUniformf("u_resolution", Core.graphics.getWidth(), Core.graphics.getHeight());

    setUniformf("u_camPos", cam.position.x, cam.position.y);

    setUniformf("u_zoom", z);

    setUniformf("u_texSize", (float) texture.width, (float) texture.height);
    setUniformf("u_parallax", parallaxScale);

    texture.bind(0);
    setUniformi("u_texture", 0);
  }

  // 总之能用了
  public void render() {
    // Draw.shader(this);
    Draw.blit(this);
    // Draw.rect(Draw.wrap(texture),cam.position.x,cam.position.y);
    // Draw.flush();
    // Draw.shader();
  }

  @Override
  public void dispose() {
    super.dispose(); // 调用父类释放 shader
    if (texture != null)
      texture.dispose();
  }
}
