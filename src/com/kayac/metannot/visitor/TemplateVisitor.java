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
import com.kayac.metannot.annotation.TemplateParameter;
import com.kayac.metannot.model.MetannotTemplate;
import com.kayac.metannot.model.MetannotTemplateParameter;
import com.kayac.metannot.util.MetannotUtil;
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
                TemplateParameterVisitor parameterVisitor = new TemplateParameterVisitor();
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
                
                /*
                if (className != null) {
                    source = MetannotUtil.replaceTokenInString(
                        source,
                        tree.getSimpleName().toString(),
                        className);
                }*/
                
                registerTemplate(
                    source, tree.getSimpleName().toString(), writer, parameterVisitor);
            }
        }
    }
    
    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        super.visitMethodDef(tree);
        
        for (JCAnnotation annotation : tree.mods.annotations) {
            if (isTemplate(annotation)) {
                TemplateParameterVisitor parameterVisitor = new TemplateParameterVisitor();
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
                    tree.body.toString(), tree.name.toString(), writer, parameterVisitor);
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
    
    private void registerTemplate(String source, String templateName, String writer, TemplateParameterVisitor parameterVisitor) {
        List<MetannotTemplateParameter> parameters = new ArrayList<MetannotTemplateParameter>();
        int matchPos = 0;
        for (JCVariableDecl variableDecl : parameterVisitor.getVarDecs()) {
            boolean shouldKeepLhs = shouldKeepLhs(variableDecl);
            
            int annotationPos = source.indexOf("@" + TemplateParameter.class.getSimpleName() + "(", matchPos);
            
            if (annotationPos > -1) {
                String key = variableDecl.name.toString();
                if (shouldKeepLhs) {
                    int varTypePos =  source.indexOf(variableDecl.vartype.toString(), annotationPos);
                    /*n
                    source = source.substring(0, annotationPos) +
                             source.substring(varTypePos);*/
                    
                    int rhsPos = source.indexOf(
                        variableDecl.init.toString(), varTypePos);
                    int end = source.indexOf(';', rhsPos);
                    
                    parameters.add(
                        new MetannotTemplateParameter(
                            rhsPos, end, key));
                } else {
                    int end = source.indexOf(';', annotationPos) + 1;
                    parameters.add(
                        new MetannotTemplateParameter(
                            annotationPos, end, key));
                    matchPos = end;
                }
            }
        }
        
        mTemplates.add(
            new MetannotTemplate(
                source.replace("\\", "\\\\").replace("\"", "\\\""),
                parameters,
                writer,
                templateName));
    }
    
    private static boolean shouldKeepLhs(JCVariableDecl variableDecl) {
        for (JCAnnotation annotation : variableDecl.mods.annotations) {
            if (TemplateParameterVisitor.isTemplateParameter(annotation)) {
                for (JCExpression expression : annotation.args) {
                    JCAssign assign = (JCAssign) expression;
                    if ("keepLhs".equals(assign.lhs.toString())) {
                        return Boolean.parseBoolean(assign.rhs.toString());
                    }
                }
            }
        }
        return true;
    }
    
    @Override
    public void visitImport(JCImport tree) {
        super.visitImport(tree);
        
        String fqn = tree.qualid.toString();
        mImports.add(fqn);
    }
    
    private static final boolean isTemplate(JCAnnotation annotation) {
        return annotation.type != null ?
            Template.class.getCanonicalName().equals(
                annotation.type.toString()) : false;
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
