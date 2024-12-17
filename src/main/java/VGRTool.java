import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

public class VGRTool {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java VGRTool <sourceDirPath> <refactoringModule>");
            System.out.println("Available Modules:");
            System.out.println(" - WrapWithCheckNotNullRefactoring");
            System.out.println(" - AddNullChecksForNullableReferences");
            System.out.println(" - AddNullCheckBeforeDereferenceRefactoring");
            System.out.println(" - IntroduceLocalVariableAndNullCheckRefactoring");
            System.out.println(" - IntroduceLocalVariableWithNullCheckRefactoring");
            System.out.println(" - SimplifyNullCheckRefactoring");
            System.exit(1);
        }

        String targetDir = args[0];
        String refactoringModule = args[1];

        System.out.println("Processing directory: " + targetDir);
        System.out.println("Selected Refactoring Module: " + refactoringModule);

        try {
            // Step 1: Collect all Java files in the target directory
            List<File> javaFiles = findJavaFiles(targetDir);

            // Step 2: Process each Java file using the selected refactoring module
            for (File file : javaFiles) {
                System.out.println("Processing file: " + file.getPath());
                processFile(file, refactoringModule);
            }

            System.out.println("Refactoring completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<File> findJavaFiles(String directory) throws IOException {
        List<File> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(directory))
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(path -> javaFiles.add(path.toFile()));
        return javaFiles;
    }

    private static void processFile(File file, String refactoringModule) {
        try {
            // Step 3: Read the file content
            String content = Files.readString(file.toPath());
            Document document = new Document(content);

            // Step 4: Parse the content into an AST
            ASTParser parser = ASTParser.newParser(AST.JLS15); // Use Java 17
            parser.setSource(content.toCharArray());
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            // Step 5: Extract nullable expressions
            Set<Expression> nullableExpressions = extractExpressionsPossiblyNull(cu);

            // Step 6: Initialize RefactoringEngine with the selected module
            List<String> selectedModules = Collections.singletonList(refactoringModule);
            RefactoringEngine refactoringEngine = new RefactoringEngine(selectedModules, nullableExpressions);

            // Step 7: Apply refactorings using RefactoringEngine
            String refactoredSourceCode = refactoringEngine.applyRefactorings(cu, content);

            // Step 8: Write the refactored code back to the file
            Files.writeString(file.toPath(), refactoredSourceCode);
            System.out.println("Refactored file saved: " + file.getPath());

        } catch (Exception e) {
            System.err.println("Error processing file: " + file.getPath());
            e.printStackTrace();
        }
    }

    private static Set<Expression> extractExpressionsPossiblyNull(CompilationUnit cu) {
        Set<Expression> expressions = new HashSet<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                if (node.getExpression() != null) {
                    expressions.add(node.getExpression());
                }
                return super.visit(node);
            }

            @Override
            public boolean visit(FieldAccess node) {
                if (node.getExpression() != null) {
                    expressions.add(node.getExpression());
                }
                return super.visit(node);
            }
        });
        return expressions;
    }
}
