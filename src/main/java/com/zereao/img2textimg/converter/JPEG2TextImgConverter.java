package com.zereao.img2textimg.converter;

import com.zereao.img2textimg.utils.ThreadPoolUtils;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

/**
 * @author Zereao
 * @version 2019/04/12 14:45
 */
public class JPEG2TextImgConverter extends AbstractImgConverter {

    @Override
    public void transfer2TextImg(InputStream source, String sourcePath) throws IOException {
        BufferedImage bi = ImageIO.read(source);
        int oldWidth = bi.getWidth();
        int oldHeight = bi.getHeight();
        int maxLine = 500;
        if (Math.min(oldHeight, oldWidth) > maxLine) {
            // 等比例缩放至 最长边为1000
            bi = Thumbnails.of(bi).size(maxLine, maxLine).asBufferedImage();
            log.info("将文件[{}]等比例压缩值最长边为{}px，保存路径为：{}", sourcePath, maxLine,
                    this.getCompressedFile(bi, sourcePath, maxLine).getAbsolutePath());
        }
        CountDownLatch latch = new CountDownLatch(3);
        for (int fontSize = 10; fontSize <= 14; fontSize++) {
            ThreadPoolUtils.execute(new Text2ImgTask(this.transfer2CharArray(bi), sourcePath, fontSize, latch));
            log.info("开始转换：fontSize = {}，zoom = {}，文件保存路径为：{}",
                    fontSize, fontSize / 2, super.getOutputFile(sourcePath, fontSize));
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error(e, "发生异常：CountDownLatch被中断啦~");
        }
    }
}
