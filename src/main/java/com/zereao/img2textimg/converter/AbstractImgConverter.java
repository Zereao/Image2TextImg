package com.zereao.img2textimg.converter;

import com.zereao.img2textimg.utils.Logger;
import lombok.AllArgsConstructor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

/**
 * @author Zereao
 * @version 2019/04/12 14:46
 */
public abstract class AbstractImgConverter {

    protected Logger log = Logger.getInstance();

    /**
     * 组成图案的基本字符元素
     */
    private final String strElements = "@#&$%*o!; ";

    /**
     * 将图片转换为字符画，由子类实现
     *
     * @param source     源文件的输入流
     * @param sourcePath 源文件路径，生成的文件也将放在该路径下
     * @throws IOException IO异常
     */
    public abstract void transfer2TextImg(InputStream source, String sourcePath) throws IOException;

    /**
     * 图片转字符数组，宽 x 高 = 1 x 1
     * 返回的二维数组的大小等于 Height ； 元素的大小等于 Width
     *
     * @param img BufferedImage图片
     * @return 转换后二维字符数组
     */
    public char[][] transfer2CharArray(BufferedImage img) {
        int len = strElements.length();
        int width = img.getWidth();
        int height = img.getHeight();
        char[][] result = new char[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int index = this.getCharIndex(img.getRGB(j, i));
                result[i][j] = index >= len ? ' ' : strElements.charAt(index);
            }
        }
        return result;
    }

    /**
     * 图片转字符再保存为图片，并写入本地磁盘
     *
     * @param chars      原图转出的二维字符数组
     * @param fontSizePt 转换出的图片中的文字大小，单位 pt(磅)，推荐 8；
     * @param zoom       缩放倍数，推荐传 8 / 2 =4；
     * @return 生成的文件的信息Map
     */
    public BufferedImage textToBufferedImage(char[][] chars, int fontSizePt, int zoom) {
        int width = chars[0].length * zoom;
        int height = chars.length * zoom;
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // 获取图像上下文
        Graphics graphics = this.createGraphics(bufferedImage, width, height, fontSizePt);
        /* 字体大小 单位换算   磅 pt = 像素 px 乘 3/4
            int interval = fontSizePt * 3 / 4 / zoom + 1;  然而 zoom = fontSizePt / 2 ，所以计算出 interval 为固定值 2  */
        int interval = 2;
        int fontSizePx = Math.round(fontSizePt * 3 / 4f);
        log.info("计算出图片的字体大小为：{}磅，即{}像素；字体间隔为：{}像素", fontSizePt, fontSizePx, interval);
        for (int i = 0, lenOfH = chars.length; i < lenOfH; i += interval) {
            for (int j = 0, lenOfW = chars[i].length; j < lenOfW; j += interval) {
                graphics.drawString(String.valueOf(chars[i][j]), j * zoom, i * zoom);
            }
        }
        graphics.dispose();
        return bufferedImage;
    }

    /**
     * 图片转字符画多线程任务
     */
    @AllArgsConstructor
    public class Text2ImgTask implements Runnable {
        private char[][] chars;
        private String outPath;
        private int fontSize;
        private CountDownLatch latch;

        @Override
        public void run() {
            try {
                int zoom = fontSize / 2;
                File outImg = getOutputFile(outPath, fontSize);
                BufferedImage img = textToBufferedImage(chars, fontSize, zoom);
                boolean result = ImageIO.write(img, outPath.substring(outPath.lastIndexOf(".") + 1), outImg);
                log.info("转换{}！生成文件路径：{}", result ? "成功" : "失败", outImg.getAbsolutePath());
            } catch (IOException e) {
                log.error(e, "图片转化 Text2ImgTask 出现IOException！");
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * 根据相关参数，生成文件在源文件的基础上，在文件名中加上 字体大小、缩放倍数等参数配置信息
     *
     * @param outPath  源文件路径
     * @param fontSize 字体大小
     * @return 输出文件的File对象
     */
    protected File getOutputFile(String outPath, int fontSize) {
        StringBuilder config = new StringBuilder("_fontsize=").append(fontSize).append("_zoom=").append(fontSize / 2);
        File out = new File(new StringBuilder(outPath).insert(outPath.lastIndexOf("."), config).toString());
        if (!out.getParentFile().exists()) {
            boolean mkdirs = out.mkdirs();
        }
        return out;
    }

    protected File getCompressedFile(BufferedImage bi, String sourcePath, int maxLine) {
        String ext = sourcePath.substring(sourcePath.lastIndexOf(".") + 1);
        StringBuilder config = new StringBuilder("_maxLine=").append(maxLine);
        String outPath = new StringBuilder(sourcePath).insert(sourcePath.lastIndexOf("."), config).toString();
        File out = new File(outPath);
        try {
            ImageIO.write(bi, ext, out);
        } catch (IOException e) {
            log.error(e, "文件[{}]压缩后，保存失败！outPath = {}", sourcePath, outPath);
        }
        return out;
    }

    /**
     * 提取的公共方法，根据像素值pixel获取该像素应该填充的字符元素的索引
     *
     * @param pixel 像素值
     * @return 填充元素的索引
     */
    private int getCharIndex(int pixel) {
        // 下面三行代码将一个数字转换为RGB数字
        int red = (pixel & 0xff0000) >> 16;
        int green = (pixel & 0xff00) >> 8;
        int blue = (pixel & 0xff);
        float gray = 0.299f * red + 0.578f * green + 0.114f * blue;
        return Math.round(gray * (strElements.length() + 1) / 255);
    }

    /**
     * 画板默认一些参数设置
     *
     * @param image    图片
     * @param width    图片宽
     * @param height   图片高
     * @param fontSize 字体大小
     * @return 创建的Graphics对象
     */
    private Graphics createGraphics(BufferedImage image, int width, int height, int fontSize) {
        Graphics graphics = image.createGraphics();
        // 设置背景色
        graphics.setColor(null);
        // 绘制背景
        graphics.fillRect(0, 0, width, height);
        // 设置前景色
        graphics.setColor(Color.BLACK);
        // 设置字体
        graphics.setFont(new Font("宋体", Font.PLAIN, fontSize));
        return graphics;
    }
}
