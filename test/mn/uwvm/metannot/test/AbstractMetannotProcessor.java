package mn.uwvm.metannot.test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import mn.uwvm.metannot.annotation.Template;
import mn.uwvm.metannot.annotation.TemplateParameter;
import mn.uwvm.metannot.model.MetannotTemplate;
import mn.uwvm.metannot.model.MetannotTemplateParameter;
import mn.uwvm.metannot.util.Logger;
import mn.uwvm.metannot.util.MetannotUtil;
import mn.uwvm.metannot.visitor.TemplateVisitor;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;

@SupportedAnnotationTypes("mn.uwvm.metannot.annotation.Template")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public abstract class AbstractMetannotProcessor extends AbstractProcessor {
    private static final String IMPORT_WRITER = "writeImports";
    private Trees mTrees;
    private Set<String> mVisitedClass = new HashSet<String>();
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        
        mTrees = Trees.instance(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        boolean processed = false;
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(annotation)) {
                TypeElement typeElement = (TypeElement) element.getEnclosingElement();
                if (handleEnclosingElement(typeElement)) {
                    processed = true;
                }
            }
        }
        return processed;
    }

    private boolean handleEnclosingElement(TypeElement typeElement) {
        final JCCompilationUnit unit;
        {
            TreePath treePath = mTrees.getPath(typeElement);
            unit = (JCCompilationUnit) (treePath != null ? treePath.getCompilationUnit() : null);
        }
        if (unit == null) {
            return false;
        }
        
        if (unit.sourcefile.getKind() != JavaFileObject.Kind.SOURCE) {
            Logger.log("skipped:", unit.sourcefile);
            return false;
        }
        
        processClass(unit, typeElement);
        return false;
    }
    
    private boolean processClass(JCCompilationUnit unit, TypeElement element) {
        boolean processed = false;
        String packageName = element.getEnclosingElement().toString();
        String originalName = element.getQualifiedName().toString();
        String originalSimpleName = originalName.substring(packageName.length() + 1);
        
        if (!originalSimpleName.startsWith("Abstract")) {
            processingEnv.getMessager()
                .printMessage(Kind.ERROR, "class name using @" + Template.class.getSimpleName()
                    + " should start with Abstract");
            return false;
        }
        String simpleName = originalSimpleName.substring("Abstract".length());
        String sourceName = packageName + "." + simpleName;
        if (mVisitedClass.contains(sourceName)) {
            return false;
        }
        
        Filer filer = processingEnv.getFiler();
        Writer writer = null;
        JavaFileObject javaFileObject;
        
        
        try {
            javaFileObject = filer.createSourceFile(sourceName);
            writer = javaFileObject.openWriter();
            
            TemplateVisitor templateVisitor = new TemplateVisitor(processingEnv);
            unit.accept(templateVisitor);
            
            String generationTime = String.format("%1$tFT%1$tH:%1$tM:%1$tS.%1$tL%1$tz", new Date(System.currentTimeMillis()));
            writer.append("package ").append(packageName).append(";\n\n");
            
            writeImports(writer);
            
            writer
                .append("@javax.annotation.Generated(value = \"" + this.getClass().getName() + "\", date = \"" + generationTime + "\")\n");
            
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                writer.append(mirror.toString()).append('\n');
            }
            
            writer
                .append("public class ")
                .append(simpleName)
                .append(" extends ")
                .append(originalName)
                .append(' ');
            
            Map<String, String> templateParams = new HashMap<String, String>();
            //
            {
                StringWriter stringWriter = new StringWriter();
                writeTemplateWriters(templateVisitor, element, stringWriter);
                templateParams.put("templateWriters", stringWriter.toString());
            }
            writeImportWriter(templateVisitor, element, templateParams);
            writeClassImpl(writer, processingEnv.getMessager(), templateParams, simpleName);
            //
            writer.flush();
            processed = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        
        mVisitedClass.add(sourceName);
        
        return processed;
    }
    
    private void writeImportWriter(TemplateVisitor templateVisitor, TypeElement typeElement, Map<String, String> templateParams) throws IOException {
        { // 
            Map<String, JCMethodDecl> absMethods = templateVisitor.getAbstractMethods();
            if (!absMethods.containsKey(IMPORT_WRITER)) {
                processingEnv.getMessager()
                    .printMessage(Kind.ERROR, "Declare abstract method void writeImports(java.io.Writer writer) throws IOException; in " + typeElement.getQualifiedName() + "!");
                return;
            }
            
            JCMethodDecl writerDecl = absMethods.get(IMPORT_WRITER);
            
            boolean hasIOException = false;
            boolean hasOtherException = false;
            for (JCExpression expression : writerDecl.thrown) {
                if ("IOException".equals(expression.toString())) {
                    hasIOException = true;
                } else {
                    hasOtherException = true;
                    break;
                }
            }
            
            if (!hasIOException || hasOtherException) {
                processingEnv.getMessager()
                    .printMessage(Kind.ERROR,
                        "Throws expression should be 'throws IOException' at " + IMPORT_WRITER + " in " + typeElement);
                return;
            }
        }
        
        StringBuilder builder = new StringBuilder();
        builder.append("new String[] {");
        for (Iterator<String> iter = templateVisitor.getImports().iterator();;) {
            builder
                .append("\"")
                .append(iter.next())
                .append("\"");
            if (iter.hasNext()) {
                builder.append(", ");
            } else {
                break;
            }
        }
        builder.append("}");
        
        templateParams.put("strImports", builder.toString());
    }
    
    private void writeTemplateWriters(TemplateVisitor templateVisitor, TypeElement element, Writer writer) throws IOException {
        List<MetannotTemplate> templates = templateVisitor.getTemplates();
        Map<String, JCMethodDecl> abstractMethods = templateVisitor.getAbstractMethods();
        for (MetannotTemplate template : templates) {
            if (!abstractMethods.containsKey(template.writer)) {
                processingEnv.getMessager()
                    .printMessage(Kind.ERROR, "you should declare abstract method abstract void " + template.writer + "(Writer writer, Messager messager, Map<String, String> params) throws IOException; in " + element);
                return;
            }
            
            writer
                .append("protected void ")
                .append(template.writer)
                .append("(Writer writer, Messager messager, Map<String, String> params, String typename) throws IOException ");
            
            Map<String, String> params = new HashMap<String, String>();
            
            params.put("template", "\"" + template.template.replace("\n", "\\n") + "\"");
            
            StringBuilder parametersBuilder = new StringBuilder();
            parametersBuilder.append("new String[]{");
            
            StringBuilder indecesBuilder = new StringBuilder();
            indecesBuilder.append("new int[]{");
            
            if (template.parameters.size() > 0) {
                for (Iterator<MetannotTemplateParameter> iter = template.parameters.iterator();;){
                    MetannotTemplateParameter parameter = iter.next();
                    indecesBuilder.append(parameter.start + ", " + parameter.end);
                    parametersBuilder
                        .append("\"")
                        .append(parameter.key)
                        .append("\"");
                    if (iter.hasNext()) {
                        indecesBuilder.append(", ");
                        parametersBuilder.append(", ");
                    } else {
                        break;
                    }
                }
            }
            
            parametersBuilder.append("}");
            indecesBuilder.append("}");
            
            params.put("parameters", parametersBuilder.toString());
            params.put("indeces", indecesBuilder.toString());
            params.put("writerName", "\"" + template.writer + "\"");
            params.put("oldToken", "\"" + template.templateName + "\"");
            params.put(
                "call$writeTemplateParameters$",
                "$writeTemplateParameters$(template, parameters, params, messager, writerName, builder);");
            
            writeTemplateWriter(writer, processingEnv.getMessager(), params, null);
            writer.append("\n");
        }
    }
    
    protected abstract void writeImports(Writer writer) throws IOException;
    protected abstract void writeClassImpl(Writer writer, Messager messager, Map<String, String> params, String typename) throws IOException;
    protected abstract void writeTemplateWriter(Writer writer, Messager messager, Map<String, String> params, String typename) throws IOException;
    
    @Template(writer = "writeTemplateWriter")
    protected void templateWriterTemplate(Writer writer, Messager messager, Map<String, String> params, String typename) throws IOException {
        @TemplateParameter
        String template = null;
        @TemplateParameter
        String[] parameters = null;
        @TemplateParameter
        String writerName = null;
        @TemplateParameter
        String oldToken = null;
        
        StringBuilder builder = new StringBuilder();
        
        @TemplateParameter(keepLhs = false)
        String call$writeTemplateParameters$;
        
        if (typename != null) {
            writer.append(
                MetannotUtil.replaceTokenInString(
                    builder.toString(), oldToken, typename));
        } else {
            writer.append(builder);
        }
    }
    
    @Template(writer = "writeClassImpl")
    protected abstract static class MetannotProcessorTemplate extends AbstractProcessor {
        @TemplateParameter(keepLhs = false)
        String templateWriters;
        
        public MetannotProcessorTemplate() {
            
        }
        
        private void $writeTemplateParameters$(String template, String[] parameters, Map<String, String> params, Messager messager, String writerName, StringBuilder builder) {
            int pos = 0;
            
            Pattern templatePattern = Pattern.compile("@" + TemplateParameter.class.getSimpleName() + "\\(");
            Matcher matcher = templatePattern.matcher(template);
            int keyIndex = 0;
            while (true) {
                if (matcher.find(pos)) {
                    String strKeepLhs = template.substring(
                            matcher.end(), template.indexOf(')', matcher.end()));
                    boolean keepLhs = true;
                    if (strKeepLhs.length() > 0) {
                        keepLhs = strKeepLhs.indexOf("true") > -1;
                    }
                    
                    int start, end;
                    int nextLf = template.indexOf('\n', matcher.end());
                    String key = parameters[keyIndex ++];
                    
                    if (keepLhs) {
                        start = template.indexOf('=', nextLf + 1) + 2;
                        end = template.indexOf(';', nextLf + 1);
                    } else {
                        start = matcher.start();
                        end = template.indexOf(';', nextLf + 1);
                    }
                    
                    builder.append(
                            template.subSequence(
                                    pos, start));
                    if (!params.containsKey(key)) {
                        messager.printMessage(Kind.ERROR,
                                "Template parameter " + key + " was given, but there are no such key in the params passed to injectionClassWriter!");
                        return;
                    }
                    builder.append(params.get(key));
                    pos = end;
                } else {
                    break;
                }
            }
            
            builder.append(template.substring(pos));
        }
        
        protected void writeImports(java.io.Writer writer) throws IOException {
            @TemplateParameter
            String[] strImports = null;
            
            for (String strImport : strImports) {
                writer
                    .append("import ")
                    .append(strImport)
                    .append(";\n");
            }
            
            writer.append("\n");
        }
    }
}
