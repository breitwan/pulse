package pulse.test;

public final class MathServiceImpl implements MathService {
    @Override
    public int sum(int a, int b) {
        return a + b;
    }

    @Override
    public int div(DivideRequest request) {
        return request.dividend() / request.divisor();
    }

    @Override
    public int[] unite(int a, int b) {
        return new int[]{a, b};
    }
}
