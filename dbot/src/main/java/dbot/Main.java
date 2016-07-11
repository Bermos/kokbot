package dbot;

import java.io.FileNotFoundException;
import javax.security.auth.login.LoginException;

import net.dv8tion.jda.JDABuilder;
import structs.DiscordInfo;

public class Main {
	
	public static void main(String[] args) {
		
		try {
			DiscordInfo info = new DiscordInfo();
			
			new JDABuilder()
			.setBotToken(info.getToken())
			.addListener(new Listener())
			.buildBlocking();
			
		} catch (LoginException e) {
			System.out.println("[Error] invalid bot token.");
		} catch (IllegalArgumentException e) {
			System.out.println("[Error] no bot token found.");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			System.out.println("[Error] 'discord.json' not found.");
		}
	}

}
