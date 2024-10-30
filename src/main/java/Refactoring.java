import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

abstract class Refactoring {
  public abstract boolean isApplicable(ASTNode node);

  public abstract void apply(ASTNode node, ASTRewrite rewriter);
}
