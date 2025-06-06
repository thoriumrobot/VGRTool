public class NestedNullTest {
    String str = "Hello World";

    public boolean checkNull() {
        return str != null;
    }

    public void test() {

        if (checkNull()) {
            ;
        }

        if (!checkNull()) {
            ;
        }
    }
}
