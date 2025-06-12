import java.util.Collections;
import org.checkerframework.com.google.common.collect.Lists;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestingEngine {

<<<<<<< HEAD
	private Set<Expression> expressionsPossiblyNull;
	private RefactoringEngine fullEngine;
	private ASTParser parser;

	public static TestingEngine testEngine = new TestingEngine();
=======
	private static ASTParser parser = ASTParser.newParser(AST.getJLSLatest()); // Use appropriate
																				// JLS version
	;
>>>>>>> c8c22c3051 (Fixed all tests)

	public TestingEngine() {
		expressionsPossiblyNull = new HashSet<>();
		fullEngine = new RefactoringEngine(Lists.newArrayList("SentinelRefactoring",
				"AddNullCheckBeforeDereferenceRefactoring",
				"BooleanFlagRefactoring",
				"NestedNullRefactoring",
				"SentinelRefactoring"), expressionsPossiblyNull);
		parser = ASTParser.newParser(AST.JLS17); // Use appropriate JLS version
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
	}

	public void testAllRefactorings(String input, String expectedOutput) {
		// Set parser source code
		parser.setSource(input.toCharArray());

		// Parse the source code into an AST
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		// Apply refactoring
		String result = fullEngine.applyRefactorings(cu, input);

		// Assert that the output matches the expected transformation
		assertEquals(expectedOutput, result);
	}

<<<<<<< HEAD
	public void testSingleRefactoring(String input, String expectedOutput, String refactoring) {
=======
	public static void testSingleRefactoring(String input, String expectedOutput,
			String refactoring) {
		System.out.println("Testing input:\n" + input + "\n");

>>>>>>> c8c22c3051 (Fixed all tests)
		// Set parser source code
		parser.setSource(input.toCharArray());

		// Parse the source code into an AST
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		// Set engine
		RefactoringEngine engine = new RefactoringEngine(Collections.singletonList(refactoring),
				expressionsPossiblyNull);
		// Apply refactoring
		String result = engine.applyRefactorings(cu, input);

		// Assert that the output matches the expected transformation
		assertEquals(expectedOutput, result);

	}
}
