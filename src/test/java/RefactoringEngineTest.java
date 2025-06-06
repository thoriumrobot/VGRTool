import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class RefactoringEngineTest {
    // Configure the refactoring engine
    List<String> refactorings = Collections.singletonList("AddNullCheckBeforeDereferenceRefactoring");

    public void test(String testFileName) {
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
            assertEquals(expectedOutput, result);
        } catch (IOException e) {
            fail("IOException on file " + testFileName + ": " + e.getMessage());
        }
    }

    @Test
    public void simpleTest() {
        String input = """
                public class SentinelTest {
                    public void printNonNull(@NonNull String str) {
                        System.out.println(str);
                    }

                    public void test() {
                        String str = "Hello World";
                        int val = 0;

                        if (str == null) {
                            val = -1;
                        }

                        if (val == -1) {
                            System.out.println("ERROR: str is null");
                        }

                        if (val != -1) {
                            printNonNull(str);
                        }

                        if (val == 0) {
                            printNonNull(str);
                        }
                    }
                }
                        """;
        String output = """
                public class SentinelTest {
                    public void printNonNull(@NonNull String str) {
                        System.out.println(str);
                    }

                    public void test() {
                        String str = "Hello World";
                        int val = 0;

                        if (str == null) {
                            val = -1;
                        }

                        if (str == null) {
                            System.out.println("ERROR: str is null");
                        }

                        if (str != null) {
                            printNonNull(str);
                        }

                        if (str != null) {
                            printNonNull(str);
                        }
                    }
                }
                                        """;

        Set<Expression> expressionsPossiblyNull = new HashSet<>();
        RefactoringEngine engine = new RefactoringEngine(refactorings, expressionsPossiblyNull);
        @SuppressWarnings("deprecation")
        ASTParser parser = ASTParser.newParser(AST.JLS17); // Use appropriate JLS version
        parser.setSource(input.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);

        // Parse the source code into an AST
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        // Apply refactoring
        String result = engine.applyRefactorings(cu, input);

        // Assert that the output matches the expected transformation
        assertEquals(output, result);

    }

    private String readFile(String filename) throws IOException {
        InputStream fileStream = Objects.requireNonNull(this.getClass().getResourceAsStream(filename),
                "Test input file not found: " + filename);
        return IOUtils.toString(fileStream, StandardCharsets.UTF_8);
    }
}
