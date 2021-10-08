package ru.mail.polis.homework.analyzer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** class CustomAnalyzer
 *  Проверяет наличие пробела после запятой
 *  в Regex используется negative look forward
*/
public class CustomAnalyzer implements TextAnalyzer{
    private final FilterType filterType = FilterType.CUSTOM;
    private final Pattern pattern = Pattern.compile("(,)(?! )");

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
