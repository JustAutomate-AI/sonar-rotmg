package sonar.ui;

/** The public door to {@link PerfProbe}, which stays package-private. */
public final class PerfHooks {
    private PerfHooks() {
    }

    public static void install() {
        PerfProbe.installIfAsked();
    }
}
