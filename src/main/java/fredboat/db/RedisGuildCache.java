package fredboat.db;

import fredboat.util.BotConstants;
import java.util.Map;
import java.util.function.Consumer;
import net.dv8tion.jda.entities.TextChannel;
import org.json.JSONArray;
import org.json.JSONObject;

public class RedisGuildCache {

    public final String guild;
    private Map<String, String> data = null;

    private long lastUpdateMs = 0;
    protected static final long CACHE_TIMEOUT = 5 * 60 * 1000;

    protected RedisGuildCache(String guild) {
        this.guild = guild;
    }

    public Map<String, String> getData() {
        ensureRecentData();
        return data;
    }
    
    public JSONObject getSettings() {
        String raw = getData().get("settings");
        if(raw != null){
            return new JSONObject(raw);
        } else {
            return new JSONObject();
        }
    }
    
    public String getPrefix(){
        Map<String, String> d = getData();
        return d.getOrDefault("prefix", BotConstants.DEFAULT_BOT_PREFIX);
    }

    private void ensureRecentData() {
        if (System.currentTimeMillis() - lastUpdateMs > CACHE_TIMEOUT) {
            query();
        }
    }

    private void query() {
        data = RedisCache.jedis.hgetAll("guild:" + guild);
    }
    
    /* READ - Helper functions */
    
    public boolean isTextChannelMusicLocked(TextChannel tc){
        JSONObject settings = getSettings();
        
        if(settings.has("musicLockAllowedChannels")){
            JSONArray allowed = settings.getJSONArray("musicLockAllowedChannels");
            
            if(allowed.length() == 0){
                return false;
            }
            
            for(Object str : allowed){
                if(str.equals(tc.getId())){
                    return false;
                }
            }
            
            //Only if there are values and they don't match our given channel
            return true;
        }
        
        return false;
    }

}
