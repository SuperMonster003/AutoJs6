package com.huaban.analysis.jieba;

/**
 * @description: enable output content to be controlled by switch
 * @author: sharkdoodoo@foxmail.com
 * @date: 2022/6/21
 */
public class Log {

    private static final boolean LOG_ENABLE = Boolean.parseBoolean(System.getProperty("jieba.log.enable", "true"));

    public static final void debug(String debugInfo) {
        if (LOG_ENABLE) {
            System.out.println(debugInfo);
        }
    }

    public static final void error(String errorInfo) {
        if (LOG_ENABLE) {
            System.err.println(errorInfo);
        }
    }

}
