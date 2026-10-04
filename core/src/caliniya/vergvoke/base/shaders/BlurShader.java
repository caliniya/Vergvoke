package caliniya.vergvoke.base.shaders;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Shader;

public class BlurShader extends Shader {
    // 这是一个简单的单次遍历模糊算法
    public static final String fragmentSource = 
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_resolution;\n" +
        "uniform float u_radius;\n" +
        "varying vec2 v_texCoords;\n" +
        "\n" +
        "void main(){\n" +
        "    vec4 color = vec4(0.0);\n" +
        "    vec2 size = vec2(1.0) / u_resolution;\n" +
        "    // 9点采样近似高斯模糊\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2(-1.0, -1.0) * size * u_radius) * 0.0625;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2( 0.0, -1.0) * size * u_radius) * 0.125;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2( 1.0, -1.0) * size * u_radius) * 0.0625;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2(-1.0,  0.0) * size * u_radius) * 0.125;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2( 0.0,  0.0) * size * u_radius) * 0.25;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2( 1.0,  0.0) * size * u_radius) * 0.125;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2(-1.0,  1.0) * size * u_radius) * 0.0625;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2( 0.0,  1.0) * size * u_radius) * 0.125;\n" +
        "    color += texture2D(u_texture, v_texCoords + vec2( 1.0,  1.0) * size * u_radius) * 0.0625;\n" +
        "    \n" +
        "    gl_FragColor = color;\n" +
        "}";

    public BlurShader() {
        // 使用默认的顶点着色器，传入自定义的片段着色器字符串
        super(Core.files.internal("shaders/default.vert"), 
              new Fi("blur_dummy.frag") { 
                  @Override public String readString() { return fragmentSource; } 
              });
    }

    @Override
    public void apply() {
        // 设置模糊参数
        setUniformf("u_resolution", Core.graphics.getWidth(), Core.graphics.getHeight());
        setUniformf("u_radius", 8.0f); // 调整这个值来改变模糊强度 (建议 4.0 - 12.0)
    }
}