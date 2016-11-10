package dbot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.JDA;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import provider.Statistics;

class Karma {
	private static Long lastLeaderboardOut = (long) 0;
	
	void generateNew(GuildMessageReceivedEvent event) {
		Connection connect = Connections.getConnection();
		
		try {
			PreparedStatement ps = connect.prepareStatement("SELECT * FROM users WHERE userid = ?");
			ps.setString(1, event.getAuthor().getId());
			ResultSet rs = ps.executeQuery();
			
			if (rs.next()) {
				long lastKarmaGenerated = rs.getLong("lastkarmagenerated");
				String karmaGiverID = rs.getString("userid");
				if ((new Date().getTime() - lastKarmaGenerated ) > 60*60*1000) {
					List<String> karmaReceiverIDs = new ArrayList<>();
					List<String> karmaReceiverNames = new ArrayList<>();
					for (User user : event.getMessage().getMentionedUsers()) {
						karmaReceiverIDs.add(user.getId());
						karmaReceiverNames.add(user.getUsername());
						
						if (user.getId().equals(event.getAuthor().getId())) {
							event.getChannel().sendMessageAsync("Trying to give yourself karma. Shame!", null);
							karmaReceiverIDs.remove(user.getId());
							karmaReceiverNames.remove(user.getUsername());
							Statistics.getInstance().logKarma("self_karma");
						} else {
							Statistics.getInstance().logKarma("generated");
						}
					}
					increase(karmaReceiverIDs, 1);
					resetCD(karmaGiverID);
					if (karmaReceiverIDs.size() > 0) {
						if (karmaReceiverIDs.size() == 1 && karmaReceiverIDs.get(0).equals(event.getJDA().getSelfInfo().getId()))
							event.getChannel().sendMessageAsync("Aww yiss, sweet karma for me!", null);
						else
							event.getChannel().sendMessageAsync("Nice one, " + String.join(", ", karmaReceiverNames) + ". Your karma goes up!", null);
					}
				}
			} else {
				newUser(event);
				generateNew(event);
			}
		
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	Map<String, String> getLeaderboard() {
		Map<String, String> leaderboard = new LinkedHashMap<>();
		Connection connect = Connections.getConnection();
		
		try {
			PreparedStatement ps = connect.prepareStatement("SELECT userid, karma FROM users ORDER BY karma DESC LIMIT 5");
			ResultSet rs = ps.executeQuery();
			
			while (rs.next()) {
				leaderboard.put(rs.getString("userid"), rs.getString("karma"));
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		lastLeaderboardOut = new Date().getTime();
		
		return leaderboard;
	}

	boolean give(String giverID, String receiverID) {
		if (Integer.parseInt(getKarmaFor(giverID)) > 0 ) {
			decrease(giverID, 1);
			increase(Arrays.asList(receiverID), 1);
			return true;
		}
		return false;
	}
	
	private void newUser(GuildMessageReceivedEvent event) {
		Connection connect = Connections.getConnection();
		
		try {
			String name = event.getAuthorName();
			if (name != null)
				name = EmojiParser.parseToUnicode(name);
			String nick = event.getAuthorNick();
			if (nick != null)
				nick = EmojiParser.parseToUnicode(nick);

			PreparedStatement ps = connect.prepareStatement("INSERT INTO users (userid, username, nickname) VALUES (?, ?, ?)");
			ps.setString(1, event.getAuthor().getId());
			ps.setString(2, name);
			ps.setString(3, nick);
			ps.executeUpdate();
		
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	void increase(List<String> karmaReceiverIDs, int ammount) {
		Connection connect = Connections.getConnection();
		
		try {
			for (String id : karmaReceiverIDs) {
				PreparedStatement ps = connect.prepareStatement("UPDATE users SET karma = karma + ? WHERE userid = ?");
				ps.setInt(1, ammount);
				ps.setString(2, id);
				int updatedRows = ps.executeUpdate();
				
				if (updatedRows == 0) {
					ps.close();
					ps = connect.prepareStatement("INSERT INTO users (userid, karma) VALUES (?, ?)");
					ps.setString(1, id);
					ps.setInt(2, ammount);
					ps.executeUpdate();
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	boolean decrease(String userID, int ammount) {
		Connection connect = Connections.getConnection();
	
		try {
			PreparedStatement ps = connect.prepareStatement("UPDATE users SET karma = karma - ? WHERE userid = ?");
			ps.setInt(1, ammount);
			ps.setString(2, userID);
			int updatedRows = ps.executeUpdate();
			
			if (updatedRows == 0) {
				ps.close();
				ps = connect.prepareStatement("INSERT INTO users (userid, karma) VALUES (?, ?)");
				ps.setString(1, userID);
				ps.setInt(2, -ammount);
				ps.executeUpdate();
			}
			
			return updatedRows != 0;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	private void resetCD(String karmaGiverID) {
		Connection connect = Connections.getConnection();
		
		try {
			PreparedStatement ps = connect.prepareStatement("UPDATE users SET lastkarmagenerated = ? WHERE userid = ?");
			ps.setLong(1, new Date().getTime());
			ps.setString(2, karmaGiverID);
			int updatedRows = ps.executeUpdate();
			
			if (updatedRows == 0) {
				ps.close();
				ps = connect.prepareStatement("INSERT INTO users (userid, lastkarmagenerated) VALUES (?, ?)");
				ps.setString(1, karmaGiverID);
				ps.setLong(2, new Date().getTime());
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	boolean isOnCD() {
		return (lastLeaderboardOut + (60*60*1000) > new Date().getTime());
	}

	void pmLeaderboard(GuildMessageReceivedEvent event) {
		Connection connect = Connections.getConnection();
		String message = "Complete Leaderboard:\n";
		message += "```Karma | User\n";
		message += "--------------------------------------------\n";
		
		try {
			PreparedStatement ps = connect.prepareStatement("SELECT userid, karma FROM users WHERE karma < -10 OR karma > 10 ORDER BY karma DESC");
			ResultSet rs = ps.executeQuery();
			
			while (rs.next()) {
				message += String.format("%1$5s | " + event.getJDA().getUserById(rs.getString("userid")).getUsername() + "\n", rs.getString("karma"));
			}
			message += "```";
			
			event.getAuthor().getPrivateChannel().sendMessageAsync(message, null);
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	String getKarmaFor(String id) {
		Connection connect = Connections.getConnection();
		String points = "0";
		
		try {
			PreparedStatement ps = connect.prepareStatement("SELECT karma FROM users WHERE userid = ?");
			ps.setString(1, id);
			ResultSet rs = ps.executeQuery();
			
			if (rs.next()) {
				points = rs.getString("karma");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return points;
	}

	void updateUsernames(JDA jda) {
		Connection connect = Connections.getConnection();

		try {
			PreparedStatement ps = connect.prepareStatement("SELECT userid FROM users");
			ResultSet rs = ps.executeQuery();

			ps = connect.prepareStatement("UPDATE users SET username = ?, nickname = ? WHERE userid = ?");
			while (rs.next()) {
				User user = jda.getUserById(rs.getString("userid"));
				if (user != null) {
					String name = user.getUsername();
					if (name != null)
						name = EmojiParser.parseToHtmlHexadecimal(name);
					String nick = jda.getGuildById("141575893691793408").getNicknameForUser(user);
					if (nick != null)
						nick = EmojiParser.parseToHtmlHexadecimal(nick);

					ps.setString(1, name);
					ps.setString(2, nick);
					ps.setString(3, rs.getString("userid"));
					ps.addBatch();
				}
			}

			ps.executeBatch();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
}
