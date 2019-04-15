package com.zereao.img2textimg.utils;

import java.util.concurrent.*;

/**
 * 线程池
 *
 * @author Zereao
 * @version 2019/04/12 14:34
 */
public class ThreadPoolUtils {
    private static ExecutorService executor = new ThreadPoolExecutor(
            10,
            15,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy());

    public static void execute(Runnable task) {
        executor.execute(task);
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    public static void shutdown() {
        executor.shutdown();
    }
}
