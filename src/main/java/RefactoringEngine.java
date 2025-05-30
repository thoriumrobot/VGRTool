import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class RefactoringEngine {
    private static final Logger logger = Logger.getLogger(VGRTool.class.getName());

    private final List<Refactoring> refactorings;
    @SuppressWarnings("unused")
    private final Set<Expression> expressionsPossiblyNull;

    public RefactoringEngine(List<String> refactoringNames, Set<Expression> expressionsPossiblyNull) {
        this.expressionsPossiblyNull = expressionsPossiblyNull;
        refactorings = new ArrayList<>();

        for (String name : refactoringNames) {
            Refactoring refactoring = null;
            /*
             * if (name.equals("WrapWithCheckNotNullRefactoring")) { refactoring = new
             * WrapWithCheckNotNullRefactoring(expressionsPossiblyNull); } else if
             * (name.equals("AddNullChecksForNullableReferences")) { refactoring = new
             * AddNullChecksForNullableReferencesRefactoring(expressionsPossiblyNull); }
             * else
             */ if (name.equals("AddNullCheckBeforeDereferenceRefactoring")) {
                refactoring = new AddNullCheckBeforeDereferenceRefactoring();
                /*
                 * } else if (name.equals("GeneralizedNullCheck")) { refactoring = new
                 * GeneralizedNullCheck(); } else if
                 * (name.equals("AddNullCheckBeforeMethodCallRefactoring")) { refactoring = new
                 * AddNullCheckBeforeMethodCallRefactoring(variablesPossiblyNull,
                 * expressionsPossiblyNull); } else if
                 * (name.equals("AddNullnessAnnotationsRefactoring")) { refactoring = new
                 * AddNullnessAnnotationsRefactoring(); } else if
                 * (name.equals("IntroduceLocalVariableAndNullCheckRefactoring")) { refactoring
                 * = new IntroduceLocalVariableAndNullCheckRefactoring(expressionsPossiblyNull);
                 * } else if (name.equals("IntroduceLocalVariableWithNullCheckRefactoring")) {
                 * refactoring = new
                 * IntroduceLocalVariableWithNullCheckRefactoring(expressionsPossiblyNull); }
                 * else if (name.equals("NullabilityRefactoring")) { refactoring = new
                 * NullabilityRefactoring(); } else if
                 * (name.equals("SimplifyNullCheckRefactoring")) { refactoring = new
                 * SimplifyNullCheckRefactoring();
                 */
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
            /*
             * if (refactoring instanceof GeneralizedNullCheck) { ((GeneralizedNullCheck)
             * refactoring).traverseAST(cu); }
             */
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
        } catch (MalformedTreeException | org.eclipse.jface.text.BadLocationException e) {
            logger.log(Level.WARNING, e.toString());
        }

        return document.get();
    }

}
