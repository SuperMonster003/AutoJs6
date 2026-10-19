package org.autojs.autojs.engine;

public abstract class JavaSubclassFixture {
    public final String constructed;
    protected JavaSubclassFixture(String seed) { constructed = decorate(seed); }
    public abstract String decorate(String value);
    public int sum(int a, int b) { return a + b; }
    protected long twice(long value) { return value * 2; }
    public final int fixed() { return 7; }
    public interface ParentInterface {
        String inherited();
        default String defaultName() { return "inherited-default"; }
    }
    public abstract static class InheritedBase implements ParentInterface {}
}
