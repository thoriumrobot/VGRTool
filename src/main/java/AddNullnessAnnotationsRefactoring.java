import java.util.List;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

class AddNullnessAnnotationsRefactoring extends Refactoring {

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration varDecl = (SingleVariableDeclaration) node;
            // Check if variable is missing nullness annotations
            if (!hasNullnessAnnotation(varDecl.modifiers())) {
                return true;
            }
        }
        if (node instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment varFrag = (VariableDeclarationFragment) node;
            ASTNode parent = varFrag.getParent();
            if (parent instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement varStmt = (VariableDeclarationStatement) parent;
                if (!hasNullnessAnnotation(varStmt.modifiers())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        AST ast = node.getAST();

        if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration varDecl = (SingleVariableDeclaration) node;
            addNullableAnnotation(varDecl.modifiers(), rewriter, ast);
        }
        if (node instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment varFrag = (VariableDeclarationFragment) node;
            VariableDeclarationStatement varStmt = (VariableDeclarationStatement) varFrag.getParent();
            addNullableAnnotation(varStmt.modifiers(), rewriter, ast);
        }
    }

    private void addNullableAnnotation(List<?> modifiers, ASTRewrite rewriter, AST ast) {
        // Add @Nullable annotation
        MarkerAnnotation nullableAnnotation = ast.newMarkerAnnotation();
        nullableAnnotation.setTypeName(ast.newSimpleName("Nullable"));

        // Insert the annotation at the beginning of the modifiers
        ListRewrite listRewrite = rewriter.getListRewrite((ASTNode) modifiers.get(0).getParent(), VariableDeclarationStatement.MODIFIERS2_PROPERTY);
        listRewrite.insertFirst(nullableAnnotation, null);
    }

    private boolean hasNullnessAnnotation(List<?> modifiers) {
        for (Object modifier : modifiers) {
            if (modifier instanceof Annotation) {
                Annotation annotation = (Annotation) modifier;
                String name = annotation.getTypeName().getFullyQualifiedName();
                if (name.equals("Nullable") || name.equals("NonNull")) {
                    return true;
                }
            }
        }
        return false;
    }
}
