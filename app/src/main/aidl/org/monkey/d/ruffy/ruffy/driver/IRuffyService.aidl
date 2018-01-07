// IRuffyService.aidl
package org.monkey.d.ruffy.ruffy.driver;

// Declare any non-default types here with import statements
import org.monkey.d.ruffy.ruffy.driver.IRTHandler;
import org.monkey.d.ruffy.ruffy.driver.ICmdHandler;

interface IRuffyService {

    void setHandler(IRTHandler handler);
    void setCmdHandler(ICmdHandler handler);

    /** Connect to the pump
    *
    * @return 0 if successful, -1 otherwise
    */
    int doRTConnect();

    /** Disconnect from the pump */
    void doRTDisconnect();

    // TODO what's the meaning of 'changed'?
    void rtSendKey(byte keyCode, boolean changed);
    void resetPairing();
    boolean isConnected();

    int doCmdConnect();

    void doCmdBolus(double bolus);
    void doCmdBolusState();
    void doCmdBolusCancel();

    void doCmdHistorie();

    void doCmdDisconnect();
}
