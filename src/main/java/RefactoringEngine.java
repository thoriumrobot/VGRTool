import java.util.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

public class RefactoringEngine {

    private List<Refactoring> refactorings;
    private Set<Expression> expressionsPossiblyNull;

    public RefactoringEngine(List<String> refactoringNames, Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
        refactorings = new ArrayList<>();

        for (String name : refactoringNames) {
            Refactoring refactoring = null;
            if (name.equals("IntroduceLocalVariableWithNullCheck")) {
                refactoring = new IntroduceLocalVariableWithNullCheckRefactoring(expressionsPossiblyNull);
            } else {
                // Handle other refactorings if necessary
            }

            if (refactoring != null) {
                refactorings.add(refactoring);
            } else {
                System.err.println("Unknown refactoring: " + name);
            }
        }

        if (refactorings.isEmpty()) {
            System.err.println("No valid refactorings specified. Exiting.");
            System.exit(1);
        }
    }

    public String applyRefactorings(CompilationUnit cu, String sourceCode) {
        AST ast = cu.getAST();
        ASTRewrite rewriter = ASTRewrite.create(ast);

        // Apply each refactoring to the AST
        for (Refactoring refactoring : refactorings) {
            cu.accept(new ASTVisitor() {
                @Override
                public void preVisit(ASTNode node) {
                    if (refactoring.isApplicable(node)) {
                        refactoring.apply(node, rewriter);
                    }
                }
            });
        }

        // Apply the rewrite changes to the source code
        Document document = new Document(sourceCode);
        TextEdit edits = rewriter.rewriteAST(document, null);
        try {
            edits.apply(document);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return the refactored source code
        return document.get();
    }
}

