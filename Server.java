package Lab04;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.io.Serializable;
import java.util.Arrays;


public class Server {
    public static void main(String[] args) {
        try{
            LocateRegistry.createRegistry(1099); // RMI on port 1099 (default)
            int numProcesses = 3;

            MaekawaManager manager = new MaekawaManager();
            ProcessInterface[] processes = new ProcessInterface[3];

            for(int x = 0; x < numProcesses; x++) {
                ProcessInterface remoteProcess = new Process(x, numProcesses, manager);
                processes[x] = remoteProcess;
                Naming.rebind("rmi://localhost/Process" + x, remoteProcess);
                System.out.println("Process " + x + " is ready");
            }
            processes[0].setQuorom(new ProcessInterface[]{processes[1], processes[2]});
            processes[1].setQuorom(new ProcessInterface[]{processes[0], processes[2]});
            processes[2].setQuorom(new ProcessInterface[]{processes[0], processes[1]});
            manager.addProcesses(processes);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}

class MaekawaManager {
    private ProcessInterface[] processList;
    private int currProcess;

    public MaekawaManager() {
        this.currProcess = -1;
    }

    public void grantAccess() throws RemoteException {
        if (currProcess == -1) {
            int[] processVotes = new int[processList.length];
            int max = 0;
            int maxIndex = -1;
            for (int x = 0; x < processList.length; x++) {
                int vote = processList[x].checkQueue();
                if (vote != -1) {
                    processVotes[vote] += 1;
                }
            }
            for (int i = 0; i < processVotes.length; i++) {
                if (processVotes[i] > max) {
                    max = processVotes[i];
                    maxIndex = i;
                }
            }
            if (max != 0) {
                currProcess = maxIndex;
                processList[maxIndex].reply();
            }
        }
    }

    public void releaseAccess() throws RemoteException {
        System.out.println("Released process " + currProcess);
        for(int x = 0; x < processList.length; x++){
            processList[x].release();
        }
        currProcess = -1;
    }

    public void handleRequest(Request request) throws RemoteException{
        for(int x = 0; x < processList.length; x++){
            processList[x].sendRequest(request);
        }
    }

    public void addProcesses(ProcessInterface[] processes) {
        processList = processes;
    }
}

interface ProcessInterface extends Remote {
    public void requestCriticalSection() throws RemoteException;
    public void releaseCriticalSection() throws RemoteException;
    public VectorClockInterface getVectorClock() throws RemoteException;
    public void reply() throws RemoteException;
    public void sendRequest(Request request) throws RemoteException;
    public void release() throws RemoteException;
    public int checkQueue() throws RemoteException;
    public void setQuorom(ProcessInterface[] otherProcesses) throws RemoteException;
}

class Process extends UnicastRemoteObject implements ProcessInterface {
    private int processId;
    private VectorClock vectorClock;
    public boolean criticalSectionStatus;
    private ProcessInterface[] quorom;
    private PriorityQueue<Request> requestQueue;
    public MaekawaManager manager;

    public Process(int processId, int totalProcesses, MaekawaManager manager) throws RemoteException {
        this.processId = processId;
        this.vectorClock = new VectorClock(totalProcesses);
        this.criticalSectionStatus = false;
        this.requestQueue = new PriorityQueue<>(Comparator.comparing((Request r) -> sum(r.getClock())).thenComparing(Request::getProcessId));
        this.manager = manager;
    }

    public int sum(int[] array){
        int total = 0;
        for(int x = 0; x < array.length; x++){
                total += array[x];
        }
        return(total);
}

    @Override
    public void requestCriticalSection() throws RemoteException {
        this.vectorClock.increment(processId);
        manager.handleRequest(new Request(processId, vectorClock));
        manager.grantAccess();
    }

    @Override
    public void releaseCriticalSection() throws RemoteException {
        this.criticalSectionStatus = false;
        manager.releaseAccess();
        if(!requestQueue.isEmpty()){
            manager.grantAccess();
        }
    }

    @Override
    public VectorClockInterface getVectorClock() throws RemoteException {
        return this.vectorClock;
    }

    @Override
    public void reply() {
        // in critical section
        System.out.println("Process " + processId + " is in critical section.");
        criticalSectionStatus = true;
    }

    @Override
    public void sendRequest(Request request) {
        requestQueue.add(request);
    }

    @Override
    public void release() throws RemoteException {
        requestQueue.poll();
    }
    @Override
    public int checkQueue(){
        if(requestQueue.isEmpty()){
            return -1;
        }
        return(requestQueue.peek().getProcessId());
    }

    @Override
    public void setQuorom(ProcessInterface[] otherProcesses){
        this.quorom = otherProcesses;
    }
    public int getId() {
        return this.processId;
    }
}

class Request {
    private int processId;       
    private VectorClock clock;   

    public Request(int processId, VectorClock clock) {
        this.processId = processId;
        this.clock = clock;
    }

    public int getProcessId() {
        return processId;
    }

    public int[] getClock() {
        return clock.getClock();
    }
}

interface VectorClockInterface extends Serializable {
    public int[] getClock(); // Returns internal clock array
    public void increment(int processId); // increment value of clock at processId
    public void update(VectorClock clock); // Compares local clock with remote clock and update with max
    public String toString(); // Returns clock value as string
}

class VectorClock implements VectorClockInterface {
    private int[] clock;

    public VectorClock(int numProcesses) {
        clock = new int[numProcesses];
    }

    @Override 
    public synchronized int[] getClock() {
        return clock.clone();
    }
    @Override
    public synchronized void increment(int processId) {
        clock[processId]++;
    }
    @Override
    public synchronized void update(VectorClock remoteClock) {
        int[] newClock = remoteClock.getClock();
        for(int x = 0; x < clock.length; x++) {
            clock[x] = Math.max(clock[x], newClock[x]);
        }
    }
    @Override
    public String toString() {
        return Arrays.toString(clock);
    }
}