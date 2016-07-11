package dbot;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class Connections {
	private static Connection SQLConnection;
	private class SQLInfo {
		String IP;
		String DB;
		String US;
		String PW;
	}
	
	private void connect() {
		Gson gson = new Gson();
		
		try {
			JsonReader jReader = new JsonReader(new FileReader("./sqlinfo.json"));
			SQLInfo info = gson.fromJson(jReader, SQLInfo.class);
			
			SQLConnection = DriverManager.getConnection(
					"jdbc:mysql://" + info.IP +
					"/" + info.DB +
					"?user=" + info.US +
					"&password=" + info.PW);
			
		} catch (FileNotFoundException e) {
			System.out.println("[Error] 'login.json' not found.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public Connection getConnection() {
		if (SQLConnection == null) {
			connect();
		}
		
		return SQLConnection;
	}
}
