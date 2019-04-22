package com.zereao.img2textimg.converter;

import com.zereao.img2textimg.converter.gifencoder.AnimatedGifEncoder;
import com.zereao.img2textimg.converter.gifencoder.GifDecoder;
import lombok.AllArgsConstructor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author Zereao
 * @version 2019/04/12 14:45
 */
public class GIF2TextImgConverter extends AbstractImgConverter {
    @Override
    public void transfer2TextImg(InputStream source, String sourcePath) throws IOException {
        int fontSizePt = 8;
        GifDecoder.GifImage gif = GifDecoder.read(source);
        Map<Integer, BufferedImage> imgMap = new HashMap<>(16);
        for (int i = 0, frameNum = gif.getFrameCount(); i < frameNum; i++) {
            imgMap.put(i, gif.getFrame(i));
        }
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        ForkJoinTask<Map<Integer, BufferedImage>> task = forkJoinPool.submit(new Text2GifForkJoinTask(imgMap, fontSizePt));
        Map<Integer, BufferedImage> resultMap = new TreeMap<>();
        try {
            resultMap.putAll(task.get());
        } catch (InterruptedException | ExecutionException e) {
            log.error(e, "Text2GifForkJoinTask get()中断，或者执行异常！");
        }
        if (resultMap.size() <= 0) {
            log.info("图片[{}]转换失败！", sourcePath);
            return;
        }
        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        File outFile = this.getOutputFile(sourcePath, fontSizePt);
        boolean result = encoder.start(outFile);
        // 获取第二帧的延迟
        encoder.setDelay(gif.getDelay(1));
        resultMap.forEach((k, v) -> encoder.addFrame(v));
        result = encoder.finish();
        log.info("GIF转换成功！生成文件路径：{}", outFile.getAbsolutePath());
    }

    /**
     * GIF图片转GIF字符画多线程ForkJoin任务
     */
    @AllArgsConstructor
    private class Text2GifForkJoinTask extends RecursiveTask<Map<Integer, BufferedImage>> {
        private Map<Integer, BufferedImage> frameMap;
        private Integer fontSizePt;

        @Override
        protected Map<Integer, BufferedImage> compute() {
            int eachThreadTaskNum = 5;
            int zoom = fontSizePt / 2;
            boolean canCompute = frameMap.size() <= eachThreadTaskNum;
            Map<Integer, BufferedImage> resultMap = new ConcurrentHashMap<>();
            if (canCompute) {
                frameMap.forEach((index, img) -> resultMap.put(index, textToBufferedImage(transfer2CharArray(img), fontSizePt, zoom)));
                return resultMap;
            } else {
                List<Text2GifForkJoinTask> taskList = new ArrayList<>();
                // 拆分任务的数量 24 / 5 = 4 ······ 1  ，  25 / 5 = 5
                int splitNum = frameMap.size() % eachThreadTaskNum == 0 ? (frameMap.size() / eachThreadTaskNum) : (frameMap.size() / eachThreadTaskNum + 1);
                for (int i = 1; i <= splitNum; i++) {
                    Map<Integer, BufferedImage> pre5EntryMap = frameMap.entrySet().stream().limit(5).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    Text2GifForkJoinTask task = new Text2GifForkJoinTask(pre5EntryMap, fontSizePt);
                    taskList.add(task);
                    pre5EntryMap.forEach((k, v) -> frameMap.remove(k));
                    task.fork();
                }
                taskList.forEach(task -> resultMap.putAll(task.join()));
            }
            return resultMap;
        }
    }
}
