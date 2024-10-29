import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.core.runtime.CoreException; // Import CoreException
import org.eclipse.jdt.core.JavaModelException; // Import JavaModelException
import org.example.utils.RefactoringUtils;

/**
 * Engine to apply various refactorings to CompilationUnits.
 */
public class RefactoringEngine {

    private List<Refactoring> refactorings;

    // Map of available refactorings
    private static final Map<String, Refactoring> AVAILABLE_REFACTORINGS = new HashMap<>();

    static {
        AVAILABLE_REFACTORINGS.put("SimplifyNullCheck", new SimplifyNullCheckRefactoring());
        AVAILABLE_REFACTORINGS.put("AddNullnessAnnotations", new AddNullnessAnnotationsRefactoring());
        AVAILABLE_REFACTORINGS.put("NullabilityRefactoring", new NullabilityRefactoring());
        // Add more refactorings as needed
    }

    public RefactoringEngine(List<String> refactoringNames) {
        refactorings = new ArrayList<>();

        for (String name : refactoringNames) {
            Refactoring refactoring = AVAILABLE_REFACTORINGS.get(name);
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

    public CompilationUnit refactor(CompilationUnit cu, List<String> warnings) throws JavaModelException {
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
        Document document = new Document(cu.toString());
        TextEdit edits = rewriter.rewriteAST(document, null);
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
        int lineNumber = extractLineNumber(warning);
        if (lineNumber == -1) {
            continue;
        }

        // Use the CompilationUnit's line number mapping
        int position = cu.getPosition(lineNumber, 0);
        ASTNode node = NodeFinder.perform(cu, position, 0);
        if (node != null) {
            warningNodes.put(node, warning);
        }
    }

    return warningNodes;
}

    // Utility method to extract line number from warning string
    private int extractLineNumber(String warning) {
        try {
            String[] parts = warning.split(":");
            return Integer.parseInt(parts[0].replace("Line ", "").trim());
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
