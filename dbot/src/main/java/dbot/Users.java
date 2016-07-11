package dbot;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;

public class Users {
	List<User> users = new ArrayList<User>();
	class User {
		String id;
		List<OffsetDateTime> messageTimes = new ArrayList<OffsetDateTime>();
		List<OffsetDateTime> warnings = new ArrayList<OffsetDateTime>();
	}
	
	public void newMessage(GuildMessageReceivedEvent event) {
		User author = null;
		Karma karma = new Karma();
		
		if (event.getAuthor().equals(event.getJDA().getSelfInfo()))
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

}
