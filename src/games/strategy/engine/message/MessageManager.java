/*
 * MessageManager.java
 *
 * Created on December 26, 2001, 7:09 PM
 */

package games.strategy.engine.message;

import java.util.*;
import java.io.Serializable;
import games.strategy.net.GUID;

import games.strategy.net.*;

/**
 *
 * @author  Sean Bridges
 *
 * A message manger built on top of an IMessenger
 */
public class MessageManager implements IMessageManager
{
	private IMessenger m_messenger;
	
	//maps String -> IDestination
	private Map m_local = Collections.synchronizedMap(new HashMap());
	
	//maps String -> INode
	private Map m_remote = Collections.synchronizedMap(new HashMap());
	
	//maps GUID -> Object
	private Map m_locks = Collections.synchronizedMap(new HashMap());
	
	//Mpas GUID -> Message
	private Map m_responses = Collections.synchronizedMap(new HashMap());
	
	public MessageManager(IMessenger messenger)
	{
		m_messenger = messenger;
		
		m_messenger.addMessageListener(m_blockingMessageListener);
		m_messenger.addMessageListener(m_unblockMessageListener);
		m_messenger.addMessageListener(m_stateListener);
		
		m_messenger.broadcast(new InitRequest());
	}
	
	public void addDestination(IDestination destination)
	{
		m_local.put(destination.getName(), destination);
		
		Serializable msg = new DestinationChangeMessage(destination.getName(), true);
		m_messenger.broadcast(msg);
	}
	
	public void removeDestination(IDestination destination)
	{
		m_local.remove(destination.getName());
		
		Serializable msg = new DestinationChangeMessage(destination.getName(), false);
		m_messenger.broadcast(msg);
	}
	
	public Message send(Message msg, String destination)
	{
		if(m_local.containsKey(destination))
			return sendLocal(msg, (IDestination) m_local.get(destination));
		else if(m_remote.containsKey(destination))
			return sendRemote(msg, destination,  (INode) m_remote.get(destination));
		else
			throw new IllegalStateException("Destination not found:" + destination);
	}
	
	private Message sendLocal(Message msg, IDestination destination)
	{
		return destination.sendMessage(msg);
	}
	
	private Message sendRemote(Message msg, String destination, INode node)
	{
		BlockedMessage blockedMessage = new BlockedMessage(msg, destination);
		GUID id = blockedMessage.getID();
		
		Object lock = new Object();
		
		synchronized(lock)
		{
			m_locks.put(id, lock);
			
			m_messenger.send(blockedMessage, node);
			
			try
			{
				lock.wait();
			}
			catch(InterruptedException ie)
			{
				ie.printStackTrace();
				System.exit(0);
			}
				
			Message response = (Message) m_responses.get(id);
			
			//clean up
			m_locks.remove(id);
			m_responses.remove(id);
			return response;
		}	
	}
	
	public boolean hasDestination(String destination)
	{
		return m_local.containsKey(destination) || m_remote.containsKey(destination);
	}
	
	private IMessageListener m_blockingMessageListener = new IMessageListener()
	{
		public void messageReceived(Serializable msg, INode from)
		{		
			if(! (msg instanceof BlockedMessage))
				return;
			
			BlockedMessage clientMessage = (BlockedMessage) msg;
			
			if(!m_local.containsKey(clientMessage.getDestination()))
				throw new IllegalStateException("Destination not found:" + clientMessage.getDestination());
				
			IDestination target = (IDestination) m_local.get(clientMessage.getDestination());
			Message response = target.sendMessage(clientMessage.getMessage());
			UnblockMessage unBlock = new UnblockMessage(response, clientMessage.getID());
			m_messenger.send(unBlock, from);				
		}
	};
	
	private IMessageListener m_unblockMessageListener = new IMessageListener()
	{
		public void messageReceived(Serializable msg, INode from)
		{
			if(! (msg instanceof UnblockMessage))
				return;
	
			UnblockMessage clientMessage = (UnblockMessage) msg;
			GUID id = clientMessage.getID();
			Object lock = m_locks.get(id);

			if(lock == null)
				throw new IllegalStateException("No lock:" + msg);

			synchronized(lock)
			{
				m_responses.put(id, clientMessage.getResponse());
				lock.notifyAll();					
			}
		}
	};
	
	private IMessageListener m_stateListener = new IMessageListener()
	{
		public void messageReceived(Serializable msg, INode from)
		{
			if(msg instanceof DestinationChangeMessage)
			{
				destinationChanged((DestinationChangeMessage) msg, from);
			}
			else if(msg instanceof InitRequest)
			{
				initRequest( (InitRequest) msg, from);
			}
			else if (msg instanceof InitMessage)
			{
				initMessage( (InitMessage) msg, from);
			}
		}
		
		private void destinationChanged(DestinationChangeMessage destinationChange, INode from)
		{

			String destination = destinationChange.getDestination();
			if(destinationChange.isAdd())
				m_remote.put(destination, from);
			else
				m_remote.remove(destination);					
		}
		
		private void initRequest(InitRequest msg, INode from)
		{
			ArrayList destinations = new ArrayList(m_local.keySet());
			InitMessage init = new InitMessage(destinations);
			m_messenger.send(init, from);
			
		}
		
		private void initMessage(InitMessage msg, INode from)
		{
			Iterator iter = msg.getDestinations().iterator();
			while(iter.hasNext())
			{
				String dest = (String) iter.next();
				m_remote.put(dest, from);
			}
		}
	};
}