package ru.mail.polis.homework.analyzer;

public class TooLongAnalyzer implements TextAnalyzer {
    private final long maxLength;
    private final FilterType filterType = FilterType.TOO_LONG;

    public TooLongAnalyzer(long length) {
        this.maxLength = length;
    }


    @Override
    public boolean isNotValid(String text) {
        return text.length() > maxLength;
    }

    @Override
    public FilterType getFilterType() {
        return filterType;
    }
}
