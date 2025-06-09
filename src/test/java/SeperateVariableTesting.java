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

/**
 * Class to perform JUnit tests on the SeperateVariableRefactoring refactoring
 * module
 * 
 * @see SeperateVariableRefactoring
 */
public class SeperateVariableTesting {

	public void test(String input, String expectedOutput) {
		TestingEngine.testSingleRefactoring(input, expectedOutput, "SeperateVariableRefactoring");
	}

	@Test
	public void simpleTest() {
		String input = """
				public class SeperateVariableTest {
				    public void printNonNull(@NonNull String str) {
				        System.out.println(str);
				    }

				    public void test() {
				        String str = "Hello World";

				        Object refObject = str != null ? new Object() : null;

				        if (refObject != null) {
				            printNonNull(str);
				        }
				    }
				}
				""";
		String expectedOutput = """
				public class SeperateVariableTest {
				    public void printNonNull(@NonNull String str) {
				        System.out.println(str);
				    }

				    public void test() {
				        String str = "Hello World";

				        Object refObject = str != null ? new Object() : null;

				        if (str != null) {
				            printNonNull(str);
				        }
				    }
				}
				""";
		test(input, expectedOutput);
	}
}
