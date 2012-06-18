package mn.uwvm.metannot.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic.Kind;

import mn.uwvm.metannot.annotation.TemplateParameter;

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
    
    public static void writeTemplateParameters(String template, String[] parameters, Map<String, String> params, Messager messager, String writerName, StringBuilder builder) {
        int pos = 0;
        
        Pattern templatePattern = Pattern.compile("@" + TemplateParameter.class.getSimpleName() + "\\(");
        Matcher matcher = templatePattern.matcher(template);
        int keyIndex = 0;
        while (true) {
            if (matcher.find(pos)) {
                String strKeepLhs = template.substring(
                        matcher.end(), template.indexOf(')', matcher.end()));
                boolean keepLhs = true;
                if (strKeepLhs.length() > 0) {
                    keepLhs = strKeepLhs.indexOf("true") > -1;
                }
                
                int start, end;
                int nextLf = template.indexOf('\n', matcher.end());
                String key = parameters[keyIndex ++];
                
                if (keepLhs) {
                    start = template.indexOf('=', nextLf + 1) + 2;
                    end = template.indexOf(';', nextLf + 1);
                } else {
                    start = matcher.start();
                    end = template.indexOf(';', nextLf + 1);
                }
                
                builder.append(
                        template.subSequence(
                                pos, start));
                if (!params.containsKey(key)) {
                    messager.printMessage(Kind.ERROR,
                            "Template parameter " + key + " was given, but there are no such key in the params passed to injectionClassWriter!");
                    return;
                }
                builder.append(params.get(key));
                pos = end;
            } else {
                break;
            }
        }
        
        builder.append(template.substring(pos));
    }
}
