package br.com.liberdadefamilia.markets.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** How alike two product names are: Jaccard over the words of the normalized names, single letters left out. */
public final class NameSimilarity {

    private NameSimilarity() {}

    /** 0 (no word in common) to 1 (the same words). A name with no words at all scores 0. */
    public static double score(String a, String b) {
        return score(words(a), words(b));
    }

    /** The same score over word sets already made by {@link #words}, so a caller comparing many pairs normalizes once. */
    public static double score(Set<String> wordsA, Set<String> wordsB) {
        if (wordsA.isEmpty() || wordsB.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(wordsA);
        common.retainAll(wordsB);
        int union = wordsA.size() + wordsB.size() - common.size();
        return (double) common.size() / union;
    }

    /** The words of the normalized name, without the one-letter ones. */
    public static Set<String> words(String name) {
        Set<String> words = new HashSet<>(Arrays.asList(TextNorm.normalize(name).split(" ")));
        words.removeIf(word -> word.codePointCount(0, word.length()) < 2);
        return words;
    }
}
