package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class EmptyTryBlockInspection extends StatementInspection {

    public String getDisplayName() {
        return "Empty 'try' block";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "Empty #ref block #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyTryBlockVisitor();
    }

    private static class EmptyTryBlockVisitor extends StatementInspectionVisitor {

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);

            if(statement.getContainingFile() instanceof JspFile){
                return;
            }
            final PsiCodeBlock finallyBlock = statement.getTryBlock();
            if (finallyBlock == null) {
                return;
            }
            if (finallyBlock.getStatements().length != 0) {
                return;
            }
            registerStatementError(statement);
        }
    }

}
