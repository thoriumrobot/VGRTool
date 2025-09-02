
import org.junit.jupiter.api.Test;

/**
 * Class to perform JUnit tests on the AddNullCheckBeforeDereferenceRefactoring
 * refactoring module
 * 
 * @see DereferenceTesting
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
						if ((independentObj != null ? 0 : null) != null) {
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
						Class<?> dependentObj = (independentObj == null ? independentObj.getDependent() : null);
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj == null ? independentObj.getDependent() : null);
						if ((independentObj == null ? 0 : null) != null) {
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
						if (!((independentObj != null ? 0 : null) != null)) {
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
						if (((independentObj != null && 5 > 3) ? 0 : null) != null) {
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
						Class<?> dependentObj = (independentObj != null ? null : independentObj.getDependent());
						if (dependentObj != null) {
							;
						}
					}
				}
				""";
		String expectedOutput = """
				public class Test {
					private void test() {
						Class<?> dependentObj = (independentObj != null ? null : independentObj.getDependent());
						if ((independentObj != null ? null : 0) != null) {
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
}
