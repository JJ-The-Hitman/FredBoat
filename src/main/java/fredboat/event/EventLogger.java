/*
 * The MIT License (MIT)
 * Copyright (c) 2016 Frederik Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package fredboat.event;

import fredboat.FredBoat;
import fredboat.util.DiscordUtil;
import net.dv8tion.jda.JDA;
import net.dv8tion.jda.MessageBuilder;
import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.events.ReadyEvent;
import net.dv8tion.jda.events.guild.GuildJoinEvent;
import net.dv8tion.jda.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.hooks.ListenerAdapter;

public class EventLogger extends ListenerAdapter {

    public final String logChannelId;
    public JDA jda;

    public EventLogger(String logChannelId) {
        this.logChannelId = logChannelId;
        Runtime.getRuntime().addShutdownHook(new Thread(ON_SHUTDOWN));
    }

    private void send(Message msg) {
        send(msg.getRawContent());
    }

    private void send(String msg) {
        DiscordUtil.sendShardlessMessage(jda, logChannelId,
                "["
                + FredBoat.shardId
                + ":"
                + FredBoat.numShards
                + "] "
                + msg
        );
    }

    @Override
    public void onReady(ReadyEvent event) {
        jda = event.getJDA();
        send(new MessageBuilder()
                .appendString("[:rocket:] Received ready event.")
                .build()
        );
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        send(
                "[:white_check_mark:] Joined guild `" + event.getGuild() + "`. Users: `" + event.getGuild().getUsers().size() + "`."
        );
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        send(
                "[:x:] Left guild `" + event.getGuild() + "`. Users: `" + event.getGuild().getUsers().size() + "`."
        );
    }

    private final Runnable ON_SHUTDOWN = () -> {
        Runtime rt = Runtime.getRuntime();
        if(FredBoat.shutdownCode != FredBoat.UNKNOWN_SHUTDOWN_CODE){
            send("[:door:] Exiting with code " + FredBoat.shutdownCode + ".");
        } else {
            send("[:door:] Exiting with unknown code.");
        }
    };

}
