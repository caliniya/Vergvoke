package caliniya.tools;

import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.struct.Ar;
import arc.util.Log;
import arc.util.Time;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

public class IconGen {
    float width, height;

    public static void main(String[] args) {
        // 设置无头模式，避免 AWT 在服务器环境报错
        System.setProperty("java.awt.headless", "true");

        if (args.length < 2) {
            Log.info("Usage: java -jar IconGen.jar <input_png_directory> <output_svg_directory> [extra_svg_directory]");
            System.exit(1);
        }

        Fi inputDir = new Fi(args[0]);
        Fi outputDir = new Fi(args[1]);
        Fi extraDir = args.length > 2 ? new Fi(args[2]) : null;

        Log.info("Converting icons...");
        Time.mark();

        outputDir.deleteDirectory();
        outputDir.mkdirs();

        Ar<Fi> files = new Ar<>();

        for (Fi img : inputDir.list()) {
            if (img.extension().equals("png")) {
                Fi dst = outputDir.child(img.nameWithoutExtension().replace("icon-", "") + ".svg");
                new IconGen().convert(new Pixmap(img), dst);
                files.add(dst);
            }
        }

        if (extraDir != null && extraDir.exists()) {
            extraDir.findAll().each(f -> f.copyTo(outputDir.child(f.name())));
        }

        Log.info("Done converting icons in @ ms.", Time.elapsed());
        System.exit(0);
    }

    void convert(Pixmap pixmap, Fi output) {
        boolean[][] grid = new boolean[pixmap.width][pixmap.height];

        for (int x = 0; x < pixmap.width; x++) {
            for (int y = 0; y < pixmap.height; y++) {
                grid[x][pixmap.height - 1 - y] = !pixmap.empty(x, y);
            }
        }

        float xscl = 1f, yscl = 1f;
        float scl = xscl;

        width = pixmap.width;
        height = pixmap.height;

        // 使用 java.awt.geom.Area 来累积所有形状
        // 这相当于 Inkscape 的 "path-union" 功能
        Area totalArea = new Area();

        for (int x = -1; x < pixmap.width; x++) {
            for (int y = -1; y < pixmap.height; y++) {
                int index = index(x, y, pixmap.width, pixmap.height, grid);

                float leftx = x * xscl, boty = y * yscl, rightx = x * xscl + xscl, topy = y * xscl + yscl,
                        midx = x * xscl + xscl / 2f, midy = y * yscl + yscl / 2f;

                switch (index) {
                    case 0: break;
                    case 1: addTri(totalArea, leftx, midy, leftx, topy, midx, topy); break;
                    case 2: addTri(totalArea, midx, topy, rightx, topy, rightx, midy); break;
                    case 3: addRect(totalArea, leftx, midy, scl, scl / 2f); break;
                    case 4: addTri(totalArea, midx, boty, rightx, boty, rightx, midy); break;
                    case 5:
                        addTri(totalArea, leftx, midy, midx, midy, midx, boty);
                        addTri(totalArea, midx, topy, midx, midy, rightx, midy);
                        addRect(totalArea, leftx, midy, scl / 2f, scl / 2f);
                        addRect(totalArea, midx, boty, scl / 2f, scl / 2f);
                        break;
                    case 6: addRect(totalArea, midx, boty, scl / 2f, scl); break;
                    case 7:
                        addTri(totalArea, leftx, midy, midx, midy, midx, boty);
                        addRect(totalArea, leftx, midy, scl, scl / 2f);
                        addRect(totalArea, midx, boty, scl / 2f, scl / 2f);
                        break;
                    case 8: addTri(totalArea, leftx, boty, leftx, midy, midx, boty); break;
                    case 9: addRect(totalArea, leftx, boty, scl / 2f, scl); break;
                    case 10:
                        addTri(totalArea, midx, boty, midx, midy, rightx, midy);
                        addTri(totalArea, leftx, midy, midx, midy, midx, topy);
                        addRect(totalArea, midx, midy, scl / 2f, scl / 2f);
                        addRect(totalArea, leftx, boty, scl / 2f, scl / 2f);
                        break;
                    case 11:
                        addTri(totalArea, midx, boty, midx, midy, rightx, midy);
                        addRect(totalArea, leftx, midy, scl, scl / 2f);
                        addRect(totalArea, leftx, boty, scl / 2f, scl / 2f);
                        break;
                    case 12: addRect(totalArea, leftx, boty, scl, scl / 2f); break;
                    case 13:
                        addTri(totalArea, midx, topy, midx, midy, rightx, midy);
                        addRect(totalArea, leftx, boty, scl, scl / 2f);
                        addRect(totalArea, leftx, midy, scl / 2f, scl / 2f);
                        break;
                    case 14:
                        addTri(totalArea, leftx, midy, midx, midy, midx, topy);
                        addRect(totalArea, leftx, boty, scl, scl / 2f);
                        addRect(totalArea, midx, midy, scl / 2f, scl / 2f);
                        break;
                    case 15: addSquare(totalArea, midx, midy, scl); break;
                }
            }
        }

        // 将 Area 转换为 SVG 并写入文件
        writeAreaToSvg(totalArea, output);
    }

    // 辅助方法：创建 Area 对象并添加到 totalArea
    void addTri(Area area, float x1, float y1, float x2, float y2, float x3, float y3) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x1 + 0.5f, flip(y1 + 0.5f));
        path.lineTo(x2 + 0.5f, flip(y2 + 0.5f));
        path.lineTo(x3 + 0.5f, flip(y3 + 0.5f));
        path.closePath();
        area.add(new Area(path));
    }

    void addRect(Area area, float x1, float y1, float w, float h) {
        // AWT 的矩形 y 坐标是左上角，SVG/Pixmap 是左下角逻辑，需注意转换
        // 这里的 y1 是底部 y，flip 后是顶部 y
        float startY = flip(y1 + 0.5f) - h;
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x1 + 0.5f, startY);
        path.lineTo(x1 + 0.5f + w, startY);
        path.lineTo(x1 + 0.5f + w, startY + h);
        path.lineTo(x1 + 0.5f, startY + h);
        path.closePath();
        area.add(new Area(path));
    }

    void addSquare(Area area, float x, float y, float size) {
        addRect(area, x - size/2f, y - size/2f, size, size);
    }

    float flip(float y) {
        return height - y;
    }

    int index(int x, int y, int w, int h, boolean[][] grid) {
        int botleft = sample(grid, x, y);
        int botright = sample(grid, x + 1, y);
        int topright = sample(grid, x + 1, y + 1);
        int topleft = sample(grid, x, y + 1);
        return (botleft << 3) | (botright << 2) | (topright << 1) | topleft;
    }

    int sample(boolean[][] grid, int x, int y) {
        return (x < 0 || y < 0 || x >= grid.length || y >= grid[0].length) ? 0 : grid[x][y] ? 1 : 0;
    }

    /**
     * 将 Area 对象写入 SVG 文件
     * 实现了 Inkscape 的 "fit-canvas-to-selection" 功能
     */
    void writeAreaToSvg(Area area, Fi output) {
        Rectangle2D bounds = area.getBounds2D();
        
        // 如果图形为空，创建空 SVG
        if (bounds.isEmpty()) {
            output.writeString("<svg width=\"0\" height=\"0\"></svg>");
            return;
        }

        StringBuilder out = new StringBuilder();
        
        // 设置画布大小为图形实际大小 (Fit Canvas)
        int svgWidth = (int) Math.ceil(bounds.getWidth());
        int svgHeight = (int) Math.ceil(bounds.getHeight());
        
        out.append("<svg width=\"").append(svgWidth)
           .append("\" height=\"").append(svgHeight)
           .append("\" viewBox=\"").append(bounds.getX()).append(" ")
           .append(bounds.getY()).append(" ")
           .append(bounds.getWidth()).append(" ")
           .append(bounds.getHeight()).append("\">\n");

        // 将 Area 转换为 SVG Path Data
        out.append("<path d=\"");
        PathIterator iter = area.getPathIterator(null);
        float[] coords = new float[6];
        
        while (!iter.isDone()) {
            int type = iter.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    out.append("M ").append(format(coords[0])).append(" ").append(format(coords[1])).append(" ");
                    break;
                case PathIterator.SEG_LINETO:
                    out.append("L ").append(format(coords[0])).append(" ").append(format(coords[1])).append(" ");
                    break;
                case PathIterator.SEG_CLOSE:
                    out.append("Z ");
                    break;
                // Area 通常只产生直线段，但在理论上可能包含曲线
                case PathIterator.SEG_QUADTO:
                     out.append("Q ").append(format(coords[0])).append(" ").append(format(coords[1]))
                        .append(" ").append(format(coords[2])).append(" ").append(format(coords[3])).append(" ");
                    break;
                case PathIterator.SEG_CUBICTO:
                    out.append("C ").append(format(coords[0])).append(" ").append(format(coords[1]))
                       .append(" ").append(format(coords[2])).append(" ").append(format(coords[3]))
                       .append(" ").append(format(coords[4])).append(" ").append(format(coords[5])).append(" ");
                    break;
            }
            iter.next();
        }
        
        out.append("\" style=\"fill:white\" />\n");
        out.append("</svg>");

        output.writeString(out.toString());
    }
    
    private String format(float val) {
        // 简单的格式化，去掉多余的 .0
        if (val == (int)val) return String.valueOf((int)val);
        return String.valueOf(val);
    }
}