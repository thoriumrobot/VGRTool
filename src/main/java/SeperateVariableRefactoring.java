import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class SeperateVariableRefactoring extends Refactoring {
	public boolean isApplicable(ASTNode node) {
		return false;
	}

	public void apply(ASTNode node, ASTRewrite rewriter) {
		return;
	}
}
