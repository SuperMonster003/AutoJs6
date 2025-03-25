package com.huaban.analysis.jieba;

import org.jetbrains.annotations.NotNull;

public class Pair<K> {
    public K key;
    public Double freq;

    public Pair(K key, double freq) {
        this.key = key;
        this.freq = freq;
    }

    @NotNull
    @Override
    public String toString() {
        return "Candidate [key=" + key + ", freq=" + freq + "]";
    }

}
