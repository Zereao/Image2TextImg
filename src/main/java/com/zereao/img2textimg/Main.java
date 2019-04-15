package com.zereao.img2textimg;

import com.zereao.img2textimg.converter.AbstractImgConverter;
import com.zereao.img2textimg.converter.ConverterFactory;
import com.zereao.img2textimg.utils.Logger;
import com.zereao.img2textimg.utils.ScannerUtils;
import com.zereao.img2textimg.utils.ThreadPoolUtils;
import lombok.AllArgsConstructor;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Zereao
 * @version 2019/04/12 14:33
 */
public class Main {
    private static final Pattern IMG_EXT_PATTERN = Pattern.compile("^.*\\.(gif|png|jpg|jpeg|bmp)$");

    private static Logger log = Logger.getInstance();

    /**
     * 启动方法，main方法
     *
     * @param args 命令行传入的参数
     */
    public static void main(String[] args) {
        try {
            Main main = new Main();
            List<File> fileList = main.getTargetFiles();
            if (fileList.size() > 2) {
                CountDownLatch latch = new CountDownLatch(fileList.size());
                fileList.forEach(img -> ThreadPoolUtils.execute(main.new MultiTask(img, latch)));
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    log.error(e, "发生异常：CountDownLatch被中断啦~");
                }
            } else {
                fileList.forEach(img -> {
                    String imgPath = img.getAbsolutePath();
                    try (FileInputStream fis = new FileInputStream(img)) {
                        ConverterFactory.getInstance(img).transfer2TextImg(fis, imgPath);
                    } catch (IOException e) {
                        log.error(e, "图片转化失败！,filePath = {}", imgPath);
                    }
                });
            }
        } finally {
            ThreadPoolUtils.shutdown();
        }
        log.info("图片转换完毕~ Bye~");
    }

    /**
     * 获取需要处理的图片文件
     *
     * @return 需要处理的文件List
     */
    private List<File> getTargetFiles() {
        try (InputStream is = this.getClass().getResourceAsStream("/banner.txt");
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null && !"".equals(line)) {
                System.out.println(line);
            }
        } catch (IOException e) {
            log.info("读取banner文件失败！跳过banner输出！");
        }
        File folder = new File(".");
        String currentFolder = folder.getAbsolutePath();
        System.out.printf("使用前，请将需要转换的图片文件放到【%s】路径下！\n\n", currentFolder);
        File[] imgFiles = folder.listFiles(new ImgFileFilter());
        if (imgFiles == null || imgFiles.length <= 0) {
            log.error(new IOException("当前文件夹下不存在图片文件！"), "当前文件夹[{}]下不存在图片文件！", currentFolder);
        }
        int index = 1;
        // log.error()会抛出一个RuntimeException，所以这里直接断言
        assert imgFiles != null;
        for (File imgFile : imgFiles) {
            System.out.printf("%s：%s\n", index, imgFile.getAbsolutePath());
            ++index;
        }
        System.out.print("\n请输入需要转换的图片前面的数字，转换多张图片使用 半角逗号、全角逗号或空格 隔开，按回车键继续:");
        Scanner scanner = ScannerUtils.getInstance();
        String indexStr = scanner.nextLine();
        String[] indexes = indexStr.split("[,， ]");
        log.info("您选择的图片为：{}", Arrays.toString(indexes));
        List<File> fileList = new ArrayList<>();
        for (String idx : indexes) {
            fileList.add(imgFiles[Integer.valueOf(idx) - 1]);
        }
        return fileList;
    }

    /**
     * 如果用户选择了超过2个文件，则使用多线程处理；多线程任务
     */
    @AllArgsConstructor
    private class MultiTask implements Runnable {
        private File img;
        private CountDownLatch latch;

        @Override
        public void run() {
            AbstractImgConverter converter = ConverterFactory.getInstance(img);
            String imgPath = img.getAbsolutePath();
            try (FileInputStream fis = new FileInputStream(img)) {
                converter.transfer2TextImg(fis, imgPath);
            } catch (IOException e) {
                log.error(e, "图片转换失败！filePath = {}", imgPath);
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * File.list() 图片文件格式过滤器
     */
    private class ImgFileFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            Matcher matcher = IMG_EXT_PATTERN.matcher(name.toLowerCase());
            return matcher.find();
        }
    }
}
