import org.checkerframework.checker.nullness.qual.NonNull;

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
