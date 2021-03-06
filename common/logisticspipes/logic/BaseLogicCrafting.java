package logisticspipes.logic;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.NetworkConstants;
import logisticspipes.network.packets.PacketCoordinates;
import logisticspipes.network.packets.PacketInventoryChange;
import logisticspipes.network.packets.PacketPipeInteger;
import logisticspipes.pipes.basic.RoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.interfaces.ICraftingRecipeProvider;
import logisticspipes.request.RequestManager;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.ItemIdentifier;
import logisticspipes.utils.SimpleInventory;
import logisticspipes.utils.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import buildcraft.core.network.TileNetworkData;
import buildcraft.transport.TileGenericPipe;
import cpw.mods.fml.common.network.Player;

public class BaseLogicCrafting extends BaseRoutingLogic implements IRequireReliableTransport {

	protected SimpleInventory _dummyInventory = new SimpleInventory(10, "Requested items", 127);
	
	@TileNetworkData
	public int signEntityX = 0;
	@TileNetworkData
	public int signEntityY = 0;
	@TileNetworkData
	public int signEntityZ = 0;
	//public LogisticsTileEntiy signEntity;

	protected final LinkedList<ItemIdentifier> _lostItems = new LinkedList<ItemIdentifier>();

	@TileNetworkData
	public int satelliteId = 0;

	@TileNetworkData
	public int priority = 0;

	public BaseLogicCrafting() {
		throttleTime = 40;
	}

	/* ** SATELLITE CODE ** */

	protected int getNextConnectSatelliteId(boolean prev) {
		final List<IRouter> routes = getRouter().getIRoutersByCost();
		int closestIdFound = prev ? 0 : Integer.MAX_VALUE;
		for (final BaseLogicSatellite satellite : BaseLogicSatellite.AllSatellites) {
			if (routes.contains(satellite.getRouter())) {
				if (!prev && satellite.satelliteId > satelliteId && satellite.satelliteId < closestIdFound) {
					closestIdFound = satellite.satelliteId;
				} else if (prev && satellite.satelliteId < satelliteId && satellite.satelliteId > closestIdFound) {
					closestIdFound = satellite.satelliteId;
				}
			}
		}
		if (closestIdFound == Integer.MAX_VALUE) {
			return satelliteId;
		}

		return closestIdFound;

	}

	public void setNextSatellite(EntityPlayer player) {
		satelliteId = getNextConnectSatelliteId(false);
		if (MainProxy.isClient(player.worldObj)) {
			final PacketCoordinates packet = new PacketCoordinates(NetworkConstants.CRAFTING_PIPE_NEXT_SATELLITE, xCoord, yCoord, zCoord);
			MainProxy.sendPacketToServer(packet.getPacket());
		} else {
			final PacketPipeInteger packet = new PacketPipeInteger(NetworkConstants.CRAFTING_PIPE_SATELLITE_ID, xCoord, yCoord, zCoord, satelliteId);
			MainProxy.sendPacketToPlayer(packet.getPacket(), (Player)player);
		}

	}
	
	// This is called by the packet PacketCraftingPipeSatelliteId
	public void setSatelliteId(int satelliteId) {
		this.satelliteId = satelliteId;
	}

	public void setPrevSatellite(EntityPlayer player) {
		satelliteId = getNextConnectSatelliteId(true);
		if (MainProxy.isClient(player.worldObj)) {
			final PacketCoordinates packet = new PacketCoordinates(NetworkConstants.CRAFTING_PIPE_PREV_SATELLITE, xCoord, yCoord, zCoord);
			MainProxy.sendPacketToServer(packet.getPacket());
		} else {
			final PacketPipeInteger packet = new PacketPipeInteger(NetworkConstants.CRAFTING_PIPE_SATELLITE_ID, xCoord, yCoord, zCoord, satelliteId);
			MainProxy.sendPacketToPlayer(packet.getPacket(), (Player)player);
		}
	}

	public boolean isSatelliteConnected() {
		for (final BaseLogicSatellite satellite : BaseLogicSatellite.AllSatellites) {
			if (satellite.satelliteId == satelliteId) {
				if (getRouter().getIRoutersByCost().contains(satellite.getRouter())) {
					return true;
				}
			}
		}
		return false;
	}

	public IRouter getSatelliteRouter() {
		for (final BaseLogicSatellite satellite : BaseLogicSatellite.AllSatellites) {
			if (satellite.satelliteId == satelliteId) {
				return satellite.getRouter();
			}
		}
		return null;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		_dummyInventory.readFromNBT(nbttagcompound, "");
		satelliteId = nbttagcompound.getInteger("satelliteid");
		signEntityX = nbttagcompound.getInteger("CraftingSignEntityX");
		signEntityY = nbttagcompound.getInteger("CraftingSignEntityY");
		signEntityZ = nbttagcompound.getInteger("CraftingSignEntityZ");
		
		priority = nbttagcompound.getInteger("priority");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		_dummyInventory.writeToNBT(nbttagcompound, "");
		nbttagcompound.setInteger("satelliteid", satelliteId);
		
		nbttagcompound.setInteger("CraftingSignEntityX", signEntityX);
		nbttagcompound.setInteger("CraftingSignEntityY", signEntityY);
		nbttagcompound.setInteger("CraftingSignEntityZ", signEntityZ);
		
		nbttagcompound.setInteger("priority", priority);
	}

	@Override
	public void destroy() {
		if(signEntityX != 0 && signEntityY != 0 && signEntityZ != 0) {
			worldObj.setBlockWithNotify(signEntityX, signEntityY, signEntityZ, 0);
			signEntityX = 0;
			signEntityY = 0;
			signEntityZ = 0;
		}
	}

	@Override
	public void onWrenchClicked(EntityPlayer entityplayer) {
		if (MainProxy.isServer(entityplayer.worldObj)) {
			entityplayer.openGui(LogisticsPipes.instance, GuiIDs.GUI_CRAFTINGPIPE_ID, worldObj, xCoord, yCoord, zCoord);
		}
	}

	@Override
	public void throttledUpdateEntity() {
		super.throttledUpdateEntity();
		if (_lostItems.isEmpty()) {
			return;
		}
		final Iterator<ItemIdentifier> iterator = _lostItems.iterator();
		while (iterator.hasNext()) {
			if (RequestManager.request(iterator.next().makeStack(1), ((RoutedPipe) container.pipe), ((RoutedPipe) container.pipe).getRouter().getIRoutersByCost(), null)) {
				iterator.remove();
			}
		}
	}

	@Override
	public void itemArrived(ItemIdentifier item) {
	}

	@Override
	public void itemLost(ItemIdentifier item) {
		_lostItems.add(item);
	}

	public void openAttachedGui(EntityPlayer player) {
		if (MainProxy.isClient(player.worldObj)) {
			if(player instanceof EntityPlayerMP) {
				((EntityPlayerMP)player).closeScreen();
			} else if(player instanceof EntityPlayerSP) {
				((EntityPlayerSP)player).closeScreen();
			}
			final PacketCoordinates packet = new PacketCoordinates(NetworkConstants.CRAFTING_PIPE_OPEN_CONNECTED_GUI, xCoord, yCoord, zCoord);
			MainProxy.sendPacketToServer(packet.getPacket());
			return;
		}
		final WorldUtil worldUtil = new WorldUtil(worldObj, xCoord, yCoord, zCoord);
		boolean found = false;
		for (final AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
			for (ICraftingRecipeProvider provider : SimpleServiceLocator.craftingRecipeProviders) {
				if (provider.canOpenGui(tile.tile)) {
					found = true;
					break;
				}
			}

			if (!found)
				found = (tile.tile instanceof IInventory && !(tile.tile instanceof TileGenericPipe));

			if (found) {
				Block block = worldObj.getBlockId(tile.tile.xCoord, tile.tile.yCoord, tile.tile.zCoord) < Block.blocksList.length ? Block.blocksList[worldObj.getBlockId(tile.tile.xCoord, tile.tile.yCoord, tile.tile.zCoord)] : null;
				if(block != null) {
					if(block.onBlockActivated(worldObj, tile.tile.xCoord, tile.tile.yCoord, tile.tile.zCoord, player, 0, 0, 0, 0)){
						break;
					}
				}
			}
		}
	}

	public void importFromCraftingTable(EntityPlayer player) {
		final WorldUtil worldUtil = new WorldUtil(worldObj, xCoord, yCoord, zCoord);
		for (final AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
			for (ICraftingRecipeProvider provider : SimpleServiceLocator.craftingRecipeProviders) {
				if (provider.importRecipe(tile.tile, _dummyInventory))
					break;
			}
		}
		
		if(player == null) return;
		
		if (MainProxy.isClient(player.worldObj)) {
			// Send packet asking for import
			final PacketCoordinates packet = new PacketCoordinates(NetworkConstants.CRAFTING_PIPE_IMPORT, xCoord, yCoord, zCoord);
			MainProxy.sendPacketToServer(packet.getPacket());
		} else{
			// Send inventory as packet
			final PacketInventoryChange packet = new PacketInventoryChange(NetworkConstants.CRAFTING_PIPE_IMPORT_BACK, xCoord, yCoord, zCoord, _dummyInventory);
			MainProxy.sendPacketToPlayer(packet.getPacket(), (Player)player);

		}
	}

	public void handleStackMove(int number) {
		if(MainProxy.isClient()) {
			MainProxy.sendPacketToServer(new PacketPipeInteger(NetworkConstants.CRAFTING_PIPE_STACK_MOVE,xCoord,yCoord,zCoord,number).getPacket());
		}
		ItemStack stack = _dummyInventory.getStackInSlot(number);
		if(stack == null ) return;
		for(int i = 6;i < 9;i++) {
			ItemStack stackb = _dummyInventory.getStackInSlot(i);
			if(stackb == null) {
				_dummyInventory.setInventorySlotContents(i, stack);
				_dummyInventory.setInventorySlotContents(number, null);
				break;
			}
		}
	}
	
	public void priorityUp(EntityPlayer player) {
		priority++;
		if(MainProxy.isClient()) {
			MainProxy.sendPacketToServer(new PacketCoordinates(NetworkConstants.CRAFTING_PIPE_PRIORITY_UP, xCoord, yCoord, zCoord).getPacket());
		} else if(MainProxy.isServer() && player != null) {
			MainProxy.sendPacketToPlayer(new PacketPipeInteger(NetworkConstants.CRAFTING_PIPE_PRIORITY, xCoord, yCoord, zCoord, priority).getPacket(), (Player)player);
		}
	}
	
	public void priorityDown(EntityPlayer player) {
		priority--;
		if(MainProxy.isClient()) {
			MainProxy.sendPacketToServer(new PacketCoordinates(NetworkConstants.CRAFTING_PIPE_PRIORITY_DOWN, xCoord, yCoord, zCoord).getPacket());
		} else if(MainProxy.isServer() && player != null) {
			MainProxy.sendPacketToPlayer(new PacketPipeInteger(NetworkConstants.CRAFTING_PIPE_PRIORITY, xCoord, yCoord, zCoord, priority).getPacket(), (Player)player);
		}
	}
	
	public void setPriority(int amount) {
		priority = amount;
	}
	
	/* ** INTERFACE TO PIPE ** */
	public ItemStack getCraftedItem() {
		return _dummyInventory.getStackInSlot(9);
	}

	public ItemStack getMaterials(int slotnr) {
		return _dummyInventory.getStackInSlot(slotnr);
	}

	/**
	 * Simply get the dummy inventory
	 * 
	 * @return the dummy inventory
	 */
	public SimpleInventory getDummyInventory() {
		return _dummyInventory;
	}
	
	public void setDummyInventorySlot(int slot, ItemStack itemstack) {
		_dummyInventory.setInventorySlotContents(slot, itemstack);
	}

	/* ** NON NETWORKING ** */
	@SuppressWarnings("deprecation")
	public void paintPathToSatellite() {
		final IRouter satelliteRouter = getSatelliteRouter();
		if (satelliteRouter == null) {
			return;
		}

		getRouter().displayRouteTo(satelliteRouter);
	}


}
