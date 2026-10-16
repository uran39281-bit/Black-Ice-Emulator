package com.blackice.bc1.editor;

import java.util.Locale;

/** Metadata-only media browsing; never changes clip order or the edit graph. */
public final class MediaFilter {
    private MediaFilter() {}
    public enum Kind { ALL, VIDEO, AUDIO, IMAGE }

    public static boolean matches(EditProject.Clip clip, Kind kind, String query) {
        boolean audio=clip.track>=4;
        if(kind==Kind.AUDIO&&!audio || kind==Kind.IMAGE&&(audio||!clip.image)
                || kind==Kind.VIDEO&&(audio||clip.image))return false;
        String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        return search.isEmpty() || (clip.name!=null&&clip.name.toLowerCase(Locale.ROOT).contains(search));
    }
}
