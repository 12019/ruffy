// IRTHandler.aidl
package org.monkey.d.ruffy.ruffy.driver;

// Declare any non-default types here with import statements

import org.monkey.d.ruffy.ruffy.driver.display.Menu;

interface ICmdHandler {
    void log(String message);
    void fail(String message);

    void requestBluetooth();

    void cmdStopped();
    void cmdStarted();

    void bolusStarted(boolean success);
    void bolusCancled(boolean success);
    void bolusStatus(int status,double remaining);

    void reportBolusDelivered(boolean manual, double infused, long ts, long eventCnt, int eventId);
    void reportBolusRequested(boolean manual, double infused, long ts, long eventCnt, int eventId);
}
