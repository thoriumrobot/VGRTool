import org.eclipse.jdt.core.dom.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RefactoringEngineTest {

    @Test
    public void testReplaceIndirectNullCheck() {
        // Input Java source before refactoring
        String sourceCode = 
            "class Test {\n" +
            "    void test() {\n" +
            "        Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);\n" +
            "        Object exceptionHandlerObject = null;\n" +
            "        Method exceptionHandlerMethod = null;\n" +
            "\n" +
            "        if (handlerType != null) {\n" +
            "            exceptionHandlerObject = handlerMethod.getBean();\n" +
            "        }\n" +
            "    }\n" +
            "}";

        // Expected output after refactoring
        String expectedOutput =
            "class Test {\n" +
            "    void test() {\n" +
            "        Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);\n" +
            "        Object exceptionHandlerObject = null;\n" +
            "        Method exceptionHandlerMethod = null;\n" +
            "\n" +
            "        if (handlerMethod != null) {\n" + // Expected fix: Check handlerMethod directly
            "            exceptionHandlerObject = handlerMethod.getBean();\n" +
            "        }\n" +
            "    }\n" +
            "}";

        // Parse the source code into an AST
        CompilationUnit cu = parseSource(sourceCode);
        
        // Configure the refactoring engine
        List<String> refactorings = Collections.singletonList("AddNullCheckBeforeDereferenceRefactoring");
        Set<Expression> expressionsPossiblyNull = new HashSet<>();
        RefactoringEngine engine = new RefactoringEngine(refactorings, expressionsPossiblyNull);

        // Apply refactoring
        String result = engine.applyRefactorings(cu, sourceCode);

        // Assert that the output matches the expected transformation
        assertEquals(expectedOutput, result);
    }

    /**
     * Parses Java source code into an AST CompilationUnit.
     */
    private CompilationUnit parseSource(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS17); // Use appropriate JLS version
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        return (CompilationUnit) parser.createAST(null);
    }
}
