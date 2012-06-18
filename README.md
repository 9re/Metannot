Metannot
========

Metannot is a meta-annotation processor which generates annotation processor.

By annotating '@Template', you can make the method / class as template for annotation processing.


From

```java
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
```

Metannot generates the following source code.


```java
package mn.uwvm.metannot.test;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import javax.annotation.processing.Messager;
import mn.uwvm.metannot.annotation.Template;
import mn.uwvm.metannot.annotation.TemplateParameter;

@javax.annotation.Generated(value = "mn.uwvm.metannot.MetannotProcessor", date = "2012-06-18T19:41:02.525+0900")
public class Test extends mn.uwvm.metannot.test.AbstractTest {
void genClassWriter(Writer writer, Messager messager, Map<String, String> params, String typename) throws IOException {
    String template = "{\n    \n    private InjectionTemplate() {\n        super();\n    }\n    \n    @Override()\n    public void run() {\n        @TemplateParameter()\n        String[] files = null;\n        for (String file : files) {\n            File f = new File(file);\n            if (f.exists()) {\n                f.delete();\n            }\n        }\n    }\n}";
    int pos = 0;
    String writerName = "genClassWriter";
    StringBuilder builder = new StringBuilder();
    String[] parameters = new String[] {"files"};
    mn.uwvm.metannot.util.MetannotUtil.writeTemplateParameters(template, parameters, params, messager, writerName, builder);
    String output = builder.toString();
    if (typename != null) {
        output = mn.uwvm.metannot.util.MetannotUtil.replaceTokenInString(
            output, "InjectionTemplate", typename);
    }
    writer.append(output);
}
void writeImports(Writer writer) throws IOException {
    for (String strImport :
         new String[] {"java.io.File", "java.io.IOException", "java.io.Writer", "java.util.Map", "javax.annotation.processing.Messager", "mn.uwvm.metannot.annotation.Template", "mn.uwvm.metannot.annotation.TemplateParameter"}) {
        writer
            .append("import ")
            .append(strImport)
            .append(";\n");
    }
    writer.append('\n');
}
}
```


For more pragmatic example, i have written [MetannotProcessor](https://github.com/9re/Metannot/blob/master/test/mn/uwvm/metannot/test/AbstractMetannotProcessor.java) itself using MetannotProcessor.