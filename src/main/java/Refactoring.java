import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * Abstract class representing a refactoring that can be applied to an AST
 */
public abstract class Refactoring {
	/**
	 * Identifies if an AST node could potentially be refactored
	 * 
	 * @param node
	 *            The ASTNode under analysis
	 * @return A boolean representing whether node can be refactored
	 */
	public abstract boolean isApplicable(ASTNode node);

	/**
	 * Applies refactoring to an ASTNode
	 * 
	 * @param node
	 *            The ASTNode to refactor
	 * @param rewriter
	 *            The ASTRewriter to use for rewriting
	 */
	public abstract void apply(ASTNode node, ASTRewrite rewriter);
}
