package org.monkey.d.ruffy.ruffy.driver;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.monkey.d.ruffy.ruffy.driver.display.DisplayParser;
import org.monkey.d.ruffy.ruffy.driver.display.DisplayParserHandler;
import org.monkey.d.ruffy.ruffy.driver.display.Menu;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by fishermen21 on 25.05.17.
 */

public class Ruffy extends Service {

    private long lastCmdMessageSent;

    public static class Key {
        public static byte NO_KEY				=(byte)0x00;
        public static byte MENU					=(byte)0x03;
        public static byte CHECK				=(byte)0x0C;
        public static byte UP					=(byte)0x30;
        public static byte DOWN					=(byte)0xC0;
    }

    private IRTHandler rtHandler = null;
    private BTConnection btConn;
    private PumpData pumpData;

    private boolean rtModeRunning = false;
    private long lastRtMessageSent = 0;

    private final Object rtSequenceSemaphore = new Object();
    private short rtSequence = 0;

    private int modeErrorCount = 0;
    private int step = 0;

    private boolean synRun=false;//With set to false, write process is started at first time
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool( 3 );

    private Display display;

    private ICmdHandler cmdHandler;

    private boolean cmdModeRunning;
    private final IRuffyService.Stub serviceBinder = new IRuffyService.Stub(){

        @Override
        public void setHandler(IRTHandler handler) throws RemoteException {
            Ruffy.this.rtHandler = handler;
        }

        @Override
        public void setCmdHandler(ICmdHandler handler) throws RemoteException {
            Ruffy.this.cmdHandler = handler;
        }

        @Override
        public int doRTConnect() throws RemoteException {
            if(isConnected() && rtModeRunning)
            {
                rtHandler.rtStarted();
                return 0;
            }
            step= 0;
            if(Ruffy.this.rtHandler==null)
            {
                throw new IllegalStateException("XXX");

//                return -2;//FIXME make errors
            }
            if(!isConnected()) {
                if (pumpData == null) {
                    pumpData = PumpData.loadPump(Ruffy.this, rtHandler);
                }
                if (pumpData != null) {
                    inShutDown=false;
                    btConn = new BTConnection(rtBTHandler);
                    rtModeRunning = true;
                    btConn.connect(pumpData, 10);
                    return 0;
                }
            } else {
                inShutDown=false;
                rtModeRunning = true;
                cmdModeRunning = false;
                Application.sendAppCommand(Application.Command.COMMAND_DEACTIVATE,btConn);
            }
            return -1;
        }

        public void doRTDisconnect()
        {
            step = 200;
            stopRT();
        }

        public void rtSendKey(byte keyCode, boolean changed)
        {
            //FIXME
            lastRtMessageSent = System.currentTimeMillis();
            synchronized (rtSequenceSemaphore) {
                rtSequence = Application.rtSendKey(keyCode, changed, rtSequence, btConn);
            }
        }

        public void resetPairing()
        {
            SharedPreferences prefs = Ruffy.this.getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);
            prefs.edit().putBoolean("paired",false).apply();

            /*
            String bondedDeviceId = prefs.getString("device", null);
            if (bondedDeviceId != null) {
                BluetoothDevice boundedPump = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bondedDeviceId);
                // TODO I know!
                try {
                    Method removeBound = boundedPump.getClass().getMethod("removeBond", (Class<?>[]) null);
                    removeBound.invoke(boundedPump, (Object[]) null);
                } catch (ReflectiveOperationException e) {
                    // it's not going better here either
                }
            }
*/

            synRun=false;
            rtModeRunning =false;
        }

        public boolean isConnected() {
            return btConn != null && btConn.isConnected();
        }

        @Override
        public int doCmdConnect() throws RemoteException {
            if(isConnected() && cmdModeRunning)
            {
                cmdHandler.cmdStarted();
                return 0;
            }
            step= 0;
            if(Ruffy.this.cmdHandler==null)
            {
                throw new IllegalStateException("XXX");

//                return -2;//FIXME make errors
            }
            if(!isConnected()) {
                if (pumpData == null) {
                    pumpData = PumpData.loadPump(Ruffy.this, rtHandler);
                }
                if (pumpData != null) {
                    inShutDown=false;
                    btConn = new BTConnection(rtBTHandler);
                    cmdModeRunning = true;
                    btConn.connect(pumpData, 10);
                    return 0;
                }
            } else {
                cmdModeRunning = true;
                rtModeRunning=false;
                Application.sendAppCommand(Application.Command.RT_DEACTIVATE,btConn);
            }
            return -1;
        }
        @Override
        public void doCmdDisconnect() {
            step = 200;
            stopCmd();
        }

        @Override
        public void doCmdBolusState() {
            lastCmdMessageSent = System.currentTimeMillis();
            Application.doCmdBolusState(btConn);
        }

        @Override
        public void doCmdBolusCancel() throws RemoteException {
            lastCmdMessageSent = System.currentTimeMillis();
            Application.doCmdBolusCancel(btConn);
        }

        @Override
        public void doCmdBolus(double bolus) throws RemoteException {
            lastCmdMessageSent = System.currentTimeMillis();
            Application.doCmdBolus((int)(bolus*10),btConn);
        }

        @Override
        public void doCmdHistorie() throws RemoteException {
            Application.doCmdHistorie(btConn);
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private BTHandler rtBTHandler = new BTHandler() {
        @Override
        public void deviceConnected() {
            inShutDown=false;
            log("connected to pump");
            if(synRun==false && !inShutDown) {
                synRun = true;
                log("start synThread");
                scheduler.execute(synThread);
            }
        }

        @Override
        public void log(String s) {
            Ruffy.this.log(s);
            if(s.equals("got error in read") && step < 200 && !inShutDown)
            {
                synRun=false;
                btConn.connect(pumpData,4);
            }
            Log.v("RuffyService",s);
        }

        @Override
        public void fail(String s) {
            log("failed: "+s);
            synRun=false;
            if(step < 200)
                btConn.connect(pumpData,4);
            else
                Ruffy.this.fail(s);

            Log.e("RuffyService",s);
        }

        @Override
        public void deviceFound(BluetoothDevice bd) {
            log("not be here!?!");
        }

        @Override
        public void handleRawData(byte[] buffer, int bytes) {
            log("got data from pump");
            synRun=false;
            step=0;
            Packet.handleRawData(buffer,bytes, rtPacketHandler);
        }

        @Override
        public void requestBlueTooth() {
            try {
                rtHandler.requestBluetooth();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private void stopRT()
    {
        step=200;
        rtModeRunning = false;
        Application.sendAppCommand(Application.Command.DEACTIVATE_ALL,btConn);
    }

    private void stopCmd()
    {
        step=200;
        cmdModeRunning = false;
        Application.sendAppCommand(Application.Command.DEACTIVATE_ALL,btConn);
    }

    private void startRT() {
        log("starting RT keepAlive");
        new Thread(){
            @Override
            public void run() {
                rtModeRunning = true;
                rtSequence = 0;
                lastRtMessageSent = System.currentTimeMillis();
                rtModeRunning = true;
                try {
                    display = new Display(new DisplayUpdater() {
                        @Override
                        public void clear() {
                            try {
                                rtHandler.rtClearDisplay();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void update(byte[] quarter, int which) {
                            try {
                                rtHandler.rtUpdateDisplay(quarter,which);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    display.setCompletDisplayHandler(new CompleteDisplayHandler() {
                        @Override
                        public void handleCompleteFrame(byte[][] pixels) {
                            DisplayParser.findMenu(pixels, new DisplayParserHandler() {
                                @Override
                                public void menuFound(Menu menu) {
                                    try {
                                        rtHandler.rtDisplayHandleMenu(menu);
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void noMenuFound() {
                                    try {
                                        rtHandler.rtDisplayHandleNoMenu();
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });

                        }
                    });
                    rtHandler.rtStarted();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                while(rtModeRunning)
                {
                    try {
                        if (System.currentTimeMillis() > lastRtMessageSent + 1000L) {
                            log("sending keep alive");
                            synchronized (rtSequenceSemaphore) {
                                rtSequence = Application.sendRTKeepAlive(rtSequence, btConn);
                                lastRtMessageSent = System.currentTimeMillis();
                            }
                        }
                    } catch (Exception e) {
                        if (rtModeRunning) {
                            fail("Error sending keep alive while rtModeRunning is still true");
                        } else {
                            fail("Error sending keep alive. rtModeRunning is false, so this is most likely a race condition during disconnect");
                        }
                    }
                    try{
                        Thread.sleep(500);}catch(Exception e){/*ignore*/}
                }
                try {
                    rtHandler.rtStopped();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

            }
        }.start();
    }

    private Runnable synThread = new Runnable(){
        @Override
        public void run() {
            while(synRun && (rtModeRunning||cmdModeRunning))
            {
                Protocol.sendSyn(btConn);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private AppHandler rtAppHandler = new AppHandler() {
        @Override
        public void log(String s) {
            Ruffy.this.log(s);
        }

        @Override
        public void connected() {
            if(rtModeRunning)
                Application.sendAppCommand(Application.Command.RT_MODE, btConn);
            else if (cmdModeRunning)
                Application.sendAppCommand(Application.Command.COMMAND_MODE, btConn);
        }

        @Override
        public void rtModeActivated() {
            startRT();
        }

        @Override
        public void cmdModeActivated() {
            startCmd();
        }

        @Override
        public void rtModeDeactivated() {
            rtSequence =0;

            if(rtHandler!=null)
                try {rtHandler.rtStopped();} catch (RemoteException e){};

            if(cmdModeRunning) {
                Application.sendAppCommand(Application.Command.COMMAND_MODE,btConn);
            } else {
                if(!inShutDown) {
                    inShutDown = true;
                    Application.sendAppDisconnect(btConn);
                    btConn.disconnect();
                }
            }
        }

        @Override
        public void cmdModeDeactivated() {
            rtSequence =0;

            if(cmdHandler!=null)
                try {cmdHandler.cmdStopped();} catch (RemoteException e){};

            if(rtModeRunning) {
                Application.sendAppCommand(Application.Command.RT_MODE,btConn);
            } else {
                if(!inShutDown) {
                    inShutDown = true;
                    Application.sendAppDisconnect(btConn);
                    btConn.disconnect();
                }
            }
        }

        @Override
        public void modeDeactivated() {
            rtModeRunning = false;
            cmdModeRunning = false;
            rtSequence =0;
            if(rtHandler!=null)
                try {rtHandler.rtStopped();} catch (RemoteException e){};
            if(cmdHandler!=null)
                try {cmdHandler.cmdStopped();} catch (RemoteException e){};
            if(!inShutDown) {
                inShutDown = true;
                Application.sendAppDisconnect(btConn);
                btConn.disconnect();
            }
        }

        @Override
        public void addDisplayFrame(ByteBuffer b) {
            display.addDisplayFrame(b);
        }

        @Override
        public void modeError() {
            modeErrorCount++;

            if (modeErrorCount > Application.MODE_ERROR_TRESHHOLD) {
                stopRT();
                log("wrong mode, deactivate");

                modeErrorCount = 0;
                Application.sendAppCommand(Application.Command.DEACTIVATE_ALL, btConn);
            }
        }

        @Override
        public void sequenceError() {
            Application.sendAppCommand(Application.Command.APP_DISCONNECT, btConn);
        }

        @Override
        public void error(short error, String desc) {
            switch (error)
            {
                case (short) 0xF056:
                    PumpData d = btConn.getPumpData();
                    btConn.disconnect();
                    btConn.connect(d,4);
                    break;
                default:
                    log(desc);
            }
        }

        @Override
        public void cmdBolusStarted(boolean success) {
            if(cmdHandler!=null) {
                try {cmdHandler.bolusStarted(success);}catch(Exception e){e.printStackTrace();}
            }
        }

        @Override
        public void cmdBolusState(BolusState notDelivering, double remaining) {
            if(cmdHandler!=null) {
                switch (notDelivering) {
                    case ABORTED:
                        try {cmdHandler.bolusStatus(10,remaining);}catch(Exception e){e.printStackTrace();}
                        break;
                    case CANCELED:
                        try {cmdHandler.bolusStatus(5,remaining);}catch(Exception e){e.printStackTrace();}
                        break;
                    case DELIVERED:
                        try {cmdHandler.bolusStatus(2,remaining);}catch(Exception e){e.printStackTrace();}
                        break;
                    case DELIVERING:
                        try {cmdHandler.bolusStatus(1,remaining);}catch(Exception e){e.printStackTrace();}
                        break;
                    case NOT_DELIVERING:
                        try {cmdHandler.bolusStatus(20,remaining);}catch(Exception e){e.printStackTrace();}
                        break;
                    default:
                        try {cmdHandler.bolusStatus(-1,remaining);}catch(Exception e){e.printStackTrace();}
                        break;
                }

            }
        }

        @Override
        public void cmdBolusCanceled(boolean success) {
            if (cmdHandler != null) {
                try {
                    cmdHandler.bolusCancled(success);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void reportBolusDelivered(boolean manual, double infused, long ts, long eventCnt, short eventId) {
            if(cmdHandler!= null) {
                try { cmdHandler.reportBolusDelivered(manual,infused,ts,eventCnt,eventId);}catch(Exception e){e.printStackTrace();}
            }
        }

        @Override
        public void reportBolusRequest(boolean manual, double requested, long ts, long eventCnt, short eventId) {
            if(cmdHandler!= null) {
                try { cmdHandler.reportBolusRequested(manual,requested,ts,eventCnt,eventId);}catch(Exception e){e.printStackTrace();}
            }
        }

        @Override
        public void doConfirmHistorie() {
            Application.doCmdHistorieAck(btConn);
        }

        @Override
        public void doReadHistorie() {
            Application.doCmdHistorie(btConn);
        }
    };

    private void startCmd() {
        log("starting Cmd keepAlive");
        new Thread(){
            @Override
            public void run() {
                cmdModeRunning = true;
                lastCmdMessageSent = System.currentTimeMillis();
                try {
                    cmdHandler.cmdStarted();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                while(cmdModeRunning)
                {
                    try {
                        if (System.currentTimeMillis() > lastCmdMessageSent + 5000L) {
                            log("sending keep alive");
                            Application.sendCmdKeepAlive(btConn);
                            lastCmdMessageSent = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        if (cmdModeRunning) {
                            fail("Error sending keep alive while cmdModeRunning is still true");
                        } else {
                            fail("Error sending keep alive. cmdModeRunning is false, so this is most likely a race condition during disconnect");
                        }
                    }
                    try{
                        Thread.sleep(500);}catch(Exception e){/*ignore*/}
                }
                try {
                    cmdHandler.cmdStopped();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

            }
        }.start();
    }

    public void log(String s) {
        try{
            rtHandler.log(s);
        }catch(Exception e){e.printStackTrace();}
    }

    public void fail(String s) {
        try{
            rtHandler.fail(s);
        }catch(Exception e){e.printStackTrace();}
    }

    private boolean inShutDown = false;
    private PacketHandler rtPacketHandler = new PacketHandler(){
        @Override
        public void sendImidiateAcknowledge(byte sequenceNumber) {
            if(!inShutDown)
                Protocol.sendAck(sequenceNumber,btConn);
        }

        @Override
        public void log(String s) {
            Ruffy.this.log(s);
        }

        @Override
        public void handleResponse(Packet.Response response, boolean reliableFlagged, byte[] payload) {
            switch (response)
            {
                case ID:
                    Protocol.sendSyn(btConn);
                    break;
                case SYNC:
                    btConn.seqNo = 0x00;

                    if(step<100)
                        Application.sendAppConnect(btConn);
                    else
                    {
                        Application.sendAppDisconnect(btConn);
                        step = 200;
                    }
                    break;
                case UNRELIABLE_DATA:
                case RELIABLE_DATA:
                    Application.processAppResponse(payload, reliableFlagged, rtAppHandler);
                    break;
            }
        }

        @Override
        public void handleErrorResponse(byte errorCode, String errDecoded, boolean reliableFlagged, byte[] payload) {
            log(errDecoded);
        }

        @Override
        public Object getToDeviceKey() {
            return pumpData.getToDeviceKey();
        }
    };
}
