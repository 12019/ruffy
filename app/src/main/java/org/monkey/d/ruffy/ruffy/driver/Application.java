package org.monkey.d.ruffy.ruffy.driver;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.List;

/**
 * Some statics to do some things on application level
 */
public class Application {
    /**
     * how often we accept an mode error before accepting we are in the wrong mode
     */
    public static int MODE_ERROR_TRESHHOLD = 3;

    public static void sendCmdKeepAlive(BTConnection btConn) {
        sendAppCommand(Command.CMD_PING,btConn);
    }

    public static void doCmdBolus(int bolus, BTConnection conn) {
        ByteBuffer payload = ByteBuffer.allocate(26);

        payload.put((byte) 16);
        payload.put((byte) 0xB7);
        payload.put((byte) 0x69);				//CBOL_DELIVER_BOLUS Command
        payload.put((byte) 0x96);

        //CRC starts here
        payload.put((byte) 0x55);				//Hard coded standard bolus mode
        payload.put((byte) 0x59);

        payload.order(ByteOrder.LITTLE_ENDIAN);

        payload.putShort((short) bolus);			//Ch1 amount 0.1U increments
        payload.putShort((short) 0);				//Ch1 duration in minutes
        payload.putShort((short) 0);				//Ch1 fast amount

        payload.putFloat((float) bolus);			//Ch2 amount 0.1U increments
        payload.putFloat(0);						//Ch2 duration in minutes
        payload.putFloat(0);						//Ch2 fast amount
        //CRC ends here

        short crc = (short)0xFFFF;
        for(int i = 4;i<24;i++)
            crc = Utils.updateCrc(crc, payload.get(i));

        payload.putShort(crc);

        sendData(payload,true,conn);
    }

    public static void doCmdBolusState(BTConnection btConn) {
        ByteBuffer payload = ByteBuffer.allocate(4);

        payload.put((byte)16);
        payload.put((byte)0xB7);
        payload.put((byte) 0x6A);				//CBOL_IMMEDIATE_BOLUS_STATUS_REQ Command
        payload.put((byte) 0x96);

        sendData(payload,true,btConn);
    }

    public static void doCmdBolusCancel(BTConnection btConn) {
        ByteBuffer payload = ByteBuffer.allocate(5);

        payload.put((byte) 16);
        payload.put((byte) 0xB7);
        payload.put((byte) 0x95);
        payload.put((byte) 0x96);
        payload.put((byte) 0x47);

        sendData(payload,true,btConn);
    }

    public static void doCmdHistorie(BTConnection btConn) {
        ByteBuffer payload = ByteBuffer.allocate(4);

        payload.put((byte) 16);
        payload.put((byte) 0xB7);
        payload.put((byte) 0x96);
        payload.put((byte) 0x99);

        sendData(payload,true,btConn);
    }

    public static void doCmdHistorieAck(BTConnection btConn) {
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.put((byte)16);
        payload.put((byte)0xB7);
        payload.put((byte)0x99);
        payload.put((byte)0x99);

        sendData(payload,true,btConn);
    }

    public static enum Command
    {
        COMMANDS_SERVICES_VERSION,
        REMOTE_TERMINAL_VERSION,
        BINDING,
        COMMAND_MODE,
        COMMAND_DEACTIVATE,
        RT_MODE,
        RT_DEACTIVATE,
        DEACTIVATE_ALL,
        APP_DISCONNECT,
        CMD_PING,
    }

    public static void sendAppConnect(BTConnection btConn) {
        ByteBuffer payload = null;
        byte[] connect_app_layer = {16, 0, 85, -112};

        payload = ByteBuffer.allocate(8);				//4 bytes for application header, 4 for payload
        payload.put(connect_app_layer);					//Add prefix array
        payload.order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(Integer.parseInt("12345"));

        Application.sendData(payload,true,btConn);
    }

    private static void sendData(ByteBuffer payload, boolean reliable,BTConnection btConn)  {
        btConn.getPumpData().incrementNonceTx();

        byte[] sendR = {16,3,0,0,0};

        List<Byte> packet  = Packet.buildPacket(sendR, payload, true,btConn);					//Add the payload, set the address if valid

        if(reliable) {
            int seq = btConn.seqNo;
            packet.set(1, setSeqRel(packet.get(1), true,btConn));                        //Set the sequence and reliable bits
        }
        Packet.adjustLength(packet, payload.capacity());							//Set the payload length

        packet = Utils.ccmAuthenticate(packet, btConn.getPumpData().getToPumpKey(), btConn.getPumpData().getNonceTx());		//Authenticate packet


        List<Byte> temp = Frame.frameEscape(packet);
        byte[] ro = new byte[temp.size()];
        int i = 0;
        for(byte b : temp)
            ro[i++]=b;

        btConn.write(ro);
    }

    private static Byte setSeqRel(Byte b, boolean rel,BTConnection btConn)
    {
        b = (byte) (b | btConn.seqNo);			//Set the sequence bit

        if(rel)
            b = (byte) (b |0x20);			//Set the reliable bit

        btConn.seqNo ^= 0x80;

        return b;
    }
    private static byte[] service_activate = {16, 0, 0x66, (byte)0x90};
    private static byte[] service_deactivate = {16, 0, 0x69, (byte)0x90};

    public static void sendAppCommand(Command command, BTConnection btConn){
        ByteBuffer payload = null;

        String s = "";

        boolean reliable = true;

        switch(command)
        {
            case COMMAND_MODE:
                s = "COMMAND_ACTIVATE";
                payload = ByteBuffer.allocate(7);
                payload.put(service_activate);
                payload.put((byte)0xB7);
                payload.put((byte)0x01);
                payload.put((byte)0x00);
                reliable = true;
                break;

            case COMMAND_DEACTIVATE:
                s = "COMMAND DEACTIVATE";
                payload = ByteBuffer.allocate(5);
                payload.put(service_deactivate);
                payload.put((byte)0xB7);
                reliable = true;
                break;
            case RT_MODE:
                s = "RT_ACTIVATE";
                payload = ByteBuffer.allocate(7);
                payload.put(service_activate);
                payload.put((byte)0x48);
                payload.put((byte)0x01);
                payload.put((byte)0x00);
                reliable = true;
                break;
            case RT_DEACTIVATE:
                s = "RT DEACTIVATE";
                payload = ByteBuffer.allocate(5);
                payload.put(service_deactivate);
                payload.put((byte)0x48);
                reliable = true;
                break;
            case COMMANDS_SERVICES_VERSION:
                s = "COMMAND_SERVICES_VERSION";
                payload = ByteBuffer.allocate(5);
                payload.put((byte)16);
                payload.put((byte)0);
                payload.put((byte) (((short)0x9065) & 0xFF));
                payload.put((byte) ((((short)0x9065)>>8) & 0xFF));
                payload.put((byte)0xb7);
                reliable = true;
                break;
            case REMOTE_TERMINAL_VERSION:
                s = "REMOTE_TERMINAL_VERSION";
                payload = ByteBuffer.allocate(5);
                payload.put((byte)16);
                payload.put((byte)0);
                payload.put((byte) (((short)0x9065) & 0xFF));
                payload.put((byte) ((((short)0x9065)>>8) & 0xFF));
                payload.put((byte)0x48);
                reliable = true;
                break;
            case BINDING:
                s = "BINDING";
                payload = ByteBuffer.allocate(5);
                payload.put((byte)16);
                payload.put((byte)0);
                payload.put((byte) (((short)0x9095) & 0xFF));
                payload.put((byte) ((((short)0x9095)>>8) & 0xFF));
                payload.put((byte) 0x48);		//Binding OK
                reliable = true;
                break;

            case APP_DISCONNECT:
                byte[] connect_app_layer = {16 , 0, 0x5a, 0x00};
                payload = ByteBuffer.allocate(6);				//4 bytes for application header, 4 for payload
                payload.put(connect_app_layer);					//Add prefix array
                payload.order(ByteOrder.LITTLE_ENDIAN);
                payload.putShort((byte)0x6003);
                reliable = true;
                break;

            case CMD_PING:
                payload = ByteBuffer.allocate(4);
                payload.put((byte)16);
                payload.put((byte)0xB7);
                payload.put((byte) (0x9AAA & 0xFF));
                payload.put((byte) ((0x9AAA>>8) & 0xFF));
                reliable = true;
                break;

            case DEACTIVATE_ALL:
                Log.d("ApplicatioN","Send deactivate");
                payload = ByteBuffer.allocate(4);
                payload.put((byte)16);
                payload.put((byte)0);
                payload.put((byte)(0x6A));
                payload.put((byte)(0x90));
                reliable = true;
                break;

            default:
                s = "uknown subcommand: "+command;
                break;
        }

        if(payload != null)
        {
            sendData(payload,reliable,btConn);
        }
    }

    public static void sendAppDisconnect(BTConnection btConn) {
        sendAppCommand(Command.APP_DISCONNECT,btConn);
    }

    public static void cmdPing(BTConnection btConn)
    {
        sendAppCommand(Command.CMD_PING,btConn);
    }

    public static short sendRTKeepAlive(short rtSeq, BTConnection btConn) {
        ByteBuffer payload = ByteBuffer.allocate(6);
        payload.put((byte)16);
        payload.put((byte)0x48);
        payload.put((byte) (0x0566 & 0xFF));
        payload.put((byte) ((0x0566>>8) & 0xFF));

        payload.put((byte) (rtSeq & 0xFF));
        payload.put((byte) ((rtSeq>>8) & 0xFF));

        sendData(payload,false,btConn);

        btConn.log("/////////////////////////////////////////////////////////////////////");
        btConn.log("send alive with seq: "+rtSeq);
        btConn.log("/////////////////////////////////////////////////////////////////////");
        rtSeq++;
        return rtSeq;

    }

    public static void cmdErrStatus(BTConnection btConn)
    {
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.put((byte)16);

        payload.put((byte)0x48);

        payload.put((byte) (0x9AA5 & 0xFF));
        payload.put((byte) ((0x9AA5>>8) & 0xFF));

        sendData(payload, true, btConn);
    }

    public static short rtSendKey(byte key, boolean changed,short rtSeq, BTConnection btConn)
    {
        ByteBuffer payload = ByteBuffer.allocate(8);

        payload.put((byte)16);
        payload.put((byte)0x48);
        payload.put((byte) 0x65);
        payload.put((byte) 0x05);

        payload.put((byte) (rtSeq & 0xFF));
        payload.put((byte) ((rtSeq>>8) & 0xFF));

        payload.put(key);

        if(changed)
            payload.put((byte) 0xB7);
        else
            payload.put((byte) 0x48);

        btConn.log("/////////////////////////////////////////////////////////////////////");
        String k = "";
        switch (key)
        {
            case 0x00:
                k="NOKEY";
                break;
            case 0x03:
                k="MENU";
                break;
            case 0x0C:
                k="CHECK";
                break;
            case 0x30:
                k="UP";
                break;
            case (byte)0xC0:
                k="DOWN";
                break;
        }
        btConn.log("send key "+k+" with seq: "+rtSeq);
        btConn.log("/////////////////////////////////////////////////////////////////////");
        sendData(payload, false, btConn);

        rtSeq++;
        return rtSeq;
    }

    public static void processAppResponse(byte[] payload, boolean reliable, AppHandler handler) {
        handler.log("processing app response");
        ByteBuffer b = ByteBuffer.wrap(payload);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.get();//ignore
        byte servId = b.get();
        short commId = b.getShort();

        handler.log("Service ID: " + String.format("%X", servId) + " Comm ID: " + String.format("%X", commId) + " reliable: " + reliable);

        String descrip = null;
        if(reliable)
        {
            short error = b.getShort();
            if (!cmdProcessError(error,handler)) {
                return;
            }

            switch (commId) {
                case (short) 0xA055://connect answer:
                    handler.connected();
                    break;
                case (short) 0xA065://something
                case (short) 0xA095://bind
                    handler.log("not should happen here!");
                    break;
                case (short) 0xA066: {//activate service:
                    byte service = b.get();
                    if (service == -73)
                        handler.cmdModeActivated();
                    else
                        handler.rtModeActivated();
                } break;
                case (short) 0x005A://AL_DISCONNECT_RES:
                    descrip = "AL_DISCONNECT_RES";
                    break;
                case (short) 0xA069: {//service deactivated
                    byte service = b.get();
                    descrip = "AL_DEACTIVATE_RES";
                    if (service == -73)
                        handler.cmdModeDeactivated();
                    else
                        handler.rtModeDeactivated();
                } break;
                case (short) 0xA06A://service all deactivate
                    descrip = "AL_DEACTIVATE_ALL_RES";
                    handler.modeDeactivated();
                    break;
                case (short)0xAAAA: //ping response
                    descrip = "CMD_PING_RES";
                    break;
                case (short)0xA669:
                    descrip = "CMD_BOLUS_STARTED";
                    // 10 B7 69 A6 00 00 48 38 20 4D 2E 44 91 A2 EA CC
                    handler.cmdBolusStarted(b.get()==0x48);
                    break;
                case (short)0xA695:
                    descrip = "CMD_BOLUS_CANCELED";
                    handler.cmdBolusCanceled(b.get()==0x48);
                    break;
                case (short)0xA66A: {
                    descrip = "CMD_BOLUS_STATUS";
                    b.get();//ignore
                    byte status = b.get();
                    short rem = b.getShort();

                    switch (status) {
                        case (byte) 0x55:
                            handler.cmdBolusState(BolusState.NOT_DELIVERING,(double)(rem/10.0));
                            break;
                        case (byte) 0x66:
                            handler.cmdBolusState(BolusState.DELIVERING,(double)(rem/10.0));
                            break;
                        case (byte) 0x99:
                            handler.cmdBolusState(BolusState.DELIVERED,(double)(rem/10.0));
                            break;
                        case (byte) 0xA9:
                            handler.cmdBolusState(BolusState.CANCELED,(double)(rem/10.0));
                            break;
                        case (byte) 0xAA:
                            handler.cmdBolusState(BolusState.ABORTED,(double)(rem/10.0));
                            break;
                        default:
                            handler.cmdBolusState(BolusState.UNKNOWN,(double)(rem/10.0));
                    }
                }   break;

                case (short)0xA996:
                    descrip = "CMD_HISTORY";
                {
                    b.order(ByteOrder.LITTLE_ENDIAN);

                    short histRem = b.getShort();
                    byte end = b.get();
                    byte histGap = b.get();
                    byte numEvents = b.get();
                    boolean more = true;
                    if (end == 0x48)                            //0x48 indicates more events are available
                        more = false;
                    byte[] data = new byte[4];
                    byte[] time = new byte[4];
                    for (int i = 0; i < numEvents; i++) {
                        //Extract the data from the event
                        b.get(time);
                        b.get(data);
                        short eventId = b.getShort();
                        short check = b.getShort();
                        long eventCnt = (long) b.getInt();
                        short checkCnt = b.getShort();

                        ByteBuffer d = ByteBuffer.wrap(data);
                        d.order(ByteOrder.LITTLE_ENDIAN);

                        // Get the time data *******************************************************************************
                        int seconds = time[0] & 0x3F;
                        int minutes = (time[0] >> 6) & 0x03;
                        minutes |= (time[1] << 2) & 0x3C;
                        int hours = (time[1] >> 4) & 0x0F;
                        hours |= (time[2] << 4) & 0x10;
                        int day = (time[2] >> 1) & 0x1F;
                        int month = (time[2] >> 6) & 0x03;
                        month |= (time[3] << 2) & 0x0C;
                        int year = (time[3] >> 2) & 0x3F;

                        long ts = dateToMS(day, month, year, hours, minutes, seconds);

                        switch (eventId) {
                            case 15:
                            case 17:
                            {
                                double infused = d.getShort() / 10.0d;
                                boolean manual = false;
                                if(eventId == 7)
                                {
                                    manual = true;
                                }
                                handler.reportBolusDelivered(manual,infused,ts,eventCnt,eventId);
                            }
                            break;
                            case 14:
                            case 6:
                            {
                                double requested = d.getShort() / 10.0d;
                                boolean manual = false;
                                if(eventId == 7)
                                {
                                    manual = true;
                                }
                                handler.reportBolusRequest(manual,requested,ts,eventCnt,eventId);
                            }
                            break;
                            default:
                                Log.v("Application","unknown eventid:"+eventId);
                                break;
                        }
                    }
                    if(numEvents>0) {
                        handler.doConfirmHistorie();
                    }
                    if((histRem - numEvents) > 0)
                    {
                        handler.doReadHistorie();
                    }
                }
                    break;
                case (short)0xA999:
                    descrip = "CMD_HISTORY_ACK";
                    break;

                default:
                    descrip = "UNKNOWN";
                    break;
            }
        } else {
            switch (commId) {
                case (short) 0x0555://Display frame
                    handler.addDisplayFrame(b);
                    break;
                case (short) 0x0556://key answer
                    break;
                case (short) 0x0566://alive answer, often missed
                    break;
                default:
                    descrip = "UNKNOWN";
                    break;
            }
        }
        handler.log("appProcess: "+descrip);
    }

    private static boolean cmdProcessError(short error, AppHandler handler) {
        String desc = "Error > " + String.format("%X", error) + " ";

        if (error == 0x0000) {
            desc = "No error found!";
            return true;
        } else {
            switch (error) {
                //Application Layer **********************************************//
                case (short) 0xF003:
                    desc = "Unknown Service ID, AL, RT, or CMD";
                    break;
                case (short) 0xF006:
                    desc = "Invalid payload length";
                    break;
                case (short) 0xF05F:
                    desc = "wrong mode";
                    handler.modeError();
                    break;

                case (short) 0xF50C:
                    desc = "wrong sequence";
                    handler.sequenceError();
                    break;
                case (short) 0xF533:
                    desc = "died - no alive";
                    break;
                case (short) 0xF056:
                    desc = "not complete connected";
                    break;
            }

            handler.error(error,desc);
            return false;
        }
    }

    private static long dateToMS(int day, int month, int year, int hours, int minutes, int seconds)
    {
        final String FUNC_TAG = "dateToSeconds";

        long total = 0;

        Calendar epoch = Calendar.getInstance();
        epoch.clear();
        epoch.set(year+2000, month-1, day, hours, minutes, seconds);
        total = epoch.getTimeInMillis();

        return total;
    }
}
