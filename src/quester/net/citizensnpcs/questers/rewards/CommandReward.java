package net.citizensnpcs.questers.rewards;

import net.citizensnpcs.properties.Storage;
import net.citizensnpcs.resources.npclib.HumanNPC;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class CommandReward implements Reward {
	private final String command;
	private final boolean isServerCommand;

	CommandReward(String command, boolean isServerCommand) {
		this.command = command;
		this.isServerCommand = isServerCommand;
	}

	@Override
	public void grant(Player player, HumanNPC npc) {
		if (isServerCommand) {
			Bukkit.getServer().dispatchCommand(
					new ConsoleCommandSender(Bukkit.getServer()), command);
		} else {
			player.performCommand(command);
		}
	}

	@Override
	public boolean canTake(Player player) {
		return false;
	}

	@Override
	public boolean isTake() {
		return false;
	}

	@Override
	public String getRequiredText(Player player) {
		return ""; // This should never execute.
	}

	@Override
	public void save(Storage storage, String root) {
		storage.setString(root + ".command", command);
		storage.setBoolean(root + ".server", isServerCommand);
	}

	public static class CommandRewardBuilder implements RewardBuilder {
		@Override
		public Reward build(Storage storage, String root, boolean take) {
			return new CommandReward(storage.getString(root + ".command"),
					storage.getBoolean(root + ".server"));
		}
	}
}
