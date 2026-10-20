package sonar.ui;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Drives the animated values of one component. A value eases toward its
 * target every frame, and the timer only runs while something is moving.
 */
final class Motion {

    private final JComponent owner;
    private final ArrayList<Value> values = new ArrayList<>();
    /** Stepped by the shared {@link AnimationClock}, at the display's rate, only while moving. */
    private final AnimationClock.Animated stepper = this::frame;
    private boolean running;

    Motion(JComponent owner) {
        this.owner = owner;
    }

    Value value(double start) {
        return value(start, 12);
    }

    /**
     * @param speed Higher settles faster; 12 takes roughly a third of a second.
     */
    Value value(double start, double speed) {
        Value v = new Value(start, speed);
        values.add(v);
        return v;
    }

    void forget(Value v) {
        values.remove(v);
    }

    private boolean frame(double dt) {
        boolean moving = false;
        for (int i = 0; i < values.size(); i++) {
            moving |= values.get(i).step(dt);
        }
        owner.repaint();
        if (!moving) running = false;
        if (moving) PerfProbe.stillMoving(this, owner, values);
        return moving;
    }

    private void wake() {
        if (!running) {
            running = true;
            AnimationClock.start(stepper);
        }
    }

    final class Value {
        double targetForProbe() {
            return target;
        }

        private double now, target;
        private final double speed;

        private Value(double start, double speed) {
            this.now = start;
            this.target = start;
            this.speed = speed;
        }

        double get() {
            return now;
        }

        double target() {
            return target;
        }

        void to(double t) {
            if (t == target) return;
            target = t;
            wake();
        }

        void jump(double t) {
            now = t;
            target = t;
        }

        private boolean step(double dt) {
            double diff = target - now;
            if (Math.abs(diff) < 0.0005 * Math.max(1, Math.abs(target))) {
                now = target;
                return false;
            }
            now += diff * (1 - Math.exp(-dt * speed));
            return true;
        }
    }
}
