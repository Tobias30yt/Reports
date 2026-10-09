package de.example.reports.command;

import de.example.reports.gui.SnakeGameService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SnakeCommand implements CommandExecutor {
    private final SnakeGameService snake;

    public SnakeCommand(SnakeGameService snake) {
        this.snake = snake;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can play Snake.");
            return true;
        }
        snake.open(player);
        return true;
    }
}
