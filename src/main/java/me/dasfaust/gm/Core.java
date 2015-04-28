package me.dasfaust.gm;

import java.util.UUID;

import me.dasfaust.gm.config.Config;
import me.dasfaust.gm.config.Config.Defaults;
import me.dasfaust.gm.menus.Menus;
import me.dasfaust.gm.storage.JsonStorage;
import me.dasfaust.gm.storage.ObjectTicker;
import me.dasfaust.gm.storage.RedisStorage;
import me.dasfaust.gm.storage.abs.StorageHandler;
import me.dasfaust.gm.tools.GMLogger;
import me.dasfaust.gm.tools.LocaleHandler;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Core extends JavaPlugin
{
	public static Core instance;
	protected StorageHandler storage;
	protected MiscListener miscListener;
	protected MenuHandler handler;
	protected Config conf;
	protected Economy economy;
	protected boolean postEnable = false;
	
	@Override
	public void onEnable()
	{
		instance = this;
		GMLogger.setLogger(getLogger());
		
		conf = new Config();
		try
		{
			conf.load();
		}
		catch(Exception e)
		{
			GMLogger.severe(e, "Can't load config. Check your tabs!");
			return;
		}
		
		GMLogger.setDebug(conf.get(Defaults.ENABLE_DEBUG));
		
		BlacklistHandler.init();
		
		if (conf.get(Defaults.PERSISTENCE_METHOD).equalsIgnoreCase("redis"))
		{
			storage = new RedisStorage(
				conf.get(Defaults.PERSISTENCE_METHOD_REDIS_ADDRESS),
				conf.get(Defaults.PERSISTENCE_METHOD_REDIS_PORT),
				conf.get(Defaults.PERSISTENCE_METHOD_REDIS_POOLSIZE)
			);
		}
		else
		{
			storage = new JsonStorage();
		}
		
		LocaleHandler.init();
		
		if (!storage.init())
		{
			GMLogger.warning("Storage couldn't be initialized, can't continue.");
			return;
		}
		storage.load();
		
		miscListener = new MiscListener();
		getServer().getPluginManager().registerEvents(miscListener, this);
		
		RegisteredServiceProvider<Economy> econ
			= getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
		if (econ == null)
		{
			GMLogger.warning("No economy plugin currently loaded, waiting...");
			return;
		}
		economy = econ.getProvider();
		
		postEnable();
	}
	
	public void postEnable()
	{
		int saveTicks = conf.get(Defaults.PERSISTENCE_METHOD_SAVEINTERVAL);
		if (saveTicks > 0)
		{
			new BukkitRunnable()
			{
				UUID uuid = storage.getChanged();
				
				@Override
				public void run()
				{
					if (!uuid.equals(storage.getChanged()))
					{
						GMLogger.debug("Saving data (async)");
						storage.saveAsync();
					}
					uuid = storage.getChanged();
				}
				
			}.runTaskTimer(this, saveTicks, saveTicks);
		}
		
		handler = new MenuHandler();
		getServer().getPluginManager().registerEvents(handler, this);
		
		// Tick MarketObjects once per hour at 10 objects per 20 ticks
		new ObjectTicker().runTaskTimer(this, 20, 1200);
		
		postEnable = true;
	}
	
	@Override
	public void onDisable()
	{
		if (storage != null)
		{
			GMLogger.debug("Saving data");
			storage.close();
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		if (cmd.getName().equalsIgnoreCase("market") && sender instanceof Player)
		{
			Player player = (Player) sender;
			handler.initViewer(player, Menus.MENU_LISTINGS);
			return true;
		}
		return false;
	}
	
	/**
	 * Get the StorageHandler
	 * @return
	 */
	public StorageHandler storage()
	{
		return storage;
	}
	
	/**
	 * Get the MenuHandler
	 * @return
	 */
	public MenuHandler handler()
	{
		return handler;
	}
	
	/**
	 * Get the ConfigHandler
	 * @return
	 */
	public Config config()
	{
		return conf;
	}
	
	/**
	 * Get the Vault economy handler
	 * @return
	 */
	public Economy econ()
	{
		return economy;
	}
}
