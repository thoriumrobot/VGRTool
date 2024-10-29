import java.util.List;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.example.utils.RefactoringUtils;
import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Adds @Nullable annotations to variables missing nullness annotations.
 */
public class AddNullnessAnnotationsRefactoring extends Refactoring {

    @Override
    public boolean isApplicable(ASTNode node) {
        if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration varDecl = (SingleVariableDeclaration) node;
            // Check if variable is missing nullness annotations
            if (!hasNullnessAnnotation(varDecl.modifiers())) {
                return true;
            }
        }
        if (node instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt = (VariableDeclarationStatement) node;
            if (!hasNullnessAnnotation(varStmt.modifiers())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        AST ast = node.getAST();
        CompilationUnit cu = (CompilationUnit) node.getRoot();

        if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration varDecl = (SingleVariableDeclaration) node;
            addNullableAnnotation(varDecl, rewriter, ast, SingleVariableDeclaration.MODIFIERS2_PROPERTY);
        }
        if (node instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt = (VariableDeclarationStatement) node;
            addNullableAnnotation(varStmt, rewriter, ast, VariableDeclarationStatement.MODIFIERS2_PROPERTY);
        }

        // Ensure the import statement for @Nullable is present
        ensureNullableImport(rewriter, cu, ast);
    }

    private void addNullableAnnotation(ASTNode node, ASTRewrite rewriter, AST ast, ChildListPropertyDescriptor modifiersProperty) {
        // Create the @Nullable annotation
        MarkerAnnotation nullableAnnotation = ast.newMarkerAnnotation();
        nullableAnnotation.setTypeName(ast.newSimpleName("Nullable"));

        // Get the ListRewrite for the modifiers
        ListRewrite listRewrite = rewriter.getListRewrite(node, modifiersProperty);
        listRewrite.insertFirst(nullableAnnotation, null);
    }

    private void ensureNullableImport(ASTRewrite rewriter, CompilationUnit cu, AST ast) {
        List<?> imports = cu.imports();
        boolean hasImport = false;
        for (Object impObj : imports) {
            ImportDeclaration imp = (ImportDeclaration) impObj;
            if (imp.getName().getFullyQualifiedName().equals("javax.annotation.Nullable")) {
                hasImport = true;
                break;
            }
        }
        if (!hasImport) {
            ImportDeclaration importDeclaration = ast.newImportDeclaration();
            importDeclaration.setName(ast.newName("javax.annotation.Nullable"));
            ListRewrite importRewrite = rewriter.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);
            importRewrite.insertLast(importDeclaration, null);
        }
    }
}

