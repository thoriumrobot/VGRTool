package org.example.utils;

import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Utility class for refactoring operations, providing reusable methods to support multiple
 * refactoring tasks.
 */
public class RefactoringUtils {

  public static String getFieldName(ASTNode node) {
    if (node instanceof FieldAccess) {
      return ((FieldAccess) node).getName().getIdentifier();
    } else if (node instanceof SimpleName) {
      SimpleName simpleName = (SimpleName) node;
      if (!simpleName.isDeclaration() && isField(simpleName)) {
        return simpleName.getIdentifier();
      }
    }
    return null;
  }

  public static boolean isField(SimpleName simpleName) {
    IBinding binding = simpleName.resolveBinding();
    return binding instanceof IVariableBinding && ((IVariableBinding) binding).isField();
  }

  public static VariableDeclarationFragment findVariableDeclaration(ASTNode node, String varName) {
    ASTNode current = node;
    while (current != null) {
      if (current instanceof Block) {
        Block block = (Block) current;
        for (Object stmt : block.statements()) {
          if (stmt instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varStmt = (VariableDeclarationStatement) stmt;
            for (Object frag : varStmt.fragments()) {
              VariableDeclarationFragment fragment = (VariableDeclarationFragment) frag;
              if (fragment.getName().getIdentifier().equals(varName)) {
                return fragment;
              }
            }
          }
        }
      }
      current = current.getParent();
    }
    return null;
  }

  public static boolean hasNullnessAnnotation(List<?> modifiers) {
    for (Object modifier : modifiers) {
      if (modifier instanceof Annotation) {
        Annotation annotation = (Annotation) modifier;
        String name = annotation.getTypeName().getFullyQualifiedName();
        if (name.equals("Nullable") || name.equals("NonNull")) {
          return true;
        }
      }
    }
    return false;
  }

  public static int extractLineNumber(String warning) {
    try {
      String[] parts = warning.split(":");
      return Integer.parseInt(parts[0].replace("Line ", "").trim());
    } catch (Exception e) {
      return -1;
    }
  }
}
