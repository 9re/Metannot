package com.kayac.metannot.util;

public class Logger {
    public static final void log(Object... objects) {
        StringBuilder builder = new StringBuilder();
        
        for (Object object : objects) {
            builder
                .append(object)
                .append(' ');
        }
        
        System.out.println(builder);
    }
}
