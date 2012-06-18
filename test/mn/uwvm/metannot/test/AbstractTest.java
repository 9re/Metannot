package mn.uwvm.metannot.test;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import javax.annotation.processing.Messager;

import mn.uwvm.metannot.annotation.Template;
import mn.uwvm.metannot.annotation.TemplateParameter;

public abstract class AbstractTest {
    @Template(className = "GenClass", writer = "genClassWriter")
    private final class InjectionTemplate implements Runnable {
        @Override
        public void run() {
            @TemplateParameter
            String[] files = null;
            
            for (String file : files) {
                File f = new File(file);
                if (f.exists()) {
                    f.delete();
                }
            }
        }
    }
    
    abstract void genClassWriter(Writer writer, Messager messager, Map<String, String> params, String typename) throws IOException;
    abstract void writeImports(Writer writer) throws IOException;
}
