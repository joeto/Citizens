package net.citizensnpcs.traders;

import java.util.HashMap;
import java.util.Map;

import net.citizensnpcs.Economy;
import net.citizensnpcs.resources.npclib.HumanNPC;
import net.citizensnpcs.utils.InventoryUtils;
import net.citizensnpcs.utils.MessageUtils;
import net.citizensnpcs.utils.StringUtils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class TraderTask implements Listener {
    private final TraderMode mode;
    private final HumanNPC npc;
    private final Player player;
    private int prevPlayerSlot = -1;
    private int prevTraderSlot = -1;

    // Gets run every tick, checks the inventory for changes.
    public TraderTask(HumanNPC npc, Player player, TraderMode mode) {
        this.npc = npc;
        this.player = player;
        this.mode = mode;
        sendJoinMessage();
    }

    private boolean checkMiscellaneous(Inventory inv, Stockable stockable, boolean buying) {
        ItemStack stocking = stockable.getStocking();
        if (buying) {
            if (!InventoryUtils.has(npc.getPlayer(), stocking)) {
                sendNoMoneyMessage(stocking, false);
                return true;
            }
            if (!Economy.hasEnough(player, stockable.getPrice().getPrice())) {
                sendNoMoneyMessage(stocking, true);
                return true;
            }
        } else {
            if (!InventoryUtils.has(player, stocking)) {
                sendNoMoneyMessage(stocking, true);
                return true;
            }
            if (mode != TraderMode.INFINITE && !Economy.hasEnough(npc, stockable.getPrice().getPrice())) {
                sendNoMoneyMessage(stocking, false);
                return true;
            }
        }

        return false;
    }

    private void exit() {
        HandlerList.unregisterAll(this);
        sendLeaveMessage();
        ((Trader) npc.getType("trader")).setFree();
    }

    private Stockable getStockable(ItemStack item, String keyword, boolean selling) {
        // durability needs to be reset to 0 for tools / weapons / armor
        short durability = item.getDurability();
        if (InventoryUtils.isTool(item.getTypeId()) || InventoryUtils.isArmor(item.getTypeId())) {
            durability = 0;
        }
        Trader trader = npc.getType("trader");
        if (!trader.isStocked(item.getTypeId(), durability, selling)) {
            player.sendMessage(StringUtils.wrap(MessageUtils.getItemName(item.getTypeId()), ChatColor.RED)
                    + " isn't being " + keyword + " here.");
            return null;
        }
        return trader.getStockable(item.getTypeId(), durability, selling);
    }
    
    public Player getPlayer() {
        return this.player;
    }

    public void ensureValid() {
        if (!player.isOnline())
            exit();
    }

    @SuppressWarnings("deprecation")
    public void handlePlayerClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        Inventory playerInv = player.getInventory();
        Stockable stockable = getStockable(event.getView().getItem(event.getRawSlot()), "bought", true);
        if (stockable == null) {
            return;
        }
        if (prevPlayerSlot != slot) {
            prevPlayerSlot = slot;
            sendStockableMessage(stockable);
            return;
        }
        prevPlayerSlot = slot;
        prevTraderSlot = -1;
        if (checkMiscellaneous(playerInv, stockable, false)) {
            return;
        }
        ItemStack selling = stockable.getStocking().clone();
        if (mode != TraderMode.INFINITE) {
            Economy.pay(npc, stockable.getPrice().getPrice());
        }
        InventoryUtils.removeItems(player, selling, slot);
        Map<Integer, ItemStack> unsold = new HashMap<Integer, ItemStack>();
        Trader trader = npc.getType("trader");
        if (mode != TraderMode.INFINITE) {
            if (!trader.isLocked())
                unsold = npc.getInventory().addItem(selling);
        }
        if (unsold.size() >= 1) {
            player.sendMessage(ChatColor.RED + "Not enough room available to add "
                    + MessageUtils.getStackString(selling, ChatColor.RED) + " to the trader's stock.");
            return;
        }
        double price = stockable.getPrice().getPrice();
        Economy.add(player.getName(), price);
        npc.getPlayer().updateInventory();
        player.updateInventory();
        player.sendMessage(ChatColor.GREEN + "Transaction successful.");
    }

    @SuppressWarnings("deprecation")
    public void handleTraderClick(InventoryClickEvent event) {
        Inventory npcInv = npc.getInventory();
        int slot = event.getSlot();
        Stockable stockable = getStockable(event.getView().getItem(event.getRawSlot()), "sold", false);
        if (stockable == null) {
            return;
        }
        if (prevTraderSlot != slot) {
            prevTraderSlot = slot;
            sendStockableMessage(stockable);
            return;
        }
        prevTraderSlot = slot;
        prevPlayerSlot = -1;
        if (checkMiscellaneous(npcInv, stockable, true)) {
            return;
        }
        ItemStack buying = stockable.getStocking().clone();
        Economy.pay(player, stockable.getPrice().getPrice());
        if (mode != TraderMode.INFINITE) {
            InventoryUtils.removeItems(npc.getPlayer(), buying, slot);
        }
        Map<Integer, ItemStack> unbought = player.getInventory().addItem(buying);
        if (unbought.size() >= 1) {
            player.sendMessage(ChatColor.RED + "Not enough room in your inventory to add "
                    + MessageUtils.getStackString(buying, ChatColor.RED) + ".");
            return;
        }
        double price = stockable.getPrice().getPrice();
        npc.setBalance(npc.getBalance() + price);
        npc.getPlayer().updateInventory();
        player.updateInventory();
        player.sendMessage(ChatColor.GREEN + "Transaction successful.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player) || mode == TraderMode.STOCK
                || event.getSlotType() == SlotType.OUTSIDE)
            return;
        ItemStack slot = event.getView().getItem(event.getRawSlot());
        if (slot == null || slot.getType() == Material.AIR)
            return;
        event.setCancelled(true);
        if (event.isRightClick() || event.isShiftClick()) {
            player.sendMessage(ChatColor.GRAY + "Only plain left clicks are supported.");
            return;
        }
        int rawSlot = event.getRawSlot();
        boolean trader = event.getView().convertSlot(rawSlot) == rawSlot;
        // see convertSlot javadoc - if rawSlot is returned unchanged, then it's
        // a click on the top inventory.
        if (trader) {
            handleTraderClick(event);
        } else {
            handlePlayerClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player))
            return;
        exit();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!event.getPlayer().equals(player))
            return;
        exit();
    }

    private void sendJoinMessage() {
        switch (mode) {
        case INFINITE:
        case NORMAL:
            player.sendMessage(ChatColor.GREEN + "Transaction log");
            player.sendMessage(ChatColor.GOLD + "-------------------------------");
            break;
        case STOCK:
            player.sendMessage(ChatColor.GOLD + "Stocking of " + StringUtils.wrap(npc.getName(), ChatColor.GOLD)
                    + " started.");
            break;
        }
    }

    private void sendLeaveMessage() {
        switch (mode) {
        case INFINITE:
        case NORMAL:
            player.sendMessage(ChatColor.GOLD + "-------------------------------");
            break;
        case STOCK:
            player.sendMessage(ChatColor.GOLD + "Stocking of " + StringUtils.wrap(npc.getName(), ChatColor.GOLD)
                    + " finished.");
            break;
        }
    }

    private void sendNoMoneyMessage(ItemStack stocking, boolean selling) {
        String start = "The trader doesn't";
        if (selling) {
            start = "You don't";
        }
        player.sendMessage(ChatColor.RED + start + " have enough money available to buy "
                + MessageUtils.getStackString(stocking) + ".");
    }

    private void sendStockableMessage(Stockable stockable) {
        String[] message = TraderMessageUtils.getStockableMessage(stockable, ChatColor.AQUA).split("for");
        player.sendMessage(ChatColor.AQUA + "Item: " + message[0].trim());
        player.sendMessage(ChatColor.AQUA + "Price: " + message[1].trim());
        player.sendMessage(ChatColor.GOLD + "Click to confirm.");
    }
}