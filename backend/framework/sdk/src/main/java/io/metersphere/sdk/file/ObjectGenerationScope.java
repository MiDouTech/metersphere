package io.metersphere.sdk.file;

/** Keep image-generation coordination out of unrelated plugin/system-local storage. */
final class ObjectGenerationScope {
    private static final java.util.regex.Pattern MANAGED=java.util.regex.Pattern.compile("(?:^|/)project/[^/]+/(?:file-management/.+|bug/[^/]+/.+)$",java.util.regex.Pattern.CASE_INSENSITIVE);
    static boolean managed(String object){return MANAGED.matcher(object.replace('\\','/')).find();}
}
