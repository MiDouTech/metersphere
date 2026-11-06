package io.metersphere.agent.service;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parse saved HTML only; never resolve or fetch a URL. */
final class BugImageDescription {
    record Image(int occurrence, String fileId, String alt, String reason) { }
    record Result(List<Image> images, boolean complete) { }
    private static final Pattern PREVIEW = Pattern.compile("^(?:/api)?/bug/attachment/preview/md/([^/]+)/([^/]+)/(?:true|false)(?:[?].*)?$");

    static Result parse(String html, String project) {
        List<Image> result = new ArrayList<>();
        try {
            Parser parser = Parser.htmlParser().setTrackErrors(100);
            for (var element : Jsoup.parse(html == null ? "" : html, "", parser).select("img")) {
                String src = element.attr("src"), id = element.attr("fileid");
                String reason = null;
                var path = PREVIEW.matcher(src);
                if (src.startsWith("blob:") || src.startsWith("data:")) reason = "UNSAVED_IMAGE";
                else if (src.matches("(?is)^(?:[a-z][a-z0-9+.-]*:|//).*")) reason = "EXTERNAL_IMAGE";
                else if (path.matches() && path.group(1).equals(project)) {
                    if (!id.isEmpty() && !id.equals(path.group(2))) reason = "RESOURCE_UNAVAILABLE";
                    id = path.group(2);
                } else reason = "RESOURCE_UNAVAILABLE";
                if (id.isBlank() || !id.matches("[A-Za-z0-9_-]{1,128}")) reason = reason == null ? "RESOURCE_UNAVAILABLE" : reason;
                result.add(new Image(result.size() + 1, id, element.attr("alt"), reason));
            }
            return new Result(List.copyOf(result), parser.getErrors().isEmpty());
        } catch (RuntimeException failure) {
            return new Result(List.copyOf(result), false);
        }
    }
}
