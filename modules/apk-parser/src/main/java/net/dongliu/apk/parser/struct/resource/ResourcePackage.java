package net.dongliu.apk.parser.struct.resource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.struct.StringPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource packge.
 *
 * @author dongliu
 */
public class ResourcePackage {
    // the packageName
    private String name;
    private short id;
    /**
     * contains the names of the types of the Resources defined in the ResourcePackage
     */
    private StringPool typeStringPool;
    /**
     * contains the names (keys) of the Resources defined in the ResourcePackage.
     */
    private StringPool keyStringPool;

    public ResourcePackage(final @NonNull PackageHeader header) {
        this.name = header.getName();
        this.id = (short) header.getId();
    }

    private Map<Short, TypeSpec> typeSpecMap = new HashMap<>();

    private Map<Short, List<Type>> typesMap = new HashMap<>();

    public void addTypeSpec(final @NonNull TypeSpec typeSpec) {
        this.typeSpecMap.put(typeSpec.id, typeSpec);
    }

    @Nullable
    public TypeSpec getTypeSpec(final short id) {
        return this.typeSpecMap.get(id);
    }

    public void addType(final Type type) {
        List<Type> types = this.typesMap.get(type.id);
        if (types == null) {
            types = new ArrayList<>();
            this.typesMap.put(type.id, types);
        }
        types.add(type);
    }

    @Nullable
    public List<Type> getTypes(final short id) {
        return this.typesMap.get(id);
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public short getId() {
        return this.id;
    }

    public void setId(final short id) {
        this.id = id;
    }

    public StringPool getTypeStringPool() {
        return this.typeStringPool;
    }

    public void setTypeStringPool(final @NonNull StringPool typeStringPool) {
        this.typeStringPool = typeStringPool;
    }

    public StringPool getKeyStringPool() {
        return this.keyStringPool;
    }

    public void setKeyStringPool(final @NonNull StringPool keyStringPool) {
        this.keyStringPool = keyStringPool;
    }

    public Map<Short, TypeSpec> getTypeSpecMap() {
        return this.typeSpecMap;
    }

    public void setTypeSpecMap(final Map<Short, TypeSpec> typeSpecMap) {
        this.typeSpecMap = typeSpecMap;
    }

    public Map<Short, List<Type>> getTypesMap() {
        return this.typesMap;
    }

    public void setTypesMap(final Map<Short, List<Type>> typesMap) {
        this.typesMap = typesMap;
    }
}
