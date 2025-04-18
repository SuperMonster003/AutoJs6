package net.dongliu.apk.parser.utils;

/**
 * @author Liu Dong {@literal <dongliu@live.cn>}
 */
public class Pair<K, V> {
    private K left;
    private V right;

    public Pair() {
    }

    public Pair(final K left, final V right) {
        this.left = left;
        this.right = right;
    }

    public K getLeft() {
        return this.left;
    }

    public void setLeft(final K left) {
        this.left = left;
    }

    public V getRight() {
        return this.right;
    }

    public void setRight(final V right) {
        this.right = right;
    }
}
