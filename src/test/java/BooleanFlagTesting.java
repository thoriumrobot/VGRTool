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
 * Class to perform JUnit tests on the BooleanFlagRefactoring refactoring module
 * 
 * @see BooleanFlagRefactoring
 */
public class BooleanFlagTesting {

	public void test(String input, String expectedOutput) {
		TestingEngine.testSingleRefactoring(input, expectedOutput, "BooleanFlagRefactoring");
	}

	@Test
	public void simpleTest() {
		String input = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNull = x == null;
				    if (xIsNull) {
				      ;
				    }
				  }
				}
				""";
		String expectedOutput = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNull = x == null;
				    if (x == null) {
				      ;
				    }
				  }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void swappedSignsTest() {
		String input = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNotNull = x != null;
				    if (xIsNotNull) {
				      ;
				    }
				  }
				}
				""";
		String expectedOutput = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNotNull = x != null;
				    if (x != null) {
				      ;
				    }
				  }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void inverseFlagTest1() {
		String input = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNotNull = x != null;
				    if (!xIsNotNull) {
				      ;
				    }
				  }
				}
				""";
		String expectedOutput = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNotNull = x != null;
				    if (x == null) {
				      ;
				    }
				  }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void inverseFlagTest2() {
		String input = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNotNull = x != null;
				    if (!xIsNotNull) {
				      ;
				    }
				  }
				}
				""";
		String expectedOutput = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNotNull = x != null;
				    if (x == null) {
				      ;
				    }
				  }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void andConditionTest() {
		String input = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNull = x == null;
				    if (xIsNull && 1 > 0) {
				      ;
				    }
				  }
				}
				""";
		String expectedOutput = """
				public class BooleanFlagTest {
				  public void testMethod(String x) {
				    boolean xIsNull = x == null;
				    if (x == null && 1 > 0) {
				      ;
				    }
				  }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void ternaryTest() {
		String input = """
				class TernaryBooleanFlagTest {
				    @SuppressWarnings("all")
				    void test() {
				        boolean xIsNull = (handlerMethod == null ? true : false);
				        Object exceptionHandlerObject = null;
				        Method exceptionHandlerMethod = null;

				        if (xIsNull) {
				           ;
				        }
				    }
				}
				""";
		String expectedOutput = """
				class TernaryBooleanFlagTest {
				    @SuppressWarnings("all")
				    void test() {
				        boolean xIsNull = (handlerMethod == null ? true : false);
				        Object exceptionHandlerObject = null;
				        Method exceptionHandlerMethod = null;

				        if (handlerMethod == null) {
				           ;
				        }
				    }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void inverseTernaryTest() {
		String input = """
				class TernaryBooleanFlagTest {
				    @SuppressWarnings("all")
				    void test() {
				        boolean xIsNotNull = (handlerMethod != null ? true : false);
				        Object exceptionHandlerObject = null;
				        Method exceptionHandlerMethod = null;

				        if (!xIsNotNull) {
				           ;
				        }
				    }
				}
				""";
		String expectedOutput = """
				class TernaryBooleanFlagTest {
				    @SuppressWarnings("all")
				    void test() {
				        boolean xIsNotNull = (handlerMethod != null ? true : false);
				        Object exceptionHandlerObject = null;
				        Method exceptionHandlerMethod = null;

				        if (handlerMethod == null) {
				           ;
				        }
				    }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void newContainerTest() {
		String input = """
				public class NewContainerTest {
				    List<String> items = Arrays.asList("Hello World");

				    public void test() {
				        boolean hasItems = (items != null && !items.isEmpty());

				        // Indirectly implies items != null
				        if (hasItems) {
				            TreeSet<?> set = new TreeSet<>(items);
				        }
				    }
				}
				""";
		String expectedOutput = """
				public class NewContainerTest {
				    List<String> items = Arrays.asList("Hello World");

				    public void test() {
				        boolean hasItems = (items != null && !items.isEmpty());

				        // Indirectly implies items != null
				        if ((items != null && !items.isEmpty())) {
				            TreeSet<?> set = new TreeSet<>(items);
				        }
				    }
				}
				""";
		test(input, expectedOutput);
	}
}
