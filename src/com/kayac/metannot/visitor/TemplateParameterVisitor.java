package com.kayac.metannot.visitor;

import java.util.ArrayList;
import java.util.List;

import com.kayac.metannot.annotation.TemplateParameter;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeTranslator;


public class TemplateParameterVisitor extends TreeTranslator {
    private final List<JCVariableDecl> mVarDecs = new ArrayList<JCVariableDecl>();
    @Override
    public void visitVarDef(JCVariableDecl tree) {
        super.visitVarDef(tree);
        
        for (JCAnnotation annotation : tree.mods.annotations) {
            if (isTemplateParameter(annotation)) {
                mVarDecs.add(tree);
            }
        }
    }
    
    public final List<JCVariableDecl> getVarDecs() {
        return new ArrayList<JCVariableDecl>(mVarDecs);
    }
    
    public static final boolean isTemplateParameter(JCAnnotation annotation) {
        return annotation.type != null ?
            TemplateParameter.class.getCanonicalName().equals(annotation.type.toString()) :
            TemplateParameter.class.getSimpleName().equals(annotation.annotationType.toString());
    }
}
