import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * This class represents a refactoring in which checks on variables that imply
 * non-nullness of a seperate varaible are replaced with explicit checks of the
 * latter
 */
public class SeperateVariableRefactoring extends Refactoring {
	public boolean isApplicable(ASTNode node) {
		return false;
	}

	public void apply(ASTNode node, ASTRewrite rewriter) {
		return;
	}
}
