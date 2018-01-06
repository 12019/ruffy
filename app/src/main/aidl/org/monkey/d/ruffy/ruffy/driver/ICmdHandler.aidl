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

}
