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

package fredboat.command.music;

import fredboat.FredBoat;
import fredboat.audio.GuildPlayer;
import fredboat.audio.PlayerRegistry;
import fredboat.commandmeta.abs.Command;
import fredboat.commandmeta.abs.IMusicCommand;
import fredboat.util.BotConstants;
import net.dv8tion.jda.Permission;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.utils.PermissionUtil;

public class StopCommand extends Command implements IMusicCommand {

    @Override
    public void onInvoke(Guild guild, TextChannel channel, User invoker, Message message, String[] args) {
        if (PermissionUtil.checkPermission(guild, invoker, Permission.MESSAGE_MANAGE) || invoker.getId().equals(BotConstants.OWNER_ID)) {
            GuildPlayer player = PlayerRegistry.get(guild.getId());
            player.setCurrentTC(channel);
            int count = player.getRemainingTracks().size();

            player.clear();
            player.skip();

            switch (count) {
                case 0:
                    channel.sendMessage("The queue was already empty.");
                    break;
                case 1:
                    channel.sendMessage("The queue has been emptied, `1` song has been removed.");
                    break;
                default:
                    channel.sendMessage("The queue has been emptied, `" + count + "` songs have been removed.");
                    break;
            }
            player.leaveVoiceChannelRequest(channel, true);
        } else {
            channel.sendMessage("In order to prevent abuse, this command is only available to those who can manage messages.");
        }
    }

}
