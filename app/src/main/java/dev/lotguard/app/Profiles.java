package dev.lotguard.app;
import android.content.Context;
import org.json.*;
import java.util.*;
final class Profiles {
    static List<Detector.Profile> load(Context c) {
        List<Detector.Profile> items=new ArrayList<>();
        try {
            JSONArray arr=new JSONArray(c.getSharedPreferences("profiles",0).getString("items","[]"));
            for(int i=0;i<arr.length();i++){JSONArray p=arr.getJSONArray(i);
                items.add(new Detector.Profile(p.getString(0),p.getString(1),p.getString(2),p.getString(3)));}
        } catch(JSONException ignored) { items.clear(); }
        return items;
    }
    static void save(Context c,List<Detector.Profile> items) {
        JSONArray arr=new JSONArray();
        for(Detector.Profile p:items) arr.put(new JSONArray(Arrays.asList(p.login(),p.server(),p.symbol(),p.preset())));
        c.getSharedPreferences("profiles",0).edit().putString("items",arr.toString()).apply();
    }
}
