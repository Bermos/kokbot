package dbot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;

import structs.DiscordInfo;
import structs.EDSystem;
import net.dv8tion.jda.entities.Message.Attachment;
import net.dv8tion.jda.entities.Role;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.utils.AvatarUtil;
import net.dv8tion.jda.utils.AvatarUtil.Avatar;
import provider.Statistics;

interface PMCommand {
	void runCommand(PrivateMessageReceivedEvent event, String[] args);
}

interface GuildCommand {
	void runCommand(GuildMessageReceivedEvent event, String[] args);
	String getHelp(GuildMessageReceivedEvent event);
}

public class Commands {
	public Map<String, PMCommand> pmCommands = new LinkedHashMap<String, PMCommand>();
	public Map<String, GuildCommand> guildCommands = new LinkedHashMap<String, GuildCommand>();
	
	public Commands() {
		//Private message commands
		pmCommands.put("ping", new PMCommand() {
			public void runCommand(PrivateMessageReceivedEvent event, String[] args) {
				event.getChannel().sendMessageAsync("pong", null);
			}
		});
		
		pmCommands.put("version", new PMCommand() {
			public void runCommand(PrivateMessageReceivedEvent event, String[] args) {
				event.getChannel().sendMessageAsync("Version: " + Listener.VERSION_NUMBER, null);
			}
		});

		pmCommands.put("restart", new PMCommand() {
			public void runCommand(PrivateMessageReceivedEvent event, String[] args) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!info.isOwner(event.getAuthor().getId())) {
					event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
					return;
				}
				
				event.getChannel().sendMessage("Trying to restart...");
				System.exit(1);
			}
		});
		
		pmCommands.put("note", new PMCommand() {
			public void runCommand(PrivateMessageReceivedEvent event, String[] args) {
				try {
					if (args.length == 0) { // default case. Just list all notes
						PreparedStatement ps = new Connections().getConnection().prepareStatement("SELECT * FROM notes WHERE userid = ?");
						ps.setString(1, event.getAuthor().getId());
						ResultSet rs = ps.executeQuery();
						
						String message = "Here are all your notes:\n";
						while (rs.next()) {
							message += ("-" + rs.getString("name") + "\n");
						}
						if (!message.equals("Here are all saved notes:\n"))
							event.getChannel().sendMessageAsync(message, null);
						else
							event.getChannel().sendMessageAsync("No notes found", null);
					}	
					else if (args[0].equals("add")) { // add case. Create a new note
						PreparedStatement ps = new Connections().getConnection().prepareStatement("INSERT INTO notes(name, content, userid) VALUES(?, ?, ?)") ;
						ps.setString(1, args[1].trim());
						ps.setString(2, args[2].trim());
						ps.setString(3, event.getAuthor().getId());
						ps.executeUpdate();
						
						event.getChannel().sendMessageAsync("Note saved", null);
					}
					else if (args[0].equals("del")) { // delete case. Delete note with that name
						PreparedStatement ps = new Connections().getConnection().prepareStatement("DELETE FROM notes WHERE name = ? AND userid = ?") ;
						ps.setString(1, args[1]);
						ps.setString(2, event.getAuthor().getId());
						int rowsUpdated = ps.executeUpdate();
						
						if (rowsUpdated == 1)
							event.getChannel().sendMessageAsync("Note deleted", null);
						else
							event.getChannel().sendMessageAsync("No note with that name found", null);
					}
					else {
						if (args[0].equals("view"))
							args[0] = args[1];
						PreparedStatement ps = new Connections().getConnection().prepareStatement("SELECT * FROM notes WHERE name = ?");
						ps.setString(1, args[0]);
						ResultSet rs = ps.executeQuery();
						
						if (rs.next())
							event.getChannel().sendMessageAsync(rs.getString("content"), null);
						else
							event.getChannel().sendMessageAsync("No note found", null);
					}
				} catch (MySQLIntegrityConstraintViolationException e) {
					event.getChannel().sendMessageAsync("[Error] A note with that name already exists", null);
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});
		
		//Guild message commands
		guildCommands.put("help", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				String message = "Commands available:\n```html\n";
				for (Map.Entry<String, GuildCommand> entry : guildCommands.entrySet()) {
					if (!entry.getValue().getHelp(event).isEmpty())
						message += String.format("/%1$-12s | " + entry.getValue().getHelp(event) + "\n", entry.getKey());
				}
				message += "```";
				event.getChannel().sendMessageAsync(message, null);
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				return "< ?> variables are optional, <a>|<b> either var a OR b";
			}
		});

		guildCommands.put("setavatar", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				event.getChannel().sendTyping();
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
					event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
					return;
				}
				
				if (!event.getMessage().getAttachments().isEmpty()) {
					File avatarFile;
					Attachment attachment = event.getMessage().getAttachments().get(0);
					attachment.download(avatarFile = new File("./temp/newavatar.jpg"));
					try {
						Avatar avatar = AvatarUtil.getAvatar(avatarFile);
						event.getJDA().getAccountManager().setAvatar(avatar).update();
					} catch (UnsupportedEncodingException e) {
						event.getChannel().sendMessageAsync("[Error] Filetype", null);
					}
					event.getChannel().sendMessageAsync("[Success] Avatar changed.", null);
					avatarFile.delete();
				}
				else {
					event.getChannel().sendMessageAsync("[Error] No image attached", null);
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "";
				return "Upload desiered pic to discord and enter command in the description prompt";
			}
		});

		guildCommands.put("setname", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				event.getChannel().sendTyping();
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
					event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
					return;
				}
				
				if (args.length == 0) {
					event.getChannel().sendMessageAsync("[Error] No name stated", null);
				} else {
					event.getJDA().getAccountManager().setUsername(args[0]).update();
					event.getChannel().sendMessageAsync("[Success] Name changed", null);
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "";
				return "<name>";
			}
		});

		guildCommands.put("setgame", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				event.getChannel().sendTyping();
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
					event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
					return;
				}
				
				String game = "";
				if (args.length == 0)
					event.getJDA().getAccountManager().setGame(null);
				else {
					game = args[0];
					event.getJDA().getAccountManager().setGame(args[0]);
				}
				info.setGame(game);
				event.getChannel().sendMessageAsync("[Success] Game changed", null);
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "";
				return "<game?> - To set the Playing: ...";
			}
		});
		
		guildCommands.put("dist", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				if (args.length < 2) {
					event.getChannel().sendMessageAsync("[Error] Not enough systems specified", null);
					return;
				}
				Gson gson = new Gson();
				EDSystem sys1 = null;
				EDSystem sys2 = null;
				String jsonSys1 = "";
				String jsonSys2 = "";
				String urlSys1 = "http://www.edsm.net/api-v1/system?sysname=" + args[0].trim().replaceAll(" ", "+") + "&coords=1";
				String urlSys2 = "http://www.edsm.net/api-v1/system?sysname=" + args[1].trim().replaceAll(" ", "+") + "&coords=1";
				
				try {
					Document docSys1 = Jsoup.connect(urlSys1).ignoreContentType(true).get();
					Document docSys2 = Jsoup.connect(urlSys2).ignoreContentType(true).get();
					
					jsonSys1 = docSys1.body().text();
					jsonSys2 = docSys2.body().text();
					
					if (jsonSys1.contains("[]") || jsonSys2.contains("[]")) {
						event.getChannel().sendMessageAsync("[Error] System not found or coordinates not in db.", null);
						return;
					}
					
					sys1 = gson.fromJson(jsonSys1, EDSystem.class);
					sys2 = gson.fromJson(jsonSys2, EDSystem.class);
					
					if (sys1.coords == null || sys2.coords == null) {
						event.getChannel().sendMessageAsync("[Error] System not found or coordinates not in db.", null);
						return;
					}
					
					float x = sys2.coords.x - sys1.coords.x;
					float y = sys2.coords.y - sys1.coords.y;
					float z = sys2.coords.z - sys1.coords.z;
					
					double dist = Math.sqrt(x*x + y*y + z*z);

					event.getChannel().sendMessageAsync(String.format("Distance: %.1f ly", dist), null);
				} catch (JsonSyntaxException e) {
					event.getChannel().sendMessageAsync("[Error] Processing edsm result failed", null);
				} catch (SocketException e) {
					event.getChannel().sendMessageAsync("[Error] Failed connecting to edsm. You might want to retry in a few", null);
				} catch (IOException e) {
					event.getChannel().sendMessageAsync("[Error] Processing data failed", null);
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				return "<system1>, <system2> - Gives the distance between those systems.";
			}
		});

		guildCommands.put("new", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				DiscordInfo info = new DiscordInfo();
				//Permission check
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
					event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
					return;
				}
				
				try {
					if (args.length == 0) {
						event.getChannel().sendMessageAsync(info.getNewMemberInfo().replaceAll("<user>", event.getAuthorName()), null);
					}
					else if (args.length == 1) {
						event.getChannel().sendMessageAsync("[Error] channel or message missing", null);
					}
					else if (args.length > 1) {
						TextChannel welcomeChan = null;
						if (!event.getMessage().getMentionedChannels().isEmpty())
							welcomeChan = event.getMessage().getMentionedChannels().get(0);
						else {
							for (TextChannel chan : event.getGuild().getTextChannels()) {
								if (chan.getName().equals(args[0]))
									welcomeChan = chan;
							}
						}
						if (welcomeChan == null) {
							event.getChannel().sendMessageAsync("[Error] Channel not found", null);
							return;
						}
						
						info.setWelcomeChannelID(welcomeChan.getId());
						info.setNewMemberInfo(event.getMessage().getRawContent().replaceFirst("/new", "")
								.replaceFirst(welcomeChan.getAsMention() + ",", "")
								.replaceFirst(welcomeChan.getName() + ",", "").trim());
						event.getChannel().sendMessageAsync("[Success] New member message changed", null);
					}
						
				} catch (FileNotFoundException e) {
					event.getChannel().sendMessageAsync("[Error] Couldn't find the message, sorry", null);
				} catch (IOException e) {
					event.getChannel().sendMessageAsync("[Error] Couldn't read required file", null);
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "";
				return "<channel>, <information?> - sets information for new players or shows it";
			}
		});

		guildCommands.put("adminchannel", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				DiscordInfo info = new DiscordInfo();
				
				//Permission check
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
					event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
					return;
				}
				try {
					if (args.length == 0){
						event.getChannel().sendMessageAsync("Admin channel is: <#" + info.getAdminChanID() + ">", null);
					}
					else if (!event.getMessage().getMentionedChannels().isEmpty()) {
						info.setAdminChanID(event.getMessage().getMentionedChannels().get(0).getId());
						event.getChannel().sendMessageAsync("[Success] Admin channel saved", null);
					}
					else {
						TextChannel chan = event.getGuild().getTextChannels().stream().filter(vChan -> vChan.getName().equalsIgnoreCase(args[0].trim()))
								.findFirst().orElse(null);
						if (chan == null) {
							event.getChannel().sendMessageAsync("Channel not found", null);
							return;
						} else
							info.setAdminChanID(chan.getId());
						event.getChannel().sendMessageAsync("[Success] Admin channel saved", null);
					}
				} catch (IOException e) {
					event.getChannel().sendMessageAsync("[Error] Couldn't read required file", null);
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "";
				return "<channel> - sets admin channel";
			}
		});

		guildCommands.put("admin", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				DiscordInfo info = new DiscordInfo();
				
				//Permission check
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
					event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
					return;
				}
				
				try {
					if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("view"))) {
						String message = "";
						for (String id : info.getAdminRoleIDs())
							message += ( "-" + event.getGuild().getRoleById(id).getName() + "\n" );
						
						if (!message.isEmpty())
							event.getChannel().sendMessageAsync("Roles with admin privileges:\n" + message, null);
						else
							event.getChannel().sendMessageAsync("No admin roles defined", null);
					}
					else if (args[0].equalsIgnoreCase("add")) {
						if (!event.getMessage().getMentionedRoles().isEmpty()) {
							info.addAdminRoleID(event.getMessage().getMentionedRoles().get(0).getId());
						} else if (args.length == 2) {
							Role role = event.getGuild().getRoles().stream().filter(vrole -> vrole.getName().equalsIgnoreCase(args[1].trim())).findFirst().orElse(null);
							if (role != null) {
								info.addAdminRoleID(role.getId());
							} else {
								event.getChannel().sendMessageAsync("[Error] No role with this name found", null);
								return;
							}
						} else {
							event.getChannel().sendMessageAsync("[Error] No role specified", null);
							return;
						}
						event.getChannel().sendMessageAsync("[Success] Admin role saved", null);
					}
					else if (args[0].equalsIgnoreCase("del")) {
						if (!event.getMessage().getMentionedRoles().isEmpty()) {
							info.removeAdminRoleID(event.getMessage().getMentionedRoles().get(0).getId());
						} else if (args.length == 2) {
							Role role = event.getGuild().getRoles().stream().filter(vrole -> vrole.getName().equalsIgnoreCase(args[1].trim())).findFirst().orElse(null);
							if (role != null) {
								info.removeAdminRoleID(role.getId());
							} else {
								event.getChannel().sendMessageAsync("[Error] No role with this name found", null);
								return;
							}
						} else {
							event.getChannel().sendMessageAsync("[Error] No role specified", null);
							return;
						}
						event.getChannel().sendMessageAsync("[Success] Admin role removed", null);
					}
				} catch (IOException e) {
					event.getChannel().sendMessageAsync("[Error] Couldn't read required file", null);
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "";
				return "<add?>|<del?>, <role?> - shows, adds or delets a role in/to/from admins";
			}
		});
		
		guildCommands.put("give", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				Karma karma = new Karma();
				for (User user : event.getMessage().getMentionedUsers()) {
					if (karma.give(event.getAuthor().getId(), user.getId())) {
						event.getChannel().sendMessageAsync(user.getUsername() + ", you received karma from " + event.getAuthorName() + ". Keep up the positive spirit.", null);
						Statistics.getInstance().logKarma("give");
					}
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				return "<user> - gives a user one of your karma points";
			}
		});
		
		guildCommands.put("karma", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				Karma karma = new Karma();
				
				if (args.length == 0)
					event.getChannel().sendMessageAsync("You've got " + karma.getKarmaFor(event.getAuthor().getId()) + " karma.", null);
				else {
					//Permission check
					DiscordInfo info = new DiscordInfo();
					if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
						event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
						return;
					}
					
					if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("add")) {
						User user = null;
						if (!event.getMessage().getMentionedUsers().isEmpty())
							user = event.getMessage().getMentionedUsers().get(0);
						else {
							user = event.getGuild().getUsers().stream().filter(vUser -> vUser.getUsername().equalsIgnoreCase(args[1])).findFirst()
									.orElse(event.getGuild().getUsers().stream().filter(vUser -> event.getGuild().getNicknameForUser(vUser).equals(args[1])).findFirst()
											.orElse(null));
						}
						if (user != null) {
							karma.increase(Arrays.asList(user.getId()), Integer.parseInt(args[2]));
							event.getChannel().sendMessageAsync("Karma increased", null);
						} else {
							event.getChannel().sendMessageAsync("User not found", null);
						}
					}
					else if (args[0].equalsIgnoreCase("take")) {
						User user = null;
						if (!event.getMessage().getMentionedUsers().isEmpty())
							user = event.getMessage().getMentionedUsers().get(0);
						else {
							user = event.getGuild().getUsers().stream().filter(vUser -> vUser.getUsername().equalsIgnoreCase(args[1])).findFirst()
									.orElse(event.getGuild().getUsers().stream().filter(vUser -> event.getGuild().getNicknameForUser(vUser).equals(args[1])).findFirst()
											.orElse(null));
						}
						if (user != null) {
							karma.decrease(user.getId(), Integer.parseInt(args[2]));
							event.getChannel().sendMessageAsync("Karma decreased", null);
						} else {
							event.getChannel().sendMessageAsync("User not found", null);
						}
					}
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "shows your karma points";
				return "<give|take?>, <mention?>, <ammount?> - shows your karma points or gives/takes the number of karma from that player";
			}
		});
		
		guildCommands.put("leaderboard", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				Karma karma = new Karma();
				
				karma.pmLeaderboard(event);
				if (karma.isOnCD())
					return;
				
				String message = "Leaderboard:\n";
				message += "```Karma | User\n";
				message += "--------------------------------------------\n";
				for (Map.Entry<String, String> entry : karma.getLeaderboard().entrySet()) {
					message += String.format( "%1$5s | " + event.getJDA().getUserById(entry.getKey()).getUsername() + "\n", entry.getValue());
				}
				message += "```";
				
				event.getChannel().sendMessageAsync(message, null);
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				return "shows a leaderboard of the 5 users with the most karma and PM's you all of them.";
			}
		});

		guildCommands.put("whois", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				event.getChannel().sendTyping();
				
				if (args.length > 0) {
					Whois whois = new Whois();
					if (args[0].equalsIgnoreCase("add")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						whois.newEntry(event, args[1]);
					}
					else if (args[0].toLowerCase().startsWith("edit")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						if (whois.editEntry(event, args)){
							event.getChannel().sendMessageAsync("Saved", null);;
						} else {
							event.getChannel().sendMessageAsync("No entry found to edit", null);
						}
					}
					else if (args[0].equalsIgnoreCase("cancel")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						whois.cancelCreation();
					}
					else if (args[0].equalsIgnoreCase("del")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						if (args.length == 1)
							event.getChannel().sendMessageAsync("[Error] Please enter a name to delete", null);
						else if (whois.delEntry(args[1]))
							event.getChannel().sendMessageAsync("Entry deleted", null);;
					}
					else if (Whois.creation != null && Whois.creation.author.equals(event.getAuthor())) {
						String arg = String.join(", ", args);
						whois.nextStep(arg);
					}
					else {
						event.getChannel().sendMessageAsync(whois.getInfo(args[0]), null);
					}
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "<name> - shows the available information about that player";
				return "<name>|<add>|<del>, <name?> - show, add* or delete* information about a player";
			}
		});

		guildCommands.put("whoare", new GuildCommand() {
			public void runCommand(GuildMessageReceivedEvent event, String[] args) {
				event.getChannel().sendTyping();
				
				if (args.length > 0) {
					Whoare whoare = new Whoare();
					if (args[0].equalsIgnoreCase("add")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						whoare.newEntry(event, args[1]);
					}
					else if (args[0].toLowerCase().startsWith("edit")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						if (whoare.editEntry(event, args)){
							event.getChannel().sendMessageAsync("Saved", null);;
						} else {
							event.getChannel().sendMessageAsync("No entry found to edit", null);
						}
					}
					else if (args[0].equalsIgnoreCase("cancel")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						whoare.cancelCreation();
					}
					else if (args[0].equalsIgnoreCase("del")) {
						//Permission check
						DiscordInfo info = new DiscordInfo();
						if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor())))) {
							event.getChannel().sendMessageAsync("[Error] You aren't authorized to do this", null);
							return;
						}
						
						if (args.length == 1)
							event.getChannel().sendMessageAsync("[Error] Please enter a name to delete", null);
						else if (whoare.delEntry(args[1]))
							event.getChannel().sendMessageAsync("Entry deleted", null);;
					}
					else if (Whoare.creation != null && Whoare.creation.author.equals(event.getAuthor())) {
						whoare.nextStep(String.join(", ", args));
					}
					else {
						event.getChannel().sendMessageAsync(whoare.getInfo(args[0]), null);
					}
				}
			}
			
			public String getHelp(GuildMessageReceivedEvent event) {
				//Permission check
				DiscordInfo info = new DiscordInfo();
				if (!(info.isOwner(event.getAuthor().getId()) || info.isAdmin(event.getGuild().getRolesForUser(event.getAuthor()))))
					return "<name> - shows the available information about that group";
				return "<name>|<add>|<del>, <name?> - show, add* or delete* information about a group";
			}
		});
		//end of commands
	}
}
