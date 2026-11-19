package hk.luce.printerlab;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Private bounded audit journal. Does not store QR payloads, MAC addresses or photo files. */
public final class Journal extends SQLiteOpenHelper {
    private final String salt;
    public Journal(Context context) {
        super(context,"printer-lab.sqlite",null,1);
        setWriteAheadLoggingEnabled(true);
        android.content.SharedPreferences p=context.getSharedPreferences("private",Context.MODE_PRIVATE);
        String existing=p.getString("salt",null);
        if(existing==null){existing=UUID.randomUUID().toString();p.edit().putString("salt",existing).commit();}
        salt=existing;
        getWritableDatabase().execSQL("UPDATE attempts SET state='interrupted_unknown' WHERE state='sending'");
    }
    @Override public void onConfigure(SQLiteDatabase db){ db.execSQL("PRAGMA synchronous=FULL"); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY, at_ms INTEGER NOT NULL, kind TEXT NOT NULL, detail TEXT NOT NULL)");
        db.execSQL("CREATE TABLE attempts(id TEXT PRIMARY KEY, at_ms INTEGER NOT NULL, method TEXT NOT NULL, device_tag TEXT NOT NULL, packet_bytes INTEGER NOT NULL, packet_sha256 TEXT NOT NULL, settings TEXT NOT NULL, state TEXT NOT NULL, observation TEXT NOT NULL DEFAULT 'not_checked')");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int a,int b){throw new IllegalStateException("Unsupported journal migration");}
    public String deviceTag(String address){return Protocol.sha256((salt+address).getBytes(StandardCharsets.UTF_8)).substring(0,12);}
    public synchronized void log(String kind,String detail) {
        ContentValues v=new ContentValues();v.put("at_ms",System.currentTimeMillis());v.put("kind",kind);v.put("detail",detail);
        SQLiteDatabase db=getWritableDatabase();db.insertOrThrow("events",null,v);
        db.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 2000)");
    }
    public synchronized void begin(String id,String method,String device,byte[] packet,String settings) {
        ContentValues v=new ContentValues();v.put("id",id);v.put("at_ms",System.currentTimeMillis());v.put("method",method);v.put("device_tag",device);
        v.put("packet_bytes",packet.length);v.put("packet_sha256",Protocol.sha256(packet));v.put("settings",settings);v.put("state","sending");
        SQLiteDatabase db=getWritableDatabase();db.insertOrThrow("attempts",null,v);
        db.execSQL("DELETE FROM attempts WHERE id NOT IN (SELECT id FROM attempts ORDER BY at_ms DESC LIMIT 500)");
    }
    public synchronized void finish(String id,String state) {
        if(!state.equals("sent_unconfirmed")&&!state.equals("outcome_unknown"))throw new IllegalArgumentException("State");
        ContentValues v=new ContentValues();v.put("state",state);getWritableDatabase().update("attempts",v,"id=?",new String[]{id});
    }
    public synchronized void observe(String id,String value) {
        ContentValues v=new ContentValues();v.put("observation",AttemptPolicy.observation(value));getWritableDatabase().update("attempts",v,"id=?",new String[]{id});
    }
    public synchronized List<String[]> attempts() {
        List<String[]> r=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,method,state,observation FROM attempts ORDER BY rowid DESC LIMIT 500",null)){
            while(c.moveToNext())r.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3)});
        }return r;
    }
    public synchronized List<GuidePolicy.Evidence> guideEvidence(String tag) {
        List<GuidePolicy.Evidence> r=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,method,device_tag,state,observation,rowid FROM attempts WHERE device_tag=? ORDER BY rowid DESC LIMIT 500",new String[]{tag})){
            while(c.moveToNext())r.add(new GuidePolicy.Evidence(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5)));
        }return r;
    }
    public synchronized String recent() {
        StringBuilder r=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT kind,detail FROM events ORDER BY id DESC LIMIT 18",null)){
            while(c.moveToNext())r.append(c.getString(0)).append(": ").append(c.getString(1)).append('\n');
        }return r.toString();
    }
    private JSONArray rows(String table,String order,int limit)throws org.json.JSONException {
        JSONArray rows=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM "+table+" ORDER BY "+order+" DESC LIMIT "+limit,null)){
            while(c.moveToNext()){
                JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++){
                    if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)o.put(c.getColumnName(i),c.getLong(i));
                    else o.put(c.getColumnName(i),c.getString(i));
                }rows.put(o);
            }
        }return rows;
    }
    public synchronized String report()throws org.json.JSONException {
        JSONObject r=new JSONObject();r.put("schema","luce.printerlab.report/2");r.put("app_version",BuildConfig.VERSION_NAME);
        r.put("created_at_ms",System.currentTimeMillis());r.put("android_sdk",Build.VERSION.SDK_INT);
        r.put("phone_manufacturer",Build.MANUFACTURER);r.put("phone_model",Build.MODEL);
        r.put("candidate_label","Yokoscan 58B3 / T58_F82E (user-supplied self-test; not protocol-certified)");
        r.put("candidate_firmware","H20_2023_V1.0-8009");
        r.put("scope","Foreground manual diagnostic. Sent does not mean physically printed. No automatic retry.");
        r.put("redaction","No MAC, PIN, QR payload, cloud credential or photo content exported. Device tags salted locally. Review phone model/status bytes before sharing.");
        r.put("guidance_scope","Per selected printer, latest observations only; not certification. Original report/1 columns retained; new observation categories added.");
        r.put("attempts",rows("attempts","at_ms",500));r.put("events",rows("events","id",2000));
        return r.toString(2);
    }
}
