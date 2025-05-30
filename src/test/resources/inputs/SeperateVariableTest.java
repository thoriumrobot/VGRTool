import org.checkerframework.checker.nullness.qual.NonNull;

public class SeperateVariableTest {
    public void printNonNull(@NonNull String str) {
        System.out.println(str);
    }

    public void test() {
        String str = "Hello World";

        Object refObject = str != null ? new Object() : null;

        if (refObject != null) {
            printNonNull(str);
        }
    }
}
