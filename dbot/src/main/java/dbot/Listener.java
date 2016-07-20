package dbot;

import java.io.FileNotFoundException;

import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.Role;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.events.ReadyEvent;
import net.dv8tion.jda.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.hooks.ListenerAdapter;
import provider.Statistics;
import structs.DiscordInfo;

public class Listener extends ListenerAdapter {
	private Commands commands;
	private Karma karma;
	private Users users;
	static final String VERSION_NUMBER = "1.1.3_20";
	
	public Listener() {
		this.commands = new Commands();
		this.karma = new Karma();
		this.users = new Users();
	}
	
	@Override
	public void onReady(ReadyEvent event) {
		System.out.println("[Info] Listener ready!");
		System.out.println("[Info] Connected to:");
		for (Guild guild : event.getJDA().getGuilds()) {
			System.out.println("	" + guild.getName());
		}

		new Users().startUserCheck(event.getJDA());
		Statistics stats = Statistics.getInstance();
		stats.connect(event.getJDA());
		
		event.getJDA().getAccountManager().setGame(new DiscordInfo().getGame());
	}
	
	@Override
	public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
		System.out.printf("[PM] %s: %s\n", 	event.getAuthor().getUsername(),
											event.getMessage().getContent());
		
		//Check for command
				if (event.getMessage().getContent().startsWith("/") && !event.getAuthor().equals(event.getJDA().getSelfInfo())) {
					String content = event.getMessage().getContent();
					String commandName = content.replaceFirst("/", "").split(" ")[0];
					String[] args = {};
					if (content.replaceFirst("/" + commandName, "").trim().length() > 0) {
						args = content.replaceFirst("/" + commandName, "").trim().split(",");
						for (int i = 0; i < args.length; i++)
							args[i] = args[i].trim();
					}
					
					event.getChannel().sendTyping();
					if (commands.pmCommands.containsKey(commandName))
						commands.pmCommands.get(commandName).runCommand(event, args);
				}
	}
	
	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent  event) {
		System.out.printf("[%s][%s] %s: %s\n", 	event.getGuild().getName(),
												event.getChannel().getName(),
												event.getAuthor().getUsername(),
												event.getMessage().getContent());
		
		//Check for command
		if (event.getMessage().getContent().startsWith("/") && !event.getAuthor().isBot()) {
			String content = event.getMessage().getContent();
			String commandName = content.replaceFirst("/", "").split(" ")[0];
			String[] args = {};
			if (content.replaceFirst("/" + commandName, "").trim().length() > 0) {
				args = content.replaceFirst("/" + commandName, "").trim().split(",");
				for (int i = 0; i < args.length; i++)
					args[i] = args[i].trim();
			}
			
			if (commands.guildCommands.containsKey(commandName)) {
				event.getChannel().sendTyping();
				Statistics.getInstance().logCommandReceived(commandName);
				commands.guildCommands.get(commandName).runCommand(event, args);
			}
		}
		
		//Check for karma
		if ((event.getMessage().getContent().toLowerCase().contains("thanks")
				|| event.getMessage().getContent().toLowerCase().contains("thank you")
				|| event.getMessage().getContent().toLowerCase().contains("cheers"))
				&& !event.getMessage().getMentionedUsers().isEmpty() && !event.getAuthor().isBot())
			karma.generateNew(event);
		
		//Keep the spam in check
		users.newMessage(event);
		
		Statistics.getInstance().logMessage(event);
	}
	
	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		DiscordInfo info = new DiscordInfo();
		try {
			TextChannel channel = event.getJDA().getTextChannelById(info.getWelcomeChannelID());
			channel.sendMessageAsync(info.getNewMemberInfo()
					.replaceAll("@<user>", event.getUser().getAsMention())
					.replaceAll("<user>", event.getUser().getUsername()), null);
		} catch (FileNotFoundException e) {
			event.getGuild().getPublicChannel().sendMessageAsync("[Error] Couldn't find the new member message, sorry. ", null);
		}
	}

	@Override
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		for (Role role : event.getRoles()) {
			if (role.getName().equals("Squire"))
				Users.squireNew(event.getUser());
		}
	}
	
	@Override
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		for (Role role : event.getRoles()) {
			if (role.getName().equals("Squire"))
				Users.squireRemoved(event.getUser());
		}
	}
	
}
