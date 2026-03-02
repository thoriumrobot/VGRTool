import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.apache.logging.log4j.LogManager;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

/**
 * Class to run VGRs on an AST
 */
public class RefactoringEngine {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Logger SEARCH_LOGGER = LogManager.getLogger("SearchLogger");

	/**
	 * List of refactorings to apply
	 */
	private final List<Refactoring> refactorings;

	public RefactoringEngine(List<String> refactoringNames) {
		refactorings = new ArrayList<>();

		for (String name : refactoringNames) {
			switch (name) {
				case AddNullCheckBeforeDereferenceRefactoring.NAME ->
					refactorings.add(new AddNullCheckBeforeDereferenceRefactoring());
				case BooleanFlagRefactoring.NAME -> refactorings.add(new BooleanFlagRefactoring());
				case SentinelRefactoring.NAME -> refactorings.add(new SentinelRefactoring());
				case NestedNullRefactoring.NAME -> refactorings.add(new NestedNullRefactoring());
				case "All" -> refactorings.addAll(Arrays.asList(new AddNullCheckBeforeDereferenceRefactoring(),
						new BooleanFlagRefactoring(), new SentinelRefactoring(), new NestedNullRefactoring()));
				default -> System.err.println("Unknown refactoring: " + name);
			}

			if (refactorings.isEmpty()) {
				System.err.println("No valid refactorings specified. Exiting.");
				System.exit(1);
			}
		}
	}

	/**
	 * Applies all refactorings in {@value refactorings} to a given source file
	 * 
	 * @param cu
	 *            The compilation unit to use
	 * @param sourceCode
	 *            A string representing the filepath of the source code to refactor
	 */
	public @NonNull String applyRefactorings(CompilationUnit cu, String sourceCode) {
		AST ast = cu.getAST();
		ASTRewrite rewriter = ASTRewrite.create(ast);

		for (Refactoring refactoring : refactorings) {
			cu.accept(new ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					LOGGER.debug("Visiting AST Node {}", node);

					if (refactoring.isApplicable(node)) {
						LOGGER.info("Applying refactoring to AST Node:\n {}", node);
						refactoring.apply(node, rewriter);
					}
				}
			});
		}

		Document document = new Document(sourceCode);

		TextEdit edits = rewriter.rewriteAST(document, null);
		try {
			edits.apply(document);
		} catch (MalformedTreeException | org.eclipse.jface.text.BadLocationException e) {
			LOGGER.error("Failed to rewrite AST for document '{}'", document, e);
		}

		return document.get();
	}

	/**
	 * Searches a given source file for applicable refactorings in
	 * {@value refactorings} Search results are logged to a CSV file via a dedicated
	 * logger ("SearchLogger")
	 * 
	 * @param cu
	 *            The compilation unit to use
	 * @param sourceCode
	 *            A string containing the source code of the file to search through
	 * @param filePath
	 *            A string containing the patth of the file to search through
	 * @param fileName
	 *            A string containing the name of the file to search through
	 */
	public void searchRefactorings(CompilationUnit cu, String sourceCode, String filePath, String fileName) {
		Document document = new Document(sourceCode);

		for (Refactoring refactoring : refactorings) {
			cu.accept(new ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					LOGGER.debug("Visiting AST Node {}", node);

					if (!refactoring.isApplicable(node)) {
						return;
					}

					String refactoringModule = refactoring.getClass().getSimpleName();
					int lineNumber = cu.getLineNumber(node.getStartPosition());
					String nodeClass = node.getClass().getSimpleName();
					// Escape all new lines and quote the code snippet to preserve csv formatting
					String codeSnippet = "\"" + node.toString().replace("\"", "\"\"") + "\"";

					SEARCH_LOGGER.info("{},{},{},{},{},{}", refactoringModule, filePath, fileName, lineNumber,
							nodeClass, codeSnippet);
				}
			});
		}
	}
}
