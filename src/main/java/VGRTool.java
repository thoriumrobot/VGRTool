import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class VGRTool {

  public static void main(String[] args) throws IOException {
    // Check if source file is provided
    if (args.length < 1) {
      System.out.println("Usage: java VGRTool <SourceFile.java> [Refactoring1 Refactoring2 ...]");
      return;
    }

    String sourceFilePath = args[0];
    String sourceCode = readFileToString(sourceFilePath);

    // Collect refactoring names from command-line arguments
    List<String> refactoringNames = new ArrayList<>();
    if (args.length > 1) {
      refactoringNames = Arrays.asList(args).subList(1, args.length);
    } else {
      System.out.println("No refactorings specified. No refactoring will be performed.");
      return;
    }

    // Parse the source code into an AST
    CompilationUnit cu = parse(sourceCode);

    // Run the verifier on the original code
    List<String> originalWarnings = runVerifier(sourceFilePath);

    if (originalWarnings.isEmpty()) {
      System.out.println("No warnings from the verifier. No refactoring needed.");
      return;
    } else {
      System.out.println("Verifier Warnings:");
      for (String warning : originalWarnings) {
        System.out.println(warning);
      }
    }

    try {
      // Apply specified refactorings to eliminate warnings
      RefactoringEngine refactoringEngine = new RefactoringEngine(refactoringNames);
      CompilationUnit refactoredCU = refactoringEngine.refactor(cu, originalWarnings);

      // Generate refactored source code
      String refactoredSource = refactoredCU.toString();

      // Write refactored code to a new file
      String refactoredFilePath = "Refactored" + new File(sourceFilePath).getName();
      writeStringToFile(refactoredFilePath, refactoredSource);

      // Run the verifier on the refactored code
      List<String> refactoredWarnings = runVerifier(refactoredFilePath);

      if (refactoredWarnings.isEmpty()) {
        System.out.println("Refactoring successful. No warnings in refactored code.");
        System.out.println("Refactored code written to " + refactoredFilePath);
      } else {
        System.out.println("Warnings still present after refactoring:");
        for (String warning : refactoredWarnings) {
          System.out.println(warning);
        }
      }
    } catch (JavaModelException e) {
      e.printStackTrace();
      System.err.println("Error during refactoring: " + e.getMessage());
      return;
    }
  }

  // Utility method to read a file into a string
  private static String readFileToString(String filePath) throws IOException {
    return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));
  }

  // Utility method to write a string to a file
  private static void writeStringToFile(String filePath, String content) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
    writer.write(content);
    writer.close();
  }

  // Method to parse source code into a CompilationUnit (AST)
  static CompilationUnit parse(String source) {
    ASTParser parser = ASTParser.newParser(AST.JLS17);
    Map<String, String> options = JavaCore.getOptions();
    options.put(JavaCore.COMPILER_SOURCE, "17");
    parser.setCompilerOptions(options);
    parser.setSource(source.toCharArray());
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(true);

    return (CompilationUnit) parser.createAST(null);
  }

  // Method to run the verifier (Checker Framework's Nullness Checker)
  static List<String> runVerifier(String sourceFilePath) throws IOException {
    List<String> warnings = new ArrayList<>();

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      System.out.println("No Java compiler available. Make sure to run with JDK, not JRE.");
      return warnings;
    }

    // Set up diagnostic listener to capture compiler output
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    // Prepare compilation units
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
    Iterable<? extends JavaFileObject> compilationUnits =
        fileManager.getJavaFileObjectsFromStrings(Arrays.asList(sourceFilePath));

    // Set up compilation options to include the Checker Framework
    List<String> optionList = new ArrayList<>();
    optionList.add("-processor");
    optionList.add("org.checkerframework.checker.nullness.NullnessChecker");
    optionList.add("-classpath");
    optionList.add(System.getProperty("java.class.path"));

    // Compile the file
    JavaCompiler.CompilationTask task =
        compiler.getTask(null, fileManager, diagnostics, optionList, null, compilationUnits);
    task.call();

    // Process diagnostics to collect warnings
    for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
      if (diagnostic.getKind() == Diagnostic.Kind.WARNING
          || diagnostic.getKind() == Diagnostic.Kind.ERROR) {
        String message = diagnostic.getMessage(null);
        warnings.add("Line " + diagnostic.getLineNumber() + ": " + message);
      }
    }

    fileManager.close();

    return warnings;
  }
}
