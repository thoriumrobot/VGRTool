
import org.junit.jupiter.api.Test;

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

				        if ((str != null)) {
				            ;
				        }

				        if (!(str != null)) {
				            ;
				        }
				    }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void overloadTest() {
		String input = """
				public class NestedNullTest {
				    String str = "Hello World";

				    public boolean checkNullOverloaded() {
				        return str != null;
				    }

				    public boolean checkNullOverloaded(Object var) {
				        return var != null;
				    }

				    public void test() {

				        if (checkNullOverloaded()) {
				            ;
				        }

				        if (!checkNullOverloaded()) {
				            ;
				        }

				        if (checkNullOverloaded(null)) {
				            ;
				        }

				        if (!checkNullOverloaded(null)) {
				            ;
				        }
				    }
				}
				""";
		String expectedOutput = """
				public class NestedNullTest {
				    String str = "Hello World";

				    public boolean checkNullOverloaded() {
				        return str != null;
				    }

				    public boolean checkNullOverloaded(Object var) {
				        return var != null;
				    }

				    public void test() {

				        if ((str != null)) {
				            ;
				        }

				        if (!(str != null)) {
				            ;
				        }

				        if (checkNullOverloaded(null)) {
				            ;
				        }

				        if (!checkNullOverloaded(null)) {
				            ;
				        }
				    }
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void checkEqualsNullTest() {
		String input = """
				public class NestedNullTest {
				    String str = "Hello World";

				    public boolean checkEqualsNull() {
				        return str == null;
				    }

				    public void test() {

				        if (checkEqualsNull()) {
				            ;
				        }

				        if (!checkEqualsNull()) {
				            ;
				        }
				    }
				}
				""";
		String expectedOutput = """
				public class NestedNullTest {
				    String str = "Hello World";

				    public boolean checkEqualsNull() {
				        return str == null;
				    }

				    public void test() {

				        if ((str == null)) {
				            ;
				        }

				        if (!(str == null)) {
				            ;
				        }
				    }
				}
				""";
		test(input, expectedOutput);
	}
}
