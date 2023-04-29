package org.autojs.autojs.model.explorer;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ExplorerSorter {

    private static final Collator collator = Collator.getInstance();

    public static final Comparator<ExplorerItem> NAME = (o1, o2) -> collator.compare(o1.getName(), o2.getName());

    public static final Comparator<ExplorerItem> DATE = Comparator.comparingLong(ExplorerItem::lastModified);

    public static final Comparator<ExplorerItem> TYPE = Comparator.comparing(ExplorerItem::getType);

    public static final Comparator<ExplorerItem> SIZE = Comparator.comparingLong(ExplorerItem::getSize);

    public static void sort(ExplorerItem[] items, final Comparator<ExplorerItem> comparator, boolean ascending) {
        if (ascending) {
            Arrays.sort(items, comparator);
        } else {
            Arrays.sort(items, (o1, o2) -> comparator.compare(o2, o1));
        }
    }

    public static void sort(ExplorerItem[] items, Comparator<ExplorerItem> comparator) {
        sort(items, comparator, true);
    }

    public static void sort(List<? extends ExplorerItem> items, final Comparator<ExplorerItem> comparator, boolean ascending) {
        if (ascending) {
            items.sort(comparator);
        } else {
            items.sort((Comparator<ExplorerItem>) (o1, o2) -> comparator.compare(o2, o1));
        }
    }

    public static void sort(List<? extends ExplorerItem> items, Comparator<ExplorerItem> comparator) {
        sort(items, comparator, true);
    }
}
