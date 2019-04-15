package com.zereao.img2textimg.converter;

import com.zereao.img2textimg.utils.Logger;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author Zereao
 * @version 2019/04/15 17:24
 */
public class ConverterFactory {
    private static Logger log = Logger.getInstance();

    /**
     * 静态图片文件格式 正则
     */
    private static final Pattern IMG_EXT_PATTERN = Pattern.compile("^.*\\.(png|jpg|jpeg|bmp)$");

    /**
     * 工厂方法，根据文件获取对应的转换器
     * 实际是根据文件格式---> 文件扩展名
     *
     * @param img 图片文件File对象
     * @return 对应的转换器
     */
    public static AbstractImgConverter getInstance(File img) {
        AbstractImgConverter converter = null;
        String imgPath = img.getAbsolutePath().toLowerCase();
        if (imgPath.endsWith("gif")) {
            converter = new GIF2TextImgConverter();
        } else if (IMG_EXT_PATTERN.matcher(imgPath).find()) {
            converter = new JPEG2TextImgConverter();
        } else {
            log.error(new IllegalArgumentException("未知的文件格式！"), "未知的图片格式！imgPath = {}", imgPath);
        }
        return converter;
    }
}
