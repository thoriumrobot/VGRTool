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
 * Class to perform JUnit tests on the SentinelTesting refactoring module
 * 
 * @see SentinelTesting
 */
public class SentinelTesting {

	public void test(String input, String expectedOutput) {
		TestingEngine.testSingleRefactoring(input, expectedOutput, "SentinelRefactoring");
	}

	@Test
	public void simpleTest() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (val == -1) {
				            System.out.println("ERROR: str is null");
				        }
				    }
				}
				        """;
		String expectedOutput = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (str == null) {
				            System.out.println("ERROR: str is null");
				        }
				    }
				}
				                        """;
		test(input, expectedOutput);
	}

	@Test
	public void swappedSignsTest1() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (val != -1) {
				            System.out.println("ERROR: str is null");
				        }
				    }
				}
				        """;
		String expectedOutput = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (str != null) {
				            System.out.println("ERROR: str is null");
				        }
				    }
				}
				                        """;
		test(input, expectedOutput);
	}

	@Test
	public void swappedSignsTest2() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str != null) {
				            val = -1;
				        }

				        if (val == -1) {
				            System.out.println("Str is not null");
				        }
				    }
				}
				        """;
		String expectedOutput = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str != null) {
				            val = -1;
				        }

				        if (str != null) {
				            System.out.println("Str is not null");
				        }
				    }
				}
				                        """;
		test(input, expectedOutput);
	}

	@Test
	public void indirectCheck() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (val == 0) {
				            System.out.println("Str is not null");
				        }
				    }
				}
				        """;
		String expectedOutput = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (str != null) {
				            System.out.println("Str is not null");
				        }
				    }
				}
				                        """;
		test(input, expectedOutput);
	}
}
