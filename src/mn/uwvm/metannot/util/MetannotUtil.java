package mn.uwvm.metannot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MetannotUtil {
    public static final String replaceTokenInString(String source, String oldToken, String newToken) {
        Pattern pattern = Pattern.compile("(\\W)" + oldToken + "(\\W)");
        Matcher matcher = pattern.matcher(source);
        
        int pos = 0;
        StringBuilder builder = new StringBuilder();
        while (matcher.find(pos)) {
            builder.append(source.subSequence(pos, matcher.start()));
            builder.append(matcher.group(1));
            builder.append(newToken);
            builder.append(matcher.group(2));
            pos = matcher.end();
        }
        builder.append(source.substring(pos));
        return builder.toString();
    }
}
