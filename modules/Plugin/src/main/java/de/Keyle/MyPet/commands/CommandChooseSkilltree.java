/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2018 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.commands;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.gui.IconMenu;
import de.Keyle.MyPet.api.gui.IconMenuItem;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
import de.Keyle.MyPet.api.skill.skilltree.SkilltreeIcon;
import de.Keyle.MyPet.api.util.Colorizer;
import de.Keyle.MyPet.api.util.inventory.material.ItemDatabase;
import de.Keyle.MyPet.api.util.inventory.material.MaterialHolder;
import de.Keyle.MyPet.api.util.locale.Translation;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandChooseSkilltree implements CommandExecutor, TabCompleter {

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You can't use this command from server console!");
            return true;
        }
        Player player = (Player) sender;
        if (MyPetApi.getMyPetManager().hasActiveMyPet(player)) {
            final MyPet myPet = MyPetApi.getMyPetManager().getMyPet(player);
            final MyPetPlayer myPetOwner = myPet.getOwner();
            if (Configuration.Skilltree.AUTOMATIC_SKILLTREE_ASSIGNMENT && !myPet.getOwner().isMyPetAdmin()) {
                myPet.autoAssignSkilltree();
                sender.sendMessage(Translation.getString("Message.Command.ChooseSkilltree.AutomaticSkilltreeAssignment", myPet.getOwner().getLanguage()));
            } else if (myPet.getSkilltree() != null && Configuration.Skilltree.CHOOSE_SKILLTREE_ONLY_ONCE && !myPet.getOwner().isMyPetAdmin()) {
                sender.sendMessage(Util.formatText(Translation.getString("Message.Command.ChooseSkilltree.OnlyOnce", myPet.getOwner().getLanguage()), myPet.getPetName()));
            } else {
                if (args.length >= 1) {
                    String skilltreeName = "";
                    for (String arg : args) {
                        skilltreeName += arg + " ";
                    }
                    skilltreeName = skilltreeName.substring(0, skilltreeName.length() - 1);
                    if (MyPetApi.getSkilltreeManager().hasSkilltree(skilltreeName)) {
                        Skilltree skilltree = MyPetApi.getSkilltreeManager().getSkilltree(skilltreeName);
                        if (skilltree.getMobTypes().contains(myPet.getPetType()) && Permissions.has(player, skilltree.getFullPermission())) {
                            int requiredLevel = skilltree.getRequiredLevel();
                            if (requiredLevel > 1 && myPet.getExperience().getLevel() < requiredLevel) {
                                myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skilltree.RequiresLevel.Message", player), myPet.getPetName(), requiredLevel));
                            } else if (myPet.setSkilltree(skilltree)) {
                                sender.sendMessage(Util.formatText(Translation.getString("Message.Skilltree.SwitchedTo", player), skilltree.getName()));
                                if (!myPet.getOwner().isMyPetAdmin() || Configuration.Skilltree.SWITCH_FEE_ADMIN) {
                                    double switchPenalty = Configuration.Skilltree.SWITCH_FEE_FIXED;
                                    switchPenalty += myPet.getExperience().getExp() * Configuration.Skilltree.SWITCH_FEE_PERCENT / 100.;

                                    if (requiredLevel > 1) {
                                        double minExp = myPet.getExperience().getExpByLevel(requiredLevel);
                                        switchPenalty = myPet.getExp() - switchPenalty < minExp ? myPet.getExp() - minExp : switchPenalty;
                                    }
                                    if (Configuration.LevelSystem.Experience.ALLOW_LEVEL_DOWNGRADE) {
                                        myPet.getExperience().removeExp(switchPenalty);
                                    } else {
                                        myPet.getExperience().removeCurrentExp(switchPenalty);
                                    }
                                }
                            } else {
                                sender.sendMessage(Translation.getString("Message.Skilltree.NotSwitched", player));
                            }
                        } else {
                            sender.sendMessage(Util.formatText(Translation.getString("Message.Command.Skilltree.CantFindSkilltree", player), skilltreeName));
                        }
                    } else {
                        sender.sendMessage(Util.formatText(Translation.getString("Message.Command.Skilltree.CantFindSkilltree", player), skilltreeName));
                    }
                } else {
                    List<Skilltree> availableSkilltrees = new ArrayList<>();
                    for (Skilltree skilltree : MyPetApi.getSkilltreeManager().getOrderedSkilltrees()) {
                        if (skilltree.getMobTypes().contains(myPet.getPetType()) && Permissions.has(player, skilltree.getFullPermission())) {
                            availableSkilltrees.add(skilltree);
                        }
                    }

                    if (availableSkilltrees.size() == 0) {
                        sender.sendMessage(Util.formatText(Translation.getString("Message.Command.ChooseSkilltree.NoneAvailable", player), myPet.getPetName()));
                        return true;
                    }

                    final Map<Integer, Skilltree> skilltreeSlotMap = new HashMap<>();
                    IconMenu menu = new IconMenu(Util.cutString(Util.formatText(Translation.getString("Message.Skilltree.Available", myPetOwner), myPet.getPetName()), 32), event -> {
                        if (myPet != myPetOwner.getMyPet()) {
                            event.setWillClose(true);
                            event.setWillDestroy(true);
                            return;
                        }
                        if (skilltreeSlotMap.containsKey(event.getPosition())) {
                            Skilltree selecedSkilltree = skilltreeSlotMap.get(event.getPosition());
                            if (selecedSkilltree != null) {
                                int requiredLevel = selecedSkilltree.getRequiredLevel();
                                if (requiredLevel > 1 && myPet.getExperience().getLevel() < requiredLevel) {
                                    myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skilltree.RequiresLevel.Message", myPetOwner), myPet.getPetName(), requiredLevel));
                                } else if (myPet.setSkilltree(selecedSkilltree)) {
                                    myPet.getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skilltree.SwitchedTo", myPetOwner), selecedSkilltree.getName()));
                                    if (!myPet.getOwner().isMyPetAdmin() || Configuration.Skilltree.SWITCH_FEE_ADMIN) {
                                        double switchPenalty = Configuration.Skilltree.SWITCH_FEE_FIXED;
                                        switchPenalty += myPet.getExperience().getExp() * Configuration.Skilltree.SWITCH_FEE_PERCENT / 100.;

                                        if (requiredLevel > 1) {
                                            double minExp = myPet.getExperience().getExpByLevel(requiredLevel);
                                            switchPenalty = myPet.getExp() - switchPenalty < minExp ? myPet.getExp() - minExp : switchPenalty;
                                        }
                                        if (Configuration.LevelSystem.Experience.ALLOW_LEVEL_DOWNGRADE) {
                                            myPet.getExperience().removeExp(switchPenalty);
                                        } else {
                                            myPet.getExperience().removeCurrentExp(switchPenalty);
                                        }
                                    }
                                } else {
                                    myPet.getOwner().sendMessage(Translation.getString("Message.Skilltree.NotSwitched", myPetOwner));
                                }
                            }
                        }
                        event.setWillClose(true);
                        event.setWillDestroy(true);
                    }, MyPetApi.getPlugin());

                    ItemDatabase itemDatabase = MyPetApi.getServiceManager().getService(ItemDatabase.class).get();
                    for (int i = 0; i < availableSkilltrees.size(); i++) {
                        Skilltree addedSkilltree = availableSkilltrees.get(i);

                        SkilltreeIcon icon = addedSkilltree.getIcon();
                        MaterialHolder material = itemDatabase.getByID(icon.getMaterial());
                        if (material == null) {
                            material = itemDatabase.getByID("oak_sapling");
                        }
                        IconMenuItem option = new IconMenuItem().setMaterial(material.getMaterial()).setData(material.getLegacyId().getData()).setGlowing(icon.isGlowing());
                        option.setTitle(ChatColor.RESET + "❱❱❱  " + ChatColor.DARK_GREEN + Colorizer.setColors(addedSkilltree.getDisplayName()) + ChatColor.RESET + "  ❰❰❰");

                        boolean selectable = false;
                        int requiredLevel = addedSkilltree.getRequiredLevel();
                        if (requiredLevel > 1) {
                            selectable = myPet.getExperience().getLevel() >= addedSkilltree.getRequiredLevel();
                        }

                        List<String> description = new ArrayList<>();
                        if (requiredLevel > 1) {
                            String reqLevelMessage = ChatColor.RESET + "▶▶▶  ";
                            if (selectable) {
                                reqLevelMessage += ChatColor.GREEN;
                            } else {
                                reqLevelMessage += ChatColor.DARK_RED;
                            }
                            reqLevelMessage += Util.formatText(Translation.getString("Message.Skilltree.RequiresLevel.Item", myPetOwner), requiredLevel) + ChatColor.RESET + "  ◀◀◀";
                            description.add(reqLevelMessage);
                        }
                        for (String line : addedSkilltree.getDescription()) {
                            description.add(ChatColor.RESET + Colorizer.setColors(line));
                        }

                        option.addLore(description);
                        menu.setOption(i, option);
                        skilltreeSlotMap.put(i, addedSkilltree);
                    }
                    menu.open(player);
                }
            }
        } else {
            sender.sendMessage(Translation.getString("Message.No.HasPet", player));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] strings) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (MyPetApi.getMyPetManager().hasActiveMyPet(player)) {
                MyPet myPet = MyPetApi.getMyPetManager().getMyPet(player);
                if (Configuration.Skilltree.AUTOMATIC_SKILLTREE_ASSIGNMENT && !myPet.getOwner().isMyPetAdmin()) {
                    return Collections.emptyList();
                } else if (myPet.getSkilltree() != null && Configuration.Skilltree.CHOOSE_SKILLTREE_ONLY_ONCE && !myPet.getOwner().isMyPetAdmin()) {
                    return Collections.emptyList();
                } else {
                    List<String> skilltreeList = new ArrayList<>();
                    for (Skilltree skilltree : MyPetApi.getSkilltreeManager().getOrderedSkilltrees()) {
                        if (skilltree.getMobTypes().contains(myPet.getPetType()) && Permissions.has(player, skilltree.getPermission())) {
                            skilltreeList.add(skilltree.getName());
                        }
                    }
                    return skilltreeList;
                }
            }
        }
        return Collections.emptyList();
    }
}