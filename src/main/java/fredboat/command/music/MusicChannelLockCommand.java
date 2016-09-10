package fredboat.command.music;

import fredboat.commandmeta.abs.Command;
import fredboat.db.RedisCache;
import fredboat.db.RedisGuildCache;
import fredboat.util.DiscordUtil;
import net.dv8tion.jda.Permission;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.utils.PermissionUtil;

public class MusicChannelLockCommand extends Command {

    @Override
    public void onInvoke(Guild guild, TextChannel channel, User invoker, Message message, String[] args) {
        if(DiscordUtil.isUserBotCommander(guild, invoker) || PermissionUtil.checkPermission(guild, invoker, Permission.MANAGE_SERVER)){
            channel.sendTyping();
            RedisGuildCache rgc = RedisCache.getGuild(guild);
            if(rgc.isTextChannelMusicLocked(channel)){
                rgc.setChannelLock(null);
                channel.sendMessage(guild.getNicknameForUser(invoker) + ": Lock removed. You can now use music commands in any channel I can respond in.");
            } else {
                rgc.setChannelLock(channel);
                channel.sendMessage(guild.getNicknameForUser(invoker) + ": Music lock set. Music commands can only be invoked in this channel.");
            }
        } else {
            channel.sendMessage(guild.getNicknameForUser(invoker) + ": You need to be a Bot Commander or server manager to use this command.");
        }
    }
    
}
