package dbot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.commons.lang3.StringUtils;

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;

import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;

public class Whois {
	public static Creation creation;
	class Creation {
		User author;
		TextChannel channel;
		String user;
		int step;
	}

	public String getInfo(String name) {
		Connection connect = new Connections().getConnection();
		String message = "";
		
		try {
			PreparedStatement ps = connect.prepareStatement("SELECT * FROM whois WHERE name = ?");
			ps.setString(1, name);
			ResultSet rs = ps.executeQuery();
			
			while (rs.next()) {
				message += ("CMDR **" + rs.getString("name") + "**:\n"
						+ "Ship: " + rs.getString("ship") + "\n"
						+ "Group(s): " + rs.getString("groups") + "\n"
						+ "Status: " + rs.getString("status") + "\n"
						+ "Location: " + rs.getString("location") + "\n"
						+ "Platform: " + rs.getString("platform") + "\n"
						+ "Note: " + rs.getString("notes") + "\n");
			}
			
			if (message.isEmpty()) {
				ps.close();
				ps = connect.prepareStatement("SELECT name FROM whois");
				rs = ps.executeQuery();
				
				while (rs.next()) {
					if (StringUtils.getJaroWinklerDistance(name, rs.getString("name")) > 0.9)
						message += ( rs.getString("name") + "\n" );
				}
				
				if (message.isEmpty())
					message += "Sorry, no entry for that name found.";
				else
					message = "No entry found but maybe you meant one of those?\n" + message;
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return message;
	}

	public void newEntry(GuildMessageReceivedEvent event, String args) {
		creation = new Creation();
		creation.author = event.getAuthor();
		creation.channel = event.getChannel();
		creation.step = 0;
		creation.user = args;
		
		editEntryName(args);
		nextStep();
	}

	public void nextStep() {
		switch (creation.step) {
			case 0: creation.channel.sendMessageAsync("Name set, continue with '/whois <ship>'. Use '/whois cancel' to abort.", null);
					break;
			case 1: creation.channel.sendMessageAsync("Ship set, continue with '/whois <groups>'", null);
					break;
			case 2: creation.channel.sendMessageAsync("Groups set, continue with '/whois <status>'", null);
					break;
			case 3: creation.channel.sendMessageAsync("Status set, continue with '/whois <location>'", null);
					break;
			case 4: creation.channel.sendMessageAsync("Location set, continue with '/whois <platform>'", null);
					break;
			case 5: creation.channel.sendMessageAsync("Platform set, finish with '/whois <notes>'", null);
					break;
		}
	}
	
	public void nextStep(String args) {
		switch (creation.step) {
			case 0: editEntry("ship", args, creation.user);
					break;
			case 1: editEntry("groups", args, creation.user);
					break;
			case 2: editEntry("status", args, creation.user);
					break;
			case 3: editEntry("location", args, creation.user);
					break;
			case 4: editEntry("platform", args, creation.user);
					break;
			case 5: editEntry("notes", args, creation.user);
					break;
		}
		
		creation.step++;
		if (creation.step > 5) {
			creation.channel.sendMessageAsync("Whois created and saved.", null);
			creation = null;
		} else
			nextStep();
	}
	
	public void cancelCreation() {
		creation.channel.sendMessageAsync("Cancelled. Use '/whois del, <name>' to delete unfishied entry or continue later.", null);
		creation = null;
	}
	
	private void editEntryName(String args) {
		Connection connect = new Connections().getConnection();
		
		try {
			PreparedStatement ps = connect.prepareStatement("INSERT INTO whois (name) VALUES (?)");
			ps.setString(1, args);
			ps.executeUpdate();
		} catch (MySQLIntegrityConstraintViolationException e) {
			creation.channel.sendMessageAsync("[Error] A whois for that name already exists", null);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private boolean editEntry(String field, String arg, String name) {
		Connection connect = new Connections().getConnection();
		
		try {
			PreparedStatement ps = connect.prepareStatement("UPDATE whois SET field = ? WHERE name = ?".replaceAll("field", field));
			ps.setString(1, arg);
			ps.setString(2, name);
			if (ps.executeUpdate() > 0)
				return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean editEntry(GuildMessageReceivedEvent event, String[] args) {
		String arg = "";
		for (int i = 2; i < args.length; i++)
			arg += ( ", " + args[i] );
		arg = arg.replaceFirst(", ", "");
		
		if (args[0].toLowerCase().equals("editship"))
			return editEntry("ship", arg, args[1]);
		
		else if (args[0].toLowerCase().equals("editgroup") || args[0].toLowerCase().equals("editgroups"))
			return editEntry("groups", arg, args[1]);
		
		else if (args[0].toLowerCase().equals("editstatus"))
			return editEntry("status", arg, args[1]);
		
		else if (args[0].toLowerCase().equals("editlocation"))
			return editEntry("location", arg, args[1]);

		else if (args[0].toLowerCase().equals("editplatform"))
			return editEntry("platform", arg, args[1]);
		
		else if (args[0].toLowerCase().equals("editnote") || args[0].toLowerCase().equals("editnotes"))
			return editEntry("notes", arg, args[1]);
		
		return false;
	}

	public boolean delEntry(String args) {
		Connection connect = new Connections().getConnection();
		
		try {
			PreparedStatement ps = connect.prepareStatement("DELETE FROM whois WHERE name = ?");
			ps.setString(1, args);
			if (ps.executeUpdate() > 0)
				return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
}
