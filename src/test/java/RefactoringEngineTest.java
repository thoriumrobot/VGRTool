import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class RefactoringEngineTest {
	// Configure the refactoring engine
	// "BooleanFlagRefactoring",
	// "NestedNullRefactoring","AddNullCheckBeforeDereferenceRefactoring",
	List<String> refactorings = Lists.newArrayList("SentinelRefactoring");

	@SuppressWarnings("unused")
	private static Stream<String> getTestFiles() {
		ClassLoader classLoader = RefactoringEngineTest.class.getClassLoader();
		URL resource = Objects.requireNonNull(classLoader.getResource("inputs"));
		File folder = null;
		try {
			folder = new File(resource.toURI());
		} catch (URISyntaxException e) {
			fail("URISyntaxException on inputs folder:\n" + e.getMessage());
		}
		return Stream.of(Objects.requireNonNull(folder).listFiles()).map(File::getName);
	}

	@ParameterizedTest
	// @MethodSource("getTestFiles")
	@ValueSource(strings = {"SentinelTest.java"})
	public void test(String testFileName) {
		System.out.println("[DEBUG] Testing file " + testFileName);
		try {
			String sourceCode = readFile("inputs/" + testFileName);
			String expectedOutput = readFile("outputs/" + testFileName);

			Set<Expression> expressionsPossiblyNull = new HashSet<>();
			RefactoringEngine engine = new RefactoringEngine(refactorings, expressionsPossiblyNull);

			@SuppressWarnings("deprecation")
			ASTParser parser = ASTParser.newParser(AST.JLS17); // Use appropriate JLS version
			parser.setSource(sourceCode.toCharArray());
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setResolveBindings(false);

			// Parse the source code into an AST
			CompilationUnit cu = (CompilationUnit) parser.createAST(null);

			// Apply refactoring
			String result = engine.applyRefactorings(cu, sourceCode);

			// Assert that the output matches the expected transformation
			System.out.println("[DEBUG] Finished testing file " + testFileName + "\n");
			assertEquals(expectedOutput, result);
		} catch (IOException e) {
			System.out.println("[DEBUG] Finished testing file " + testFileName + "\n");
			fail("IOException on file " + testFileName + ": " + e.getMessage());
		}
	}

	private String readFile(String filename) throws IOException {
		InputStream fileStream = Objects.requireNonNull(this.getClass().getResourceAsStream(filename),
				"Test input file not found: " + filename);
		return IOUtils.toString(fileStream, StandardCharsets.UTF_8);
	}
}
