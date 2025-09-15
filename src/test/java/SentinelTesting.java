import org.junit.jupiter.api.Test;

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
	public void swappedSignsTest() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (val != -1) {
				            System.out.println("str is not null");
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
				            System.out.println("str is not null");
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
				            val = 1;
				        }

				        if (val == 1) {
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
				            val = 1;
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

	@Test
	public void shadowingTest() {
		String input = """
				public class SentinelTest {
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
				public class SentinelTest {
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

	public void variableReassignmentTest() {
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

					val = 0;

				        if (val == 0) {
						;
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

					val = 0;

				        if (val == 0) {
						;
				        }
				    }
				}
				                        """;
		test(input, expectedOutput);
	}

	@Test
	public void elseIfTest() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (val != -1) {
				            System.out.println("str is not null");
					} else if (val == -1) {
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
				            System.out.println("str is not null");
					} else if (str == null) {
				            System.out.println("ERROR: str is null");
				        }
				    }
				}
					""";
		test(input, expectedOutput);
	}

	@Test
	public void infixConditionalTest() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (val == -1 && 5 > 3) {
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

				        if (str == null && 5 > 3) {
				            System.out.println("ERROR: str is null");
				        }
				    }
				}
					""";
		test(input, expectedOutput);
	}

	@Test
	public void inverseCheck() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str == null) {
				            val = -1;
				        }

				        if (!(val == -1)) {
				            System.out.println("str is null");
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

				        if (!(str == null)) {
				            System.out.println("str is null");
				        }
				    }
				}
					""";
		test(input, expectedOutput);
	}

	@Test
	public void defaultValue() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

					if (val == 0) {
				            System.out.println("This tells us nothing.");
				        }

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

					if (val == 0) {
				            System.out.println("This tells us nothing.");
				        }

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
	public void shadowingTest2() {
		String input = """
				public class SentinelTest {
				    int val = 0;
				    public void test() {
				        String str = "Hello World";
				        if (str == null) {
				            val = -1;
				        }

					if (val == 0) {
				            System.out.println("str is not null");
				        }

					int val = 1;

				        if (val == 0) {
				            System.out.println("This tells us nothing");
				        }
				    }
				        """;
		String expectedOutput = """
				public class SentinelTest {
				    int val = 0;
				    public void test() {
				        String str = "Hello World";
				        if (str == null) {
				            val = -1;
				        }

					if (str != null) {
				            System.out.println("str is not null");
				        }

					int val = 1;

				        if (val == 0) {
				            System.out.println("This tells us nothing");
				        }
				    }
				        """;
		test(input, expectedOutput);
	}

	@Test
	public void indeterminateNullnessTest() {
		String input = """
				public class SentinelTest {
				    public void test() {
				        String str = "Hello World";
				        int val = 0;

				        if (str != null) {
				            val = 0;
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

				        if (str != null) {
				            val = 0;
				        }

				        if (val == 0) {
				            System.out.println("Str is not null");
				        }
				    }
				}
				        """;
		test(input, expectedOutput);
	}

}
