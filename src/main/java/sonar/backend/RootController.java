package sonar.backend;

import sonar.backend.data.GameData;

import java.util.ArrayList;

public class RootController {

    private final ArrayList<Controller> controllers;
    private GameData data;

    public RootController(GameData data) {
        this.data = data;
        controllers = new ArrayList<>();
    }

    public void addController(Controller c) {
        controllers.add(c);
    }

    public void dispose() {
        for (Controller c : controllers) {
            c.dispose();
        }
    }
}
