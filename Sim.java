import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


/* Matt Shepherd <s0935850>
 * This program can be run with some optional arguments:
 *  -t to switch to a psuedo-threaded mode (explained below)
 *  -s to disable send-backs
 *  -v to enable verbose output (mentions file + running options + number of sent tables before each run)
 *  
 *  This program can also be run with a list of files & options ie:
 *  Sim -v prog1 prog2 -t prog1 prog2 -s prog1 prog2
 *  Will run prog1 & prog2 and then run them again with the pseudo-threaded mode and then again with threading on and sendbacks off
 * 
 * prog1:
 * 
 * [p1]---[p2]
 *  |     /
 *  |    /
 *  |   /
 *  |  /
 *  | /
 * [p3]------[p4]
 * 
 * Sim -t prog1
 * Will run prog 1 giving each node a chance to process one received table at a time ie:
 * 	p1 starts
 * 	p4 processes one table
 * 	p3 processes one table
 * 	p2 processes one table
 * 	p1 processes one table
 * 	p4 processes one table
 * 	p3 processes one table
 *  p2 processes one table
 *  p1 processes one table  (and so on)
 *  
 *  otherwise the algorithm will act depth-first, with each node processing a table (the first in it's queue) immediately after being sent one.
 *  in the case of prog1 (no back returns for simplicity of the diagram):
 *  p1 -> p2
 *  	p2 -> p3
 *  		p3 -> p4
 *  			STOP
 *  		p3 -> p1
 *  			p1 -> p2
 *  				p2 -> p3
 *  					p3 -> p4
 *  						STOP
 *  					p3 -> p1
 *  					...
 *  					...
 *  p1 -> p3
 *  	p3 -> p4
 *  		STOP
 *  	p3 -> p2
 *  		p2 -> p1
 *  				...
 *  				...
 *  
 * 
QUESTIONS:
 
1)
Yes
Because if p1 only has one link (to p2) then addresses local to p1 are never passed to the rest of the network as a table containing their information is never returned.
The only time a node with one link will get it's local addresses into the table of other nodes is if it is the start node. It will receive information from the rest of the network if a cycle exists somewhere in the network. (assuming all nodes have a path to all nodes)
In the case that every node has more than one link, then passing back a table is not required as there will be a cycle in the network graph that the routing information will follow to reach all other nodes.
examples: (if table is not passed back)

p1: 1 2
p2: 3 4
[p1]---------[p2]

table p1 (1|local|0) (2|local|0)
table p2 (1|p1|1) (2|p1|1) (3|local|0) (4|local|0)

table 1 never discovers 3 & 4

==========================

prog1:
p1 1
p2 2
p3 3
p4 4 5

[p1]---[p2]
  |   /
  |  / 
  | /
  |/
[p3]----[p4]

=============================
prog3:
p1: 1 2
p2: 3 4
p3: 5

[p1]---------[p2]---------[p3]
  |                         |
  |_________________________|

two cycles in this network (opposite directions)
 
send p1 -> p2	
send p2 -> p3
send p3 -> p1
(and so on)

send p1 -> p3
send p3 -> p2
send p2 -> p1
(and so on)

This means that information effectively goes both ways, and each node ends up with a table with a route to every address

=================================



2)
The number of events needed for the algorithm to converge is dependent on all 3 factors (if tables are sent back, the actual network & the ordering of events).

For example, running my simulator with prog3 (with sendbacks & not using the psuedo-threaded mode) there are a total of 16 send events before the algorithm converges, but using the psuedo-threading it only takes 14 (resulting in the same routing tables). Since the psuedo-threading changes the order of the events, this suggests that event ordering effects the number of events beore convergence.

When the same network is simulated without psuedo-threading and sendbacks off, then the algorithm only takes 9 events to converge (eith both resulting in same tables). And with threading on and sendbacks off this only takes 8 events. 

These results suggest there is a relationship between event ordering & sendbacks that dictate the number of send events required for the algorithm to converge.

When the network contains a node with only one link (and this node does not start) then locals on that node will never have their address passed back to the rest of the network. This will affect how quickly the network will converge as some information will not get the chance to propegate.

In the case of prog1:
With send-backs the algorithm takes 28 send events to get a full routing table, whereas without sendback it only takes 10, but nodes p1 p2 p3 do not have any routing information for addresses 4 & 5.
With sendbacks & changing the ordering (pseudo-threading), the algorithm only takes 25 events to converge to the same tables.

		
 *  
 *  
 *  	
 * 
 * 
 */

public class Sim {

	
	static int sendEvents = 0;
	static int receiveEvents = 0;
	static int mode = 0;
	static boolean sendBack = true;
	static boolean verbose = false;
	
	// Class to store routing information,
	// An address, which node to go via & it's cost at that point
	 class Route {
		
		String address;
		String to;
		int cost;
	
	}
	
	// Class to simulate a node
	// Holds a table, with the address as the key, a set of links and a set of locals
	 class Node {
	
		String name;
		Map<String, Route> table;
		ArrayList<Node> links;
		ArrayList<String> locals;
		
		// Holds a table in a queue
		LinkedList<Map<String, Route>> queue;
		// a parallell queue to store who sent each table
		LinkedList<String> queuefrom;


		// initalise collections
		Node() {
			queue = new LinkedList<Map<String, Route>>();
			queuefrom = new LinkedList<String>();
			table= new HashMap<String, Route>();
			links = new ArrayList<Node>();
			locals = new ArrayList<String>();
		}
		
		// start on this node, send out it's table
		void start() {
			
			for(Node n : links) {
				sendEvents++;
				print(this.table, "send", n.name);
				n.receiveTable(this.copyTable(), this.name);
			}
		}
		
		// Makes a copy of it's table (to prevent a reference to a nodes table being pasted by accident)
		Map<String, Route> copyTable() {
			Map<String, Route> _t = new HashMap<String, Route>();
			for(Route r: table.values()) {
				Route _r = new Route();
				_r.address = r.address;
				_r.to = r.to;
				_r.cost = r.cost;
				_t.put(_r.address, r);
			}
			return _t;
		}
		
		// accept a table and place it in the queue, if we are not in pseudo-threading mode then process immediately
		void receiveTable(Map<String, Route> table, String from) {
			queue.add(table);
			queuefrom.add(from);
			if(mode==0) {
				processTable();
			}
		}
		
		// process a single table
		boolean processTable() {
			if(queue.size() > 0) {
				boolean changed = false;
				Map<String, Route> newtable = queue.pop();
				String link = queuefrom.pop();
				print(newtable, "receive", link);
				for(Route r : newtable.values()) {
					if(!this.table.containsKey(r.address)) {
						// our table does not contain this address, so add it
						Route _r = new Route();
						_r.address = r.address;
						_r.to = link;
						_r.cost = r.cost + 1;
						this.table.put(_r.address, _r);
						changed = true;
					}
					if(this.table.containsKey(r.address)) {
						if(r.cost +1 < this.table.get(r.address).cost) {
							// We have the address, but new route is better
							this.table.remove(r.address);
							Route _r = new Route();
							_r.address = r.address;
							_r.cost = r.cost + 1;
							_r.to = link;
							this.table.put(_r.address, _r);
							changed = true;
						}
						if(this.table.get(r.address).to == link) {

							if(this.table.get(r.address).cost - r.cost > 1) {
								//the cost for link is not exactly one less than ours
								Route _r = this.table.get(r.address);
								_r.address = r.address;
								_r.to = link;
								_r.cost = r.cost + 1;
								
								changed = true;
							}
						}
					}
				}
				// If we have changed, pass on changes
				if(changed) {
					
					for(Node n : links) {
						if(n.name.equals(link)) {
							
							if(sendBack) {
								// if sendbacks are on, return to sender
								print(this.table, "send", n.name);
								sendEvents++;
								n.receiveTable(this.copyTable(), this.name);
							}
						} else {
							// everyone else gets an updated table
							print(this.table, "send", n.name);
							sendEvents++;
							n.receiveTable(this.copyTable(), this.name);
						}
					}
				}
				return changed;
			}
			return false;
		}
		
		// add a new link
		boolean AddLink(Node to) {
			if(links.contains(to)) {
				return false;
			}
			links.add(to);
			return true;
		}
		
		// add a local
		void addLocal(String l) {
			Route r = new Route();
			r.address = l;
			r.to = "local";
			r.cost = 0;
			table.put(l,r);
			locals.add(l);
		}
		
		// string-ify  the node
		public String toString() {
			String rtn = name + ":";
			for(String l : locals) {
				rtn += " " + l;
			}
			return rtn;
		}
		
		// print out final table
		public void printFinal() {
			String routes =  " ";
			for(Route r : this.table.values()) {
				routes += "(" + r.address + "|" + r.to + "|" + r.cost + ") ";
				
			}
			
			
			System.out.println("table " + this.name  + routes);
		
		}
		
		// print out a command
		void print(Map<String, Route> tab, String cmd, String other) {
			
			String routes = " ";
			for(Route r : tab.values()) {
				routes += "(" + r.address + "|" + r.to + "|" + r.cost + ") ";
				
			}
			
			if(cmd.equals("send")) {
				System.out.println(cmd + " " + this.name + " " + other + routes);
			}
			if(cmd.equals("receive")) {
				System.out.println(cmd + " " + other + " " + this.name + routes);
			}
			
			
			
		}
		
	}
	
	
	// link two nodes
	static void MakeLink(Node a, Node b) {
		a.AddLink(b);
		b.AddLink(a);
	}
	
	// Main entry point
	public static void main(String[] args) {
		
		if(args.length > 0) {
			for(String f : args) {
				if(f.contains("-t")) {
					// enable pseudo-threading
					mode = 1;
				} else if(f.contains("-v")) {
					// enable verbose output
					verbose = true;
				} else if(f.contains("-s")) {
					// disbale send-backs
					sendBack = false;
				} else {
					// otherwise attempt to run file
					String message = "Running " + f + " : ";
					if(mode==1){ message += " WITH PSEUDO-THREAD ";}
					if(sendBack){ message += " WITH SEND BACK";} else {message += " NO SEND BACK";}
			
					if(verbose) {System.out.println(message);}
					sendEvents = 0;
					run(f);
					
					
					
				}
			}
		}
	}
	
	
	// run a file
	public static void run(String file) {
		// Load data from file
		ArrayList<String> srcData = new ArrayList<String>();
		try{
				FileInputStream fstream = new FileInputStream(file);
		
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				String strLine;
		
				while ((strLine = br.readLine()) != null)   {
						srcData.add(strLine);
					  //System.out.println (strLine);
				}
		
				in.close();
				} catch (Exception e){
					  System.err.println("Error: " + e.getMessage());
				}
			String toStart = "";
			Sim sm = new Sim();
			Map<String, Node> nodes = new HashMap<String, Node>();
			// handle each string in file
			for(String s : srcData) {
				String[] tokens = s.split(" ");
				if(tokens[0].equals("node")) {
					// Node command
					Sim.Node n =  sm.new Node();
					n.name = tokens[1];
					int i = tokens.length - 2;
					for(int j = 0; j < i; j++) {
						n.addLocal(tokens[j+2]);
						
					}
					nodes.put(n.name, n);
					//System.out.println(n.toString());
					
				}
				if(tokens[0].equals("link")) {
					// Link command
					MakeLink(nodes.get(tokens[1]), nodes.get(tokens[2]));
				}
				
				if(tokens[0].equals("send")) {
					// start a node
					toStart +=  tokens[1] + " ";
				}
				
				
				
			}
			
			if(!toStart.equals("")) {
				// split send commands an run them all (for multiple sends)
				for(String start : toStart.split(" ")) {
					nodes.get(start).start();
				}
				
			}
			
			if(mode==1) {
				// if we are in pseudo-threaded mode, give each node a chance to process a single table in order
				boolean changed = true;
				while(changed) {
					changed = false;
					for(Node n : nodes.values()) {
						if(n.processTable()) {changed=true;} 
					}
					
				}
				
				
			} else if(mode==0) {
				
			}
			// print out final talbes
			for(Node n : nodes.values()) {
				n.printFinal();
			}
			if(verbose) {
				// print number of send events
				System.out.println("Sends: " + sendEvents);
			}
	}


}
