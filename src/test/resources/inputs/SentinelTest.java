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
