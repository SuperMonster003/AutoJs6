package org.autojs.rhino.fixture;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Superclass of a script class, loaded with runtime.loadJar: class file version 52 with a lambda, a method reference and
 * default and static interface methods, which D8 lowers for the API level of the device. Regenerate the Base64 in
 * app/src/androidTest/assets/rhino/class-syntax-cases.js with tools/rhino/build_class_fixtures.py.
 */
public class RhinoClassJava8Fixture implements RhinoClassJava8FixtureNamed {
    private final String name;

    public RhinoClassJava8Fixture(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    public String describe() {
        Function<String, String> wrap = RhinoClassJava8FixtureNamed::wrap;
        return wrap.apply(greet());
    }

    protected String guarded() {
        Supplier<String> text = () -> "guarded " + name;
        return text.get();
    }
}
