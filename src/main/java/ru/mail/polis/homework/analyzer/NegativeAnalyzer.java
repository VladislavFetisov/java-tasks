package ru.mail.polis.homework.analyzer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3) фильтр для текстов с плохими эмоциями. (в тексте не должно быть таких смайлов:
 * "=(", ":(", ":|"
 */
public class NegativeAnalyzer implements TextAnalyzer {
    private final FilterType filterType = FilterType.NEGATIVE_TEXT;
    private final Pattern pattern = Pattern.compile("(=\\()|(:\\()|(:\\|)");

    @Override
    public boolean isNotValid(String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }

    @Override
    public FilterType getFilterType() {
        return filterType;
    }
}
