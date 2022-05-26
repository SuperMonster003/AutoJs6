package com.stardust.util;

import com.stardust.pio.PFiles;

import java.io.File;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Stardust on 2017/3/31.
 */
public class FileSorter {

    public static final Comparator<File> NAME = new Comparator<>() {
        final Collator collator = Collator.getInstance();

        @Override
        public int compare(File o1, File o2) {
            if (o1.isDirectory() != o2.isDirectory())
                return o1.isDirectory() ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            return -collator.compare(o1.getName(), o2.getName());
        }
    };

    public static final Comparator<File> DATE = Comparator.comparingLong(File::lastModified);

    public static final Comparator<File> TYPE = (o1, o2) -> -PFiles.getExtension(o1.getName()).compareTo(PFiles.getExtension(o2.getName()));

    public static final Comparator<File> SIZE = (o1, o2) -> Long.compare(o2.length(), o1.length());

    public static void sort(File[] files, final Comparator<File> comparator, boolean ascending) {
        if (ascending) {
            Arrays.sort(files, comparator);
        } else {
            Arrays.sort(files, (o1, o2) -> comparator.compare(o2, o1));
        }
    }

    public static void sort(File[] files, Comparator<File> comparator) {
        sort(files, comparator, true);
    }

    public static void sort(List<? extends File> files, final Comparator<File> comparator, boolean ascending) {
        if (ascending) {
            files.sort(comparator);
        } else {
            files.sort((Comparator<File>) (o1, o2) -> comparator.compare(o2, o1));
        }
    }

    public static void sort(List<? extends File> files, Comparator<File> comparator) {
        sort(files, comparator, true);
    }

}
