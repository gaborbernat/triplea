/*
 * TransportTracker.java
 *
 * Created on November 21, 2001, 3:51 PM
 */

package games.strategy.triplea.delegate;

import java.util.*;

import games.strategy.util.*;

import games.strategy.engine.data.*;

import games.strategy.triplea.Constants;
import games.strategy.triplea.attatchments.*;


/**
 *
 * @author  Sean Bridges
 * @version 1.0
 *
 * Tracks which transports are carrying which units.  Also tracks the capacity 
 * that has been unloaded.  To reset the unloaded call clearUnloadedCapacity().
 */
public class TransportTracker 
{
	
	public static int getCost(Collection units)
	{
		if(units == null)
			return 0;
		
		Iterator iter = units.iterator();
		int sum = 0;
		while(iter.hasNext())
		{
			Unit unit = (Unit) iter.next();
			UnitAttatchment ua = UnitAttatchment.get(unit.getType());
			sum += ua.getTransportCost();
		}
		return sum;
	}
	
	private Map m_transporting = new HashMap(); //maps unit -> transporter
	private Map m_transportedBy = new HashMap(); //maps transporter -> unit collection, inverse of m_transports
	private Map m_unloaded = new HashMap();
	
	/**
	 * Returns the collection of units that the given transport is transporting.
	 * Could be null.
	 */
	public Collection transporting(Unit transport)
	{
		Collection transporting = (Collection) m_transporting.get(transport);
		if(transporting == null)
			return null;
		
		return new ArrayList(transporting);
	}
	
	private Collection unloaded(Unit transport)
	{
		Collection unloaded = (Collection) m_unloaded.get(transport);
		if(unloaded == null)
			return Collections.EMPTY_LIST;
		return unloaded;
	}
	
	public Collection transportingAndUnloaded(Unit transport)
	{
		
		Collection rVal = transporting(transport);
		if(rVal == null)
			rVal = new ArrayList();
		
		rVal.addAll(unloaded(transport));
		return rVal;
	}
	
	/**
	 * Returns a map of transport -> collection of transported units.
	 */

	public Map transporting(Collection units)
	{
		Map returnVal = new HashMap();
		Iterator iter = units.iterator();
		while(iter.hasNext())
		{
			Unit transported = (Unit) iter.next();
			Unit transport = transportedBy(transported);
			Collection transporting = transporting(transport);
			if(transporting != null)
			{
				returnVal.put(transport, transporting);
			}
		}
		return returnVal;
	}
	
	public void unload(Unit unit)
	{
		UnitAttatchment ua = UnitAttatchment.get(unit.getType());
		int cost = ua.getTransportCost();
		
		Unit transport = (Unit) m_transportedBy.get(unit);
		m_transportedBy.remove(unit);
		unload(unit, transport);
		
		Collection carrying = (Collection) m_transporting.get(transport);
		carrying.remove(unit);
	}
	
	private void unload(Unit unit, Unit transport)
	{
		Collection unload = (Collection) m_unloaded.get(transport);
		if(unload == null)
		{
			unload = new ArrayList();
			m_unloaded.put(transport, unload);	
		}
		unload.add(unit);
	}
	
	public void load(Unit unit, Unit transport)
	{
		m_transportedBy.put(unit, transport);
		loadTransport(transport, unit);
	}
	
	private void loadTransport(Unit transport, Unit unit)
	{
		Collection carrying = (Collection) m_transporting.get(transport);
		if(carrying == null)
		{
			carrying = new ArrayList();
			m_transporting.put(transport, carrying);
		}
	
		if(!carrying.contains(unit))
			carrying.add(unit);
	}
	
	/**
	 * Return the transport that holds the given unit.
	 * Could be null.
	 */
	public Unit transportedBy(Unit unit)
	{
		return (Unit) m_transportedBy.get(unit);
	}
	
	public String toString()
	{
		StringBuffer buf = new StringBuffer();
		buf.append("transporting:").append(m_transporting).append("\n");
		buf.append("transportedBy:").append( m_transportedBy);
		return buf.toString();
	}
	
	public int getAvailableCapacity(Unit unit)
	{
		UnitAttatchment ua = UnitAttatchment.get(unit.getType());
		if(ua.getTransportCapacity() == -1)
			return 0;
		int capacity = ua.getTransportCapacity();
		int used = getCost( (Collection) m_transporting.get(unit));
		int unloaded = getCost( unloaded(unit) );
		return capacity - used - unloaded;
	}
	
	public void clearUnloadedCapacity()
	{
		m_unloaded.clear();
	}
}