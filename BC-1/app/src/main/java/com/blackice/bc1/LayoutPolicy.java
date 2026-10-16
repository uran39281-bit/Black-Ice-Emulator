package com.blackice.bc1;

/** Chooses only the WORKSPACE chrome, never the video composition or output aspect. */
public final class LayoutPolicy {
    public enum Preference { AUTO, PHONE, WIDE }
    public enum Workspace { PHONE, WIDE }
    private LayoutPolicy() { }

    public static Workspace resolve(Preference preference, int widthDp, int heightDp, int smallestWidthDp,
                                    boolean activityOnExternalDisplay) {
        // A narrow phone window cannot safely accommodate the full three-column UI,
        // even if the user has explicitly requested WIDE. Keep that preference for
        // when they rotate, expand the window, or connect an external monitor.
        boolean canShowWide = widthDp >= 680 && widthDp > heightDp * 0.85;
        if (preference == Preference.PHONE) return Workspace.PHONE;
        if (preference == Preference.WIDE) return canShowWide ? Workspace.WIDE : Workspace.PHONE;
        // Rotation alone must not turn a normal phone into "DeX".
        boolean tabletOrDesktop = canShowWide && (activityOnExternalDisplay
                || smallestWidthDp >= 600
                || (widthDp >= 1100 && heightDp >= 600));
        return tabletOrDesktop ? Workspace.WIDE : Workspace.PHONE;
    }
}
