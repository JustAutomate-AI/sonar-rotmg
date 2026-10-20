package sonar.version;
/**
 * Don't edit this class. Gradle writes it from build.gradle on every build.
 */
public class Version {

    /**
     * The app's version, the one shown in the left rail and used for release names.
     * Comes from Updater.CURRENT, which is where it is raised.
     */
    public static final String VERSION = "2.54.1";

    /**
     * The asset-extraction format, which is NOT the app's version and does not move
     * with it. It forms part of the key deciding whether an install re-extracts the
     * game's assets, so raising it costs every install one re-extraction. Raise it
     * only when what gets extracted actually changes.
     */
    public static final String ASSET_FORMAT = "v1.9.2";
}

