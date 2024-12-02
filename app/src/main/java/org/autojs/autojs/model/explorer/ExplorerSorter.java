package org.autojs.autojs.model.explorer;

import org.apache.commons.lang3.StringUtils;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ExplorerSorter {

    private static final Collator collator = Collator.getInstance();

    // @Hint by JetBrains AI Assistant on Oct 26, 2024.
    //  ! Comparator for sorting ExplorerItem by name.
    //  ! This comparator prioritizes ASCII printable characters when comparing names.
    //  ! If both names start with ASCII printable characters, it ignores case during the comparison for better performance.
    //  ! If one of the names starts with an ASCII printable character and the other does not,
    //  ! the one with the ASCII printable character is considered smaller.
    //  ! If neither name starts with an ASCII printable character, the comparator falls back
    //  ! to using Collator for internationalized comparison.
    //  !
    //  ! zh-CN (translated by Jetbrains AI Assistant on Oct 26, 2024):
    //  !
    //  ! 按名称对 ExplorerItem 进行排序的比较器.
    //  ! 当比较名称时, 此比较器优先考虑 ASCII 可打印字符.
    //  ! 如果两个名称都以 ASCII 可打印字符开头, 则在比较时忽略大小写, 以提高性能.
    //  ! 如果一个名称以 ASCII 可打印字符开头, 而另一个名称不是, 则以 ASCII 可打印字符开头的名称较小.
    //  ! 如果名称都不以 ASCII 可打印字符开头, 则比较器回退到使用 Collator 进行国际化比较.
    //  !
    //  # public static final Comparator<ExplorerItem> NAME = (o1, o2) -> collator.compare(o1.getName(), o2.getName());
    public static final Comparator<ExplorerItem> NAME = (o1, o2) -> {
        String name1 = o1.getName();
        String name2 = o2.getName();

        boolean isName1Ascii = StringUtils.isAsciiPrintable(name1.substring(0, 1));
        boolean isName2Ascii = StringUtils.isAsciiPrintable(name2.substring(0, 1));

        if (isName1Ascii && isName2Ascii) return name1.compareToIgnoreCase(name2);
        if (isName1Ascii) return -1;
        if (isName2Ascii) return 1;
        return collator.compare(name1, name2);
    };

    public static final Comparator<ExplorerItem> DATE = Comparator.comparingLong(ExplorerItem::lastModified);

    public static final Comparator<ExplorerItem> TYPE = Comparator.comparing(ExplorerItem::getType);

    public static final Comparator<ExplorerItem> SIZE = Comparator.comparingLong(ExplorerItem::getSize);

    public static void sort(ExplorerItem[] items, Comparator<ExplorerItem> comparator, boolean ascending) {
        if (ascending) {
            Arrays.sort(items, comparator);
        } else {
            Arrays.sort(items, (o1, o2) -> comparator.compare(o2, o1));
        }
    }

    public static void sort(ExplorerItem[] items, Comparator<ExplorerItem> comparator) {
        sort(items, comparator, true);
    }

    public static void sort(List<? extends ExplorerItem> items, Comparator<ExplorerItem> comparator, boolean ascending) {
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
