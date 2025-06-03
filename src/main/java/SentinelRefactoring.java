import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class SentinelRefactoring extends Refactoring {
	@Override
	public boolean isApplicable(ASTNode node) {
		if (node instanceof IfStatement ifStmt) {
			System.out.println("7");
			Expression condition = ifStmt.getExpression();
			if (condition instanceof InfixExpression infix
					&& (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
							|| infix.getOperator() == InfixExpression.Operator.EQUALS)) {
				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();
				if ((leftOperand instanceof SimpleName && rightOperand instanceof NullLiteral)
						|| (rightOperand instanceof SimpleName && leftOperand instanceof NullLiteral)) {
					System.out.println("007");
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		return;
	}
}
