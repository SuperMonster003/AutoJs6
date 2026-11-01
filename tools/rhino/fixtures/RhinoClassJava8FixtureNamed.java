package org.autojs.rhino.fixture;

/** The default and static interface methods of {@link RhinoClassJava8Fixture}. */
public interface RhinoClassJava8FixtureNamed {
    String name();

    default String greet() {
        return "hello " + name();
    }

    static String wrap(String text) {
        return "[" + text + "]";
    }
}
