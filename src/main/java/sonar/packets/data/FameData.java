package sonar.packets.data;

import sonar.packets.reader.BufferReader;

import java.io.Serializable;

/**
 * Death fame data storing name and fame given on death
 */
public class FameData implements Serializable {

    /**
     * Name of the achievement.
     */
    public String achievement;
    /**
     * Fame level
     */
    public int fameLevel;
    /**
     * Fame
     */
    public int fameAdded;

    public FameData deserialize(BufferReader buffer) {
        achievement = buffer.readString();
        fameLevel = buffer.readCompressedInt();
        fameAdded = buffer.readCompressedInt();
        return this;
    }

    @Override
    public String toString() {
        return "FameData{" +
                "\n   achievement=" + achievement +
                "\n   fameLevel=" + fameLevel +
                "\n   fameAdded=" + fameAdded;
    }
}
