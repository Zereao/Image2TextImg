package com.zereao.img2textimg.utils;

import lombok.NoArgsConstructor;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Zereao
 * @version 2019/04/15 09:58
 */
@NoArgsConstructor
public class Logger {
    private enum InnerLogger {
        /**
         * 单例枚举
         */
        INSTANCE;

        private Logger logger;

        InnerLogger() {
            this.logger = new Logger();
        }
    }

    private static ThreadLocal<SimpleDateFormat> sdfThreadLocal = new ThreadLocal<>();

    public static Logger getInstance() {
        sdfThreadLocal.remove();
        return InnerLogger.INSTANCE.logger;
    }

    public void info(String msg) {
        String data = this.getThreadLocal().format(new Date());
        String threadName = Thread.currentThread().getName();
        System.out.printf("%s | %s ----- %s\n", data, threadName, msg);
    }

    public void info(String msg, Object... params) {
        String data = this.getThreadLocal().format(new Date());
        String threadName = Thread.currentThread().getName();
        String resultMsg = msg.replaceAll("\\{}", "%s");
        String msgTemplate = data + " | " + threadName + " ----- " + resultMsg + "\n";
        System.out.printf(msgTemplate, params);
    }

    public void error(Throwable e, String msg, Object... params) {
        this.info(msg, params);
        throw new RuntimeException(e);
    }

    /**
     * 从ThreadLocal中获取变量，如果get()方法获取到的值为null，则执行set方法为当前ThreadLocal变量设置值
     *
     * @return 当前线程的SimpleDateFormat对象
     */
    private SimpleDateFormat getThreadLocal() {
        SimpleDateFormat sdf = sdfThreadLocal.get();
        if (sdf == null) {
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            sdfThreadLocal.set(sdf);
        }
        return sdf;
    }
}
