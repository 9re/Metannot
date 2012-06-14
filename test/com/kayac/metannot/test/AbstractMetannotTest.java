package com.kayac.metannot.test;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.processing.Messager;

import com.kayac.metannot.annotation.Template;
import com.kayac.metannot.annotation.TemplateParameter;
import com.kayac.metannot.util.Logger;

public abstract class AbstractMetannotTest {
    public abstract void templateMethodWriter(Writer writer, Messager messager, Map<String, String> map) throws IOException;
    protected abstract void writeImports(Writer writer) throws IOException; 
    abstract void injectionClassWriter(Writer writer, Messager messager, Map<String, String> params) throws IOException;

    @SuppressWarnings("unused")
    @Template(writer = "templateMethodWriter")
    private void templateMethod(String str, Map<String, String> map) {
        for (Iterator<Entry<String, String>> iterator = map.entrySet().iterator();
             iterator.hasNext();) {
            iterator.next();
        }
    }
    
    @SuppressWarnings("unused")
    @Template(writer = "injectionClassWriter")
    private static final class InjectionClass {
        @TemplateParameter
        private String fields;
        
        private final Runnable mRunnable = new Runnable() {
            @Override
            public void run() {
                @TemplateParameter
                String runImpl;
                
                @TemplateParameter
                String fields;
            }
        };
    }
    
    public void test() {
        { // test imports
            StringWriter writer = new StringWriter();
            try {
                writeImports(writer);
                Logger.log(writer);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close(writer);
            }
        }
        
        { // test template method writer
            StringWriter writer = new StringWriter();
            
            try {
                templateMethodWriter(writer, null, null);
                Logger.log(writer);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close(writer);
            }
        }
        
        { // test template class writer
            StringWriter writer = new StringWriter();
            
            Map<String, String> params = new HashMap<String, String>();
            params.put("fields", "private String hoge;\npublic String hoge;");
            params.put("runImpl", "test test!!");
            try {
                injectionClassWriter(writer, null, params);
                Logger.log(writer);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close(writer);
            }
        }
    }
    
    private void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
