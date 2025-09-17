package pulse.util;

public class DefiningClassLoader extends ClassLoader {
    public Class<?> define(String name, byte[] b, int off, int len) {
        return super.defineClass(name, b, off, len, null);
    }
}
