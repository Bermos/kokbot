package provider;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import net.dv8tion.jda.JDA;
import net.dv8tion.jda.OnlineStatus;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;

public class Statistics extends Thread {
	private static Statistics instance;
	private static InfluxDB influxDB;
	private BatchPoints batchPoints;
	private static JDA jda;

	class InfluxInfo {
		String IP;
		String US;
		String PW;
	}

	private Statistics() {
		
	}
	
	static {
		instance = new Statistics();
	}
	
	public static Statistics getInstance() {
		return instance;
	}
	
	public void connect(JDA jda) {
		Gson gson = new Gson();
		try {
			JsonReader jReader = new JsonReader(new FileReader("./influxinfo.json"));
			InfluxInfo info = gson.fromJson(jReader, InfluxInfo.class);
			
			Statistics.influxDB = InfluxDBFactory.connect(info.IP, info.US, info.PW);
			Statistics.jda = jda;
			
			boolean connected = false;
			do {
				Pong response;
				try {
					response = influxDB.ping();
					if (!response.getVersion().equalsIgnoreCase("unknown")) {
						connected = true;
					}

					Thread.sleep(10L);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} while (!connected);
			System.out.println("[InfluxDB] connected. Version: " + influxDB.version());
			
			batchPoints = BatchPoints
					.database("kok_monitor")
					.tag("async", "true")
					.retentionPolicy("default")
					.consistency(ConsistencyLevel.ALL)
					.build();
			
			this.start();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
		while (true) {
			try {
				//update statistics
				int onlineUser = 0;
				for(User user : jda.getUsers()) {
					if (!user.getOnlineStatus().equals(OnlineStatus.OFFLINE))
						onlineUser++;
				}
				Point users = Point.measurement("users")
						.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
						.addField("online", onlineUser)
						.addField("total", jda.getUsers().size())
						.build();
				
				Point system = Point.measurement("system")
						.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
						.addField("used_ram", (double)(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024)
						.addField("total_ram", (double) Runtime.getRuntime().maxMemory() / 1024 / 1024)
						.addField("no_threads", Thread.getAllStackTraces().keySet().size())
						.build();
				
				batchPoints.point(system);
				batchPoints.point(users);
				influxDB.write(batchPoints);
				
				Thread.sleep(1000);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void logMessage(GuildMessageReceivedEvent event) {
		Point messages = Point.measurement("messages")
				.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
				.addField("content_length", event.getMessage().getContent().length())
				.addField("author", event.getAuthor().getUsername())
				.addField("channel", event.getChannel().getName())
				.build();
		
		batchPoints.point(messages);
	}
	
	public void logCommandReceived(String commandName) {
		Point commands = Point.measurement("commands")
				.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
				.addField("name", commandName)
				.build();
		
		batchPoints.point(commands);
	}

	
	public void logKarmaGenerated() {
		Point karma = Point.measurement("karma")
				.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
				.build();
		
		batchPoints.point(karma);
	}
	
}