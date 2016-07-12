package structs;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import net.dv8tion.jda.entities.Role;

/**
 * Handles login information for Discord and ownership
 * information such as OwnerID and permitted RoleID
 */
public class DiscordInfo {
	private static Info info;
	private class Info {
		String token;
		List<String> idOwner;
		List<String> idRoles;
		String newMember;
		String adminChanID;
		String welcomeChanID;
		String game;
	}
	
	private void getInfo() throws FileNotFoundException {
		Gson gson = new Gson();
		JsonReader jReader = new JsonReader(new FileReader(""
				+ "./discord.json"));
		info = gson.fromJson(jReader, Info.class);
	}
	
	private void setInfo() throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonWriter jWriter = new JsonWriter(new FileWriter("./discord.json"));
		jWriter.setHtmlSafe(false);
		jWriter.setIndent("	");	
		gson.toJson(info, Info.class, jWriter);
		jWriter.close();
	}
	
	/**
	 * Returns the token necessary to login to Discord
	 * 
	 * @return the token to login to Discord
	 * @throws FileNotFoundException
	 */
	public String getToken() throws FileNotFoundException {
		if (info == null)
			getInfo();
		return info.token;
	}
	
	/**
	 * Returns a list of all owners that are allowed
	 * to perform critical changes
	 * 
	 * @return list of ID strings
	 * @throws FileNotFoundException
	 */
	public List<String> getOwnerIDs() throws FileNotFoundException {
		if (info == null)
			getInfo();
		return info.idOwner;
	}
	
	/**
	 * 
	 * @param id of the owner to add
	 * @throws IOException
	 */
	public void addOwner(String id) throws IOException {
		if (info == null)
			getInfo();
		info.idOwner.add(id);
		setInfo();
	}
	
	/**
	 * 
	 * @param id of the owner to remove
	 * @throws IOException
	 */
	public void removeOwner(String id) throws IOException {
		if (info == null)
			getInfo();
		info.idOwner.remove(info.idOwner.indexOf(id));
		setInfo();
	}
	
	/**
	 * Get the saved message for new members.
	 * 
	 * @return message as string
	 * @throws FileNotFoundException
	 */
	public String getNewMemberInfo() throws FileNotFoundException {
		if (info == null)
			getInfo();
		return info.newMember;
	}
	
	/**
	 * Save a new message for the new members.
	 * 
	 * @param message as string
	 * @throws IOException
	 */
	public void setNewMemberInfo(String message) throws IOException {
		if (info == null)
			getInfo();
		info.newMember = message;
		setInfo();
	}

	
	/**
	 * 
	 * @return the admin channel id as string
	 * @throws FileNotFoundException
	 */
	public String getAdminChanID() throws FileNotFoundException {
		if (info == null)
			getInfo();
		return info.adminChanID;
	}
	
	/**
	 * 
	 * @param id of the channel used for admins
	 * @throws IOException
	 */
	public void setAdminChanID(String id) throws IOException {
		if (info == null)
			getInfo();
		info.adminChanID = id;
		setInfo();
	}
	
	/**
	 * 
	 * @return
	 * @throws FileNotFoundException
	 */
	public List<String> getAdminRoleIDs() throws FileNotFoundException {
		if (info == null)
			getInfo();
		return info.idRoles;
	}
	
	/**
	 * 
	 * @param id
	 * @throws IOException
	 */
	public void addAdminRoleID(String id) throws IOException {
		if (info == null)
			getInfo();
		info.idRoles.add(id);
		setInfo();
	}
	
	public void removeAdminRoleID(String id) throws IOException {
		if (info == null)
			getInfo();
		info.idRoles.remove(id);
		setInfo();
	}
	
	public boolean isOwner(String id) {
		try {
			return getOwnerIDs().contains(id);
		} catch (FileNotFoundException e) {
			return false;
		}
		
	}
	
	public boolean isAdmin(List<Role> roles) {
		try {
			boolean isAdmin = false;
			for (Role role : roles) {
				if (getAdminRoleIDs().contains(role.getId()))
					isAdmin = true;
			}
			return isAdmin;
		} catch (FileNotFoundException e) {
			return false;
		}
	}
	
	public String getWelcomeChannelID() throws FileNotFoundException {
		if (info == null)
			getInfo();
		return info.welcomeChanID;
	}

	public void setWelcomeChannelID(String id) throws IOException {
		if (info == null)
			getInfo();
		info.welcomeChanID = id;
		setInfo();
	}

	
	public String getGame() {
		if (info == null) {
			try {
				getInfo();
			} catch (FileNotFoundException e) {
			}
		}
		return info.game;
	}
	
	public void setGame(String game) {
		try {
			if (info == null)
				getInfo();
			info.game = game;
			setInfo();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
