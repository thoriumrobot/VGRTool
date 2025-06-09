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
 * Class to perform JUnit tests on the NestedNullRefactoring refactoring module
 * 
 * @see NestedNullRefactoring
 */
public class NestedNullTesting {

	public void test(String input, String expectedOutput) {
		TestingEngine.testSingleRefactoring(input, expectedOutput, "NestedNullRefactoring");
	}

	@Test
	public void simpleTest() {
		String input = """
				public class NestedNullTest {
				    String str = "Hello World";

				    public boolean checkNull() {
				        return str != null;
				    }

				    public void test() {

				        if (checkNull()) {
				            ;
				        }

				        if (!checkNull()) {
				            ;
				        }
				    }
				}
				""";
		String expectedOutput = """
				public class NestedNullTest {
				    String str = "Hello World";

				    public boolean checkNull() {
				        return str != null;
				    }

				    public void test() {

				        if (str != null) {
				            ;
				        }

				        if (!(str != null)) {
				            ;
				        }
				    }
				}
				""";
	}
}
