import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ConnectException;
import java.util.Random;
import java.util.HashMap;

/*
 * Author: Edward Hong
 * Class:  CS 6378.0U1
 * Advanced Operating Systems - Project 1
 */

public class Client extends Thread {
	final Node self;
	final HashMap<Integer, ClientThread> threads = new HashMap<>();

	public Client(Node n) {
		this.self = n;
	}

    @Override
    public void run() {
		while(true) {
			if (threads.keySet().size() == self.neighbors.size()) break;	// Break once all channels established

			// Spawn a new thread for each channel to each neighbor
			for (Node neighbor : self.neighbors) {
				if (threads.keySet().contains(neighbor.id)) continue;
				try {
					//todo set hostname

					// Connect to neighbors
					Socket sock = new Socket("localhost", neighbor.listenPort);
					DataInputStream in = new DataInputStream(sock.getInputStream());	
					DataOutputStream out = new DataOutputStream(sock.getOutputStream());
					
					// Build index of channels to neighbors
					threads.put(neighbor.id, new ClientThread(sock, in, out, self));
				} catch (ConnectException c) {
					try {
						//debug
						//System.out.print(".");
						// Wait 50ms and try again
						Thread.sleep(50);	
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		// Start all threads
		for(Integer id : threads.keySet()) {
			threads.get(id).start();
		}

		// Once all channels established, begin MAP 
		while(true) {
			if(self.isActive()) {
				System.out.println("  ACTIVE");
				int randomMsgCount = new Random().ints(1, Node.MIN_PER_ACTIVE, Node.MAX_PER_ACTIVE + 1).findFirst().getAsInt();
				while(randomMsgCount > 0) {
					if(self.sentMessageCount >= Node.MAX_NUMBER) {
						self.setActive(false);
						break;
					}
					int randomIndex = new Random().nextInt(threads.keySet().size());
					Node destNode = self.neighbors.get(randomIndex);
					threads.get(destNode.id).addMessage(appMessage(self.id));
					randomMsgCount--;
	
					try {
						// Wait MIN_SEND_DELAY ms before sending again
						Thread.sleep(Node.MIN_SEND_DELAY);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				self.setActive(false);
			}
			
			// On Color Change from Blue to Red if we have marker messages to send, send them
			if(self.hasMarker()) {
				self.recordLocalState();

				// Multicast MARK message to all neighbors
				while(self.hasMarker()) {
					int id = self.removeMarker();
					threads.get(id).addMessage(markMessage(self.id));
				}
			}

			// Finished recording local state and channel state - sending FIN message to neighbors
			if(self.hasFinMessage()) {
				while(self.hasFinMessage()) {
					int id = self.removeFinMessage();
					threads.get(id).addMessage(finMessage(self.id));
				}
			}

			// Node termination
			if(self.hasDoneMessage()) { 
				// Root node initiates
				if(self.id == Node.startingNodeId) {
					while(self.hasDoneMessage()) {
						self.removeDoneMessage();
						for (Node neighbor : self.neighbors) {
							threads.get(neighbor.id).addMessage(doneMessage(self.id));							
						}
					}
				} else {
					// Multicast DONE message to all neighbors
					while(self.hasDoneMessage()) {
						int id = self.removeDoneMessage();
						threads.get(id).addMessage(doneMessage(self.id));
					}
				}
				
				// todo root has to wait for all other processes before terminating
				if (self.id != Node.startingNodeId) {
					System.out.println("YOU HAVE BEEN TERMINATED.");
					self.setTerminated(true);
					break;
				}
			}

			if (self.isTerminated()) {
				break;
			}
		}
		//cleanup
		//threads.clear();
	}
	
	private static String appMessage(int senderId) {
		return Node.APP_MESSAGE + senderId;
	}
	
	private static String markMessage(int senderId) {
		return Node.MARK_MESSAGE + senderId;
	}

	private static String finMessage(int senderId) {
		return Node.FIN_MESSAGE + senderId;
	}

	private static String doneMessage(int senderId) {
		return Node.DONE_MESSAGE + senderId;
	}
}