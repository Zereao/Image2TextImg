package com.zereao.img2textimg.utils;

import java.util.Scanner;

/**
 * @author Zereao
 * @version 2019/04/15 15:57
 */
public class ScannerUtils {
    private enum InnerScanner {
        /**
         * 单例枚举
         */
        INSTANCE;

        private Scanner scanner;

        InnerScanner() {
            this.scanner = new Scanner(System.in);
        }
    }

    public static Scanner getInstance() {
        return InnerScanner.INSTANCE.scanner;
    }

    public static void close() {
        InnerScanner.INSTANCE.scanner.close();
    }
}
