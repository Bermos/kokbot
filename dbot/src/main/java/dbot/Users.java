package dbot;

import java.io.FileNotFoundException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.JDA;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import structs.DiscordInfo;

public class Users {
	List<User> users = new ArrayList<User>();
	public JDA jda;
	class User {
		String id;
		List<OffsetDateTime> messageTimes = new ArrayList<OffsetDateTime>();
		List<OffsetDateTime> warnings = new ArrayList<OffsetDateTime>();
	}
	
	public void newMessage(GuildMessageReceivedEvent event) {
		User author = null;
		Karma karma = new Karma();
		
		if (event.getAuthor().isBot())
			return;
		
		for (User user : users) {
			if (user.id.equals(event.getAuthor().getId())) {
				author = user;
			}
		}
		if (author == null) {
			author = new User();
			author.id = event.getAuthor().getId();
			users.add(author);
		}

		if (checkIfSpam(author, event.getMessage().getTime(), event)) {
			int noOfSpams = author.warnings.size();
			if (noOfSpams == 1) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. Please keep it down or you'll lose karma.", null);
			} else if (noOfSpams == 2) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. There goes one of your precious karma.", null);
				karma.decrease(event.getAuthor().getId(), 1);
			} else if (noOfSpams == 3) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. This one costs you 3 karma.", null);
				karma.decrease(event.getAuthor().getId(), 3);
			} else if (noOfSpams > 3) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. -10 karma for you. Seriously, stop!", null);
				karma.decrease(event.getAuthor().getId(), 10);
			}
		}

		author.messageTimes.add(event.getMessage().getTime());
	}
	
	private boolean checkIfSpam(User author, OffsetDateTime lastMessage, GuildMessageReceivedEvent event) {
		int lowCount = 0;
		int highCount = 0;
		
		Iterator<OffsetDateTime> iter = author.messageTimes.iterator();
		while (iter.hasNext()) {
			OffsetDateTime time = iter.next();
			if (time.plusSeconds(6).isAfter(lastMessage))
				lowCount++;
			if (time.plusSeconds(60).isAfter(lastMessage))
				highCount++;
			if (time.plusSeconds(60).isBefore(lastMessage))
				iter.remove();
		}
		
		iter = author.warnings.iterator();
		while (iter.hasNext()) {
			if (iter.next().plusHours(1).isBefore(lastMessage))
				iter.remove();
		}
		
		if ((lowCount + 1) % 4 == 0) {
			author.warnings.add(lastMessage);
			return true;
		} else if ((highCount + 1) % 20 == 0) {
			author.warnings.add(lastMessage);
			return true;
		}
		
		return false;
	}

	public static void squireNew(net.dv8tion.jda.entities.User user) {
		try {
			PreparedStatement ps = Connections.getConnection().prepareStatement("INSERT INTO squires (squireid, addtime) VALUES (?, ?)");
			ps.setString(1, user.getId());
			ps.setLong(2, new Date(System.currentTimeMillis()).getTime());
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void squireRemoved(String userid) {
		PreparedStatement ps;
		try {
			ps = Connections.getConnection().prepareStatement("DELETE FROM squires WHERE squireid = ?");
			ps.setString(1, userid);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void squireNoticeSent(String userid, int days) {
		PreparedStatement ps;
		try {
			String notice = days + "_d_notice";
			ps = Connections.getConnection().prepareStatement("UPDATE squires SET " + notice + " = 1 WHERE squireid = ?");
			ps.setString(1, userid);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static Map<String, String> squireList() {
		Map<String, String> squires = new LinkedHashMap<String, String>();
		
		try {
			PreparedStatement ps = Connections.getConnection().prepareStatement("SELECT * FROM squires ORDER BY addtime ASC");
			ResultSet rs = ps.executeQuery();
			
			while (rs.next()) {
				squires.put(rs.getString("squireid"), rs.getString("addtime"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return squires;
	}
	
	public void startUserCheck(JDA jda) {
		Timer timer = new Timer();
		//Calendar today = Calendar.getInstance();
		//today.set(Calendar.HOUR_OF_DAY, 20);
		//today.set(Calendar.MINUTE, 0);
		//today.set(Calendar.SECOND, 0);
		
		timer.scheduleAtFixedRate(new CheckTask(jda), new Date(), TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS));
	}
	
	class CheckTask  extends TimerTask {
		private JDA jda;
		
		public CheckTask(JDA jda) {
			this.jda = jda;
			boolean initialised = jda != null;
			System.out.println("jda initialised: " + initialised);
		}

		@Override
		public void run() {
			try {
				System.out.println("Squire check started...");
				PreparedStatement ps = Connections.getConnection().prepareStatement("SELECT * FROM squires WHERE 14_d_notice = 0 OR 30_d_notice = 0");
				ResultSet rs = ps.executeQuery();
				
				Long now = new Date().getTime();
				while (rs.next()) {
					if ((rs.getLong("addtime") + TimeUnit.MILLISECONDS.convert(14, TimeUnit.DAYS)) < now && !rs.getBoolean("14_d_notice")) {
						net.dv8tion.jda.entities.User user = jda.getUserById(rs.getString("squireid"));
						if (user == null) {
							squireRemoved(rs.getString("squireid"));
							return; 
						} else if (user.getJDA().getGuildById("141575893691793408").getNicknameForUser(user) == null) {
							jda.getTextChannelById(new DiscordInfo().getAdminChanID())
							.sendMessageAsync("@everyone. " + user.getAsMention() + " has been a squire for over 2 weeks now.", null);
							
						} else {
							jda.getTextChannelById(new DiscordInfo().getAdminChanID())
							.sendMessageAsync("@everyone. " + user.getAsMention() + "(" + user.getUsername() + ") has been a squire for over 2 weeks now.", null);
						}
						squireNoticeSent(rs.getString("squireid"), 14);
					} else if ((rs.getLong("addtime") + TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS)) < now && !rs.getBoolean("30_d_notice")) {
						net.dv8tion.jda.entities.User user = jda.getUserById(rs.getString("squireid"));
						if (user == null) {
							squireRemoved(rs.getString("squireid"));
							return; 
						} else if (user.getJDA().getGuildById("141575893691793408").getNicknameForUser(user) == null) {
							jda.getTextChannelById(new DiscordInfo().getAdminChanID())
							.sendMessageAsync("@everyone. " + user.getAsMention() + " has been a squire for over 30 days now.", null);
							
						} else {
							jda.getTextChannelById(new DiscordInfo().getAdminChanID())
							.sendMessageAsync("@everyone. " + user.getAsMention() + "(" + user.getUsername() + ") has been a squire for over 30 days now.", null);
						}
						squireNoticeSent(rs.getString("squireid"), 30);
					}
				}
				System.out.println("Squire check finished");
			} catch (SQLException e) {
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

}
