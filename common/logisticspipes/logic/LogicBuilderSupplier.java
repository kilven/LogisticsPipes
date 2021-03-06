/** 
 * Copyright (c) Krapht, 2011
 * 
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package logisticspipes.logic;

import java.util.Collection;
import java.util.HashMap;

import logisticspipes.interfaces.IChassiePowerProvider;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.pipes.PipeItemsBuilderSupplierLogistics;
import logisticspipes.request.RequestManager;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.InventoryUtil;
import logisticspipes.utils.InventoryUtilFactory;
import logisticspipes.utils.ItemIdentifier;
import logisticspipes.utils.WorldUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import buildcraft.builders.TileBuilder;
import buildcraft.core.utils.Utils;

public class LogicBuilderSupplier extends BaseRoutingLogic implements IRequireReliableTransport {
	
	private final InventoryUtilFactory _invUtilFactory;
	
	private final HashMap<ItemIdentifier, Integer> _requestedItems = new HashMap<ItemIdentifier, Integer>();
	
	private boolean _requestPartials = false;

	public IChassiePowerProvider _power;
	
	public boolean pause = false;
	
	
	public LogicBuilderSupplier() {
		this(new InventoryUtilFactory());
	}
	
	public LogicBuilderSupplier(InventoryUtilFactory inventoryUtilFactory){
		_invUtilFactory = inventoryUtilFactory;
		throttleTime = 100;
	}
	
	@Override
	public void destroy() {}

	
	@Override
	public void throttledUpdateEntity() {
		if (pause) return;
		super.throttledUpdateEntity();
		WorldUtil worldUtil = new WorldUtil(worldObj, xCoord, yCoord, zCoord);
		for (AdjacentTile tile :  worldUtil.getAdjacentTileEntities(true)){
			if (!(tile.tile instanceof TileBuilder)) continue;
			TileBuilder builder = (TileBuilder) tile.tile;
			
			IInventory inv = Utils.getInventory((IInventory) tile.tile);
			InventoryUtil invUtil = _invUtilFactory.getInventoryUtil(inv);
			
			//How many do I want?
			Collection<ItemStack> neededItems = builder.getNeededItems();
			HashMap<ItemIdentifier, Integer> needed = new HashMap<ItemIdentifier, Integer>();
			if (neededItems == null) return;
			for(ItemStack stack : neededItems){
				ItemIdentifier item = ItemIdentifier.get(stack);
				if (!needed.containsKey(item)){
					needed.put(item, stack.stackSize);
				} else {
					needed.put(item, needed.get(item) + stack.stackSize);
				}
			}
			
			//How many do I have?
			HashMap<ItemIdentifier, Integer> have = invUtil.getItemsAndCount();
			
			//Reduce what I have
			for (ItemIdentifier item : needed.keySet()){
				if (have.containsKey(item)){
					needed.put(item, needed.get(item) - have.get(item));
				}
			}
			
			//Reduce what have been requested already
			for (ItemIdentifier item : needed.keySet()){
				if (_requestedItems.containsKey(item)){
					needed.put(item, needed.get(item) - _requestedItems.get(item));
				}
			}
			
			((PipeItemsBuilderSupplierLogistics)this.container.pipe).setRequestFailed(false);
			
			//Make request
			for (ItemIdentifier need : needed.keySet()){
				if (needed.get(need) < 1) continue;
				int neededCount = needed.get(need);
				
				if(!_power.useEnergy(15)) {
					break;
				}
				
				boolean success = false;
				do{ 
					success = RequestManager.request(need.makeStack(neededCount),  (IRequestItems) container.pipe, getRouter().getIRoutersByCost(), null);
					if (success || neededCount == 1){
						break;
					}
					neededCount = neededCount / 2;
				} while (_requestPartials && !success);
				
				if (success){
					if (!_requestedItems.containsKey(need)){
						_requestedItems.put(need, neededCount);
					}else
					{
						_requestedItems.put(need, _requestedItems.get(need) + neededCount);
					}
				} else{
					((PipeItemsBuilderSupplierLogistics)this.container.pipe).setRequestFailed(true);
				}
				
			}
		}
	}

	@Override
	public void itemLost(ItemIdentifier item) {
		if (_requestedItems.containsKey(item)){
			_requestedItems.put(item, _requestedItems.get(item) - 1);
		}
	}

	@Override
	public void itemArrived(ItemIdentifier item) {
		super.resetThrottle();
		if (_requestedItems.containsKey(item)){
			_requestedItems.put(item, _requestedItems.get(item) - 1);
		}
		
	}

	@Override
	public void onWrenchClicked(EntityPlayer entityplayer) {
		
	}
}
