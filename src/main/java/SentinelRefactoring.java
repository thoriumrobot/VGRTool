import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.core.dom.Statement;

public class SentinelRefactoring extends Refactoring {
	private final Dictionary<String, Sentinel> sentinels;

	protected class Sentinel {
		private SimpleName varName;
		private String nullValue;
		private String notNullValue;
		private Assignment.Operator operator;

		public Sentinel() {
		}

		public String toString() {
			return "Sentinel: \n\tnullValue: " + nullValue + "\n\tnotNullValue: " + notNullValue;
		}

		public SimpleName getVarName() {
			return this.varName;
		}

		public String getNullValue() {
			return this.nullValue;
		}

		public String getNotNullValue() {
			return this.notNullValue;
		}

		public Assignment.Operator getOperator() {
			return this.operator;
		}

		public SimpleName setVarName(SimpleName varName) {
			return this.varName = varName;
		}

		public void setNullValue(String val) {
			this.nullValue = val;
		}

		public void setNotNullValue(String val) {
			this.notNullValue = val;
		}

		public void setOperator(Assignment.Operator operator) {
			this.operator = operator;
		}
	}

	public SentinelRefactoring() {
		super();
		this.sentinels = new Hashtable<>();
	}

	public void addSentinel() {

	}

	@Override
	public boolean isApplicable(ASTNode node) {
		if (node instanceof IfStatement ifStmt) {
			System.out.println("[DEBUG] If-statement");
			Expression condition = ifStmt.getExpression();
			if (condition instanceof InfixExpression infix
					&& (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
							|| infix.getOperator() == InfixExpression.Operator.EQUALS)) {
				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();

				if ((leftOperand instanceof SimpleName lhs && sentinels.get(lhs.toString()) != null)
						|| (rightOperand instanceof SimpleName rhs
								&& sentinels.get(rhs.toString()) != null)) {
					System.out.println("[DEBUG] Found sentinel in if-statement: " + condition);
					return true;
				}
			}
		}

		if (node instanceof IfStatement ifStmt) {
			System.out.println("7");
			Expression condition = ifStmt.getExpression();
			if (condition instanceof InfixExpression infix) {
				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();
				Operator operator = infix.getOperator();
				if ((leftOperand instanceof SimpleName ogVarName
						&& rightOperand instanceof NullLiteral)) {
					if (ifStmt.getThenStatement() instanceof Block thenBlock) {
						List<Statement> stmts = thenBlock.statements();

						// Checks if there is only one line
						boolean isOneLine = stmts.size() == 1;
						if (!isOneLine)
							return false;

						if (stmts.get(0) instanceof ExpressionStatement exprStmt
								&& exprStmt.getExpression() instanceof Assignment assign) {
							leftOperand = assign.getLeftHandSide();
							rightOperand = assign.getRightHandSide();
							Assignment.Operator assignOperator = assign.getOperator();
							System.out.println("Left: " + leftOperand.getClass());
							System.out.println("Right: " + rightOperand.getClass());
							System.out.println("Operator: " + assignOperator.getClass());
							if (leftOperand instanceof SimpleName varName) {
								if (operator == InfixExpression.Operator.EQUALS) {
									Sentinel sent = new Sentinel();
									sent.setNotNullValue(rightOperand.toString());
									sent.setVarName(ogVarName);
									sentinels.put(varName.toString(), sent);
									System.out.println(sent);
								} else if (operator == InfixExpression.Operator.NOT_EQUALS) {
									Sentinel sent = new Sentinel();
									sent.setNotNullValue(rightOperand.toString());
									sent.setVarName(ogVarName);
									sentinels.put(varName.toString(), sent);
								}
							} else if (rightOperand instanceof SimpleName varName) {
								if (operator == InfixExpression.Operator.EQUALS) {
									Sentinel sent = new Sentinel();
									sent.setNotNullValue(rightOperand.toString());
									sent.setVarName(ogVarName);
									sentinels.put(varName.toString(), sent);
								} else if (operator == InfixExpression.Operator.NOT_EQUALS) {
									Sentinel sent = new Sentinel();
									sent.setNotNullValue(rightOperand.toString());
									sent.setVarName(ogVarName);
									sentinels.put(varName.toString(), sent);
								}
							}
						}
					}
				} else if ((rightOperand instanceof SimpleName && leftOperand instanceof NullLiteral)) {

				}
			}
		}
		return false;
	}

	@Override
	public void apply(ASTNode node, ASTRewrite rewriter) {
		if (node instanceof IfStatement ifStmt) {
			System.out.println("[DEBUG] If-statement");
			Expression condition = ifStmt.getExpression();
			if (condition instanceof InfixExpression infix
					&& (infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
							|| infix.getOperator() == InfixExpression.Operator.EQUALS)) {
				Expression leftOperand = infix.getLeftOperand();
				Expression rightOperand = infix.getRightOperand();
				Operator operator = infix.getOperator();
				Sentinel sentinel = null;
				Expression checkValue = null;
				if ((leftOperand instanceof SimpleName lhs)) {
					sentinel = sentinels.get(lhs.toString());
					checkValue = rightOperand;

				} else if (rightOperand instanceof SimpleName rhs) {
					sentinel = sentinels.get(rhs.toString());
					checkValue = leftOperand;
				}
				if (sentinel == null) {
					return;
				}

				System.out.println(sentinel.getNotNullValue());
				System.out.println(checkValue.toString());
				AST ast = node.getAST();
				InfixExpression newCheck = ast.newInfixExpression();
				newCheck.setLeftOperand(ast.newSimpleName(sentinel.getVarName().toString()));
				newCheck.setRightOperand(ast.newNullLiteral());
				if (sentinel.getNotNullValue().equals(checkValue.toString())) {
					newCheck.setOperator(operator);
				} else {
					operator = operator == InfixExpression.Operator.EQUALS
							? InfixExpression.Operator.NOT_EQUALS
							: InfixExpression.Operator.EQUALS;
					newCheck.setOperator(operator);
				}
				System.out.println("Replacing \n" + condition + "\nWith\n" + newCheck);
				rewriter.replace(condition, newCheck, null);

			}
		}
	}
}
