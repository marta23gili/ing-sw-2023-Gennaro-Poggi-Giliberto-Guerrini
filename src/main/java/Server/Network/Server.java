package Server.Network;

import Server.Controller.Controller;
import Server.Events.SelectViewEvents.LoginView;
import Server.Events.SelectViewEvents.SelectViewEvent;
import Server.Model.Match;
import Server.Model.MatchStatus.NotRunning;
import Server.Model.MatchStatus.WaitingForPlayers;
import Utils.ConnectionInfo;

import java.io.IOException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * This class is the main class of the Server and is used to istantiate the RMI waiter and the Socket waiter,
 * which will wait for clients to connect to the server.
 * @see Server.Network.RMIWaiter
 * @see Server.Network.SocketWaiter
 * @author patrickpoggi
 */
public class Server implements Runnable{
    SocketWaiter socketWaiter;
    RMIWaiterInterface rmiWaiter;
    Registry rmiRegistry;
    List<Match> matches;
    Map<Match, Controller> matchesControllers;
    Map<Match, List<VirtualView>> matchesViews;
    PingManager pingManager;
    Map<ConnectionInfo, Boolean> clientsConnectionStatuses;

    Queue<VirtualView> clientsWaitingForMatch;

    public Server() throws IOException{
        boolean done = false;
        this.matches = new ArrayList<>();
        matchesViews = new HashMap<>();
        this.matchesControllers = new HashMap<>();
        this.clientsConnectionStatuses = new HashMap<>();
        pingManager = new PingManager(this, new CopyOnWriteArrayList<>(), new HashMap<>());
        this.socketWaiter = new SocketWaiter(this,1098);
        this.clientsWaitingForMatch = new LinkedList<>();
        while(!done){
            try{
                this.rmiWaiter = new RMIWaiter(this);
                done = true;
            }catch (RemoteException e){
                System.err.println("Impossible to istantiate the RMI waiter");
                System.err.println(e.getStackTrace());
            }
        }

    }

    public static void main(String[] args) {
        try{
            Server server = new Server();
            server.run();
        }catch (IOException e){
            System.err.println("Error occurred when trying to istantiate the socket waiter");
            System.err.println(e.getStackTrace());
        }

    }

    /**
     * This method is used to start the server. It will bind the RMI waiter to the RMI registry and start the socket
     * waiter in a new thread
     */
    @Override
    public void run() {
        boolean done = false;
        new Thread(socketWaiter).start();
        done = false;
        while(!done){
            try{
                this.rmiRegistry = LocateRegistry.createRegistry(1099);
                System.setProperty("java.rmi.server.hostname","localhost");
                done = true;
            }catch (RemoteException e){
                System.err.println(e.getStackTrace());
            }
        }

        done = false;
        while(!done){
            try{
                rmiRegistry.bind("RMIWaiter", rmiWaiter);
                done = true;
            }catch (AccessException e){
                System.err.println(e.getStackTrace());
            }catch (RemoteException e){
                System.err.println(e.getStackTrace());
            }catch (AlreadyBoundException e){
                System.err.println(e.getStackTrace());
            }
        }
        new Thread(pingManager).start();
        System.out.println("Server started");
    }

    /**
     * This method is used from both the RMI waiter and the Socket waiter to see if there is a match which is waiting
     * for players to join.
     * @return true if and only if there is a match which is waiting for players to join. If that's the case it must be
     * the last match in the list of matches.
     */
    protected synchronized boolean waitingMatch(){
        if(matches.size() == 0)
            return false;
        //return (matches.get(matches.size()-1).getMatchStatus() instanceof WaitingForPlayers) || (matches.get(matches.size()-1).getMatchStatus() instanceof NotRunning) ;
        return (matches.get(matches.size()-1).getMatchStatus() instanceof WaitingForPlayers);
    }

    public synchronized boolean matchWaitingForInit(){
        if(matches.size()==0){
            return false;
        }
        return (matches.get(matches.size()-1).getMatchStatus() instanceof NotRunning);
    }

    public synchronized Match getMatchWaitingForInit(){
        return matches.get(matches.size()-1);
    }

    /**
     * This method is used from both the RMI waiter and the Socket waiter to get the match which is waiting for players
     * @return the match which is waiting for players.
     */
    protected synchronized Match getWaitingMatch(){
        return matches.get(matches.size()-1);
    }

    /**
     * This method is used from both the RMI waiter and the Socket waiter to get the controller of a given
     * @return the controller of the given match
     */
    protected  Controller getMatchsController(Match m){
        synchronized (matchesControllers){
            return matchesControllers.get(m);
        }
    }

    /**
     * This method is used from both the RMI waiter and the Socket waiter to tell the server that a new match has been
     * created and that it must be added to the list of matches.
     * @param m is the new match
     * @param c is the controller of the new match
     * @param vv is the first virtual view we have istantiated for the new match, since if we had to create a new match
     *           there must have been one client connected to the server.
     */
    protected synchronized void subscribeNewMatch(Match m, Controller c, VirtualView vv){
        matches.add(m);
        matchesControllers.put(m,c);
        matchesViews.put(m, new ArrayList<>());
        matchesViews.get(m).add(vv);
        pingManager.addVirtualView(vv, c);
    }

    protected synchronized void subscribeNewViewToExistingMatch(Match m, VirtualView vv){
        //matchesViews.put(m,vv);
        /*If the match has at least one client "attached" to it but not logged in yet (thus not a contestant), we must
        avoid the possibility of it being over the number of players allowed in the match.
        */
        matchesViews.get(m).add(vv);
        pingManager.addVirtualView(vv, matchesControllers.get(m));
    }

    public boolean mayOverloadLastMatch(VirtualView vv){
        Match m = matches.get(matches.size()-1);
        if(matchesViews.get(m).size() > m.getPlayers().size()){
            //This means that we have one virtual view attached to the match more than the number of contestants
            //In this case this new client must be attached to that match only if, assuming that both the clients actually
            //become contestants, they don't overflow the numberof players allowed in the match
            int notLoggedInYet = matchesViews.get(m).size() - m.getPlayers().size();
            if(m.getPlayers().size()+(notLoggedInYet+1) <= m.getNumberOfPlayers()){
                //In this case we can add the new client to the match
                /*matchesViews.get(m).add(vv);
                pingManager.addVirtualView(vv, matchesControllers.get(m));*/
                return false;
            }else{
                /*We cannot be sure if the not-logged-in-yet client will actually become a contestant, freeing a spot,
                or not, so we can only wait*/
                /*subscribeNewWaitingClient(vv);
                vv.onSelectViewEvent(new LoginView(false, "Waiting for a player to login in order to insert you " +
                        "in the right match, please wait... "));*/
                return true;
            }
        }else{
            /*matchesViews.get(m).add(vv);
            pingManager.addVirtualView(vv, matchesControllers.get(m));
             */
            return false;
        }
    }

    public synchronized void subscribeNewWaitingClient(VirtualView waitingClient){
        this.clientsWaitingForMatch.add(waitingClient);
    }

    public synchronized void clientLoggedIn(VirtualView client){

    }

    public Queue<VirtualView> dequeueWaitingClients(){
        Match lastMatch;
        synchronized (matches){
            lastMatch = matches.get(matches.size()-1);
        }
        Queue<VirtualView> noMoreWaitingClients = new LinkedList<>();
        if(lastMatch.getMatchStatus() instanceof WaitingForPlayers){
            //int numbeOfMissingPlayers = lastMatch.getNumberOfPlayers()-lastMatch.getPlayers().size();
            int numbeOfMissingPlayers = lastMatch.getNumberOfPlayers()-matchesViews.get(lastMatch).size();
            while(numbeOfMissingPlayers>0){
                VirtualView vv;
                synchronized (clientsWaitingForMatch){
                    vv = clientsWaitingForMatch.poll();
                }
                if(vv == null){
                    System.err.println("Server.evolveLastMatch(): There are no more clients waiting for a match");
                    return noMoreWaitingClients;
                }
                noMoreWaitingClients.add(vv);
                lastMatch.addMVEventListener(vv);
                this.subscribeNewViewToExistingMatch(lastMatch, vv);
                this.updateConnectionStatus(vv.getConnectionInfo(), true);
                numbeOfMissingPlayers--;
            }
            if(!clientsWaitingForMatch.isEmpty()){
                clientsWaitingForMatch.peek().onSelectViewEvent(new LoginView(false, "Waiting for a player to login in order to insert you " +
                        "in the right match, please wait... "));
            }
            /*
            //If there are still clients waiting for joining a match, the first one will be a match opener and the others
            //wil continue waiting
            if(!clientsWaitingForMatch.isEmpty()){
                VirtualView vv;
                synchronized (clientsWaitingForMatch){
                    vv = clientsWaitingForMatch.poll();
                }
                if(vv == null){
                    throw new RuntimeException("[Server.dequeueWaitingClients()]: " +
                            "There seem to be clients waiting for a match, but the queue is empty");
                }
                vv.setIsFirstToJoin(true);
                Match m = new Match();
                Controller c = new Controller(m, this);
                m.addMVEventListener(vv);
                vv.addVCEventListener(c);
                c.addSelectViewEventListener(vv);
                this.subscribeNewMatch(m, c, vv);
                this.updateConnectionStatus(vv.getConnectionInfo(), true);
                vv.onSelectViewEvent(new LoginView(true));
            }
            */
        }else{
            if(!clientsWaitingForMatch.isEmpty()){
                VirtualView vv;
                synchronized (clientsWaitingForMatch){
                    vv = clientsWaitingForMatch.poll();
                }
                if(vv == null){
                    throw new RuntimeException("[Server.dequeueWaitingClients()]: " +
                            "There seem to be clients waiting for a match, but the queue is empty");
                }
                vv.setIsFirstToJoin(true);
                Match m = new Match();
                Controller c = new Controller(m, this);
                m.addMVEventListener(vv);
                vv.addVCEventListener(c);
                c.addSelectViewEventListener(vv);
                if(vv instanceof VirtualRMIView){
                    vv.run();
                    vv.onSelectViewEvent(new LoginView(true));
                }else{
                    new Thread(vv).start();
                }
                this.subscribeNewMatch(m, c, vv);
                this.updateConnectionStatus(vv.getConnectionInfo(), true);
            }
        }
        return noMoreWaitingClients;
    }

    public Queue<VirtualView> getClientsWaitingForMatch(){
        return clientsWaitingForMatch;
    }

    public boolean isWaitingQueueEmpty(){
        return clientsWaitingForMatch.isEmpty();
    }
    public Map<ConnectionInfo, Boolean> getClientsConnectionStatuses() {
        //return clientsConnectionStatuses;
        synchronized (clientsConnectionStatuses){
            return new HashMap<>(clientsConnectionStatuses);
        }
    }

    public void setClientsConnectionStatuses(ConnectionInfo connectionInfo, boolean status) {
        synchronized (clientsConnectionStatuses){
            this.clientsConnectionStatuses.put(connectionInfo, status);
        }
    }

    public Map<Match, Controller> getmatchesControllers() {
        //return matchesControllers;
        synchronized (matchesControllers){
            return new HashMap<>(matchesControllers);
        }
    }

    public Map<Match, List<VirtualView>> getMatchesViews() {
        //return matchesViews;
        synchronized (matchesViews) {
            return new HashMap<>(matchesViews);
        }
    }

    public void updateConnectionStatus(ConnectionInfo connectionInfo, boolean status){
        synchronized (clientsConnectionStatuses){
            clientsConnectionStatuses.put(connectionInfo, status);
        }
    }

    public boolean wasConnectedAndHasDisconnected(ConnectionInfo connectionInfo){
        synchronized (clientsConnectionStatuses){
            if(!clientsConnectionStatuses.containsKey(connectionInfo))
                return false;
            for(ConnectionInfo ci : clientsConnectionStatuses.keySet()){
                if(ci.getSignature().equals(connectionInfo.getSignature())){
                    return clientsConnectionStatuses.get(ci)==false;
                }
            }
            return false;
        }
    }

    public void disconnectClient(VirtualView clientsVV){
        //This method is called by the controller when a client disconnects from a
        //match in WaitingForPlayers status
        ConnectionInfo clientConnectionInfo = clientsVV.getConnectionInfo();
        Match matchWherePlayerWas = null;
        for(Match m : matchesViews.keySet()){
            if(matchesViews.get(m).contains(clientsVV)){
                matchWherePlayerWas = m;
                matchesViews.get(m).remove(clientsVV);
                break;
            }
        }
        matchesControllers.get(matchWherePlayerWas).removeSelectViewEventListener(clientsVV);
        //pingManager.removeVirtualView(clientsVV, matchesControllers.get(matchWherePlayerWas));
        clientsConnectionStatuses.remove(clientConnectionInfo);
        /*A client lost connection: if he wasn't logged in (his match was in WaitingForPlayers status), we must dequeue
        one client that was waiting (if any)*/
        if(matchWherePlayerWas.getMatchStatus() instanceof WaitingForPlayers){
            Queue<VirtualView> noMoreWaitingClients = this.dequeueWaitingClients(); //Almost sure size()<=1
            for(VirtualView vv: noMoreWaitingClients){
                matchesControllers.get(matchWherePlayerWas).addSelectViewEventListener(vv);
                vv.addVCEventListener(matchesControllers.get(matchWherePlayerWas));
                if(vv instanceof VirtualRMIView){
                    vv.run();
                }else{
                    new Thread(vv).start();
                }
                //vv.onSelectViewEvent(new LoginView(false));
            }
        }
    }

    public /*synchronized*/ void eraseMatch(Match m){
        //This method is called (1) from a NotRunning match when its opener disconnects or (2) from a WaitingForplayers
        //match with no more players (they all disconnected).
        //(1) If the match was NotRunning, we must make the head of the queue (if any) the new match opener
        //(2) just delete the match and its controller and its views

        //It may happen that this method gets called because all the clients that were logged in and waiting
        //for other players to join disconnected, but there still is one player connected to the server, whose
        //virtual view is thus in the macthesViews map, that hasn't logged in yet and wants to play. He, in fact, would
        //have been the player thanks to whom the match would have started. In this case,
        //we choose to still erase the match and its controller and its views, and to make the player that player
        //the match opener of another match.


        if(m != null && /*m.getMatchStatus() instanceof WaitingForPlayers ||*/ m.getMatchStatus() == null){
            if(matchesViews.get(m).size()>1){
                //There is at least one player that hasn't logged in yet and wants to play, while all the others have disconnected
                LinkedList<VirtualView> playersVV = new LinkedList<>();
                for(int i=1;i<matchesViews.get(m).size();i++){
                    playersVV.add(matchesViews.get(m).get(i));
                    matchesViews.get(m).remove(i);
                }
                VirtualView opener = playersVV.poll();
                opener.setIsFirstToJoin(true);
                opener.setPongReceived();
                opener.removeVCEventListener(matchesControllers.get(m)); //Removing the old controller from the opener's listeners
                opener.removeAllVCEventListeners();
                Match match = new Match();
                assert(match != null);
                Controller c = new Controller(match, this);
                match.addMVEventListener(opener);
                opener.addVCEventListener(c);
                assert(opener.getVCEventListeners().size() == 1);
                c.addSelectViewEventListener(opener);
                pingManager.removeVirtualView(opener, matchesControllers.get(m));
                this.subscribeNewMatch(match, c, opener);
                this.updateConnectionStatus(opener.getConnectionInfo(), true);
                opener.onSelectViewEvent(new LoginView(true, "Since some players have disconnected, you have " +
                        "become the match opener of another match. " +
                        "\nPlease insert your nickname and the number of players you want to play with."));
                playersVV.forEach(vv -> {
                    vv.onSelectViewEvent(new LoginView(false, "Please insert your nickname"));
                    vv.addVCEventListener(c);
                    c.addSelectViewEventListener(vv);
                    //this.updateConnectionStatus(vv.getConnectionInfo(), true);
                });
            }

            matchesViews.get(m).clear();
            matchesViews.remove(m);
            matchesControllers.remove(m);
            matches.remove(m);
            m = null;
        }else{
            //(1)
            VirtualView vv;
            synchronized (clientsWaitingForMatch){
                vv = clientsWaitingForMatch.poll();
            }
            if(vv != null){
                //There are some clients waiting for a match
                vv.setIsFirstToJoin(true);
                Match match = new Match();
                Controller c = new Controller(match, this);
                match.addMVEventListener(vv);
                vv.addVCEventListener(c);
                c.addSelectViewEventListener(vv);
                this.subscribeNewMatch(match, c, vv);
                this.updateConnectionStatus(vv.getConnectionInfo(), true);
                vv.setPongReceived();
                vv.onSelectViewEvent(new LoginView(true));
            }else{
                //There were no clients waiting for a match: simply erase it
                matchesViews.get(m).clear();
                matchesViews.remove(m);
                //}
                matchesControllers.remove(m);
                matches.remove(m);
                m = null;
            }
        }
        /*if(matchesViews.get(m).size()>1){
            //There is at least one player that hasn't logged in yet and wants to play, while all the others have disconnected
            LinkedList<VirtualView> playersVV = new LinkedList<>();
            for(int i=1;i<matchesViews.get(m).size();i++){
                playersVV.add(matchesViews.get(m).get(i));
                matchesViews.get(m).remove(i);
            }
            VirtualView opener = playersVV.poll();
            opener.setIsFirstToJoin(true);
            opener.removeVCEventListener(matchesControllers.get(m)); //Removing the old controller from the opener's listeners
            Match match = new Match();
            Controller c = new Controller(match, this);
            match.addMVEventListener(opener);
            opener.addVCEventListener(c);
            c.addSelectViewEventListener(opener);
            this.subscribeNewMatch(match, c, opener);
            this.updateConnectionStatus(opener.getConnectionInfo(), true);
            opener.onSelectViewEvent(new LoginView(true));
            opener.onSelectViewEvent(new LoginView(true, "Since some players have disconnected, you have " +
                    "become the match opener of another match. " +
                    "\nPlease insert your nickname and the number of players you want to play with."));
            playersVV.forEach(vv -> vv.onSelectViewEvent(new LoginView(false, "Please insert your nickname")));
        }

        //synchronized (pingManager.virtualViews){
            matchesViews.get(m).clear();
            matchesViews.remove(m);
        //}
        matchesControllers.remove(m);
        matches.remove(m);*/
        /*if(m.getMatchStatus() instanceof NotRunning){
            //(1)
            VirtualView vv;
            synchronized (clientsWaitingForMatch){
                vv = clientsWaitingForMatch.poll();
            }
            if(vv == null){
                throw new RuntimeException("[Server.eraseMatch()]: " +
                        "There seem to be clients waiting for a match, but the queue is empty");
            }
            vv.setIsFirstToJoin(true);
            Match match = new Match();
            Controller c = new Controller(match, this);
            match.addMVEventListener(vv);
            vv.addVCEventListener(c);
            c.addSelectViewEventListener(vv);
            this.subscribeNewMatch(match, c, vv);
            this.updateConnectionStatus(vv.getConnectionInfo(), true);
            vv.onSelectViewEvent(new LoginView(true));
        }*/
    }

    /*
    public boolean atLeastOneDisconnected(){
        synchronized (clientsConnectionStatuses){
            for(ConnectionInfo ci : clientsConnectionStatuses.keySet()){
                if(clientsConnectionStatuses.get(ci)==false)
                    return true;
            }
            return false;
        }

    }*/
}
