package com.kayac.metannot.model;

public class MetannotTemplateParameter {
    public final int start;
    public final int end;
    public final String key;
    public MetannotTemplateParameter(int start, int end, String key) {
        this.start = start;
        this.end = end;
        this.key = key;
    }
}
