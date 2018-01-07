package org.monkey.d.ruffy.ruffy.driver;

import java.nio.ByteBuffer;

/**
 * Callbacks for the Application-Layer
 */
public interface AppHandler {
    void log(String s);

    void connected();

    void rtModeActivated();

    void cmdModeActivated();

    void rtModeDeactivated();

    void cmdModeDeactivated();

    void modeDeactivated();

    void addDisplayFrame(ByteBuffer b);

    void modeError();

    void sequenceError();

    void error(short error, String desc);

    void cmdBolusStarted(boolean success);

    void cmdBolusState(BolusState notDelivering, double remaining);

    void cmdBolusCanceled(boolean b);

    void reportBolusDelivered(boolean manual, double infused, long ts, long eventCnt, short eventId);

    void reportBolusRequest(boolean manual, double requested, long ts, long eventCnt, short eventId);

    void doConfirmHistorie();

    void doReadHistorie();
}
