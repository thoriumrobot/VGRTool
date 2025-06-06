import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.checkerframework.com.google.common.collect.Lists;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TestingEngine {

	private Set<Expression> expressionsPossiblyNull;
	private RefactoringEngine fullEngine;
	private ASTParser parser;

	public static TestingEngine testEngine = new TestingEngine();

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

	public void testSingleRefactoring(String input, String expectedOutput, String refactoring) {
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
