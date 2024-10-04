import java.util.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.*;

class RefactoringEngine {

    private List<Refactoring> refactorings;

    public RefactoringEngine() {
        // Initialize the list of semantics-preserving refactorings
        refactorings = new ArrayList<>();
        refactorings.add(new SimplifyNullCheckRefactoring());
        refactorings.add(new AddNullnessAnnotationsRefactoring());
    }

    public CompilationUnit refactor(CompilationUnit cu, List<String> warnings) {
        AST ast = cu.getAST();

        // Copy the original AST to avoid modifying it directly
        ASTRewrite rewriter = ASTRewrite.create(ast);

        // Map warnings to AST nodes
        Map<ASTNode, String> warningNodes = mapWarningsToNodes(cu, warnings);

        // Apply refactorings near warnings
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

        // Apply the changes and return the modified CompilationUnit
        TextEdit edits = rewriter.rewriteAST();
        Document document = new Document(cu.toString());
        try {
            edits.apply(document);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Parse the modified source code into a new CompilationUnit
        CompilationUnit refactoredCU = VGRTool.parse(document.get());
        return refactoredCU;
    }

    // Method to map warnings to AST nodes
    private Map<ASTNode, String> mapWarningsToNodes(CompilationUnit cu, List<String> warnings) {
        Map<ASTNode, String> warningNodes = new HashMap<>();

        for (String warning : warnings) {
            long lineNumber = extractLineNumber(warning);

            // Find the node at the line number
            ASTNode node = getNodeAtLine(cu, (int) lineNumber);
            if (node != null) {
                warningNodes.put(node, warning);
            }
        }

        return warningNodes;
    }

    // Utility method to extract line number from warning string
    private long extractLineNumber(String warning) {
        try {
            String[] parts = warning.split(":");
            return Long.parseLong(parts[0].replace("Line ", "").trim());
        } catch (Exception e) {
            return -1;
        }
    }

    // Utility method to get AST node at a specific line number
    private ASTNode getNodeAtLine(CompilationUnit cu, int lineNumber) {
        try {
            int position = cu.getPosition(lineNumber, 0);
            return NodeFinder.perform(cu, position, 0);
        } catch (Exception e) {
            return null;
        }
    }
}
