package com.stardust.util;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BiMaps {

    public static <K, V> BiMap<K, V> make(Map<K, V> keyToValue, Map<V, K> valueToKey) {
        return new BiMapImpl<>(keyToValue, valueToKey);
    }

    public static <K, V> BiMapBuilder<K, V> newBuilder() {
        return new BiMapBuilder<>();
    }

    public static class BiMapBuilder<K, V> {

        private final BiMap<K, V> mBiMap = make(new HashMap<>(), new HashMap<>());

        public BiMapBuilder<K, V> put(K key, V value) {
            mBiMap.put(key, value);
            return this;
        }

        public BiMap<K, V> build() {
            return mBiMap;
        }

    }

    private static class BiMapImpl<K, V> implements BiMap<K, V> {

        private final Map<K, V> mKVMap;
        private final Map<V, K> mVKMap;

        private BiMapImpl(Map<K, V> kvMap, Map<V, K> vkMap) {
            mKVMap = kvMap;
            mVKMap = vkMap;
        }

        @Override
        public int size() {
            return mKVMap.size();
        }

        @Override
        public boolean isEmpty() {
            return mKVMap.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return mKVMap.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return mVKMap.containsKey(value);
        }

        @Override
        public V get(Object key) {
            return mKVMap.get(key);
        }

        @Override
        public V put(K key, V value) {
            V put = mKVMap.put(key, value);
            mVKMap.put(value, key);
            return put;
        }

        @Override
        public V remove(Object key) {
            V remove = mKVMap.remove(key);
            if (remove != null) {
                mVKMap.remove(key);
            }
            return remove;
        }

        @Override
        public void putAll(@NonNull Map<? extends K, ? extends V> m) {
            mKVMap.putAll(m);
        }

        @Override
        public void clear() {
            mKVMap.clear();
            mVKMap.clear();
        }

        @NonNull
        @Override
        public Set<K> keySet() {
            return mKVMap.keySet();
        }

        @Override
        public K getKey(V value) {
            return mVKMap.get(value);
        }

        @Override
        public Set<V> valueSet() {
            return mVKMap.keySet();
        }

        @Override
        public V getOr(K key, V def) {
            V v = get(key);
            return v == null ? def : v;
        }

        @Override
        public K getKeyOr(V value, K def) {
            K key = getKey(value);
            return key == null ? def : key;
        }

        @NonNull
        @Override
        public Collection<V> values() {
            return mKVMap.values();
        }

        @NonNull
        @Override
        public Set<Entry<K, V>> entrySet() {
            return mKVMap.entrySet();
        }

        @Override
        public boolean equals(Object o) {
            return mKVMap.equals(o);
        }

        @Override
        public int hashCode() {
            return mKVMap.hashCode();
        }

        @Override
        public V getOrDefault(Object key, V defaultValue) {
            return mKVMap.getOrDefault(key, defaultValue);
        }

        @Override
        public void forEach(@NonNull BiConsumer<? super K, ? super V> action) {
            mKVMap.forEach(action);
        }

        @Override
        public void replaceAll(@NonNull BiFunction<? super K, ? super V, ? extends V> function) {
            mKVMap.replaceAll(function);
        }

        @Override
        public V putIfAbsent(K key, V value) {
            return mKVMap.putIfAbsent(key, value);
        }

        @Override
        public boolean remove(Object key, Object value) {
            return mKVMap.remove(key, value);
        }

        @Override
        public boolean replace(K key, V oldValue, V newValue) {
            return mKVMap.replace(key, oldValue, newValue);
        }

        @Override
        public V replace(K key, V value) {
            return mKVMap.replace(key, value);
        }

        @Override
        public V computeIfAbsent(K key, @NonNull Function<? super K, ? extends V> mappingFunction) {
            return mKVMap.computeIfAbsent(key, mappingFunction);
        }

        @Override
        public V computeIfPresent(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            return mKVMap.computeIfPresent(key, remappingFunction);
        }

        @Override
        public V compute(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            return mKVMap.compute(key, remappingFunction);
        }

        @Override
        public V merge(K key, @NonNull V value, @NonNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
            return mKVMap.merge(key, value, remappingFunction);
        }
    }
}
