package fan.x0.focus.data.device.display;

import android.os.ParcelFileDescriptor;

interface IFocusDisplay {
    String start(int width, int height, int density) = 0;
    String stop() = 1;
    String status() = 2;
    ParcelFileDescriptor capture(String format, int quality, int maxWidth) = 3;
    boolean tap(int displayId, int x, int y, int durationMs) = 4;
    boolean swipe(int displayId, int startX, int startY, int endX, int endY, int durationMs) = 5;
    boolean key(int displayId, int keyCode) = 6;
    boolean text(int displayId, String text) = 7;
    boolean launch(int displayId, String packageName, String activity) = 8;
    void destroy() = 16777114;
}
