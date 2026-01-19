
import org.junit.jupiter.api.Test;

/**
 * Class to perform JUnit tests on the AddNullCheckBeforeDereferenceRefactoring
 * refactoring module
 */
public class DereferenceTesting {

	public void test(String input, String expectedOutput) {
		TestingEngine.testSingleRefactoring(input, expectedOutput, AddNullCheckBeforeDereferenceRefactoring.NAME);
	}

	@Test
	public void simpleTest() {
		String input = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						if ((independentObj != null)) {
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
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj == null ? factory.getDependent() : null);
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj == null ? factory.getDependent() : null);
						if ((independentObj == null)) {
							;
						}
					}
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void prefixTest() {
		String input = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						if (!(dependentObj != null)) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						if (!((independentObj != null))) {
							;
						}
					}
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void infixTest() {
		String input = """
				public class Test {
					private void test() {
						Class<?> dependentObj = ((independentObj != null && 5 > 3) ? independentObj.getDependent() : null);
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = ((independentObj != null && 5 > 3) ? independentObj.getDependent() : null);
						if (((independentObj != null && 5 > 3))) {
							;
						}
					}
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void swappedSidesTest() {
		String input = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? null : factory.getDependent());
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? null : factory.getDependent());
						if ((!(independentObj != null))) {
							;
						}
					}
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void reassignmentTest() {
		String input = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						dependentObj = null;
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						dependentObj = null;
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void reassignmentTest2() {
		String input = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						dependentObj = someMethod();
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? independentObj.getDependent() : null);
						dependentObj = someMethod();
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		test(input, expectedOutput);
	}

	@Test
	public void shadowingTest() {
		String input = """
				public class Test {
				    int val = 0;
				    public void test() {
				        int val = 0;
				        String str = "Hello World";
				        if (str == null) {
				            val = -1;
				        }
				        if (this.val == 0) {
				            System.out.println("Str is not null");
				        }
				    }
				        """;
		String expectedOutput = """
				public class Test {
				    int val = 0;
				    public void test() {
				        int val = 0;
				        String str = "Hello World";
				        if (str == null) {
				            val = -1;
				        }
				        if (this.val == 0) {
				            System.out.println("Str is not null");
				        }
				    }
				        """;
		test(input, expectedOutput);
	}
}
