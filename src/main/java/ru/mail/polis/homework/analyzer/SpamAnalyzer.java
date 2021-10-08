package ru.mail.polis.homework.analyzer;

public class SpamAnalyzer implements TextAnalyzer {
    private final FilterType filterType = FilterType.SPAM;
    private final String[] spam;

    public SpamAnalyzer(String[] spam) {
        this.spam = spam;
    }


    @Override
    public boolean isNotValid(String text) {
        for (String spamWord : spam) {
            if (text.contains(spamWord)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FilterType getFilterType() {
        return filterType;
    }
}
