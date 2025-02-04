import java.util.List;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
// (Assume Refactoring is an abstract base class provided in the same framework)
public class AddNullCheckBeforeDereferenceRefactoring extends Refactoring {
    /** Optional list of expressions identified as possibly null (to guide applicability) */
    private List<Expression> expressionsPossiblyNull;
    
    /** Default constructor (for RefactoringEngine integration) */
    public AddNullCheckBeforeDereferenceRefactoring() {
        super();  // Call to base class (if it expects a name/ID)
    }
    /** Constructor that accepts a list of possibly-null expressions */
    public AddNullCheckBeforeDereferenceRefactoring(List<Expression> expressionsPossiblyNull) {
        super();
        this.expressionsPossiblyNull = expressionsPossiblyNull;
    }
    
    @Override
    public boolean isApplicable(ASTNode node) {
        // Only consider method calls, field accesses, qualified names, or array accesses as dereference points
        Expression targetExpr = null;
        if (node instanceof MethodInvocation) {
            // For method calls, check the object on which the method is invoked
            targetExpr = ((MethodInvocation) node).getExpression();
            if (targetExpr == null) {
                // If null, this is a static method call or implicit this, no null-check needed
                return false;
            }
        } else if (node instanceof FieldAccess) {
            // For explicit field access (object.field)
            targetExpr = ((FieldAccess) node).getExpression();
        } else if (node instanceof QualifiedName) {
            // QualifiedName can represent a field access (object.field) or a package/class reference.
            QualifiedName qName = (QualifiedName) node;
            // Use the qualifier if this is an instance field access (heuristic: assume non-uppercase start means instance var)
            // (Alternatively, type binding check would distinguish static vs instance, but assume simple heuristic or context.)
            targetExpr = qName.getQualifier();
        } else if (node instanceof ArrayAccess) {
            // For array access expression object[index], the array expression is the target
            targetExpr = ((ArrayAccess) node).getArray();
        } else {
            // Other types of nodes are not applicable for this refactoring
            return false;
        }
        if (targetExpr == null) {
            return false;
        }
        // If a list of possibly-null expressions is provided, use it to filter applicability
        if (expressionsPossiblyNull != null && !expressionsPossiblyNull.isEmpty()) {
            // Only applicable if the target expression is one of the known possibly-null expressions
            if (!expressionsPossiblyNull.contains(targetExpr)) {
                return false;
            }
        }
        // Also ensure we can wrap the entire statement containing the node
        ASTNode parentNode = node.getParent();
        if (parentNode == null) {
            return false;
        }
        // Only proceed if the node's parent is a statement that we can replace (to insert the if-block)
        // e.g., an ExpressionStatement (a standalone call or assignment), or an Assignment within an ExpressionStatement.
        if (parentNode instanceof Statement) {
            // If the parent is a ReturnStatement, we skip because handling return with null-check is non-trivial here
            if (parentNode.getNodeType() == ASTNode.RETURN_STATEMENT) {
                return false;
            }
            return true;  // The node is used in a statement context that can be handled
        }
        return false;
    }
    
    @Override
    public void apply(ASTNode node, ASTRewrite rewriter) {
        // Precondition: isApplicable returned true for this node
        AST ast = node.getAST();  // Get the AST to create new nodes
        Expression targetExpr;
        // Determine the target (the object being dereferenced) similar to isApplicable logic
        if (node instanceof MethodInvocation) {
            targetExpr = ((MethodInvocation) node).getExpression();
        } else if (node instanceof FieldAccess) {
            targetExpr = ((FieldAccess) node).getExpression();
        } else if (node instanceof QualifiedName) {
            targetExpr = ((QualifiedName) node).getQualifier();
        } else if (node instanceof ArrayAccess) {
            targetExpr = ((ArrayAccess) node).getArray();
        } else {
            // Should not happen if isApplicable is correctly used
            return;
        }
        if (targetExpr == null) {
            // Safety check: no target to null-check
            return;
        }
        // Create a copy of the target expression for use in the null-check condition 
        Expression targetExprCopy = (Expression) ASTNode.copySubtree(ast, targetExpr);
        // Build the null-check condition: (targetExpr != null)
        InfixExpression condition = ast.newInfixExpression();
        condition.setLeftOperand(targetExprCopy);
        condition.setOperator(InfixExpression.Operator.NOT_EQUALS);
        condition.setRightOperand(ast.newNullLiteral());
        // Create the if statement with the condition
        IfStatement ifStatement = ast.newIfStatement();
        ifStatement.setExpression(condition);
        // The "then" block of the if will contain the original operation (node) as a statement
        Block thenBlock = ast.newBlock();
        // Find the statement to encapsulate. Usually, if node is part of an ExpressionStatement or Assignment,
        // we want to wrap that entire statement.
        ASTNode parentNode = node.getParent();
        Statement origStatement;
        if (parentNode instanceof Statement) {
            origStatement = (Statement) parentNode;
        } else {
            // In unexpected cases, just treat the node itself as a statement (though normally node will be inside a Statement)
            origStatement = (Statement) node;
        }
        // Copy the original statement (deep copy) to place inside the if-block
        Statement origStatementCopy = (Statement) ASTNode.copySubtree(ast, origStatement);
        thenBlock.statements().add(origStatementCopy);
        ifStatement.setThenStatement(thenBlock);
        // **Apply the replacement**: replace the original statement with the new if-statement in the AST
        rewriter.replace(origStatement, ifStatement, null);  //&#8203;:contentReference[oaicite:2]{index=2}
    }
}

