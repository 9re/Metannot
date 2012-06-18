package com.kayac.metannot.model;

import java.util.List;



public class MetannotTemplate {
    public final String template;
    public final List<MetannotTemplateParameter> parameters;
    public final String writer;
    public final String templateName;
    
    public MetannotTemplate(String template, List<MetannotTemplateParameter> parameters, String writer, String templateName) {
        this.template = template;
        this.parameters = parameters;
        this.writer = writer;
        this.templateName = templateName;
    }
}
