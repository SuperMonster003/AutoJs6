package org.autojs.rhino.fixture;

/**
 * Superclass of a script class, loaded with runtime.loadDex. Regenerate the Base64 in
 * app/src/androidTest/assets/rhino/class-syntax-cases.js with tools/rhino/build_class_fixtures.py.
 */
public class RhinoClassDexFixture {
    private final String name;

    public RhinoClassDexFixture(String name) {
        this.name = name;
    }

    public String greet() {
        return "hello " + name;
    }

    public String describe() {
        return "[" + greet() + "]";
    }

    protected String guarded() {
        return "guarded " + name;
    }
}
