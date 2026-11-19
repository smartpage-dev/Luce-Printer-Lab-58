package hk.luce.printerlab;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.os.Build;
import android.os.ParcelUuid;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One manually selected connection; no reconnect, no raw BLE writes, no hidden RFCOMM channels. */
@SuppressLint("MissingPermission")
public final class BtTransport implements AutoCloseable {
    public interface Logger {void event(String type,String detail);}
    public static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Logger logger;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private volatile BluetoothSocket socket;
    private volatile BluetoothGatt gatt;
    private volatile String address;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final ByteArrayOutputStream rx=new ByteArrayOutputStream();
    private volatile boolean insecure;
    public BtTransport(Context context,BluetoothAdapter adapter,Logger logger){this.context=context;this.adapter=adapter;this.logger=logger;}
    public boolean connectedTo(BluetoothDevice device){BluetoothSocket s=socket;return s!=null && s.isConnected() && device!=null && device.getAddress().equals(address);}
    public String mode(){return insecure?"RFCOMM_insecure_compat":"RFCOMM_secure";}
    public void beginOperation(){cancelled.set(false);}
    public void checkCancelled()throws InterruptedIOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("cancelled");}
    public void disconnect(){ cancelled.set(true); dropConnection(); }
    private void dropConnection(){
        BluetoothSocket s=socket;socket=null;address=null;
        if(s!=null)try{s.close();}catch(IOException ignored){}
        BluetoothGatt g=gatt;gatt=null;if(g!=null)try{g.disconnect();g.close();}catch(SecurityException ignored){}
    }
    public void connect(BluetoothDevice device,boolean compatibility)throws Exception {
        dropConnection();checkCancelled();adapter.cancelDiscovery();
        if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("Pair using Android Settings first");
        BluetoothSocket s=compatibility?device.createInsecureRfcommSocketToServiceRecord(SPP):device.createRfcommSocketToServiceRecord(SPP);
        socket=s;insecure=compatibility;
        ScheduledFuture<?> timeout=null;
        try{
            checkCancelled();
            timeout=timer.schedule(()->{try{s.close();}catch(IOException ignored){}},12,TimeUnit.SECONDS);
            checkCancelled();s.connect();checkCancelled();address=device.getAddress();
            logger.event("CONNECTED",mode()+"; standard SPP UUID; not proof of printing support");
            Thread reader=new Thread(()->readLoop(s),"printer-lab-receiver");reader.setDaemon(true);reader.start();
        }catch(Exception e){disconnect();throw e;}finally{if(timeout!=null)timeout.cancel(false);}
    }
    private void readLoop(BluetoothSocket s) {
        try{byte[] b=new byte[64];InputStream in=s.getInputStream();int n;
            while(socket==s && (n=in.read(b))!=-1){ synchronized(rx){int keep=Math.min(n,256-rx.size());if(keep>0)rx.write(b,0,keep);rx.notifyAll();} }
        }catch(IOException ignored){}finally{
            if(socket==s){socket=null;address=null;try{s.close();}catch(IOException ignored){}logger.event("CONNECTION_CLOSED","Remote/local socket closed; no automatic reconnect");}
        }
    }
    public int send(byte[] packet,int chunk,int delayMs)throws Exception {
        if(packet.length>Protocol.MAX_PACKET || (chunk!=128 && chunk!=256) || (delayMs!=20 && delayMs!=50))throw new IllegalArgumentException("Send limits");
        BluetoothSocket s=socket;if(s==null||!s.isConnected())throw new IOException("Not connected");
        ScheduledFuture<?> timeout=null;
        int sent=0;
        try{
            checkCancelled();
            timeout=timer.schedule(()->{try{s.close();}catch(IOException ignored){}},30,TimeUnit.SECONDS);
            OutputStream out=s.getOutputStream();
            while(sent<packet.length){checkCancelled();int n=Math.min(chunk,packet.length-sent);out.write(packet,sent,n);sent+=n;
                if(sent<packet.length)Thread.sleep(delayMs);
            }
            out.flush();checkCancelled();return sent;
        }catch(Exception e){logger.event("SEND_INTERRUPTED","completed_write_bytes="+sent+"; current write may be partial; no automatic retry");disconnect();throw e;}
        finally{if(timeout!=null)timeout.cancel(false);}
    }
    public void queryStatus()throws Exception {
        synchronized(rx){rx.reset();}
        send(Protocol.statusQuery(),128,20);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(System.nanoTime()<deadline){checkCancelled();synchronized(rx){if(rx.size()>0)break;rx.wait(100);}}
        byte[] reply;synchronized(rx){reply=rx.toByteArray();rx.reset();}
        if(reply.length==0){logger.event("STATUS","No response in 2s; support unknown. Disconnecting before any further commands.");disconnect();}
        else logger.event("STATUS_RAW","DLE EOT 1; first <=8 bytes="+Protocol.hex(reply,8)+"; not interpreted as print completion");
    }
    public void inspectSdp(BluetoothDevice device)throws Exception {
        adapter.cancelDiscovery();CountDownLatch latch=new CountDownLatch(1);
        BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            BluetoothDevice got;
            if(Build.VERSION.SDK_INT>=33)got=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
            else got=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(got==null||!got.getAddress().equals(device.getAddress()))return;
            android.os.Parcelable[] a=i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
            recordUuids(a);latch.countDown();
        }};
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,new IntentFilter(BluetoothDevice.ACTION_UUID),Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver,new IntentFilter(BluetoothDevice.ACTION_UUID));
        try{
            recordUuids(device.getUuids());
            if(!device.fetchUuidsWithSdp()){logger.event("SDP","Request not started; cached/empty results are inconclusive");return;}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(latch.getCount()>0&&System.nanoTime()<deadline){checkCancelled();latch.await(100,TimeUnit.MILLISECONDS);}
            if(latch.getCount()>0)logger.event("SDP","No result in 10s; does not prove SPP is unsupported");
        }finally{context.unregisterReceiver(receiver);}
    }
    private void recordUuids(android.os.Parcelable[] uuids){
        if(uuids==null||uuids.length==0){logger.event("SDP","No UUID list available yet");return;}
        for(android.os.Parcelable p:uuids)if(p instanceof ParcelUuid)logger.event("SDP_UUID",((ParcelUuid)p).getUuid().toString());
    }
    public void inspectGatt(BluetoothDevice device)throws Exception {
        dropConnection();checkCancelled();adapter.cancelDiscovery();CountDownLatch latch=new CountDownLatch(1);
        BluetoothGattCallback callback=new BluetoothGattCallback(){
            @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){
                logger.event("GATT_STATE","status="+status+"; state="+state);
                if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){
                    if(!g.discoverServices())latch.countDown();
                }else if(state==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS)latch.countDown();
            }
            @Override public void onServicesDiscovered(BluetoothGatt g,int status){
                logger.event("GATT_DISCOVERY","status="+status+"; no characteristics read or written");
                if(status==BluetoothGatt.GATT_SUCCESS)for(BluetoothGattService s:g.getServices()){
                    logger.event("GATT_SERVICE",s.getUuid().toString());
                    for(BluetoothGattCharacteristic c:s.getCharacteristics())logger.event("GATT_CHARACTERISTIC",c.getUuid()+"; properties="+c.getProperties());
                }latch.countDown();
            }
        };
        checkCancelled();
        BluetoothGatt g=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);gatt=g;
        if(g==null)throw new IOException("GATT unavailable");
        try{
            checkCancelled();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(latch.getCount()>0&&System.nanoTime()<deadline){checkCancelled();latch.await(100,TimeUnit.MILLISECONDS);}
            if(latch.getCount()>0)logger.event("GATT","Timeout; capability unknown");
        }finally{if(gatt==g)gatt=null;g.disconnect();g.close();}
    }
    @Override public void close(){disconnect();timer.shutdownNow();}
}
