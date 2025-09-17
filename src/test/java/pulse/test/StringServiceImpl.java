package pulse.test;

public final class StringServiceImpl implements StringService {
    @Override
    public String concat(String a, String b) {
        return a + ' ' + b;
    }
}
