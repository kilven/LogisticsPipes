package logisticspipes.logisticspipes;

import logisticspipes.interfaces.ILogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.utils.SinkReply;
import net.minecraftforge.common.ForgeDirection;

public class ChassiTransportLayer extends TransportLayer{

	private final PipeLogisticsChassi _chassiPipe;
	
	public ChassiTransportLayer(PipeLogisticsChassi chassiPipe) {
		_chassiPipe = chassiPipe;
	}

	@Override
	public ForgeDirection itemArrived(IRoutedItem item) {
//		item.setSpeedBoost(50F);	//Boost speed to help item arrive faster so we don't get overflow
		return _chassiPipe.getPointedOrientation();
	}

	@Override
	public boolean stillWantItem(IRoutedItem item) {
		ILogisticsModule module = _chassiPipe.getLogisticsModule();
		if (module == null) return false;
		if (!_chassiPipe.isEnabled()) return false;
		SinkReply reply = module.sinksItem(item.getItemStack());
		if (reply == null) return false;
		
		if (reply.maxNumberOfItems != 0 && item.getItemStack().stackSize > reply.maxNumberOfItems){
			ForgeDirection o = _chassiPipe.getPointedOrientation();
			if (o==null || o == ForgeDirection.UNKNOWN) o = ForgeDirection.UP;
			
			IRoutedItem newItem = item.split(_chassiPipe.worldObj, reply.maxNumberOfItems, o.getOpposite());
			//return false;
		}
		
		return module.sinksItem(item.getItemStack()) != null;	
	}

}
