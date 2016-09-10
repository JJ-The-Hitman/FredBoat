package fredboat;

import fredboat.command.maintenance.UpdateCommand;
import fredboat.agent.CarbonAgent;
import fredboat.agent.CarbonitexAgent;
import fredboat.agent.MusicGC;
import fredboat.audio.MusicPersistenceHandler;
import fredboat.audio.PlayerRegistry;
import fredboat.audio.queue.MusicQueueProcessor;
import fredboat.command.fun.*;
import fredboat.command.util.*;
import fredboat.command.maintenance.*;
import fredboat.command.music.*;
import fredboat.commandmeta.CommandRegistry;
import fredboat.db.RedisCache;
import fredboat.event.EventListenerBoat;
import fredboat.event.EventListenerSelf;
import fredboat.event.EventLogger;
import fredboat.util.BotConstants;
import fredboat.util.DiscordUtil;
import fredboat.util.SimpleLogToSLF4JAdapter;
import frederikam.jca.JCA;
import frederikam.jca.JCABuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.JDA;
import net.dv8tion.jda.JDABuilder;
import net.dv8tion.jda.JDAInfo;
import net.dv8tion.jda.client.JDAClientBuilder;
import net.dv8tion.jda.entities.impl.JDAImpl;
import net.dv8tion.jda.events.ReadyEvent;
import net.dv8tion.jda.utils.SimpleLog;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FredBoat {

    private static final Logger log = LoggerFactory.getLogger(FredBoat.class);
    
    public static String otherBotId = "";

    public static int scopes = 0;
    public static volatile JDA jdaBot;
    public static volatile JDA jdaSelf;
    public static JCA jca;
    public static final long START_TIME = System.currentTimeMillis();
    public static final String ACCOUNT_TOKEN_KEY = BotConstants.IS_BETA ? "tokenBeta" : "tokenProduction";
    private static String accountToken;
    public static String mashapeKey;

    public static String MALPassword;
    public static String googleServerKey = "";

    public static String myUserId = "";

    public static int readyEvents = 0;
    public static int readyEventsRequired = 0;

    public static void main(String[] args) throws LoginException, IllegalArgumentException, InterruptedException, IOException {
        //Attach log adapter
        SimpleLog.addListener(new SimpleLogToSLF4JAdapter());

        //Make JDA not print to console, we have Logback for that
        SimpleLog.LEVEL = SimpleLog.Level.OFF;
        
        try {
            scopes = Integer.parseInt(args[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            log.info("Invalid arguments: " + args + ", defaulting to scopes 0x101");
            scopes = 0x100;
        }
        log.info("Starting with scopes:"
                + "\n\tMain: " + ((scopes & 0x100) == 0x100)
                + "\n\tMusic: " + ((scopes & 0x010) == 0x010)
                + "\n\tSelf: " + ((scopes & 0x001) == 0x001));

        //Determine what the "other bot" is
        otherBotId = ((scopes & 0x010) == 0x010) ? BotConstants.MAIN_BOT_ID : BotConstants.MUSIC_BOT_ID;

        //Load credentials file
        InputStream is = new FileInputStream(new File("./credentials.json"));
        //InputStream is = instance.getClass().getClassLoader().getResourceAsStream("credentials.json");
        Scanner scanner = new Scanner(is);
        JSONObject credsjson = new JSONObject(scanner.useDelimiter("\\A").next());

        //accountEmail = credsjson.getString(ACCOUNT_EMAIL_KEY);
        //accountPassword = credsjson.getString(ACCOUNT_PASSWORD_KEY);
        accountToken = credsjson.getString(ACCOUNT_TOKEN_KEY);
        mashapeKey = credsjson.getString("mashapeKey");
        String clientToken = credsjson.getString("clientToken");
        MALPassword = credsjson.getString("malPassword");
        String carbonHost = credsjson.optString("carbonHost");
        googleServerKey = credsjson.optString("googleServerKey");

        if (credsjson.has("scopePasswords")) {
            JSONObject scopePasswords = credsjson.getJSONObject("scopePasswords");
            for (String k : scopePasswords.keySet()) {
                scopePasswords.put(k, scopePasswords.getString(k));
            }
        }

        scanner.close();

        //Initialise event listeners
        EventListenerBoat listenerBot = new EventListenerBoat(scopes & 0x110, BotConstants.DEFAULT_BOT_PREFIX);
        EventListenerSelf listenerSelf = new EventListenerSelf(scopes & 0x001, BotConstants.DEFAULT_SELF_PREFIX);

        /* Init JDA */
        //Doing increments here because concurrency
        if ((scopes & 0x110) != 0) {
            readyEventsRequired++;
        }

        if ((scopes & 0x001) != 0) {
            readyEventsRequired++;
        }

        if ((scopes & 0x110) != 0) {
            jdaBot = new JDABuilder()
                    .addListener(listenerBot)
                    .addListener(new EventLogger("216689009110417408"))
                    .setBotToken(accountToken)
                    .setBulkDeleteSplittingEnabled(false)
                    .buildAsync();
        }

        if ((scopes & 0x001) != 0) {
            jdaSelf = new JDAClientBuilder()
                    .addListener(listenerSelf)
                    .setClientToken(clientToken)
                    .buildAsync();
        }

        /* JDA initialising */
        log.info("JDA version:\t" + JDAInfo.VERSION);

        //Initialise JCA
        String cbUser = credsjson.getString("cbUser");
        String cbKey = credsjson.getString("cbKey");
        jca = new JCABuilder().setKey(cbKey).setUser(cbUser).buildBlocking();

        //Redis
        String redisHost = credsjson.getString("redisHost");
        String redisPassword = credsjson.getString("redisPassword");
        RedisCache.init(redisHost, redisPassword);

        if (!BotConstants.IS_BETA) {
            CarbonitexAgent carbonitexAgent = new CarbonitexAgent(jdaBot, credsjson.getString("carbonKey"));
            carbonitexAgent.setDaemon(true);
            carbonitexAgent.start();
        }

        if (!carbonHost.equals("")) {
            //Determine metric name
            String metricName = "beta";
            if (!BotConstants.IS_BETA) {
                metricName = DiscordUtil.isMusicBot() ? "music" : "production";
            }

            CarbonAgent carbonAgent = new CarbonAgent(jdaBot, carbonHost, metricName, !BotConstants.IS_BETA);
            carbonAgent.setDaemon(true);
            carbonAgent.start();
            log.info("Started reporting to carbon-cache at " + carbonHost + " with metric name " + metricName + ".");
        } else {
            log.info("No carbon host configured. Skipping carbon daemon.");
        }

        MusicQueueProcessor mqp = new MusicQueueProcessor();
        mqp.setDaemon(true);
        mqp.start();

        MusicGC mgc = new MusicGC(jdaBot);
        mgc.setDaemon(true);
        mgc.start();
    }

    public static void init(ReadyEvent event) {
        readyEvents = readyEvents + 1;

        log.info("INIT: " + readyEvents + "/" + readyEventsRequired);

        if (readyEvents < readyEventsRequired) {
            return;
        }

        //Init music system
        PlayerRegistry.init(jdaBot);

        /*for (Guild guild : jdaBot.getGuilds()) {
            log.info(guild.getName());

            for (TextChannel channel : guild.getTextChannels()) {
                log.info("\t" + channel.getName());
            }
        }*/
        //Commands
        CommandRegistry.registerCommand(0x110, "help", new HelpCommand());
        CommandRegistry.registerCommand(0x101, "version", new VersionCommand());
        CommandRegistry.registerCommand(0x101, "say", new SayCommand());
        CommandRegistry.registerCommand(0x101, "uptime", new StatsCommand());
        CommandRegistry.registerAlias("uptime", "stats");
        CommandRegistry.registerCommand(0x101, "exit", new ExitCommand());
        CommandRegistry.registerCommand(0x101, "avatar", new AvatarCommand());
        CommandRegistry.registerCommand(0x101, "test", new TestCommand());
        CommandRegistry.registerCommand(0x101, "lua", new LuaCommand());
        CommandRegistry.registerCommand(0x101, "brainfuck", new BrainfuckCommand());
        CommandRegistry.registerCommand(0x101, "joke", new JokeCommand());
        CommandRegistry.registerCommand(0x101, "leet", new LeetCommand());
        CommandRegistry.registerAlias("leet", "1337");
        CommandRegistry.registerAlias("leet", "l33t");
        CommandRegistry.registerCommand(0x101, "riot", new RiotCommand());
        CommandRegistry.registerCommand(0x101, "update", new UpdateCommand());
        CommandRegistry.registerCommand(0x101, "restart", new RestartCommand());
        CommandRegistry.registerCommand(0x101, "find", new FindCommand());
        CommandRegistry.registerCommand(0x101, "dance", new DanceCommand());
        CommandRegistry.registerCommand(0x101, "eval", new EvalCommand());
        CommandRegistry.registerCommand(0x101, "s", new TextCommand("¯\\_(ツ)_/¯"));
        CommandRegistry.registerAlias("s", "shrug");
        CommandRegistry.registerCommand(0x101, "lenny", new TextCommand("( ͡° ͜ʖ ͡°)"));
        CommandRegistry.registerCommand(0x101, "useless", new TextCommand("This command is useless."));
        CommandRegistry.registerCommand(0x101, "clear", new ClearCommand());
        CommandRegistry.registerCommand(0x101, "talk", new TalkCommand());
        CommandRegistry.registerCommand(0x101, "dump", new DumpCommand());
        CommandRegistry.registerCommand(0x101, "mal", new MALCommand());

        /* Music commands */
        CommandRegistry.registerCommand(0x010, "mexit", new ExitCommand());
        CommandRegistry.registerCommand(0x010, "mrestart", new RestartCommand());
        CommandRegistry.registerCommand(0x010, "mstats", new StatsCommand());
        CommandRegistry.registerCommand(0x010, "play", new PlayCommand());
        CommandRegistry.registerCommand(0x010, "minfo", new MusicInfoCommand());
        CommandRegistry.registerCommand(0x010, "meval", new EvalCommand());
        CommandRegistry.registerCommand(0x010, "skip", new SkipCommand());
        CommandRegistry.registerCommand(0x010, "join", new JoinCommand());
        CommandRegistry.registerCommand(0x010, "nowplaying", new NowplayingCommand());
        CommandRegistry.registerCommand(0x010, "leave", new LeaveCommand());
        CommandRegistry.registerCommand(0x010, "list", new ListCommand());
        CommandRegistry.registerCommand(0x010, "mupdate", new UpdateCommand());
        CommandRegistry.registerCommand(0x010, "select", new SelectCommand());
        CommandRegistry.registerCommand(0x010, "stop", new StopCommand());
        CommandRegistry.registerCommand(0x010, "pause", new PauseCommand());
        CommandRegistry.registerCommand(0x010, "unpause", new UnpauseCommand());
        CommandRegistry.registerCommand(0x010, "getid", new GetIdCommand());
        CommandRegistry.registerCommand(0x010, "shuffle", new ShuffleCommand());
        CommandRegistry.registerCommand(0x010, "repeat", new RepeatCommand());
        CommandRegistry.registerCommand(0x010, "volume", new VolumeCommand());
        CommandRegistry.registerCommand(0x010, "musiclock", new MusicChannelLockCommand());
        CommandRegistry.registerCommand(0x010, "playerdebug", new PlayerDebugCommand());

        /* Other Anime Discord or Sergi memes */
        CommandRegistry.registerCommand(0x101, "ram", new RemoteFileCommand("http://imgur.com/jeGVLk3"));
        CommandRegistry.registerCommand(0x101, "welcome", new RemoteFileCommand("http://i.imgur.com/yjpmmBk.gif"));
        CommandRegistry.registerCommand(0x101, "rude", new RemoteFileCommand("http://i.imgur.com/pUn7ijx.png"));
        CommandRegistry.registerCommand(0x101, "fuck", new RemoteFileCommand("http://i.imgur.com/1bllKNh.png"));
        CommandRegistry.registerCommand(0x101, "idc", new RemoteFileCommand("http://i.imgur.com/0ZPjpNg.png"));
        CommandRegistry.registerCommand(0x101, "beingraped", new RemoteFileCommand("http://i.imgur.com/dPsYRYV.png"));
        CommandRegistry.registerCommand(0x101, "anime", new RemoteFileCommand("https://cdn.discordapp.com/attachments/132490115137142784/177751190333816834/animeeee.png"));
        CommandRegistry.registerCommand(0x101, "wow", new RemoteFileCommand("http://img.prntscr.com/img?url=http://i.imgur.com/aexiMAG.png"));
        CommandRegistry.registerCommand(0x101, "what", new RemoteFileCommand("http://i.imgur.com/CTLraK4.png"));
        CommandRegistry.registerCommand(0x101, "pun", new RemoteFileCommand("http://i.imgur.com/2hFMrjt.png"));
        CommandRegistry.registerCommand(0x101, "die", new RemoteFileCommand("http://nekomata.moe/i/k2B0f6.png"));
        CommandRegistry.registerCommand(0x101, "stupid", new RemoteFileCommand("http://nekomata.moe/i/c056y7.png"));
        CommandRegistry.registerCommand(0x101, "cancer", new RemoteFileCommand("http://puu.sh/oQN2j/8e09872842.jpg"));
        CommandRegistry.registerCommand(0x101, "stupidbot", new RemoteFileCommand("https://cdn.discordapp.com/attachments/143976784545841161/183171963399700481/unknown.png"));
        CommandRegistry.registerCommand(0x101, "escape", new RemoteFileCommand("http://i.imgur.com/kk7Zu3C.png"));
        CommandRegistry.registerCommand(0x101, "explosion", new RemoteFileCommand("https://cdn.discordapp.com/attachments/143976784545841161/182893975965794306/megumin7.gif"));
        CommandRegistry.registerCommand(0x101, "gif", new RemoteFileCommand("https://cdn.discordapp.com/attachments/132490115137142784/182907929765085185/spacer.gif"));
        CommandRegistry.registerCommand(0x101, "noods", new RemoteFileCommand("http://i.imgur.com/CUE3gm2.png"));
        CommandRegistry.registerCommand(0x101, "internetspeed", new RemoteFileCommand("http://www.speedtest.net/result/5529046933.png"));
        CommandRegistry.registerCommand(0x101, "hug", new RemoteFileCommand("http://i.imgur.com/U2l7mnr.gif"));

        CommandRegistry.registerCommand(0x101, "github", new TextCommand("https://github.com/Frederikam"));
        CommandRegistry.registerCommand(0x101, "repo", new TextCommand("https://github.com/Frederikam/FredBoat"));

        String[] pats = {
            "http://i.imgur.com/wF1ohrH.gif",
            "http://cdn.photonesta.com/images/i.imgur.com/I3yvqFL.gif",
            "http://i4.photobucket.com/albums/y131/quentinlau/Blog/sola-02-Large15.jpg",
            "http://i.imgur.com/OYiSZWX.gif",
            "http://i.imgur.com/tmidE9Q.gif",
            "http://i.imgur.com/CoW20gH.gif",
            "http://31.media.tumblr.com/e759f2da1f07de37832fc8269e99f1e7/tumblr_n3w02z954N1swm6rso1_500.gif",
            "https://media1.giphy.com/media/ye7OTQgwmVuVy/200.gif",
            "http://data.whicdn.com/images/224314340/large.gif",
            "http://i.imgur.com/BNiNMWC.gifv",
            "http://i.imgur.com/9q6fkSK.jpg",
            "http://i.imgur.com/eOJlnwP.gif",
            "http://i.imgur.com/i7bklkm.gif",
            "http://i.imgur.com/fSDbKwf.jpg",
            "https://66.media.tumblr.com/ec7472fef28b2cdf394dc85132c22ed8/tumblr_mx1asbwrBv1qbvovho1_500.gif",};
        CommandRegistry.registerCommand(0x101, "pat", new PatCommand(pats));

        String[] facedesk = {
            "https://45.media.tumblr.com/tumblr_lpzn2uFp4D1qew6kmo1_500.gif",
            "http://i862.photobucket.com/albums/ab181/Shadow_Milez/Animu/kuroko-facedesk.gif",
            "http://2.bp.blogspot.com/-Uw0i2Xv8r-M/UhyYzSHIiCI/AAAAAAAAAdg/hcI1-V7Y3A4/s1600/facedesk.gif",
            "https://67.media.tumblr.com/dfa4f3c1b65da06d76a271feef0d08f0/tumblr_inline_o6zkkh6VsK1u293td_500.gif",
            "http://stream1.gifsoup.com/webroot/animatedgifs/57302_o.gif",
            "http://img.neoseeker.com/mgv/59301/301/26/facedesk_display.gif"
        };

        CommandRegistry.registerCommand(0x101, "facedesk", new FacedeskCommand(facedesk));

        String[] roll = {
            "https://media.giphy.com/media/3xz2BCBXokf7rag0Ba/giphy.gif",
            "http://i.imgur.com/IWQZaHD.gif",
            "https://warosu.org/data/cgl/img/0077/57/1408042492433.gif",
            "https://media.giphy.com/media/tso0dniqIDWwg/giphy.gif",
            "http://s19.postimg.org/lg5x9zx8z/Hakase_Roll_anime_32552527_500_282.gif",
            "http://i.imgur.com/UJxrB.gif",
            "http://25.media.tumblr.com/tumblr_m4k42bwzNy1qj0i6io1_500.gif",
            "http://i.giphy.com/3o6LXfWUBTdBcccgSc.gif",
            "http://66.media.tumblr.com/23dec349d26317df439099fdcb4c75a4/tumblr_mld6epWdgR1riizqco1_500.gif",
            "https://images-2.discordapp.net/eyJ1cmwiOiJodHRwOi8vZmFybTguc3RhdGljZmxpY2tyLmNvbS83NDUxLzEyMjY4NDM2MjU1XzgwZGIxOWNlOGZfby5naWYifQ.1e8OKozMAx22ZGELeNzRkqT3v-Q.gif",
            "https://images-1.discordapp.net/eyJ1cmwiOiJodHRwOi8vaS5pbWd1ci5jb20vS2VHY1lYSi5naWYifQ.4dCItRLO5l91JuDw-8ls-fi8wWc.gif",
            "http://i.imgur.com/s2TL7A8.gif",
            "http://i.imgur.com/vqTAjp5.gif",
            "https://data.desustorage.org/a/image/1456/58/1456582568150.gif"

        };

        CommandRegistry.registerCommand(0x101, "roll", new RollCommand(roll));

        MusicPersistenceHandler.reloadPlaylists();
    }

    public static void shutdown(int code) {
        log.info("Shutting down with exit code " + code);

        try {
            MusicPersistenceHandler.handlePreShutdown(code);
        } catch (Exception e) {
            log.info("Critical error while handling music persistence: ");
            e.printStackTrace();
        }

        for (Object listener : ((JDAImpl) jdaBot).getEventManager().getRegisteredListeners()) {
            if (listener instanceof EventLogger) {
                ((EventLogger) listener).onExit(code);
            }
        }

        //jdaBot.shutdown(true);
        //jdaSelf.shutdown(true);
        System.exit(code);
    }
}
