package hk.luce.printerlab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Foreground-only, deliberately separate from LensDrop and all production queues. */
@SuppressLint({"MissingPermission", "SetTextI18n"})
public final class MainActivity extends Activity {
    private static final int PERMISSIONS=41, EXPORT=42;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AtomicInteger operationGeneration=new AtomicInteger();
    private final List<View> lockViews=new ArrayList<>();
    private final List<BluetoothDevice> devices=new ArrayList<>();
    private final LinkedHashMap<String,BluetoothDevice> bleFound=new LinkedHashMap<>();
    private BluetoothAdapter adapter;
    private BtTransport transport;
    private Journal journal;
    private LinearLayout root;
    private Spinner deviceList, tests, widths, modules, pacing;
    private CheckBox compatibility;
    private EditText custom;
    private TextView status, logView, previewText, guideText;
    private ImageView preview;
    private volatile boolean destroyed=false,foreground=false;
    private volatile Future<?> running;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private Runnable scanStop;
    private Uri pendingExport;
    private String modeLabel="Disconnected";
    private static final String[] TESTS={
        "A - Plain ASCII (no ESC commands)",
        "B - ESC @ then ASCII",
        "C - Native QR / short demo URL",
        "D - Native QR / long demo URL",
        "E - GS v 0 / 160-dot calibration grid",
        "F - GS v 0 / short QR raster",
        "G - GS v 0 / long QR raster",
        "H - ESC * 24-dot / short QR raster",
        "I - GS v 0 / Chinese bitmap sample",
        "J - Native QR / custom test HTTPS URL",
        "K - GS v 0 / custom test HTTPS URL"
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        journal=new Journal(this);
        BluetoothManager manager=getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
        transport=new BtTransport(this,adapter,this::event);
        buildUi();
        event("APP_START","Printer Lab "+BuildConfig.VERSION_NAME+"; interrupted writes are never replayed");
    }
    @Override protected void onStart(){super.onStart();foreground=true;}
    @Override protected void onResume(){super.onResume();foreground=true;handler.post(this::finishPendingExport);}
    @Override protected void onStop(){
        foreground=false;
        stopAll("App left foreground: connection closed; nothing will be resent");
        super.onStop();
    }
    @Override protected void onDestroy(){
        destroyed=true;stopScan();transport.close();worker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();
        // A worker may still be finalizing its audit record. Process teardown closes SQLite safely.
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(24,47,49));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(5),0,dp(5));return t;
    }
    private void note(LinearLayout box,String s){box.addView(text(s,14,false));}
    private LinearLayout card(String title){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),Color.rgb(212,228,224));c.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,dp(6));root.addView(c,p);c.addView(text(title,20,true));return c;
    }
    private Button button(LinearLayout box,String name,Runnable action,boolean lock){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setOnClickListener(v->{try{action.run();}catch(Exception e){showError(e);}});
        box.addView(b,new LinearLayout.LayoutParams(-1,-2));if(lock)lockViews.add(b);return b;
    }
    private Spinner spinner(LinearLayout box,String[] values){
        Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);box.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));lockViews.add(s);return s;
    }
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(24));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });scroll.requestApplyInsets();
        root.addView(text("LUCE / HARDWARE LAB",13,true));root.addView(text("Printer Lab 58",30,true));
        root.addView(text("Independent diagnostic app  |  "+BuildConfig.VERSION_NAME,14,false));
        status=text("Disconnected - no device selected",16,true);root.addView(status);
        button(root,"STOP / Disconnect",()->stopAll("Stopped by operator; check paper before retrying"),false);
        LinearLayout intro=card("0  Prepare safely");
        note(intro,"Yokoscan 58B3 / T58_F82E is the supplied sample label, not a certified profile. Close all other printer apps. Load ordinary receipt paper. Use fake URLs only.");
        note(intro,"Nothing prints automatically. Sent bytes do not prove paper output. Leaving this app closes Bluetooth. STOP cannot recall data already in the printer.");
        note(intro,"No firmware, sleep, density, cutter, cash-drawer or NV-memory commands. ESC @ resets the volatile print buffer; do not share the printer during a test.");
        LinearLayout connect=card("1  Pair and select");
        button(connect,"Grant Bluetooth permissions",()->permissions(false),true);
        button(connect,"Open Android Bluetooth settings",()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),true);
        note(connect,"Pair with T58_F82E; PIN 0000 only if requested by Android. A name is not a unique identity. Select your own printer below.");
        button(connect,"Refresh paired devices",this::refreshDevices,true);
        deviceList=spinner(connect,new String[]{"Choose your printer (no auto-connect)"});
        deviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                if(!busy.get()){transport.disconnect();modeLabel="Disconnected";refreshStatus();refreshGuide(false);}
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        LinearLayout inspect=card("2  Identify transport");
        button(inspect,"Inspect Classic service UUIDs (SDP)",()->{
            BluetoothDevice d=selected();if(d==null||!permissions(false))return;
            runTask("SDP inspection",()->{transport.inspectSdp(d);return null;});
        },true);
        compatibility=new CheckBox(this);compatibility.setText("Compatibility RFCOMM (weaker authentication; manual opt-in)");inspect.addView(compatibility);lockViews.add(compatibility);
        button(inspect,"Connect selected printer via SPP",()->{
            BluetoothDevice d=selected();if(d==null||!permissions(false))return;
            boolean insecure=compatibility.isChecked();
            Runnable go=()->runTask("Connect",()->{transport.connect(d,insecure);modeLabel=transport.mode();return null;});
            if(insecure)new AlertDialog.Builder(this).setTitle("Compatibility connection").setMessage("Use only for this known local test printer and fake data. This does not use the secure RFCOMM authentication option. No automatic fallback is performed.").setPositiveButton("Try compatibility",(a,b)->go.run()).setNegativeButton("Cancel",null).show();else go.run();
        },true);
        note(inspect,"SPP UUID: 00001101-0000-1000-8000-00805f9b34fb. A missing SDP result is inconclusive. BLE inspection below never writes printer data.");
        button(inspect,"Scan BLE for 8 seconds (optional)",this::scanBle,true);
        button(inspect,"Inspect selected BLE services (NO writes)",()->{
            BluetoothDevice d=selected();if(d==null||!permissions(false))return;
            runTask("BLE service inspection",()->{transport.inspectGatt(d);modeLabel="Disconnected";return null;});
        },true);
        LinearLayout guide=card("3  Guided next step / \u4e0b\u4e00\u6b65");
        guideText=text("Select a printer, then refresh this guide. No commands are sent automatically.",15,false);guide.addView(guideText);
        button(guide,"Refresh suggested next step",()->refreshGuide(false),true);
        button(guide,"Select suggested test (NO PRINT)",()->refreshGuide(true),true);
        note(guide,"Guidance uses this printer's observations only. Record each Test ID before continuing. A printed QR is not verified until its complete decoded text matches.");
        LinearLayout print=card("4  Preview, then send ONE test");
        tests=spinner(print,TESTS);
        note(print,"Raster width in dots (not a measured DPI or paper width):");
        widths=spinner(print,new String[]{"320", "160", "240", "384"});
        note(print,"Native QR module size in dots:");modules=spinner(print,new String[]{"4","3","5","6"});
        note(print,"Transmission pacing:");pacing=spinner(print,new String[]{"128 bytes / 20 ms", "128 bytes / 50 ms", "256 bytes / 20 ms"});
        custom=new EditText(this);custom.setSingleLine(true);custom.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);custom.setText(Protocol.SHORT_URL);custom.setHint("Custom TEST HTTPS URL, max 160 ASCII chars");print.addView(custom);lockViews.add(custom);
        button(print,"Use short demo URL",()->custom.setText(Protocol.SHORT_URL),true);
        button(print,"Use long demo URL",()->custom.setText(Protocol.LONG_URL),true);
        previewText=text("Start with A, then B. Do not advance while output is garbled.",14,false);print.addView(previewText);
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight(dp(320));print.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));preview.setVisibility(View.GONE);
        button(print,"Preview selected test (does not print)",()->showPreview(makePacket()),true);
        button(print,"Send ONE selected test...",this::confirmPrint,true);
        note(print,"C/D/J use Epson-style native QR. E/F/G/I/K use legacy GS v 0. H uses ESC * 24-dot columns. These are hypotheses to test, not promises of 58B3 compatibility.");
        button(print,"Optional DLE EOT 1 status probe...",()->{
            BluetoothDevice d=selected();if(d==null||!permissions(false))return;
            if(!transport.connectedTo(d)){toast("Connect to the selected printer first");return;}
            new AlertDialog.Builder(this).setTitle("Optional status probe").setMessage("Send only 10 04 01, then wait up to 2 seconds. No response does NOT mean the printer is broken. On timeout the socket closes before any more commands.").setPositiveButton("Query once",(a,b)->runTask("Status probe",()->{transport.queryStatus();return null;})).setNegativeButton("Cancel",null).show();
        },true);
        LinearLayout results=card("5  Record what actually happened");
        note(results,"Use another phone to scan printed QR without opening the URL. Compare the full decoded text with the test URL. Record the observation against the printed test ID.");
        button(results,"History / record physical result",this::recordObservation,true);
        button(results,"Export diagnostic JSON",this::exportReport,true);
        note(results,"Report excludes QR contents, MAC address, pairing PIN and credentials. It includes phone model, locally salted device ID, service UUIDs, test settings, status response bytes and your observations. Review before sharing.");
        LinearLayout log=card("Recent activity");logView=text(journal.recent(),12,false);logView.setTypeface(Typeface.MONOSPACE);logView.setTextIsSelectable(true);log.addView(logView);
        note(root,"Standalone lab only. No Hostinger login, no LensDrop queue, no USB tests, no BLE printing, no background jobs. Keep source and build report with your test results.");
    }
    private boolean permissions(boolean scanning){
        if(adapter==null){toast("This phone has no available Bluetooth adapter");return false;}
        List<String> need=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31){need.add(Manifest.permission.BLUETOOTH_CONNECT);need.add(Manifest.permission.BLUETOOTH_SCAN);}
        else if(scanning)need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        List<String> missing=new ArrayList<>();for(String s:need)if(checkSelfPermission(s)!=PackageManager.PERMISSION_GRANTED)missing.add(s);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),PERMISSIONS);return false;}
        if(!adapter.isEnabled()){toast("Enable Bluetooth in Android settings first");return false;}
        return true;
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){
        super.onRequestPermissionsResult(request,names,results);if(request==PERMISSIONS){
            boolean all=results.length>0;for(int r:results)all &= r==PackageManager.PERMISSION_GRANTED;
            event("PERMISSION",all?"Granted; press the required step again":"Denied; use app settings to allow Nearby devices / legacy scan location");
        }
    }
    private void refreshDevices(){
        if(!permissions(false))return;
        transport.disconnect();devices.clear();devices.addAll(adapter.getBondedDevices());
        devices.sort(Comparator.comparing(this::deviceName));updateDeviceList();event("DEVICE_LIST","paired_count="+devices.size()+"; nothing connected automatically");
    }
    private String deviceName(BluetoothDevice d){String n=d.getName();return n==null?"Unnamed device":n;}
    private void updateDeviceList(){
        List<String> names=new ArrayList<>();names.add("Choose your printer (no auto-connect)");
        for(BluetoothDevice d:devices){String a=d.getAddress();names.add(deviceName(d)+" / ..."+a.substring(Math.max(0,a.length()-5))+" / type "+d.getType());}
        deviceList.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
    }
    private BluetoothDevice selected(){int p=deviceList.getSelectedItemPosition();if(p<1||p>devices.size()){toast("Select your printer first");return null;}return devices.get(p-1);}
    private void refreshStatus(){
        if(destroyed)return;
        BluetoothDevice d=null;int p=deviceList==null?0:deviceList.getSelectedItemPosition();if(p>0&&p<=devices.size())d=devices.get(p-1);
        try{status.setText((busy.get()?"Working - ":"")+(d==null?"No printer selected":transport.connectedTo(d)?"SPP connected / "+transport.mode():"Disconnected / selected device only"));}
        catch(SecurityException e){status.setText("Bluetooth permission required");}
    }
    private void setLocked(boolean locked){for(View v:lockViews)v.setEnabled(!locked);refreshStatus();if(!locked)refreshGuide(false);}
    private void refreshGuide(boolean selectTest){
        if(destroyed||guideText==null)return;
        int n=deviceList==null?0:deviceList.getSelectedItemPosition();
        GuidePolicy.Advice a;
        try{
            if(n<1||n>devices.size())a=GuidePolicy.recommend(null,false,Collections.emptyList());
            else{
                BluetoothDevice d=devices.get(n-1);String tag=journal.deviceTag(d.getAddress());
                a=GuidePolicy.recommend(tag,transport.connectedTo(d),journal.guideEvidence(tag));
            }
            guideText.setText(a.code+(a.evidenceId.isEmpty()?"":" / "+a.evidenceId)+"\n"+a.message);
            if(selectTest){
                if(a.canSelect()&&tests!=null){tests.setSelection(a.testIndex);toast("Selected only. Preview, then Send ONE separately.");}
                else toast("No automatic next test. Follow the displayed check.");
            }
        }catch(SecurityException e){guideText.setText("Grant Nearby devices permission before checking printer-specific progress.");}
    }
    private void runTask(String label,Callable<Void> task){
        if(!busy.compareAndSet(false,true)){toast("One operation at a time");return;}
        int generation=operationGeneration.incrementAndGet();
        setLocked(true);transport.beginOperation();event("OPERATION",label);
        running=worker.submit(()->{
            try{if(generation==operationGeneration.get() && foreground)task.call();}catch(Exception e){event("ERROR",label+" / "+AttemptPolicy.safeError(e));}
            finally{busy.set(false);handler.post(()->{if(!destroyed){setLocked(false);if(logView!=null)logView.setText(journal.recent());}});}
        });
    }
    private void stopAll(String why){
        operationGeneration.incrementAndGet();stopScan();if(transport!=null)transport.disconnect();
        modeLabel="Disconnected";if(journal!=null&&!destroyed)event("STOP",why);if(status!=null)refreshStatus();if(guideText!=null)refreshGuide(false);
    }
    private void event(String kind,String detail){
        if(destroyed)return;
        journal.log(kind,detail);
        handler.post(()->{if(!destroyed){if(logView!=null)logView.setText(journal.recent());if(deviceList!=null)refreshStatus();}});
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showError(Exception e){toast(e instanceof IllegalArgumentException?e.getMessage():AttemptPolicy.safeError(e));event("ERROR",AttemptPolicy.safeError(e));}
    private static final class Packet {
        String id,method,settings,url;
        byte[] bytes;
        boolean[][] image;
    }
    private Packet makePacket()throws Exception{
        Packet p=new Packet();p.id="T-"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT);
        int n=tests.getSelectedItemPosition(),width=Integer.parseInt((String)widths.getSelectedItem()),module=Integer.parseInt((String)modules.getSelectedItem());
        p.method=TESTS[n];p.url=n==3||n==6?Protocol.LONG_URL:n>=9?Protocol.validUrl(custom.getText().toString()):Protocol.SHORT_URL;
        p.settings="requested_raster_width_dots="+width+"; native_module_dots="+module+"; ECC=M; no_measured_dpi; "+pacing.getSelectedItem();
        if(n==0||n==1)p.bytes=Protocol.text(p.id,n==1);
        else if(n==2||n==3||n==9){
            p.bytes=Protocol.concat(Protocol.bytes(27,64),Protocol.ascii("LAB "+p.id+"\nNATIVE QR\n"),Protocol.nativeQr(p.url,module));
            p.image=QrRenderer.create(p.url,320);
        }else{
            if(n==4)p.image=Protocol.calibration();
            else if(n==8)p.image=chineseBitmap();
            else p.image=QrRenderer.create(p.url,width);
            p.bytes=Protocol.imageJob(p.id,p.image,n==7);
        }
        if(n>1 && n!=4 && n!=8)p.settings+="; url_bytes="+p.url.length()+"; url_sha256="+Protocol.sha256(p.url.getBytes(StandardCharsets.US_ASCII));
        return p;
    }
    private boolean[][] chineseBitmap(){
        Bitmap b=Bitmap.createBitmap(320,104,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
        Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);pen.setColor(Color.BLACK);pen.setTextSize(26);
        c.drawText("\u9818\u76f8\u6e2c\u8a66 / Group 012",8,36,pen);pen.setTextSize(22);c.drawText("\u6383\u78bc\u9818\u53d6\u7167\u7247",8,76,pen);
        boolean[][] a=new boolean[104][320];for(int y=0;y<104;y++)for(int x=0;x<320;x++){int rgb=b.getPixel(x,y);a[y][x]=(Color.red(rgb)+Color.green(rgb)+Color.blue(rgb))<384;}
        b.recycle();return a;
    }
    private void showPreview(Packet p){
        previewText.setText(p.method+"\nTest ID: "+p.id+"\nBytes: "+p.bytes.length+"\n"+p.settings+(p.method.contains("QR")?"\nQR text (test only): "+p.url:"")+(p.method.contains("Native")?"\nPreview is QR data only, NOT proof of native printer layout.":""));
        if(p.image==null){preview.setVisibility(View.GONE);return;}
        int h=p.image.length,w=p.image[0].length;Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=p.image[y][x]?Color.BLACK:Color.WHITE;
        b.setPixels(pixels,0,w,0,0,w,h);preview.setImageBitmap(b);preview.setVisibility(View.VISIBLE);
    }
    private void confirmPrint()throws Exception{
        if(!permissions(false))return;BluetoothDevice d=selected();if(d==null)return;
        if(!transport.connectedTo(d)){toast("Connect the selected printer using SPP first");return;}
        Packet p=makePacket();showPreview(p);int pace=pacing.getSelectedItemPosition(),chunk=pace==2?256:128,delay=pace==1?50:20;
        new AlertDialog.Builder(this).setTitle("Send exactly ONE diagnostic test?")
            .setMessage(deviceName(d)+"\n"+p.method+"\n"+p.id+" / "+p.bytes.length+" bytes\nMay consume paper. If a test fails, do not blindly retry. No automatic replay. ESC tests may reset the volatile buffer.")
            .setNegativeButton("Cancel",null).setPositiveButton("Send one",(a,b)->runTask("Print test",()->{
                if(!transport.connectedTo(d))throw new java.io.IOException("Connection changed");
                journal.begin(p.id,p.method,journal.deviceTag(d.getAddress()),p.bytes,p.settings+"; "+transport.mode());
                try{int sent=transport.send(p.bytes,chunk,delay);journal.finish(p.id,AttemptPolicy.afterWrite(sent,p.bytes.length,true));event("SENT_UNCONFIRMED",p.id+"; bytes="+sent+"; record physical observation separately");}
                catch(Exception e){journal.finish(p.id,"outcome_unknown");throw e;}
                return null;
            })).show();
    }
    private void recordObservation(){
        List<String[]> rows=journal.attempts();if(rows.isEmpty()){toast("No attempted print tests yet");return;}
        String[] labels=new String[rows.size()];for(int i=0;i<rows.size();i++){String[] r=rows.get(i);labels[i]=r[0]+" | "+r[1]+"\n"+r[2]+" / "+r[3];}
        new AlertDialog.Builder(this).setTitle("Select printed test ID").setItems(labels,(a,index)->{
            String[] row=rows.get(index);boolean qr=row[1].contains("QR");
            String[] opts=qr?new String[]{"Not checked", "Paper output looks correct (QR NOT verified)", "QR decoded AND full text matches", "No paper output", "Partial / garbled / truncated output", "QR printed but cannot decode", "QR decoded but WRONG text", "Wrong printer received output"}:
                new String[]{"Not checked", "Paper output looks correct", "No paper output", "Partial / garbled / truncated output", "Wrong printer received output"};
            String[] vals=qr?new String[]{"not_checked","printed_ok","qr_decoded_match","no_output","partial_or_garbled","qr_unreadable","qr_decoded_mismatch","wrong_device"}:
                new String[]{"not_checked","printed_ok","no_output","partial_or_garbled","wrong_device"};
            new AlertDialog.Builder(this).setTitle(row[0]+" - your observation").setItems(opts,(d,k)->{journal.observe(row[0],vals[k]);event("OPERATOR_OBSERVATION",row[0]+" / "+vals[k]);refreshGuide(false);}).setNegativeButton("Cancel",null).show();
        }).setNegativeButton("Close",null).show();
    }
    private void exportReport(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"PrinterLab58-report-"+System.currentTimeMillis()+".json");startActivityForResult(intent,EXPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==EXPORT && result==RESULT_OK && data!=null && data.getData()!=null){
            pendingExport=data.getData();handler.post(this::finishPendingExport);
        }
    }
    private void finishPendingExport(){
        if(destroyed||!foreground||pendingExport==null)return;
        if(busy.get()){handler.postDelayed(this::finishPendingExport,150);return;}
        Uri uri=pendingExport;pendingExport=null;
        runTask("Export report",()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new java.io.IOException("Cannot write report");out.write(journal.report().getBytes(StandardCharsets.UTF_8));
            }
            event("EXPORT","Report saved through Android document picker");return null;
        });
    }
    private void scanBle(){
        if(!permissions(true))return;
        if(!busy.compareAndSet(false,true))return;
        transport.disconnect();setLocked(true);bleFound.clear();scanner=adapter.getBluetoothLeScanner();
        if(scanner==null){busy.set(false);setLocked(false);toast("BLE scan unavailable");return;}
        scanCallback=new ScanCallback(){
            @Override public void onScanResult(int type,ScanResult r){handler.post(()->{
                if(scanCallback==null||destroyed)return;BluetoothDevice d=r.getDevice();if(bleFound.size()<40)bleFound.put(d.getAddress(),d);
            });}
            @Override public void onScanFailed(int code){event("BLE_SCAN","failed_code="+code);handler.post(()->finishScan(false));}
        };
        try{scanner.startScan(scanCallback);}catch(RuntimeException e){stopScan();showError(e);return;}
        event("BLE_SCAN","8-second bounded scan; no payloads sent");
        scanStop=()->finishScan(true);handler.postDelayed(scanStop,8000);
    }
    private void stopScan(){
        if(scanStop!=null){handler.removeCallbacks(scanStop);scanStop=null;}
        if(scanner!=null&&scanCallback!=null){try{scanner.stopScan(scanCallback);}catch(SecurityException ignored){}scanCallback=null;scanner=null;busy.set(false);if(!destroyed&&deviceList!=null)setLocked(false);}
    }
    private void finishScan(boolean choose){
        stopScan();if(destroyed||!foreground)return;
        event("BLE_SCAN","unique_devices="+bleFound.size()+"; addresses not exported");
        if(!choose||bleFound.isEmpty())return;
        List<BluetoothDevice> found=new ArrayList<>(bleFound.values());String[] names=new String[found.size()];
        for(int i=0;i<found.size();i++){BluetoothDevice d=found.get(i);String addr=d.getAddress();names[i]=deviceName(d)+" / ..."+addr.substring(Math.max(0,addr.length()-5));}
        new AlertDialog.Builder(this).setTitle("Select your BLE printer only").setItems(names,(a,i)->{
            BluetoothDevice d=found.get(i);int pos=-1;for(int j=0;j<devices.size();j++)if(devices.get(j).getAddress().equals(d.getAddress()))pos=j;
            if(pos<0){devices.add(d);pos=devices.size()-1;}updateDeviceList();deviceList.setSelection(pos+1);
            event("BLE_SELECTED","Device selected only; use NO-WRITES inspection. BLE printing is not implemented.");
        }).setNegativeButton("Cancel",null).show();
    }
}
