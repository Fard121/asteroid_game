package dk.sdu.mmmi.cbse.common.data;

public class GameKeys {

    private static boolean[] keys;
    private static boolean[] pkeys;

    private static final int NUM_KEYS = 13;
    public static final int UP = 0;
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int SPACE = 3;
    public static final int RESTART = 4;
    public static final int PAUSE = 5;
    public static final int MUTE = 6;
    public static final int HELP = 7;
    public static final int QUIT = 8;
    public static final int DOWN = 9;
    public static final int TOGGLE_PLAYER = 10;
    public static final int TOGGLE_ENEMY = 11;
    public static final int TOGGLE_WEAPON = 12;

    public GameKeys() {
        keys = new boolean[NUM_KEYS];
        pkeys = new boolean[NUM_KEYS];
    }

    public void update() {
        for (int i = 0; i < NUM_KEYS; i++) {
            pkeys[i] = keys[i];
        }
    }

    public void setKey(int k, boolean b) {
        keys[k] = b;
    }

    public boolean isDown(int k) {
        return keys[k];
    }

    public boolean isPressed(int k) {
        return keys[k] && !pkeys[k];
    }

}
