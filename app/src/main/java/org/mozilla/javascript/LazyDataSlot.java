package org.mozilla.javascript;

import java.util.function.Supplier;

/**
 * A data property whose value is built on its first read (AutoJs6 script module layer, docs/augment/ROADMAP.md T3).
 *
 * <p>The property is a plain data property from the start: its attributes (enumerable / writable / configurable) are
 * the final ones, `hasOwnProperty`, `in` and `Object.keys` see it without building the value, and its descriptor
 * reports the built value instead of an accessor. Reading the value or the descriptor builds the value once and keeps
 * it in the slot, so later reads cost the same as an ordinary data property. Writing a writable property before the
 * first read replaces the pending value, and the builder is never called.
 *
 * <p>Rhino's own {@link LazyLoadSlot} is not used because its descriptor exposes the pending {@link LazilyLoadedCtor}
 * and its initializer requires a {@link VarScope} target. This class lives in Rhino's package (like {@link
 * ContextWrapper}) because slots and the slot map are package-private.
 *
 * <p>zh-CN: 首次读取时才构建值的数据属性 (脚本模块层, ROADMAP T3). 属性从一开始就是数据属性, 特性即最终特性; hasOwnProperty, in 与
 * Object.keys 不会触发构建, 描述符返回构建后的值而非访问器. 读取值或描述符时构建一次并保存在槽内, 之后与普通数据属性开销相同. 可写属性在首次读取前被赋值时,
 * 待构建的值被替换, 不再调用构建函数. 不用 Rhino 自带的 LazyLoadSlot: 它的描述符会暴露尚未初始化的 LazilyLoadedCtor, 且初始化要求目标为 VarScope.
 * 槽与槽表是包内可见的, 因此本类放在 Rhino 的包中 (与 ContextWrapper 相同).
 */
public final class LazyDataSlot extends StandardSlot<Scriptable> {

    private static final long serialVersionUID = 6195023412763390081L;

    private enum Pending {
        INSTANCE
    }

    private transient Supplier<?> builder;
    private transient boolean building;

    private LazyDataSlot(Object name, int index, int attributes, Supplier<?> builder) {
        super(name, index, attributes);
        this.value = Pending.INSTANCE;
        this.builder = builder;
    }

    private LazyDataSlot(LazyDataSlot oldSlot) {
        super(oldSlot);
        this.builder = oldSlot.builder;
    }

    /**
     * Defines (or redefines) property [name] of [target] as a lazy data property with [attributes]. The [builder] runs
     * on the first read of the value or the descriptor, at most once per slot; it must be safe to call again from a
     * copy of the slot (the slot map may copy slots while it grows), which the script module mounter guarantees by
     * memoizing the module object.
     */
    public static void define(ScriptableObject target, String name, int attributes, Supplier<?> builder) {
        target.checkNotSealed(name, 0);
        target.getMap().compute(target, name, 0, (key, index, existing, map, owner) -> new LazyDataSlot(key, index, attributes, builder));
    }

    /** True while property [name] of [target] is a lazy data property whose value has not been built yet. */
    public static boolean isPending(ScriptableObject target, String name) {
        Slot<Scriptable> slot = target.getMap().query(name, 0);
        return slot instanceof LazyDataSlot && slot.value == Pending.INSTANCE;
    }

    @Override
    LazyDataSlot copySlot() {
        LazyDataSlot newSlot = new LazyDataSlot(this);
        newSlot.next = null;
        newSlot.orderedNext = null;
        return newSlot;
    }

    @Override
    public Object getValue(Scriptable start) {
        Object current = value;
        return current == Pending.INSTANCE ? build() : current;
    }

    @Override
    ScriptableObject.DescriptorInfo getPropertyDescriptor(Context cx, Scriptable scope) {
        return ScriptableObject.buildDataDescriptor(getValue(scope), getAttributes());
    }

    private synchronized Object build() {
        if (value != Pending.INSTANCE) return value;
        if (building) throw new IllegalStateException("Recursive initialization of " + getName());
        Supplier<?> current = builder;
        if (current == null) {
            // Deserialized before the first read: the builder did not survive.
            value = Undefined.instance;
            return value;
        }
        building = true;
        try {
            Object built = current.get();
            value = built;
            builder = null;
            return built;
        } finally {
            building = false;
        }
    }
}
