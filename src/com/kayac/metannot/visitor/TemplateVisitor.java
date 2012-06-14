package com.kayac.metannot.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic.Kind;

import com.kayac.metannot.annotation.Template;
import com.kayac.metannot.model.MetannotTemplate;
import com.kayac.metannot.model.MetannotTemplateParameter;
import com.kayac.metannot.util.Logger;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeTranslator;

public class TemplateVisitor extends TreeTranslator {
    private final ProcessingEnvironment mProcessingEnvironment;
    private final List<MetannotTemplate> mTemplates = new ArrayList<MetannotTemplate>();
    private final Map<String, JCMethodDecl> mAbstractMethods = new HashMap<String, JCMethodDecl>();
    private final List<String> mImports = new ArrayList<String>();
    
    public TemplateVisitor(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
    }
    
    @Override
    public void visitClassDef(JCClassDecl tree) {
        super.visitClassDef(tree);
        
        for (JCAnnotation annotation : tree.mods.annotations) {
            if (isTemplate(annotation)) {
                TemplateParameterVisitor parameterVisitor = new TemplateParameterVisitor(mProcessingEnvironment);
                tree.accept(parameterVisitor);
                
                String source = tree.toString();
                int start = source.indexOf('{');
                int end = source.lastIndexOf('}') + 1;
                source = source.substring(start, end);
                
                String writer = null;
                String className = null;
                for (JCExpression expression : annotation.getArguments()) {
                    JCAssign assign = (JCAssign) expression;
                    String lhs = assign.lhs.toString();
                    String rhs = ((JCLiteral)assign.rhs).getValue().toString();
                    
                    if ("writer".equals(lhs)) {
                        writer = rhs;
                    } else if ("className".equals(lhs)) {
                        className = rhs;
                    }
                }
                
                if (className != null) {
                    Pattern pattern = Pattern.compile("(\\W)" + tree.getSimpleName() + "(\\W)");
                    Matcher matcher = pattern.matcher(source);
                    
                    
                    int pos = 0;
                    StringBuilder builder = new StringBuilder();
                    while (matcher.find(pos)) {
                        builder.append(source.subSequence(pos, matcher.start()));
                        builder.append(matcher.group(1));
                        builder.append(className);
                        builder.append(matcher.group(2));
                        pos = matcher.end();
                    }
                    builder.append(source.substring(pos));
                    source = builder.toString();
                }
                
                registerTemplate(
                    source, writer, annotation, parameterVisitor);
            }
        }
    }
    
    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        super.visitMethodDef(tree);
        
        for (JCAnnotation annotation : tree.mods.annotations) {
            if (isTemplate(annotation)) {
                TemplateParameterVisitor parameterVisitor = new TemplateParameterVisitor(mProcessingEnvironment);
                tree.accept(parameterVisitor);
                
                String writer = null;
                for (JCExpression expression : annotation.getArguments()) {
                    JCAssign assign = (JCAssign) expression;
                    String lhs = assign.lhs.toString();
                    String rhs = ((JCLiteral)assign.rhs).getValue().toString();
                    
                    if ("writer".equals(lhs)) {
                        writer = rhs;
                    } else if ("className".equals(lhs)) {
                        mProcessingEnvironment.getMessager()
                            .printMessage(Kind.ERROR, "You should not specify className for method templates.");
                        return;
                    }
                }
                
                registerTemplate(
                    tree.toString(), writer, annotation, parameterVisitor);
            }
        }
        
        Set<Modifier> modifers = tree.mods.getFlags();
        for (Modifier modifier : modifers) {
            if (modifier == Modifier.ABSTRACT) {
                mAbstractMethods.put(tree.name.toString(), tree);
                break;
            }
        }
    }
    
    private void registerTemplate(String source, String writer, JCAnnotation annotation, TemplateParameterVisitor parameterVisitor) {
        List<MetannotTemplateParameter> parameters = new ArrayList<MetannotTemplateParameter>();
        int matchPos = 0;
        for (JCVariableDecl variableDecl : parameterVisitor.getVarDecs()) {
            String varDecSource = variableDecl.toString().replace("\n", "\\s+").replace("()", "(\\(\\))?") + ";";
            Pattern pattern = Pattern.compile(varDecSource, Pattern.MULTILINE);
            
            Matcher matcher = pattern.matcher(source);
            if (matcher.find(matchPos)) {
                
                parameters.add(
                    new MetannotTemplateParameter(
                        matcher.start(), matcher.end(), variableDecl.name.toString()));
                matchPos = matcher.end();
            }
        }
        
        mTemplates.add(
            new MetannotTemplate(source.replace("\"", "\\\""), parameters, writer));
    }
    
    @Override
    public void visitImport(JCImport tree) {
        super.visitImport(tree);
        
        String fqn = tree.qualid.toString();
        mImports.add(fqn);
    }
    
    private static final boolean isTemplate(JCAnnotation annotation) {
        return annotation.type != null ?
            Template.class.getCanonicalName().equals(annotation.type.toString()) : false;
    }
    
    public List<MetannotTemplate> getTemplates() {
        return new ArrayList<MetannotTemplate>(mTemplates);
    }

    public Map<String, JCMethodDecl> getAbstractMethods() {
        return new HashMap<String, JCMethodDecl>(mAbstractMethods);
    }

    public List<String> getImports() {
        return new ArrayList<String>(mImports);
    }
}
