public class NestedNullTest {
    String str = "Hello World";

    public boolean checkNull() {
        return str != null;
    }

    public void test() {

        if (str != null) {
            ;
        }

        if (str == null) {
            ;
        }
    }
}
