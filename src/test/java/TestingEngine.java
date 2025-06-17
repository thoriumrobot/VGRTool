import java.util.Collections;
import org.checkerframework.com.google.common.collect.Lists;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Utility class for other testing classes to use to run tests
 */
public class TestingEngine {
	/**
	 * RefactoringEngine to use to run tests
	 */
	private static RefactoringEngine fullEngine = new RefactoringEngine(
			Lists.newArrayList("SentinelRefactoring", "AddNullCheckBeforeDereferenceRefactoring",
					"BooleanFlagRefactoring", "NestedNullRefactoring", "SentinelRefactoring"));;

	private static ASTParser parser = ASTParser.newParser(AST.getJLSLatest()); // Use appropriate
																				// JLS version
	;

	public TestingEngine() {
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
	}

	public static void testAllRefactorings(String input, String expectedOutput) {
		// Set parser source code
		parser.setSource(input.toCharArray());

		// Parse the source code into an AST
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		// Apply refactoring
		String result = fullEngine.applyRefactorings(cu, input);

		// Assert that the output matches the expected transformation
		assertEquals(expectedOutput, result);
	}

	public static void testSingleRefactoring(String input, String expectedOutput,
			String refactoring) {

		// Set parser source code
		parser.setSource(input.toCharArray());

		// Parse the source code into an AST
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		// Set engine
		RefactoringEngine engine = new RefactoringEngine(Collections.singletonList(refactoring));
		// Apply refactoring
		String result = engine.applyRefactorings(cu, input);

		// Assert that the output matches the expected transformation
		assertEquals(expectedOutput, result);
	}
}
