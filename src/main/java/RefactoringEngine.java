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
            if (name.equals("WrapWithCheckNotNullRefactoring")) {
                refactoring = new WrapWithCheckNotNullRefactoring(expressionsPossiblyNull);
            } else if (name.equals("AddNullChecksForNullableReferences")) {
                refactoring = new AddNullChecksForNullableReferencesRefactoring(expressionsPossiblyNull);
            } else if (name.equals("AddNullCheckBeforeDereferenceRefactoring")) {
                refactoring = new AddNullCheckBeforeDereferenceRefactoring();
            } else if (name.equals("GeneralizedNullCheck")) {
                refactoring = new GeneralizedNullCheck();
            /*} else if (name.equals("AddNullCheckBeforeMethodCallRefactoring")) {
                refactoring = new AddNullCheckBeforeMethodCallRefactoring(variablesPossiblyNull, expressionsPossiblyNull);*/
            } else if (name.equals("AddNullnessAnnotationsRefactoring")) {
                refactoring = new AddNullnessAnnotationsRefactoring();
            } else if (name.equals("IntroduceLocalVariableAndNullCheckRefactoring")) {
                refactoring = new IntroduceLocalVariableAndNullCheckRefactoring(expressionsPossiblyNull);
            } else if (name.equals("IntroduceLocalVariableWithNullCheckRefactoring")) {
                refactoring = new IntroduceLocalVariableWithNullCheckRefactoring(expressionsPossiblyNull);
            } else if (name.equals("NullabilityRefactoring")) {
                refactoring = new NullabilityRefactoring();
            } else if (name.equals("SimplifyNullCheckRefactoring")) {
                refactoring = new SimplifyNullCheckRefactoring();
            } else {
                System.err.println("Unknown refactoring: " + name);
            }

            if (refactoring != null) {
                refactorings.add(refactoring);
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

    for (Refactoring refactoring : refactorings) {
    if (refactoring instanceof GeneralizedNullCheck) {
        ((GeneralizedNullCheck) refactoring).traverseAST(cu);
    }
        cu.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                System.out.println("[DEBUG] Visiting AST Node: " + node.getClass().getSimpleName());

                if (refactoring.isApplicable(node)) {
                    System.out.println("[DEBUG] Applying refactoring to: " + node);
                    refactoring.apply(node, rewriter);
                }
            }
        });
    }

    Document document = new Document(sourceCode);
    TextEdit edits = rewriter.rewriteAST(document, null);
    try {
        edits.apply(document);
    } catch (Exception e) {
        e.printStackTrace();
    }

    return document.get();
}


}

