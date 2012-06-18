package mn.uwvm.metannot;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import mn.uwvm.metannot.annotation.Template;
import mn.uwvm.metannot.model.MetannotTemplate;
import mn.uwvm.metannot.model.MetannotTemplateParameter;
import mn.uwvm.metannot.util.Logger;
import mn.uwvm.metannot.visitor.TemplateVisitor;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;

@SupportedAnnotationTypes("mn.uwvm.metannot.annotation.Template")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class MetannotProcessor extends AbstractProcessor {
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
            
            writeImports(templateVisitor, writer);
            
            
            writer
                .append("@javax.annotation.Generated(value = \"" + this.getClass().getName() + "\", date = \"" + generationTime + "\")\n");
            
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                writer.append(mirror.toString()).append('\n');
            }
            
            writer
                .append("public class ").append(simpleName)
                .append(" extends ").append(originalName).append(" {\n");
            
            writeTemplateWriters(templateVisitor, element, writer);
            writeImportWriter(templateVisitor, element, writer);
            
            writer.append("}");
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
    
    private void writeImports(TemplateVisitor templateVisitor, Writer writer) throws IOException {
        List<String> imports = templateVisitor.getImports();
        for (String strImport : imports) {
            writer
                .append("import ")
                .append(strImport)
                .append(";\n");
        }
        
        writer.append('\n');
    }
    
    private void writeImportWriter(TemplateVisitor templateVisitor, TypeElement typeElement, Writer writer) throws IOException {
        { // 
            Map<String, JCMethodDecl> absMethods = templateVisitor.getAbstractMethods();
            if (!absMethods.containsKey(IMPORT_WRITER)) {
                processingEnv.getMessager()
                    .printMessage(Kind.ERROR, "Declare abstract method abstract void writeImports(java.io.Writer writer) throws IOException; in " + typeElement.getQualifiedName() + "!");
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
            writeModifier(writerDecl.mods.getFlags(), writer);
        }
        
        writer
            .append("void ")
            .append(IMPORT_WRITER)
            .append("(Writer writer) throws IOException {\n")
            .append("    for (String strImport :\n")
            .append("         new String[] {");
        for (Iterator<String> iter = templateVisitor.getImports().iterator();;) {
            writer
                .append('"')
                .append(iter.next())
                .append('"');
            
            if (iter.hasNext()) {
                writer.append(", ");
            } else {
                break;
            }
        }
        
        writer
            .append("}) {\n")
            .append("        writer\n")
            .append("            .append(\"import \")\n")
            .append("            .append(strImport)\n")
            .append("            .append(\";\\n\");\n")
            .append("    }\n")
            .append("    writer.append('\\n');\n")
            .append("}\n");
    }
    
    private void writeTemplateWriters(TemplateVisitor templateVisitor, TypeElement element, Writer writer) throws IOException {
        List<MetannotTemplate> templates = templateVisitor.getTemplates();
        Map<String, JCMethodDecl> abstractMethods = templateVisitor.getAbstractMethods();
        for (MetannotTemplate template : templates) {
            if (!abstractMethods.containsKey(template.writer)) {
                processingEnv.getMessager()
                    .printMessage(Kind.ERROR, "you should declare abstract method abstract void " + template.writer + "(java.io.Writer writer, javax.annotation.processing.Messager messager, java.util.Map<String, String> params, String typename) throws java.io.IOException; in " + element);
                return;
            }
            
            JCMethodDecl abstractMethod = abstractMethods.get(template.writer);
            writeModifier(abstractMethod.mods.getFlags(), writer);
            
            writer
                .append("void ")
                .append(template.writer)
                .append("(Writer writer, Messager messager, Map<String, String> params, String typename) throws IOException {\n")
                .append("    String template = \"")
                .append(template.template.replace("\n", "\\n"))
                .append("\";\n");
                    
            StringBuilder builder = new StringBuilder();
            builder.append("    String[] parameters = new String[] {");
            writer
                .append("    int pos = 0;\n")
                .append("    String writerName = \"")
                .append(template.writer)
                .append("\";\n")
                .append("    StringBuilder builder = new StringBuilder();\n");
            
            if (template.parameters.size() > 0) {
                for (Iterator<MetannotTemplateParameter> iter = template.parameters.iterator();;){
                    MetannotTemplateParameter parameter = iter.next();
                    builder
                    .append("\"")
                    .append(parameter.key)
                    .append("\"");
                    if (iter.hasNext()) {
                        builder.append(", ");
                    } else {
                        break;
                    }
                    //pos = parameter.end;
                }
            }
            builder.append("};\n");
            
            writer
                .append(builder)
                .append("    mn.uwvm.metannot.util.MetannotUtil.writeTemplateParameters(template, parameters, params, messager, writerName, builder);\n")
                .append("    String output = builder.toString();\n")
                .append("    if (typename != null) {\n")
                .append("        output = mn.uwvm.metannot.util.MetannotUtil.replaceTokenInString(\n")
                .append("            output, \"")
                .append(template.templateName)
                .append("\", typename);\n")
                .append("    }\n")
                .append("    writer.append(output);\n");
            
            writer
                .append("}\n");
            
        }
    }
    
    private void writeModifier(Set<Modifier> modifiers, Writer writer) throws IOException {
        for (Modifier modifier : modifiers) {
            switch (modifier) {
            case PROTECTED:
                writer.append("protected ");
                break;
            case PUBLIC:
                writer.append("public ");
                break;
            case PRIVATE:
                writer.append("private ");
                break;
            default:
                break;
            }
        }
    }

}
